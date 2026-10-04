package org.qing.musicagent.model;

import lombok.Data;

@Data
public class MusicParams {

    private String mood;        // 情绪：忧郁、欢快、平静
    private String genre;       // 风格：流行、古典、爵士
    private int bpm;            // 速度：60-180
    private String key;         // 调式：C Major、A Minor

    private int capo;           // 吉他变调夹位置：0 表示不使用

    private String instrument;  // 主乐器：钢琴、木吉他、电吉他等

    private String chords;      // 和弦进行：Am - F - C - G
    private String lyrics;      // 完整歌词，包含主歌/副歌和逐句和弦
}