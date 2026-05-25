package org.qing.musicagent.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    // 签名密钥，用来生成和验证Token
    // 实际项目应该放在配置文件里，这里为了简单直接写死
    private static final String SECRET = "music-agent-secret-key-2024-very-long-string";

    // Token有效期：7天（毫秒）
    private static final long EXPIRATION = 7 * 24 * 60 * 60 * 1000L;

    // 根据密钥字符串生成Key对象
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    // 生成Token
    // 把用户名存入Token的Subject字段

    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)           // 存用户名
                .setIssuedAt(new Date())         // 签发时间
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION)) // 过期时间
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // 用HS256算法签名
                .compact();
    }


    // 从Token中提取用户名

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }


    // 验证Token是否有效

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            // 检查Token是否过期
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            // Token格式错误或签名不匹配
            return false;
        }
    }

    // 解析Token，提取载荷（Claims）

    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}