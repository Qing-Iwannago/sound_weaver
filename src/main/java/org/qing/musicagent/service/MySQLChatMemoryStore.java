package org.qing.musicagent.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.qing.musicagent.model.ChatMemoryEntity;
import org.qing.musicagent.repository.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

// @Component 让Spring管理这个类，可以被@Autowired注入
// 实现ChatMemoryStore接口，LangChain4j会自动调用这三个方法管理对话历史
@Component
public class MySQLChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private ChatMemoryRepository chatMemoryRepository;

    // ============================================================
    // 读取对话历史
    // 每次调用AI之前，LangChain4j自动调用这个方法
    // 根据userId从MySQL查出该用户的历史消息，转成LangChain4j的ChatMessage格式
    // ============================================================
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // memoryId就是我们传入的userId，比如用户名test
        String userId = memoryId.toString();

        // 按时间正序查出该用户所有对话记录
        List<ChatMemoryEntity> entities =
                chatMemoryRepository.findByUserIdOrderByCreatedAtAsc(userId);

        // 把数据库实体转成LangChain4j的消息格式
        // role=user -> UserMessage
        // role=assistant -> AiMessage
        return entities.stream().map(entity -> {
            if ("user".equals(entity.getRole())) {
                return (ChatMessage) UserMessage.from(entity.getContent());
            } else {
                return (ChatMessage) AiMessage.from(entity.getContent());
            }
        }).collect(Collectors.toList());
    }

    // ============================================================
    // 保存对话历史
    // 每次AI回复之后，LangChain4j自动调用这个方法
    // 把完整的对话历史（包括新消息）保存到MySQL
    // ============================================================
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String userId = memoryId.toString();

        // 先删除该用户的旧记录
        // 为什么要先删？因为LangChain4j传来的是完整的历史列表
        // 直接覆盖比增量更新更简单可靠
        chatMemoryRepository.deleteByUserId(userId);

        // 把新的完整对话历史存入MySQL
        List<ChatMemoryEntity> entities = messages.stream().map(message -> {
            ChatMemoryEntity entity = new ChatMemoryEntity();
            entity.setUserId(userId);
            // 判断消息类型，存user或assistant
            entity.setRole(message.type() == ChatMessageType.USER ? "user" : "assistant");
            // 取出消息文本内容
            entity.setContent(message.type() == ChatMessageType.USER
                    ? ((UserMessage) message).singleText()
                    : ((AiMessage) message).text());
            return entity;
        }).collect(Collectors.toList());

        // 批量保存，比循环save效率更高
        chatMemoryRepository.saveAll(entities);
    }

    // ============================================================
    // 删除对话历史
    // 用户退出或者手动清空对话时调用
    // ============================================================
    @Override
    public void deleteMessages(Object memoryId) {
        chatMemoryRepository.deleteByUserId(memoryId.toString());
    }
}