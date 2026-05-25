package org.qing.musicagent.service;

import org.qing.musicagent.model.MusicParams;
import org.springframework.stereotype.Service;


import javax.sound.midi.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MidiService {

    // ============================================================
    // 调式音阶表
    // 每个调式都是一组音符编号，代表该调式的7个音
    // 比如C大调：Do Re Mi Fa Sol La Si = 60 62 64 65 67 69 71
    // ============================================================
    private static final Map<String, int[]> KEY_SCALES = new HashMap<>();

    static {
        // 大调（明亮、开朗）
        KEY_SCALES.put("C大调", new int[]{60, 62, 64, 65, 67, 69, 71});
        KEY_SCALES.put("G大调", new int[]{67, 69, 71, 72, 74, 76, 78});
        KEY_SCALES.put("D大调", new int[]{62, 64, 66, 67, 69, 71, 73});
        KEY_SCALES.put("F大调", new int[]{65, 67, 69, 70, 72, 74, 76});
        KEY_SCALES.put("A大调", new int[]{69, 71, 73, 74, 76, 78, 80});
        KEY_SCALES.put("E大调", new int[]{64, 66, 68, 69, 71, 73, 75});
        KEY_SCALES.put("B大调", new int[]{71, 73, 75, 76, 78, 80, 82});

        // 小调（忧郁、深沉）
        KEY_SCALES.put("A小调", new int[]{69, 71, 72, 74, 76, 77, 79});
        KEY_SCALES.put("E小调", new int[]{64, 66, 67, 69, 71, 72, 74});
        KEY_SCALES.put("D小调", new int[]{62, 64, 65, 67, 69, 70, 72});
        KEY_SCALES.put("C小调", new int[]{60, 62, 63, 65, 67, 68, 70});
        KEY_SCALES.put("B小调", new int[]{71, 73, 74, 76, 78, 79, 81});
        KEY_SCALES.put("G小调", new int[]{67, 69, 70, 72, 74, 75, 77});
    }

    // ============================================================
    // 和弦音符表
    // 每个和弦由3个音组成（根音、三音、五音）
    // 比如C和弦 = C+E+G = 60+64+67
    // ============================================================
    private static final Map<String, int[]> CHORD_NOTES = new HashMap<>();

    static {
        // 大三和弦（明亮）
        CHORD_NOTES.put("C",  new int[]{48, 52, 55});
        CHORD_NOTES.put("G",  new int[]{43, 47, 50});
        CHORD_NOTES.put("D",  new int[]{50, 54, 57});
        CHORD_NOTES.put("F",  new int[]{53, 57, 60});
        CHORD_NOTES.put("A",  new int[]{45, 49, 52});
        CHORD_NOTES.put("E",  new int[]{52, 56, 59});
        CHORD_NOTES.put("B",  new int[]{47, 51, 54});

        // 小三和弦（忧郁）
        CHORD_NOTES.put("Am", new int[]{45, 48, 52});
        CHORD_NOTES.put("Em", new int[]{40, 43, 47});
        CHORD_NOTES.put("Dm", new int[]{50, 53, 57});
        CHORD_NOTES.put("Cm", new int[]{48, 51, 55});
        CHORD_NOTES.put("Bm", new int[]{47, 50, 54});
        CHORD_NOTES.put("Gm", new int[]{43, 46, 50});
    }

    // ============================================================
    // 乐器音色表（MIDI Program Number 0-127）
    // 根据genre选择合适的乐器
    // ============================================================
    private static final Map<String, Integer> GENRE_INSTRUMENTS = new HashMap<>();

    static {
        GENRE_INSTRUMENTS.put("流行",   0);   // 钢琴
        GENRE_INSTRUMENTS.put("古典",   48);  // 弦乐合奏
        GENRE_INSTRUMENTS.put("爵士",   66);  // 萨克斯
        GENRE_INSTRUMENTS.put("摇滚",   29);  // 电吉他
        GENRE_INSTRUMENTS.put("民谣",   25);  // 原声吉他
        GENRE_INSTRUMENTS.put("电子",   81);  // 合成器
        GENRE_INSTRUMENTS.put("R&B",    33);  // 电贝斯
        GENRE_INSTRUMENTS.put("嘻哈",   0);   // 钢琴
        GENRE_INSTRUMENTS.put("默认",   0);   // 钢琴
    }

    // ============================================================
    // 主方法：根据AI返回的MusicParams生成MIDI文件
    // ============================================================
    public String generateMidi(MusicParams params) throws Exception {

        // 每拍48个tick，比之前的24更细腻
        Sequence sequence = new Sequence(Sequence.PPQ, 48);

        // 设置速度
        int bpm = params.getBpm() > 0 ? params.getBpm() : 80;
        long mpq = 60000000L / bpm;
        setTempo(sequence, mpq);

        // 获取调式音阶，找不到就默认C大调
        int[] scale = getScale(params.getKey());

        // 获取力度（根据mood决定轻重）
        int velocity = getVelocity(params.getMood());

        // 获取乐器（根据genre决定音色）
        int instrument = getInstrument(params.getGenre());

        // 解析和弦进行
        List<String> chordList = parseChords(params.getChords());

        // 生成旋律轨道（第0轨）
        Track melodyTrack = sequence.createTrack();
        generateMelody(melodyTrack, scale, bpm, velocity, instrument, chordList,params);

        // 生成和弦伴奏轨道（第1轨）
        Track chordTrack = sequence.createTrack();
        generateChords(chordTrack, chordList, velocity);

        // 保存文件
        String fileName = "music_" + System.currentTimeMillis() + ".mid";
        String filePath = "output/" + fileName;
        new File("output").mkdirs();
        MidiSystem.write(sequence, 1, new File(filePath));

        return filePath;
    }

    // ============================================================
    // 设置速度
    // ============================================================
    private void setTempo(Sequence sequence, long mpq) throws Exception {
        Track track = sequence.createTrack();
        MetaMessage tempoMsg = new MetaMessage();
        byte[] bt = {(byte)(mpq >> 16), (byte)(mpq >> 8), (byte)(mpq)};
        tempoMsg.setMessage(0x51, bt, 3);
        track.add(new MidiEvent(tempoMsg, 0));
    }

    // ============================================================
    // 获取调式音阶
    // 先精确匹配，匹配不到就模糊匹配，最后默认C大调
    // ============================================================
    private int[] getScale(String key) {
        if (key == null) return KEY_SCALES.get("C大调");

        // 精确匹配
        if (KEY_SCALES.containsKey(key)) {
            return KEY_SCALES.get(key);
        }

        // 模糊匹配（AI可能返回"C大调（明亮）"这种带括号的）
        for (Map.Entry<String, int[]> entry : KEY_SCALES.entrySet()) {
            if (key.contains(entry.getKey()) || entry.getKey().contains(key)) {
                return entry.getValue();
            }
        }

        // 判断是否含"小调"关键词，默认用A小调
        if (key.contains("小调")) return KEY_SCALES.get("A小调");

        return KEY_SCALES.get("C大调");
    }

    // ============================================================
    // 根据mood获取力度
    // 忧郁=轻柔，欢快=响亮，平静=中等
    // ============================================================
    private int getVelocity(String mood) {
        if (mood == null) return 70;
        if (mood.contains("忧郁") || mood.contains("悲伤") || mood.contains("低沉")) return 50;
        if (mood.contains("欢快") || mood.contains("激动") || mood.contains("兴奋")) return 95;
        if (mood.contains("平静") || mood.contains("舒缓") || mood.contains("温柔")) return 60;
        if (mood.contains("紧张") || mood.contains("激烈")) return 100;
        return 70;
    }

    // ============================================================
    // 根据genre获取乐器编号
    // ============================================================
    private int getInstrument(String genre) {
        if (genre == null) return 0;
        for (Map.Entry<String, Integer> entry : GENRE_INSTRUMENTS.entrySet()) {
            if (genre.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 0; // 默认钢琴
    }

    // ============================================================
    // 解析和弦字符串
    // 输入："Am-F-C-G" 或 "Am - F - C - G"
    // 输出：["Am", "F", "C", "G"]
    // ============================================================
    private List<String> parseChords(String chords) {
        List<String> result = new ArrayList<>();
        if (chords == null || chords.isEmpty()) {
            result.add("C"); result.add("G");
            result.add("Am"); result.add("F");
            return result;
        }

        // 用-或空格分割，清理空白
        String[] parts = chords.split("[-–—]");
        for (String part : parts) {
            String chord = part.trim()
                    .replaceAll("\\(.*?\\)", "") // 去掉括号内容
                    .replaceAll("[^A-Za-z#b]", "") // 只保留和弦名称
                    .trim();
            if (!chord.isEmpty()) {
                result.add(chord);
            }
        }

        if (result.isEmpty()) {
            result.add("C"); result.add("G");
            result.add("Am"); result.add("F");
        }
        return result;
    }

    // ============================================================
    // 生成旋律轨道
    // 根据音阶生成旋律，每个和弦段落用对应音阶的音符
    // ============================================================
    private void generateMelody(Track track, int[] scale, int bpm,
                                int velocity, int instrument, List<String> chords,MusicParams params) throws Exception {

        // 设置乐器音色
        ShortMessage programChange = new ShortMessage();
        programChange.setMessage(ShortMessage.PROGRAM_CHANGE, 0, instrument, 0);
        track.add(new MidiEvent(programChange, 0));

        int ticksPerBeat = 48;
        int tick = 0;

// 定义几种不同的旋律节奏型，随机选
        int[][] patterns = {
                {0, 2, 4, 6, 4, 2, 0, 2},   // 上行再下行
                {4, 2, 0, 2, 4, 5, 4, 2},   // 从高到低
                {0, 0, 2, 4, 2, 4, 6, 4},   // 跳跃型
                {6, 4, 2, 0, 2, 4, 2, 0},   // 下行
                {0, 2, 4, 2, 0, 4, 2, 6},   // 混合
        };

// 根据mood选择旋律风格
        int patternIndex;
        if (params.getMood() != null && params.getMood().contains("忧郁")) {
            patternIndex = 3; // 忧郁用下行旋律
        } else if (params.getMood() != null && params.getMood().contains("欢快")) {
            patternIndex = 2; // 欢快用跳跃型
        } else {
            patternIndex = (int)(Math.random() * patterns.length); // 其他随机
        }

        int[] selectedPattern = patterns[patternIndex];

        for (int loop = 0; loop < 2; loop++) {
            for (int ci = 0; ci < chords.size(); ci++) {
                for (int i = 0; i < selectedPattern.length / 2; i++) {
                    int scaleIndex = selectedPattern[i] % scale.length;
                    int note = scale[scaleIndex];

                    // 第二遍升高八度
                    if (loop == 1) note += 12;

                    // 力度随机波动
                    int v = velocity + (int)(Math.random() * 15 - 7);
                    v = Math.max(30, Math.min(120, v));

                    // 节奏变化：偶尔用半拍
                    int duration = (i % 3 == 2) ? ticksPerBeat / 2 : ticksPerBeat;

                    addNote(track, 0, note, v, tick, duration);
                    tick += duration;
                }
            }
        }
    }

    // ============================================================
    // 生成和弦伴奏轨道
    // 每个和弦持续4拍，低音区演奏
    // ============================================================
    private void generateChords(Track track, List<String> chords, int velocity) throws Exception {

        // 伴奏用钢琴，通道1
        ShortMessage programChange = new ShortMessage();
        programChange.setMessage(ShortMessage.PROGRAM_CHANGE, 1, 0, 0);
        track.add(new MidiEvent(programChange, 0));

        int ticksPerBeat = 48;
        int beatsPerChord = 4; // 每个和弦持续4拍
        int tick = 0;
        int chordVelocity = (int)(velocity * 0.7); // 伴奏比旋律轻一点

        for (int loop = 0; loop < 2; loop++) {
            for (String chord : chords) {
                int[] notes = CHORD_NOTES.getOrDefault(chord, CHORD_NOTES.get("C"));
                int duration = ticksPerBeat * beatsPerChord;

                // 三个音同时响（和弦）
                for (int note : notes) {
                    addNote(track, 1, note, chordVelocity, tick, duration);
                }
                tick += duration;
            }
        }
    }

    // ============================================================
    // 工具方法：添加一个音符事件
    // channel: MIDI通道（0=旋律，1=伴奏，9=鼓）
    // note: 音符编号
    // velocity: 力度
    // startTick: 开始时间
    // durationTick: 持续时间
    // ============================================================
    private void addNote(Track track, int channel, int note,
                         int velocity, int startTick, int durationTick) throws Exception {
        ShortMessage on = new ShortMessage();
        on.setMessage(ShortMessage.NOTE_ON, channel, note, velocity);
        track.add(new MidiEvent(on, startTick));

        ShortMessage off = new ShortMessage();
        off.setMessage(ShortMessage.NOTE_OFF, channel, note, 0);
        track.add(new MidiEvent(off, startTick + durationTick));
    }
}