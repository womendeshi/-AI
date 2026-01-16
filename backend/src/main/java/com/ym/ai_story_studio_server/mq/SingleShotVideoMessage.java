package com.ym.ai_story_studio_server.mq;

import com.ym.ai_story_studio_server.dto.ai.ShotVideoGenerateRequest.AssetResource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 单个分镜视频生成任务消息
 *
 * <p>用于传递单个分镜视频生成任务的参数
 *
 * @author AI Story Studio
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SingleShotVideoMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private Long jobId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 分镜ID
     */
    private Long shotId;

    /**
     * 完整提示词(内嵌规则 + 剧本 + 用户自定义)
     */
    private String prompt;

    /**
     * 画幅比例
     */
    private String aspectRatio;

    /**
     * 首帧参考图URL(可选)
     */
    private String referenceImageUrl;

    /**
     * 场景信息(可选)
     */
    private AssetResource scene;

    /**
     * 角色列表(可选)
     */
    private List<AssetResource> characters;

    /**
     * 道具列表(可选)
     */
    private List<AssetResource> props;
}
