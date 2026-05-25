package org.qing.musicagent.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

@Data
public class MusicParams {
    private String mood;        // 情绪：忧郁、欢快、平静
    private String genre;       // 风格：流行、古典、爵士
    private int bpm;            // 速度：60-180S
    private String key;         // 调式：C大调、A小调
    private String chords;      // 和弦进行：Am-F-C-G
    private String lyrics;      // 生成的歌词
}