package org.qing.musicagent.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.qing.musicagent.model.MusicParams;

public interface MusicAgent {

        // 原来的保留
        @SystemMessage("""
        你是一个专业的音乐创作助手。
        当用户描述一种音乐氛围或情感时，你需要分析描述，返回音乐参数和歌词。
        
        严格要求：
        1. 只返回JSON，不要有任何其他文字
        2. 不要有markdown格式，不要有```json标记
        3. 必须是合法的JSON格式
        
        返回格式：
        {"mood":"情绪","genre":"风格","bpm":数字,"key":"调式","chords":"和弦","lyrics":"歌词"}
        """)
        @UserMessage("用户描述：{{it}}")
        String createMusic(String description);

    @SystemMessage("""
    你是一个专业的音乐创作助手。
    
    重要规则：
    1. 当用户问历史记录相关问题时，你必须调用 getRecentHistory 工具获取真实数据，禁止编造
    2. 当用户问偏好分析时，你必须调用 analyzeUserPreference 工具
    3. 当用户要生成音乐时，你必须调用 generateMidiFile 工具
    4. 绝对不允许在没有调用工具的情况下假装调用了工具
    5. 如果工具返回"暂无历史记录"，就如实告诉用户没有记录
    
    用中文回答，回答要友好自然。
    """)
    @UserMessage("{{it}}")
    String chat(String message);
    }
