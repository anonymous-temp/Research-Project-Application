package com.faers.agent.service;

import com.faers.agent.model.Session;
import com.faers.agent.repository.SessionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于MongoDB的Session存储服务
 * 原 RedisSessionStorageService 迁移为使用 MongoDB 存储，会话功能保持一致
 *
 * 特性：
 * - 持久化存储，服务重启后数据不丢失
 * - 线程安全的并发访问（由MongoDB和Spring Data保证）
 * - 自动过期清理（基于lastAccessed和配置的过期时间）
 * - 按用户查询会话
 */
@Service
public class MongodbSessionStorageService {

    private static final Logger log = LoggerFactory.getLogger(MongodbSessionStorageService.class);

    // 应用名称（用于日志）
    @Value("${spring.application.name:agent-evimed}")
    private String applicationName;

    // 过期时间（秒），沿用原有配置键
    @Value("${session.storage.redis.expire-time:2592000}")
    private long sessionExpireTime;

    // 清理任务的Cron表达式（沿用原有配置键）
    @Value("${session.storage.mongo.cleanup-cron:0 0 * * * ?}")
    private String cleanupCron;

    @Autowired
    private SessionRepository sessionRepository;

    @PostConstruct
    public void init() {
        log.info("MONGO_SESSION_INIT | applicationName={} | expireTime={}s | cleanupCron={}",
                applicationName, sessionExpireTime, cleanupCron);
    }

    /**
     * 获取Session
     *
     * @param sessionId 会话ID
     * @return Session对象，如果不存在返回null
     */
    public Session getSession(String sessionId) {
        if (sessionId == null) {
            log.warn("GET_SESSION_FAILED | sessionId is null");
            return null;
        }

        try {
            Session session = sessionRepository.findById(sessionId).orElse(null);
            if (session != null) {
                // 更新最后访问时间并持久化
                session.setLastAccessed(LocalDateTime.now());
                sessionRepository.save(session);
                log.debug("SESSION_RETRIEVED | sessionId={} | memorySize={}",
                        sessionId, session.getMemory() != null ? session.getMemory().size() : 0);
            } else {
                log.debug("SESSION_NOT_FOUND | sessionId={}", sessionId);
            }
            return session;
        } catch (Exception e) {
            log.error("SESSION_RETRIEVAL_FAILED | sessionId={} | error={}", sessionId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 保存Session
     *
     * @param session Session对象
     */
    public void saveSession(Session session) {
        if (session == null || session.getSessionId() == null || session.getUserId() == null) {
            log.warn("SAVE_SESSION_FAILED | session is null or sessionId is null or userId is null");
            return;
        }

        try {
            // 更新最后访问时间
            if (session.getLastAccessed() == null) {
                session.setLastAccessed(LocalDateTime.now());
            }

            sessionRepository.save(session);

            log.info("SESSION_SAVED | sessionId={} | memoryEntries={} | paperVersions={} | state={}",
                    session.getSessionId(),
                    session.getMemory() != null ? session.getMemory().size() : 0,
                    session.getPaperVersions() != null ? session.getPaperVersions().size() : 0,
                    session.getAnalysisState());
        } catch (Exception e) {
            log.error("SESSION_SAVE_FAILED | sessionId={} | error={}", session.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * 获取用户的所有会话
     *
     * @param userId 用户ID
     * @return 用户的会话列表
     */
    public List<Session> getUserSessions(String userId) {
        if (userId == null) {
            log.warn("GET_USER_SESSIONS_FAILED | userId is null");
            return new ArrayList<>();
        }

        try {
            List<Session> sessions = sessionRepository.findByUserId(userId);
            if (sessions == null) {
                sessions = new ArrayList<>();
            }
            log.debug("GET_USER_SESSIONS | userId={} | count={}", userId, sessions.size());
            return sessions;
        } catch (Exception e) {
            log.error("GET_USER_SESSIONS_FAILED | userId={} | error={}", userId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 删除Session
     *
     * @param sessionId 会话ID
     */
    public void deleteSession(String sessionId) {
        if (sessionId == null) {
            log.warn("DELETE_SESSION_FAILED | sessionId is null");
            return;
        }

        try {
            if (sessionRepository.existsById(sessionId)) {
                sessionRepository.deleteById(sessionId);
                log.info("SESSION_DELETED | sessionId={}", sessionId);
            } else {
                log.warn("SESSION_DELETE_FAILED | sessionId={} not found", sessionId);
            }
        } catch (Exception e) {
            log.error("DELETE_SESSION_FAILED | sessionId={} | error={}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 获取所有Session数量
     *
     * @return Session总数
     */
    public int getSessionCount() {
        try {
            long count = sessionRepository.count();
            return (int) count;
        } catch (Exception e) {
            log.error("GET_SESSION_COUNT_FAILED | error={}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 清空所有Session（慎用）
     */
    public void clearAll() {
        try {
            long count = sessionRepository.count();
            sessionRepository.deleteAll();
            log.warn("ALL_SESSIONS_CLEARED | count={}", count);
        } catch (Exception e) {
            log.error("CLEAR_ALL_SESSIONS_FAILED | error={}", e.getMessage(), e);
        }
    }

    /**
     * 定时清理过期Session（基于lastAccessed和过期时间）
     */
    @Scheduled(cron = "${session.storage.redis.cleanup-cron:0 0 * * * ?}")
    public void cleanupExpiredSessions() {
        try {
            LocalDateTime expireBefore = LocalDateTime.now().minusSeconds(sessionExpireTime);
            List<Session> expired = sessionRepository.findByLastAccessedBefore(expireBefore);
            if (expired == null || expired.isEmpty()) {
                return;
            }
            sessionRepository.deleteAll(expired);
            log.info("CLEANUP_EXPIRED_SESSIONS_COMPLETED | cleanedCount={}", expired.size());
        } catch (Exception e) {
            log.error("CLEANUP_EXPIRED_SESSIONS_FAILED | error={}", e.getMessage(), e);
        }
    }

    /**
     * 获取所有会话的统计信息
     *
     * @return 统计信息Map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();

        try {
            List<Session> sessions = sessionRepository.findAll();
            int totalSessions = sessions.size();
            stats.put("totalSessions", totalSessions);

            long sessionsWithMemory = 0;
            long sessionsWithReports = 0;
            long totalMemoryEntries = 0;
            long totalReportVersions = 0;

            for (Session session : sessions) {
                if (session.getMemory() != null && !session.getMemory().isEmpty()) {
                    sessionsWithMemory++;
                    totalMemoryEntries += session.getMemory().size();
                }

                if (session.getPaperVersions() != null && !session.getPaperVersions().isEmpty()) {
                    sessionsWithReports++;
                    totalReportVersions += session.getPaperVersions().size();
                }
            }

            stats.put("sessionsWithMemory", sessionsWithMemory);
            stats.put("sessionsWithReports", sessionsWithReports);
            stats.put("totalMemoryEntries", totalMemoryEntries);
            stats.put("totalReportVersions", totalReportVersions);

            log.debug("SESSION_STATISTICS | {}", stats);
            return stats;
        } catch (Exception e) {
            log.error("GET_STATISTICS_FAILED | error={}", e.getMessage(), e);
            return stats;
        }
    }

    /**
     * 检查Session是否存在
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    public boolean existsSession(String sessionId) {
        if (sessionId == null) {
            log.warn("EXISTS_SESSION_FAILED | sessionId is null");
            return false;
        }

        try {
            return sessionRepository.existsById(sessionId);
        } catch (Exception e) {
            log.error("EXISTS_SESSION_FAILED | sessionId={} | error={}", sessionId, e.getMessage(), e);
            return false;
        }
    }
}

