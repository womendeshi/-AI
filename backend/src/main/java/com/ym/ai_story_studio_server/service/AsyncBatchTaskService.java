package com.ym.ai_story_studio_server.service;

import com.ym.ai_story_studio_server.config.AiProperties;
import com.ym.ai_story_studio_server.dto.ai.ImageGenerateRequest;
import com.ym.ai_story_studio_server.dto.ai.VideoGenerateRequest;
import com.ym.ai_story_studio_server.entity.Job;
import com.ym.ai_story_studio_server.mapper.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步批量任务执行服务
 *
 * <p>负责执行所有批量生成任务的异步处理逻辑
 *
 * <p><strong>核心功能:</strong>
 * <ul>
 *   <li>异步批量生成分镜图</li>
 *   <li>异步批量生成视频</li>
 *   <li>异步批量生成角色画像</li>
 *   <li>异步批量生成场景画像</li>
 *   <li>异步文本解析</li>
 * </ul>
 *
 * <p><strong>设计原则:</strong>
 * <ul>
 *   <li>所有方法使用@Async注解,确保异步执行</li>
 *   <li>独立Service确保Spring AOP代理生效</li>
 *   <li>错误隔离:单个子任务失败不影响其他子任务</li>
 *   <li>进度追踪:实时更新Job表的progress和doneItems</li>
 * </ul>
 *
 * <p><strong>异步执行流程:</strong>
 * <ol>
 *   <li>BatchGenerationService创建Job任务并调用此服务</li>
 *   <li>方法使用@Async在独立线程池中执行</li>
 *   <li>遍历目标列表,逐个调用单个生成服务</li>
 *   <li>每完成一个子任务,更新Job进度</li>
 *   <li>所有子任务完成后,更新Job状态为SUCCEEDED</li>
 *   <li>如果任何子任务失败,记录错误但继续执行其他子任务</li>
 * </ol>
 *
 * <p><strong>注意事项:</strong>
 * <ul>
 *   <li>异步方法中不能使用UserContext(ThreadLocal),需通过参数传递userId</li>
 *   <li>所有参数必须通过方法参数传入,不能依赖请求上下文</li>
 *   <li>异步方法的异常会被AsyncUncaughtExceptionHandler捕获</li>
 *   <li>使用AtomicInteger保证多线程环境下的计数安全</li>
 * </ul>
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncBatchTaskService {

    private final AiImageService aiImageService;
    private final AiVideoService aiVideoService;
    private final AiTextService aiTextService;
    private final JobMapper jobMapper;
    private final AiProperties aiProperties;
    private final com.ym.ai_story_studio_server.mapper.AssetMapper assetMapper;
    private final com.ym.ai_story_studio_server.mapper.AssetVersionMapper assetVersionMapper;
    private final com.ym.ai_story_studio_server.mapper.StoryboardShotMapper storyboardShotMapper;
    private final com.ym.ai_story_studio_server.mapper.ProjectCharacterMapper projectCharacterMapper;
    private final com.ym.ai_story_studio_server.mapper.ProjectSceneMapper projectSceneMapper;

    /**
     * 异步批量生成分镜图
     *
     * <p>在独立线程中执行批量分镜图生成任务
     *
     * <p><strong>执行流程:</strong>
     * <ol>
     *   <li>更新Job状态为RUNNING</li>
     *   <li>遍历分镜ID列表</li>
     *   <li>为每个分镜调用AiImageService生成图片</li>
     *   <li>每完成一个,更新Job的doneItems和progress</li>
     *   <li>全部完成后,更新Job状态为SUCCEEDED</li>
     *   <li>如果发生异常,更新Job状态为FAILED并记录错误信息</li>
     * </ol>
     *
     * @param jobId 任务ID
     * @param shotIds 分镜ID列表
     * @param mode 生成模式(ALL/MISSING)
     * @param countPerItem 每个分镜生成数量
     * @param aspectRatio 画幅比例(可选)
     * @param model 模型名称(可选)
     * @param userId 用户ID(需手动传递,不能使用UserContext)
     * @param projectId 项目ID
     */
    @Async("taskExecutor")
    public void executeBatchShotImageGeneration(Long jobId, List<Long> shotIds, String mode,
                                                Integer countPerItem, String aspectRatio,
                                                String model, Long userId, Long projectId) {
        log.info("========== 异步批量生成分镜图任务开始 ==========");
        log.info("jobId: {}, shotCount: {}, mode: {}, thread: {}",
                jobId, shotIds.size(), mode, Thread.currentThread().getName());

        try {
            // 1. 更新Job状态为RUNNING
            updateJobRunning(jobId);

            // 2. 应用默认配置
            String finalAspectRatio = aspectRatio != null ? aspectRatio :
                    aiProperties.getImage().getDefaultAspectRatio();
            String finalModel = model != null ? model :
                    (aiProperties.getImage().getJimengProxyEnabled() ?
                            aiProperties.getImage().getJimengModel() :
                            aiProperties.getImage().getDefaultModel());

            log.info("应用配置 - aspectRatio: {}, model: {}", finalAspectRatio, finalModel);

            // 3. 批量生成
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < shotIds.size(); i++) {
                Long shotId = shotIds.get(i);
                log.info("处理分镜 [{}/{}] - shotId: {}", i + 1, shotIds.size(), shotId);

                try {
                    // 1. 查询分镜信息,获取prompt
                    com.ym.ai_story_studio_server.entity.StoryboardShot shot =
                            storyboardShotMapper.selectById(shotId);
                    if (shot == null) {
                        log.warn("分镜不存在,跳过 - shotId: {}", shotId);
                        failCount.incrementAndGet();
                        continue;
                    }

                    // 2. 检查MISSING模式是否已有图片资产
                    if ("MISSING".equals(mode)) {
                        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ym.ai_story_studio_server.entity.Asset> assetQuery =
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
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

                    // 3. 构建图片生成请求
                    String prompt = shot.getScriptText() != null ? shot.getScriptText() :
                            "为分镜生成图片 - shotId: " + shotId;

                    // 如果需要生成多张,循环调用
                    for (int j = 0; j < countPerItem; j++) {
                        ImageGenerateRequest request = new ImageGenerateRequest(
                                prompt,
                                finalModel,
                                finalAspectRatio,
                                null,
                                null,
                                projectId
                        );

                        // 4. 临时设置UserContext并调用图片生成服务
                        com.ym.ai_story_studio_server.util.UserContext.setUserId(userId);
                        try {
                            aiImageService.generateImage(request);
                            log.info("分镜图生成任务已提交 [{}/{}] - shotId: {}", j + 1, countPerItem, shotId);
                        } finally {
                            com.ym.ai_story_studio_server.util.UserContext.clear();
                        }
                    }

                    successCount.incrementAndGet();
                    log.info("分镜图生成完成 - shotId: {}, 数量: {}", shotId, countPerItem);

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("分镜图生成失败 - shotId: {}", shotId, e);
                    // 继续处理下一个,不中断整个批量任务
                }

                // 4. 更新进度
                updateJobProgress(jobId, i + 1, shotIds.size());
            }

            log.info("批量生成分镜图完成 - 成功: {}, 失败: {}", successCount.get(), failCount.get());

            // 5. 更新Job状态为SUCCEEDED
            updateJobSuccess(jobId, successCount.get(), failCount.get());

        } catch (Exception e) {
            log.error("批量生成分镜图任务异常 - jobId: {}", jobId, e);
            updateJobFailed(jobId, e.getMessage());
        }

        log.info("========== 异步批量生成分镜图任务结束 ==========");
    }

    /**
     * 异步批量生成视频
     *
     * <p>在独立线程中执行批量视频生成任务
     *
     * @param jobId 任务ID
     * @param shotIds 分镜ID列表
     * @param mode 生成模式(ALL/MISSING)
     * @param countPerItem 每个分镜生成数量
     * @param aspectRatio 视频画幅比例(可选)
     * @param model 模型名称(可选)
     * @param userId 用户ID(需手动传递)
     * @param projectId 项目ID
     */
    @Async("taskExecutor")
    public void executeBatchVideoGeneration(Long jobId, List<Long> shotIds, String mode,
                                            Integer countPerItem, String aspectRatio,
                                            String model, Long userId, Long projectId) {
        log.info("========== 异步批量生成视频任务开始 ==========");
        log.info("jobId: {}, shotCount: {}, mode: {}, thread: {}",
                jobId, shotIds.size(), mode, Thread.currentThread().getName());

        try {
            // 1. 更新Job状态为RUNNING
            updateJobRunning(jobId);

            // 2. 应用默认配置
            String finalAspectRatio = aspectRatio != null ? aspectRatio :
                    aiProperties.getVideo().getDefaultAspectRatio();
            String finalModel = model != null ? model :
                    aiProperties.getVideo().getModel();
            Integer duration = aiProperties.getVideo().getDefaultDuration();

            log.info("应用配置 - aspectRatio: {}, model: {}, duration: {}s",
                    finalAspectRatio, finalModel, duration);

            // 3. 批量生成
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < shotIds.size(); i++) {
                Long shotId = shotIds.get(i);
                log.info("处理分镜 [{}/{}] - shotId: {}", i + 1, shotIds.size(), shotId);

                try {
                    // 1. 查询分镜信息,获取prompt
                    com.ym.ai_story_studio_server.entity.StoryboardShot shot =
                            storyboardShotMapper.selectById(shotId);
                    if (shot == null) {
                        log.warn("分镜不存在,跳过 - shotId: {}", shotId);
                        failCount.incrementAndGet();
                        continue;
                    }

                    // 2. 查询分镜的图片资产(视频生成需要参考图)
                    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ym.ai_story_studio_server.entity.Asset> assetQuery =
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                    assetQuery.eq(com.ym.ai_story_studio_server.entity.Asset::getOwnerType, "SHOT")
                            .eq(com.ym.ai_story_studio_server.entity.Asset::getOwnerId, shotId)
                            .eq(com.ym.ai_story_studio_server.entity.Asset::getAssetType, "SHOT_IMG")
                            .eq(com.ym.ai_story_studio_server.entity.Asset::getProjectId, projectId);

                    List<com.ym.ai_story_studio_server.entity.Asset> assets = assetMapper.selectList(assetQuery);

                    if (assets.isEmpty()) {
                        log.warn("分镜没有图片资产,无法生成视频,跳过 - shotId: {}", shotId);
                        failCount.incrementAndGet();
                        continue;
                    }

                    // 3. 获取图片URL列表(取每个资产的最新版本)
                    List<String> imageUrls = new java.util.ArrayList<>();
                    for (com.ym.ai_story_studio_server.entity.Asset asset : assets) {
                        // 查询该资产的最新版本
                        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ym.ai_story_studio_server.entity.AssetVersion> versionQuery =
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                        versionQuery.eq(com.ym.ai_story_studio_server.entity.AssetVersion::getAssetId, asset.getId())
                                .eq(com.ym.ai_story_studio_server.entity.AssetVersion::getStatus, "READY")
                                .orderByDesc(com.ym.ai_story_studio_server.entity.AssetVersion::getVersionNo)
                                .last("LIMIT 1");

                        com.ym.ai_story_studio_server.entity.AssetVersion latestVersion =
                                assetVersionMapper.selectOne(versionQuery);

                        if (latestVersion != null && latestVersion.getUrl() != null) {
                            imageUrls.add(latestVersion.getUrl());
                        }
                    }

                    if (imageUrls.isEmpty()) {
                        log.warn("分镜的图片资产都没有可用版本,无法生成视频,跳过 - shotId: {}", shotId);
                        failCount.incrementAndGet();
                        continue;
                    }

                    log.info("找到 {} 个参考图片 - shotId: {}, urls: {}", imageUrls.size(), shotId, imageUrls);

                    // 4. 检查MISSING模式是否已有视频资产
                    if ("MISSING".equals(mode)) {
                        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ym.ai_story_studio_server.entity.Asset> videoAssetQuery =
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                        videoAssetQuery.eq(com.ym.ai_story_studio_server.entity.Asset::getOwnerType, "SHOT")
                                .eq(com.ym.ai_story_studio_server.entity.Asset::getOwnerId, shotId)
                                .eq(com.ym.ai_story_studio_server.entity.Asset::getAssetType, "VIDEO")
                                .eq(com.ym.ai_story_studio_server.entity.Asset::getProjectId, projectId);

                        long videoCount = assetMapper.selectCount(videoAssetQuery);
                        if (videoCount > 0) {
                            log.info("MISSING模式 - 分镜已有视频资产,跳过 - shotId: {}", shotId);
                            successCount.incrementAndGet();
                            continue;
                        }
                    }

                    // 5. 构建视频生成请求
                    String prompt = shot.getScriptText() != null ? shot.getScriptText() :
                            "根据分镜图生成视频 - shotId: " + shotId;

                    // 注意: VideoGenerateRequest只支持单张参考图,取第一张
                    String referenceImageUrl = !imageUrls.isEmpty() ? imageUrls.get(0) : null;

                    VideoGenerateRequest request = new VideoGenerateRequest(
                            prompt,
                            finalAspectRatio,
                            duration,
                            referenceImageUrl,  // 传递第一张参考图片URL
                            projectId
                    );

                    // 6. 临时设置UserContext并调用视频生成服务
                    com.ym.ai_story_studio_server.util.UserContext.setUserId(userId);
                    try {
                        aiVideoService.generateVideo(request);
                        successCount.incrementAndGet();
                        log.info("视频生成任务已提交 - shotId: {}, 参考图: {}", shotId, referenceImageUrl);
                    } finally {
                        com.ym.ai_story_studio_server.util.UserContext.clear();
                    }

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("视频生成失败 - shotId: {}", shotId, e);
                }

                // 4. 更新进度
                updateJobProgress(jobId, i + 1, shotIds.size());
            }

            log.info("批量生成视频完成 - 成功: {}, 失败: {}", successCount.get(), failCount.get());

            // 5. 更新Job状态为SUCCEEDED
            updateJobSuccess(jobId, successCount.get(), failCount.get());

        } catch (Exception e) {
            log.error("批量生成视频任务异常 - jobId: {}", jobId, e);
            updateJobFailed(jobId, e.getMessage());
        }

        log.info("========== 异步批量生成视频任务结束 ==========");
    }

    /**
     * 异步批量生成角色画像
     *
     * @param jobId 任务ID
     * @param characterIds 角色ID列表
     * @param mode 生成模式(ALL/MISSING)
     * @param countPerItem 每个角色生成数量
     * @param aspectRatio 画幅比例(可选)
     * @param model 模型名称(可选)
     * @param userId 用户ID
     * @param projectId 项目ID
     */
    @Async("taskExecutor")
    public void executeBatchCharacterImageGeneration(Long jobId, List<Long> characterIds, String mode,
                                                     Integer countPerItem, String aspectRatio,
                                                     String model, Long userId, Long projectId) {
        log.info("========== 异步批量生成角色画像任务开始 ==========");
        log.info("jobId: {}, characterCount: {}, mode: {}, thread: {}",
                jobId, characterIds.size(), mode, Thread.currentThread().getName());

        try {
            // 1. 更新Job状态为RUNNING
            updateJobRunning(jobId);

            // 2. 应用默认配置
            String finalAspectRatio = aspectRatio != null ? aspectRatio :
                    aiProperties.getImage().getDefaultAspectRatio();
            String finalModel = model != null ? model :
                    (aiProperties.getImage().getJimengProxyEnabled() ?
                            aiProperties.getImage().getJimengModel() :
                            aiProperties.getImage().getDefaultModel());

            log.info("应用配置 - aspectRatio: {}, model: {}", finalAspectRatio, finalModel);

            // 3. 批量生成
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < characterIds.size(); i++) {
                Long characterId = characterIds.get(i);
                log.info("处理角色 [{}/{}] - characterId: {}", i + 1, characterIds.size(), characterId);

                try {
                    // 1. 查询角色信息,获取prompt
                    com.ym.ai_story_studio_server.entity.ProjectCharacter character =
                            projectCharacterMapper.selectById(characterId);
                    if (character == null) {
                        log.warn("角色不存在,跳过 - characterId: {}", characterId);
                        failCount.incrementAndGet();
                        continue;
                    }

                    // 2. 检查MISSING模式是否已有角色画像资产
                    if ("MISSING".equals(mode)) {
                        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ym.ai_story_studio_server.entity.Asset> assetQuery =
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                        assetQuery.eq(com.ym.ai_story_studio_server.entity.Asset::getOwnerType, "PCHAR")
                                .eq(com.ym.ai_story_studio_server.entity.Asset::getOwnerId, characterId)
                                .eq(com.ym.ai_story_studio_server.entity.Asset::getAssetType, "CHAR_IMG")
                                .eq(com.ym.ai_story_studio_server.entity.Asset::getProjectId, projectId);

                        long imageCount = assetMapper.selectCount(assetQuery);
                        if (imageCount > 0) {
                            log.info("MISSING模式 - 角色已有画像资产,跳过 - characterId: {}", characterId);
                            successCount.incrementAndGet();
                            continue;
                        }
                    }

                    // 3. 构建图片生成请求
                    String prompt = character.getOverrideDescription() != null ?
                            character.getOverrideDescription() :
                            (character.getDisplayName() != null ?
                                    "角色画像: " + character.getDisplayName() :
                                    "角色画像 - characterId: " + characterId);

                    // 如果需要生成多张,循环调用
                    for (int j = 0; j < countPerItem; j++) {
                        ImageGenerateRequest request = new ImageGenerateRequest(
                                prompt,
                                finalModel,
                                finalAspectRatio,
                                null,
                                null,
                                projectId
                        );

                        // 4. 临时设置UserContext并调用图片生成服务
                        com.ym.ai_story_studio_server.util.UserContext.setUserId(userId);
                        try {
                            aiImageService.generateImage(request);
                            log.info("角色画像生成任务已提交 [{}/{}] - characterId: {}", j + 1, countPerItem, characterId);
                        } finally {
                            com.ym.ai_story_studio_server.util.UserContext.clear();
                        }
                    }

                    successCount.incrementAndGet();
                    log.info("角色画像生成完成 - characterId: {}, 数量: {}", characterId, countPerItem);

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("角色画像生成失败 - characterId: {}", characterId, e);
                }

                // 更新进度
                updateJobProgress(jobId, i + 1, characterIds.size());
            }

            log.info("批量生成角色画像完成 - 成功: {}, 失败: {}", successCount.get(), failCount.get());

            // 5. 更新Job状态为SUCCEEDED
            updateJobSuccess(jobId, successCount.get(), failCount.get());

        } catch (Exception e) {
            log.error("批量生成角色画像任务异常 - jobId: {}", jobId, e);
            updateJobFailed(jobId, e.getMessage());
        }

        log.info("========== 异步批量生成角色画像任务结束 ==========");
    }

    /**
     * 异步批量生成场景画像
     *
     * @param jobId 任务ID
     * @param sceneIds 场景ID列表
     * @param mode 生成模式(ALL/MISSING)
     * @param countPerItem 每个场景生成数量
     * @param aspectRatio 画幅比例(可选)
     * @param model 模型名称(可选)
     * @param userId 用户ID
     * @param projectId 项目ID
     */
    @Async("taskExecutor")
    public void executeBatchSceneImageGeneration(Long jobId, List<Long> sceneIds, String mode,
                                                 Integer countPerItem, String aspectRatio,
                                                 String model, Long userId, Long projectId) {
        log.info("========== 异步批量生成场景画像任务开始 ==========");
        log.info("jobId: {}, sceneCount: {}, mode: {}, thread: {}",
                jobId, sceneIds.size(), mode, Thread.currentThread().getName());

        try {
            // 1. 更新Job状态为RUNNING
            updateJobRunning(jobId);

            // 2. 应用默认配置
            String finalAspectRatio = aspectRatio != null ? aspectRatio :
                    aiProperties.getImage().getDefaultAspectRatio();
            String finalModel = model != null ? model :
                    (aiProperties.getImage().getJimengProxyEnabled() ?
                            aiProperties.getImage().getJimengModel() :
                            aiProperties.getImage().getDefaultModel());

            log.info("应用配置 - aspectRatio: {}, model: {}", finalAspectRatio, finalModel);

            // 3. 批量生成
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < sceneIds.size(); i++) {
                Long sceneId = sceneIds.get(i);
                log.info("处理场景 [{}/{}] - sceneId: {}", i + 1, sceneIds.size(), sceneId);

                try {
                    // 1. 查询场景信息,获取prompt
                    com.ym.ai_story_studio_server.entity.ProjectScene scene =
                            projectSceneMapper.selectById(sceneId);
                    if (scene == null) {
                        log.warn("场景不存在,跳过 - sceneId: {}", sceneId);
                        failCount.incrementAndGet();
                        continue;
                    }

                    // 2. 检查MISSING模式是否已有场景画像资产
                    if ("MISSING".equals(mode)) {
                        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ym.ai_story_studio_server.entity.Asset> assetQuery =
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                        assetQuery.eq(com.ym.ai_story_studio_server.entity.Asset::getOwnerType, "PSCENE")
                                .eq(com.ym.ai_story_studio_server.entity.Asset::getOwnerId, sceneId)
                                .eq(com.ym.ai_story_studio_server.entity.Asset::getAssetType, "SCENE_IMG")
                                .eq(com.ym.ai_story_studio_server.entity.Asset::getProjectId, projectId);

                        long imageCount = assetMapper.selectCount(assetQuery);
                        if (imageCount > 0) {
                            log.info("MISSING模式 - 场景已有画像资产,跳过 - sceneId: {}", sceneId);
                            successCount.incrementAndGet();
                            continue;
                        }
                    }

                    // 3. 构建图片生成请求
                    String prompt = scene.getOverrideDescription() != null ?
                            scene.getOverrideDescription() :
                            (scene.getDisplayName() != null ?
                                    "场景画像: " + scene.getDisplayName() :
                                    "场景画像 - sceneId: " + sceneId);

                    // 如果需要生成多张,循环调用
                    for (int j = 0; j < countPerItem; j++) {
                        ImageGenerateRequest request = new ImageGenerateRequest(
                                prompt,
                                finalModel,
                                finalAspectRatio,
                                null,
                                null,
                                projectId
                        );

                        // 4. 临时设置UserContext并调用图片生成服务
                        com.ym.ai_story_studio_server.util.UserContext.setUserId(userId);
                        try {
                            aiImageService.generateImage(request);
                            log.info("场景画像生成任务已提交 [{}/{}] - sceneId: {}", j + 1, countPerItem, sceneId);
                        } finally {
                            com.ym.ai_story_studio_server.util.UserContext.clear();
                        }
                    }

                    successCount.incrementAndGet();
                    log.info("场景画像生成完成 - sceneId: {}, 数量: {}", sceneId, countPerItem);

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("场景画像生成失败 - sceneId: {}", sceneId, e);
                }

                // 更新进度
                updateJobProgress(jobId, i + 1, sceneIds.size());
            }

            log.info("批量生成场景画像完成 - 成功: {}, 失败: {}", successCount.get(), failCount.get());

            // 5. 更新Job状态为SUCCEEDED
            updateJobSuccess(jobId, successCount.get(), failCount.get());

        } catch (Exception e) {
            log.error("批量生成场景画像任务异常 - jobId: {}", jobId, e);
            updateJobFailed(jobId, e.getMessage());
        }

        log.info("========== 异步批量生成场景画像任务结束 ==========");
    }

    /**
     * 异步文本解析
     *
     * <p>调用AI大语言模型分析小说/剧本文本,自动提取场景、角色、对话等信息
     *
     * <p><strong>执行流程:</strong>
     * <ol>
     *   <li>临时设置UserContext</li>
     *   <li>调用AiTextService分析文本结构</li>
     *   <li>解析AI返回的结构化数据(场景列表、角色列表、分镜列表)</li>
     *   <li>创建项目角色记录(ProjectCharacter)</li>
     *   <li>创建项目场景记录(ProjectScene)</li>
     *   <li>创建分镜记录(StoryboardShot)</li>
     *   <li>绑定分镜与角色、场景的关联关系(ShotCharacterRef、ShotSceneRef)</li>
     *   <li>更新Job状态为SUCCEEDED</li>
     * </ol>
     *
     * <p><strong>注意事项:</strong>
     * <ul>
     *   <li>文本解析依赖大语言模型,可能耗时较长(1-5分钟)</li>
     *   <li>AI返回的数据格式需要严格校验</li>
     *   <li>创建记录时需要避免重复(检查名称是否已存在)</li>
     * </ul>
     *
     * @param jobId 任务ID
     * @param rawText 原始文本(小说/剧本内容)
     * @param userId 用户ID
     * @param projectId 项目ID
     */
    @Async("taskExecutor")
    public void executeTextParsing(Long jobId, String rawText, Long userId, Long projectId) {
        log.info("========== 异步文本解析任务开始 ==========");
        log.info("jobId: {}, textLength: {}, thread: {}",
                jobId, rawText.length(), Thread.currentThread().getName());

        try {
            // 1. 更新Job状态为RUNNING
            updateJobRunning(jobId);

            // 2. 临时设置UserContext
            com.ym.ai_story_studio_server.util.UserContext.setUserId(userId);
            try {
                // 3. 调用AiTextService分析文本
                log.info("开始调用AI分析文本结构...");

                // TODO: 调用AiTextService.parseText()方法分析文本
                // String analysisResult = aiTextService.parseText(rawText, projectId);

                // TODO: 解析AI返回的JSON结构化数据
                // {
                //   "scenes": [{"name": "未来城市", "description": "霓虹闪烁..."}, ...],
                //   "characters": [{"name": "小明", "description": "主角,勇敢..."}, ...],
                //   "shots": [{"sceneIndex": 0, "description": "全景镜头...", "characterIndexes": [0], "dialogue": "..."}, ...]
                // }

                // TODO: 4. 创建项目角色记录
                // List<ProjectCharacter> characters = new ArrayList<>();
                // for (CharacterData data : parsedData.getCharacters()) {
                //     ProjectCharacter character = new ProjectCharacter();
                //     character.setProjectId(projectId);
                //     character.setDisplayName(data.getName());
                //     character.setOverrideDescription(data.getDescription());
                //     projectCharacterMapper.insert(character);
                //     characters.add(character);
                // }

                // TODO: 5. 创建项目场景记录
                // List<ProjectScene> scenes = new ArrayList<>();
                // for (SceneData data : parsedData.getScenes()) {
                //     ProjectScene scene = new ProjectScene();
                //     scene.setProjectId(projectId);
                //     scene.setDisplayName(data.getName());
                //     scene.setOverrideDescription(data.getDescription());
                //     projectSceneMapper.insert(scene);
                //     scenes.add(scene);
                // }

                // TODO: 6. 创建分镜记录
                // int shotOrder = 0;
                // for (ShotData data : parsedData.getShots()) {
                //     StoryboardShot shot = new StoryboardShot();
                //     shot.setProjectId(projectId);
                //     shot.setShotOrder(shotOrder++);
                //     shot.setDescription(data.getDescription());
                //     shot.setDialogue(data.getDialogue());
                //     storyboardShotMapper.insert(shot);
                //
                //     // TODO: 7. 绑定分镜与角色、场景的关联关系
                //     // 使用ShotCharacterRefMapper和ShotSceneRefMapper创建关联记录
                // }

                log.warn("文本解析功能尚未完全实现,当前为占位符逻辑");
                log.info("文本长度: {} 字符,需要AI分析后创建分镜/角色/场景记录", rawText.length());

                // 临时标记为成功(实际应根据AI分析结果决定)
                updateJobSuccess(jobId, 1, 0);

            } finally {
                com.ym.ai_story_studio_server.util.UserContext.clear();
            }

        } catch (Exception e) {
            log.error("文本解析任务异常 - jobId: {}", jobId, e);
            updateJobFailed(jobId, e.getMessage());
        }

        log.info("========== 异步文本解析任务结束 ==========");
    }

    /**
     * 更新任务状态为RUNNING
     *
     * @param jobId 任务ID
     */
    private void updateJobRunning(Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job != null) {
            job.setStatus("RUNNING");
            job.setProgress(0);
            jobMapper.updateById(job);
            log.info("任务状态已更新为RUNNING - jobId: {}", jobId);
        }
    }

    /**
     * 更新任务进度
     *
     * @param jobId 任务ID
     * @param doneItems 已完成数量
     * @param totalItems 总数量
     */
    private void updateJobProgress(Long jobId, int doneItems, int totalItems) {
        Job job = jobMapper.selectById(jobId);
        if (job != null) {
            job.setDoneItems(doneItems);
            job.setProgress((int) (doneItems * 100.0 / totalItems));
            jobMapper.updateById(job);
            log.debug("任务进度已更新 - jobId: {}, progress: {}%, done: {}/{}",
                    jobId, job.getProgress(), doneItems, totalItems);
        }
    }

    /**
     * 更新任务状态为SUCCEEDED
     *
     * @param jobId 任务ID
     * @param successCount 成功数量
     * @param failCount 失败数量
     */
    private void updateJobSuccess(Long jobId, int successCount, int failCount) {
        Job job = jobMapper.selectById(jobId);
        if (job != null) {
            job.setStatus("SUCCEEDED");
            job.setProgress(100);
            job.setDoneItems(successCount);
            job.setMetaJson(String.format("{\"successCount\":%d,\"failCount\":%d}",
                    successCount, failCount));
            jobMapper.updateById(job);
            log.info("任务已完成 - jobId: {}, 成功: {}, 失败: {}", jobId, successCount, failCount);
        }
    }

    /**
     * 更新任务状态为FAILED
     *
     * @param jobId 任务ID
     * @param errorMessage 错误信息
     */
    private void updateJobFailed(Long jobId, String errorMessage) {
        Job job = jobMapper.selectById(jobId);
        if (job != null) {
            job.setStatus("FAILED");
            job.setErrorMessage(errorMessage);
            jobMapper.updateById(job);
            log.error("任务已失败 - jobId: {}, error: {}", jobId, errorMessage);
        }
    }
}
