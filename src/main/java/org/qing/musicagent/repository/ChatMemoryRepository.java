package org.qing.musicagent.repository;

import org.qing.musicagent.model.ChatMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMemoryRepository extends JpaRepository<ChatMemoryEntity, Long> {

    // 按用户ID查询所有对话历史，按时间正序
    List<ChatMemoryEntity> findByUserIdOrderByCreatedAtAsc(String userId);

    // 删除某个用户的所有对话历史
    void deleteByUserId(String userId);
}