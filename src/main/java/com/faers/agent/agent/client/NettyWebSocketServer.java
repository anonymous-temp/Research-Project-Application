//package com.faers.agent.agent.client;
//
//import io.netty.bootstrap.ServerBootstrap;
//import io.netty.channel.*;
//import io.netty.channel.nio.NioEventLoopGroup;
//import io.netty.channel.socket.SocketChannel;
//import io.netty.channel.socket.nio.NioServerSocketChannel;
//import io.netty.handler.codec.http.HttpObjectAggregator;
//import io.netty.handler.codec.http.HttpServerCodec;
//import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
//import io.netty.handler.logging.LogLevel;
//import io.netty.handler.logging.LoggingHandler;
//import io.netty.handler.timeout.IdleStateHandler;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.DisposableBean;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.ApplicationEventPublisher;
//import org.springframework.stereotype.Component;
//
//import java.util.concurrent.TimeUnit;
//
///**
// * Netty WebSocket 服务端
// * 提供本地WebSocket服务，支持握手、认证、心跳检测等功能
// */
//@Slf4j
//@Component
//public class NettyWebSocketServer implements CommandLineRunner, DisposableBean {
//
//    @Value("${websocket.server.port:8080}")
//    private int serverPort;
//
//    @Value("${websocket.server.path:/ws/ws}")
//    private String serverPath;
//
//    @Value("${websocket.server.timeout:600}")
//    private int timeoutSeconds;
//
//    private EventLoopGroup bossGroup;
//    private EventLoopGroup workerGroup;
//    private Channel serverChannel;
//    private final ApplicationEventPublisher eventPublisher;
//
//    public NettyWebSocketServer(ApplicationEventPublisher eventPublisher) {
//        this.eventPublisher = eventPublisher;
//    }
//
//    @Override
//    public void run(String... args) throws Exception {
//        // 在Spring Boot启动完成后异步启动WebSocket服务端
//        new Thread(this::start, "NettyWebSocketServer-Thread").start();
//    }
//
//    /**
//     * 启动WebSocket服务端
//     */
//    public void start() {
//        bossGroup = new NioEventLoopGroup(1);
//        workerGroup = new NioEventLoopGroup();
//
//        try {
//            ServerBootstrap bootstrap = new ServerBootstrap();
//            bootstrap.group(bossGroup, workerGroup)
//                    .channel(NioServerSocketChannel.class)
//                    .option(ChannelOption.SO_BACKLOG, 1024)
//                    .option(ChannelOption.SO_REUSEADDR, true)
//                    .childOption(ChannelOption.TCP_NODELAY, true)
//                    .childOption(ChannelOption.SO_KEEPALIVE, true)
//                    .handler(new LoggingHandler(LogLevel.INFO))
//                    .childHandler(new ChannelInitializer<SocketChannel>() {
//                        @Override
//                        protected void initChannel(SocketChannel ch) {
//                            ChannelPipeline pipeline = ch.pipeline();
//
//                            // HTTP编解码器
//                            pipeline.addLast(new HttpServerCodec());
//                            pipeline.addLast(new HttpObjectAggregator(65536));
//
//                            // WebSocket协议处理器（处理握手）
//                            pipeline.addLast(new WebSocketServerProtocolHandler(
//                                    serverPath, null, true, 65536, false, true, 1200000));
//
//                            // 空闲状态检测（用于心跳检测）
//                            // 读空闲：1200秒（20分钟），写空闲：20秒，全部空闲：0（不检测）
//                            pipeline.addLast(new IdleStateHandler(1200, 20, 0, TimeUnit.SECONDS));
//
//                            // WebSocket服务端业务处理器
//                            pipeline.addLast(new WebSocketServerHandler(eventPublisher, timeoutSeconds));
//                        }
//                    });
//
//            log.info("启动WebSocket服务端，端口: {}, 路径: {}", serverPort, serverPath);
//            ChannelFuture future = bootstrap.bind(serverPort).sync();
//            serverChannel = future.channel();
//            log.info("WebSocket服务端启动成功，监听端口: {}", serverPort);
//
//            // 等待服务器关闭
//            serverChannel.closeFuture().sync();
//        } catch (InterruptedException e) {
//            log.error("WebSocket服务端启动失败", e);
//            Thread.currentThread().interrupt();
//        } catch (Exception e) {
//            log.error("WebSocket服务端启动异常", e);
//        } finally {
//            shutdown();
//        }
//    }
//
//    /**
//     * 关闭WebSocket服务端
//     */
//    public void shutdown() {
//        if (serverChannel != null) {
//            serverChannel.close();
//        }
//        if (bossGroup != null) {
//            bossGroup.shutdownGracefully();
//        }
//        if (workerGroup != null) {
//            workerGroup.shutdownGracefully();
//        }
//        log.info("WebSocket服务端已关闭");
//    }
//
//    @Override
//    public void destroy() throws Exception {
//        shutdown();
//    }
//}
//
//
