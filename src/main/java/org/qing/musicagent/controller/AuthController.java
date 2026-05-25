package org.qing.musicagent.controller;

import org.qing.musicagent.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    // 注册接口
    @PostMapping("/register")
    public Map<String, Object> register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String email) {
        return userService.register(username, password, email);
    }

    // 登录接口
    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestParam String username,
            @RequestParam String password) {
        return userService.login(username, password);
    }

    // 获取当前登录用户信息
    @GetMapping("/me")
    public Map<String, Object> me() {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        return Map.of("username", username);
    }
}