package com.ym.ai_story_studio_server.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.Collections;
import java.util.List;
/**
 * 图片生成请求DTO
 *
 * <p>用于调用AI图片生成服务,支持多种模型和画幅比例
 *
 * <p><strong>支持的模型:</strong>
 * <ul>
 *   <li>gemini-3-pro-image-preview - Google Gemini图片生成模型</li>
 *   <li>jimeng-4.5 - 即梦图片生成模型(反代)</li>
 * </ul>
 *
 * <p><strong>支持的画幅比例:</strong>
 * <ul>
 *   <li>1:1 - 正方形</li>
 *   <li>16:9 - 横向宽屏</li>
 *   <li>9:16 - 竖向</li>
 *   <li>21:9 - 超宽屏(默认)</li>
 * </ul>
 *
 * <p><strong>使用示例:</strong>
 * <pre>
 * ImageGenerateRequest request = new ImageGenerateRequest(
 *     "一个科幻风格的城市，赛博朋克，霓虹灯",
 *     "gemini-3-pro-image-preview",
 *     "16:9",
 *     null,
 *     1L
 * );
 * </pre>
 *
 * @param prompt 提示词(必填,1-2000字符)
 * @param model 模型名称(可选,默认使用配置值)
 * @param aspectRatio 画幅比例(可选,默认使用配置值)
 * @param referenceImageUrl 参考图URL(可选,用于图生图)
 * @param projectId 所属项目ID(必填)
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
public record ImageGenerateRequest(
        @NotBlank(message = "提示词不能为空")
        @Size(min = 1, max = 2000, message = "提示词长度必须在1-2000个字符之间")
        String prompt,

        @Pattern(regexp = "gemini-3-pro-image-preview|jimeng-4.5|gpt-image-1|gpt-4o-image-vip", message = "不支持的图片生成模型")
        String model,

        @Pattern(regexp = "1:1|16:9|9:16|21:9", message = "不支持的画幅比例")
        String aspectRatio,

        List<@URL(message = "参考图URL格式不正确") String> referenceImageUrls,

        @URL(message = "参考图URL格式不正确")
        String referenceImageUrl,

        @NotNull(message = "项目ID不能为空")
        Long projectId
) {
    public List<String> referenceImageUrlList() {
        if (referenceImageUrls != null && !referenceImageUrls.isEmpty()) {
            return referenceImageUrls;
        }
        if (referenceImageUrl != null && !referenceImageUrl.isBlank()) {
            return List.of(referenceImageUrl);
        }
        return Collections.emptyList();
    }
}
