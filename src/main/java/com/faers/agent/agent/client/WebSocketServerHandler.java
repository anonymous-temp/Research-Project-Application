//package com.faers.agent.agent.client;
//
//import com.alibaba.fastjson.JSONObject;
//import io.netty.channel.Channel;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.SimpleChannelInboundHandler;
//import io.netty.handler.timeout.IdleState;
//import io.netty.handler.timeout.IdleStateEvent;
//import io.netty.handler.codec.http.websocketx.*;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.ApplicationEventPublisher;
//
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.TimeUnit;
//
///**
// * WebSocket服务端处理器
// * 处理握手、认证、心跳检测、消息转发等功能
// */
//@Slf4j
//public class WebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
//
//    private final ApplicationEventPublisher eventPublisher;
//    private final int timeoutSeconds;
//
//    // 存储已认证的客户端连接（key: channelId, value: clientInfo）
//    private static final Map<String, ClientInfo> authenticatedClients = new ConcurrentHashMap<>();
//
//    // 存储客户端认证信息（key: token, value: clientType）
//    private static final Map<String, String> tokenCache = new ConcurrentHashMap<>();
//
//    public WebSocketServerHandler(ApplicationEventPublisher eventPublisher, int timeoutSeconds) {
//        this.eventPublisher = eventPublisher;
//        this.timeoutSeconds = timeoutSeconds;
//    }
//
//    @Override
//    public void channelActive(ChannelHandlerContext ctx) throws Exception {
//        Channel channel = ctx.channel();
//        log.info("新的WebSocket连接: {}", channel.remoteAddress());
//        super.channelActive(ctx);
//    }
//
//    @Override
//    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
//        Channel channel = ctx.channel();
//        String channelId = channel.id().asShortText();
//
//        // 移除已认证的客户端
//        authenticatedClients.remove(channelId);
//        log.info("WebSocket连接断开: {} (channelId: {})", channel.remoteAddress(), channelId);
//
//        super.channelInactive(ctx);
//    }
//
//    @Override
//    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
//        Channel channel = ctx.channel();
//        String channelId = channel.id().asShortText();
//
//        // 处理关闭帧
//        if (frame instanceof CloseWebSocketFrame) {
//            log.info("收到客户端关闭请求: {}", channel.remoteAddress());
//            channel.close();
//            return;
//        }
//
//        // 处理Ping帧（心跳）
//        if (frame instanceof PingWebSocketFrame) {
//            log.debug("收到Ping心跳: {}", channel.remoteAddress());
//            // 回复Pong帧
//            channel.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
//            return;
//        }
//
//        // 处理Pong帧（心跳响应）
//        if (frame instanceof PongWebSocketFrame) {
//            log.debug("收到Pong响应: {}", channel.remoteAddress());
//            // 更新客户端最后活跃时间
//            ClientInfo clientInfo = authenticatedClients.get(channelId);
//            if (clientInfo != null) {
//                clientInfo.updateLastActiveTime();
//            }
//            return;
//        }
//
//        // 处理文本消息
//        if (frame instanceof TextWebSocketFrame) {
//            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
//            String message = textFrame.text();
////            log.debug("收到客户端消息: {} from {}", message, channel.remoteAddress());
//
//            try {
//                JSONObject jsonMessage = JSONObject.parseObject(message);
//                String type = jsonMessage.getString("type");
//
//                switch (type) {
//                    case "auth":
//                        handleAuth(ctx, jsonMessage, channelId);
//                        break;
//                    case "heartbeat":
//                        handleHeartbeat(ctx, channelId);
//                        break;
//                    case "text":
//                        // 检查消息方向：客户端发送的业务消息 or 服务端发送的响应消息
//                        if (jsonMessage.containsKey("targetClientId") && jsonMessage.containsKey("content")) {
//                            // 这是客户端发送的业务消息，需要转发给DialogRouter
//                            handleTextMessage(ctx, jsonMessage, channelId);
//                        } else {
//                            // 这可能是服务端发送的响应消息，直接转发给对应的客户端
//                            handleResponseMessage(ctx, jsonMessage, channelId);
//                        }
//                        break;
//                    default:
//                        log.warn("未知消息类型: {}", type);
//                        sendSystemMessage(ctx, "未知消息类型: " + type);
//                }
//            } catch (Exception e) {
//                log.error("处理消息异常: {}", message, e);
//                sendSystemMessage(ctx, "消息格式错误: " + e.getMessage());
//            }
//        } else {
//            log.warn("不支持的消息类型: {}", frame.getClass().getSimpleName());
//        }
//    }
//
//    /**
//     * 处理认证消息
//     */
//    private void handleAuth(ChannelHandlerContext ctx, JSONObject message, String channelId) {
//        Channel channel = ctx.channel();
//        String clientType = message.getString("clientType");
//        String token = message.getString("token");
//
//        log.info("收到认证请求: clientType={}, token={}, channelId={}", clientType, token, channelId);
//
//        // 简单的认证逻辑（可以根据实际需求修改）
//        // 这里使用简单的token验证，实际项目中应该从Redis或数据库验证
//        boolean authenticated = validateToken(token, clientType);
//
//        if (authenticated) {
//            // 保存客户端信息
//            ClientInfo clientInfo = new ClientInfo(channelId, clientType, token, channel);
//            authenticatedClients.put(channelId, clientInfo);
//
//            // 发送认证成功消息
//            JSONObject response = new JSONObject();
//            response.put("type", "system");
//            response.put("content", "认证成功");
//            channel.writeAndFlush(new TextWebSocketFrame(response.toJSONString()));
//
//            log.info("客户端认证成功: {} (channelId: {})", channel.remoteAddress(), channelId);
//        } else {
//            // 发送认证失败消息
//            JSONObject response = new JSONObject();
//            response.put("type", "system");
//            response.put("content", "认证失败，无效的令牌");
//            channel.writeAndFlush(new TextWebSocketFrame(response.toJSONString()));
//
//            log.warn("客户端认证失败: {} (channelId: {})", channel.remoteAddress(), channelId);
//
//            // 认证失败后关闭连接（可选）
//            // channel.close();
//        }
//    }
//
//    /**
//     * 验证Token（简化版，实际应该从Redis或数据库验证）
//     */
//    private boolean validateToken(String token, String clientType) {
//        if (token == null || token.isEmpty()) {
//            return false;
//        }
//
//        // 简单的token验证逻辑
//        // 实际项目中应该：
//        // 1. 从Redis获取token验证
//        // 2. 或调用认证API验证
//        // 3. 或从数据库验证
//
//        // 这里为了测试，接受任何非空token（实际应该替换为真实验证逻辑）
//        // 可以添加token到缓存，用于后续验证
//        if (!tokenCache.containsKey(token)) {
//            tokenCache.put(token, clientType);
//            // 设置token过期时间（24小时）
//            // 实际应该使用Redis的过期机制
//        }
//
//        return true; // 简化版：接受所有token
//    }
//
//    /**
//     * 处理心跳消息
//     */
//    private void handleHeartbeat(ChannelHandlerContext ctx, String channelId) {
//        Channel channel = ctx.channel();
//        ClientInfo clientInfo = authenticatedClients.get(channelId);
//
//        if (clientInfo != null) {
//            clientInfo.updateLastActiveTime();
//
//            // 回复心跳
//            JSONObject response = new JSONObject();
//            response.put("type", "server");
//            response.put("content", "heartbeat_ack");
//            channel.writeAndFlush(new TextWebSocketFrame(response.toJSONString()));
//
//            log.debug("回复心跳: {}", channel.remoteAddress());
//        } else {
//            log.warn("未认证客户端发送心跳: {}", channel.remoteAddress());
//        }
//    }
//
//    /**
//     * 处理文本消息（业务消息）
//     */
//    private void handleTextMessage(ChannelHandlerContext ctx, JSONObject message, String channelId) {
//        Channel channel = ctx.channel();
//        ClientInfo clientInfo = authenticatedClients.get(channelId);
//
//        // 检查是否已认证
//        if (clientInfo == null) {
//            log.warn("未认证客户端发送消息: {}", channel.remoteAddress());
//            sendSystemMessage(ctx, "请先进行认证");
//            return;
//        }
//
//        // 更新最后活跃时间
//        clientInfo.updateLastActiveTime();
//
//        // 提取消息内容
//        String targetClientId = message.getString("targetClientId");
//        String content = message.getString("content");
//
//        log.info("收到业务消息: targetClientId={}, content={}, channelId={}", targetClientId, content, channelId);
//
//        // 提取实际内容（可能是嵌套的JSON结构）
//        String actualContent = extractContent(content);
//
//        // 如果提取的内容为空，说明是系统回复，不应该处理
//        if (actualContent == null || actualContent.trim().isEmpty()) {
//            log.info("提取的内容为空，跳过处理（可能是系统回复）");
//            return;
//        }
//
//        log.info("提取的实际内容: {}", actualContent);
//
//        // 将消息发布为Spring事件，由DialogRouter处理
//        // DialogRouter期望的消息格式：targetClientId, id, parentId, userId, content
//        JSONObject eventMessage = new JSONObject();
//        eventMessage.put("targetClientId", targetClientId != null ? targetClientId : channelId);
//        eventMessage.put("id", message.getString("id"));
//        eventMessage.put("parentId", message.getString("parentId"));
//        eventMessage.put("userId", message.getString("userId"));
//        eventMessage.put("content", actualContent);
//
//        String eventMessageStr = eventMessage.toJSONString();
//        log.info("准备发布事件到DialogRouter: {}", eventMessageStr);
//
//        try {
//            // 发布事件，由DialogRouter监听处理
//            eventPublisher.publishEvent(eventMessageStr);
//            log.info("✅ 事件已成功发布到事件总线，等待DialogRouter处理");
//        } catch (Exception e) {
//            log.error("❌ 发布事件失败", e);
//            sendSystemMessage(ctx, "消息处理失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 从消息中提取实际内容
//     * 支持多种格式：
//     * 1. 直接字符串
//     * 2. JSON字符串（包含data.content、data.text、data.delta）
//     * 3. ResponseDTO格式
//     *
//     * 注意：需要区分用户输入和系统回复，避免循环处理
//     */
//    private String extractContent(String content) {
//        if (content == null || content.isEmpty()) {
//            return "";
//        }
//
//        try {
//            // 尝试解析为JSON
//            JSONObject contentJson = JSONObject.parseObject(content);
//
//            // 如果是ResponseDTO格式（包含data字段）
//            if (contentJson.containsKey("data")) {
//                Object dataObj = contentJson.get("data");
//                if (dataObj instanceof JSONObject) {
//                    JSONObject data = (JSONObject) dataObj;
//
//                    // 改进后的系统回复判断逻辑
//                    // 只有当delta内容确实是系统回复时才忽略处理
//                    if (data.containsKey("delta") && data.containsKey("inprogress")) {
//                        String deltaContent = data.getString("delta");
//                        // 这里可以根据实际业务逻辑调整判断条件
//                        if (deltaContent != null &&
//                            (deltaContent.contains("系统回复") ||
//                             deltaContent.contains("system reply") ||
//                             deltaContent.contains("认证成功") ||
//                             deltaContent.contains("heartbeat_ack") ||
//                             deltaContent.contains("认证失败") ||
//                             deltaContent.contains("连接成功"))) {
//                            // 这是真正的系统回复，不应该作为用户输入处理
//                            log.warn("检测到系统回复被当作用户输入，忽略处理: {}", deltaContent);
//                            return ""; // 返回空，避免循环处理
//                        } else {
//                            // 不是系统回复，尝试获取有效内容
//                            log.debug("包含delta和inprogress字段，但不是系统回复，尝试提取内容: {}", deltaContent);
//                            // 直接返回delta内容，因为客户端可能错误地将内容放在了delta字段
//                            return deltaContent;
//                        }
//                    }
//
//                    // 尝试获取content字段（用户输入）
//                    if (data.containsKey("content")) {
//                        return data.getString("content");
//                    }
//                    // 尝试获取text字段（用户输入）
//                    if (data.containsKey("text")) {
//                        return data.getString("text");
//                    }
//                    // 新增：尝试获取delta字段（用户输入，客户端可能错误放置）
//                    if (data.containsKey("delta")) {
//                        return data.getString("delta");
//                    }
//                }
//            }
//
//            // 如果直接包含content字段
//            if (contentJson.containsKey("content")) {
//                return contentJson.getString("content");
//            }
//
//            // 如果都不匹配，返回原始内容（可能是纯文本用户输入）
//            return content;
//        } catch (Exception e) {
//            // 如果不是JSON，直接返回原始内容（纯文本用户输入）
//            return content;
//        }
//    }
//
//    /**
//     * 处理响应消息（从DialogRouter返回的消息，需要转发给对应的客户端）
//     */
//    private void handleResponseMessage(ChannelHandlerContext ctx, JSONObject message, String channelId) {
//        // 响应消息应该直接发送给客户端
//        // 这里实际上不需要特殊处理，因为消息已经通过WebSocket发送了
////        log.debug("收到响应消息: {}", message.toJSONString());
//    }
//
//    /**
//     * 发送系统消息
//     */
//    private void sendSystemMessage(ChannelHandlerContext ctx, String content) {
//        JSONObject response = new JSONObject();
//        response.put("type", "system");
//        response.put("content", content);
//        ctx.channel().writeAndFlush(new TextWebSocketFrame(response.toJSONString()));
//    }
//
//    /**
//     * 根据targetClientId查找对应的客户端连接并发送消息
//     * 注意：这是一个简化实现，实际项目中可能需要更复杂的路由逻辑
//     */
//    public static void sendMessageToClient(String targetClientId, String message) {
//        // 查找对应的客户端连接
//        for (Map.Entry<String, ClientInfo> entry : authenticatedClients.entrySet()) {
//            ClientInfo clientInfo = entry.getValue();
//            // 这里简化处理：如果channelId匹配或clientType匹配，就发送
//            // 实际项目中应该使用targetClientId来路由
//            if (clientInfo.getChannel().isActive()) {
//                clientInfo.getChannel().writeAndFlush(new TextWebSocketFrame(message));
//                log.debug("消息已发送到客户端: channelId={}, message={}", entry.getKey(), message);
//                break; // 简化：只发送给第一个匹配的客户端
//            }
//        }
//    }
//
//    /**
//     * 处理空闲状态事件（心跳超时检测）
//     */
//    @Override
//    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//        if (evt instanceof IdleStateEvent) {
//            IdleStateEvent event = (IdleStateEvent) evt;
//            Channel channel = ctx.channel();
//            String channelId = channel.id().asShortText();
//
//            if (event.state() == IdleState.READER_IDLE) {
//                // 读空闲超时（客户端长时间未发送消息）
//                ClientInfo clientInfo = authenticatedClients.get(channelId);
//                if (clientInfo != null) {
//                    long idleTime = System.currentTimeMillis() - clientInfo.getLastActiveTime();
//                    if (idleTime > timeoutSeconds * 1000L) {
//                        log.warn("客户端读空闲超时，关闭连接: {} (空闲时间: {}ms)",
//                                channel.remoteAddress(), idleTime);
//                        channel.close();
//                    }
//                }
//            } else if (event.state() == IdleState.WRITER_IDLE) {
//                // 写空闲超时，可以发送Ping心跳
//                log.debug("写空闲，发送Ping心跳: {}", channel.remoteAddress());
//                channel.writeAndFlush(new PingWebSocketFrame());
//            }
//        } else {
//            super.userEventTriggered(ctx, evt);
//        }
//    }
//
//    @Override
//    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
//        log.error("WebSocket处理异常: {}", ctx.channel().remoteAddress(), cause);
//        ctx.close();
//    }
//
//    /**
//     * 客户端信息
//     */
//    private static class ClientInfo {
//        private final String channelId;
//        private final String clientType;
//        private final String token;
//        private final Channel channel;
//        private long lastActiveTime;
//
//        public ClientInfo(String channelId, String clientType, String token, Channel channel) {
//            this.channelId = channelId;
//            this.clientType = clientType;
//            this.token = token;
//            this.channel = channel;
//            this.lastActiveTime = System.currentTimeMillis();
//        }
//
//        public void updateLastActiveTime() {
//            this.lastActiveTime = System.currentTimeMillis();
//        }
//
//        public long getLastActiveTime() {
//            return lastActiveTime;
//        }
//
//        public String getChannelId() {
//            return channelId;
//        }
//
//        public String getClientType() {
//            return clientType;
//        }
//
//        public Channel getChannel() {
//            return channel;
//        }
//    }
//}
//
