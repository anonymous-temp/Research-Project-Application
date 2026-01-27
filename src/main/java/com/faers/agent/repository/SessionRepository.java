package com.faers.agent.repository;

import com.faers.agent.model.Session;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MongoDB 会话存储仓库
 */
public interface SessionRepository extends MongoRepository<Session, String> {

    /**
     * 按用户ID查询会话列表
     */
    List<Session> findByUserId(String userId);

    /**
     * 查询在某个时间点之前最后访问的会话，用于过期清理
     */
    List<Session> findByLastAccessedBefore(LocalDateTime time);
}



