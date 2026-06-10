package org.qing.musicagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.qing.musicagent.model.MusicHistory;
import org.qing.musicagent.model.MusicParams;
import org.qing.musicagent.repository.MusicHistoryRepository;
import org.qing.musicagent.service.MusicAgent;
import org.qing.musicagent.service.MidiService;
import org.qing.musicagent.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/music")
public class MusicController {

    @Autowired private MusicAgent musicAgent;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MidiService midiService;
    @Autowired private MusicHistoryRepository historyRepository;
    @Autowired private RagService ragService;

    // 获取当前登录用户名
    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // 生成音乐：RAG检索乐理知识注入提示词，AI生成参数，MidiService生成MIDI文件
    @PostMapping("/create")
    public Map<String, Object> createMusic(@RequestParam String description) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = getCurrentUsername();

            // RAG检索相关乐理知识
            String knowledge = ragService.retrieve(description);
            String enhancedDescription = description;
            if (!knowledge.isEmpty()) {
                enhancedDescription = description + "\n\n参考以下乐理知识：\n" + knowledge;
            }

            // AI生成音乐参数
            String raw = musicAgent.createMusic(userId, enhancedDescription);
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start == -1 || end == -1) throw new RuntimeException("AI返回格式异常");
            String json = raw.substring(start, end + 1);

            MusicParams params = objectMapper.readValue(json, MusicParams.class);
            String filePath = midiService.generateMidi(params);

            // 保存生成历史
            MusicHistory history = new MusicHistory();
            history.setUsername(userId);
            history.setDescription(description);
            history.setMood(params.getMood());
            history.setGenre(params.getGenre());
            history.setBpm(params.getBpm());
            history.setKey(params.getKey());
            history.setChords(params.getChords());
            history.setLyrics(params.getLyrics());
            history.setFilePath(filePath);
            historyRepository.save(history);

            result.put("success", true);
            result.put("params", params);
            result.put("historyId", history.getId());
            result.put("downloadUrl", "/music/download?path=" + filePath);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // AI对话，支持工具调用
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestParam String message) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = getCurrentUsername();
            String response = musicAgent.chat(userId, message);
            result.put("success", true);
            result.put("response", response);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // 查询当前用户的历史记录
    @GetMapping("/history")
    public List<MusicHistory> getHistory() {
        return historyRepository.findByUsernameOrderByCreatedAtDesc(getCurrentUsername());
    }

    // 下载MIDI文件
    @GetMapping("/download")
    public ResponseEntity<FileSystemResource> download(@RequestParam String path) {
        File file = new File(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }
}