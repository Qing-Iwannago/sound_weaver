package org.qing.musicagent.controller;

import org.qing.musicagent.model.User;
import org.qing.musicagent.repository.UserRepository;
import org.qing.musicagent.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestParam String username, @RequestParam String password) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (userRepository.existsByUsername(username)) {
                result.put("success", false);
                result.put("error", "用户名已存在");
                return result;
            }
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            userRepository.save(user);
            String token = jwtService.generateToken(username);
            result.put("success", true);
            result.put("token", token);
            result.put("username", username);
            result.put("remaining", 30);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "注册失败，请重试");
        }
        return result;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam String username, @RequestParam String password) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
                result.put("success", false);
                result.put("error", "用户名或密码错误");
                return result;
            }
            String token = jwtService.generateToken(username);
            result.put("success", true);
            result.put("token", token);
            result.put("username", username);
            result.put("remaining", 30);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "登录失败，请重试");
        }
        return result;
    }
}
