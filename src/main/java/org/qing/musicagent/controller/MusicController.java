package org.qing.musicagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.qing.musicagent.model.MusicHistory;
import org.qing.musicagent.model.MusicParams;
import org.qing.musicagent.repository.MusicHistoryRepository;
import org.qing.musicagent.service.MidiService;
import org.qing.musicagent.service.MusicAgent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/music")
public class MusicController {

    @Autowired
    private MusicAgent musicAgent;

    @Autowired
    private MidiService midiService;

    @Autowired
    private MusicHistoryRepository musicHistoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();


    // ============================================================
    // 当前用户名
    // 先保持你现有项目逻辑
    // ============================================================
    private String getCurrentUsername() {
        return "guest";
    }


    // ============================================================
    // 创建音乐 - 登录用户
    // ============================================================
    @PostMapping("/create")
    public Map<String, Object> createMusic(
            @RequestParam String description,
            @RequestParam String instrument) {

        return doGenerate(
                description,
                instrument,
                getCurrentUsername()
        );
    }


    // ============================================================
    // 创建音乐 - 游客
    // ============================================================
    @PostMapping("/create/guest")
    public Map<String, Object> createMusicGuest(
            @RequestParam String description,
            @RequestParam String instrument) {

        return doGenerate(
                description,
                instrument,
                "guest_" + UUID.randomUUID()
        );
    }


    // ============================================================
    // 核心生成
    // ============================================================
    private Map<String, Object> doGenerate(
            String description,
            String instrument,
            String userId) {

        Map<String, Object> result = new HashMap<>();

        try {

            String selectedInstrument =
                    normalizeInstrument(instrument);

            System.out.println();
            System.out.println("================================");
            System.out.println("🎵 开始生成音乐");
            System.out.println("用户: " + userId);
            System.out.println("描述: " + description);
            System.out.println("乐器: " + selectedInstrument);
            System.out.println("================================");


            // ====================================================
            // 把用户选的乐器明确传给 AI
            // ====================================================
            String fullDescription = """
                    用户明确选择的主要伴奏乐器：%s

                    用户的创作描述：
                    %s

                    instrument 字段必须返回“%s”。

                    不要因为 genre 改变用户明确选择的乐器。
                    """.formatted(
                    selectedInstrument,
                    description,
                    selectedInstrument
            );


            String raw =
                    musicAgent.createMusic(
                            userId,
                            fullDescription
                    );


            if (raw == null || raw.isBlank()) {
                throw new RuntimeException("AI 返回为空");
            }


            System.out.println("【AI 原始响应】");
            System.out.println(raw);


            MusicParams params =
                    extractJsonParams(raw);


            if (params == null) {
                throw new RuntimeException("无法解析 AI 返回参数");
            }


            // ====================================================
            // 乐器强制以用户实际选择为准
            // ====================================================
            params.setInstrument(
                    selectedInstrument
            );


            // 钢琴不使用 capo
            if ("钢琴".equals(
                    params.getInstrument())) {

                params.setCapo(0);

            } else if (
                    params.getCapo() < 0 ||
                            params.getCapo() > 12
            ) {

                params.setCapo(0);
            }


            System.out.println();
            System.out.println("✅ 参数解析成功");
            System.out.println("Mood: " + params.getMood());
            System.out.println("Genre: " + params.getGenre());
            System.out.println("BPM: " + params.getBpm());
            System.out.println("Key: " + params.getKey());
            System.out.println("Capo: " + params.getCapo());
            System.out.println("Instrument: " + params.getInstrument());
            System.out.println("Chords: " + params.getChords());


            // ====================================================
            // 生成 MIDI
            // ====================================================
            String filePath =
                    midiService.generateMidi(
                            params
                    );


            if (filePath == null ||
                    filePath.isBlank()) {

                throw new RuntimeException(
                        "MIDI 文件生成失败"
                );
            }


            // ====================================================
            // 保存历史
            // ====================================================
            MusicHistory history =
                    new MusicHistory();

            history.setUsername(userId);
            history.setDescription(description);
            history.setMood(params.getMood());
            history.setGenre(params.getGenre());
            history.setBpm(params.getBpm());
            history.setKey(params.getKey());
            history.setChords(params.getChords());
            history.setLyrics(params.getLyrics());
            history.setFilePath(filePath);

            musicHistoryRepository.save(
                    history
            );


            // ====================================================
            // 返回前端
            // ====================================================
            result.put(
                    "success",
                    true
            );

            result.put(
                    "params",
                    params
            );

            result.put(
                    "historyId",
                    history.getId()
            );

            result.put(
                    "downloadUrl",
                    buildDownloadUrl(filePath)
            );


        } catch (Exception e) {

            System.err.println(
                    "❌ 生成失败: " +
                            e.getMessage()
            );

            e.printStackTrace();

            result.put(
                    "success",
                    false
            );

            result.put(
                    "error",
                    "生成失败: " +
                            e.getMessage()
            );
        }

        return result;
    }


    // ============================================================
    // 根据已有参数重新生成 MIDI
    // 登录用户
    // ============================================================
    @PostMapping("/regenerate")
    public Map<String, Object> regenerate(
            @RequestBody MusicParams params) {

        return doRegenerate(
                params
        );
    }


    // ============================================================
    // 根据已有参数重新生成 MIDI
    // 游客
    // ============================================================
    @PostMapping("/regenerate/guest")
    public Map<String, Object> regenerateGuest(
            @RequestBody MusicParams params) {

        return doRegenerate(
                params
        );
    }


    // ============================================================
    // 重生成 MIDI
    // ============================================================
    private Map<String, Object> doRegenerate(
            MusicParams params) {

        Map<String, Object> result =
                new HashMap<>();

        try {

            if (params == null) {
                throw new RuntimeException(
                        "歌曲参数为空"
                );
            }


            params.setInstrument(
                    normalizeInstrument(
                            params.getInstrument()
                    )
            );


            if ("钢琴".equals(
                    params.getInstrument())) {

                params.setCapo(0);

            } else if (
                    params.getCapo() < 0 ||
                            params.getCapo() > 12
            ) {

                params.setCapo(0);
            }


            System.out.println();
            System.out.println("================================");
            System.out.println("🔄 根据修改后的参数重新生成 MIDI");
            System.out.println(
                    "Instrument: " +
                            params.getInstrument()
            );
            System.out.println(
                    "Key: " +
                            params.getKey()
            );
            System.out.println(
                    "BPM: " +
                            params.getBpm()
            );
            System.out.println(
                    "Chords: " +
                            params.getChords()
            );
            System.out.println("================================");


            String filePath =
                    midiService.generateMidi(
                            params
                    );


            result.put(
                    "success",
                    true
            );

            result.put(
                    "params",
                    params
            );

            result.put(
                    "downloadUrl",
                    buildDownloadUrl(filePath)
            );


        } catch (Exception e) {

            System.err.println(
                    "❌ MIDI 重新生成失败: " +
                            e.getMessage()
            );

            e.printStackTrace();

            result.put(
                    "success",
                    false
            );

            result.put(
                    "error",
                    "重新生成失败: " +
                            e.getMessage()
            );
        }

        return result;
    }


    // ============================================================
    // AI 对话 - 登录用户
    // ============================================================
    @PostMapping("/chat")
    public Map<String, Object> chat(
            @RequestBody Map<String, Object> body) {

        return doChat(
                getCurrentUsername(),
                body
        );
    }


    // ============================================================
    // AI 对话 - 游客
    // ============================================================
    @PostMapping("/chat/guest")
    public Map<String, Object> chatGuest(
            @RequestBody Map<String, Object> body) {

        return doChat(
                "guest_chat_" +
                        UUID.randomUUID(),
                body
        );
    }


    // ============================================================
    // 对话核心逻辑
    // ============================================================
    private Map<String, Object> doChat(
            String userId,
            Map<String, Object> body) {

        Map<String, Object> result =
                new HashMap<>();

        try {

            Object messageObj =
                    body.get(
                            "message"
                    );

            String message =
                    messageObj == null
                            ? ""
                            : messageObj
                            .toString()
                            .trim();


            if (message.isEmpty()) {

                result.put(
                        "success",
                        false
                );

                result.put(
                        "error",
                        "消息不能为空"
                );

                return result;
            }


            // ====================================================
            // 当前歌曲参数
            // ====================================================
            MusicParams currentParams =
                    null;

            Object currentParamsObj =
                    body.get(
                            "currentParams"
                    );


            if (currentParamsObj != null) {

                try {

                    currentParams =
                            objectMapper.convertValue(
                                    currentParamsObj,
                                    MusicParams.class
                            );

                } catch (Exception e) {

                    System.err.println(
                            "⚠️ currentParams 解析失败: " +
                                    e.getMessage()
                    );
                }
            }


            String prompt =
                    message;


            // ====================================================
            // 给 AI 带上完整当前歌曲
            // ====================================================
            if (currentParams != null) {

                String paramsJson =
                        objectMapper
                                .writeValueAsString(
                                        currentParams
                                );


                prompt = """
                        当前歌曲完整参数如下：

                        %s

                        用户本次要求：

                        %s

                        规则：

                        1. 如果用户只是提问，正常回答，不要返回 MUSIC_UPDATE。
                        2. 如果用户要求修改歌曲，必须返回完整 MUSIC_UPDATE。
                        3. 未要求修改的字段保持原值。
                        4. 更换乐器时必须修改 instrument。
                        5. 用户说“换成吉他”时默认 instrument = 木吉他。
                        6. 用户没有要求换乐器时，instrument 必须保持不变。
                        7. lyrics 必须保留完整歌曲结构。
                        8. 每句歌词必须保留和弦标记。
                        """.formatted(
                        paramsJson,
                        message
                );
            }


            String response =
                    musicAgent.chat(
                            userId,
                            prompt
                    );


            if (response == null ||
                    response.isBlank()) {

                throw new RuntimeException(
                        "AI 没有返回内容"
                );
            }


            System.out.println();
            System.out.println(
                    "【AI 对话原始响应】"
            );

            System.out.println(
                    response
            );


            // ====================================================
            // 聊天窗口只显示自然语言
            // ====================================================
            String displayResponse =
                    response.replaceAll(
                            "(?s)\\[MUSIC_UPDATE\\].*?\\[/MUSIC_UPDATE\\]",
                            ""
                    ).trim();


            if (displayResponse.isEmpty()) {

                displayResponse =
                        "好的，已经按你的要求调整了。";
            }


            result.put(
                    "success",
                    true
            );

            result.put(
                    "response",
                    displayResponse
            );


            // ====================================================
            // 提取更新后的参数
            // ====================================================
            MusicParams updatedParams =
                    parseUpdatedParams(
                            response
                    );


            if (updatedParams != null) {

                // AI 漏 instrument 时继承原来的
                if (
                        (updatedParams.getInstrument() == null ||
                                updatedParams
                                        .getInstrument()
                                        .isBlank())
                                &&
                                currentParams != null
                ) {

                    updatedParams.setInstrument(
                            currentParams.getInstrument()
                    );
                }


                updatedParams.setInstrument(
                        normalizeInstrument(
                                updatedParams
                                        .getInstrument()
                        )
                );


                if ("钢琴".equals(
                        updatedParams.getInstrument())) {

                    updatedParams.setCapo(0);

                } else if (
                        updatedParams.getCapo() < 0 ||
                                updatedParams.getCapo() > 12
                ) {

                    updatedParams.setCapo(0);
                }


                result.put(
                        "params",
                        updatedParams
                );
            }


        } catch (Exception e) {

            System.err.println(
                    "❌ 对话失败: " +
                            e.getMessage()
            );

            e.printStackTrace();

            result.put(
                    "success",
                    false
            );

            result.put(
                    "error",
                    "对话失败: " +
                            e.getMessage()
            );
        }

        return result;
    }


    // ============================================================
    // MUSIC_UPDATE 解析
    // ============================================================
    private MusicParams parseUpdatedParams(
            String aiResponse) {

        if (aiResponse == null ||
                aiResponse.isBlank()) {

            return null;
        }


        try {

            Pattern pattern =
                    Pattern.compile(
                            "\\[MUSIC_UPDATE\\]\\s*([\\s\\S]*?)\\s*\\[/MUSIC_UPDATE\\]"
                    );


            Matcher matcher =
                    pattern.matcher(
                            aiResponse
                    );


            if (!matcher.find()) {
                return null;
            }


            String json =
                    matcher
                            .group(1)
                            .replace(
                                    "```json",
                                    ""
                            )
                            .replace(
                                    "```JSON",
                                    ""
                            )
                            .replace(
                                    "```",
                                    ""
                            )
                            .trim();


            try {

                return objectMapper.readValue(
                        json,
                        MusicParams.class
                );

            } catch (Exception firstError) {

                String cleaned =
                        json
                                .replaceAll(
                                        ",\\s*}",
                                        "}"
                                )
                                .replaceAll(
                                        ",\\s*]",
                                        "]"
                                );


                return objectMapper.readValue(
                        cleaned,
                        MusicParams.class
                );
            }


        } catch (Exception e) {

            System.err.println(
                    "⚠️ MUSIC_UPDATE 解析失败: " +
                            e.getMessage()
            );

            return null;
        }
    }


    // ============================================================
    // 初次生成 JSON 解析
    // ============================================================
    private MusicParams extractJsonParams(
            String aiResponse) {

        if (aiResponse == null ||
                aiResponse.isBlank()) {

            return null;
        }


        try {

            String text =
                    aiResponse
                            .replace(
                                    "```json",
                                    ""
                            )
                            .replace(
                                    "```JSON",
                                    ""
                            )
                            .replace(
                                    "```",
                                    ""
                            )
                            .trim();


            int startIdx =
                    text.indexOf('{');

            int endIdx =
                    text.lastIndexOf('}');


            if (startIdx < 0 ||
                    endIdx < 0 ||
                    startIdx >= endIdx) {

                return null;
            }


            String json =
                    text.substring(
                            startIdx,
                            endIdx + 1
                    );


            try {

                return objectMapper.readValue(
                        json,
                        MusicParams.class
                );

            } catch (Exception firstError) {

                String cleaned =
                        json
                                .replaceAll(
                                        ",\\s*}",
                                        "}"
                                )
                                .replaceAll(
                                        ",\\s*]",
                                        "]"
                                );


                return objectMapper.readValue(
                        cleaned,
                        MusicParams.class
                );
            }


        } catch (Exception e) {

            System.err.println(
                    "❌ JSON 解析失败: " +
                            e.getMessage()
            );

            return null;
        }
    }


    // ============================================================
    // MIDI 下载
    //
    // 注意：
    // 不再接收 path=output\\xxx.mid
    //
    // 只接收：
    // file=xxx.mid
    //
    // 解决 Windows 反斜杠导致 HTTP 400
    // ============================================================
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadMusic(
            @RequestParam String file)
            throws IOException {


        File outputDir =
                new File("output")
                        .getCanonicalFile();


        // ========================================================
        // 防止传入 ../ 或目录路径
        // 只允许文件名
        // ========================================================
        String safeFileName =
                new File(file)
                        .getName();


        File target =
                new File(
                        outputDir,
                        safeFileName
                ).getCanonicalFile();


        // ========================================================
        // 防止目录穿越
        // ========================================================
        if (!target.toPath()
                .startsWith(
                        outputDir.toPath()
                )) {

            return ResponseEntity
                    .badRequest()
                    .build();
        }


        if (!target.exists() ||
                !target.isFile()) {

            System.err.println(
                    "❌ MIDI 文件不存在: " +
                            target.getAbsolutePath()
            );

            return ResponseEntity
                    .notFound()
                    .build();
        }


        Resource resource =
                new FileSystemResource(
                        target
                );


        String name =
                target
                        .getName()
                        .toLowerCase();


        MediaType type;


        if (
                name.endsWith(".mid") ||
                        name.endsWith(".midi")
        ) {

            type =
                    MediaType.parseMediaType(
                            "audio/midi"
                    );

        } else if (
                name.endsWith(".mp3")
        ) {

            type =
                    MediaType.parseMediaType(
                            "audio/mpeg"
                    );

        } else if (
                name.endsWith(".wav")
        ) {

            type =
                    MediaType.parseMediaType(
                            "audio/wav"
                    );

        } else {

            type =
                    MediaType.APPLICATION_OCTET_STREAM;
        }


        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" +
                                target.getName() +
                                "\""
                )
                .contentType(type)
                .contentLength(
                        target.length()
                )
                .body(resource);
    }


    // ============================================================
    // 构造下载 URL
    //
    // 原来：
    // /music/download?path=output\\music_xxx.mid
    //
    // 现在：
    // /music/download?file=music_xxx.mid
    // ============================================================
    private String buildDownloadUrl(
            String filePath) {


        File file =
                new File(
                        filePath
                );


        String fileName =
                file.getName();


        return "/music/download?file=" +
                URLEncoder.encode(
                        fileName,
                        StandardCharsets.UTF_8
                );
    }


    // ============================================================
    // 乐器名称统一
    // ============================================================
    private String normalizeInstrument(
            String instrument) {


        if (instrument == null ||
                instrument.isBlank()) {

            return "钢琴";
        }


        String value =
                instrument.trim();


        if (value.contains("电吉他")) {
            return "电吉他";
        }


        if (value.contains("尼龙吉他")) {
            return "尼龙吉他";
        }


        if (
                value.contains("木吉他") ||
                        value.equals("吉他") ||
                        value.contains("原声吉他")
        ) {

            return "木吉他";
        }


        if (value.contains("钢琴")) {
            return "钢琴";
        }


        if (value.contains("贝斯")) {
            return "贝斯";
        }


        if (value.contains("弦乐")) {
            return "弦乐";
        }


        return value;
    }
}