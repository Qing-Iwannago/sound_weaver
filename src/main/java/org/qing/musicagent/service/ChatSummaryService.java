package org.qing.musicagent.service;


import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatSummaryService {

    @Autowired
    private OpenAiChatModel chatModel;

    // 触发压缩的阈值，超过30条开始压缩
    private static final int COMPRESS_THRESHOLD = 30;

    // 每次压缩前N条消息
    private static final int COMPRESS_COUNT = 20;

    // 检查是否需要压缩，超过阈值时压缩早期消息
    // ============================================================
    public List<ChatMessage> compressIfNeeded(List<ChatMessage> messages) {
        if (messages.size() < COMPRESS_THRESHOLD) {
            return messages; // 不需要压缩
        }

        // 取出前20条做摘要
        List<ChatMessage> toSummarize = messages.subList(0, COMPRESS_COUNT);
        // 保留后面的消息
        List<ChatMessage> remaining = messages.subList(COMPRESS_COUNT, messages.size());

        // 调用AI生成摘要
        String summary = summarize(toSummarize);

        // 用摘要替换前20条
        List<ChatMessage> compressed = new ArrayList<>();
        compressed.add(AiMessage.from("【对话摘要】" + summary)); // 摘要作为一条AI消息
        compressed.addAll(remaining);

        return compressed;
    }

    // 调用AI对历史消息做摘要
    // ============================================================
    private String summarize(List<ChatMessage> messages) {
        // 把历史消息转成文本
        String historyText = messages.stream().map(msg -> {
            if (msg instanceof UserMessage) {
                return "用户：" + ((UserMessage) msg).singleText();
            } else if (msg instanceof AiMessage) {
                return "助手：" + ((AiMessage) msg).text();
            }
            return "";
        }).collect(Collectors.joining("\n"));

        // 让AI总结
        String prompt = "请将以下对话历史总结成一段简洁的摘要，保留关键信息：\n\n" + historyText;

        try {
            return chatModel.generate(prompt);
        } catch (Exception e) {
            return "早期对话已压缩"; // 摘要失败时的兜底
        }
    }
}