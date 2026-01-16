package com.ym.ai_story_studio_server.service;

import com.ym.ai_story_studio_server.client.VectorEngineClient;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.config.AiProperties;
import com.ym.ai_story_studio_server.dto.ai.ImageGenerateRequest;
import com.ym.ai_story_studio_server.dto.ai.ImageGenerateResponse;
import com.ym.ai_story_studio_server.entity.Job;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.mapper.JobMapper;
import com.ym.ai_story_studio_server.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI图片生成服务
 *
 * <p>提供AI图片生成能力,支持多种图片生成模型
 *
 * <p><strong>核心功能:</strong>
 * <ul>
 *   <li>调用向量引擎API生成图片</li>
 *   <li>支持文生图和图生图两种模式</li>
 *   <li>自动下载生成的图片并上传到OSS</li>
 *   <li>自动应用配置中的默认参数</li>
 *   <li>创建和管理任务记录</li>
 *   <li>自动进行积分计费</li>
 * </ul>
 *
 * <p><strong>支持的模型:</strong>
 * <ul>
 *   <li>gemini-3-pro-image-preview - Google Gemini图片生成模型(默认)</li>
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
 * // 文生图
 * ImageGenerateRequest request = new ImageGenerateRequest(
 *     "一个科幻风格的未来城市，赛博朋克，霓虹灯",
 *     "gemini-3-pro-image-preview",  // 可选,默认从配置读取
 *     "16:9",                         // 可选,默认21:9
 *     null                            // 参考图URL(可选)
 * );
 *
 * ImageGenerateResponse response = aiImageService.generateImage(request);
 * System.out.println("图片URL: " + response.imageUrl());
 * System.out.println("消耗积分: " + response.costPoints());
 *
 * // 图生图
 * ImageGenerateRequest request = new ImageGenerateRequest(
 *     "将图片风格改为水彩画",
 *     "jimeng-4.5",
 *     "1:1",
 *     "https://example.com/reference.jpg"  // 参考图
 * );
 * </pre>
 *
 * <p><strong>计费规则:</strong>
 * 按张数计费,费用从用户积分钱包扣除,具体单价由pricing_rules表配置
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageService {

    private final VectorEngineClient vectorEngineClient;
    private final ChargingService chargingService;
    private final StorageService storageService;
    private final JobMapper jobMapper;
    private final AiProperties aiProperties;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 异步生成图片（工具箱使用）
     *
     * <p>立即返回jobId，在后台异步处理生成任务
     *
     * @param request 图片生成请求参数
     * @return 图片生成响应（仅包含jobId和model）
     */
    public ImageGenerateResponse generateImageAsync(ImageGenerateRequest request) {
        Long userId = UserContext.getUserId();
        log.info("异步图片生成任务提交 - userId: {}, promptLength: {}",
                userId, request.prompt().length());

        // 1. 应用默认配置
        AiProperties.Image imageConfig = aiProperties.getImage();
        String model = request.model() != null ? request.model() : imageConfig.getDefaultModel();
        String aspectRatio = request.aspectRatio() != null ? request.aspectRatio() : imageConfig.getDefaultAspectRatio();

        if (request.model() == null && imageConfig.getJimengProxyEnabled()) {
            model = imageConfig.getJimengModel();
        }

        // 2. 创建任务记录
        Job job = createJob(userId, request.projectId(), model, aspectRatio, request.prompt());
        log.info("Job created - jobId: {}, jobType: IMAGE_GENERATION", job.getId());

        // 3. 异步执行生成
        executeImageGenerationAsync(job.getId(), request, model, aspectRatio);

        // 4. 立即返回jobId
        return new ImageGenerateResponse(
                null,  // imageUrls将在异步生成后写入Job
                null,  // primaryImageUrl
                job.getId(),
                model,
                aspectRatio,
                null   // costPoints将在生成完成后计算
        );
    }

    /**
     * 异步执行图片生成任务
     */
    @org.springframework.scheduling.annotation.Async("taskExecutor")
    public void executeImageGenerationAsync(Long jobId, ImageGenerateRequest request, String model, String aspectRatio) {
        try {
            // 从数据库加载Job
            Job job = jobMapper.selectById(jobId);
            if (job == null) {
                log.error("任务不存在 - jobId: {}", jobId);
                return;
            }

            // 设置用户上下文
            UserContext.setUserId(job.getUserId());

            log.info("开始异步生成图片 - jobId: {}", jobId);

            // ✅ 步骤1: 先进行积分扣费检查(预扣费)
            Map<String, Object> metaData = new HashMap<>();
            metaData.put("model", model);
            metaData.put("aspectRatio", aspectRatio);
            metaData.put("prompt", request.prompt());

            ChargingService.ChargingResult chargingResult = chargingService.charge(
                    ChargingService.ChargingRequest.builder()
                            .jobId(job.getId())
                            .bizType("IMAGE_GENERATION")
                            .modelCode(model)
                            .quantity(1)
                            .metaData(metaData)
                            .build()
            );

            log.info("预扣费完成 - jobId: {}, cost: {}, balanceAfter: {}", 
                    jobId, chargingResult.getTotalCost(), chargingResult.getBalanceAfter());

            // ✅ 步骤2: 调用向量引擎API生成图片
            VectorEngineClient.ImageApiResponse apiResponse = vectorEngineClient.generateImage(
                    request.prompt(),
                    model,
                    aspectRatio,
                    request.referenceImageUrlList()
            );

            // 空值安全检查
            if (apiResponse == null || apiResponse.data() == null || apiResponse.data().isEmpty()) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "向量引擎API返回的图片数据为空");
            }

            // 提取所有生成的图片数据
            List<String> allImageData = apiResponse.data().stream()
                    .map(VectorEngineClient.ImageApiResponse.ImageData::url)
                    .filter(url -> url != null && !url.isBlank())
                    .toList();

            if (allImageData.isEmpty()) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "向量引擎API返回的图片数据为空");
            }

            log.info("图片生成成功 - jobId: {}, totalImages: {}", jobId, allImageData.size());

            // ✅ 步骤3: 批量处理所有图片结果并上传到OSS
            List<String> ossImageUrls = new java.util.ArrayList<>();
            for (int i = 0; i < allImageData.size(); i++) {
                String imageData = allImageData.get(i);
                String ossUrl = processImageResult(imageData, jobId, i);
                ossImageUrls.add(ossUrl);
            }

            String primaryImageUrl = ossImageUrls.get(0);

            log.info("所有图片已上传到OSS - jobId: {}", jobId);

            // ✅ 步骤4: 更新任务状态为成功
            job.setCostPoints(chargingResult.getTotalCost()); // 设置消耗积分
            updateJobSuccess(job, model, aspectRatio, request.prompt(), primaryImageUrl, ossImageUrls);

            log.info("异步图片生成完成 - jobId: {}, cost: {}", jobId, chargingResult.getTotalCost());

        } catch (Exception e) {
            log.error("异步图片生成失败 - jobId: {}", jobId, e);
            Job job = jobMapper.selectById(jobId);
            if (job != null) {
                UserContext.setUserId(job.getUserId());
                updateJobFailed(job, e.getMessage());
            }
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 生成图片
     *
     * <p>调用图片生成模型创建图像,完整流程包括:
     * <ol>
     *   <li>参数验证和默认值应用</li>
     *   <li>创建任务记录</li>
     *   <li>调用向量引擎API生成图片</li>
     *   <li>从返回的URL下载图片</li>
     *   <li>上传图片到OSS存储</li>
     *   <li>进行积分计费</li>
     *   <li>更新任务状态为成功</li>
     *   <li>返回生成结果</li>
     * </ol>
     *
     * <p><strong>注意事项:</strong>
     * <ul>
     *   <li>该方法使用@Transactional确保任务创建和计费的原子性</li>
     *   <li>如果API调用失败,任务状态会更新为FAILED</li>
     *   <li>积分余额不足会抛出异常,不会创建任务</li>
     *   <li>生成的图片会自动上传到OSS,原始URL不会返回</li>
     * </ul>
     *
     * @param request 图片生成请求参数
     * @return 图片生成响应(包含OSS图片URL、消耗积分等)
     * @throws BusinessException 当API调用失败、余额不足或参数错误时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public ImageGenerateResponse generateImage(ImageGenerateRequest request) {
        Long userId = UserContext.getUserId();
        log.info("Starting image generation - userId: {}, promptLength: {}",
                userId, request.prompt().length());

        // 1. 应用默认配置
        AiProperties.Image imageConfig = aiProperties.getImage();
        String model = request.model() != null ? request.model() : imageConfig.getDefaultModel();
        String aspectRatio = request.aspectRatio() != null ? request.aspectRatio() : imageConfig.getDefaultAspectRatio();

        // 如果启用了即梦反代且用户未指定模型,则使用即梦模型
        if (request.model() == null && imageConfig.getJimengProxyEnabled()) {
            model = imageConfig.getJimengModel();
            log.debug("Using jimeng proxy model: {}", model);
        }

        log.debug("Applied config - model: {}, aspectRatio: {}", model, aspectRatio);

        // 2. 创建任务记录 (✅ 修复: 传入 prompt)
        Job job = createJob(userId, request.projectId(), model, aspectRatio, request.prompt());
        log.info("Job created - jobId: {}, jobType: IMAGE_GENERATION", job.getId());

        try {
            // 3. 进行积分计费(预扣费) - ✅ 先扣费后生成
            log.debug("Starting charging process (pre-charge)...");
            Map<String, Object> chargingMetaData = new HashMap<>();
            chargingMetaData.put("model", model);
            chargingMetaData.put("aspectRatio", aspectRatio);
            chargingMetaData.put("prompt", request.prompt());
            if (!request.referenceImageUrlList().isEmpty()) {
                chargingMetaData.put("referenceImages", request.referenceImageUrlList());
            }

            ChargingService.ChargingResult chargingResult = chargingService.charge(
                    ChargingService.ChargingRequest.builder()
                            .jobId(job.getId())
                            .bizType("IMAGE_GENERATION")
                            .modelCode(model)
                            .quantity(1)  // ✅ 按固定批次计费(1次),不按图片张数
                            .metaData(chargingMetaData)
                            .build()
            );

            log.info("Pre-charging completed - jobId: {}, cost: {} points, balanceAfter: {}",
                    job.getId(), chargingResult.getTotalCost(), chargingResult.getBalanceAfter());

            // 4. 调用向量引擎API生成图片
            log.debug("Calling VectorEngineClient.generateImage...");
            VectorEngineClient.ImageApiResponse apiResponse = vectorEngineClient.generateImage(
                    request.prompt(),
                    model,
                    aspectRatio,
                    request.referenceImageUrlList()
            );

            // 空值安全检查: 验证API响应的完整性
            if (apiResponse == null) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "向量引擎API返回null响应");
            }
            if (apiResponse.data() == null) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "向量引擎API返回的data字段为null - model: " + model);
            }
            if (apiResponse.data().isEmpty()) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "向量引擎API返回的图片列表为空 - model: " + model);
            }

            // 提取所有生成的图片数据(URL或base64)
            List<String> allImageData = apiResponse.data().stream()
                    .map(VectorEngineClient.ImageApiResponse.ImageData::url)
                    .filter(url -> url != null && !url.isBlank())
                    .toList();

            if (allImageData.isEmpty()) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "向量引擎API返回的图片数据为空 - model: " + model);
            }

            log.info("Images generated - jobId: {}, totalImages: {}, firstDataType: {}",
                    job.getId(),
                    allImageData.size(),
                    isBase64(allImageData.get(0)) ? "base64" : "url");

            // 5. 批量处理所有图片结果(base64或URL)并上传到OSS
            log.debug("Processing {} image results and uploading to OSS...", allImageData.size());
            List<String> ossImageUrls = new java.util.ArrayList<>();

            for (int i = 0; i < allImageData.size(); i++) {
                String imageData = allImageData.get(i);
                String ossUrl = processImageResult(imageData, job.getId(), i);
                ossImageUrls.add(ossUrl);
                log.debug("Image uploaded to OSS - index: {}, jobId: {}, ossUrl: {}",
                        i, job.getId(), ossUrl);
            }

            log.info("All images uploaded to OSS - jobId: {}, totalImages: {}",
                    job.getId(), ossImageUrls.size());

            // 主图片URL(第一张,用于向后兼容)
            String primaryImageUrl = ossImageUrls.get(0);

            // 6. 更新任务状态为成功 (✅ 修复: 传入 model, aspectRatio, prompt, imageUrl, allImageUrls)
            updateJobSuccess(job, model, aspectRatio, request.prompt(), primaryImageUrl, ossImageUrls);

            // 7. 返回响应(包含所有生成的图片)
            return new ImageGenerateResponse(
                    ossImageUrls,          // 所有生成的图片URL
                    primaryImageUrl,        // 主图片URL(第一张,向后兼容)
                    job.getId(),
                    model,
                    aspectRatio,
                    chargingResult.getTotalCost()
            );

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 任务失败,更新任务状态
            log.error("Image generation failed - jobId: {}", job.getId(), e);
            updateJobFailed(job, e.getMessage());
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "图片生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理图片结果并上传到OSS
     *
     * <p>自动识别数据类型(base64或URL)并调用相应的处理方法
     *
     * @param imageData 图片数据(base64字符串或URL)
     * @param jobId 任务ID(用于生成文件名)
     * @param index 图片索引(当生成多张图片时使用,用于生成不同的文件名)
     * @return OSS存储的图片URL
     * @throws BusinessException 当处理失败时抛出
     */
    private String processImageResult(String imageData, Long jobId, int index) {
        // 判断1: 是否为base64编码
        if (isBase64(imageData)) {
            log.debug("Detected base64 image data - index: {}, length: {}", index, imageData.length());
            return uploadBase64ToOss(imageData, jobId, index);
        }

        // 判断2: 是否为URL
        if (isUrl(imageData)) {
            log.debug("Detected image URL - index: {}, url: {}", index, imageData);
            return downloadAndUploadToOss(imageData, jobId, index);
        }

        throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                "无法识别的图片数据格式,既不是base64也不是URL");
    }

    /**
     * 处理图片结果并上传到OSS(单图模式,向后兼容)
     *
     * <p>自动识别数据类型(base64或URL)并调用相应的处理方法
     *
     * @param imageData 图片数据(base64字符串或URL)
     * @param jobId 任务ID(用于生成文件名)
     * @return OSS存储的图片URL
     * @throws BusinessException 当处理失败时抛出
     */
    private String processImageResult(String imageData, Long jobId) {
        return processImageResult(imageData, jobId, 0);
    }

    /**
     * 上传base64编码的图片到OSS
     *
     * @param base64Data base64编码的图片数据
     * @param jobId 任务ID(用于生成文件名)
     * @return OSS存储的图片URL
     * @throws BusinessException 当上传失败时抛出
     */
    private String uploadBase64ToOss(String base64Data, Long jobId, int index) {
        try {
            // 1. 解码base64 → byte[]
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            log.debug("Base64 decoded - size: {} bytes", imageBytes.length);

            // 2. 包装为InputStream
            InputStream inputStream = new ByteArrayInputStream(imageBytes);

            // 3. 生成文件名(包含索引,用于区分多张图片)
            String fileName = String.format("ai_image_%d_%d.png", jobId, index);

            // 4. 上传到OSS
            String ossUrl = storageService.upload(inputStream, fileName, "image/png");
            log.debug("Base64 image uploaded to OSS successfully - index: {}, ossUrl: {}", index, ossUrl);
            return ossUrl;

        } catch (IllegalArgumentException e) {
            log.error("Failed to decode base64 image data", e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "base64解码失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to upload base64 image to OSS", e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "base64图片上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断字符串是否为base64编码
     *
     * <p>简化判断逻辑：如果不是URL且长度足够长，就认为是base64
     * （向量引擎API只返回URL或base64两种格式）
     *
     * @param data 待判断的字符串
     * @return true表示是base64编码
     */
    private boolean isBase64(String data) {
        if (data == null || data.isBlank()) {
            return false;
        }

        // 简化判断：不是URL + 长度>100 = base64
        // 不使用正则表达式，避免大字符串性能问题
        return data.length() > 100
                && !data.startsWith("http://")
                && !data.startsWith("https://");
    }

    /**
     * 判断字符串是否为URL
     *
     * @param data 待判断的字符串
     * @return true表示是URL
     */
    private boolean isUrl(String data) {
        if (data == null || data.isBlank()) {
            return false;
        }

        return data.startsWith("http://") || data.startsWith("https://");
    }

    /**
     * 从URL下载图片并上传到OSS
     *
     * @param imageUrl 图片URL
     * @param jobId 任务ID(用于生成文件名)
     * @return OSS存储的图片URL
     * @throws BusinessException 当下载或上传失败时抛出
     */
    private String downloadAndUploadToOss(String imageUrl, Long jobId, int index) {
        try {
            // 1. 从URL下载图片
            URL url = new URL(imageUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(30000);  // 30秒连接超时
            connection.setReadTimeout(60000);      // 60秒读取超时

            String contentType = connection.getContentType();
            if (contentType == null) {
                contentType = "image/jpeg";  // 默认类型
            }

            // 2. 获取文件扩展名
            String extension = getExtensionFromContentType(contentType);
            String fileName = String.format("ai_image_%d_%d%s", jobId, index, extension);

            log.debug("Downloading image - index: {}, url: {}, contentType: {}, fileName: {}",
                    index, imageUrl, contentType, fileName);

            // 3. 上传到OSS
            try (InputStream inputStream = connection.getInputStream()) {
                String ossUrl = storageService.upload(inputStream, fileName, contentType);
                log.debug("Image uploaded to OSS successfully - ossUrl: {}", ossUrl);
                return ossUrl;
            }

        } catch (Exception e) {
            log.error("Failed to download and upload image - url: {}", imageUrl, e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "图片下载或上传失败: " + e.getMessage(), e);
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

    /**
     * 创建任务记录
     *
     * @param userId 用户ID
     * @param projectId 项目ID
     * @param model 使用的模型
     * @param aspectRatio 画幅比例
     * @param prompt 用户提示词
     * @return 任务对象
     */
    private Job createJob(Long userId, Long projectId, String model, String aspectRatio, String prompt) {
        Job job = new Job();
        job.setUserId(userId);
        job.setProjectId(projectId != null ? projectId : 0L);
        job.setJobType("IMAGE_GENERATION");
        job.setStatus("RUNNING");
        job.setProgress(0);
        job.setTotalItems(1);
        job.setDoneItems(0);

        // ✅ 修复: 使用 ObjectMapper 安全地序列化 JSON
        try {
            Map<String, Object> metaData = new HashMap<>();
            metaData.put("model", model);
            metaData.put("aspectRatio", aspectRatio);
            metaData.put("prompt", prompt);
            job.setMetaJson(objectMapper.writeValueAsString(metaData));
        } catch (Exception e) {
            log.error("Failed to serialize meta_json", e);
            job.setMetaJson(String.format("{\"model\":\"%s\",\"aspectRatio\":\"%s\"}", model, aspectRatio));
        }

        jobMapper.insert(job);
        return job;
    }

    /**
     * 更新任务状态为成功
     *
     * @param job 任务对象
     * @param model 使用的模型
     * @param aspectRatio 画幅比例
     * @param prompt 用户提示词
     * @param imageUrl OSS主图片URL(第一张)
     * @param allImageUrls 所有生成的图片URL列表
     */
    private void updateJobSuccess(Job job, String model, String aspectRatio, String prompt, String imageUrl, List<String> allImageUrls) {
        job.setStatus("SUCCEEDED");
        job.setProgress(100);
        job.setDoneItems(1);
        job.setResultUrl(imageUrl); // ✅ 设置resultUrl(主图片)

        // ✅ 将所有图片URL保存到meta_json中
        try {
            Map<String, Object> metaData = new HashMap<>();
            metaData.put("model", model);
            metaData.put("aspectRatio", aspectRatio);
            metaData.put("prompt", prompt);
            metaData.put("resultUrl", imageUrl);
            metaData.put("allImageUrls", allImageUrls); // 保存所有图片URL
            metaData.put("imageCount", allImageUrls.size()); // 图片总数
            job.setMetaJson(objectMapper.writeValueAsString(metaData));
        } catch (Exception e) {
            log.error("Failed to serialize meta_json", e);
        }

        jobMapper.updateById(job);
        log.info("任务状态已更新为SUCCEEDED - jobId: {}, resultUrl: {}, totalImages: {}", job.getId(), imageUrl, allImageUrls.size());
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
}
