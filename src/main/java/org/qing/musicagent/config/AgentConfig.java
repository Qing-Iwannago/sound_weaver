package org.qing.musicagent.config;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.qing.musicagent.service.MusicAgent;
import org.qing.musicagent.service.MusicTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public MusicAgent musicAgent(OpenAiChatModel chatModel, MusicTools musicTools) {
        return AiServices.builder(MusicAgent.class)
                .chatLanguageModel(chatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(30)
                                .build()
                )
                .tools(musicTools)
                .build();
    }
}
