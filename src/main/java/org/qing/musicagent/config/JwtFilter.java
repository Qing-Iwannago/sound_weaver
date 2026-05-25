package org.qing.musicagent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.qing.musicagent.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    // ============================================================
    // 每个请求都会经过这个过滤器
    // 检查请求头里有没有合法的Token
    // ============================================================
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 从请求头获取Authorization字段
        // 格式：Authorization: Bearer eyJhbGci...
        String authHeader = request.getHeader("Authorization");

        // 没有Token或格式不对，直接放行（Security会自己拦截需要认证的接口）
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 截取掉"Bearer "前缀，拿到真正的Token
        String token = authHeader.substring(7);

        // 验证Token是否合法
        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 从Token里提取用户名
        String username = jwtService.extractUsername(token);

        // 把用户信息存入Security上下文
        // 这样后续代码可以通过SecurityContextHolder获取当前登录用户
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        username, null, new ArrayList<>());
        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 放行
        filterChain.doFilter(request, response);
    }
}