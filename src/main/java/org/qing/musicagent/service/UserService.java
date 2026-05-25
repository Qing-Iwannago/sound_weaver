package org.qing.musicagent.service;

import org.qing.musicagent.model.User;
import org.qing.musicagent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // ============================================================
    // 注册
    // ============================================================
    public Map<String, Object> register(String username, String password, String email) {
        Map<String, Object> result = new HashMap<>();

        // 检查用户名是否已存在
        if (userRepository.existsByUsername(username)) {
            result.put("success", false);
            result.put("message", "用户名已存在");
            return result;
        }

        // 检查邮箱是否已存在
        if (email != null && userRepository.existsByEmail(email)) {
            result.put("success", false);
            result.put("message", "邮箱已被注册");
            return result;
        }

        // 创建用户，密码用BCrypt加密
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password)); // 加密存储
        user.setEmail(email);
        userRepository.save(user);

        // 注册成功直接返回Token，免去再次登录
        String token = jwtService.generateToken(username);
        result.put("success", true);
        result.put("message", "注册成功");
        result.put("token", token);
        result.put("username", username);
        return result;
    }

    // ============================================================
    // 登录
    // ============================================================
    public Map<String, Object> login(String username, String password) {
        Map<String, Object> result = new HashMap<>();

        // 查找用户
        User user = userRepository.findByUsername(username)
                .orElse(null);

        if (user == null) {
            result.put("success", false);
            result.put("message", "用户名不存在");
            return result;
        }

        // 验证密码
        // passwordEncoder.matches：把用户输入的明文和数据库里的加密密码对比
        if (!passwordEncoder.matches(password, user.getPassword())) {
            result.put("success", false);
            result.put("message", "密码错误");
            return result;
        }

        // 登录成功，生成Token
        String token = jwtService.generateToken(username);
        result.put("success", true);
        result.put("message", "登录成功");
        result.put("token", token);
        result.put("username", username);
        result.put("userId", user.getId());
        return result;
    }
}