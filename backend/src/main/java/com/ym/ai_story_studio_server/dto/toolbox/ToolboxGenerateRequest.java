package com.ym.ai_story_studio_server.dto.toolbox;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

import java.util.Collections;
import java.util.List;

/**
 * AI工具箱生成请求
 *
 * <p>统一的AI生成请求DTO,支持文本、图片、视频三种生成类型
 *
 * <p><strong>生成类型(type):</strong>
 * <ul>
 *   <li>TEXT - 文本生成</li>
 *   <li>IMAGE - 图片生成</li>
 *   <li>VIDEO - 视频生成</li>
 * </ul>
 *
 * <p><strong>使用示例:</strong>
 * <pre>
 * // 文本生成
 * ToolboxGenerateRequest request = new ToolboxGenerateRequest(
 *     "TEXT",
 *     "写一个科幻故事",
 *     null,
 *     null,
 *     null,
 *     null
 * );
 *
 * // 图片生成
 * ToolboxGenerateRequest request = new ToolboxGenerateRequest(
 *     "IMAGE",
 *     "赛博朋克风格的城市",
 *     "gemini-3-pro-image-preview",
 *     "16:9",
 *     null,
 *     null
 * );
 *
 * // 视频生成
 * ToolboxGenerateRequest request = new ToolboxGenerateRequest(
 *     "VIDEO",
 *     "机器人在城市中行走",
 *     null,
 *     "16:9",
 *     5,
 *     null
 * );
 * </pre>
 *
 * @param type 生成类型(TEXT/IMAGE/VIDEO)
 * @param prompt 提示词
 * @param model 模型名称(可选,使用配置的默认模型)
 * @param aspectRatio 画幅比例(可选,IMAGE和VIDEO时可用)
 * @param duration 视频时长(可选,VIDEO时可用,单位:秒)
 * @param referenceImageUrls 参考图URL列表(可选,用于图生图或图生视频)
 * @param referenceImageUrl 参考图URL(可选,用于图生图或图生视频，兼容旧字段)
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
public record ToolboxGenerateRequest(
        /**
         * 生成类型
         *
         * <p>必填,可选值:
         * <ul>
         *   <li>TEXT - 文本生成</li>
         *   <li>IMAGE - 图片生成</li>
         *   <li>VIDEO - 视频生成</li>
         * </ul>
         */
        @NotBlank(message = "生成类型不能为空")
        @Pattern(regexp = "TEXT|IMAGE|VIDEO", message = "生成类型必须是TEXT、IMAGE或VIDEO")
        String type,

        /**
         * 提示词
         *
         * <p>必填,长度限制:
         * <ul>
         *   <li>TEXT: 1-10000字符</li>
         *   <li>IMAGE: 1-2000字符</li>
         *   <li>VIDEO: 1-1000字符</li>
         * </ul>
         */
        @NotBlank(message = "提示词不能为空")
        @Size(min = 1, max = 10000, message = "提示词长度必须在1-10000字符之间")
        String prompt,

        /**
         * 模型名称
         *
         * <p>可选,不指定时使用配置的默认模型:
         * <ul>
         *   <li>TEXT: gemini-3-pro-preview</li>
         *   <li>IMAGE: gemini-3-pro-image-preview 或 jimeng-4.5</li>
         *   <li>VIDEO: sora-2</li>
         * </ul>
         */
        String model,

        /**
         * 画幅比例
         *
         * <p>可选,仅在IMAGE和VIDEO时生效
         * <p>IMAGE支持: 1:1, 16:9, 9:16, 21:9(默认)
         * <p>VIDEO支持: 16:9(默认), 9:16, 1:1
         */
        String aspectRatio,

        /**
         * 视频时长
         *
         * <p>可选,仅在VIDEO时生效
         * <p>单位:秒,范围:1-10,默认:5
         */
        @Min(value = 1, message = "视频时长不能小于1秒")
        @Max(value = 10, message = "视频时长不能超过10秒")
        Integer duration,

        /**
         * 参考图URL列表
         *
         * <p>可选,用于图生图或图生视频
         * <p>IMAGE: 作为参考图进行图生图
         * <p>VIDEO: 作为首帧参考图进行图生视频
         */
        List<@URL(message = "参考图URL格式不正确") String> referenceImageUrls,

        /**
         * 参考图URL(兼容旧字段)
         */
        @URL(message = "参考图URL格式不正确")
        String referenceImageUrl
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
