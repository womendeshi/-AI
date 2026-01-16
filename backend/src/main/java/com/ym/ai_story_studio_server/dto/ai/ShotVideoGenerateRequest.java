package com.ym.ai_story_studio_server.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.io.Serializable;
import java.util.List;

/**
 * 单个分镜视频生成请求DTO
 *
 * <p>用于为单个分镜生成视频,整合剧本、角色、场景、道具等资源
 *
 * <p><strong>生成逻辑:</strong>
 * <ul>
 *   <li>有什么资源就使用什么资源</li>
 *   <li>剧本、场景、角色、道具都是可选的</li>
 *   <li>最终prompt = 内嵌规则 + 剧本 + 用户自定义内容</li>
 * </ul>
 *
 * @param prompt 完整的提示词(内嵌规则 + 剧本 + 用户自定义,必填)
 * @param aspectRatio 画幅比例(可选,默认16:9)
 * @param duration 视频时长(秒,可选,默认配置值)
 * @param size 输出分辨率(可选,默认由画幅比例决定)
 * @param referenceImageUrl 首帧参考图URL(可选)
 * @param scene 场景信息(可选)
 * @param characters 角色列表(可选)
 * @param props 道具列表(可选)
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
public record ShotVideoGenerateRequest(
        @NotBlank(message = "提示词不能为空")
        @Size(min = 1, max = 5000, message = "提示词长度必须在1-5000个字符之间")
        String prompt,

        @Pattern(regexp = "16:9|9:16|1:1|21:9", message = "不支持的画幅比例")
        String aspectRatio,

        @Min(value = 1, message = "视频时长必须大于0秒")
        @Max(value = 12, message = "视频时长不能超过12秒")
        Integer duration,

        @Pattern(
                regexp = "720x1280|1280x720|1024x1792|1792x1024",
                message = "不支持的输出分辨率"
        )
        String size,

        @URL(message = "参考图URL格式不正确")
        String referenceImageUrl,

        AssetResource scene,

        List<AssetResource> characters,

        List<AssetResource> props
) {
    /**
     * 资产资源
     *
     * @param id 资产ID
     * @param name 资产名称
     * @param thumbnailUrl 缩略图URL
     */
    public record AssetResource(
            @NotNull Long id,
            String name,
            String thumbnailUrl
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
