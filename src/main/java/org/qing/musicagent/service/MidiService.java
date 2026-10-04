package org.qing.musicagent.service;

import org.qing.musicagent.model.MusicParams;
import org.springframework.stereotype.Service;

import javax.sound.midi.*;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MidiService {

    private static final int PPQ = 48;
    private static final int BAR = PPQ * 4;

    private static final int CH_MELODY = 0;
    private static final int CH_CHORD = 1;
    private static final int CH_BASS = 2;
    private static final int CH_DRUM = 9;

    private static final Map<String, Integer> NOTE_TO_SEMITONE = new HashMap<>();
    private static final Map<String, Integer> INSTRUMENT_PROGRAMS = new LinkedHashMap<>();
    private static final Map<String, Integer> GENRE_PROGRAMS = new LinkedHashMap<>();

    static {
        NOTE_TO_SEMITONE.put("C", 0);
        NOTE_TO_SEMITONE.put("C#", 1);
        NOTE_TO_SEMITONE.put("Db", 1);
        NOTE_TO_SEMITONE.put("D", 2);
        NOTE_TO_SEMITONE.put("D#", 3);
        NOTE_TO_SEMITONE.put("Eb", 3);
        NOTE_TO_SEMITONE.put("E", 4);
        NOTE_TO_SEMITONE.put("F", 5);
        NOTE_TO_SEMITONE.put("F#", 6);
        NOTE_TO_SEMITONE.put("Gb", 6);
        NOTE_TO_SEMITONE.put("G", 7);
        NOTE_TO_SEMITONE.put("G#", 8);
        NOTE_TO_SEMITONE.put("Ab", 8);
        NOTE_TO_SEMITONE.put("A", 9);
        NOTE_TO_SEMITONE.put("A#", 10);
        NOTE_TO_SEMITONE.put("Bb", 10);
        NOTE_TO_SEMITONE.put("B", 11);

        INSTRUMENT_PROGRAMS.put("钢琴", 0);
        INSTRUMENT_PROGRAMS.put("原声钢琴", 0);
        INSTRUMENT_PROGRAMS.put("尼龙吉他", 24);
        INSTRUMENT_PROGRAMS.put("木吉他", 25);
        INSTRUMENT_PROGRAMS.put("原声吉他", 25);
        INSTRUMENT_PROGRAMS.put("电吉他", 29);
        INSTRUMENT_PROGRAMS.put("贝斯", 33);
        INSTRUMENT_PROGRAMS.put("弦乐", 48);
        INSTRUMENT_PROGRAMS.put("小提琴", 40);
        INSTRUMENT_PROGRAMS.put("萨克斯", 65);
        INSTRUMENT_PROGRAMS.put("合成器", 81);

        GENRE_PROGRAMS.put("流行", 0);
        GENRE_PROGRAMS.put("民谣", 25);
        GENRE_PROGRAMS.put("摇滚", 29);
        GENRE_PROGRAMS.put("爵士", 0);
        GENRE_PROGRAMS.put("R&B", 4);
        GENRE_PROGRAMS.put("R＆B", 4);
        GENRE_PROGRAMS.put("电子", 81);
        GENRE_PROGRAMS.put("古典", 0);
        GENRE_PROGRAMS.put("嘻哈", 4);
    }

    public String generateMidi(MusicParams params) throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, PPQ);

        int bpm = normalizeBpm(params.getBpm());
        int mainProgram = getInstrumentProgram(params.getInstrument(), params.getGenre());
        int baseVelocity = getVelocity(params.getMood());
        int[] scale = getScale(params.getKey());
        List<String> progression = parseChords(params.getChords());
        List<SongSection> sections = parseSongSections(params.getLyrics());

        if (sections.isEmpty()) {
            sections = defaultSections();
        }

        setTempo(sequence, bpm);

        Track melodyTrack = sequence.createTrack();
        Track chordTrack = sequence.createTrack();
        Track bassTrack = sequence.createTrack();
        Track drumTrack = sequence.createTrack();

        setProgram(melodyTrack, CH_MELODY, mainProgram);
        setProgram(chordTrack, CH_CHORD, mainProgram);
        setProgram(bassTrack, CH_BASS, 33);

        Random random = new Random(buildSeed(params));

        System.out.println("🎵 MIDI v2 生成参数");
        System.out.println("Mood: " + params.getMood());
        System.out.println("Genre: " + params.getGenre());
        System.out.println("Instrument: " + params.getInstrument());
        System.out.println("Program: " + mainProgram);
        System.out.println("Key: " + params.getKey());
        System.out.println("BPM: " + bpm);
        System.out.println("Chords: " + progression);
        System.out.println("Sections: " + sections);

        int tick = 0;

        int introBars = 2;
        generateIntro(chordTrack, bassTrack, drumTrack, progression, params, baseVelocity, tick, introBars);
        tick += introBars * BAR;

        for (SongSection section : sections) {
            int bars = normalizeSectionBars(section);
            generateSection(
                    melodyTrack,
                    chordTrack,
                    bassTrack,
                    drumTrack,
                    scale,
                    progression,
                    params,
                    section,
                    baseVelocity,
                    tick,
                    bars,
                    random
            );
            tick += bars * BAR;
        }

        int outroBars = 2;
        generateOutro(chordTrack, bassTrack, drumTrack, progression, params, baseVelocity, tick, outroBars);
        tick += outroBars * BAR;

        addEndOfTrack(melodyTrack, tick + PPQ);
        addEndOfTrack(chordTrack, tick + PPQ);
        addEndOfTrack(bassTrack, tick + PPQ);
        addEndOfTrack(drumTrack, tick + PPQ);

        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String fileName = "music_" + System.currentTimeMillis() + ".mid";
        File outputFile = new File(outputDir, fileName);
        MidiSystem.write(sequence, 1, outputFile);

        System.out.println("✅ MIDI v2 生成完成: " + outputFile.getPath());
        return outputFile.getPath();
    }

    private List<SongSection> parseSongSections(String lyrics) {
        List<SongSection> sections = new ArrayList<>();

        if (lyrics == null || lyrics.isBlank()) {
            return sections;
        }

        String currentName = "Verse 1";
        int lyricLines = 0;

        String[] lines = lyrics.split("\\R");

        for (String raw : lines) {
            String line = raw.trim();

            if (line.isEmpty()) {
                continue;
            }

            if (isSectionTag(line)) {
                if (lyricLines > 0) {
                    sections.add(new SongSection(currentName, lyricLines));
                }

                currentName = line.substring(1, line.length() - 1).trim();
                lyricLines = 0;
                continue;
            }

            lyricLines++;
        }

        if (lyricLines > 0) {
            sections.add(new SongSection(currentName, lyricLines));
        }

        return sections;
    }

    private boolean isSectionTag(String line) {
        return line.matches("^\\[(Verse(?:\\s+\\d+)?|Pre-Chorus|Chorus|Bridge|Final Chorus|Intro|Outro)\\]$");
    }

    private List<SongSection> defaultSections() {
        return new ArrayList<>(List.of(
                new SongSection("Verse 1", 4),
                new SongSection("Pre-Chorus", 2),
                new SongSection("Chorus", 4),
                new SongSection("Verse 2", 4),
                new SongSection("Chorus", 4),
                new SongSection("Bridge", 4),
                new SongSection("Final Chorus", 4)
        ));
    }

    private int normalizeSectionBars(SongSection section) {
        int bars = section.lyricLines;

        if (section.type() == SectionType.PRE_CHORUS) {
            return clamp(bars, 2, 4);
        }

        if (section.type() == SectionType.BRIDGE) {
            return clamp(bars, 2, 6);
        }

        if (section.type() == SectionType.CHORUS ||
                section.type() == SectionType.FINAL_CHORUS) {
            return clamp(bars, 4, 8);
        }

        return clamp(bars, 4, 8);
    }

    private void generateSection(
            Track melodyTrack,
            Track chordTrack,
            Track bassTrack,
            Track drumTrack,
            int[] scale,
            List<String> progression,
            MusicParams params,
            SongSection section,
            int baseVelocity,
            int startTick,
            int bars,
            Random random
    ) throws Exception {

        SectionType type = section.type();
        int energy = energyFor(type);

        System.out.println("  ▶ " + section.name + " | bars=" + bars + " | energy=" + energy);

        for (int barIndex = 0; barIndex < bars; barIndex++) {
            int barTick = startTick + barIndex * BAR;
            String chord = progression.get(barIndex % progression.size());

            generateMelodyBar(
                    melodyTrack,
                    scale,
                    chord,
                    params,
                    type,
                    baseVelocity,
                    energy,
                    barTick,
                    random
            );

            generateAccompanimentBar(
                    chordTrack,
                    chord,
                    params,
                    type,
                    baseVelocity,
                    energy,
                    barTick
            );

            generateBassBar(
                    bassTrack,
                    chord,
                    type,
                    baseVelocity,
                    energy,
                    barTick
            );

            generateDrumBar(
                    drumTrack,
                    params,
                    type,
                    baseVelocity,
                    energy,
                    barTick,
                    barIndex
            );
        }
    }

    private int energyFor(SectionType type) {
        return switch (type) {
            case VERSE -> 2;
            case PRE_CHORUS -> 3;
            case CHORUS -> 4;
            case FINAL_CHORUS -> 5;
            case BRIDGE -> 3;
            case INTRO -> 1;
            case OUTRO -> 1;
        };
    }

    private void generateMelodyBar(
            Track track,
            int[] scale,
            String chord,
            MusicParams params,
            SectionType type,
            int baseVelocity,
            int energy,
            int barTick,
            Random random
    ) throws Exception {

        int[] chordNotes = chordToNotes(chord);
        String genre = safe(params.getGenre());

        int[] rhythm;

        if (genre.contains("R&B") || genre.contains("R＆B")) {
            rhythm = new int[]{PPQ, PPQ / 2, PPQ / 2, PPQ, PPQ};
        } else if (type == SectionType.CHORUS || type == SectionType.FINAL_CHORUS) {
            rhythm = new int[]{PPQ / 2, PPQ / 2, PPQ / 2, PPQ / 2, PPQ, PPQ};
        } else if (type == SectionType.BRIDGE) {
            rhythm = new int[]{PPQ * 2, PPQ, PPQ};
        } else {
            rhythm = new int[]{PPQ, PPQ, PPQ / 2, PPQ / 2, PPQ};
        }

        int cursor = barTick;

        for (int i = 0; i < rhythm.length && cursor < barTick + BAR; i++) {
            int duration = Math.min(rhythm[i], barTick + BAR - cursor);
            boolean strongBeat = ((cursor - barTick) % PPQ == 0);

            int note;

            if (strongBeat && chordNotes.length > 0) {
                int chordTone = chordNotes[Math.min(i % chordNotes.length, chordNotes.length - 1)];
                note = raiseToMelodyRegister(chordTone);
            } else {
                note = scale[random.nextInt(scale.length)];
            }

            if (type == SectionType.CHORUS || type == SectionType.FINAL_CHORUS) {
                if (note < 67) {
                    note += 12;
                }
            }

            if (type == SectionType.BRIDGE && i == rhythm.length - 1) {
                note += random.nextBoolean() ? 2 : -2;
            }

            int velocity = clampVelocity(
                    baseVelocity +
                            energy * 4 +
                            random.nextInt(9) - 4
            );

            int gate = Math.max(PPQ / 4, (int) (duration * 0.82));

            addNote(
                    track,
                    CH_MELODY,
                    clamp(note, 48, 88),
                    velocity,
                    cursor,
                    gate
            );

            cursor += duration;
        }
    }

    private int raiseToMelodyRegister(int note) {
        int result = note;

        while (result < 60) {
            result += 12;
        }

        while (result > 76) {
            result -= 12;
        }

        return result;
    }

    private void generateAccompanimentBar(
            Track track,
            String chord,
            MusicParams params,
            SectionType type,
            int baseVelocity,
            int energy,
            int barTick
    ) throws Exception {

        int[] notes = chordToNotes(chord);
        String instrument = safe(params.getInstrument());

        if (instrument.contains("吉他")) {
            generateGuitarBar(track, notes, type, baseVelocity, energy, barTick);
        } else {
            generatePianoBar(track, notes, type, baseVelocity, energy, barTick);
        }
    }

    private void generatePianoBar(
            Track track,
            int[] notes,
            SectionType type,
            int baseVelocity,
            int energy,
            int barTick
    ) throws Exception {

        int velocity = clampVelocity((int) (baseVelocity * 0.72) + energy * 3);

        if (type == SectionType.VERSE) {
            int[] order = buildArpeggioOrder(notes);

            for (int i = 0; i < 8; i++) {
                int note = order[i % order.length];
                addNote(track, CH_CHORD, note, velocity - 6, barTick + i * (PPQ / 2), PPQ / 2 - 4);
            }
            return;
        }

        if (type == SectionType.PRE_CHORUS) {
            for (int beat = 0; beat < 4; beat++) {
                int v = velocity + beat * 2;
                addChord(track, CH_CHORD, notes, v, barTick + beat * PPQ, PPQ - 6);
            }
            return;
        }

        if (type == SectionType.CHORUS || type == SectionType.FINAL_CHORUS) {
            for (int eighth = 0; eighth < 8; eighth++) {
                int v = velocity + (eighth % 2 == 0 ? 5 : -3);
                addChord(track, CH_CHORD, notes, v, barTick + eighth * (PPQ / 2), PPQ / 2 - 3);
            }

            if (notes.length > 0) {
                addNote(track, CH_CHORD, notes[0] + 12, velocity + 6, barTick, BAR - 8);
            }
            return;
        }

        if (type == SectionType.BRIDGE) {
            addChord(track, CH_CHORD, notes, velocity - 4, barTick, PPQ * 2 - 8);
            addChord(track, CH_CHORD, invertChord(notes), velocity, barTick + PPQ * 2, PPQ * 2 - 8);
            return;
        }

        addChord(track, CH_CHORD, notes, velocity, barTick, BAR - 8);
    }

    private void generateGuitarBar(
            Track track,
            int[] notes,
            SectionType type,
            int baseVelocity,
            int energy,
            int barTick
    ) throws Exception {

        int velocity = clampVelocity((int) (baseVelocity * 0.75) + energy * 3);

        if (type == SectionType.VERSE || type == SectionType.BRIDGE) {
            int[] order = buildArpeggioOrder(notes);

            for (int i = 0; i < 8; i++) {
                int note = order[i % order.length];

                addNote(
                        track,
                        CH_CHORD,
                        note,
                        velocity - 5,
                        barTick + i * (PPQ / 2),
                        PPQ / 2 - 5
                );
            }
            return;
        }

        int[] offsets = {
                0,
                PPQ,
                PPQ + PPQ / 2,
                PPQ * 2 + PPQ / 2,
                PPQ * 3,
                PPQ * 3 + PPQ / 2
        };

        boolean[] upStroke = {
                false, false, true, true, false, true
        };

        for (int i = 0; i < offsets.length; i++) {
            int v = velocity + (i == 0 || i == 4 ? 6 : -2);

            strumChord(
                    track,
                    notes,
                    v,
                    barTick + offsets[i],
                    upStroke[i],
                    PPQ / 2
            );
        }
    }

    private int[] buildArpeggioOrder(int[] notes) {
        if (notes.length == 0) {
            return new int[]{48, 55, 52, 55};
        }

        if (notes.length == 1) {
            return new int[]{notes[0]};
        }

        if (notes.length == 2) {
            return new int[]{notes[0], notes[1], notes[0] + 12, notes[1]};
        }

        return new int[]{
                notes[0],
                notes[Math.min(2, notes.length - 1)],
                notes[1],
                notes[Math.min(2, notes.length - 1)]
        };
    }

    private int[] invertChord(int[] notes) {
        if (notes.length < 2) {
            return notes;
        }

        int[] result = Arrays.copyOf(notes, notes.length);
        result[0] += 12;
        Arrays.sort(result);
        return result;
    }

    private void strumChord(
            Track track,
            int[] notes,
            int velocity,
            int startTick,
            boolean upStroke,
            int duration
    ) throws Exception {

        if (upStroke) {
            for (int i = notes.length - 1; i >= 0; i--) {
                int delay = (notes.length - 1 - i) * 3;
                addNote(track, CH_CHORD, notes[i], velocity, startTick + delay, duration);
            }
        } else {
            for (int i = 0; i < notes.length; i++) {
                addNote(track, CH_CHORD, notes[i], velocity, startTick + i * 3, duration);
            }
        }
    }

    private void generateBassBar(
            Track track,
            String chord,
            SectionType type,
            int baseVelocity,
            int energy,
            int barTick
    ) throws Exception {

        int[] chordNotes = chordToNotes(chord);

        if (chordNotes.length == 0) {
            return;
        }

        int root = lowerToBassRegister(chordNotes[0]);
        int fifth = lowerToBassRegister(chordNotes[Math.min(2, chordNotes.length - 1)]);
        int velocity = clampVelocity((int) (baseVelocity * 0.78) + energy * 2);

        if (type == SectionType.VERSE) {
            addNote(track, CH_BASS, root, velocity, barTick, PPQ * 2 - 8);
            addNote(track, CH_BASS, fifth, velocity - 5, barTick + PPQ * 2, PPQ * 2 - 8);
            return;
        }

        if (type == SectionType.CHORUS || type == SectionType.FINAL_CHORUS) {
            int[] notes = {root, root, fifth, root + 12};

            for (int beat = 0; beat < 4; beat++) {
                addNote(
                        track,
                        CH_BASS,
                        clamp(notes[beat], 28, 52),
                        velocity + (beat == 0 ? 5 : 0),
                        barTick + beat * PPQ,
                        PPQ - 6
                );
            }
            return;
        }

        if (type == SectionType.PRE_CHORUS) {
            addNote(track, CH_BASS, root, velocity, barTick, PPQ - 5);
            addNote(track, CH_BASS, fifth, velocity, barTick + PPQ, PPQ - 5);
            addNote(track, CH_BASS, root + 12, velocity + 3, barTick + PPQ * 2, PPQ - 5);
            addNote(track, CH_BASS, fifth, velocity + 3, barTick + PPQ * 3, PPQ - 5);
            return;
        }

        addNote(track, CH_BASS, root, velocity, barTick, BAR - 8);
    }

    private int lowerToBassRegister(int note) {
        int result = note;

        while (result > 47) {
            result -= 12;
        }

        while (result < 28) {
            result += 12;
        }

        return result;
    }

    private void generateDrumBar(
            Track track,
            MusicParams params,
            SectionType type,
            int baseVelocity,
            int energy,
            int barTick,
            int barIndex
    ) throws Exception {

        String genre = safe(params.getGenre());
        int velocity = clampVelocity(baseVelocity + 10 + energy * 2);

        boolean rnb = genre.contains("R&B") || genre.contains("R＆B");
        boolean rock = genre.contains("摇滚");
        boolean folk = genre.contains("民谣");
        boolean electronic = genre.contains("电子");

        if (type == SectionType.BRIDGE && barIndex % 2 == 0) {
            addDrum(track, 36, velocity - 8, barTick, PPQ / 2);
            addDrum(track, 38, velocity - 10, barTick + PPQ * 2, PPQ / 2);
            return;
        }

        int hatStep =
                (type == SectionType.CHORUS ||
                        type == SectionType.FINAL_CHORUS ||
                        rock ||
                        electronic)
                        ? PPQ / 2
                        : PPQ;

        for (int t = 0; t < BAR; t += hatStep) {
            int hat = (t == BAR - PPQ / 2 && energy >= 4) ? 46 : 42;
            addDrum(track, hat, velocity - 18, barTick + t, PPQ / 4);
        }

        addDrum(track, 38, velocity, barTick + PPQ, PPQ / 3);
        addDrum(track, 38, velocity + 2, barTick + PPQ * 3, PPQ / 3);

        addDrum(track, 36, velocity + 5, barTick, PPQ / 3);

        if (rnb) {
            addDrum(track, 36, velocity - 2, barTick + PPQ * 2 + PPQ / 2, PPQ / 3);
        } else if (folk) {
            addDrum(track, 36, velocity - 5, barTick + PPQ * 2, PPQ / 3);
        } else {
            addDrum(track, 36, velocity, barTick + PPQ * 2, PPQ / 3);
        }

        if (energy >= 4) {
            addDrum(track, 36, velocity - 2, barTick + PPQ * 3 + PPQ / 2, PPQ / 3);
        }

        if (type == SectionType.FINAL_CHORUS && barIndex % 4 == 0) {
            addDrum(track, 49, velocity + 8, barTick, PPQ / 2);
        }
    }

    private void addDrum(
            Track track,
            int note,
            int velocity,
            int startTick,
            int duration
    ) throws Exception {
        addNote(track, CH_DRUM, note, velocity, startTick, duration);
    }

    private void generateIntro(
            Track chordTrack,
            Track bassTrack,
            Track drumTrack,
            List<String> progression,
            MusicParams params,
            int baseVelocity,
            int startTick,
            int bars
    ) throws Exception {

        for (int bar = 0; bar < bars; bar++) {
            int barTick = startTick + bar * BAR;
            String chord = progression.get(bar % progression.size());
            int[] notes = chordToNotes(chord);

            if (safe(params.getInstrument()).contains("吉他")) {
                generateGuitarBar(chordTrack, notes, SectionType.VERSE, baseVelocity - 8, 1, barTick);
            } else {
                generatePianoBar(chordTrack, notes, SectionType.VERSE, baseVelocity - 8, 1, barTick);
            }

            if (bar > 0) {
                generateBassBar(bassTrack, chord, SectionType.VERSE, baseVelocity - 10, 1, barTick);
            }

            for (int beat = 0; beat < 4; beat++) {
                addDrum(drumTrack, 42, baseVelocity - 22, barTick + beat * PPQ, PPQ / 4);
            }
        }
    }

    private void generateOutro(
            Track chordTrack,
            Track bassTrack,
            Track drumTrack,
            List<String> progression,
            MusicParams params,
            int baseVelocity,
            int startTick,
            int bars
    ) throws Exception {

        for (int bar = 0; bar < bars; bar++) {
            int barTick = startTick + bar * BAR;
            String chord = progression.get(bar % progression.size());
            int[] notes = chordToNotes(chord);
            int v = Math.max(36, baseVelocity - 10 - bar * 6);

            if (bar == bars - 1) {
                addChord(chordTrack, CH_CHORD, notes, v, barTick, BAR - 4);

                if (notes.length > 0) {
                    addNote(
                            bassTrack,
                            CH_BASS,
                            lowerToBassRegister(notes[0]),
                            v,
                            barTick,
                            BAR - 4
                    );
                }

                addDrum(drumTrack, 49, v + 10, barTick, PPQ / 2);
            } else {
                if (safe(params.getInstrument()).contains("吉他")) {
                    generateGuitarBar(chordTrack, notes, SectionType.VERSE, v, 1, barTick);
                } else {
                    generatePianoBar(chordTrack, notes, SectionType.VERSE, v, 1, barTick);
                }
            }
        }
    }

    private List<String> parseChords(String chords) {
        List<String> result = new ArrayList<>();

        if (chords == null || chords.isBlank()) {
            return new ArrayList<>(List.of("C", "G", "Am", "F"));
        }

        String normalized = chords
                .replace("→", "-")
                .replace("—", "-")
                .replace("–", "-")
                .replace(",", "-")
                .replace("，", "-")
                .replace("|", "-");

        String[] parts = normalized.split("-");

        for (String part : parts) {
            String chord = part.trim()
                    .replaceAll("\\(.*?\\)", "")
                    .replaceAll("\\s+", "");

            if (chord.matches("[A-Ga-g](?:#|b)?[A-Za-z0-9+#/]*")) {
                result.add(normalizeChordName(chord));
            }
        }

        if (result.isEmpty()) {
            result.addAll(List.of("C", "G", "Am", "F"));
        }

        return result;
    }

    private int[] chordToNotes(String chord) {
        if (chord == null || chord.isBlank()) {
            return chordToNotes("C");
        }

        String clean = chord.trim()
                .replace("♯", "#")
                .replace("♭", "b")
                .replaceAll("\\s+", "");

        if (clean.contains("/")) {
            clean = clean.substring(0, clean.indexOf('/'));
        }

        Matcher matcher = Pattern.compile("^([A-Ga-g](?:#|b)?)(.*)$").matcher(clean);

        if (!matcher.find()) {
            return chordToNotes("C");
        }

        String root = normalizeNoteName(matcher.group(1));
        String suffix = matcher.group(2);
        String lower = suffix.toLowerCase();

        Integer semitone = NOTE_TO_SEMITONE.get(root);

        if (semitone == null) {
            return chordToNotes("C");
        }

        int rootMidi = 48 + semitone;
        List<Integer> intervals = new ArrayList<>();

        boolean minor = suffix.startsWith("m") && !suffix.startsWith("maj");

        if (lower.startsWith("dim")) {
            intervals.addAll(List.of(0, 3, 6));
        } else if (lower.startsWith("aug") || lower.startsWith("+")) {
            intervals.addAll(List.of(0, 4, 8));
        } else if (lower.startsWith("sus2")) {
            intervals.addAll(List.of(0, 2, 7));
        } else if (lower.startsWith("sus4")) {
            intervals.addAll(List.of(0, 5, 7));
        } else if (minor) {
            intervals.addAll(List.of(0, 3, 7));
        } else {
            intervals.addAll(List.of(0, 4, 7));
        }

        if (lower.contains("maj7")) {
            intervals.add(11);
        } else if (lower.contains("7")) {
            intervals.add(10);
        }

        if (lower.contains("add9")) {
            intervals.add(14);
        } else if (lower.endsWith("9") || lower.contains("9")) {
            if (!lower.contains("7") && !lower.contains("maj7")) {
                intervals.add(10);
            }
            intervals.add(14);
        }

        if (lower.contains("11")) {
            intervals.add(17);
        }

        if (lower.contains("13")) {
            intervals.add(21);
        }

        return intervals.stream()
                .distinct()
                .mapToInt(i -> rootMidi + i)
                .toArray();
    }

    private int[] getScale(String key) {
        if (key == null || key.isBlank()) {
            return buildScale("C", true);
        }

        String normalized = key.trim()
                .replace("大调", " Major")
                .replace("小调", " Minor");

        Matcher matcher = Pattern
                .compile("^([A-Ga-g](?:#|b)?).*?(Major|Minor|major|minor)")
                .matcher(normalized);

        if (matcher.find()) {
            String root = normalizeNoteName(matcher.group(1));
            boolean major = matcher.group(2).equalsIgnoreCase("Major");
            return buildScale(root, major);
        }

        if (normalized.toLowerCase().contains("minor")) {
            return buildScale("A", false);
        }

        return buildScale("C", true);
    }

    private int[] buildScale(String root, boolean major) {
        int rootSemitone = NOTE_TO_SEMITONE.getOrDefault(root, 0);
        int base = 60 + rootSemitone;

        int[] intervals = major
                ? new int[]{0, 2, 4, 5, 7, 9, 11}
                : new int[]{0, 2, 3, 5, 7, 8, 10};

        int[] scale = new int[intervals.length];

        for (int i = 0; i < intervals.length; i++) {
            scale[i] = base + intervals[i];
        }

        return scale;
    }

    private int normalizeBpm(int bpm) {
        if (bpm <= 0) {
            return 80;
        }

        return clamp(bpm, 50, 180);
    }

    private int getVelocity(String mood) {
        if (mood == null) {
            return 72;
        }

        if (mood.contains("忧郁") ||
                mood.contains("悲伤") ||
                mood.contains("低沉") ||
                mood.contains("失恋")) {
            return 58;
        }

        if (mood.contains("温柔") ||
                mood.contains("舒缓") ||
                mood.contains("平静") ||
                mood.contains("治愈")) {
            return 64;
        }

        if (mood.contains("欢快") ||
                mood.contains("开心") ||
                mood.contains("兴奋")) {
            return 92;
        }

        if (mood.contains("激烈") ||
                mood.contains("热血") ||
                mood.contains("紧张")) {
            return 104;
        }

        return 74;
    }

    private int getInstrumentProgram(String instrument, String genre) {
        if (instrument != null) {
            String text = instrument.trim();

            for (Map.Entry<String, Integer> entry : INSTRUMENT_PROGRAMS.entrySet()) {
                if (text.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        if (genre != null) {
            for (Map.Entry<String, Integer> entry : GENRE_PROGRAMS.entrySet()) {
                if (genre.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        return 0;
    }

    private void setTempo(Sequence sequence, int bpm) throws Exception {
        Track tempoTrack = sequence.createTrack();
        long mpq = 60_000_000L / bpm;

        MetaMessage tempoMessage = new MetaMessage();

        byte[] data = {
                (byte) (mpq >> 16),
                (byte) (mpq >> 8),
                (byte) mpq
        };

        tempoMessage.setMessage(0x51, data, 3);
        tempoTrack.add(new MidiEvent(tempoMessage, 0));
    }

    private void setProgram(Track track, int channel, int instrument) throws Exception {
        ShortMessage message = new ShortMessage();

        message.setMessage(
                ShortMessage.PROGRAM_CHANGE,
                channel,
                clamp(instrument, 0, 127),
                0
        );

        track.add(new MidiEvent(message, 0));
    }

    private void addChord(
            Track track,
            int channel,
            int[] notes,
            int velocity,
            int startTick,
            int duration
    ) throws Exception {

        for (int note : notes) {
            addNote(track, channel, note, velocity, startTick, duration);
        }
    }

    private void addNote(
            Track track,
            int channel,
            int note,
            int velocity,
            int startTick,
            int durationTick
    ) throws Exception {

        int safeNote = clamp(note, 0, 127);
        int safeVelocity = clampVelocity(velocity);
        int safeDuration = Math.max(1, durationTick);

        ShortMessage on = new ShortMessage();

        on.setMessage(
                ShortMessage.NOTE_ON,
                channel,
                safeNote,
                safeVelocity
        );

        track.add(new MidiEvent(on, startTick));

        ShortMessage off = new ShortMessage();

        off.setMessage(
                ShortMessage.NOTE_OFF,
                channel,
                safeNote,
                0
        );

        track.add(new MidiEvent(off, startTick + safeDuration));
    }

    private void addEndOfTrack(Track track, int tick) throws Exception {
        MetaMessage end = new MetaMessage();
        end.setMessage(0x2F, new byte[0], 0);
        track.add(new MidiEvent(end, tick));
    }

    private int clampVelocity(int velocity) {
        return clamp(velocity, 1, 127);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String normalizeNoteName(String note) {
        if (note == null || note.isBlank()) {
            return "C";
        }

        String first = note.substring(0, 1).toUpperCase();

        if (note.length() == 1) {
            return first;
        }

        return first + note.substring(1);
    }

    private String normalizeChordName(String chord) {
        if (chord == null || chord.isBlank()) {
            return "C";
        }

        String first = chord.substring(0, 1).toUpperCase();
        return first + chord.substring(1);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private long buildSeed(MusicParams params) {
        return Objects.hash(
                safe(params.getMood()),
                safe(params.getGenre()),
                safe(params.getKey()),
                safe(params.getChords()),
                safe(params.getLyrics()),
                safe(params.getInstrument()),
                params.getBpm()
        );
    }

    private enum SectionType {
        INTRO,
        VERSE,
        PRE_CHORUS,
        CHORUS,
        BRIDGE,
        FINAL_CHORUS,
        OUTRO
    }

    private static class SongSection {
        private final String name;
        private final int lyricLines;

        private SongSection(String name, int lyricLines) {
            this.name = name == null ? "Verse" : name.trim();
            this.lyricLines = Math.max(1, lyricLines);
        }

        private SectionType type() {
            String lower = name.toLowerCase();

            if (lower.contains("final chorus")) {
                return SectionType.FINAL_CHORUS;
            }

            if (lower.contains("pre-chorus")) {
                return SectionType.PRE_CHORUS;
            }

            if (lower.contains("chorus")) {
                return SectionType.CHORUS;
            }

            if (lower.contains("bridge")) {
                return SectionType.BRIDGE;
            }

            if (lower.contains("intro")) {
                return SectionType.INTRO;
            }

            if (lower.contains("outro")) {
                return SectionType.OUTRO;
            }

            return SectionType.VERSE;
        }

        @Override
        public String toString() {
            return name + "(" + lyricLines + ")";
        }
    }
}
