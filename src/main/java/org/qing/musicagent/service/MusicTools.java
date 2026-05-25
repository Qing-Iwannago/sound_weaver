package org.qing.musicagent.service;

import dev.langchain4j.agent.tool.Tool;
import org.qing.musicagent.model.MusicHistory;
import org.qing.musicagent.model.MusicParams;
import org.qing.musicagent.repository.MusicHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MusicTools {

    @Autowired
    private MusicHistoryRepository historyRepository;

    @Autowired
    private MidiService midiService;

    // ============================================================
    // Tool 1：查询最近5条历史记录
    // AI可以调用这个了解用户的音乐偏好
    // ============================================================
    @Tool("查询用户最近生成的音乐历史记录，返回最近5条")
    public String getRecentHistory() {
        List<MusicHistory> list = historyRepository.findTop5ByOrderByCreatedAtDesc();
        if (list.isEmpty()) return "暂无历史记录";

        return list.stream().map(h ->
                String.format("ID:%d | 描述:%s | 风格:%s | 情绪:%s | 调式:%s | 时间:%s",
                        h.getId(), h.getDescription(), h.getGenre(),
                        h.getMood(), h.getKey(), h.getCreatedAt())
        ).collect(Collectors.joining("\n"));
    }

    // ============================================================
    // Tool 2：根据ID查询某条历史的歌词
    // ============================================================
    @Tool("根据历史记录ID查询对应的歌词")
    public String getLyricsById(Long id) {
        return historyRepository.findById(id)
                .map(h -> "歌词：\n" + h.getLyrics())
                .orElse("找不到该记录");
    }

    // ============================================================
    // Tool 3：分析用户最喜欢的风格
    // ============================================================
    @Tool("分析用户历史生成记录，总结最常用的音乐风格和情绪偏好")
    public String analyzeUserPreference() {
        List<MusicHistory> all = historyRepository.findAll();
        if (all.isEmpty()) return "暂无数据，无法分析偏好";

        // 统计genre出现次数
        String genreSummary = all.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getGenre() != null ? h.getGenre() : "未知",
                        Collectors.counting()
                ))
                .entrySet().stream()
                .map(e -> e.getKey() + "(" + e.getValue() + "次)")
                .collect(Collectors.joining(", "));

        // 统计mood出现次数
        String moodSummary = all.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getMood() != null ? h.getMood() : "未知",
                        Collectors.counting()
                ))
                .entrySet().stream()
                .map(e -> e.getKey() + "(" + e.getValue() + "次)")
                .collect(Collectors.joining(", "));

        return String.format("共生成%d首，风格分布：%s；情绪分布：%s",
                all.size(), genreSummary, moodSummary);
    }

    // ============================================================
    // Tool 4：生成MIDI文件
    // AI可以直接调用这个工具生成音乐
    // ============================================================
    @Tool("根据音乐参数生成MIDI文件，返回下载路径")
    public String generateMidiFile(String mood, String genre,
                                   int bpm, String key,
                                   String chords, String lyrics) {
        try {
            MusicParams params = new MusicParams();
            params.setMood(mood);
            params.setGenre(genre);
            params.setBpm(bpm);
            params.setKey(key);
            params.setChords(chords);
            params.setLyrics(lyrics);

            String filePath = midiService.generateMidi(params);
            return "MIDI文件生成成功，下载路径：/music/download?path=" + filePath;
        } catch (Exception e) {
            return "生成失败：" + e.getMessage();
        }
    }
}