package com.ym.ai_story_studio_server.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * 视频生成请求DTO
 *
 * <p>用于调用AI视频生成服务,支持文生视频和图生视频
 *
 * <p><strong>支持的模型:</strong>
 * <ul>
 *   <li>sora-2 - OpenAI Sora视频生成模型</li>
 * </ul>
 *
 * <p><strong>支持的画幅比例:</strong>
 * <ul>
 *   <li>16:9 - 横向宽屏(默认)</li>
 *   <li>9:16 - 竖向</li>
 *   <li>1:1 - 正方形</li>
 * </ul>
 *
 * <p><strong>使用示例:</strong>
 * <pre>
 * // 文生视频
 * VideoGenerateRequest request = new VideoGenerateRequest(
 *     "一个机器人在未来城市中行走",
 *     "16:9",
 *     5,
 *     null,
 *     1L
 * );
 *
 * // 图生视频(首帧参考)
 * VideoGenerateRequest request = new VideoGenerateRequest(
 *     "镜头缓缓推进,机器人转身看向镜头",
 *     "16:9",
 *     5,
 *     "https://example.com/reference.jpg",
 *     1L
 * );
 * </pre>
 *
 * @param prompt 提示词(必填,1-1000字符)
 * @param aspectRatio 画幅比例(可选,默认使用配置值)
 * @param duration 视频时长(秒,可选,1-10秒,默认使用配置值)
 * @param size 输出分辨率(可选,默认由画幅比例决定)
 * @param referenceImageUrl 首帧参考图URL(可选,用于图生视频)
 * @param projectId 所属项目ID(必填)
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
public record VideoGenerateRequest(
        @NotBlank(message = "提示词不能为空")
        @Size(min = 1, max = 1000, message = "提示词长度必须在1-1000个字符之间")
        String prompt,

        @Pattern(regexp = "16:9|9:16|1:1", message = "不支持的画幅比例")
        String aspectRatio,

        @Min(value = 1, message = "视频时长不能小于1秒")
        @Max(value = 10, message = "视频时长不能大于10秒")
        Integer duration,

        @Pattern(
                regexp = "720x1280|1280x720|1024x1792|1792x1024",
                message = "不支持的输出分辨率"
        )
        String size,

        @URL(message = "参考图URL格式不正确")
        String referenceImageUrl,

        @NotNull(message = "项目ID不能为空")
        Long projectId
) {
}
