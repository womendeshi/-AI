package com.ym.ai_story_studio_server.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MQ初始化配置
 * 
 * <p>声明交换机、队列、绑定关系
 * 
 * @author AI Story Studio
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class MQInitConfig {

    // ==================== 消息转换器配置 ====================
    
    /**
     * 配置消息转换器，允许反序列化自定义类
     */
    @Bean
    public SimpleMessageConverter messageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        // 允许反序列化的类模式
        converter.setAllowedListPatterns(List.of(
                "com.ym.ai_story_studio_server.dto.ai.*",
                "com.ym.ai_story_studio_server.mq.*",
                "java.util.*",
                "java.lang.*"
        ));
        log.info("配置RabbitMQ消息转换器，允许反序列化mq包下的类");
        return converter;
    }

    // ==================== 交换机声明 ====================
    
    /**
     * 业务交换机（Direct）
     */
    @Bean
    public DirectExchange businessExchange() {
        log.info("初始化业务交换机: {}", MQConstant.EXCHANGE_BUSINESS);
        return ExchangeBuilder
                .directExchange(MQConstant.EXCHANGE_BUSINESS)
                .durable(true)  // 持久化
                .build();
    }

    /**
     * 死信交换机（Direct）
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        log.info("初始化死信交换机: {}", MQConstant.EXCHANGE_DEAD_LETTER);
        return ExchangeBuilder
                .directExchange(MQConstant.EXCHANGE_DEAD_LETTER)
                .durable(true)
                .build();
    }

    // ==================== 队列声明 ====================

    /**
     * 批量生成分镜图队列
     */
    @Bean
    public Queue batchShotImageQueue() {
        Map<String, Object> args = buildQueueArgs();
        log.info("初始化队列: {}", MQConstant.QUEUE_BATCH_SHOT_IMAGE);
        return QueueBuilder
                .durable(MQConstant.QUEUE_BATCH_SHOT_IMAGE)
                .withArguments(args)
                .build();
    }

    /**
     * 单个分镜图生成队列(支持自定义prompt)
     */
    @Bean
    public Queue singleShotImageQueue() {
        Map<String, Object> args = buildQueueArgs();
        log.info("初始化队列: {}", MQConstant.QUEUE_SINGLE_SHOT_IMAGE);
        return QueueBuilder
                .durable(MQConstant.QUEUE_SINGLE_SHOT_IMAGE)
                .withArguments(args)
                .build();
    }

    /**
     * 批量生成视频队列
     */
    @Bean
    public Queue batchVideoQueue() {
        Map<String, Object> args = buildQueueArgs();
        log.info("初始化队列: {}", MQConstant.QUEUE_BATCH_VIDEO);
        return QueueBuilder
                .durable(MQConstant.QUEUE_BATCH_VIDEO)
                .withArguments(args)
                .build();
    }

    /**
     * 单个分镜视频生成队列
     */
    @Bean
    public Queue singleShotVideoQueue() {
        Map<String, Object> args = buildQueueArgs();
        log.info("初始化队列: {}", MQConstant.QUEUE_SINGLE_SHOT_VIDEO);
        return QueueBuilder
                .durable(MQConstant.QUEUE_SINGLE_SHOT_VIDEO)
                .withArguments(args)
                .build();
    }

    /**
     * 批量生成角色画像队列
     */
    @Bean
    public Queue batchCharacterImageQueue() {
        Map<String, Object> args = buildQueueArgs();
        log.info("初始化队列: {}", MQConstant.QUEUE_BATCH_CHARACTER_IMAGE);
        return QueueBuilder
                .durable(MQConstant.QUEUE_BATCH_CHARACTER_IMAGE)
                .withArguments(args)
                .build();
    }

    /**
     * 批量生成场景画像队列
     */
    @Bean
    public Queue batchSceneImageQueue() {
        Map<String, Object> args = buildQueueArgs();
        log.info("初始化队列: {}", MQConstant.QUEUE_BATCH_SCENE_IMAGE);
        return QueueBuilder
                .durable(MQConstant.QUEUE_BATCH_SCENE_IMAGE)
                .withArguments(args)
                .build();
    }

    /**
     * 批量生成道具画像队列
     */
    @Bean
    public Queue batchPropImageQueue() {
        Map<String, Object> args = buildQueueArgs();
        log.info("初始化队列: {}", MQConstant.QUEUE_BATCH_PROP_IMAGE);
        return QueueBuilder
                .durable(MQConstant.QUEUE_BATCH_PROP_IMAGE)
                .withArguments(args)
                .build();
    }

    /**
     * 文本解析队列
     */
    @Bean
    public Queue textParsingQueue() {
        Map<String, Object> args = buildQueueArgs();
        log.info("初始化队列: {}", MQConstant.QUEUE_TEXT_PARSING);
        return QueueBuilder
                .durable(MQConstant.QUEUE_TEXT_PARSING)
                .withArguments(args)
                .build();
    }

    /**
     * 死信队列
     */
    @Bean
    public Queue deadLetterQueue() {
        log.info("初始化死信队列: {}", MQConstant.QUEUE_DEAD_LETTER);
        return QueueBuilder
                .durable(MQConstant.QUEUE_DEAD_LETTER)
                .build();
    }

    // ==================== 绑定关系 ====================

    /**
     * 批量生成分镜图队列绑定
     */
    @Bean
    public Binding batchShotImageBinding(Queue batchShotImageQueue, DirectExchange businessExchange) {
        log.info("绑定: {} -> {} ({})", 
                MQConstant.QUEUE_BATCH_SHOT_IMAGE, 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_BATCH_SHOT_IMAGE);
        return BindingBuilder
                .bind(batchShotImageQueue)
                .to(businessExchange)
                .with(MQConstant.ROUTING_KEY_BATCH_SHOT_IMAGE);
    }

    /**
     * 单个分镜图生成队列绑定(支持自定义prompt)
     */
    @Bean
    public Binding singleShotImageBinding(Queue singleShotImageQueue, DirectExchange businessExchange) {
        log.info("绑定: {} -> {} ({})", 
                MQConstant.QUEUE_SINGLE_SHOT_IMAGE, 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_SINGLE_SHOT_IMAGE);
        return BindingBuilder
                .bind(singleShotImageQueue)
                .to(businessExchange)
                .with(MQConstant.ROUTING_KEY_SINGLE_SHOT_IMAGE);
    }

    /**
     * 批量生成视频队列绑定
     */
    @Bean
    public Binding batchVideoBinding(Queue batchVideoQueue, DirectExchange businessExchange) {
        log.info("绑定: {} -> {} ({})", 
                MQConstant.QUEUE_BATCH_VIDEO, 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_BATCH_VIDEO);
        return BindingBuilder
                .bind(batchVideoQueue)
                .to(businessExchange)
                .with(MQConstant.ROUTING_KEY_BATCH_VIDEO);
    }

    /**
     * 单个分镜视频生成队列绑定
     */
    @Bean
    public Binding singleShotVideoBinding(Queue singleShotVideoQueue, DirectExchange businessExchange) {
        log.info("绑定: {} -> {} ({})", 
                MQConstant.QUEUE_SINGLE_SHOT_VIDEO, 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_SINGLE_SHOT_VIDEO);
        return BindingBuilder
                .bind(singleShotVideoQueue)
                .to(businessExchange)
                .with(MQConstant.ROUTING_KEY_SINGLE_SHOT_VIDEO);
    }

    /**
     * 批量生成角色画像队列绑定
     */
    @Bean
    public Binding batchCharacterImageBinding(Queue batchCharacterImageQueue, DirectExchange businessExchange) {
        log.info("绑定: {} -> {} ({})", 
                MQConstant.QUEUE_BATCH_CHARACTER_IMAGE, 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_BATCH_CHARACTER_IMAGE);
        return BindingBuilder
                .bind(batchCharacterImageQueue)
                .to(businessExchange)
                .with(MQConstant.ROUTING_KEY_BATCH_CHARACTER_IMAGE);
    }

    /**
     * 批量生成场景画像队列绑定
     */
    @Bean
    public Binding batchSceneImageBinding(Queue batchSceneImageQueue, DirectExchange businessExchange) {
        log.info("绑定: {} -> {} ({})", 
                MQConstant.QUEUE_BATCH_SCENE_IMAGE, 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_BATCH_SCENE_IMAGE);
        return BindingBuilder
                .bind(batchSceneImageQueue)
                .to(businessExchange)
                .with(MQConstant.ROUTING_KEY_BATCH_SCENE_IMAGE);
    }

    /**
     * 批量生成道具画像队列绑定
     */
    @Bean
    public Binding batchPropImageBinding(Queue batchPropImageQueue, DirectExchange businessExchange) {
        log.info("绑定: {} -> {} ({})", 
                MQConstant.QUEUE_BATCH_PROP_IMAGE, 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_BATCH_PROP_IMAGE);
        return BindingBuilder
                .bind(batchPropImageQueue)
                .to(businessExchange)
                .with(MQConstant.ROUTING_KEY_BATCH_PROP_IMAGE);
    }

    /**
     * 文本解析队列绑定
     */
    @Bean
    public Binding textParsingBinding(Queue textParsingQueue, DirectExchange businessExchange) {
        log.info("绑定: {} -> {} ({})", 
                MQConstant.QUEUE_TEXT_PARSING, 
                MQConstant.EXCHANGE_BUSINESS, 
                MQConstant.ROUTING_KEY_TEXT_PARSING);
        return BindingBuilder
                .bind(textParsingQueue)
                .to(businessExchange)
                .with(MQConstant.ROUTING_KEY_TEXT_PARSING);
    }

    /**
     * 死信队列绑定
     */
    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        log.info("绑定: {} -> {} ({})", 
                MQConstant.QUEUE_DEAD_LETTER, 
                MQConstant.EXCHANGE_DEAD_LETTER, 
                MQConstant.ROUTING_KEY_DEAD_LETTER);
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(MQConstant.ROUTING_KEY_DEAD_LETTER);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建队列参数（配置死信交换机）
     */
    private Map<String, Object> buildQueueArgs() {
        Map<String, Object> args = new HashMap<>();
        // 配置死信交换机
        args.put(MQConstant.X_DEAD_LETTER_EXCHANGE, MQConstant.EXCHANGE_DEAD_LETTER);
        // 配置死信路由键
        args.put(MQConstant.X_DEAD_LETTER_ROUTING_KEY, MQConstant.ROUTING_KEY_DEAD_LETTER);
        return args;
    }
}
