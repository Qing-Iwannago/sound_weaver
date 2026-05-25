package org.qing.musicagent.controller;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    private OpenAiChatModel chatModel;

    @GetMapping("/test")
    public String test(@RequestParam String message) {
        String response = chatModel.generate(message);
        return response;
    }
}