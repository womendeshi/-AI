package com.ym.ai_story_studio_server.mq;

import java.io.Serializable;
import java.util.List;

/**
 * 单个分镜图生成消息
 * 
 * <p>支持自定义prompt和参考图的分镜图生成任务
 * 
 * @param jobId 任务ID
 * @param userId 用户ID
 * @param projectId 项目ID
 * @param shotId 分镜ID
 * @param aspectRatio 画幅比例(可选)
 * @param model 模型名称(可选)
 * @param customPrompt 自定义提示词(可选，如果提供则使用，否则使用分镜剧本)
 * @param referenceImageUrls 参考图URL列表(可选，用于图生图)
 * 
 * @author AI Story Studio
 * @since 1.0.0
 */
public record SingleShotImageMessage(
        Long jobId,
        Long userId,
        Long projectId,
        Long shotId,
        String aspectRatio,
        String model,
        String customPrompt,
        List<String> referenceImageUrls
) implements Serializable {
}
