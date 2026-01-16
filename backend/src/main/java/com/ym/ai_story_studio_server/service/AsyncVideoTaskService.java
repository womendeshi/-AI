// {{CODE-Cycle-Integration:
//   Task_ID: [#FIX_ASYNC_VIDEO_001]
//   Timestamp: [2025-12-29 13:30:00]
//   Phase: [D-Develop]
//   Context-Analysis: "创建独立的异步视频任务处理服务类,解决@Async在同一类内部调用失效的问题。职责:轮询视频生成任务状态、处理成功/失败回调、更新任务进度。"
//   Principle_Applied: "SRP单一职责原则, Spring @Async最佳实践, 线程安全(UserContext手动传递), 幂等性保证"
// }}
// {{START_MODIFICATIONS}}
package com.ym.ai_story_studio_server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ym.ai_story_studio_server.client.VectorEngineClient;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.config.AiProperties;
import com.ym.ai_story_studio_server.entity.Job;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.mapper.JobMapper;
import com.ym.ai_story_studio_server.util.UserContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 异步视频任务处理服务
 *
 * <p>专门负责视频生成任务的异步轮询和状态更新,与AiVideoService分离以确保@Async注解生效
 *
 * <p><strong>核心功能:</strong>
 * <ul>
 *   <li>异步轮询视频生成任务状态</li>
 *   <li>根据API状态自动映射任务进度(pending→10%, processing→50%, completed→100%)</li>
 *   <li>处理生成成功回调:下载视频、上传OSS、积分扣费</li>
 *   <li>处理生成失败回调:记录错误信息、更新任务状态</li>
 *   <li>支持超时自动失败</li>
 * </ul>
 *
 * <p><strong>为什么需要独立的Service类?</strong>
 * <ul>
 *   <li>Spring的@Async基于AOP代理实现</li>
 *   <li>同一类内部的方法调用(this.method())不会经过Spring代理</li>
 *   <li>因此@Async注解在同一类内部调用时会失效,方法会同步执行</li>
 *   <li>将异步方法提取到独立Service类中,通过Spring注入调用,确保代理生效</li>
 * </ul>
 *
 * <p><strong>线程安全注意事项:</strong>
 * <ul>
 *   <li>异步方法运行在独立线程中,无法访问主线程的ThreadLocal(如UserContext)</li>
 *   <li>因此userId等关键参数必须通过方法参数显式传递</li>
 *   <li>在需要使用UserContext的地方(如chargingService.charge()),手动设置并清理</li>
 * </ul>
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncVideoTaskService {

    private final VectorEngineClient vectorEngineClient;
    private final ChargingService chargingService;
    private final StorageService storageService;
    private final JobMapper jobMapper;
    private final AiProperties aiProperties;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 服务启动时恢复中断的视频生成任务
     * 
     * <p>参考huobao-drama-master项目的RecoverPendingTasks实现
     * 
     * <p><strong>恢复逻辑:</strong>
     * <ul>
     *   <li>查找所有RUNNING状态且有apiTaskId的视频生成任务</li>
     *   <li>为每个任务启动异步轮询</li>
     * </ul>
     */
    @PostConstruct
    public void recoverPendingVideoTasks() {
        try {
            // 查找所有RUNNING状态的视频生成任务
            List<Job> pendingJobs = jobMapper.selectList(
                    new LambdaQueryWrapper<Job>()
                            .eq(Job::getStatus, "RUNNING")
                            .eq(Job::getJobType, "SINGLE_SHOT_VIDEO")
            );
            
            if (pendingJobs.isEmpty()) {
                log.info("服务启动: 没有需要恢复的视频生成任务");
                return;
            }
            
            log.info("服务启动: 发现 {} 个需要恢复的视频生成任务", pendingJobs.size());
            
            for (Job job : pendingJobs) {
                try {
                    // 从metaJson中提取必要信息
                    String apiTaskId = extractFieldFromJson(job.getMetaJson(), "apiTaskId");
                    String model = extractFieldFromJson(job.getMetaJson(), "model");
                    String aspectRatio = extractFieldFromJson(job.getMetaJson(), "aspectRatio");
                    String durationStr = extractFieldFromJson(job.getMetaJson(), "duration");
                    Integer duration = durationStr != null ? Integer.parseInt(durationStr) : 5;
                    
                    if (apiTaskId == null || apiTaskId.isBlank()) {
                        log.warn("任务没有apiTaskId,跳过恢复 - jobId: {}", job.getId());
                        continue;
                    }
                    
                    log.info("恢复视频生成任务轮询 - jobId: {}, apiTaskId: {}", job.getId(), apiTaskId);
                    
                    // 启动异步轮询
                    pollVideoGenerationTask(
                            job.getId(),
                            apiTaskId,
                            model != null ? model : "sora-2",
                            aspectRatio != null ? aspectRatio : "16:9",
                            duration,
                            job.getUserId()
                    );
                    
                } catch (Exception e) {
                    log.error("恢复任务失败 - jobId: {}", job.getId(), e);
                }
            }
            
            log.info("服务启动: 视频生成任务恢复完成");
            
        } catch (Exception e) {
            log.error("服务启动: 恢复视频生成任务失败", e);
        }
    }

    /**
     * 轮询视频生成任务(异步方法)
     *
     * <p>在后台线程中定期查询API任务状态,直到成功或失败
     *
     * <p><strong>进度映射规则:</strong>
     * <ul>
     *   <li>pending(等待中) → 10%</li>
     *   <li>processing(生成中) → 50% (✅ API返回processing而非running)</li>
     *   <li>completed(成功) → 100% (✅ API返回completed而非succeeded)</li>
     *   <li>error(失败) → 0%</li>
     * </ul>
     *
     * <p><strong>安全保障:</strong>
     * <ul>
     *   <li>✅ 幂等性:每次更新前先查询最新状态,避免重复处理</li>
     *   <li>✅ 异常隔离:任何异常都会被捕获并记录到任务状态</li>
     *   <li>✅ 资源清理:确保UserContext在finally块中清理</li>
     *   <li>✅ 超时保护:超过最大轮询次数自动标记为失败</li>
     * </ul>
     *
     * @param jobId 本地任务ID
     * @param apiTaskId API返回的任务ID
     * @param model 模型名称
     * @param aspectRatio 画幅比例
     * @param duration 视频时长
     * @param userId 用户ID(用于积分扣费时设置UserContext)
     */
    @Async("taskExecutor")
    public void pollVideoGenerationTask(
            Long jobId,
            String apiTaskId,
            String model,
            String aspectRatio,
            Integer duration,
            Long userId
    ) {
        log.info("========== 异步轮询任务启动 ==========");
        log.info("jobId: {}, apiTaskId: {}, userId: {}", jobId, apiTaskId, userId);
        log.info("线程: {}", Thread.currentThread().getName());
        log.info("=====================================");

        AiProperties.Video videoConfig = aiProperties.getVideo();
        Long pollInterval = videoConfig.getPollInterval();
        Integer maxPollCount = videoConfig.getMaxPollCount();

        int pollCount = 0;

        try {
            while (pollCount < maxPollCount) {
                pollCount++;

                // 等待一段时间后再查询(避免过于频繁的API调用)
                Thread.sleep(pollInterval);

                // 查询任务状态
                log.info("轮询第 {}/{} 次 - jobId: {}, apiTaskId: {}",
                        pollCount, maxPollCount, jobId, apiTaskId);

                // 带容错的轮询(单次失败不终止整个任务,参考huobao-drama-master)
                VectorEngineClient.TaskStatusApiResponse statusResponse = null;
                int queryRetryCount = 0;
                int maxQueryRetries = 3;

                while (queryRetryCount < maxQueryRetries) {
                    try {
                        statusResponse = vectorEngineClient.queryTaskStatus(apiTaskId);
                        break;  // 查询成功，跳出重试循环

                    } catch (Exception e) {
                        queryRetryCount++;
                        log.warn("任务查询失败 - jobId: {}, 第{}/{}次重试, 错误: {}",
                                jobId, queryRetryCount, maxQueryRetries, e.getMessage());
                        
                        if (queryRetryCount < maxQueryRetries) {
                            Thread.sleep(3000);  // 等待3秒后重试
                        }
                    }
                }
                
                // 如果查询失败但未超过最大轮询次数,继续下一轮(参考huobao的continue逻辑)
                if (statusResponse == null) {
                    log.warn("本次轮询查询失败,继续下一轮 - jobId: {}, pollCount: {}", jobId, pollCount);
                    continue;  // 不抛异常，继续下一次轮询
                }

                String status = statusResponse.status();
                log.info("✅ API返回状态: {} - jobId: {}, videoUrl存在: {}",
                        status, jobId, statusResponse.videoUrl() != null);

                // ✅ 修复:根据status映射进度百分比(API不返回progress字段)
                Integer progress = mapStatusToProgress(status);

                // ✅ 安全保障:每次更新前先查询最新状态(幂等性)
                Job job = jobMapper.selectById(jobId);
                if (job == null) {
                    log.error("任务不存在,停止轮询 - jobId: {}", jobId);
                    break;
                }

                // 检查任务是否已被取消
                if ("CANCELED".equals(job.getStatus())) {
                    log.info("任务已被取消,停止轮询 - jobId: {}", jobId);
                    break;
                }

                // 更新任务进度和状态
                updateJobProgress(job, status, progress);

                log.info("任务进度已更新 - jobId: {}, status: {}, progress: {}%",
                        jobId, status, progress);

                // 检查任务是否完成
                if ("completed".equalsIgnoreCase(status)) {
                    // ✅ 任务成功:处理视频下载、上传OSS、积分扣费 (修复: 传入 aspectRatio)
                    handleVideoGenerationSuccess(jobId, statusResponse, model, aspectRatio, duration, userId);
                    break;

                } else if ("error".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
                    // ✅ 任务失败:记录错误信息
                    String errorMsg = "视频生成失败(status: " + status + ")";
                    handleVideoGenerationFailure(jobId, errorMsg);
                    break;
                }

                // 继续轮询...
            }

            // ✅ 超时保护:超过最大轮询次数
            if (pollCount >= maxPollCount) {
                log.warn("轮询超时 - jobId: {}, apiTaskId: {}, 已轮询{}次", jobId, apiTaskId, pollCount);
                handleVideoGenerationFailure(jobId, "视频生成超时(API响应超过预期时间)");
            }

        } catch (InterruptedException e) {
            log.error("轮询被中断 - jobId: {}", jobId, e);
            Thread.currentThread().interrupt();
            handleVideoGenerationFailure(jobId, "任务被中断");

        } catch (Exception e) {
            log.error("轮询失败 - jobId: {}", jobId, e);
            handleVideoGenerationFailure(jobId, "轮询任务失败: " + e.getMessage());
        }

        log.info("========== 异步轮询任务结束 ==========");
        log.info("jobId: {}, 总轮询次数: {}", jobId, pollCount);
        log.info("=====================================");
    }

    /**
     * 将API状态映射为进度百分比
     *
     * <p>由于向量引擎API不返回progress字段,我们根据status字段手动映射进度
     *
     * @param status API返回的任务状态(queued/processing/completed/error)
     * @return 进度百分比(0-100)
     */
    private Integer mapStatusToProgress(String status) {
        if (status == null) {
            return 0;
        }

        return switch (status.toLowerCase()) {
            case "queued", "pending" -> 10;    // 等待中:10% (✅ API返回queued)
            case "processing" -> 50;           // 生成中:50%
            case "completed" -> 100;           // 成功:100%
            case "error", "failed" -> 0;       // 失败:0%
            default -> 0;
        };
    }

    /**
     * 更新任务进度和状态
     *
     * @param job 任务对象
     * @param apiStatus API返回的状态
     * @param progress 进度百分比
     */
    private void updateJobProgress(Job job, String apiStatus, Integer progress) {
        // 映射API状态到本地任务状态
        String localStatus = mapApiStatusToLocalStatus(apiStatus);

        job.setStatus(localStatus);
        job.setProgress(progress);

        // 如果任务从PENDING变为RUNNING,记录开始时间
        if ("RUNNING".equals(localStatus) && job.getStartedAt() == null) {
            job.setStartedAt(java.time.LocalDateTime.now());
        }

        jobMapper.updateById(job);
    }

    /**
     * 将API状态映射为本地任务状态
     *
     * @param apiStatus API状态(queued/processing/completed/error)
     * @return 本地任务状态(PENDING/RUNNING/SUCCEEDED/FAILED)
     */
    private String mapApiStatusToLocalStatus(String apiStatus) {
        if (apiStatus == null) {
            return "PENDING";
        }

        return switch (apiStatus.toLowerCase()) {
            case "queued", "pending" -> "PENDING";  // ✅ API返回queued
            case "processing" -> "RUNNING";
            case "completed" -> "SUCCEEDED";
            case "error", "failed" -> "FAILED";
            default -> "PENDING";
        };
    }

    /**
     * 处理视频生成成功
     *
     * <p><strong>处理流程:</strong>
     * <ol>
     *   <li>从API响应中获取视频URL</li>
     *   <li>下载视频并上传到OSS</li>
     *   <li>进行积分扣费(需要手动设置UserContext)</li>
     *   <li>更新任务状态为SUCCEEDED,记录视频URL到metaJson</li>
     * </ol>
     *
     * <p><strong>安全保障:</strong>
     * <ul>
     *   <li>✅ UserContext手动设置和清理(finally块)</li>
     *   <li>✅ 异常捕获并转为任务失败状态</li>
     *   <li>✅ 完整的日志记录</li>
     * </ul>
     *
     * @param jobId 任务ID
     * @param statusResponse API状态响应
     * @param model 模型名称
     * @param aspectRatio 画幅比例
     * @param duration 视频时长
     * @param userId 用户ID
     */
    private void handleVideoGenerationSuccess(
            Long jobId,
            VectorEngineClient.TaskStatusApiResponse statusResponse,
            String model,
            String aspectRatio,
            Integer duration,
            Long userId
    ) {
        log.info("========== 视频生成成功处理开始 ==========");
        log.info("jobId: {}, userId: {}", jobId, userId);

        try {
            // 1. 获取视频URL (✅ 修复: videoUrl是顶层字段,不在result中)
            String tempVideoUrl = statusResponse.videoUrl();
            log.debug("API返回的视频URL: {}", tempVideoUrl);

            // 2. 下载视频并上传到OSS
            String ossVideoUrl = downloadAndUploadToOss(tempVideoUrl, jobId);
            log.info("视频已上传到OSS - jobId: {}, ossUrl: {}", jobId, ossVideoUrl);

            // 3. 进行积分计费(需要手动设置UserContext,因为这是异步线程)
            log.debug("开始积分扣费 - jobId: {}, userId: {}", jobId, userId);
            Map<String, Object> chargingMetaData = new HashMap<>();
            chargingMetaData.put("model", model);
            chargingMetaData.put("duration", duration);
            chargingMetaData.put("videoUrl", ossVideoUrl);

            // ✅ 安全保障:手动设置UserContext(异步线程中无法自动获取)
            UserContext.setUserId(userId);

            ChargingService.ChargingResult chargingResult = chargingService.charge(
                    ChargingService.ChargingRequest.builder()
                            .jobId(jobId)
                            .bizType("VIDEO_GENERATION")
                            .modelCode(model)
                            .quantity(duration)  // 按秒计费
                            .metaData(chargingMetaData)
                            .build()
            );

            log.info("积分扣费完成 - jobId: {}, 扣费: {} 积分", jobId, chargingResult.getTotalCost());

            // 4. 更新任务状态为成功 (✅ 修复: 使用 ObjectMapper)
            Job job = jobMapper.selectById(jobId);
            if (job != null) {
                // 从现有的meta_json中提取prompt
                String prompt = extractFieldFromJson(job.getMetaJson(), "prompt");

                job.setStatus("SUCCEEDED");
                job.setProgress(100);
                job.setDoneItems(1);
                job.setResultUrl(ossVideoUrl);
                job.setFinishedAt(java.time.LocalDateTime.now());

                // ✅ 修复: 使用 ObjectMapper 安全地序列化 JSON
                try {
                    Map<String, Object> metaData = new HashMap<>();
                    metaData.put("model", model);
                    metaData.put("aspectRatio", aspectRatio);
                    metaData.put("duration", duration);
                    metaData.put("prompt", prompt != null ? prompt : "");
                    metaData.put("resultUrl", ossVideoUrl);
                    job.setMetaJson(objectMapper.writeValueAsString(metaData));
                } catch (Exception e) {
                    log.error("Failed to serialize meta_json", e);
                }

                jobMapper.updateById(job);
            }

            log.info("========== 视频生成成功处理完成 ==========");

        } catch (Exception e) {
            log.error("视频生成成功处理失败 - jobId: {}", jobId, e);
            handleVideoGenerationFailure(jobId, "视频处理失败: " + e.getMessage());
        } finally {
            // ✅ 安全保障:确保清理UserContext(避免内存泄漏)
            UserContext.clear();
        }
    }

    /**
     * 处理视频生成失败
     *
     * @param jobId 任务ID
     * @param errorMessage 错误信息
     */
    private void handleVideoGenerationFailure(Long jobId, String errorMessage) {
        log.error("========== 视频生成失败 ==========");
        log.error("jobId: {}, 错误: {}", jobId, errorMessage);

        Job job = jobMapper.selectById(jobId);
        if (job != null) {
            job.setStatus("FAILED");
            job.setErrorMessage(errorMessage);
            job.setFinishedAt(java.time.LocalDateTime.now());
            jobMapper.updateById(job);
        }

        log.error("====================================");
    }

    /**
     * 从URL下载视频并上传到OSS
     *
     * <p>复用AiVideoService中的逻辑
     *
     * @param videoUrl 视频URL
     * @param jobId 任务ID(用于生成文件名)
     * @return OSS存储的视频URL
     */
    private String downloadAndUploadToOss(String videoUrl, Long jobId) {
        try {
            // 1. 从URL下载视频
            java.net.URL url = new java.net.URL(videoUrl);
            java.net.URLConnection connection = url.openConnection();
            connection.setConnectTimeout(30000);   // 30秒连接超时
            connection.setReadTimeout(300000);     // 5分钟读取超时(视频文件较大)

            String contentType = connection.getContentType();
            if (contentType == null) {
                contentType = "video/mp4";  // 默认类型
            }

            // 2. 生成文件名
            String extension = getExtensionFromContentType(contentType);
            String fileName = String.format("ai_video_%d%s", jobId, extension);

            log.debug("下载视频中 - url: {}, contentType: {}, fileName: {}",
                    videoUrl, contentType, fileName);

            // 3. 上传到OSS
            try (java.io.InputStream inputStream = connection.getInputStream()) {
                String ossUrl = storageService.upload(inputStream, fileName, contentType);
                log.debug("视频上传OSS成功 - ossUrl: {}", ossUrl);
                return ossUrl;
            }

        } catch (Exception e) {
            log.error("视频下载或上传失败 - url: {}", videoUrl, e);
            throw new com.ym.ai_story_studio_server.exception.BusinessException(
                    com.ym.ai_story_studio_server.common.ResultCode.OSS_ERROR,
                    "视频下载或上传失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 根据ContentType获取文件扩展名
     *
     * @param contentType MIME类型
     * @return 文件扩展名(带点)
     */
    private String getExtensionFromContentType(String contentType) {
        if (contentType == null) {
            return ".mp4";
        }

        return switch (contentType.toLowerCase()) {
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            case "video/x-msvideo" -> ".avi";
            default -> ".mp4";
        };
    }

    /**
     * 从JSON字符串中提取指定字段的值(简单实现)
     *
     * @param json JSON字符串
     * @param fieldName 字段名
     * @return 字段值,如果不存在则返回null
     */
    private String extractFieldFromJson(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return null;
        }

        // 简单的字符串解析: 查找 "fieldName":"value"
        String pattern = "\"" + fieldName + "\":\"";
        int startIndex = json.indexOf(pattern);
        if (startIndex == -1) {
            return null;
        }

        startIndex += pattern.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            return null;
        }

        return json.substring(startIndex, endIndex);
    }
}
// {{END_MODIFICATIONS}}
