# 声织 · 后端改动文件说明

## 替换到IDEA对应位置的文件

| 文件 | 说明 |
|------|------|
| config/SecurityConfig.java | 放行游客接口，登录接口JWT鉴权 |
| config/AgentConfig.java | 内存存储，不用Redis，按userId隔离对话 |
| config/JwtFilter.java | JWT过滤器 |
| config/CorsConfig.java | 跨域配置 |
| controller/MusicController.java | 游客8次/登录用户30次，接口分开 |
| controller/AuthController.java | 登录注册，返回remaining字段 |
| service/JwtService.java | JWT生成和验证 |
| model/User.java | 用户实体类 |
| repository/UserRepository.java | 用户数据库操作 |

## 前端文件
把 index_final.html 放到 src/main/resources/static/ 目录

## 接口说明
- 游客生成：POST /music/create/guest（不需要Token，每天8次）
- 登录生成：POST /music/create（需要Bearer Token，每天30次）
- 游客对话：POST /music/chat/guest
- 登录对话：POST /music/chat（需要Token）
- 登录：POST /auth/login
- 注册：POST /auth/register

## 不需要Redis
对话历史存在内存，重启清空，演示够用
