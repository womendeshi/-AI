package com.ym.ai_story_studio_server.service;

import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.dto.ai.BatchGenerateRequest;
import com.ym.ai_story_studio_server.dto.ai.BatchGenerateResponse;
import com.ym.ai_story_studio_server.dto.ai.ParseTextRequest;
import com.ym.ai_story_studio_server.dto.ai.ShotVideoGenerateRequest;
import com.ym.ai_story_studio_server.entity.Job;
import com.ym.ai_story_studio_server.entity.JobItem;
import com.ym.ai_story_studio_server.entity.Project;
import com.ym.ai_story_studio_server.entity.ProjectCharacter;
import com.ym.ai_story_studio_server.entity.ProjectProp;
import com.ym.ai_story_studio_server.entity.ProjectScene;
import com.ym.ai_story_studio_server.entity.StoryboardShot;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.mapper.JobMapper;
import com.ym.ai_story_studio_server.mapper.JobItemMapper;
import com.ym.ai_story_studio_server.mapper.ProjectCharacterMapper;
import com.ym.ai_story_studio_server.mapper.ProjectMapper;
import com.ym.ai_story_studio_server.mapper.ProjectSceneMapper;
import com.ym.ai_story_studio_server.mapper.ProjectPropMapper;
import com.ym.ai_story_studio_server.mapper.StoryboardShotMapper;
import com.ym.ai_story_studio_server.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
/**
 * 批量生成服务
 *
 * <p>提供批量生成分镜图、视频、角色画像、场景画像以及文本解析的核心业务逻辑
 *
 * <p><strong>核心功能:</strong>
 * <ul>
 *   <li>批量生成分镜图 - 为多个分镜批量生成图片资产</li>
 *   <li>批量生成视频 - 为多个分镜批量生成视频资产</li>
 *   <li>批量生成角色画像 - 为多个角色批量生成图片资产</li>
 *   <li>批量生成场景画像 - 为多个场景批量生成图片资产</li>
 *   <li>单个角色生成 - 为单个角色生成图片资产</li>
 *   <li>单个场景生成 - 为单个场景生成图片资产</li>
 *   <li>文本解析 - 将小说/剧本文本解析为结构化分镜</li>
 * </ul>
 *
 * <p><strong>设计原则:</strong>
 * <ul>
 *   <li>所有批量操作采用异步任务模式,立即返回任务ID</li>
 *   <li>复用单个生成服务的参数配置(model, aspectRatio等)</li>
 *   <li>使用Job表管理任务队列和进度追踪</li>
 *   <li>错误隔离:单个子任务失败不影响其他子任务</li>
 * </ul>
 *
 * <p><strong>异步处理流程:</strong>
 * <ol>
 *   <li>Controller接收批量生成请求</li>
 *   <li>Service创建Job任务记录(状态:PENDING)</li>
 *   <li>Service调用AsyncBatchTaskService执行异步任务</li>
 *   <li>立即返回任务ID和PENDING状态</li>
 *   <li>AsyncBatchTaskService在后台执行实际生成操作</li>
 *   <li>任务完成后更新Job状态为SUCCEEDED或FAILED</li>
 * </ol>
 *
 * <p><strong>使用示例:</strong>
 * <pre>
 * // 批量生成分镜图
 * BatchGenerateRequest request = new BatchGenerateRequest(
 *     Arrays.asList(1L, 2L, 3L),  // 分镜ID列表
 *     "ALL",                       // 全部生成
 *     1,                           // 每个生成1张
 *     "21:9",                      // 画幅比例
 *     "jimeng-4.5"                 // 模型
 * );
 *
 * BatchGenerateResponse response = batchGenerationService.generateShotsBatch(projectId, request);
 * System.out.println("任务ID: " + response.jobId());
 * // 通过任务ID查询进度: GET /api/jobs/{jobId}
 * </pre>
 *
 * <p><strong>注意事项:</strong>
 * <ul>
 *   <li>批量操作会验证目标ID的有效性和权限</li>
 *   <li>参数model和aspectRatio可选,默认使用配置文件值</li>
 *   <li>MISSING模式会自动跳过已有资产的目标</li>
 *   <li>任务失败时会在Job表中记录错误信息</li>
 * </ul>
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchGenerationService {

    // private final AsyncBatchTaskService asyncBatchTaskService;  // 替换为MQ
    private final com.ym.ai_story_studio_server.mq.MQProducer mqProducer;  // 使用MQ生产者
    private final JobMapper jobMapper;
    private final JobItemMapper jobItemMapper;
    private final ProjectMapper projectMapper;
    private final StoryboardShotMapper storyboardShotMapper;
    private final ProjectCharacterMapper projectCharacterMapper;
    private final ProjectSceneMapper projectSceneMapper;
    private final ProjectPropMapper projectPropMapper;

    /**
     * 批量生成分镜图
     *
     * <p>为指定的分镜列表批量生成图片资产,支持全部生成和仅缺失生成两种模式
     *
     * <p><strong>处理流程:</strong>
     * <ol>
     *   <li>验证项目存在性和权限</li>
     *   <li>验证分镜ID列表的有效性</li>
     *   <li>根据mode过滤目标(ALL-全部, MISSING-仅缺失)</li>
     *   <li>创建Job任务记录</li>
     *   <li>调用异步服务执行批量生成</li>
     *   <li>返回任务ID和PENDING状态</li>
     * </ol>
     *
     * <p><strong>参数说明:</strong>
     * <ul>
     *   <li>targetIds - 分镜ID列表,必填</li>
     *   <li>mode - ALL(全部生成)或MISSING(仅缺失),必填</li>
     *   <li>countPerItem - 每个分镜生成几张图,可选,默认1</li>
     *   <li>aspectRatio - 画幅比例,可选,默认21:9</li>
     *   <li>model - 模型名称,可选,默认gemini-3-pro-image-preview</li>
     * </ul>
     *
     * @param projectId 项目ID
     * @param request 批量生成请求参数
     * @return 批量生成响应(包含jobId和状态)
     * @throws BusinessException 当项目不存在、无权限或分镜ID无效时抛出
     */
    public BatchGenerateResponse generateShotsBatch(Long projectId, BatchGenerateRequest request) {
        Long userId = UserContext.getUserId();
        log.info("批量生成分镜图 - userId: {}, projectId: {}, targetCount: {}, mode: {}",
                userId, projectId, request.targetIds().size(), request.mode());

        // 1. 验证项目存在性和权限
        Project project = validateProjectAccess(userId, projectId);

        // 2. 验证分镜ID列表
        validateShotIds(projectId, request.targetIds());

        // 3. 创建Job任务
        Job job = createBatchJob(userId, projectId, "BATCH_GEN_SHOT_IMG",
                request.targetIds().size());
        log.info("批量生成分镜图任务已创建 - jobId: {}", job.getId());

        // 4. 发送MQ消息执行批量生成
        mqProducer.sendBatchShotImageTask(
                job.getId(),
                userId,
                projectId,
                request.targetIds(),
                request.mode(),
                request.getCountPerItemOrDefault(),
                request.aspectRatio(),
                request.model()
        );

        log.info("批量生成分镜图任务已提交 - jobId: {}", job.getId());

        // 5. 返回响应
        return BatchGenerateResponse.pending(job.getId(), request.targetIds().size());
    }

    /**
     * 批量生成视频
     *
     * <p>为指定的分镜列表批量生成视频资产,支持全部生成和仅缺失生成两种模式
     *
     * <p><strong>处理流程:</strong>
     * <ol>
     *   <li>验证项目存在性和权限</li>
     *   <li>验证分镜ID列表的有效性</li>
     *   <li>根据mode过滤目标(ALL-全部, MISSING-仅缺失)</li>
     *   <li>创建Job任务记录</li>
     *   <li>调用异步服务执行批量生成</li>
     *   <li>返回任务ID和PENDING状态</li>
     * </ol>
     *
     * <p><strong>参数说明:</strong>
     * <ul>
     *   <li>targetIds - 分镜ID列表,必填</li>
     *   <li>mode - ALL(全部生成)或MISSING(仅缺失),必填</li>
     *   <li>countPerItem - 每个分镜生成几个视频,可选,默认1</li>
     *   <li>aspectRatio - 视频画幅比例,可选,默认16:9</li>
     *   <li>model - 模型名称,可选,默认sora-2</li>
     * </ul>
     *
     * @param projectId 项目ID
     * @param request 批量生成请求参数
     * @return 批量生成响应(包含jobId和状态)
     * @throws BusinessException 当项目不存在、无权限或分镜ID无效时抛出
     */
    public BatchGenerateResponse generateVideosBatch(Long projectId, BatchGenerateRequest request) {
        Long userId = UserContext.getUserId();
        log.info("批量生成视频 - userId: {}, projectId: {}, targetCount: {}, mode: {}",
                userId, projectId, request.targetIds().size(), request.mode());

        // 1. 验证项目存在性和权限
        Project project = validateProjectAccess(userId, projectId);

        // 2. 验证分镜ID列表
        validateShotIds(projectId, request.targetIds());

        // 2. 创建Job任务
        Job job = createBatchJob(userId, projectId, "BATCH_GEN_VIDEO",
                request.targetIds().size());
        log.info("批量生成视频任务已创建 - jobId: {}", job.getId());

        // 3. 发送MQ消息执行批量生成
        mqProducer.sendBatchVideoTask(
                job.getId(),
                userId,
                projectId,
                request.targetIds(),
                request.mode(),
                request.getCountPerItemOrDefault(),
                request.aspectRatio(),
                request.model()
        );

        log.info("批量生成视频任务已提交 - jobId: {}", job.getId());

        // 4. 返回响应
        return BatchGenerateResponse.pending(job.getId(), request.targetIds().size());
    }

    /**
     * 批量生成角色画像
     *
     * <p>为指定的角色列表批量生成图片资产,支持全部生成和仅缺失生成两种模式
     *
     * <p><strong>处理流程:</strong>
     * <ol>
     *   <li>验证项目存在性和权限</li>
     *   <li>验证角色ID列表的有效性</li>
     *   <li>根据mode过滤目标(ALL-全部, MISSING-仅缺失)</li>
     *   <li>创建Job任务记录</li>
     *   <li>调用异步服务执行批量生成</li>
     *   <li>返回任务ID和PENDING状态</li>
     * </ol>
     *
     * <p><strong>参数说明:</strong>
     * <ul>
     *   <li>targetIds - 角色ID列表,必填</li>
     *   <li>mode - ALL(全部生成)或MISSING(仅缺失),必填</li>
     *   <li>countPerItem - 每个角色生成几张图,可选,默认1</li>
     *   <li>aspectRatio - 画幅比例,可选,默认21:9</li>
     *   <li>model - 模型名称,可选,默认gemini-3-pro-image-preview</li>
     * </ul>
     *
     * @param projectId 项目ID
     * @param request 批量生成请求参数
     * @return 批量生成响应(包含jobId和状态)
     * @throws BusinessException 当项目不存在、无权限或角色ID无效时抛出
     */
    public BatchGenerateResponse generateCharactersBatch(Long projectId, BatchGenerateRequest request) {
        Long userId = UserContext.getUserId();
        log.info("批量生成角色画像 - userId: {}, projectId: {}, targetCount: {}, mode: {}",
                userId, projectId, request.targetIds().size(), request.mode());

        // 1. 验证项目存在性和权限
        Project project = validateProjectAccess(userId, projectId);

        // 2. 验证角色ID列表
        validateProjectCharacterIds(projectId, request.targetIds());

        // 2. 创建Job任务
        Job job = createBatchJob(userId, projectId, "BATCH_GEN_CHAR_IMG",
                request.targetIds().size());
        log.info("批量生成角色画像任务已创建 - jobId: {}", job.getId());

        // 3. 发送MQ消息执行批量生成
        mqProducer.sendBatchCharacterImageTask(
                job.getId(),
                userId,
                projectId,
                request.targetIds(),
                request.mode(),
                request.getCountPerItemOrDefault(),
                request.aspectRatio(),
                request.model()
        );

        log.info("批量生成角色画像任务已提交 - jobId: {}", job.getId());

        // 4. 返回响应
        return BatchGenerateResponse.pending(job.getId(), request.targetIds().size());
    }

    /**
     * 批量生成场景画像
     *
     * <p>为指定的场景列表批量生成图片资产,支持全部生成和仅缺失生成两种模式
     *
     * <p><strong>处理流程:</strong>
     * <ol>
     *   <li>验证项目存在性和权限</li>
     *   <li>验证场景ID列表的有效性</li>
     *   <li>根据mode过滤目标(ALL-全部, MISSING-仅缺失)</li>
     *   <li>创建Job任务记录</li>
     *   <li>调用异步服务执行批量生成</li>
     *   <li>返回任务ID和PENDING状态</li>
     * </ol>
     *
     * <p><strong>参数说明:</strong>
     * <ul>
     *   <li>targetIds - 场景ID列表,必填</li>
     *   <li>mode - ALL(全部生成)或MISSING(仅缺失),必填</li>
     *   <li>countPerItem - 每个场景生成几张图,可选,默认1</li>
     *   <li>aspectRatio - 画幅比例,可选,默认21:9</li>
     *   <li>model - 模型名称,可选,默认gemini-3-pro-image-preview</li>
     * </ul>
     *
     * @param projectId 项目ID
     * @param request 批量生成请求参数
     * @return 批量生成响应(包含jobId和状态)
     * @throws BusinessException 当项目不存在、无权限或场景ID无效时抛出
     */
    public BatchGenerateResponse generateScenesBatch(Long projectId, BatchGenerateRequest request) {
        Long userId = UserContext.getUserId();
        log.info("批量生成场景画像 - userId: {}, projectId: {}, targetCount: {}, mode: {}",
                userId, projectId, request.targetIds().size(), request.mode());

        // 1. 验证项目存在性和权限
        Project project = validateProjectAccess(userId, projectId);

        // 2. 验证场景ID列表
        validateProjectSceneIds(projectId, request.targetIds());

        // 2. 创建Job任务
        Job job = createBatchJob(userId, projectId, "BATCH_GEN_SCENE_IMG",
                request.targetIds().size());
        log.info("批量生成场景画像任务已创建 - jobId: {}", job.getId());

        // 3. 发送MQ消息执行批量生成
        mqProducer.sendBatchSceneImageTask(
                job.getId(),
                userId,
                projectId,
                request.targetIds(),
                request.mode(),
                request.getCountPerItemOrDefault(),
                request.aspectRatio(),
                request.model()
        );

        log.info("批量生成场景画像任务已提交 - jobId: {}", job.getId());

        // 4. 返回响应
        return BatchGenerateResponse.pending(job.getId(), request.targetIds().size());
    }

    /**
     * 单个角色生成
     *
     * <p>为单个角色生成图片资产
     *
     * @param projectId 项目ID
     * @param characterId 角色ID
     * @param aspectRatio 画幅比例(可选)
     * @param model 模型名称(可选)
     * @return 批量生成响应(包含jobId和状态)
     * @throws BusinessException 当项目不存在、无权限或角色ID无效时抛出
     */
    public BatchGenerateResponse generateSingleCharacter(Long projectId, Long characterId,
                                                          String aspectRatio, String model) {
        Long userId = UserContext.getUserId();
        log.info("单个角色生成 - userId: {}, projectId: {}, characterId: {}",
                userId, projectId, characterId);

        // 复用批量生成逻辑
        BatchGenerateRequest request = new BatchGenerateRequest(
                java.util.List.of(characterId),
                "ALL",
                1,
                aspectRatio,
                model
        );

        return generateCharactersBatch(projectId, request);
    }

    /**
     * 单个场景生成
     *
     * <p>为单个场景生成图片资产
     *
     * @param projectId 项目ID
     * @param sceneId 场景ID
     * @param aspectRatio 画幅比例(可选)
     * @param model 模型名称(可选)
     * @return 批量生成响应(包含jobId和状态)
     * @throws BusinessException 当项目不存在、无权限或场景ID无效时抛出
     */
    public BatchGenerateResponse generateSingleScene(Long projectId, Long sceneId,
                                                      String aspectRatio, String model) {
        Long userId = UserContext.getUserId();
        log.info("单个场景生成 - userId: {}, projectId: {}, sceneId: {}",
                userId, projectId, sceneId);

        // 复用批量生成逻辑
        BatchGenerateRequest request = new BatchGenerateRequest(
                java.util.List.of(sceneId),
                "ALL",
                1,
                aspectRatio,
                model
        );

        return generateScenesBatch(projectId, request);
    }

    /**
     * 批量生成道具画像
     *
     * <p>为指定的道具列表批量生成图片资产,支持全部生成和仅缺失生成两种模式
     *
     * @param projectId 项目ID
     * @param request 批量生成请求参数
     * @return 批量生成响应(包含jobId和状态)
     * @throws BusinessException 当项目不存在、无权限或道具ID无效时抛出
     */
    public BatchGenerateResponse generatePropsBatch(Long projectId, BatchGenerateRequest request) {
        Long userId = UserContext.getUserId();
        log.info("批量生成道具画像 - userId: {}, projectId: {}, targetCount: {}, mode: {}",
                userId, projectId, request.targetIds().size(), request.mode());

        // 1. 验证项目存在性和权限
        Project project = validateProjectAccess(userId, projectId);

        // 2. 验证道具ID列表
        validateProjectPropIds(projectId, request.targetIds());

        // 2. 创建Job任务
        Job job = createBatchJob(userId, projectId, "BATCH_GEN_PROP_IMG",
                request.targetIds().size());
        log.info("批量生成道具画像任务已创建 - jobId: {}", job.getId());

        // 3. 发送MQ消息执行批量生成
        mqProducer.sendBatchPropImageTask(
                job.getId(),
                userId,
                projectId,
                request.targetIds(),
                request.mode(),
                request.getCountPerItemOrDefault(),
                request.aspectRatio(),
                request.model()
        );

        log.info("批量生成道具画像任务已提交 - jobId: {}", job.getId());

        // 4. 返回响应
        return BatchGenerateResponse.pending(job.getId(), request.targetIds().size());
    }

    /**
     * 单个道具生成
     *
     * <p>为单个道具生成图片资产
     *
     * @param projectId 项目ID
     * @param propId 项目道具ID
     * @param aspectRatio 画幅比例(可选)
     * @param model 模型名称(可选)
     * @return 批量生成响应(包含jobId和状态)
     * @throws BusinessException 当项目不存在、无权限或道具ID无效时抛出
     */
    public BatchGenerateResponse generateSingleProp(Long projectId, Long propId,
                                                      String aspectRatio, String model) {
        Long userId = UserContext.getUserId();
        log.info("单个道具生成 - userId: {}, projectId: {}, propId: {}",
                userId, projectId, propId);

        // 复用批量生成逻辑
        BatchGenerateRequest request = new BatchGenerateRequest(
                java.util.List.of(propId),
                "ALL",
                1,
                aspectRatio,
                model
        );

        return generatePropsBatch(projectId, request);
    }

    /**
     * 单个分镜生成
     *
     * <p>为单个分镜生成图片资产，支持自定义prompt
     *
     * @param projectId 项目ID
     * @param shotId 分镜ID
     * @param aspectRatio 画幅比例(可选)
     * @param model 模型名称(可选)
     * @param customPrompt 自定义提示词(可选，如果提供则使用，否则使用分镜剧本)
     * @return 批量生成响应(包含jobId和状态)
     * @throws BusinessException 当项目不存在、无权限或分镜ID无效时抛出
     */
    public BatchGenerateResponse generateSingleShot(Long projectId, Long shotId,
                                                     String aspectRatio, String model, String customPrompt, List<String> referenceImageUrls) {
        Long userId = UserContext.getUserId();
        log.info("单个分镜生成 - userId: {}, projectId: {}, shotId: {}, customPrompt: {}, referenceImageUrls: {}",
                userId, projectId, shotId, customPrompt != null ? "自定义" : "默认",
                referenceImageUrls != null ? referenceImageUrls.size() : 0);

        // 验证项目存在性和权限
        validateProjectAccess(userId, projectId);
        validateShotIds(projectId, java.util.List.of(shotId));

        // 创建 Job 任务
        Job job = createBatchJob(userId, projectId, "BATCH_GEN_SHOT_IMG", 1);
        log.info("单个分镜生成任务已创建 - jobId: {}", job.getId());

        // 发送MQ消息执行单个分镜生成(支持自定义prompt和参考图)
        mqProducer.sendSingleShotImageTask(
                job.getId(),
                userId,
                projectId,
                shotId,
                aspectRatio,
                model,
                customPrompt,
                referenceImageUrls
        );

        log.info("单个分镜生成任务已提交 - jobId: {}", job.getId());

        // 返回响应
        return BatchGenerateResponse.pending(job.getId(), 1);
    }

    /**
     * 解析文本
     *
     * <p>将小说/剧本文本解析为结构化的分镜脚本
     *
     * <p><strong>处理流程:</strong>
     * <ol>
     *   <li>验证项目存在性和权限</li>
     *   <li>创建Job任务记录</li>
     *   <li>调用异步服务执行文本解析</li>
     *   <li>返回任务ID和PENDING状态</li>
     * </ol>
     *
     * @param projectId 项目ID
     * @param request 文本解析请求参数
     * @return 批量生成响应(包含jobId和状态)
     * @throws BusinessException 当项目不存在或无权限时抛出
     */
    public BatchGenerateResponse parseText(Long projectId, ParseTextRequest request) {
        Long userId = UserContext.getUserId();
        log.info("解析文本 - userId: {}, projectId: {}, textLength: {}",
                userId, projectId, request.rawText().length());

        // 1. 验证项目存在性和权限
        Project project = validateProjectAccess(userId, projectId);

        // 2. 创建Job任务
        Job job = createBatchJob(userId, projectId, "PARSE_TEXT", 1);
        log.info("文本解析任务已创建 - jobId: {}", job.getId());

        // 3. 发送MQ消息执行文本解析
        mqProducer.sendTextParsingTask(
                job.getId(),
                userId,
                projectId,
                request.rawText()
        );

        log.info("文本解析任务已提交 - jobId: {}", job.getId());

        // 4. 返回响应
        return BatchGenerateResponse.pending(job.getId(), 1);
    }

    /**
     * 单个分镜视频生成
     *
     * <p>为单个分镜生成视频,整合剧本、场景、角色、道具等资源
     *
     * <p><strong>处理流程:</strong>
     * <ol>
     *   <li>验证项目存在性和权限</li>
     *   <li>验证分镜ID的有效性</li>
     *   <li>创建Job任务记录</li>
     *   <li>发送MQ消息执行视频生成</li>
     *   <li>返回任务ID和PENDING状态</li>
     * </ol>
     *
     * @param projectId 项目ID
     * @param shotId 分镜ID
     * @param request 视频生成请求参数
     * @return 批量生成响应(包含jobId和状态)
     * @throws BusinessException 当项目不存在、无权限或分镜ID无效时抛出
     */
    public BatchGenerateResponse generateSingleShotVideo(Long projectId, Long shotId, ShotVideoGenerateRequest request) {
        Long userId = UserContext.getUserId();
        log.info("单个分镜视频生成 - userId: {}, projectId: {}, shotId: {}, promptLength: {}",
                userId, projectId, shotId, request.prompt().length());

        // 1. 验证项目存在性和权限
        Project project = validateProjectAccess(userId, projectId);

        // 2. 验证分镜ID是否存在
        var shot = storyboardShotMapper.selectById(shotId);
        if (shot == null || shot.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "分镜不存在");
        }
        if (!shot.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED, "分镜不属于该项目");
        }

        // 3. 创建Job任务
        Job job = createBatchJob(userId, projectId, "SINGLE_SHOT_VIDEO", 1);
        log.info("单个分镜视频生成任务已创建 - jobId: {}", job.getId());

        // 4. 发送MQ消息执行视频生成
        // TODO: 根据实际MQ生产者接口调整
        mqProducer.sendSingleShotVideoTask(
                job.getId(),
                userId,
                projectId,
                shotId,
                request.prompt(),
                request.aspectRatio(),
                request.referenceImageUrl(),
                request.scene(),
                request.characters(),
                request.props()
        );

        log.info("单个分镜视频生成任务已提交 - jobId: {}", job.getId());

        // 5. 返回响应
        return BatchGenerateResponse.pending(job.getId(), 1);
    }

    /**
     * 验证项目访问权限
     *
     * @param userId 用户ID
     * @param projectId 项目ID
     * @return 项目对象
     * @throws BusinessException 当项目不存在或无权限时抛出
     */
    private Project validateProjectAccess(Long userId, Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        }

        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        return project;
    }

    private void validateShotIds(Long projectId, List<Long> shotIds) {
        if (shotIds == null || shotIds.isEmpty()) {
            return;
        }

        List<StoryboardShot> shots = storyboardShotMapper.selectBatchIds(shotIds).stream()
                .filter(shot -> projectId.equals(shot.getProjectId()))
                .collect(Collectors.toList());

        if (shots.size() != shotIds.size()) {
            List<Long> validIds = shots.stream()
                    .map(StoryboardShot::getId)
                    .collect(Collectors.toList());
            List<Long> invalidIds = shotIds.stream()
                    .filter(id -> !validIds.contains(id))
                    .collect(Collectors.toList());
            throw new BusinessException(ResultCode.PARAM_INVALID, "部分分镜ID无效: " + invalidIds);
        }
    }

    private void validateProjectCharacterIds(Long projectId, List<Long> characterIds) {
        if (characterIds == null || characterIds.isEmpty()) {
            return;
        }

        List<ProjectCharacter> characters = projectCharacterMapper.selectBatchIds(characterIds).stream()
                .filter(character -> projectId.equals(character.getProjectId()))
                .collect(Collectors.toList());

        if (characters.size() != characterIds.size()) {
            List<Long> validIds = characters.stream()
                    .map(ProjectCharacter::getId)
                    .collect(Collectors.toList());
            List<Long> invalidIds = characterIds.stream()
                    .filter(id -> !validIds.contains(id))
                    .collect(Collectors.toList());
            throw new BusinessException(ResultCode.PARAM_INVALID, "部分角色ID无效: " + invalidIds);
        }
    }

    private void validateProjectSceneIds(Long projectId, List<Long> sceneIds) {
        if (sceneIds == null || sceneIds.isEmpty()) {
            return;
        }

        List<ProjectScene> scenes = projectSceneMapper.selectBatchIds(sceneIds).stream()
                .filter(scene -> projectId.equals(scene.getProjectId()))
                .collect(Collectors.toList());

        if (scenes.size() != sceneIds.size()) {
            List<Long> validIds = scenes.stream()
                    .map(ProjectScene::getId)
                    .collect(Collectors.toList());
            List<Long> invalidIds = sceneIds.stream()
                    .filter(id -> !validIds.contains(id))
                    .collect(Collectors.toList());
            throw new BusinessException(ResultCode.PARAM_INVALID, "部分场景ID无效: " + invalidIds);
        }
    }

    private void validateProjectPropIds(Long projectId, List<Long> propIds) {
        if (propIds == null || propIds.isEmpty()) {
            return;
        }

        List<ProjectProp> props = projectPropMapper.selectBatchIds(propIds).stream()
                .filter(prop -> projectId.equals(prop.getProjectId()))
                .collect(Collectors.toList());

        if (props.size() != propIds.size()) {
            List<Long> validIds = props.stream()
                    .map(ProjectProp::getId)
                    .collect(Collectors.toList());
            List<Long> invalidIds = propIds.stream()
                    .filter(id -> !validIds.contains(id))
                    .collect(Collectors.toList());
            throw new BusinessException(ResultCode.PARAM_INVALID, "部分道具ID无效: " + invalidIds);
        }
    }


    /**
     * Create a batch job record.
     */
    private Job createBatchJob(Long userId, Long projectId, String jobType, int totalItems) {
        Job job = new Job();
        job.setUserId(userId);
        job.setProjectId(projectId);
        job.setJobType(jobType);
        job.setStatus("PENDING");
        job.setProgress(0);
        job.setTotalItems(totalItems);
        job.setDoneItems(0);
        jobMapper.insert(job);
        return job;
    }

    /**
     * Create job items for batch jobs.
     */
    private void createJobItems(Long jobId, String targetType, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        for (Long targetId : targetIds) {
            JobItem item = new JobItem();
            item.setJobId(jobId);
            item.setTargetType(targetType);
            item.setTargetId(targetId);
            item.setStatus("PENDING");
            jobItemMapper.insert(item);
        }
    }
}
