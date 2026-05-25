package org.qing.musicagent.repository;

import org.qing.musicagent.model.MusicHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MusicHistoryRepository extends JpaRepository<MusicHistory, Long> {

    // 按创建时间倒序查全部记录
    List<MusicHistory> findAllByOrderByCreatedAtDesc();
    List<MusicHistory> findTop5ByOrderByCreatedAtDesc();
    List<MusicHistory> findByUsernameOrderByCreatedAtDesc(String username);
}