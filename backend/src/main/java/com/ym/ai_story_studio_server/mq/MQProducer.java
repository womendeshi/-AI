package com.ym.ai_story_studio_server.mq;

import com.ym.ai_story_studio_server.dto.ai.ShotVideoGenerateRequest.AssetResource;
import com.ym.ai_story_studio_server.mq.BatchTaskMessage;
import com.ym.ai_story_studio_server.mq.TextParsingMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MQ消息生产者
 * 
 * <p>负责发送各类任务消息到RabbitMQ
 * 
 * @author AI Story Studio
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MQProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送批量生成分镜图任务
     * 
     * @param jobId 任务ID
     * @param userId 用户ID
     * @param projectId 项目ID
     * @param shotIds 分镜ID列表
     * @param mode 生成模式
     * @param countPerItem 每个目标生成数量
     * @param aspectRatio 画幅比例
     * @param model 模型名称
     */
    public void sendBatchShotImageTask(Long jobId, Long userId, Long projectId, List<Long> shotIds,
                                       String mode, Integer countPerItem, String aspectRatio, String model) {
        BatchTaskMessage message = new BatchTaskMessage(
                jobId, userId, projectId, shotIds, mode, countPerItem, aspectRatio, model
        );
        
        log.info("发送消息 - 交换机: {}, 路由键: {}, jobId: {}, shotCount: {}", 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_BATCH_SHOT_IMAGE,
                jobId, shotIds.size());
        
        rabbitTemplate.convertAndSend(
                MQConstant.EXCHANGE_BUSINESS,
                MQConstant.ROUTING_KEY_BATCH_SHOT_IMAGE,
                message
        );
    }

    /**
     * 发送单个分镜图生成任务(支持自定义prompt和参考图)
     * 
     * @param jobId 任务ID
     * @param userId 用户ID
     * @param projectId 项目ID
     * @param shotId 分镜ID
     * @param aspectRatio 画幅比例
     * @param model 模型名称
     * @param customPrompt 自定义提示词(可选)
     * @param referenceImageUrls 参考图URL列表(可选)
     */
    public void sendSingleShotImageTask(Long jobId, Long userId, Long projectId, Long shotId,
                                        String aspectRatio, String model, String customPrompt, List<String> referenceImageUrls) {
        SingleShotImageMessage message = new SingleShotImageMessage(
                jobId, userId, projectId, shotId, aspectRatio, model, customPrompt, referenceImageUrls
        );
        
        log.info("发送消息 - 交换机: {}, 路由键: {}, jobId: {}, shotId: {}, customPrompt: {}", 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_SINGLE_SHOT_IMAGE,
                jobId, shotId, customPrompt != null ? "自定义" : "默认");
        
        rabbitTemplate.convertAndSend(
                MQConstant.EXCHANGE_BUSINESS,
                MQConstant.ROUTING_KEY_SINGLE_SHOT_IMAGE,
                message
        );
    }

    /**
     * 发送批量生成视频任务
     */
    public void sendBatchVideoTask(Long jobId, Long userId, Long projectId, List<Long> shotIds,
                                   String mode, Integer countPerItem, String aspectRatio, String model) {
        BatchTaskMessage message = new BatchTaskMessage(
                jobId, userId, projectId, shotIds, mode, countPerItem, aspectRatio, model
        );
        
        log.info("发送消息 - 交换机: {}, 路由键: {}, jobId: {}, shotCount: {}", 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_BATCH_VIDEO,
                jobId, shotIds.size());
        
        rabbitTemplate.convertAndSend(
                MQConstant.EXCHANGE_BUSINESS,
                MQConstant.ROUTING_KEY_BATCH_VIDEO,
                message
        );
    }

    /**
     * 发送批量生成角色画像任务
     */
    public void sendBatchCharacterImageTask(Long jobId, Long userId, Long projectId, List<Long> characterIds,
                                            String mode, Integer countPerItem, String aspectRatio, String model) {
        BatchTaskMessage message = new BatchTaskMessage(
                jobId, userId, projectId, characterIds, mode, countPerItem, aspectRatio, model
        );
        
        log.info("发送消息 - 交换机: {}, 路由键: {}, jobId: {}, characterCount: {}", 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_BATCH_CHARACTER_IMAGE,
                jobId, characterIds.size());
        
        rabbitTemplate.convertAndSend(
                MQConstant.EXCHANGE_BUSINESS,
                MQConstant.ROUTING_KEY_BATCH_CHARACTER_IMAGE,
                message
        );
    }

    /**
     * 发送批量生成场景画像任务
     */
    public void sendBatchSceneImageTask(Long jobId, Long userId, Long projectId, List<Long> sceneIds,
                                        String mode, Integer countPerItem, String aspectRatio, String model) {
        BatchTaskMessage message = new BatchTaskMessage(
                jobId, userId, projectId, sceneIds, mode, countPerItem, aspectRatio, model
        );
        
        log.info("发送消息 - 交换机: {}, 路由键: {}, jobId: {}, sceneCount: {}", 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_BATCH_SCENE_IMAGE,
                jobId, sceneIds.size());
        
        rabbitTemplate.convertAndSend(
                MQConstant.EXCHANGE_BUSINESS,
                MQConstant.ROUTING_KEY_BATCH_SCENE_IMAGE,
                message
        );
    }

    /**
     * 发送批量生成道具画像任务
     */
    public void sendBatchPropImageTask(Long jobId, Long userId, Long projectId, List<Long> propIds,
                                       String mode, Integer countPerItem, String aspectRatio, String model) {
        BatchTaskMessage message = new BatchTaskMessage(
                jobId, userId, projectId, propIds, mode, countPerItem, aspectRatio, model
        );
        
        log.info("发送消息 - 交换机: {}, 路由键: {}, jobId: {}, propCount: {}", 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_BATCH_PROP_IMAGE,
                jobId, propIds.size());
        
        rabbitTemplate.convertAndSend(
                MQConstant.EXCHANGE_BUSINESS,
                MQConstant.ROUTING_KEY_BATCH_PROP_IMAGE,
                message
        );
    }

    /**
     * 发送单个分镜视频生成任务
     * 
     * @param jobId 任务ID
     * @param userId 用户ID
     * @param projectId 项目ID
     * @param shotId 分镜ID
     * @param prompt 完整提示词(内嵌规则 + 剧本 + 用户自定义)
     * @param aspectRatio 画幅比例
     * @param duration 视频时长(秒)
     * @param size 输出分辨率
     * @param referenceImageUrl 首帧参考图URL(可选)
     * @param scene 场景信息(可选)
     * @param characters 角色列表(可选)
     * @param props 道具列表(可选)
     */
    public void sendSingleShotVideoTask(Long jobId, Long userId, Long projectId, Long shotId,
                                        String prompt, String aspectRatio, Integer duration, String size,
                                        String referenceImageUrl,
                                        AssetResource scene, java.util.List<AssetResource> characters,
                                        java.util.List<AssetResource> props) {
        SingleShotVideoMessage message = new SingleShotVideoMessage(
                jobId, userId, projectId, shotId, prompt, aspectRatio, duration, size, referenceImageUrl, scene, characters, props
        );
        
        log.info("发送消息 - 交换机: {}, 路由键: {}, jobId: {}, shotId: {}, promptLength: {}, hasScene: {}, characterCount: {}, propCount: {}", 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_SINGLE_SHOT_VIDEO,
                jobId, shotId, prompt.length(), scene != null, 
                characters != null ? characters.size() : 0,
                props != null ? props.size() : 0);
        
        rabbitTemplate.convertAndSend(
                MQConstant.EXCHANGE_BUSINESS,
                MQConstant.ROUTING_KEY_SINGLE_SHOT_VIDEO,
                message
        );
    }

    /**
     * 发送文本解析任务
     */
    public void sendTextParsingTask(Long jobId, Long userId, Long projectId, String rawText) {
        TextParsingMessage message = new TextParsingMessage(jobId, userId, projectId, rawText);
        
        log.info("发送消息 - 交换机: {}, 路由键: {}, jobId: {}, textLength: {}", 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_TEXT_PARSING,
                jobId, rawText.length());
        
        rabbitTemplate.convertAndSend(
                MQConstant.EXCHANGE_BUSINESS,
                MQConstant.ROUTING_KEY_TEXT_PARSING,
                message
        );
    }
}
