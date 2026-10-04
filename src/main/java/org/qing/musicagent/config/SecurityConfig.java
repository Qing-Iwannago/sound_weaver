package org.qing.musicagent.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(
                        s -> s.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // 登录注册
                        .requestMatchers("/auth/**").permitAll()

                        // 游客生成
                        .requestMatchers("/music/create/guest").permitAll()

                        // 游客聊天
                        .requestMatchers("/music/chat/guest").permitAll()

                        // 游客修改后重新生成 MIDI
                        .requestMatchers("/music/regenerate/guest").permitAll()

                        // MIDI 下载 / 播放
                        .requestMatchers("/music/download").permitAll()


                        // 静态页面
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/index_final.html",
                                "/static/**"
                        ).permitAll()

                        // 其他 music 接口需要登录
                        .requestMatchers("/music/**").authenticated()

                        // 其他请求放行
                        .anyRequest().permitAll()
                )

                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}