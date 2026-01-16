package com.ym.ai_story_studio_server.mq;

import com.rabbitmq.client.Channel;
import com.ym.ai_story_studio_server.client.VectorEngineClient;
import com.ym.ai_story_studio_server.client.VectorEngineClient.ImageApiResponse;
import com.ym.ai_story_studio_server.config.AiProperties;
import com.ym.ai_story_studio_server.dto.ai.VideoGenerateRequest;
import com.ym.ai_story_studio_server.entity.Job;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.entity.CharacterLibrary;
import com.ym.ai_story_studio_server.entity.AssetRef;
import com.ym.ai_story_studio_server.entity.AssetVersion;
import com.ym.ai_story_studio_server.entity.ProjectCharacter;
import com.ym.ai_story_studio_server.entity.ProjectScene;
import com.ym.ai_story_studio_server.entity.PropLibrary;
import com.ym.ai_story_studio_server.entity.ProjectProp;
import com.ym.ai_story_studio_server.entity.SceneLibrary;
import com.ym.ai_story_studio_server.entity.ShotBinding;
import com.ym.ai_story_studio_server.mapper.AssetMapper;
import com.ym.ai_story_studio_server.mapper.AssetRefMapper;
import com.ym.ai_story_studio_server.mapper.AssetVersionMapper;
import com.ym.ai_story_studio_server.mapper.CharacterLibraryMapper;
import com.ym.ai_story_studio_server.mapper.JobMapper;
import com.ym.ai_story_studio_server.mapper.ProjectCharacterMapper;
import com.ym.ai_story_studio_server.mapper.ProjectSceneMapper;
import com.ym.ai_story_studio_server.mapper.PropLibraryMapper;
import com.ym.ai_story_studio_server.mapper.ProjectPropMapper;
import com.ym.ai_story_studio_server.mapper.SceneLibraryMapper;
import com.ym.ai_story_studio_server.mapper.ShotBindingMapper;
import com.ym.ai_story_studio_server.mapper.StoryboardShotMapper;
import com.ym.ai_story_studio_server.service.AiTextService;
import com.ym.ai_story_studio_server.service.AiVideoService;
import com.ym.ai_story_studio_server.service.AsyncVideoTaskService;
import com.ym.ai_story_studio_server.service.AssetCreationService;
import com.ym.ai_story_studio_server.service.ChargingService;
import com.ym.ai_story_studio_server.service.StorageService;
import com.ym.ai_story_studio_server.util.ImageMergeUtil;
import com.ym.ai_story_studio_server.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MQ消息消费者
 * 
 * <p>监听队列并处理任务消息
 * 
 * @author AI Story Studio
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MQConsumer {

    private final VectorEngineClient vectorEngineClient;
    private final StorageService storageService;
    private final AssetCreationService assetCreationService;
    private final ChargingService chargingService;
    private final AiVideoService aiVideoService;
    private final AsyncVideoTaskService asyncVideoTaskService;
    private final AiTextService aiTextService;
    private final ImageMergeUtil imageMergeUtil;
    private final JobMapper jobMapper;
    private final AiProperties aiProperties;
    private final AssetMapper assetMapper;
    private final AssetRefMapper assetRefMapper;
    private final AssetVersionMapper assetVersionMapper;
    private final StoryboardShotMapper storyboardShotMapper;
    private final ProjectCharacterMapper projectCharacterMapper;
    private final CharacterLibraryMapper characterLibraryMapper;
    private final ProjectSceneMapper projectSceneMapper;
    private final PropLibraryMapper propLibraryMapper;
    private final ProjectPropMapper projectPropMapper;
    private final SceneLibraryMapper sceneLibraryMapper;
    private final ShotBindingMapper shotBindingMapper;
    private final ObjectMapper objectMapper;

    /**
     * 消费批量生成分镜图任务
     */
    @RabbitListener(queues = MQConstant.QUEUE_BATCH_SHOT_IMAGE)
    public void handleBatchShotImage(BatchTaskMessage msg, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        log.info("RECEIVED message - queue: {}", MQConstant.QUEUE_BATCH_SHOT_IMAGE);
        log.info("========== 消费消息: 批量生成分镜图 ==========");
        log.info("队列: {}, jobId: {}, shotCount: {}",
                MQConstant.QUEUE_BATCH_SHOT_IMAGE, msg.getJobId(), msg.getTargetIds().size());

        try {
            executeBatchShotImageGeneration(msg);
            
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            log.info("消息确认成功 - jobId: {}", msg.getJobId());
            
        } catch (Exception e) {
            log.error("消息处理失败 - jobId: {}", msg.getJobId(), e);
            
            // 拒绝消息，不重新入队（进入死信队列）
            channel.basicNack(deliveryTag, false, false);
            
            // 更新Job状态为失败
            updateJobFailed(msg.getJobId(), e.getMessage());
        }
    }

    /**
     * 消费单个分镜图生成任务(支持自定义prompt)
     */
    @RabbitListener(queues = MQConstant.QUEUE_SINGLE_SHOT_IMAGE)
    public void handleSingleShotImage(SingleShotImageMessage msg, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        log.info("RECEIVED message - queue: {}", MQConstant.QUEUE_SINGLE_SHOT_IMAGE);
        log.info("========== 消费消息: 单个分镜图生成 ==========");
        log.info("队列: {}, jobId: {}, shotId: {}, customPrompt: {}",
                MQConstant.QUEUE_SINGLE_SHOT_IMAGE, msg.jobId(), msg.shotId(),
                msg.customPrompt() != null ? "自定义" : "默认");

        try {
            executeSingleShotImageGeneration(msg);
            
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            log.info("消息确认成功 - jobId: {}", msg.jobId());
            
        } catch (Exception e) {
            log.error("消息处理失败 - jobId: {}", msg.jobId(), e);
            
            // 拒绝消息，不重新入队（进入死信队列）
            channel.basicNack(deliveryTag, false, false);
            
            // 更新Job状态为失败
            updateJobFailed(msg.jobId(), e.getMessage());
        }
    }

    /**
     * 消费批量生成视频任务
     */
    @RabbitListener(queues = MQConstant.QUEUE_BATCH_VIDEO)
    public void handleBatchVideo(BatchTaskMessage msg, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        log.info("RECEIVED message - queue: {}", MQConstant.QUEUE_BATCH_VIDEO);
        log.info("========== 消费消息: 批量生成视频 ==========");
        log.info("队列: {}, jobId: {}, shotCount: {}",
                MQConstant.QUEUE_BATCH_VIDEO, msg.getJobId(), msg.getTargetIds().size());

        try {
            executeBatchVideoGeneration(msg);
            channel.basicAck(deliveryTag, false);
            log.info("消息确认成功 - jobId: {}", msg.getJobId());
            
        } catch (Exception e) {
            log.error("消息处理失败 - jobId: {}", msg.getJobId(), e);
            channel.basicNack(deliveryTag, false, false);
            updateJobFailed(msg.getJobId(), e.getMessage());
        }
    }

    /**
     * 消费批量生成角色画像任务
     */
    @RabbitListener(queues = MQConstant.QUEUE_BATCH_CHARACTER_IMAGE)
    public void handleBatchCharacterImage(BatchTaskMessage msg, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        log.info("RECEIVED message - queue: {}", MQConstant.QUEUE_BATCH_CHARACTER_IMAGE);
        log.info("========== 消费消息: 批量生成角色画像 ==========");
        log.info("队列: {}, jobId: {}, characterCount: {}",
                MQConstant.QUEUE_BATCH_CHARACTER_IMAGE, msg.getJobId(), msg.getTargetIds().size());

        try {
            executeBatchCharacterImageGeneration(msg);
            channel.basicAck(deliveryTag, false);
            log.info("消息确认成功 - jobId: {}", msg.getJobId());
            
        } catch (Exception e) {
            log.error("消息处理失败 - jobId: {}", msg.getJobId(), e);
            channel.basicNack(deliveryTag, false, false);
            updateJobFailed(msg.getJobId(), e.getMessage());
        }
    }

    /**
     * 消费批量生成场景画像任务
     */
    @RabbitListener(queues = MQConstant.QUEUE_BATCH_SCENE_IMAGE)
    public void handleBatchSceneImage(BatchTaskMessage msg, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        log.info("RECEIVED message - queue: {}", MQConstant.QUEUE_BATCH_SCENE_IMAGE);
        log.info("========== 消费消息: 批量生成场景画像 ==========");
        log.info("队列: {}, jobId: {}, sceneCount: {}",
                MQConstant.QUEUE_BATCH_SCENE_IMAGE, msg.getJobId(), msg.getTargetIds().size());

        try {
            executeBatchSceneImageGeneration(msg);
            channel.basicAck(deliveryTag, false);
            log.info("消息确认成功 - jobId: {}", msg.getJobId());
            
        } catch (Exception e) {
            log.error("消息处理失败 - jobId: {}", msg.getJobId(), e);
            channel.basicNack(deliveryTag, false, false);
            updateJobFailed(msg.getJobId(), e.getMessage());
        }
    }

    /**
     * 消费批量生成道具画像任务
     */
    @RabbitListener(queues = MQConstant.QUEUE_BATCH_PROP_IMAGE)
    public void handleBatchPropImage(BatchTaskMessage msg, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        log.info("RECEIVED message - queue: {}", MQConstant.QUEUE_BATCH_PROP_IMAGE);
        log.info("========== 消费消息: 批量生成道具画像 ==========");
        log.info("队列: {}, jobId: {}, propCount: {}",
                MQConstant.QUEUE_BATCH_PROP_IMAGE, msg.getJobId(), msg.getTargetIds().size());

        try {
            executeBatchPropImageGeneration(msg);
            channel.basicAck(deliveryTag, false);
            log.info("消息确认成功 - jobId: {}", msg.getJobId());
            
        } catch (Exception e) {
            log.error("消息处理失败 - jobId: {}", msg.getJobId(), e);
            channel.basicNack(deliveryTag, false, false);
            updateJobFailed(msg.getJobId(), e.getMessage());
        }
    }

    /**
     * 消费单个分镜视频生成任务
     */
    @RabbitListener(queues = MQConstant.QUEUE_SINGLE_SHOT_VIDEO)
    public void handleSingleShotVideo(SingleShotVideoMessage msg, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        log.info("RECEIVED message - queue: {}", MQConstant.QUEUE_SINGLE_SHOT_VIDEO);
        log.info("========== 消费消息: 单个分镜视频生成 ==========");
        log.info("队列: {}, jobId: {}, shotId: {}, promptLength: {}",
                MQConstant.QUEUE_SINGLE_SHOT_VIDEO, msg.getJobId(), msg.getShotId(),
                msg.getPrompt() != null ? msg.getPrompt().length() : 0);

        try {
            executeSingleShotVideoGeneration(msg);
            
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            log.info("消息确认成功 - jobId: {}", msg.getJobId());
            
        } catch (Exception e) {
            log.error("消息处理失败 - jobId: {}", msg.getJobId(), e);
            
            // 拒绝消息，不重新入队（进入死信队列）
            channel.basicNack(deliveryTag, false, false);
            
            // 更新Job状态为失败
            updateJobFailed(msg.getJobId(), e.getMessage());
        }
    }

    /**
     * 消费文本解析任务
     */
    @RabbitListener(queues = MQConstant.QUEUE_TEXT_PARSING)
    public void handleTextParsing(TextParsingMessage msg, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        log.info("RECEIVED message - queue: {}", MQConstant.QUEUE_TEXT_PARSING);
        log.info("========== 消费消息: 文本解析 ==========");
        log.info("队列: {}, jobId: {}, textLength: {}",
                MQConstant.QUEUE_TEXT_PARSING, msg.getJobId(), msg.getRawText().length());

        try {
            executeTextParsing(msg);
            channel.basicAck(deliveryTag, false);
            log.info("消息确认成功 - jobId: {}", msg.getJobId());
            
        } catch (Exception e) {
            log.error("消息处理失败 - jobId: {}", msg.getJobId(), e);
            channel.basicNack(deliveryTag, false, false);
            updateJobFailed(msg.getJobId(), e.getMessage());
        }
    }

    // ==================== 私有执行方法 ====================

    /**
     * 执行单个分镜图生成(支持自定义prompt和参考图)
     */
    private void executeSingleShotImageGeneration(SingleShotImageMessage msg) {
        Long jobId = msg.jobId();
        Long shotId = msg.shotId();
        Long userId = msg.userId();
        Long projectId = msg.projectId();
        String customPrompt = msg.customPrompt();
        List<String> referenceImageUrls = msg.referenceImageUrls();
    
        log.info("执行单个分镜图生成 - jobId: {}, shotId: {}, customPrompt: {}, referenceImageUrls: {}", 
                jobId, shotId, customPrompt != null ? "[自定义内容]" : "使用分镜剧本",
                referenceImageUrls != null ? referenceImageUrls.size() : 0);
        updateJobRunning(jobId);
    
        // 应用配置
        String finalAspectRatio = msg.aspectRatio() != null ? msg.aspectRatio() :
                aiProperties.getImage().getDefaultAspectRatio();
        String finalModel = msg.model() != null ? msg.model() :
                (aiProperties.getImage().getJimengProxyEnabled() ?
                        aiProperties.getImage().getJimengModel() :
                        aiProperties.getImage().getDefaultModel());
    
        log.info("应用配置 - aspectRatio: {}, model: {}", finalAspectRatio, finalModel);
    
        try {
            // 1. 查询分镜
            var shot = storyboardShotMapper.selectById(shotId);
            if (shot == null) {
                throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.SHOT_NOT_FOUND);
            }
    
            // 2. 准备prompt: 优先使用customPrompt，否则使用分镜剧本 + 内嵌规则
            String scriptText = shot.getScriptText() != null ? shot.getScriptText() : 
                          "为分镜生成图片 - shotId: " + shotId;
            String prompt = customPrompt != null ? customPrompt : buildShotImagePrompt(scriptText);
    
            // 日志中不显示完整的内嵌提示词，只显示用户自定义部分
            String logPrompt = customPrompt != null ? "[自定义内容]" : prompt;
            log.info("调用AI生成图片 - shotId: {}, prompt: {}, referenceImageUrls: {}", shotId, logPrompt,
                    referenceImageUrls != null ? referenceImageUrls.size() : 0);
    
            // 3. 调用AI生成图片(支持参考图)
            VectorEngineClient.ImageApiResponse apiResponse = vectorEngineClient.generateImage(
                    prompt,
                    finalModel,
                    finalAspectRatio,
                    referenceImageUrls  // 传入参考图，实现图生图
            );

            if (apiResponse == null || apiResponse.data() == null || apiResponse.data().isEmpty()) {
                throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.AI_SERVICE_ERROR, "AI返回空响应");
            }

            String imageData = apiResponse.data().get(0).url();
            log.info("AI生成成功 - shotId: {}, imageData类型: {}", shotId, 
                    isBase64(imageData) ? "base64" : "url");

            // 4. 上传到OSS
            String ossUrl = processImageAndUploadToOss(imageData, jobId, 0);
            log.info("上传OSS成功 - shotId: {}, ossUrl: {}", shotId, ossUrl);

            // 5. 保存到Asset表
            assetCreationService.createAssetWithVersion(
                    projectId,
                    "SHOT",
                    shotId,
                    "SHOT_IMG",
                    ossUrl,
                    prompt,
                    finalModel,
                    finalAspectRatio,
                    userId
            );
            log.info("Asset保存成功 - shotId: {}, ossUrl: {}", shotId, ossUrl);

            // 6. 扣积分
            Map<String, Object> metaData = new HashMap<>();
            metaData.put("model", finalModel);
            metaData.put("aspectRatio", finalAspectRatio);
            metaData.put("imageUrl", ossUrl);
            metaData.put("shotId", shotId);
            metaData.put("customPrompt", customPrompt != null);

            chargingService.charge(
                    ChargingService.ChargingRequest.builder()
                            .jobId(jobId)
                            .bizType("IMAGE_GENERATION")
                            .modelCode(finalModel)
                            .quantity(1)
                            .metaData(metaData)
                            .build()
            );
            log.info("积分扣除成功 - shotId: {}", shotId);

            // 7. 更新Job为成功，并设置resultUrl
            List<String> imageUrls = new java.util.ArrayList<>();
            imageUrls.add(ossUrl);
            updateJobSuccessWithImages(jobId, 1, 0, imageUrls);
            log.info("单个分镜图生成完成 - shotId: {}", shotId);

        } catch (Exception e) {
            log.error("单个分镜图生成失败 - shotId: {}", shotId, e);
            updateJobFailed(jobId, e.getMessage());
            throw e;
        }
    }

    /**
     * 执行批量分镜图生成
     */
    private void executeBatchShotImageGeneration(BatchTaskMessage msg) {
        Long jobId = msg.getJobId();
        List<Long> shotIds = msg.getTargetIds();
        String mode = msg.getMode();
        Integer countPerItem = msg.getCountPerItem();
        Long userId = msg.getUserId();
        Long projectId = msg.getProjectId();

        updateJobRunning(jobId);

        String finalAspectRatio = msg.getAspectRatio() != null ? msg.getAspectRatio() :
                aiProperties.getImage().getDefaultAspectRatio();
        String finalModel = msg.getModel() != null ? msg.getModel() :
                (aiProperties.getImage().getJimengProxyEnabled() ?
                        aiProperties.getImage().getJimengModel() :
                        aiProperties.getImage().getDefaultModel());

        log.info("应用配置 - aspectRatio: {}, model: {}", finalAspectRatio, finalModel);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < shotIds.size(); i++) {
            Long shotId = shotIds.get(i);
            log.info("处理分镜 [{}/{}] - shotId: {}", i + 1, shotIds.size(), shotId);

            try {
                var shot = storyboardShotMapper.selectById(shotId);
                if (shot == null) {
                    log.warn("分镜不存在,跳过 - shotId: {}", shotId);
                    failCount.incrementAndGet();
                    continue;
                }

                if ("MISSING".equals(mode)) {
                    var assetQuery = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ym.ai_story_studio_server.entity.Asset>();
                    assetQuery.eq(com.ym.ai_story_studio_server.entity.Asset::getOwnerType, "SHOT")
                              .eq(com.ym.ai_story_studio_server.entity.Asset::getOwnerId, shotId)
                              .eq(com.ym.ai_story_studio_server.entity.Asset::getAssetType, "SHOT_IMG")
                              .eq(com.ym.ai_story_studio_server.entity.Asset::getProjectId, projectId);

                    long imageCount = assetMapper.selectCount(assetQuery);
                    if (imageCount > 0) {
                        log.info("MISSING模式 - 分镜已有图片资产,跳过 - shotId: {}", shotId);
                        successCount.incrementAndGet();
                        continue;
                    }
                }

                String scriptText = shot.getScriptText() != null ? shot.getScriptText() :
                               "为分镜生成图片 - shotId: " + shotId;
                String prompt = buildShotImagePrompt(scriptText);

                // 查询分镜绑定的角色图片作为参考图
                List<String> referenceImageUrls = getBoundCharacterImages(shotId);
                log.info("分镜绑定的角色图片数量 - shotId: {}, count: {}", shotId, referenceImageUrls.size());

                // 为每个分镜生成多张图片
                for (int j = 0; j < countPerItem; j++) {
                    try {
                        // 1. 调用AI生成图片，传入角色图片作为参考图
                        log.info("调用AI生成图片 [{}/{}] - shotId: {}, prompt: {}, referenceImages: {}", 
                                j + 1, countPerItem, shotId, prompt, referenceImageUrls.size());
                        VectorEngineClient.ImageApiResponse apiResponse = vectorEngineClient.generateImage(
                                prompt,
                                finalModel,
                                finalAspectRatio,
                                referenceImageUrls  // 传入绑定的角色图片作为参考图
                        );

                        if (apiResponse == null || apiResponse.data() == null || apiResponse.data().isEmpty()) {
                            log.error("AI返回空响应 - shotId: {}", shotId);
                            throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.AI_SERVICE_ERROR, "AI返回空响应");
                        }

                        String imageData = apiResponse.data().get(0).url();
                        log.info("AI生成成功 - shotId: {}, imageData类型: {}", shotId, 
                                isBase64(imageData) ? "base64" : "url");

                        // 2. 上传到OSS
                        String ossUrl = processImageAndUploadToOss(imageData, jobId, j);
                        log.info("上传OSS成功 - shotId: {}, ossUrl: {}", shotId, ossUrl);

                        // 3. 保存到Asset表
                        assetCreationService.createAssetWithVersion(
                                projectId,
                                "SHOT",
                                shotId,
                                "SHOT_IMG",
                                ossUrl,
                                prompt,
                                finalModel,
                                finalAspectRatio,
                                userId
                        );
                        log.info("Asset保存成功 - shotId: {}, ossUrl: {}", shotId, ossUrl);

                        // 4. 扣积分（每张图片扣一次）
                        Map<String, Object> metaData = new HashMap<>();
                        metaData.put("model", finalModel);
                        metaData.put("aspectRatio", finalAspectRatio);
                        metaData.put("imageUrl", ossUrl);
                        metaData.put("shotId", shotId);

                        chargingService.charge(
                                ChargingService.ChargingRequest.builder()
                                        .jobId(jobId)
                                        .bizType("IMAGE_GENERATION")
                                        .modelCode(finalModel)
                                        .quantity(1)
                                        .metaData(metaData)
                                        .build()
                        );
                        log.info("积分扣除成功 - shotId: {}", shotId);

                    } catch (Exception e) {
                        log.error("生成单张图片失败 [{}/{}] - shotId: {}", j + 1, countPerItem, shotId, e);
                        // 单张失败不影响其他张
                    }
                }

                successCount.incrementAndGet();
                log.info("分镜图生成完成 - shotId: {}, 数量: {}", shotId, countPerItem);

            } catch (Exception e) {
                failCount.incrementAndGet();
                log.error("分镜图生成失败 - shotId: {}", shotId, e);
            }

            updateJobProgress(jobId, i + 1, shotIds.size());
        }

        log.info("批量生成分镜图完成 - 成功: {}, 失败: {}", successCount.get(), failCount.get());
        updateJobSuccess(jobId, successCount.get(), failCount.get());
    }

    private void executeBatchVideoGeneration(BatchTaskMessage msg) {
        Long jobId = msg.getJobId();
        List<Long> shotIds = msg.getTargetIds();
        Long userId = msg.getUserId();
        Long projectId = msg.getProjectId();

        updateJobRunning(jobId);

        String finalAspectRatio = msg.getAspectRatio() != null ? msg.getAspectRatio() :
                aiProperties.getVideo().getDefaultAspectRatio();
        String finalModel = msg.getModel() != null ? msg.getModel() :
                aiProperties.getVideo().getModel();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < shotIds.size(); i++) {
            Long shotId = shotIds.get(i);
            log.info("处理分镜视频 [{}/{}] - shotId: {}", i + 1, shotIds.size(), shotId);

            try {
                var shot = storyboardShotMapper.selectById(shotId);
                if (shot == null) {
                    log.warn("分镜不存在,跳过 - shotId: {}", shotId);
                    failCount.incrementAndGet();
                    continue;
                }

                String prompt = shot.getScriptText() != null ? shot.getScriptText() :
                               "为分镜生成视频 - shotId: " + shotId;

                VideoGenerateRequest request = new VideoGenerateRequest(
                        prompt,
                        finalAspectRatio,
                        aiProperties.getVideo().getDefaultDuration(),
                        null,
                        null,
                        projectId
                );

                UserContext.setUserId(userId);
                try {
                    aiVideoService.generateVideo(request);
                    log.info("分镜视频生成任务已提交 - shotId: {}", shotId);
                } finally {
                    UserContext.clear();
                }

                successCount.incrementAndGet();

            } catch (Exception e) {
                failCount.incrementAndGet();
                log.error("分镜视频生成失败 - shotId: {}", shotId, e);
            }

            updateJobProgress(jobId, i + 1, shotIds.size());
        }

        updateJobSuccess(jobId, successCount.get(), failCount.get());
    }

    /**
     * 执行单个分镜视频生成（支持自定义prompt和资产资源）
     */
    private void executeSingleShotVideoGeneration(SingleShotVideoMessage msg) {
        Long jobId = msg.getJobId();
        Long shotId = msg.getShotId();
        Long userId = msg.getUserId();
        Long projectId = msg.getProjectId();
        String prompt = msg.getPrompt();
        String aspectRatio = msg.getAspectRatio();
        Integer duration = msg.getDuration();
        String size = msg.getSize();
        String referenceImageUrl = msg.getReferenceImageUrl();

        log.info("执行单个分镜视频生成 - jobId: {}, shotId: {}, promptLength: {}, hasReference: {}",
                jobId, shotId, prompt != null ? prompt.length() : 0, referenceImageUrl != null);
        updateJobRunning(jobId);

        // 应用配置
        String finalAspectRatio = aspectRatio != null ? aspectRatio :
                aiProperties.getVideo().getDefaultAspectRatio();
        String finalModel = aiProperties.getVideo().getModel();
        Integer finalDuration = duration != null ? duration : aiProperties.getVideo().getDefaultDuration();

        log.info("应用配置 - aspectRatio: {}, model: {}, duration: {}, size: {}", finalAspectRatio, finalModel, finalDuration, size);

        try {
            // 1. 查询分镜
            var shot = storyboardShotMapper.selectById(shotId);
            if (shot == null) {
                throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.SHOT_NOT_FOUND);
            }

            // 2. 如果没有提供参考图，尝试从资产系统查询分镜当前图片
            if (referenceImageUrl == null || referenceImageUrl.isBlank()) {
                // 查询分镜当前图片资产
                AssetRef currentImageRef = assetRefMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetRef>()
                        .eq(AssetRef::getRefType, "SHOT_IMG_CURRENT")
                        .eq(AssetRef::getRefOwnerId, shotId)
                );
                
                if (currentImageRef != null) {
                    // 查询资产版本获取URL
                    AssetVersion assetVersion = assetVersionMapper.selectById(currentImageRef.getAssetVersionId());
                    if (assetVersion != null && assetVersion.getUrl() != null) {
                        referenceImageUrl = assetVersion.getUrl();
                        log.info("未提供参考图，使用分镜当前图片 - shotId: {}, imageUrl: {}", shotId, referenceImageUrl);
                    }
                }
            }

            referenceImageUrl = buildMergedReferenceImageUrl(referenceImageUrl, msg);

            // 3. 验证参考图是否存在
            if (referenceImageUrl == null || referenceImageUrl.isBlank()) {
                String errorMsg = "该分镜尚未生成图片，请先生成分镜图片后再生成视频";
                log.error(errorMsg + " - shotId: {}", shotId);
                updateJobFailed(jobId, errorMsg);
                throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.AI_SERVICE_ERROR, errorMsg);
            }

            // 4. 准备prompt
            String finalPrompt = prompt != null ? prompt :
                    (shot.getScriptText() != null ? shot.getScriptText() :
                            "为分镜生成视频 - shotId: " + shotId);

            log.info("调用AI生成视频 - shotId: {}, promptLength: {}, referenceImage: {}", 
                    shotId, finalPrompt.length(), referenceImageUrl);

            UserContext.setUserId(userId);
            try {
                VectorEngineClient.VideoApiResponse apiResponse = vectorEngineClient.generateVideo(
                        finalPrompt,
                        finalModel,
                        finalAspectRatio,
                        finalDuration,
                        size,
                        referenceImageUrl
                );

                String apiTaskId = apiResponse.id();
                log.info("视频生成任务已提交 - jobId: {}, apiTaskId: {}", jobId, apiTaskId);

                // 4. 保存taskId到metaJson
                try {
                    Map<String, Object> metaData = new HashMap<>();
                    metaData.put("model", finalModel);
                    metaData.put("aspectRatio", finalAspectRatio);
                    metaData.put("duration", finalDuration);
                    metaData.put("size", size);
                    metaData.put("prompt", finalPrompt);
                    metaData.put("apiTaskId", apiTaskId);
                    Job job = jobMapper.selectById(jobId);
                    if (job != null) {
                        job.setMetaJson(objectMapper.writeValueAsString(metaData));
                        jobMapper.updateById(job);
                    }
                } catch (Exception e) {
                    log.error("Failed to update meta_json for single shot video jobId: {}", jobId, e);
                }

                // 5. 启动异步轮询，最终结果写回同一个jobId
                asyncVideoTaskService.pollVideoGenerationTask(
                        jobId,
                        apiTaskId,
                        finalModel,
                        finalAspectRatio,
                        finalDuration,
                        userId
                );

                log.info("单个分镜视频生成任务提交完成 - shotId: {}", shotId);

            } finally {
                UserContext.clear();
            }

        } catch (Exception e) {
            log.error("单个分镜视频生成失败 - shotId: {}", shotId, e);
            updateJobFailed(jobId, e.getMessage());
            throw e;
        }
    }

    private String buildMergedReferenceImageUrl(String referenceImageUrl, SingleShotVideoMessage msg) {
        List<String> imageUrls = new ArrayList<>();

        if (isValidReferenceUrl(referenceImageUrl)) {
            imageUrls.add(referenceImageUrl);
        }

        if (msg.getScene() != null) {
            if (isValidReferenceUrl(msg.getScene().thumbnailUrl())) {
                imageUrls.add(msg.getScene().thumbnailUrl());
            } else {
                String sceneThumbnail = resolveSceneThumbnail(msg.getScene().id());
                if (isValidReferenceUrl(sceneThumbnail)) {
                    imageUrls.add(sceneThumbnail);
                }
            }
        }

        if (msg.getCharacters() != null) {
            for (var character : msg.getCharacters()) {
                if (isValidReferenceUrl(character.thumbnailUrl())) {
                    imageUrls.add(character.thumbnailUrl());
                    continue;
                }
                String characterThumbnail = resolveCharacterThumbnail(character.id());
                if (isValidReferenceUrl(characterThumbnail)) {
                    imageUrls.add(characterThumbnail);
                }
            }
        }

        if (msg.getProps() != null) {
            for (var prop : msg.getProps()) {
                if (isValidReferenceUrl(prop.thumbnailUrl())) {
                    imageUrls.add(prop.thumbnailUrl());
                    continue;
                }
                String propThumbnail = resolvePropThumbnail(prop.id());
                if (isValidReferenceUrl(propThumbnail)) {
                    imageUrls.add(propThumbnail);
                }
            }
        }

        imageUrls = imageUrls.stream().distinct().toList();

        if (imageUrls.isEmpty()) {
            return referenceImageUrl;
        }

        if (imageUrls.size() == 1) {
            return imageUrls.get(0);
        }

        try {
            byte[] mergedImageBytes = imageMergeUtil.mergeImagesHorizontally(imageUrls);
            String mergedUrl = storageService.uploadImageBytes(
                    mergedImageBytes,
                    "merged_video_ref_" + System.currentTimeMillis() + ".png"
            );
            log.info("参考图已拼接并上传: {}", mergedUrl);
            return mergedUrl;
        } catch (Exception e) {
            log.warn("参考图拼接失败，回退到首张参考图: {}", e.getMessage());
            return imageUrls.get(0);
        }
    }

    private boolean isValidReferenceUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return !url.contains("via.placeholder.com");
    }

    private String resolveSceneThumbnail(Long projectSceneId) {
        if (projectSceneId == null) {
            return null;
        }
        ProjectScene projectScene = projectSceneMapper.selectById(projectSceneId);
        if (projectScene == null) {
            return null;
        }
        if (projectScene.getLibrarySceneId() == null) {
            return null;
        }
        SceneLibrary sceneLibrary = sceneLibraryMapper.selectById(projectScene.getLibrarySceneId());
        return sceneLibrary != null ? sceneLibrary.getThumbnailUrl() : null;
    }

    private String resolveCharacterThumbnail(Long projectCharacterId) {
        if (projectCharacterId == null) {
            return null;
        }
        ProjectCharacter projectCharacter = projectCharacterMapper.selectById(projectCharacterId);
        if (projectCharacter == null) {
            return null;
        }
        if (isValidReferenceUrl(projectCharacter.getThumbnailUrl())) {
            return projectCharacter.getThumbnailUrl();
        }
        if (projectCharacter.getLibraryCharacterId() == null) {
            return null;
        }
        CharacterLibrary characterLibrary = characterLibraryMapper.selectById(projectCharacter.getLibraryCharacterId());
        return characterLibrary != null ? characterLibrary.getThumbnailUrl() : null;
    }

    private String resolvePropThumbnail(Long projectPropId) {
        if (projectPropId == null) {
            return null;
        }
        ProjectProp projectProp = projectPropMapper.selectById(projectPropId);
        if (projectProp == null) {
            return null;
        }
        if (projectProp.getLibraryPropId() == null) {
            return null;
        }
        PropLibrary propLibrary = propLibraryMapper.selectById(projectProp.getLibraryPropId());
        return propLibrary != null ? propLibrary.getThumbnailUrl() : null;
    }

    private void executeBatchCharacterImageGeneration(BatchTaskMessage msg) {
        Long jobId = msg.getJobId();
        List<Long> characterIds = msg.getTargetIds(); // 这是 project_character 的 ID
        Long userId = msg.getUserId();
        Long projectId = msg.getProjectId();

        log.info("执行批量角色画像生成 - jobId: {}, characterCount: {}", jobId, characterIds.size());
        updateJobRunning(jobId);

        String finalAspectRatio = msg.getAspectRatio() != null ? msg.getAspectRatio() : "1:1";
        String finalModel = msg.getModel() != null ? msg.getModel() :
                (aiProperties.getImage().getJimengProxyEnabled() ?
                        aiProperties.getImage().getJimengModel() :
                        aiProperties.getImage().getDefaultModel());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        // 收集所有生成的图片URL（用于Job的allImageUrls）
        List<String> allGeneratedImageUrls = new java.util.ArrayList<>();

        for (int i = 0; i < characterIds.size(); i++) {
            Long projectCharacterId = characterIds.get(i);

            try {
                // 1. 查询项目角色
                ProjectCharacter projectCharacter = projectCharacterMapper.selectById(projectCharacterId);
                if (projectCharacter == null) {
                    log.warn("项目角色不存在 - projectCharacterId: {}", projectCharacterId);
                    failCount.incrementAndGet();
                    continue;
                }

                // 2. 查询角色库中的角色（可能为NULL，支持自定义角色）
                CharacterLibrary character = null;
                if (projectCharacter.getLibraryCharacterId() != null) {
                    character = characterLibraryMapper.selectById(projectCharacter.getLibraryCharacterId());
                }
                
                // 判断是否为自定义角色（未关联角色库）
                boolean isCustomCharacter = (character == null);
                log.info("角色类型 - projectCharacterId: {}, 自定义角色: {}", projectCharacterId, isCustomCharacter);

                // 3. MISSING模式：检查是否已有图片
                String existingThumbnail = isCustomCharacter ? 
                        projectCharacter.getThumbnailUrl() : character.getThumbnailUrl();
                if ("MISSING".equals(msg.getMode()) && existingThumbnail != null && !existingThumbnail.isEmpty()) {
                    log.info("MISSING模式 - 角色已有图片,跳过 - projectCharacterId: {}", projectCharacterId);
                    successCount.incrementAndGet();
                    continue;
                }

                // 4. 构建提示词：基于AI分析的描述生成角色立绘
                // 内嵌规则：提取角色的年龄、性别、外貌、服装
                String characterName;
                String description;
                if (isCustomCharacter) {
                    // 自定义角色：使用项目角色的信息
                    characterName = projectCharacter.getDisplayName() != null ? 
                            projectCharacter.getDisplayName() : "未命名角色";
                    description = projectCharacter.getOverrideDescription();
                } else {
                    // 关联角色库：优先使用覆盖描述
                    characterName = projectCharacter.getDisplayName() != null ?
                            projectCharacter.getDisplayName() : character.getName();
                    description = projectCharacter.getOverrideDescription() != null ?
                            projectCharacter.getOverrideDescription() : character.getDescription();
                }
                
                // 构建优化的提示词：强调角色特征（年龄、性别、外貌、服装）
                String prompt;
                if (description != null && !description.trim().isEmpty()) {
                    // 有AI分析描述：直接使用描述作为主要提示词
                    prompt = String.format("角色立绘，%s，2D动漫风格，高质量，精细绘制，全身像，正面站立，面向镜头",
                            description.trim());
                } else {
                    // 无描述：使用角色名称
                    prompt = String.format("角色立绘，%s，2D动漫风格，高质量，精细绘制，全身像，正面站立",
                            characterName);
                }

                log.info("生成角色图片 - projectCharacterId: {}, name: {}, prompt: {}", projectCharacterId, characterName, prompt);

                // 5. 调用向量引擎生成图片
                UserContext.setUserId(userId);
                try {
                    ImageApiResponse response = vectorEngineClient.generateImage(
                            prompt,
                            finalModel,
                            finalAspectRatio,
                            Collections.emptyList()  // 无参考图片
                    );

                    // 6. 解析图片结果
                    if (response == null || response.data() == null || response.data().isEmpty()) {
                        throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.AI_SERVICE_ERROR, "图片生成结果为空");
                    }

                    // 获取所有返回的图片数据（即梦模型返回4张图片）
                    List<ImageApiResponse.ImageData> allResults = response.data();
                    log.info("AI返回 {} 张图片 - projectCharacterId: {}", allResults.size(), projectCharacterId);

                    // 7. 处理所有图片并上传到OSS
                    List<String> ossUrls = new java.util.ArrayList<>();
                    for (int j = 0; j < allResults.size(); j++) {
                        String imageData = allResults.get(j).url();
                        if (imageData == null) {
                            log.warn("第 {} 张图片数据为空,跳过", j + 1);
                            continue;
                        }
                        String ossUrl = processImageAndUploadToOss(imageData, jobId, i * 10 + j);
                        ossUrls.add(ossUrl);
                        allGeneratedImageUrls.add(ossUrl);
                        log.info("上传图片 [{}/{}] 到OSS成功 - ossUrl: {}", j + 1, allResults.size(), ossUrl);
                    }

                    if (ossUrls.isEmpty()) {
                        throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.AI_SERVICE_ERROR, "无法获取有效的图片数据");
                    }

                    // 8. 使用第一张图片更新缩略图URL
                    String primaryOssUrl = ossUrls.get(0);
                    if (isCustomCharacter) {
                        // 自定义角色：保存到项目角色表
                        projectCharacter.setThumbnailUrl(primaryOssUrl);
                        projectCharacterMapper.updateById(projectCharacter);
                        log.info("自定义角色图片保存成功 - projectCharacterId: {}, ossUrl: {}", projectCharacterId, primaryOssUrl);
                    } else {
                        // 关联角色库：保存到角色库表
                        character.setThumbnailUrl(primaryOssUrl);
                        characterLibraryMapper.updateById(character);
                        log.info("角色库图片更新成功 - characterId: {}, ossUrl: {}", character.getId(), primaryOssUrl);
                    }

                    log.info("角色图片生成成功 - projectCharacterId: {}, 总图片数: {}, 主图: {}", 
                            projectCharacterId, ossUrls.size(), primaryOssUrl);

                    // 9. 扣除积分（按批次扣费，不按图片张数）
                    Map<String, Object> metaData = new HashMap<>();
                    metaData.put("projectCharacterId", projectCharacterId);
                    metaData.put("characterName", characterName);
                    metaData.put("isCustomCharacter", isCustomCharacter);
                    metaData.put("model", finalModel);
                    metaData.put("imageCount", ossUrls.size());
                    metaData.put("allImageUrls", ossUrls);

                    chargingService.charge(
                            ChargingService.ChargingRequest.builder()
                                    .jobId(jobId)
                                    .bizType("IMAGE_GENERATION")
                                    .modelCode(finalModel)
                                    .quantity(1)  // 按批次扣费
                                    .metaData(metaData)
                                    .build()
                    );

                    successCount.incrementAndGet();

                } finally {
                    UserContext.clear();
                }

            } catch (Exception e) {
                failCount.incrementAndGet();
                log.error("角色图片生成失败 - projectCharacterId: {}", projectCharacterId, e);
            }

            updateJobProgress(jobId, i + 1, characterIds.size());
        }

        log.info("批量生成角色画像完成 - 成功: {}, 失败: {}, 总图片数: {}", 
                successCount.get(), failCount.get(), allGeneratedImageUrls.size());
        
        // 更新Job状态并保存所有图片URL到metaJson
        updateJobSuccessWithImages(jobId, successCount.get(), failCount.get(), allGeneratedImageUrls);
    }

    private void executeBatchSceneImageGeneration(BatchTaskMessage msg) {
        Long jobId = msg.getJobId();
        List<Long> sceneIds = msg.getTargetIds(); // 这是 project_scene 的 ID
        Long userId = msg.getUserId();
        Long projectId = msg.getProjectId();

        log.info("执行批量场景画像生成 - jobId: {}, sceneCount: {}", jobId, sceneIds.size());
        updateJobRunning(jobId);

        String finalAspectRatio = msg.getAspectRatio() != null ? msg.getAspectRatio() : "16:9";
        String finalModel = msg.getModel() != null ? msg.getModel() :
                (aiProperties.getImage().getJimengProxyEnabled() ?
                        aiProperties.getImage().getJimengModel() :
                        aiProperties.getImage().getDefaultModel());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < sceneIds.size(); i++) {
            Long projectSceneId = sceneIds.get(i);

            try {
                // 1. 查询项目场景
                ProjectScene projectScene = projectSceneMapper.selectById(projectSceneId);
                if (projectScene == null) {
                    log.warn("项目场景不存在 - projectSceneId: {}", projectSceneId);
                    failCount.incrementAndGet();
                    continue;
                }

                // 2. 查询场景库中的场景
                SceneLibrary scene = sceneLibraryMapper.selectById(projectScene.getLibrarySceneId());
                if (scene == null) {
                    log.warn("场景库场景不存在 - librarySceneId: {}", projectScene.getLibrarySceneId());
                    failCount.incrementAndGet();
                    continue;
                }

                // 3. MISSING模式：检查是否已有图片
                if ("MISSING".equals(msg.getMode()) && scene.getThumbnailUrl() != null) {
                    log.info("MISSING模式 - 场景已有图片,跳过 - sceneId: {}", scene.getId());
                    successCount.incrementAndGet();
                    continue;
                }

                // 4. 构建提示词：使用场景描述生成场景图
                String description = projectScene.getOverrideDescription() != null ?
                        projectScene.getOverrideDescription() : scene.getDescription();
                String sceneName = projectScene.getDisplayName() != null ?
                        projectScene.getDisplayName() : scene.getName();
                String prompt = String.format("纯场景背景图，%s，%s，2D动漫风格，高质量高清，画质细腻，空无一人的场景，禁止出现任何人物、角色、人影、动物，只有纯背景环境",
                        sceneName, description != null ? description : "");

                log.info("生成场景图片 - sceneId: {}, name: {}, prompt: {}", scene.getId(), sceneName, prompt);

                // 5. 调用向量引擎生成图片
                UserContext.setUserId(userId);
                try {
                    ImageApiResponse response = vectorEngineClient.generateImage(
                            prompt,
                            finalModel,
                            finalAspectRatio,
                            Collections.emptyList()  // 无参考图片
                    );

                    // 6. 解析图片结果
                    if (response == null || response.data() == null || response.data().isEmpty()) {
                        throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.AI_SERVICE_ERROR, "图片生成结果为空");
                    }

                    // 获取图片URL或base64
                    ImageApiResponse.ImageData firstResult = response.data().get(0);
                    String imageData = firstResult.url();

                    if (imageData == null) {
                        throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.AI_SERVICE_ERROR, "无法获取图片数据");
                    }

                    // 7. 上传到OSS
                    String ossUrl = processImageAndUploadToOss(imageData, jobId, i);

                    // 8. 更新场景库的缩略图URL
                    scene.setThumbnailUrl(ossUrl);
                    sceneLibraryMapper.updateById(scene);

                    log.info("场景图片生成成功 - sceneId: {}, ossUrl: {}", scene.getId(), ossUrl);

                    // 9. 扣除积分
                    Map<String, Object> metaData = new HashMap<>();
                    metaData.put("sceneId", scene.getId());
                    metaData.put("sceneName", sceneName);
                    metaData.put("model", finalModel);

                    chargingService.charge(
                            ChargingService.ChargingRequest.builder()
                                    .jobId(jobId)
                                    .bizType("IMAGE_GENERATION")
                                    .modelCode(finalModel)
                                    .quantity(1)
                                    .metaData(metaData)
                                    .build()
                    );

                    successCount.incrementAndGet();

                } finally {
                    UserContext.clear();
                }

            } catch (Exception e) {
                failCount.incrementAndGet();
                log.error("场景图片生成失败 - projectSceneId: {}", projectSceneId, e);
            }

            updateJobProgress(jobId, i + 1, sceneIds.size());
        }

        log.info("批量生成场景画像完成 - 成功: {}, 失败: {}", successCount.get(), failCount.get());
        updateJobSuccess(jobId, successCount.get(), failCount.get());
    }

    private void executeBatchPropImageGeneration(BatchTaskMessage msg) {
        Long jobId = msg.getJobId();
        List<Long> propIds = msg.getTargetIds(); // 这是 project_prop 的 ID
        Long userId = msg.getUserId();
        Long projectId = msg.getProjectId();

        log.info("执行批量道具画像生成 - jobId: {}, propCount: {}", jobId, propIds.size());
        updateJobRunning(jobId);

        String finalAspectRatio = msg.getAspectRatio() != null ? msg.getAspectRatio() : "1:1";
        String finalModel = msg.getModel() != null ? msg.getModel() :
                (aiProperties.getImage().getJimengProxyEnabled() ?
                        aiProperties.getImage().getJimengModel() :
                        aiProperties.getImage().getDefaultModel());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        // 收集所有生成的图片URL（用于Job的allImageUrls）
        List<String> allGeneratedImageUrls = new java.util.ArrayList<>();

        for (int i = 0; i < propIds.size(); i++) {
            Long projectPropId = propIds.get(i);

            try {
                // 1. 查询项目道具
                ProjectProp projectProp = projectPropMapper.selectById(projectPropId);
                if (projectProp == null) {
                    log.warn("项目道具不存在 - projectPropId: {}", projectPropId);
                    failCount.incrementAndGet();
                    continue;
                }

                // 2. 查询道具库中的道具
                PropLibrary prop = propLibraryMapper.selectById(projectProp.getLibraryPropId());
                if (prop == null) {
                    log.warn("道具库道具不存在 - libraryPropId: {}", projectProp.getLibraryPropId());
                    failCount.incrementAndGet();
                    continue;
                }

                // 3. MISSING模式：检查是否已有图片
                if ("MISSING".equals(msg.getMode()) && prop.getThumbnailUrl() != null) {
                    log.info("MISSING模式 - 道具已有图片,跳过 - propId: {}", prop.getId());
                    successCount.incrementAndGet();
                    continue;
                }

                // 4. 构建提示词：使用道具描述生成道具图
                String description = projectProp.getOverrideDescription() != null ?
                        projectProp.getOverrideDescription() : prop.getDescription();
                String propName = projectProp.getDisplayName() != null ?
                        projectProp.getDisplayName() : prop.getName();
                String prompt = String.format("道具画像，%s，%s，2D动漫风格，高质量高清，画质细腻，白色背景，单个物件",
                        propName, description != null ? description : "");

                log.info("生成道具图片 - propId: {}, name: {}, prompt: {}", prop.getId(), propName, prompt);

                // 5. 调用向量引擎生成图片
                UserContext.setUserId(userId);
                try {
                    ImageApiResponse response = vectorEngineClient.generateImage(
                            prompt,
                            finalModel,
                            finalAspectRatio,
                            Collections.emptyList()  // 无参考图片
                    );

                    // 6. 解析图片结果
                    if (response == null || response.data() == null || response.data().isEmpty()) {
                        throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.AI_SERVICE_ERROR, "图片生成结果为空");
                    }

                    // 获取所有返回的图片数据（即梦模型返回4张图片）
                    List<ImageApiResponse.ImageData> allResults = response.data();
                    log.info("AI返回 {} 张图片 - propId: {}", allResults.size(), prop.getId());

                    // 7. 处理所有图片并上传到OSS
                    List<String> ossUrls = new java.util.ArrayList<>();
                    for (int j = 0; j < allResults.size(); j++) {
                        String imageData = allResults.get(j).url();
                        if (imageData == null) {
                            log.warn("第 {} 张图片数据为空,跳过", j + 1);
                            continue;
                        }
                        String ossUrl = processImageAndUploadToOss(imageData, jobId, i * 10 + j);
                        ossUrls.add(ossUrl);
                        allGeneratedImageUrls.add(ossUrl);
                        log.info("上传图片 [{}/{}] 到OSS成功 - ossUrl: {}", j + 1, allResults.size(), ossUrl);
                    }

                    if (ossUrls.isEmpty()) {
                        throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.AI_SERVICE_ERROR, "无法获取有效的图片数据");
                    }

                    // 8. 使用第一张图片更新道具库的缩略图URL
                    String primaryOssUrl = ossUrls.get(0);
                    prop.setThumbnailUrl(primaryOssUrl);
                    propLibraryMapper.updateById(prop);

                    log.info("道具图片生成成功 - propId: {}, 总图片数: {}, 主图: {}", 
                            prop.getId(), ossUrls.size(), primaryOssUrl);

                    // 9. 扣除积分（按批次扣费，不按图片张数）
                    Map<String, Object> metaData = new HashMap<>();
                    metaData.put("propId", prop.getId());
                    metaData.put("propName", propName);
                    metaData.put("model", finalModel);
                    metaData.put("imageCount", ossUrls.size());
                    metaData.put("allImageUrls", ossUrls);

                    chargingService.charge(
                            ChargingService.ChargingRequest.builder()
                                    .jobId(jobId)
                                    .bizType("IMAGE_GENERATION")
                                    .modelCode(finalModel)
                                    .quantity(1)  // 按批次扣费
                                    .metaData(metaData)
                                    .build()
                    );

                    successCount.incrementAndGet();

                } finally {
                    UserContext.clear();
                }

            } catch (Exception e) {
                failCount.incrementAndGet();
                log.error("道具图片生成失败 - projectPropId: {}", projectPropId, e);
            }

            updateJobProgress(jobId, i + 1, propIds.size());
        }

        log.info("批量生成道具画像完成 - 成功: {}, 失败: {}, 总图片数: {}", 
                successCount.get(), failCount.get(), allGeneratedImageUrls.size());
        
        // 更新Job状态并保存所有图片URL到metaJson
        updateJobSuccessWithImages(jobId, successCount.get(), failCount.get(), allGeneratedImageUrls);
    }

    private void executeTextParsing(TextParsingMessage msg) {
        log.info("执行文本解析 - jobId: {}", msg.getJobId());
        updateJobRunning(msg.getJobId());
        
        UserContext.setUserId(msg.getUserId());
        try {
            aiTextService.generateText(new com.ym.ai_story_studio_server.dto.ai.TextGenerateRequest(
                msg.getRawText(), null, null, msg.getProjectId()
            ));
            updateJobSuccess(msg.getJobId(), 1, 0);
        } finally {
            UserContext.clear();
        }
    }

    // ==================== Job状态更新方法 ====================

    private void updateJobRunning(Long jobId) {
        Job job = new Job();
        job.setId(jobId);
        job.setStatus("RUNNING");
        jobMapper.updateById(job);
        log.info("Job状态更新为RUNNING - jobId: {}", jobId);
    }

    private void updateJobProgress(Long jobId, Integer doneItems, Integer totalItems) {
        Job job = new Job();
        job.setId(jobId);
        job.setDoneItems(doneItems);
        job.setProgress((int) Math.round((doneItems * 100.0) / totalItems));
        jobMapper.updateById(job);
        log.debug("Job进度更新 - jobId: {}, progress: {}%", jobId, job.getProgress());
    }

    private void updateJobSuccess(Long jobId, Integer successCount, Integer failCount) {
        if (successCount == 0 && failCount > 0) {
            updateJobFailedWithCounts(jobId, successCount, failCount, "All items failed");
            return;
        }

        Job job = new Job();
        job.setId(jobId);
        job.setStatus("SUCCEEDED");
        job.setDoneItems(successCount + failCount);
        job.setProgress(100);
        // 将结果摘要存储在metaJson中
        String metaJson = String.format("{\"successCount\": %d, \"failCount\": %d}", successCount, failCount);
        job.setMetaJson(metaJson);
        jobMapper.updateById(job);
        log.info("Job状态更新为SUCCEEDED - jobId: {}, 成功: {}, 失败: {}", jobId, successCount, failCount);
    }

    /**
     * 更新Job状态为成功，并保存所有生成的图片URL
     *
     * @param jobId 任务ID
     * @param successCount 成功数量
     * @param failCount 失败数量
     * @param allImageUrls 所有生成的图片URL列表
     */
    private void updateJobSuccessWithImages(Long jobId, Integer successCount, Integer failCount, List<String> allImageUrls) {
        if (successCount == 0 && failCount > 0) {
            updateJobFailedWithCounts(jobId, successCount, failCount, "All items failed");
            return;
        }

        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            log.error("任务不存在 - jobId: {}", jobId);
            return;
        }
        
        job.setStatus("SUCCEEDED");
        job.setDoneItems(successCount + failCount);
        job.setProgress(100);
        
        // 设置第一张图片为resultUrl
        if (!allImageUrls.isEmpty()) {
            job.setResultUrl(allImageUrls.get(0));
        }
        
        // 将所有图片URL保存到metaJson
        try {
            Map<String, Object> metaData = new HashMap<>();
            metaData.put("successCount", successCount);
            metaData.put("failCount", failCount);
            metaData.put("allImageUrls", allImageUrls);
            metaData.put("imageCount", allImageUrls.size());
            job.setMetaJson(objectMapper.writeValueAsString(metaData));
        } catch (Exception e) {
            log.error("序列化metaJson失败", e);
            job.setMetaJson(String.format("{\"successCount\": %d, \"failCount\": %d, \"imageCount\": %d}", 
                    successCount, failCount, allImageUrls.size()));
        }
        
        jobMapper.updateById(job);
        log.info("Job状态更新为SUCCEEDED(带图片) - jobId: {}, 成功: {}, 失败: {}, 总图片数: {}", 
                jobId, successCount, failCount, allImageUrls.size());
    }

    private void updateJobFailed(Long jobId, String errorMessage) {
        Job job = new Job();
        job.setId(jobId);
        job.setStatus("FAILED");
        job.setErrorMessage(errorMessage);
        jobMapper.updateById(job);
        log.error("Job状态更新为FAILED - jobId: {}, error: {}", jobId, errorMessage);
    }

    private void updateJobFailedWithCounts(Long jobId, Integer successCount, Integer failCount, String errorMessage) {
        Job job = new Job();
        job.setId(jobId);
        job.setStatus("FAILED");
        job.setDoneItems(successCount + failCount);
        job.setProgress(100);
        job.setErrorMessage(errorMessage);
        jobMapper.updateById(job);
        log.error("Job状态更新为FAILED - jobId: {}, success: {}, fail: {}, error: {}", jobId, successCount, failCount, errorMessage);
    }

    // ==================== 图片处理辅助方法 ====================

    /**
     * 处理图片并上传到OSS
     *
     * @param imageData 图片数据（base64或URL）
     * @param jobId 任务ID
     * @param index 图片索引
     * @return OSS存储的URL
     */
    private String processImageAndUploadToOss(String imageData, Long jobId, int index) {
        if (isBase64(imageData)) {
            log.debug("检测到base64图片 - index: {}", index);
            return uploadBase64ToOss(imageData, jobId, index);
        }

        if (isUrl(imageData)) {
            log.debug("检测到URL图片 - index: {}, url: {}", index, imageData);
            return downloadAndUploadToOss(imageData, jobId, index);
        }

        throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.PARAM_INVALID, "无法识别的图片数据格式");
    }

    /**
     * 上传base64图片到OSS
     */
    private String uploadBase64ToOss(String base64Data, Long jobId, int index) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            InputStream inputStream = new ByteArrayInputStream(imageBytes);
            String fileName = String.format("ai_image_%d_%d.png", jobId, index);
            String ossUrl = storageService.upload(inputStream, fileName, "image/png");
            log.debug("Base64图片上传成功 - index: {}, ossUrl: {}", index, ossUrl);
            return ossUrl;
        } catch (Exception e) {
            log.error("Base64图片上传失败", e);
            throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.OSS_ERROR, "base64图片上传失败: " + e.getMessage());
        }
    }

    /**
     * 下URL下载图片并上传到OSS
     */
    private String downloadAndUploadToOss(String imageUrl, Long jobId, int index) {
        try {
            URL url = new URL(imageUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);

            String contentType = connection.getContentType();
            if (contentType == null) {
                contentType = "image/jpeg";
            }

            String extension = getExtensionFromContentType(contentType);
            String fileName = String.format("ai_image_%d_%d%s", jobId, index, extension);

            try (InputStream inputStream = connection.getInputStream()) {
                String ossUrl = storageService.upload(inputStream, fileName, contentType);
                log.debug("URL图片下载并上传成功 - ossUrl: {}", ossUrl);
                return ossUrl;
            }
        } catch (Exception e) {
            log.error("图片下载或上传失败 - url: {}", imageUrl, e);
            throw new BusinessException(com.ym.ai_story_studio_server.common.ResultCode.OSS_ERROR, "图片下载失败: " + e.getMessage());
        }
    }

    /**
     * 判断是否为base64
     */
    private boolean isBase64(String data) {
        if (data == null || data.isBlank()) {
            return false;
        }
        return data.length() > 100
                && !data.startsWith("http://")
                && !data.startsWith("https://");
    }

    /**
     * 判断是否为URL
     */
    private boolean isUrl(String data) {
        if (data == null || data.isBlank()) {
            return false;
        }
        return data.startsWith("http://") || data.startsWith("https://");
    }

    /**
     * 根据ContentType获取文件扩展名
     */
    private String getExtensionFromContentType(String contentType) {
        if (contentType == null) {
            return ".jpg";
        }
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }

    // ==================== 分镜图生成内嵌规则 ====================

    /**
     * 分镜图生成的内嵌规则前缀
     * 指定2D动漫风格，确保生成的图片风格一致
     */
    private static final String SHOT_IMAGE_PROMPT_PREFIX = 
            "根据参考图的设定，使用参考图中的角色、场景、道具，" +
            "运用合理的构建分镜，合理的动作，合理的运镜，合理的环境渲染，" +
            "发散你的想象力，生成保持风格一致性的2D动漫图片，" +
            "要求线条细致，人物画风保持与参考图一致，" +
            "清晰不模糊，颜色鲜艳，光影效果，超清画质，电影级镜头（cinematic dynamic camera），" +
            "请忠实原文，不增加原文没有的内容，不减少原文包含的信息。" +
            "分镜要求如下：";

    /**
     * 构建分镜图生成的完整提示词
     * 
     * @param scriptText 分镜剧本文本
     * @return 带风格前缀的完整提示词
     */
    private String buildShotImagePrompt(String scriptText) {
        return SHOT_IMAGE_PROMPT_PREFIX + scriptText;
    }

    // ==================== 分镜绑定角色图片查询 ====================

    /**
     * 获取分镜绑定的角色图片URL列表
     * 
     * <p>查询逻辑：
     * <ol>
     *   <li>查询shot_bindings表，找到shot_id=分镜ID且bind_type=PCHAR的记录</li>
     *   <li>获取项目角色的缩略图URL</li>
     *   <li>如果项目角色没有缩略图，查询角色库的缩略图</li>
     *   <li>如果仍无缩略图，查询角色的资产版本</li>
     * </ol>
     * 
     * @param shotId 分镜ID
     * @return 角色图片URL列表
     */
    private List<String> getBoundCharacterImages(Long shotId) {
        List<String> imageUrls = new java.util.ArrayList<>();
        
        try {
            // 1. 查询分镜绑定的角色（bind_type=PCHAR）
            var bindingQuery = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShotBinding>();
            bindingQuery.eq(ShotBinding::getShotId, shotId)
                       .eq(ShotBinding::getBindType, "PCHAR");
            List<ShotBinding> bindings = shotBindingMapper.selectList(bindingQuery);
            
            if (bindings.isEmpty()) {
                log.debug("分镜未绑定角色 - shotId: {}", shotId);
                return imageUrls;
            }
            
            // 2. 获取项目角色ID列表
            List<Long> characterIds = bindings.stream()
                    .map(ShotBinding::getBindId)
                    .collect(java.util.stream.Collectors.toList());
            
            // 3. 批量查询项目角色
            List<ProjectCharacter> characters = projectCharacterMapper.selectBatchIds(characterIds);
            
            // 4. 获取关联的角色库ID，用于查询库缩略图
            List<Long> libraryCharacterIds = characters.stream()
                    .map(ProjectCharacter::getLibraryCharacterId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            
            Map<Long, String> libraryThumbnailMap = new java.util.HashMap<>();
            if (!libraryCharacterIds.isEmpty()) {
                List<CharacterLibrary> libraryCharacters = characterLibraryMapper.selectBatchIds(libraryCharacterIds);
                libraryThumbnailMap = libraryCharacters.stream()
                        .filter(c -> c.getThumbnailUrl() != null && !c.getThumbnailUrl().isEmpty())
                        .collect(java.util.stream.Collectors.toMap(CharacterLibrary::getId, CharacterLibrary::getThumbnailUrl));
            }
            
            // 5. 遍历每个角色，获取图片URL
            for (ProjectCharacter character : characters) {
                String thumbnailUrl = null;
                
                // 优先级：项目角色缩略图 > 角色库缩略图 > 角色资产
                if (character.getThumbnailUrl() != null && !character.getThumbnailUrl().isEmpty()) {
                    thumbnailUrl = character.getThumbnailUrl();
                    log.debug("使用项目角色缩略图 - characterId: {}, url: {}", character.getId(), thumbnailUrl);
                } else if (character.getLibraryCharacterId() != null) {
                    thumbnailUrl = libraryThumbnailMap.get(character.getLibraryCharacterId());
                    if (thumbnailUrl != null) {
                        log.debug("使用角色库缩略图 - characterId: {}, libraryId: {}, url: {}", 
                                character.getId(), character.getLibraryCharacterId(), thumbnailUrl);
                    }
                }
                
                // 如果还没有缩略图，查询角色资产
                if (thumbnailUrl == null) {
                    thumbnailUrl = getCharacterAssetUrl(character.getId());
                    if (thumbnailUrl != null) {
                        log.debug("使用角色资产 - characterId: {}, url: {}", character.getId(), thumbnailUrl);
                    }
                }
                
                if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                    imageUrls.add(thumbnailUrl);
                }
            }
            
            log.info("获取分镜绑定角色图片完成 - shotId: {}, characterCount: {}, imageCount: {}", 
                    shotId, characters.size(), imageUrls.size());
            
        } catch (Exception e) {
            log.error("获取分镜绑定角色图片失败 - shotId: {}", shotId, e);
        }
        
        return imageUrls;
    }
    
    /**
     * 获取角色的资产图URL
     * 
     * @param characterId 项目角色ID
     * @return 资产图URL，不存在返回null
     */
    private String getCharacterAssetUrl(Long characterId) {
        try {
            // 查询角色的图片资产
            var assetQuery = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ym.ai_story_studio_server.entity.Asset>();
            assetQuery.eq(com.ym.ai_story_studio_server.entity.Asset::getOwnerType, "PCHAR")
                     .eq(com.ym.ai_story_studio_server.entity.Asset::getOwnerId, characterId)
                     .eq(com.ym.ai_story_studio_server.entity.Asset::getAssetType, "CHAR_IMG")
                     .orderByDesc(com.ym.ai_story_studio_server.entity.Asset::getCreatedAt)
                     .last("LIMIT 1");
            
            var asset = assetMapper.selectOne(assetQuery);
            if (asset == null) {
                return null;
            }
            
            // 查询资产的最新版本
            var versionQuery = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetVersion>();
            versionQuery.eq(AssetVersion::getAssetId, asset.getId())
                       .eq(AssetVersion::getStatus, "READY")
                       .orderByDesc(AssetVersion::getVersionNo)
                       .last("LIMIT 1");
            
            AssetVersion version = assetVersionMapper.selectOne(versionQuery);
            return version != null ? version.getUrl() : null;
            
        } catch (Exception e) {
            log.error("查询角色资产失败 - characterId: {}", characterId, e);
            return null;
        }
    }
}
