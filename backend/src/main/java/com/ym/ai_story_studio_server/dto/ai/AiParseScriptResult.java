package com.ym.ai_story_studio_server.dto.ai;

import lombok.Data;

import java.util.List;

/**
 * AI解析剧本结果DTO
 *
 * <p>用于存储AI解析剧本后得到的分镜、角色和场景信息
 *
 * @param scriptSegments 分镜段落列表
 * @param characters 角色名称列表
 * @param scenes 场景描述列表
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Data
public class AiParseScriptResult {
    /**
     * 分镜段落列表
     */
    private List<String> scriptSegments;

    /**
     * 角色名称列表
     */
    private List<String> characters;

    /**
     * 场景描述列表
     */
    private List<String> scenes;

    /**
     * 默认构造函数
     */
    public AiParseScriptResult() {}

    /**
     * 构造函数
     *
     * @param scriptSegments 分镜段落列表
     * @param characters 角色名称列表
     * @param scenes 场景描述列表
     */
    public AiParseScriptResult(List<String> scriptSegments, List<String> characters, List<String> scenes) {
        this.scriptSegments = scriptSegments;
        this.characters = characters;
        this.scenes = scenes;
    }
}