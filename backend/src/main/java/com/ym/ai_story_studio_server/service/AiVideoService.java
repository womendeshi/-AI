// {{CODE-Cycle-Integration:
//   Task_ID: [#FIX_ASYNC_VIDEO_002]
//   Timestamp: [2025-12-29 13:45:00]
//   Phase: [D-Develop]
//   Context-Analysis: "修改AiVideoService,注入AsyncVideoTaskService并调用。移除@Transactional解决事务未提交问题。删除已迁移的异步方法。"
//   Principle_Applied: "职责分离(SRP), Spring @Async最佳实践, 事务边界控制"
// }}
// {{START_MODIFICATIONS}}
package com.ym.ai_story_studio_server.service;

import com.ym.ai_story_studio_server.client.VectorEngineClient;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.config.AiProperties;
import com.ym.ai_story_studio_server.dto.ai.VideoGenerateRequest;
import com.ym.ai_story_studio_server.dto.ai.VideoGenerateResponse;
import com.ym.ai_story_studio_server.entity.Job;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.mapper.JobMapper;
import com.ym.ai_story_studio_server.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * AI视频生成服务
 *
 * <p>提供AI视频生成能力,采用异步任务模式
 *
 * <p><strong>核心功能:</strong>
 * <ul>
 *   <li>调用向量引擎API生成视频(异步)</li>
 *   <li>支持文生视频和图生视频两种模式</li>
 *   <li>创建和管理任务记录</li>
 *   <li>异步轮询和后处理由AsyncVideoTaskService负责</li>
 * </ul>
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
 *     "一个机器人在未来城市中行走,镜头缓缓推进",
 *     "16:9",   // 可选,默认16:9
 *     5,        // 可选,默认5秒
 *     null,     // 参考图URL(可选)
 *     1L        // 项目ID
 * );
 *
 * VideoGenerateResponse response = aiVideoService.generateVideo(request);
 * System.out.println("任务ID: " + response.jobId());
 * System.out.println("任务状态: " + response.status());
 *
 * // 图生视频(首帧参考)
 * VideoGenerateRequest request = new VideoGenerateRequest(
 *     "镜头缓缓推进,机器人转身看向镜头",
 *     "16:9",
 *     5,
 *     "https://example.com/reference.jpg",  // 首帧参考图
 *     1L
 * );
 * </pre>
 *
 * <p><strong>异步处理流程:</strong>
 * <ol>
 *   <li>用户调用generateVideo(),立即返回任务ID和PENDING状态</li>
 *   <li>AsyncVideoTaskService在后台异步轮询API查询生成进度</li>
 *   <li>生成完成后自动下载视频并上传到OSS</li>
 *   <li>进行积分计费并更新任务状态为SUCCEEDED</li>
 *   <li>用户可通过任务ID查询最终结果</li>
 * </ol>
 *
 * <p><strong>计费规则:</strong>
 * 按视频时长(秒)计费,费用从用户积分钱包扣除,具体单价由pricing_rules表配置
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiVideoService {

    private final VectorEngineClient vectorEngineClient;
    private final AsyncVideoTaskService asyncVideoTaskService;  // ✅ 注入独立的异步任务服务
    private final JobMapper jobMapper;
    private final AiProperties aiProperties;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 生成视频(异步)
     *
     * <p>提交视频生成任务,立即返回任务ID,后台异步处理生成过程
     *
     * <p><strong>方法流程:</strong>
     * <ol>
     *   <li>参数验证和默认值应用</li>
     *   <li>创建任务记录(状态:PENDING)</li>
     *   <li>调用向量引擎API提交视频生成任务</li>
     *   <li>立即返回响应(包含jobId和PENDING状态)</li>
     *   <li>异步启动后台任务进行轮询和处理</li>
     * </ol>
     *
     * <p><strong>注意事项:</strong>
     * <ul>
     *   <li>该方法会立即返回,不会等待视频生成完成</li>
     *   <li>用户需要通过jobId查询任务进度和最终结果</li>
     *   <li>积分会在视频生成成功后扣除,提交时不扣费</li>
     * </ul>
     *
     * <p><strong>修复说明:</strong>
     * <ul>
     *   <li>✅ 移除了@Transactional注解,确保Job记录立即可查询</li>
     *   <li>✅ 通过asyncVideoTaskService调用异步任务,确保@Async生效</li>
     * </ul>
     *
     * @param request 视频生成请求参数
     * @return 视频生成响应(包含jobId和PENDING状态)
     * @throws BusinessException 当参数错误或API调用失败时抛出
     */
    public VideoGenerateResponse generateVideo(VideoGenerateRequest request) {
        Long userId = UserContext.getUserId();
        log.info("========== 开始视频生成 ==========");
        log.info("userId: {}, promptLength: {}", userId, request.prompt().length());

        // 1. 应用默认配置
        AiProperties.Video videoConfig = aiProperties.getVideo();
        String model = videoConfig.getModel();
        String aspectRatio = request.aspectRatio() != null ? request.aspectRatio() : videoConfig.getDefaultAspectRatio();
        Integer duration = request.duration() != null ? request.duration() : videoConfig.getDefaultDuration();
        String size = request.size();
        if (size != null && !size.isBlank()) {
            String mappedAspectRatio = mapSizeToAspectRatio(size);
            if (mappedAspectRatio != null) {
                aspectRatio = mappedAspectRatio;
            }
        }

        // 验证视频时长
        if (duration > videoConfig.getMaxDuration()) {
            throw new BusinessException(
                    ResultCode.PARAM_INVALID,
                    String.format("视频时长不能超过%d秒", videoConfig.getMaxDuration())
            );
        }

        log.debug("应用配置 - model: {}, aspectRatio: {}, duration: {}秒, size: {}", model, aspectRatio, duration, size);

        // 2. ✅ 创建任务记录(MyBatis-Plus的insert会自动提交) - 修复: 传入 prompt
        Job job = createJob(userId, request.projectId(), model, aspectRatio, duration, size, request.prompt());
        log.info("✅ 任务已创建 - jobId: {}, jobType: VIDEO_GENERATION", job.getId());

        try {
            // 3. 调用向量引擎API提交视频生成任务
            log.debug("调用向量引擎API提交任务...");
            VectorEngineClient.VideoApiResponse apiResponse = vectorEngineClient.generateVideo(
                    request.prompt(),
                    model,
                    aspectRatio,
                    duration,
                    size,
                    request.referenceImageUrl()
            );

            String apiTaskId = apiResponse.id();
            log.info("✅ API任务已提交 - jobId: {}, apiTaskId: {}", job.getId(), apiTaskId);

            // 更新任务的metaJson,记录API的taskId (✅ 修复: 使用 ObjectMapper)
            try {
                Map<String, Object> metaData = new HashMap<>();
                metaData.put("model", model);
                metaData.put("aspectRatio", aspectRatio);
                metaData.put("duration", duration);
                metaData.put("size", size);
                metaData.put("prompt", request.prompt());
                metaData.put("apiTaskId", apiTaskId);
                job.setMetaJson(objectMapper.writeValueAsString(metaData));
                jobMapper.updateById(job);
            } catch (Exception e) {
                log.error("Failed to update meta_json", e);
            }

            // 4. ✅ 通过独立Service启动异步轮询任务(确保@Async生效)
            log.info("========== 【诊断】调用异步任务 ==========");
            log.info("当前线程: {}", Thread.currentThread().getName());
            log.info("asyncVideoTaskService类型: {}", asyncVideoTaskService.getClass().getName());
            log.info("准备调用pollVideoGenerationTask - jobId: {}, apiTaskId: {}", job.getId(), apiTaskId);

            asyncVideoTaskService.pollVideoGenerationTask(
                    job.getId(),
                    apiTaskId,
                    model,
                    aspectRatio,
                    duration,
                    userId
            );

            log.info("【诊断】异步方法调用完成(立即返回,不等待)");
            log.info("==========================================");

            // 5. ✅ 立即返回响应(不等待视频生成完成)
            log.info("========== 视频生成任务提交完成,立即返回 ==========");
            log.info("jobId: {}, status: PENDING", job.getId());
            log.info("================================================");

            return new VideoGenerateResponse(
                    job.getId(),
                    "PENDING",
                    model,
                    aspectRatio,
                    duration,
                    null  // 积分将在生成成功后扣除
            );

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 任务提交失败,更新任务状态
            log.error("视频生成任务提交失败 - jobId: {}", job.getId(), e);
            updateJobFailed(job, e.getMessage());
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "视频生成任务提交失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建任务记录
     *
     * @param userId 用户ID
     * @param projectId 项目ID
     * @param model 使用的模型
     * @param aspectRatio 画幅比例
     * @param duration 视频时长
     * @param prompt 用户提示词
     * @return 任务对象
     */
    private Job createJob(Long userId, Long projectId, String model, String aspectRatio, Integer duration, String size, String prompt) {
        Job job = new Job();
        job.setUserId(userId);
        job.setProjectId(projectId != null ? projectId : 0L);
        job.setJobType("VIDEO_GENERATION");
        job.setStatus("PENDING");
        job.setProgress(0);
        job.setTotalItems(1);
        job.setDoneItems(0);

        // ✅ 修复: 使用 ObjectMapper 安全地序列化 JSON
        try {
            Map<String, Object> metaData = new HashMap<>();
            metaData.put("model", model);
            metaData.put("aspectRatio", aspectRatio);
            metaData.put("duration", duration);
            metaData.put("size", size);
            metaData.put("prompt", prompt);
            job.setMetaJson(objectMapper.writeValueAsString(metaData));
        } catch (Exception e) {
            log.error("Failed to serialize meta_json", e);
            job.setMetaJson(String.format("{\"model\":\"%s\",\"aspectRatio\":\"%s\",\"duration\":%d}", model, aspectRatio, duration));
        }

        jobMapper.insert(job);
        return job;
    }

    /**
     * 更新任务状态为失败
     *
     * @param job 任务对象
     * @param errorMessage 错误信息
     */
    private void updateJobFailed(Job job, String errorMessage) {
        job.setStatus("FAILED");
        job.setErrorMessage(errorMessage);
        jobMapper.updateById(job);
    }

    private String mapSizeToAspectRatio(String size) {
        return switch (size) {
            case "1280x720", "1792x1024" -> "16:9";
            case "720x1280", "1024x1792" -> "9:16";
            default -> null;
        };
    }
}
// {{END_MODIFICATIONS}}
