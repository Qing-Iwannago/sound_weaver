package org.qing.musicagent.repository;

import org.qing.musicagent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // 根据用户名查用户，登录时用
    Optional<User> findByUsername(String username);

    // 检查用户名是否已存在，注册时用
    boolean existsByUsername(String username);

    // 检查邮箱是否已存在，注册时用
    boolean existsByEmail(String email);
}