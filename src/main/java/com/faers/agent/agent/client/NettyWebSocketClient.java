package com.faers.agent.agent.client;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;

import com.faers.agent.model.ResponseDTO;
import com.faers.agent.utils.RedisUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class NettyWebSocketClient implements WebSocketMessageSender, CommandLineRunner, DisposableBean {
    //@Autowired
    //private AiSearchAgentService aiSearchAgentService;
    // 支持新旧两种配置方式
    @Value("${wesocket.url}")
//    @Value("${websocket.client.url:${wesocket.url:ws://127.0.0.1:8080/ws/ws}}")
    private String webSocketUrl;
//        @Value("${websocket.client.api:${wesocket.api:http://127.0.0.1:8080/ai-agent/token?clientType=}}")
    @Value("${wesocket.api}")
    private String authTokenUrl;
    @Value("${wesocket.max-frame-size}")
    private int maxFrameSize;
    private final static String TOKEN_KEY = "netty:pro";
    private String heartbeatMessage;
    {
        JSONObject heartJson = new JSONObject();
        heartJson.put("type", "heartbeat");
        heartbeatMessage = heartJson.toJSONString();
    }

    @Override
    public void run(String... args) throws Exception {
        // 在Spring Boot启动完成后异步启动WebSocket客户端
        new Thread(this::start, "NettyWebSocketClient-Thread").start();
    }

    @Override
    public void destroy() throws Exception {
        stop();
    }

    private EventLoopGroup group;
    private Channel channel;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);

    // 用于发送心跳的单独EventExecutor，确保优先级
    private EventExecutorGroup heartbeatExecutor;

    private final ApplicationEventPublisher eventPublisher;

    public NettyWebSocketClient(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 启动WebSocket客户端
     */
    public void start() {
        if (isConnected.get()) {
            System.out.println("WebSocket客户端已连接，无需重复启动");
            return;
        }

        group = new NioEventLoopGroup();

        heartbeatExecutor = new DefaultEventExecutorGroup(1);

        try {
            URI uri = new URI(webSocketUrl);
            String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();
            final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
            final int port;
            if (uri.getPort() == -1) {
                if ("ws".equalsIgnoreCase(scheme)) {
                    port = 80;
                } else if ("wss".equalsIgnoreCase(scheme)) {
                    port = 443;
                } else {
                    port = -1;
                }
            } else {
                port = uri.getPort();
            }

            final WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                    uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

            // 关键：根据 scheme 判断是否需要 SSL/TLS（wss 需添加）
            final boolean isSecure = "wss".equalsIgnoreCase(scheme);
            SSLEngine sslEngine = null;
            if (isSecure) {
                // 创建 SSL 上下文（默认信任所有证书，生产环境需替换为自定义证书验证）
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }}, new SecureRandom());
                // 创建 SSLEngine（客户端模式）
                sslEngine = sslContext.createSSLEngine(host, port);
                sslEngine.setUseClientMode(true);
            }
            Bootstrap b = new Bootstrap();
            SSLEngine finalSslEngine = sslEngine;
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            // ========== 核心新增：WSS 协议添加 SSL 处理器 ==========
                            if (finalSslEngine != null) {
                                p.addLast(new SslHandler(finalSslEngine));
                            }
                            p.addLast(new HttpClientCodec());
                            // 大数据流支持
                            p.addLast(new ChunkedWriteHandler());
                            // HTTP消息聚合
                            p.addLast(new HttpObjectAggregator(10000));
                            // 关键：添加WebSocket压缩处理器（放在帧聚合器之前）
                            p.addLast(new WebSocketServerCompressionHandler());
                            // WebSocket帧聚合器，处理大帧
                            p.addLast(new WebSocketFrameAggregator(10000));
                            p.addLast(WebSocketClientCompressionHandler.INSTANCE);

                            // 添加空闲状态处理器，用于检测心跳超时
                            p.addLast(new IdleStateHandler(0, 20, 0, TimeUnit.SECONDS));

                            // WebSocket客户端处理器
                            p.addLast(new WebSocketClientHandler(handshaker));

                            // 心跳发送处理器，使用单独的EventExecutor确保优先级
                            p.addLast(heartbeatExecutor, new HeartbeatHandler(heartbeatMessage));
                        }
                    });

            System.out.println("连接WebSocket服务端: " + webSocketUrl);
            ChannelFuture future = b.connect(host, port).sync();
            channel = future.channel();

            // 等待握手完成
            WebSocketClientHandler handler = channel.pipeline().get(WebSocketClientHandler.class);
            handler.handshakeFuture().sync();

            isConnected.set(true);
            System.out.println("WebSocket客户端连接成功");

            // 等待连接关闭
            channel.closeFuture().sync();
        } catch (Exception e) {
            System.err.println("WebSocket客户端连接失败: " + e.getMessage());
            isConnected.set(false);
        } finally {
            // 等待事件循环组和心跳执行器彻底终止
            if (group != null) {
                group.shutdownGracefully().syncUninterruptibly(); // 同步等待终止
            }
            if (heartbeatExecutor != null) {
                heartbeatExecutor.shutdownGracefully().syncUninterruptibly(); // 同步等待终止
            }
            isConnected.set(false);
            System.out.println("WebSocket客户端已关闭");

            // 断线重连逻辑
            try {
                TimeUnit.SECONDS.sleep(5);
                start(); // 尝试重连
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 发送消息到服务端
     */
    @Override
    public void sendMessage(String message) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(message));
        } else {
            System.err.println("WebSocket连接未建立，无法发送消息");
        }
    }

    @Override
    public void   sendMessage(String targetClientId, String content, String id,
                              String parentId,
                              String userId) {
        JSONObject payload = new JSONObject();
        payload.put("type", "text");
//        payload.put("senderId",targetClientId);
        payload.put("parentId", parentId);
        payload.put("id", id);
        payload.put("senderType", "project-application-form");
        payload.put("targetClientId", targetClientId);
        payload.put("userId", userId);
        payload.put("agentType", "project-application-form");

        ResponseDTO responseDTO = new ResponseDTO("stream", new ResponseDTO.DataDTO("text", content, true, null, null));
        payload.put("content", JSONObject.toJSONString(responseDTO));

        // 打印完整响应JSON，便于调试
        System.out.println("发送响应到客户端: " + payload.toJSONString());

        sendMessage(payload.toJSONString());
    }

    @Override
    public void   sendMessageTable(String targetClientId, String content, String id,
                                   String parentId,
                                   String userId) {
        JSONObject payload = new JSONObject();
        payload.put("type", "text");
        payload.put("parentId", parentId);
        payload.put("id", id);
        payload.put("senderId", targetClientId);
        payload.put("senderType", "project-application-form");
        payload.put("targetClientId", targetClientId);
        payload.put("userId", userId);
        payload.put("agentType", "project-application-form");
        payload.put("content", content);

        // 打印完整响应JSON，便于调试
//        System.out.println("发送表格响应到客户端: " + payload.toJSONString());

        sendMessage(payload.toJSONString());

    }

    /**
     * WebSocket客户端处理器
     */
    private class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {
        private final WebSocketClientHandshaker handshaker;
        private ChannelPromise handshakeFuture;

        public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
            this.handshaker = handshaker;
        }

        public ChannelFuture handshakeFuture() {
            return handshakeFuture;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            handshakeFuture = ctx.newPromise();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            handshaker.handshake(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            System.out.println("WebSocket连接已断开");
            isConnected.set(false);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                try {
                    handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                    System.out.println("WebSocket握手完成");
                    handshakeFuture.setSuccess();
                    // 握手完成后立即发送认证消息
                    sendAuthMessage(ctx, "");
                } catch (WebSocketHandshakeException e) {
                    System.err.println("WebSocket握手失败: " + e.getMessage());
                    handshakeFuture.setFailure(e);
                }
                return;
            }

            if (msg instanceof FullHttpResponse) {
                FullHttpResponse response = (FullHttpResponse) msg;
                throw new IllegalStateException(
                        "Unexpected FullHttpResponse (getStatus=" + response.status() +
                                ", content=" + response.content().toString() + ')');
            }

            WebSocketFrame frame = (WebSocketFrame) msg;
            if (frame instanceof TextWebSocketFrame) {
                TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
                String messageStr = textFrame.text();
                System.out.println("收到服务端消息: " + messageStr);
                try {
                    JSONObject message = JSONObject.parseObject(messageStr);
                    String type = message.getString("type");
                    System.out.println("解析消息类型: " + type);

                    switch (type) {
                        case "server":
                            System.out.println("接收到服务端返回心跳消息回复：" + messageStr);
                            break;
//                        case "system":
//                            String content = message.getString("content");
//                            System.out.println("接收到系统消息：" + content);
//                            if ("认证失败，无效的令牌".equals(content)) {
//                                // 重新获取token进行验证
//                                System.out.println("认证失败，正在重新获取token...");
//                                // 先删除Redis中的旧token，强制重新获取
//                                RedisUtil.redis.delete(TOKEN_KEY);
//                                System.out.println("已清除旧token，将重新获取");
//                                sendAuthMessage(ctx);
//                            }
//                            break;
                        case "system":
                            String content = message.getString("content");
                            if ("认证失败，无效的令牌".equals(content)) {
                                // 重新获取token进行验证
                                sendAuthMessage(ctx, "认证失败，无效的令牌");
                            }
                            break;
                        case "text":
                            System.out.println("接收到服务端消息：" + messageStr);
                            System.out.println("业务消息详情：");
                            System.out.println("  - parentId: " + message.getString("parentId"));
                            System.out.println("  - id: " + message.getString("id"));
                            System.out.println("  - targetClientId: " + message.getString("targetClientId"));
                            System.out.println("  - userId: " + message.getString("userId"));
                            System.out.println("  - content: " + message.getString("content"));
                            // 将接收到的消息封装成事件并发布
                            eventPublisher.publishEvent(messageStr);
                            break;
                        default:
                            System.out.println("接收到未知类型消息：" + type);
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("解析WebSocket消息失败: " + e.getMessage());
                    e.printStackTrace();
                }
            } else if (frame instanceof PongWebSocketFrame) {
                //System.out.println("收到服务端Pong响应");
                log.info("收到{}服务端Pong响应", ch.remoteAddress());
            } else if (frame instanceof CloseWebSocketFrame) {
                System.out.println("收到服务端关闭连接请求");
                ch.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            if (!handshakeFuture.isDone()) {
                handshakeFuture.setFailure(cause);
            }
            ctx.close();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            // 传递事件给下一个处理器（如HeartbeatHandler）
            //super.userEventTriggered(ctx, evt); // 保留父类逻辑
            ctx.fireUserEventTriggered(evt);
        }
    }

    /**
     * 发送认证消息
     */
    private void sendAuthMessage(ChannelHandlerContext ctx, String info) {
        String token;
        String clientType = "project-application-form";
        if (info.contains("无效的令牌")) {
            String s = HttpUtil.get(authTokenUrl + clientType);
            System.out.println(clientType + "获取token数据为：" + s);
            JSONObject resultJson = JSONObject.parseObject(s);
            token = resultJson.getJSONObject("data").getString("token");
            RedisUtil.redis.opsForValue().set(TOKEN_KEY, token, 24, TimeUnit.HOURS);
        }
        Object objectKey = RedisUtil.redis.opsForValue().get(TOKEN_KEY);
        if (objectKey != null) {
            token = objectKey.toString();
        } else {
            String s = HttpUtil.get(authTokenUrl + clientType);
            System.out.println(clientType + "获取token数据为：" + s);
            JSONObject resultJson = JSONObject.parseObject(s);
            token = resultJson.getJSONObject("data").getString("token");
            RedisUtil.redis.opsForValue().set(TOKEN_KEY, token, 24, TimeUnit.HOURS);
        }
        // 构建认证消息
        JSONObject authMessage = new JSONObject();
        authMessage.put("type", "auth");
        authMessage.put("clientType", clientType);
        authMessage.put("token", token);
        String jsonMessage = authMessage.toJSONString();
        // 发送认证消息
        ctx.writeAndFlush(new TextWebSocketFrame(jsonMessage));
        System.out.println("已发送认证消息: " + jsonMessage);
    }

    /**
     * 心跳处理器
     */
    private static class HeartbeatHandler extends ChannelInboundHandlerAdapter {
        private final String heartbeatMessage;

        public HeartbeatHandler(String heartbeatMessage) {
            this.heartbeatMessage = heartbeatMessage;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state() == IdleState.WRITER_IDLE) {
                    //ctx.writeAndFlush(new TextWebSocketFrame(heartbeatMessage));
                    //log.info("向{}服务端发送 Ping 心跳", ctx.channel().remoteAddress().toString().replace("/", ""));
                    //ctx.writeAndFlush(new PingWebSocketFrame());
                    // 确保在发送前检查通道状态
                    if (ctx.channel().isActive() && ctx.channel().isWritable()) {
                        log.info("向{}发送Ping心跳", ctx.channel().remoteAddress());
                        // 使用WebSocket规范的Ping帧
                        ctx.writeAndFlush(new PingWebSocketFrame())
                                .addListener(future -> {
                                    if (!future.isSuccess()) {
                                        log.error("心跳发送失败", future.cause());
                                        // 发送失败时触发重连
                                        ctx.channel().close();
                                    }
                                });
                    }
                }
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }
    }

    /**
     * 关闭WebSocket客户端
     */
    public void stop() {
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        heartbeatExecutor.shutdownGracefully();
        isConnected.set(false);
    }

}
