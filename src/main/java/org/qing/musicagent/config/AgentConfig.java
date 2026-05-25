package org.qing.musicagent.config;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.qing.musicagent.service.MusicAgent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.qing.musicagent.service.MusicTools;

@Configuration
public class AgentConfig {

    @Bean
    public MusicAgent musicAgent(OpenAiChatModel chatModel, MusicTools musicTools) {
        return AiServices.builder(MusicAgent.class)
                .chatLanguageModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(musicTools)
                .build();
    }
}