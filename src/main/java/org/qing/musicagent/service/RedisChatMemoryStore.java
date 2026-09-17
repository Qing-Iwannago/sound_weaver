package org.qing.musicagent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private StringRedisTemplate redisTemplate; // Redis操作模板

    @Autowired
    private ObjectMapper objectMapper; // JSON序列化工具

    private static final String KEY_PREFIX = "chat_memory:"; // key前缀，避免和其他Redis key冲突
    private static final long EXPIRE_DAYS = 7; // 7天过期，自动清理不活跃用户

    // 生成Redis key，格式：chat_memory:用户名
    private String buildKey(Object memoryId) {
        return KEY_PREFIX + memoryId.toString();
    }

    // 读取对话历史，LangChain4j每次调用AI前自动执行
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = buildKey(memoryId);
        String json = redisTemplate.opsForValue().get(key);

        // key不存在或已过期，返回空列表，AI当成全新对话
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // JSON字符串反序列化成Map列表
            List<Map<String, String>> list = objectMapper.readValue(
                    json, new TypeReference<List<Map<String, String>>>() {});

            // 把Map转成LangChain4j的ChatMessage格式
            return list.stream().map(map -> {
                String role = map.get("role");
                String content = map.get("content");
                if ("user".equals(role)) {
                    return (ChatMessage) UserMessage.from(content);
                } else {
                    return (ChatMessage) AiMessage.from(content);
                }
            }).collect(Collectors.toList());

        } catch (Exception e) {
            return new ArrayList<>(); // 解析失败返回空列表，兜底
        }
    }

    // 保存对话历史，每次AI回复后LangChain4j自动执行
    @Autowired
    private ChatSummaryService chatSummaryService; // 注入摘要服务

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = buildKey(memoryId);

        try {
            //保存前先压缩
            List<ChatMessage> compressed = chatSummaryService.compressIfNeeded(messages);

            List<Map<String, String>> list = compressed.stream().map(message -> {
                Map<String, String> map = new HashMap<>();
                map.put("role", message.type() == ChatMessageType.USER ? "user" : "assistant");
                map.put("content", message.type() == ChatMessageType.USER
                        ? ((UserMessage) message).singleText()
                        : ((AiMessage) message).text());
                return map;
            }).collect(Collectors.toList());
            //七天过期
            String json = objectMapper.writeValueAsString(list);
            redisTemplate.opsForValue().set(key, json, EXPIRE_DAYS, TimeUnit.DAYS);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 删除对话历史
    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(buildKey(memoryId));
    }
}