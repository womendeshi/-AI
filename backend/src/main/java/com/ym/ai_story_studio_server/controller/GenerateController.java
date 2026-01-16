package com.ym.ai_story_studio_server.controller;

import com.ym.ai_story_studio_server.common.Result;
import com.ym.ai_story_studio_server.dto.ai.BatchGenerateRequest;
import com.ym.ai_story_studio_server.dto.ai.BatchGenerateResponse;
import com.ym.ai_story_studio_server.dto.ai.ImageGenerateRequest;
import com.ym.ai_story_studio_server.dto.ai.ImageGenerateResponse;
import com.ym.ai_story_studio_server.dto.ai.ParseTextRequest;
import com.ym.ai_story_studio_server.dto.ai.ShotVideoGenerateRequest;
import com.ym.ai_story_studio_server.dto.ai.TextGenerateRequest;
import com.ym.ai_story_studio_server.dto.ai.TextGenerateResponse;
import com.ym.ai_story_studio_server.dto.ai.VideoGenerateRequest;
import com.ym.ai_story_studio_server.dto.ai.VideoGenerateResponse;
import com.ym.ai_story_studio_server.service.AiImageService;
import com.ym.ai_story_studio_server.service.AiTextService;
import com.ym.ai_story_studio_server.service.AiVideoService;
import com.ym.ai_story_studio_server.service.BatchGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * AI生成服务控制器
 *
 * <p>提供AI文本、图片、视频生成的REST API接口
 *
 * <p><strong>接口列表:</strong>
 * <ul>
 *   <li>POST /api/generate/text - 文本生成(同步)</li>
 *   <li>POST /api/generate/image - 图片生成(同步)</li>
 *   <li>POST /api/generate/video - 视频生成(异步)</li>
 * </ul>
 *
 * <p><strong>通用说明:</strong>
 * <ul>
 *   <li>所有接口需要用户登录认证</li>
 *   <li>所有接口会自动进行积分计费</li>
 *   <li>积分余额不足会返回错误</li>
 *   <li>请求参数会自动进行校验</li>
 * </ul>
 *
 * <p><strong>响应格式:</strong>
 * <pre>
 * {
 *   "code": 200,
 *   "message": "操作成功",
 *   "data": { ... },
 *   "timestamp": 1640000000000
 * }
 * </pre>
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GenerateController {

    private final AiTextService aiTextService;
    private final AiImageService aiImageService;
    private final AiVideoService aiVideoService;
    private final BatchGenerationService batchGenerationService;

    // ==================== 单个生成接口 ====================

    /**
     * 文本生成接口
     *
     * <p><strong>功能描述:</strong><br>
     * 调用大语言模型(LLM)生成文本内容,支持故事创作、对话生成、文案撰写等场景
     *
     * <p><strong>请求示例:</strong>
     * <pre>
     * POST /api/generate/text
     * Content-Type: application/json
     *
     * {
     *   "prompt": "写一个关于AI的科幻短故事",
     *   "temperature": 0.8,
     *   "topP": 0.95,
     *   "projectId": 1
     * }
     * </pre>
     *
     * <p><strong>响应示例:</strong>
     * <pre>
     * {
     *   "code": 200,
     *   "message": "操作成功",
     *   "data": {
     *     "text": "在2157年的新上海...",
     *     "tokensUsed": 1523,
     *     "model": "gemini-3-pro-preview",
     *     "costPoints": 152
     *   },
     *   "timestamp": 1640000000000
     * }
     * </pre>
     *
     * <p><strong>参数说明:</strong>
     * <ul>
     *   <li>prompt - 提示词,必填,1-10000字符</li>
     *   <li>temperature - 温度参数,可选,0-1,默认0.7</li>
     *   <li>topP - 采样参数,可选,0-1,默认0.9</li>
     *   <li>projectId - 项目ID,必填</li>
     * </ul>
     *
     * <p><strong>计费说明:</strong>
     * 按固定费用计费,费用从用户积分余额扣除
     *
     * @param request 文本生成请求参数
     * @return 文本生成响应结果
     */
    @PostMapping("/generate/text")
    public Result<TextGenerateResponse> generateText(@Valid @RequestBody TextGenerateRequest request) {
        log.info("Received text generation request - promptLength: {}", request.prompt().length());

        TextGenerateResponse response = aiTextService.generateText(request);

        log.info("Text generation completed - tokensUsed: {}, costPoints: {}",
                response.tokensUsed(), response.costPoints());

        return Result.success("文本生成成功", response);
    }

    /**
     * 图片生成接口
     *
     * <p><strong>功能描述:</strong><br>
     * 调用图片生成模型创建图像,支持文生图和图生图两种模式
     *
     * <p><strong>请求示例:</strong>
     * <pre>
     * POST /api/generate/image
     * Content-Type: application/json
     *
     * {
     *   "prompt": "一个赛博朋克风格的未来城市,霓虹灯,夜景",
     *   "model": "gemini-3-pro-image-preview",
     *   "aspectRatio": "16:9",
     *   "referenceImageUrl": null,
     *   "projectId": 1
     * }
     * </pre>
     *
     * <p><strong>响应示例:</strong>
     * <pre>
     * {
     *   "code": 200,
     *   "message": "操作成功",
     *   "data": {
     *     "imageUrl": "https://oss.example.com/images/ai_image_123.jpg",
     *     "jobId": 123,
     *     "model": "gemini-3-pro-image-preview",
     *     "aspectRatio": "16:9",
     *     "costPoints": 50
     *   },
     *   "timestamp": 1640000000000
     * }
     * </pre>
     *
     * <p><strong>参数说明:</strong>
     * <ul>
     *   <li>prompt - 提示词,必填,1-2000字符</li>
     *   <li>model - 模型名称,可选,支持gemini-3-pro-image-preview和jimeng-4.5</li>
     *   <li>aspectRatio - 画幅比例,可选,支持1:1/16:9/9:16/21:9,默认21:9</li>
     *   <li>referenceImageUrl - 参考图URL,可选,用于图生图</li>
     *   <li>projectId - 项目ID,必填</li>
     * </ul>
     *
     * <p><strong>计费说明:</strong>
     * 按张数计费,每张图片的费用从用户积分余额扣除
     *
     * <p><strong>注意事项:</strong>
     * <ul>
     *   <li>生成的图片会自动上传到OSS,返回OSS的URL</li>
     *   <li>原始API返回的URL不会保存</li>
     *   <li>如果提供referenceImageUrl,则为图生图模式</li>
     * </ul>
     *
     * @param request 图片生成请求参数
     * @return 图片生成响应结果
     */
    @PostMapping("/generate/image")
    public Result<ImageGenerateResponse> generateImage(@Valid @RequestBody ImageGenerateRequest request) {
        log.info("Received image generation request - promptLength: {}, model: {}",
                request.prompt().length(), request.model());

        ImageGenerateResponse response = aiImageService.generateImage(request);

        log.info("Image generation completed - jobId: {}, costPoints: {}",
                response.jobId(), response.costPoints());

        return Result.success("图片生成成功", response);
    }

    /**
     * 视频生成接口(异步)
     *
     * <p><strong>功能描述:</strong><br>
     * 调用视频生成模型创建视频,支持文生视频和图生视频两种模式
     *
     * <p><strong>重要提示:</strong><br>
     * 这是一个异步接口,会立即返回任务ID和PENDING状态,用户需要通过任务ID查询生成进度和最终结果
     *
     * <p><strong>请求示例:</strong>
     * <pre>
     * POST /api/generate/video
     * Content-Type: application/json
     *
     * {
     *   "prompt": "一个机器人在未来城市中行走,镜头缓缓推进",
     *   "aspectRatio": "16:9",
     *   "duration": 5,
     *   "referenceImageUrl": null,
     *   "projectId": 1
     * }
     * </pre>
     *
     * <p><strong>响应示例:</strong>
     * <pre>
     * {
     *   "code": 200,
     *   "message": "操作成功",
     *   "data": {
     *     "jobId": 456,
     *     "status": "PENDING",
     *     "model": "sora-2",
     *     "aspectRatio": "16:9",
     *     "duration": 5,
     *     "costPoints": null  // 积分将在生成成功后扣除
     *   },
     *   "timestamp": 1640000000000
     * }
     * </pre>
     *
     * <p><strong>参数说明:</strong>
     * <ul>
     *   <li>prompt - 提示词,必填,1-1000字符</li>
     *   <li>aspectRatio - 画幅比例,可选,支持16:9/9:16/1:1,默认16:9</li>
     *   <li>duration - 视频时长(秒),可选,1-10秒,默认5秒</li>
     *   <li>referenceImageUrl - 首帧参考图URL,可选,用于图生视频</li>
     *   <li>projectId - 项目ID,必填</li>
     * </ul>
     *
     * <p><strong>任务状态说明:</strong>
     * <ul>
     *   <li>PENDING - 任务已提交,等待处理</li>
     *   <li>RUNNING - 任务正在生成中</li>
     *   <li>SUCCEEDED - 任务成功完成</li>
     *   <li>FAILED - 任务失败</li>
     * </ul>
     *
     * <p><strong>计费说明:</strong>
     * <ul>
     *   <li>按视频时长(秒)计费</li>
     *   <li>积分会在视频生成成功后扣除,提交时不扣费</li>
     *   <li>如果生成失败,不会扣除积分</li>
     * </ul>
     *
     * <p><strong>后续操作:</strong>
     * <ul>
     *   <li>通过返回的jobId查询任务进度: GET /api/jobs/{jobId}</li>
     *   <li>任务完成后可获取视频的OSS URL</li>
     *   <li>任务会自动轮询API直到完成或超时</li>
     * </ul>
     *
     * @param request 视频生成请求参数
     * @return 视频生成响应结果(包含jobId和PENDING状态)
     */
    @PostMapping("/generate/video")
    public Result<VideoGenerateResponse> generateVideo(@Valid @RequestBody VideoGenerateRequest request) {
        log.info("Received video generation request - promptLength: {}, duration: {}s",
                request.prompt().length(), request.duration());

        VideoGenerateResponse response = aiVideoService.generateVideo(request);

        log.info("Video generation task submitted - jobId: {}, status: {}",
                response.jobId(), response.status());

        return Result.success("视频生成任务已提交,请通过jobId查询任务进度", response);
    }

    // ==================== 批量生成接口 ====================

    /**
     * 解析文本接口
     *
     * <p><strong>功能描述:</strong><br>
     * 将用户输入的小说/剧本文本解析为结构化的分镜脚本
     *
     * <p><strong>请求示例:</strong>
     * <pre>
     * POST /api/projects/1/parse
     * Content-Type: application/json
     *
     * {
     *   "rawText": "第一幕:未来城市\n\n主角小明站在摩天大楼的楼顶..."
     * }
     * </pre>
     *
     * <p><strong>响应示例:</strong>
     * <pre>
     * {
     *   "code": 200,
     *   "message": "操作成功",
     *   "data": {
     *     "jobId": 123,
     *     "status": "PENDING",
     *     "totalItems": 1,
     *     "message": "文本解析任务已提交"
     *   }
     * }
     * </pre>
     *
     * <p><strong>处理结果:</strong>
     * <ul>
     *   <li>自动创建分镜记录(每个场景/段落对应一个分镜)</li>
     *   <li>提取角色列表并创建项目角色</li>
     *   <li>提取场景列表并创建项目场景</li>
     *   <li>自动绑定分镜与角色、场景的关联关系</li>
     * </ul>
     *
     * @param projectId 项目ID
     * @param request 文本解析请求参数
     * @return 批量生成响应(包含jobId)
     */
    @PostMapping("/projects/{projectId}/parse")
    public Result<BatchGenerateResponse> parseText(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody ParseTextRequest request) {
        log.info("接收到文本解析请求 - projectId: {}, textLength: {}",
                projectId, request.rawText().length());

        BatchGenerateResponse response = batchGenerationService.parseText(projectId, request);

        log.info("文本解析任务已提交 - jobId: {}", response.jobId());

        return Result.success(response);
    }

    /**
     * 批量生成分镜图接口
     *
     * <p><strong>功能描述:</strong><br>
     * 为多个分镜批量生成图片资产
     *
     * <p><strong>请求示例:</strong>
     * <pre>
     * POST /api/projects/1/generate/shots
     * Content-Type: application/json
     *
     * {
     *   "targetIds": [1, 2, 3],
     *   "mode": "ALL",
     *   "countPerItem": 1,
     *   "aspectRatio": "21:9",
     *   "model": "jimeng-4.5"
     * }
     * </pre>
     *
     * <p><strong>响应示例:</strong>
     * <pre>
     * {
     *   "code": 200,
     *   "message": "操作成功",
     *   "data": {
     *     "jobId": 456,
     *     "status": "PENDING",
     *     "totalItems": 3,
     *     "message": "批量生成任务已提交"
     *   }
     * }
     * </pre>
     *
     * @param projectId 项目ID
     * @param request 批量生成请求参数
     * @return 批量生成响应(包含jobId)
     */
    @PostMapping("/projects/{projectId}/generate/shots")
    public Result<BatchGenerateResponse> generateShotsBatch(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody BatchGenerateRequest request) {
        log.info("接收到批量生成分镜图请求 - projectId: {}, targetCount: {}, mode: {}",
                projectId, request.targetIds().size(), request.mode());

        BatchGenerateResponse response = batchGenerationService.generateShotsBatch(projectId, request);

        log.info("批量生成分镜图任务已提交 - jobId: {}", response.jobId());

        return Result.success(response);
    }

    /**
     * 批量生成视频接口
     *
     * <p><strong>功能描述:</strong><br>
     * 为多个分镜批量生成视频资产
     *
     * <p><strong>请求示例:</strong>
     * <pre>
     * POST /api/projects/1/generate/videos
     * Content-Type: application/json
     *
     * {
     *   "targetIds": [1, 2, 3],
     *   "mode": "MISSING",
     *   "countPerItem": 1,
     *   "aspectRatio": "16:9",
     *   "model": "sora-2"
     * }
     * </pre>
     *
     * @param projectId 项目ID
     * @param request 批量生成请求参数
     * @return 批量生成响应(包含jobId)
     */
    @PostMapping("/projects/{projectId}/generate/videos")
    public Result<BatchGenerateResponse> generateVideosBatch(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody BatchGenerateRequest request) {
        log.info("接收到批量生成视频请求 - projectId: {}, targetCount: {}, mode: {}",
                projectId, request.targetIds().size(), request.mode());

        BatchGenerateResponse response = batchGenerationService.generateVideosBatch(projectId, request);

        log.info("批量生成视频任务已提交 - jobId: {}", response.jobId());

        return Result.success(response);
    }

    /**
     * 批量生成角色画像接口
     *
     * <p><strong>功能描述:</strong><br>
     * 为多个角色批量生成图片资产
     *
     * @param projectId 项目ID
     * @param request 批量生成请求参数
     * @return 批量生成响应(包含jobId)
     */
    @PostMapping("/projects/{projectId}/generate/characters")
    public Result<BatchGenerateResponse> generateCharactersBatch(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody BatchGenerateRequest request) {
        log.info("接收到批量生成角色画像请求 - projectId: {}, targetCount: {}, mode: {}",
                projectId, request.targetIds().size(), request.mode());

        BatchGenerateResponse response = batchGenerationService.generateCharactersBatch(projectId, request);

        log.info("批量生成角色画像任务已提交 - jobId: {}", response.jobId());

        return Result.success(response);
    }

    /**
     * 批量生成场景画像接口
     *
     * <p><strong>功能描述:</strong><br>
     * 为多个场景批量生成图片资产
     *
     * @param projectId 项目ID
     * @param request 批量生成请求参数
     * @return 批量生成响应(包含jobId)
     */
    @PostMapping("/projects/{projectId}/generate/scenes")
    public Result<BatchGenerateResponse> generateScenesBatch(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody BatchGenerateRequest request) {
        log.info("接收到批量生成场景画像请求 - projectId: {}, targetCount: {}, mode: {}",
                projectId, request.targetIds().size(), request.mode());

        BatchGenerateResponse response = batchGenerationService.generateScenesBatch(projectId, request);

        log.info("批量生成场景画像任务已提交 - jobId: {}", response.jobId());

        return Result.success(response);
    }

    /**
     * 批量生成道具画像接口
     *
     * <p><strong>功能描述:</strong><br>
     * 为多个道具批量生成图片资产
     *
     * @param projectId 项目ID
     * @param request 批量生成请求参数
     * @return 批量生成响应(包含jobId)
     */
    @PostMapping("/projects/{projectId}/generate/props")
    public Result<BatchGenerateResponse> generatePropsBatch(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody BatchGenerateRequest request) {
        log.info("接收到批量生成道具画像请求 - projectId: {}, targetCount: {}, mode: {}",
                projectId, request.targetIds().size(), request.mode());

        BatchGenerateResponse response = batchGenerationService.generatePropsBatch(projectId, request);

        log.info("批量生成道具画像任务已提交 - jobId: {}", response.jobId());

        return Result.success(response);
    }

    /**
     * 单个角色生成接口
     *
     * <p><strong>功能描述:</strong><br>
     * 为单个角色生成图片资产
     *
     * @param projectId 项目ID
     * @param characterId 角色ID
     * @param aspectRatio 画幅比例(可选)
     * @param model 模型名称(可选)
     * @return 批量生成响应(包含jobId)
     */
    @PostMapping("/projects/{projectId}/generate/character/{characterId}")
    public Result<BatchGenerateResponse> generateSingleCharacter(
            @PathVariable("projectId") Long projectId,
            @PathVariable("characterId") Long characterId,
            @RequestParam(required = false) String aspectRatio,
            @RequestParam(required = false) String model) {
        log.info("接收到单个角色生成请求 - projectId: {}, characterId: {}",
                projectId, characterId);

        BatchGenerateResponse response = batchGenerationService.generateSingleCharacter(
                projectId, characterId, aspectRatio, model);

        log.info("单个角色生成任务已提交 - jobId: {}", response.jobId());

        return Result.success(response);
    }

    /**
     * 单个场景生成接口
     *
     * <p><strong>功能描述:</strong><br>
     * 为单个场景生成图片资产
     *
     * @param projectId 项目ID
     * @param sceneId 场景ID
     * @param aspectRatio 画幅比例(可选)
     * @param model 模型名称(可选)
     * @return 批量生成响应(包含jobId)
     */
    @PostMapping("/projects/{projectId}/generate/scene/{sceneId}")
    public Result<BatchGenerateResponse> generateSingleScene(
            @PathVariable("projectId") Long projectId,
            @PathVariable("sceneId") Long sceneId,
            @RequestParam(required = false) String aspectRatio,
            @RequestParam(required = false) String model) {
        log.info("接收到单个场景生成请求 - projectId: {}, sceneId: {}",
                projectId, sceneId);

        BatchGenerateResponse response = batchGenerationService.generateSingleScene(
                projectId, sceneId, aspectRatio, model);

        log.info("单个场景生成任务已提交 - jobId: {}", response.jobId());

        return Result.success(response);
    }

    /**
     * 单个道具生成接口
     *
     * <p><strong>功能描述:</strong><br>
     * 为单个道具生成图片资产
     *
     * @param projectId 项目ID
     * @param propId 项目道具ID
     * @param aspectRatio 画幅比例(可选)
     * @param model 模型名称(可选)
     * @return 批量生成响应(包含jobId)
     */
    @PostMapping("/projects/{projectId}/generate/prop/{propId}")
    public Result<BatchGenerateResponse> generateSingleProp(
            @PathVariable("projectId") Long projectId,
            @PathVariable("propId") Long propId,
            @RequestParam(required = false) String aspectRatio,
            @RequestParam(required = false) String model) {
        log.info("接收到单个道具生成请求 - projectId: {}, propId: {}",
                projectId, propId);

        BatchGenerateResponse response = batchGenerationService.generateSingleProp(
                projectId, propId, aspectRatio, model);

        log.info("单个道具生成任务已提交 - jobId: {}", response.jobId());

        return Result.success(response);
    }

    /**
     * 单个分镜图生成接口
     *
     * <p><strong>功能描述:</strong><br>
     * 为单个分镜生成图片资产,支持自定义prompt和参考图
     *
     * <p><strong>请求示例:</strong>
     * <pre>
     * POST /api/projects/1/generate/shot/1?aspectRatio=21:9&customPrompt=系统规则+用户输入&referenceImageUrl=https://...
     * </pre>
     *
     * @param projectId 项目ID
     * @param shotId 分镜ID
     * @param aspectRatio 画幅比例(可选)
     * @param model 模型名称(可选)
     * @param customPrompt 自定义提示词(可选)
     * @param referenceImageUrl 参考图URL(可选)
     * @return 批量生成响应(包含jobId)
     */
    @PostMapping("/projects/{projectId}/generate/shot/{shotId}")
    public Result<BatchGenerateResponse> generateSingleShot(
            @PathVariable("projectId") Long projectId,
            @PathVariable("shotId") Long shotId,
            @RequestParam(required = false) String aspectRatio,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String customPrompt,
            @RequestParam(required = false) List<String> referenceImageUrls,
            @RequestParam(required = false) String referenceImageUrl) {
        log.info("接收到单个分镜图生成请求 - projectId: {}, shotId: {}, hasCustomPrompt: {}, hasReferenceImage: {}",
                projectId, shotId, customPrompt != null, referenceImageUrl != null);

        List<String> resolvedReferenceImageUrls = resolveReferenceImageUrls(referenceImageUrls, referenceImageUrl);

        BatchGenerateResponse response = batchGenerationService.generateSingleShot(
                projectId, shotId, aspectRatio, model, customPrompt, resolvedReferenceImageUrls);

        log.info("单个分镜图生成任务已提交 - jobId: {}", response.jobId());

        return Result.success(response);
    }

    private static List<String> resolveReferenceImageUrls(
            List<String> referenceImageUrls,
            String referenceImageUrl
    ) {
        if (referenceImageUrls != null && !referenceImageUrls.isEmpty()) {
            return referenceImageUrls;
        }
        if (referenceImageUrl != null && !referenceImageUrl.isBlank()) {
            return List.of(referenceImageUrl);
        }
        return Collections.emptyList();
    }

    /**
     * 单个分镜视频生成接口
     *
     * <p><strong>功能描述:</strong><br>
     * 为单个分镜生成视频,整合剧本、场景、角色、道具等资源
     *
     * <p><strong>请求示例:</strong>
     * <pre>
     * POST /api/projects/1/generate/shot-video/1
     * Content-Type: application/json
     *
     * {
     *   "prompt": "内嵌规则 + 分镜剧本 + 用户自定义内容",
     *   "aspectRatio": "16:9",
     *   "scene": {
     *     "id": 1,
     *     "name": "未来城市",
     *     "thumbnailUrl": "https://..."
     *   },
     *   "characters": [
     *     {
     *       "id": 1,
     *       "name": "小明",
     *       "thumbnailUrl": "https://..."
     *     }
     *   ],
     *   "props": []
     * }
     * </pre>
     *
     * @param projectId 项目ID
     * @param shotId 分镜ID
     * @param request 视频生成请求参数
     * @return 批量生成响应(包含jobId)
     */
    @PostMapping("/projects/{projectId}/generate/shot-video/{shotId}")
    public Result<BatchGenerateResponse> generateSingleShotVideo(
            @PathVariable("projectId") Long projectId,
            @PathVariable("shotId") Long shotId,
            @Valid @RequestBody ShotVideoGenerateRequest request) {
        log.info("接收到单个分镜视频生成请求 - projectId: {}, shotId: {}, promptLength: {}, hasScene: {}, characterCount: {}, propCount: {}",
                projectId, shotId, request.prompt().length(), 
                request.scene() != null, 
                request.characters() != null ? request.characters().size() : 0,
                request.props() != null ? request.props().size() : 0);

        BatchGenerateResponse response = batchGenerationService.generateSingleShotVideo(
                projectId, shotId, request);

        log.info("单个分镜视频生成任务已提交 - jobId: {}", response.jobId());

        return Result.success(response);
    }
}
