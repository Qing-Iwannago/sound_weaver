package org.qing.musicagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/music")
public class MusicController {

    @Autowired private MusicAgent musicAgent;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MidiService midiService;
    @Autowired private MusicHistoryRepository historyRepository;
    @Autowired private RagService ragService;

    // 游客次数限制 key=IP+日期
    private final ConcurrentHashMap<String, AtomicInteger> guestUsage = new ConcurrentHashMap<>();

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
        return ip + "_" + LocalDate.now();
    }

    @PostMapping("/create/guest")
    public Map<String, Object> createMusicGuest(@RequestParam String description, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String key = getClientIp(request);
            AtomicInteger count = guestUsage.computeIfAbsent(key, k -> new AtomicInteger(0));
            if (count.get() >= 8) {
                result.put("success", false);
                result.put("error", "今日游客次数已用完，请登录后继续使用");
                result.put("limitReached", true);
                return result;
            }
            count.incrementAndGet();
            String sessionId = "guest_" + request.getSession().getId();
            result = doGenerate(description, sessionId);
            result.put("remaining", 8 - count.get());
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/create")
    public Map<String, Object> createMusic(@RequestParam String description) {
        Map<String, Object> result = new HashMap<>();
        try {
            result = doGenerate(description, getCurrentUsername());
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private Map<String, Object> doGenerate(String description, String userId) throws Exception {
        Map<String, Object> result = new HashMap<>();
        String knowledge = ragService.retrieve(description);
        String enhancedDescription = knowledge.isEmpty() ? description
                : description + "\n\n参考以下乐理知识：\n" + knowledge;
        String raw = musicAgent.createMusic(userId, enhancedDescription);
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1) throw new RuntimeException("AI返回格式异常");
        String json = raw.substring(start, end + 1);
        MusicParams params = objectMapper.readValue(json, MusicParams.class);
        String filePath = midiService.generateMidi(params);
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
        return result;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestParam String message) {
        Map<String, Object> result = new HashMap<>();
        try {
            String response = musicAgent.chat(getCurrentUsername(), message);
            result.put("success", true);
            result.put("response", response);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/chat/guest")
    public Map<String, Object> chatGuest(@RequestParam String message, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = "guest_" + request.getSession().getId();
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

    @GetMapping("/history")
    public List<MusicHistory> getHistory() {
        return historyRepository.findByUsernameOrderByCreatedAtDesc(getCurrentUsername());
    }

    @GetMapping("/download")
    public ResponseEntity<FileSystemResource> download(@RequestParam String path) {
        File file = new File(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }
}
