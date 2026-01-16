package com.ym.ai_story_studio_server.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.config.AiProperties;
import com.ym.ai_story_studio_server.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * 向量引擎API客户端
 *
 * <p>封装对向量引擎中转站API的HTTP调用,提供文本、图片、视频生成的统一接口
 *
 * <p><strong>核心功能:</strong>
 * <ul>
 *   <li>文本生成 - 调用大语言模型生成文本内容</li>
 *   <li>图片生成 - 调用图片生成模型创建图像</li>
 *   <li>视频生成 - 调用视频生成模型创建视频(异步)</li>
 *   <li>任务查询 - 查询异步任务的执行状态和结果</li>
 * </ul>
 *
 * <p><strong>技术特性:</strong>
 * <ul>
 *   <li>使用RestClient(Spring 6.1+)进行HTTP调用</li>
 *   <li>支持超时控制(连接超时、读取超时)</li>
 *   <li>统一的异常处理和错误映射</li>
 *   <li>Bearer Token认证</li>
 * </ul>
 *
 * <p><strong>使用示例:</strong>
 * <pre>
 * // 文本生成
 * TextApiResponse response = client.generateText(
 *     "写一个科幻故事",
 *     "gemini-3-pro-preview",
 *     2000,
 *     0.7,
 *     0.9
 * );
 *
 * // 图片生成
 * ImageApiResponse response = client.generateImage(
 *     "一个未来城市的景象",
 *     "gemini-3-pro-image-preview",
 *     "16:9",
 *     List.of()
 * );
 *
 * // 视频生成
 * VideoApiResponse response = client.generateVideo(
 *     "机器人在城市中行走",
 *     "sora-2",
 *     "16:9",
 *     5,
 *     null
 * );
 *
 * // 查询任务状态
 * TaskStatusApiResponse status = client.queryTaskStatus("task_xxx");
 * </pre>
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Component
public class VectorEngineClient {

    private final RestClient restClient;
    private final AiProperties aiProperties;

    /**
     * 构造函数 - 初始化RestClient
     *
     * @param aiProperties AI服务配置属性
     */
    public VectorEngineClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;

        AiProperties.VectorEngine config = aiProperties.getVectorengine();

        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        log.info("VectorEngineClient initialized with baseUrl: {}", config.getBaseUrl());
    }

    /**
     * 生成文本
     *
     * <p>调用大语言模型生成文本内容(同步接口)
     *
     * @param prompt 提示词
     * @param model 模型名称(如: gemini-3-pro-preview)
     * @param maxTokens 最大token数
     * @param temperature 温度参数(0-1,值越大越随机)
     * @param topP 采样参数(0-1,核采样阈值)
     * @return 文本生成API响应
     * @throws BusinessException 当API调用失败时抛出
     */
    public TextApiResponse generateText(
            String prompt,
            String model,
            Integer maxTokens,
            Double temperature,
            Double topP
    ) {
        log.info("Calling text generation API - model: {}, maxTokens: {}", model, maxTokens);

        // 构建消息列表，包含系统提示词和用户提示词
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "你是一个专业的AI写作助手。请始终使用中文回复用户的问题。"),
                Map.of("role", "user", "content", prompt)
        );

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", maxTokens,
                "temperature", temperature,
                "top_p", topP
        );

        try {
            TextApiResponse response = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        String errorBody = new String(httpResponse.getBody().readAllBytes());
                        log.error("Text generation API error - status: {}, body: {}",
                                httpResponse.getStatusCode(), errorBody);
                        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI文本生成失败: " + errorBody);
                    })
                    .body(TextApiResponse.class);

            log.info("Text generation completed - usage: {}, choices: {}",
                    response.usage(), response.choices());
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Text generation failed", e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI文本生成调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成图片 (路由方法)
     *
     * <p>根据模型类型自动路由到不同的生成方法:
     * <ul>
     *   <li>jimeng-4.5 → 即梦反代</li>
     *   <li>gemini-*-image-* → Gemini Chat兼容格式</li>
     *   <li>gpt-4o-image-vip → GPT-4o多模态Chat格式</li>
     *   <li>其他模型 → OpenAI兼容格式</li>
     * </ul>
     *
     * @param prompt 提示词
     * @param model 模型名称(如: gemini-3-pro-image-preview, jimeng-4.5, gpt-4o-image-vip)
     * @param aspectRatio 画幅比例(如: 1:1, 16:9, 9:16, 21:9)
     * @param referenceImageUrls 参考图URL列表(可选,用于图生图)
     * @return 图片生成API响应
     * @throws BusinessException 当API调用失败时抛出
     */
    public ImageApiResponse generateImage(
            String prompt,
            String model,
            String aspectRatio,
            List<String> referenceImageUrls
    ) {
        log.info("Routing image generation - model: {}, aspectRatio: {}", model, aspectRatio);

        // 路由1: 即梦模型 → 转发到Gemini(禁用即梦反代)
        if (aiProperties.getImage().getJimengModel().equals(model) || model.startsWith("jimeng")) {
            log.info("即梦模型已禁用，转发到Gemini: {} -> gemini-3-pro-image-preview", model);
            return generateImageViaGeminiChat(prompt, "gemini-3-pro-image-preview", aspectRatio, referenceImageUrls);
        }

        // 路由2: Gemini图片模型 → 使用Chat兼容格式
        if (model.startsWith("gemini-") && model.contains("-image-")) {
            log.debug("Using Gemini Chat format for model: {}", model);
            return generateImageViaGeminiChat(prompt, model, aspectRatio, referenceImageUrls);
        }

        // 路由3: GPT-4o图片模型 → 使用多模态Chat格式
        if ("gpt-4o-image-vip".equals(model)) {
            log.debug("Using GPT-4o multimodal Chat format for model: {}", model);
            return generateImageViaGpt4oChat(prompt, model, aspectRatio, referenceImageUrls);
        }

        // 路由4: 其他模型 → 使用OpenAI格式(向后兼容)
        log.debug("Using OpenAI format for model: {}", model);
        return generateImageViaOpenAI(prompt, model, aspectRatio, referenceImageUrls);
    }

    /**
     * 通过GPT-4o多模态Chat格式生成图片
     *
     * <p>使用 /v1/chat/completions 端点,支持文本和图片URL的多模态输入
     *
     * <p><strong>请求格式:</strong>
     * <pre>
     * {
     *   "model": "gpt-4o-image-vip",
     *   "messages": [
     *     {
     *       "role": "user",
     *       "content": [
     *         {"type": "text", "text": "美化一下这种图片,加上 我爱中国 四个字 尺寸[4:3]"},
     *         {
     *           "type": "image_url",
     *           "image_url": {"url": "https://example.com/image.png"}
     *         }
     *       ]
     *     }
     *   ]
     * }
     * </pre>
     *
     * @param prompt 提示词
     * @param model 模型名称(gpt-4o-image-vip)
     * @param aspectRatio 画幅比例(如: 4:3, 16:9),会被添加到提示词中
     * @param referenceImageUrls 参考图URL列表(可选,用于图片编辑/美化)
     * @return 图片生成API响应
     * @throws BusinessException 当API调用失败时抛出
     */
    private ImageApiResponse generateImageViaGpt4oChat(
            String prompt,
            String model,
            String aspectRatio,
            List<String> referenceImageUrls
    ) {
        log.info("调用GPT-4o多模态Chat格式图片生成 - 模型: {}, 画幅: {}, 是否有参考图: {}",
                model, aspectRatio, referenceImageUrls != null && !referenceImageUrls.isEmpty());

        // 构建多模态content数组
        List<Map<String, Object>> contentList = new java.util.ArrayList<>();

        // 1. 添加文本提示词(包含画幅比例要求)
        String enhancedPrompt = enhancePromptWithAspectRatio(prompt, aspectRatio);
        contentList.add(Map.of(
                "type", "text",
                "text", enhancedPrompt
        ));
        log.debug("增强后的prompt: {}", enhancedPrompt);

        // 2. 如果有参考图片URL,添加到content中
        if (referenceImageUrls != null && !referenceImageUrls.isEmpty()) {
            for (String url : referenceImageUrls) {
                if (url == null || url.isBlank()) {
                    continue;
                }
                contentList.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", url)
                ));
            }
            log.debug("已添加参考图片URL到content: {}", referenceImageUrls.size());
        }

        // 3. 构建Chat Completions请求体
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", contentList
                        )
                )
        );

        log.debug("GPT-4o请求体 - model: {}, content数量: {}", model, contentList.size());

        try {
            // 调用Chat Completions端点
            TextApiResponse chatResponse = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        String errorBody = new String(httpResponse.getBody().readAllBytes());
                        log.error("GPT-4o多模态Chat生成错误 - 状态码: {}, 响应体: {}",
                                httpResponse.getStatusCode(), errorBody);
                        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "GPT-4o图片生成失败: " + errorBody);
                    })
                    .body(TextApiResponse.class);

            // 提取生成的内容(可能是图片URL或base64)
            if (chatResponse == null || chatResponse.choices() == null || chatResponse.choices().isEmpty()) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "GPT-4o返回响应为空");
            }

            String generatedContent = chatResponse.choices().get(0).message().content();
            log.info("GPT-4o多模态Chat生成完成 - 内容长度: {}", generatedContent != null ? generatedContent.length() : 0);

            // 转换为ImageApiResponse格式
            // 假设GPT-4o返回的是图片URL或base64数据
            ImageApiResponse.ImageData imageData = new ImageApiResponse.ImageData(generatedContent, null);
            return new ImageApiResponse(List.of(imageData), model);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("GPT-4o多模态Chat生成调用失败", e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "GPT-4o图片生成调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通过Gemini原生格式生成图片
     *
     * <p>使用 /v1/models/{model}:generateContent 端点(Gemini原生格式),响应中包含base64编码的图片数据
     *
     * <p><strong>重要:</strong> Gemini图片生成模型必须使用原生格式端点,而非Chat兼容格式。
     * Chat格式(/v1/chat/completions)用于对话式AI,会返回文本而非图片数据。
     *
     * <p><strong>图片编辑:</strong> 当提供referenceImageUrl时,会将参考图片编码为base64并包含在请求中,
     * 实现图生图或图片编辑功能。
     *
     * @param prompt 提示词
     * @param model 模型名称(如: gemini-3-pro-image-preview)
     * @param aspectRatio 画幅比例
     * @param referenceImageUrls 参考图URL列表(可选,用于图生图)
     * @return 图片生成API响应(data[0].url为base64字符串)
     * @throws BusinessException 当API调用失败时抛出
     */
    private ImageApiResponse generateImageViaGeminiChat(
            String prompt,
            String model,
            String aspectRatio,
            List<String> referenceImageUrls
    ) {
        log.info("调用Gemini原生格式图片生成 - 模型: {}, 画幅: {}, 是否有参考图: {}",
                model, aspectRatio, referenceImageUrls != null && !referenceImageUrls.isEmpty());

        // 构建Gemini原生格式请求体
        List<Map<String, Object>> parts = new java.util.ArrayList<>();

        // 1. 添加文本提示词（包含画幅比例要求）
        // Gemini原生API不支持aspect_ratio参数，必须在prompt中明确说明
        String enhancedPrompt = enhancePromptWithAspectRatio(prompt, aspectRatio);
        parts.add(Map.of("text", enhancedPrompt));
        log.debug("增强后的prompt: {}", enhancedPrompt);

        // 2. 如果有参考图片URL,下载并转换为base64添加到请求中
        if (referenceImageUrls != null && !referenceImageUrls.isEmpty()) {
            try {
                for (String url : referenceImageUrls) {
                    if (url == null || url.isBlank()) {
                        continue;
                    }
                    log.debug("开始下载参考图片: {}", url);

                    byte[] imageBytes = RestClient.create()
                            .get()
                            .uri(url)
                            .retrieve()
                            .body(byte[].class);

                    if (imageBytes == null || imageBytes.length == 0) {
                        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "参考图片下载失败或为空");
                    }

                    String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
                    log.debug("参考图片下载成功 - 大小: {} bytes, base64长度: {}",
                            imageBytes.length, base64Image.length());

                    parts.add(Map.of(
                            "inlineData", Map.of(
                                    "mimeType", "image/jpeg",
                                    "data", base64Image
                            )
                    ));
                }

                log.info("参考图片已添加到Gemini请求中 - count: {}", referenceImageUrls.size());

            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("下载或编码参考图片失败", e);
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "下载参考图片失败: " + e.getMessage(), e);
            }
        }

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", parts)
                )
        );

        String endpoint = "/v1/models/" + model + ":generateContent";
        log.debug("Gemini原生端点: {}, 请求parts数量: {}", endpoint, parts.size());

        try {
            // 调用Gemini原生端点
            GeminiNativeImageResponse nativeResponse = restClient.post()
                    .uri(endpoint)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        String errorBody = new String(httpResponse.getBody().readAllBytes());
                        log.error("Gemini原生格式图片生成错误 - 状态码: {}, 响应体: {}",
                                httpResponse.getStatusCode(), errorBody);
                        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "Gemini图片生成失败: " + errorBody);
                    })
                    .body(GeminiNativeImageResponse.class);

            // 诊断日志: 检查响应结构
            if (nativeResponse == null) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "Gemini原生格式响应为null");
            }
            log.debug("Gemini原生格式响应已接收 - candidates数量: {}",
                    nativeResponse.candidates() != null ? nativeResponse.candidates().size() : 0);

            // 提取base64图片数据
            String base64ImageData = nativeResponse.extractBase64ImageData();

            log.info("Gemini原生格式图片生成完成 - base64长度: {}", base64ImageData.length());

            // 转换为ImageApiResponse格式(url字段存储base64数据)
            ImageApiResponse.ImageData imageData = new ImageApiResponse.ImageData(base64ImageData, null);
            return new ImageApiResponse(List.of(imageData), model);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini原生格式图片生成调用失败", e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "Gemini图片生成调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通过即梦反代生成图片
     *
     * <p>使用即梦反代的原生API格式:
     * <ul>
     *   <li>文生图: /v1/images/generations</li>
     *   <li>图生图: /v1/images/compositions</li>
     * </ul>
     *
     * <p><strong>认证方式:</strong> Authorization: Bearer {sessionid}
     *
     * <p><strong>响应格式:</strong> {"code": 0, "message": "success", "data": {...}}
     *
     * @param prompt 提示词
     * @param model 模型名称 (如: jimeng-4.5)
     * @param aspectRatio 画幅比例 (如: 21:9)
     * @param referenceImageUrls 参考图URL列表 (可选,用于图生图)
     * @return 图片生成API响应
     * @throws BusinessException 当API调用失败时抛出
     */
    private ImageApiResponse generateImageViaJimeng(
            String prompt,
            String model,
            String aspectRatio,
            List<String> referenceImageUrls
    ) {
        log.info("调用即梦反代图片生成 - 模型: {}, 画幅: {}, 是否图生图: {}",
                model, aspectRatio, referenceImageUrls != null && !referenceImageUrls.isEmpty());

        AiProperties.JimengProxy jimengConfig = aiProperties.getImage().getJimengProxy();

        if (!jimengConfig.getEnabled()) {
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "即梦反代未启用");
        }

        if (jimengConfig.getSessionid() == null || jimengConfig.getSessionid().isBlank()) {
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "即梦反代sessionid未配置");
        }

        // 判断是文生图还是图生图
        boolean isImageToImage = (referenceImageUrls != null && !referenceImageUrls.isEmpty());
        String endpoint = isImageToImage
                ? jimengConfig.getImageToImageEndpoint()
                : jimengConfig.getTextToImageEndpoint();

        log.debug("选择的端点: {} ({})", endpoint, isImageToImage ? "图生图" : "文生图");

        // 构建即梦反代专用RestClient - 使用Authorization Bearer认证 + 超时配置
        RestClient jimengClient = RestClient.builder()
                .baseUrl(jimengConfig.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + jimengConfig.getSessionid())  // Bearer Token认证
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofSeconds(60));   // 连接超时60秒
                    setReadTimeout(Duration.ofSeconds(180));     // 读取超时180秒(图片生成耗时较长)
                }})
                .build();

        // 构建即梦原生格式请求体
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("model", model);
        requestBody.put("prompt", prompt);
        requestBody.put("ratio", aspectRatio);  // 即梦使用 ratio 而非 aspect_ratio
        requestBody.put("resolution", "2k");    // 默认2k分辨率
        requestBody.put("response_format", "url");  // 返回URL格式

        // 如果是图生图，添加参考图片
        if (isImageToImage) {
            requestBody.put("images", referenceImageUrls);
            requestBody.put("sample_strength", 0.7);  // 参考强度
            log.debug("已添加参考图片到images字段");
        }

        log.debug("即梦请求体 - model: {}, ratio: {}, endpoint: {}", model, aspectRatio, endpoint);

        try {
            // 调用即梦反代端点 - 使用字节数组读取响应，避免Content-Type解析问题
            byte[] responseBytes = jimengClient.post()
                    .uri(endpoint)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        String errorBody = new String(httpResponse.getBody().readAllBytes());
                        log.error("即梦反代图片生成错误 - 状态码: {}, 响应体: {}",
                                httpResponse.getStatusCode(), errorBody);
                        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "即梦图片生成失败: " + errorBody);
                    })
                    .body(byte[].class);
            
            // 将字节数组转换为字符串
            String rawResponse = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);

            // 【诊断日志】打印完整的原始响应体
            log.info("===== 即梦反代原始响应 =====");
            log.info(rawResponse);
            log.info("===== 响应结束 =====");

            // 解析为即梦自定义响应格式
            ObjectMapper mapper = new ObjectMapper();
            JimengImageResponse jimengResponse;
            try {
                jimengResponse = mapper.readValue(rawResponse, JimengImageResponse.class);
            } catch (Exception parseError) {
                log.error("无法将响应解析为即梦格式，原始响应: {}", rawResponse, parseError);
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "即梦反代响应格式错误: " + parseError.getMessage());
            }

            log.debug("即梦反代响应解析成功 - data数量: {}",
                    jimengResponse.data() != null ? jimengResponse.data().size() : 0);

            // 转换为标准ImageApiResponse格式 (内部会检查错误和提取所有图片URL)
            return jimengResponse.toImageApiResponse(model);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("即梦反代图片生成调用失败", e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "即梦图片生成调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通过OpenAI格式生成图片 (向后兼容)
     *
     * <p>使用 /v1/images/generations 端点
     *
     * @param prompt 提示词
     * @param model 模型名称
     * @param aspectRatio 画幅比例
     * @param referenceImageUrls 参考图URL列表
     * @return 图片生成API响应
     * @throws BusinessException 当API调用失败时抛出
     */
    private ImageApiResponse generateImageViaOpenAI(
            String prompt,
            String model,
            String aspectRatio,
            List<String> referenceImageUrls
    ) {
        log.info("Calling OpenAI format image generation - model: {}, aspectRatio: {}", model, aspectRatio);

        Map<String, Object> requestBody = new java.util.HashMap<>(Map.of(
                "model", model,
                "prompt", prompt,
                "n", 1
        ));

        if ("gpt-image-1".equals(model)) {
            requestBody.put("size", mapAspectRatioToImageSize(aspectRatio));
        } else {
            requestBody.put("aspect_ratio", aspectRatio);
        }

        if (referenceImageUrls != null && !referenceImageUrls.isEmpty()) {
            if ("gpt-image-1".equals(model)) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "gpt-image-1不支持参考图输入");
            }
            if (referenceImageUrls.size() == 1) {
                requestBody.put("reference_image", referenceImageUrls.get(0));
            } else {
                requestBody.put("images", referenceImageUrls);
            }
        }

        try {
            ImageApiResponse response = restClient.post()
                    .uri("/v1/images/generations")
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        String errorBody = new String(httpResponse.getBody().readAllBytes());
                        log.error("OpenAI format image generation error - status: {}, body: {}",
                                httpResponse.getStatusCode(), errorBody);
                        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "图片生成失败: " + errorBody);
                    })
                    .body(ImageApiResponse.class);

            log.info("OpenAI format image generation completed");
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI format image generation failed", e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "图片生成调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成视频
     *
     * <p>调用视频生成模型创建视频(异步接口,立即返回任务ID)
     *
     * @param prompt 提示词
     * @param model 模型名称(如: sora-2)
     * @param aspectRatio 画幅比例(如: 16:9, 9:16, 1:1)
     * @param duration 视频时长(秒)
     * @param size 输出分辨率(可选)
     * @param referenceImageUrl 首帧参考图URL(可选,用于图生视频)
     * @return 视频生成API响应(包含任务ID)
     * @throws BusinessException 当API调用失败时抛出
     */
    /**
     * 最大重试次数(针对临时性网络错误)
     */
    private static final int MAX_VIDEO_API_RETRIES = 3;
    
    /**
     * 重试间隔(毫秒)
     */
    private static final long VIDEO_API_RETRY_INTERVAL = 5000;

    public VideoApiResponse generateVideo(
            String prompt,
            String model,
            String aspectRatio,
            Integer duration,
            String size,
            String referenceImageUrl
    ) {
        log.info("调用视频生成API - 模型: {}, 画幅: {}, 时长: {}, 是否有参考图: {}",
                model, aspectRatio, duration, referenceImageUrl != null);

        if (referenceImageUrl == null || referenceImageUrl.isBlank()) {
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "视频生成需要提供参考图(input_reference)");
        }

        MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("model", model);
        requestBody.add("prompt", prompt);
        requestBody.add("seconds", String.valueOf(normalizeVideoSeconds(duration)));
        String targetSize = size != null && !size.isBlank()
                ? size
                : mapAspectRatioToOpenAiVideoSize(aspectRatio);
        requestBody.add("size", targetSize);

        byte[] imageBytes = RestClient.create()
                .get()
                .uri(referenceImageUrl)
                .retrieve()
                .body(byte[].class);
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "参考图下载失败或为空");
        }

        String referenceMimeType = resolveReferenceMimeType(referenceImageUrl);
        if (referenceMimeType.startsWith("image/")) {
            imageBytes = resizeReferenceImage(imageBytes, targetSize);
            referenceMimeType = MediaType.IMAGE_PNG_VALUE;
        }

        final byte[] finalImageBytes = imageBytes;
        final String finalMimeType = referenceMimeType;

        log.debug("视频生成请求体 - model: {}, size: {}, seconds: {}",
                model, targetSize, duration);

        // 带重试的API调用(处理GOAWAY、负载饱和等临时错误)
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_VIDEO_API_RETRIES; attempt++) {
            try {
                // 每次重试需要重新构建请求体(因为Resource可能被消费)
                MultiValueMap<String, Object> retryRequestBody = new LinkedMultiValueMap<>();
                retryRequestBody.add("model", model);
                retryRequestBody.add("prompt", prompt);
                retryRequestBody.add("seconds", String.valueOf(normalizeVideoSeconds(duration)));
                retryRequestBody.add("size", targetSize);
                
                ByteArrayResource imageResource = new ByteArrayResource(finalImageBytes) {
                    @Override
                    public String getFilename() {
                        return "input_reference.png";
                    }
                };
                HttpHeaders partHeaders = new HttpHeaders();
                partHeaders.setContentType(MediaType.parseMediaType(finalMimeType));
                retryRequestBody.add("input_reference", new HttpEntity<>(imageResource, partHeaders));

                // 调用向量引擎视频创建端点
                String rawResponse = restClient.post()
                        .uri("/v1/videos")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(retryRequestBody)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                            String errorBody = new String(httpResponse.getBody().readAllBytes());
                            log.error("视频生成API错误 - 状态码: {}, 响应体: {}",
                                    httpResponse.getStatusCode(), errorBody);
                            
                            // 解析错误响应,给出更友好的提示
                            String friendlyMessage = parseVideoErrorMessage(errorBody);
                            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, friendlyMessage);
                        })
                        .body(String.class);

                log.debug("视频生成API原始响应: {}", rawResponse);
                
                // 解析响应
                ObjectMapper objectMapper = new ObjectMapper();
                VideoApiResponse response = objectMapper.readValue(rawResponse, VideoApiResponse.class);
                
                // 检查是否有错误状态
                if ("error".equals(response.status())) {
                    String friendlyMessage = parseVideoErrorMessage(rawResponse);
                    throw new BusinessException(ResultCode.AI_SERVICE_ERROR, friendlyMessage);
                }

                log.info("视频生成任务已创建 - taskId: {}, 尝试次数: {}", response.id(), attempt);
                return response;

            } catch (BusinessException e) {
                // 检查是否为可重试的错误
                if (isRetryableError(e) && attempt < MAX_VIDEO_API_RETRIES) {
                    log.warn("视频生成API临时错误,准备重试 - 第{}/{}次, 错误: {}", 
                            attempt, MAX_VIDEO_API_RETRIES, e.getMessage());
                    lastException = e;
                    try {
                        Thread.sleep(VIDEO_API_RETRY_INTERVAL * attempt);  // 递增等待时间
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "视频生成被中断", ie);
                    }
                    continue;
                }
                throw e;
            } catch (Exception e) {
                // 检查是否为可重试的网络错误(GOAWAY等)
                if (isRetryableNetworkError(e) && attempt < MAX_VIDEO_API_RETRIES) {
                    log.warn("视频生成API网络错误,准备重试 - 第{}/{}次, 错误: {}", 
                            attempt, MAX_VIDEO_API_RETRIES, e.getMessage());
                    lastException = e;
                    try {
                        Thread.sleep(VIDEO_API_RETRY_INTERVAL * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "视频生成被中断", ie);
                    }
                    continue;
                }
                log.error("视频生成调用失败", e);
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "视频生成服务异常，请稍后重试", e);
            }
        }
        
        // 所有重试都失败
        log.error("视频生成API重试{}次后仍失败", MAX_VIDEO_API_RETRIES);
        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, 
                "视频生成服务繁忙，已重试" + MAX_VIDEO_API_RETRIES + "次，请稍后再试", lastException);
    }
    
    /**
     * 判断是否为可重试的业务错误
     */
    private boolean isRetryableError(BusinessException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("负载已饱和") || 
               msg.contains("繁忙") || 
               msg.contains("稍后") ||
               msg.contains("load") ||
               msg.contains("saturated");
    }
    
    /**
     * 判断是否为可重试的网络错误
     */
    private boolean isRetryableNetworkError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            // 检查cause
            Throwable cause = e.getCause();
            while (cause != null) {
                String causeMsg = cause.getMessage();
                if (causeMsg != null && (causeMsg.contains("GOAWAY") || 
                                          causeMsg.contains("Connection reset") ||
                                          causeMsg.contains("timeout"))) {
                    return true;
                }
                cause = cause.getCause();
            }
            return false;
        }
        return msg.contains("GOAWAY") || 
               msg.contains("Connection reset") || 
               msg.contains("timeout") ||
               msg.contains("I/O error");
    }

    /**
     * 查询任务状态
     *
     * <p>查询异步任务的执行状态和结果(用于视频生成)
     *
     * @param taskId 任务ID
     * @return 任务状态API响应
     * @throws BusinessException 当API调用失败时抛出
     */
    public TaskStatusApiResponse queryTaskStatus(String taskId) {
        log.debug("查询任务状态 - taskId: {}", taskId);

        try {
            // ✅ 【诊断模式】先获取原始响应体，打印后再尝试反序列化
            log.info("========== 【诊断】开始查询任务状态 ==========");
            log.info("请求URL: /v1/videos/{id}", taskId);

            String rawResponse = restClient.get()
                    .uri("/v1/videos/{id}", taskId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        String errorBody = new String(httpResponse.getBody().readAllBytes());
                        log.error("任务查询API错误 - 状态码: {}, 响应体: {}",
                                httpResponse.getStatusCode(), errorBody);
                        throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "任务状态查询失败: " + errorBody);
                    })
                    .body(String.class);  // ✅ 先获取原始字符串

            log.info("========== 【诊断】原始响应体 ==========");
            log.info("{}", rawResponse);
            log.info("=========================================");

            // ✅ 【修复】API直接返回TaskStatusApiResponse，没有wrapper包裹层
            ObjectMapper objectMapper = new ObjectMapper();
            TaskStatusApiResponse response = objectMapper.readValue(rawResponse, TaskStatusApiResponse.class);

            log.info("========== 【诊断】反序列化结果 ==========");
            log.info("response == null: {}", response == null);
            if (response != null) {
                log.info("response.id: {}", response.id());
                log.info("response.status: {}", response.status());
                log.info("response.videoUrl: {}", response.videoUrl());
                log.info("response.enhancedPrompt: {}", response.enhancedPrompt());
                log.info("response.statusUpdateTime: {}", response.statusUpdateTime());
            }
            log.info("==========================================");

            // ✅ 验证响应
            if (response == null) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "任务查询响应为空");
            }

            log.info("任务状态已查询 - taskId: {}, status: {}, videoUrl存在: {}",
                    taskId, response.status(), response.videoUrl() != null);
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("任务状态查询失败 - taskId: {}", taskId, e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "任务状态查询失败: " + e.getMessage(), e);
        }
    }

    public VideoContentResponse downloadVideoContent(String taskId) {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri("/v1/videos/{id}/content", taskId)
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "视频内容为空");
            }

            String contentType = null;
            if (response.getHeaders() != null && response.getHeaders().getContentType() != null) {
                contentType = response.getHeaders().getContentType().toString();
            }

            return new VideoContentResponse(body, contentType);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载视频内容失败 - taskId: {}", taskId, e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "下载视频内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 文本生成API响应
     *
     * @param id 响应ID
     * @param choices 生成结果列表
     * @param usage token使用情况（可能为null）
     * @param model 使用的模型名称
     */
    public record TextApiResponse(
            String id,
            List<Choice> choices,
            Usage usage,
            String model
    ) {
        public record Choice(Message message, String finishReason) {}
        public record Message(String role, String content) {}
        public record Usage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {}
    }

    /**
     * 图片生成API响应
     *
     * @param data 生成的图片列表
     * @param model 使用的模型名称
     */
    public record ImageApiResponse(
            List<ImageData> data,
            String model
    ) {
        public record ImageData(String url, String revisedPrompt) {}
    }

    /**
     * 视频生成API响应
     *
     * @param id 异步任务ID
     * @param status 任务状态
     * @param model 使用的模型名称
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoApiResponse(
            String id,         // API返回字段名为"id"而非"taskId"
            String status,
            String model,
            @com.fasterxml.jackson.annotation.JsonProperty("status_update_time")
            Long statusUpdateTime
    ) {}

    public record VideoContentResponse(
            byte[] content,
            String contentType
    ) {}

    /**
     * 任务状态API响应（直接返回的结构）
     *
     * <p>✅ 根据实际调试日志，中转站API <strong>直接返回</strong>此对象，<strong>没有外层包裹</strong>
     * <p>⚠️ <strong>响应结构是动态的</strong>：
     * <ul>
     *   <li>初期查询（1-7次）：只返回5个基本字段 (id, status, video_url, enhanced_prompt, status_update_time)</li>
     *   <li>后续查询（8次+）：返回完整结构 (额外包含 size, model, detail, object, seconds, progress, created_at)</li>
     * </ul>
     *
     * <p>初期响应示例：
     * <pre>
     * {
     *   "id": "video_xxx",
     *   "status": "queued",
     *   "video_url": null,
     *   "enhanced_prompt": null,
     *   "status_update_time": null
     * }
     * </pre>
     *
     * <p>完整响应示例：
     * <pre>
     * {
     *   "id": "video_xxx",
     *   "size": "1280x720",
     *   "model": "sora-2",
     *   "detail": {},
     *   "object": "video",
     *   "status": "queued",
     *   "seconds": "10",
     *   "progress": 0,
     *   "created_at": 1766996859
     * }
     * </pre>
     *
     * @param id 任务ID
     * @param status 任务状态(queued/processing/completed/error)
     * @param videoUrl 视频URL（仅completed状态存在）
     * @param enhancedPrompt 增强后的提示词（可选）
     * @param statusUpdateTime 状态更新时间戳（可选）
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskStatusApiResponse(
            String id,
            String status,
            @com.fasterxml.jackson.annotation.JsonProperty("video_url")
            String videoUrl,
            @com.fasterxml.jackson.annotation.JsonProperty("enhanced_prompt")
            String enhancedPrompt,
            @com.fasterxml.jackson.annotation.JsonProperty("status_update_time")
            Long statusUpdateTime
    ) {}

    /**
     * 即梦反代API响应
     *
     * <p>即梦反代服务器返回的响应结构:
     * <pre>
     * 成功响应:
     * {
     *   "created": 1766916343,
     *   "data": [
     *     {"url": "https://..."},
     *     {"url": "https://..."},
     *     {"url": "https://..."},
     *     {"url": "https://..."}
     *   ],
     *   "input_images": 1,
     *   "composition_type": "multi_image_synthesis"
     * }
     *
     * 错误响应:
     * {
     *   "code": -2000,
     *   "message": "Params headers.authorization invalid",
     *   "data": null
     * }
     * </pre>
     *
     * @param code 响应状态码 (可选,仅错误时存在)
     * @param message 响应消息 (可选,仅错误时存在)
     * @param data 图片数据数组 (成功时存在,可能包含多个URL)
     * @param created 创建时间戳 (可选,成功时存在)
     * @param input_images 输入图片数量 (可选)
     * @param composition_type 合成类型 (可选)
     */
    public record JimengImageResponse(
            Integer code,                    // 可选,仅错误时存在
            String message,                  // 可选,仅错误时存在
            List<JimengImageData> data,      // ✅ 修改为数组类型
            Long created,                    // 可选,成功时存在
            Integer input_images,            // 可选
            String composition_type          // 可选
    ) {
        /**
         * 即梦图片数据
         *
         * @param url 图片URL
         */
        public record JimengImageData(String url) {}

        /**
         * 转换为标准ImageApiResponse格式
         *
         * @param model 模型名称
         * @return 标准ImageApiResponse (包含所有生成的图片)
         */
        public ImageApiResponse toImageApiResponse(String model) {
            // 检查是否为错误响应
            if (code != null && code != 0) {
                // 解析即梦错误码，给出友好提示
                String friendlyMsg = parseJimengErrorCode(code, message);
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR, friendlyMsg);
            }

            // 检查data字段
            if (data == null || data.isEmpty()) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "即梦API返回的data为空");
            }

            // 将所有图片URL转换为ImageApiResponse格式
            List<ImageApiResponse.ImageData> imageDataList = data.stream()
                    .map(jimengData -> new ImageApiResponse.ImageData(jimengData.url(), null))
                    .toList();

            log.info("即梦反代成功生成 {} 张图片", imageDataList.size());

            return new ImageApiResponse(imageDataList, model);
        }
    }

    /**
     * Gemini原生格式API响应
     *
     * <p>Gemini图片生成原生格式的响应结构
     *
     * @param candidates 候选结果列表
     */
    public record GeminiNativeImageResponse(
            List<Candidate> candidates
    ) {
        public record Candidate(Content content) {}
        public record Content(List<Part> parts) {}
        public record Part(InlineData inlineData) {}
        public record InlineData(String mimeType, String data) {}

        /**
         * 提取base64图片数据
         *
         * @return base64编码的图片数据
         */
        public String extractBase64ImageData() {
            if (candidates == null || candidates.isEmpty()) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "Gemini原生格式响应的candidates为空");
            }

            Candidate candidate = candidates.get(0);
            if (candidate.content == null || candidate.content.parts == null || candidate.content.parts.isEmpty()) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "Gemini原生格式响应的content.parts为空");
            }

            Part part = candidate.content.parts.get(0);
            if (part.inlineData == null || part.inlineData.data == null) {
                throw new BusinessException(ResultCode.AI_SERVICE_ERROR,
                        "Gemini原生格式响应的inlineData.data为空");
            }

            return part.inlineData.data;
        }
    }

    /**
     * 解析视频错误消息，返回用户友好的提示
     *
     * @param errorBody 错误响应体
     * @return 用户友好的错误消息
     */
    private String parseVideoErrorMessage(String errorBody) {
        if (errorBody == null || errorBody.isBlank()) {
            return "视频生成服务异常，请稍后重试";
        }

        // 尝试解析JSON错误响应
        try {
            ObjectMapper mapper = new ObjectMapper();
            var errorObj = mapper.readValue(errorBody, java.util.Map.class);
            
            // 检查是否有error字段
            Object errorField = errorObj.get("error");
            if (errorField != null) {
                String errorMsg = errorField.toString();
                
                // 匹配常见错误，给出友好提示
                if (errorMsg.contains("负载已饱和") || errorMsg.contains("load") || errorMsg.contains("saturated")) {
                    return "视频生成服务繁忙，请稍后再试︁";
                }
                if (errorMsg.contains("参数") || errorMsg.contains("param") || errorMsg.contains("必需")) {
                    return "请求参数错误: " + errorMsg;
                }
                if (errorMsg.contains("超时") || errorMsg.contains("timeout")) {
                    return "视频生成超时，请重试";
                }
                
                // 默认返回原始错误信息
                return "视频生成失败: " + errorMsg;
            }
            
        } catch (Exception e) {
            log.debug("无法解析错误响应为JSON: {}", errorBody);
        }
        
        // 简单的字符串匹配
        if (errorBody.contains("负载已饱和") || errorBody.contains("load") || errorBody.contains("saturated")) {
            return "视频生成服务繁忙，请稍后再试";
        }
        
        return "视频生成失败，请稍后重试";
    }

    /**
     * 将画幅比例要求添加到prompt中（用于Gemini）
     *
     * <p>Gemini原生API不支持aspect_ratio参数，需要在prompt中明确说明画幅比例要求
     *
     * @param originalPrompt 原始提示词
     * @param aspectRatio 画幅比例 (如: "1:1", "16:9", "21:9")
     * @return 增强后的提示词
     */
    private String enhancePromptWithAspectRatio(String originalPrompt, String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isBlank() || aspectRatio.equals("1:1")) {
            // 默认1:1正方形，不需要额外说明
            return originalPrompt;
        }

        // 为不同的画幅比例添加明确的英文说明
        String aspectRatioDescription = switch (aspectRatio) {
            case "16:9" -> "in 16:9 landscape format (wide screen)";
            case "9:16" -> "in 9:16 portrait format (tall screen)";
            case "21:9" -> "in 21:9 ultrawide format (cinematic widescreen)";
            case "4:3" -> "in 4:3 standard format";
            case "3:4" -> "in 3:4 portrait format";
            default -> "in " + aspectRatio + " aspect ratio";
        };

        return "Generate an image " + aspectRatioDescription + ". " + originalPrompt;
    }

    /**
     * 将画幅比例映射为sora-2的size参数
     *
     * <p>OpenAI视频接口使用分辨率字符串
     *
     * @param aspectRatio 画幅比例 (如: "16:9", "9:16", "1:1")
     * @return size参数值
     */
    private String mapAspectRatioToOpenAiVideoSize(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isBlank()) {
            return "1280x720";
        }
        return switch (aspectRatio) {
            case "9:16" -> "720x1280";
            case "1:1" -> "1024x1024";
            default -> "1280x720";
        };
    }

    /**
     * 将画幅比例映射为gpt-image-1的size参数
     *
     * @param aspectRatio 画幅比例 (如: "16:9", "9:16", "1:1")
     * @return size参数值
     */
    private String mapAspectRatioToImageSize(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isBlank()) {
            return "auto";
        }

        return switch (aspectRatio) {
            case "1:1" -> "1024x1024";
            case "16:9" -> "1536x1024";
            case "9:16" -> "1024x1536";
            case "21:9" -> "1536x1024";
            default -> "auto";
        };
    }

    /**
     * 将画幅比例映射为sora-2的orientation参数
     *
     * <p>根据API文档,orientation使用"portrait"(竖屏)或"landscape"(横屏)
     *
     * @param aspectRatio 画幅比例 (如: "16:9", "9:16", "1:1")
     * @return orientation参数值
     */
    private String mapAspectRatioToOrientation(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isBlank()) {
            return "landscape";  // 默认横向
        }

        // 根据画幅比例返回orientation
        return switch (aspectRatio) {
            case "16:9" -> "landscape";  // 横向16:9
            case "21:9" -> "landscape";  // 超宽横向
            case "4:3" -> "landscape";  // 标准横向
            case "9:16" -> "portrait";   // 竖向9:16
            case "3:4" -> "portrait";    // 标准竖向
            case "1:1" -> "landscape";  // 正方形，默认横向
            default -> "landscape";     // 默认横向
        };
    }

    private String resolveReferenceMimeType(String referenceImageUrl) {
        if (referenceImageUrl == null || referenceImageUrl.isBlank()) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        String cleanUrl = referenceImageUrl.split("\\?")[0].toLowerCase();
        if (cleanUrl.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (cleanUrl.endsWith(".webp")) {
            return "image/webp";
        }
        if (cleanUrl.endsWith(".mp4")) {
            return "video/mp4";
        }
        return MediaType.IMAGE_PNG_VALUE;
    }

    private byte[] resizeReferenceImage(byte[] imageBytes, String targetSize) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (source == null) {
                return imageBytes;
            }

            int[] size = parseSize(targetSize);
            int targetWidth = size[0];
            int targetHeight = size[1];

            if (source.getWidth() == targetWidth && source.getHeight() == targetHeight) {
                return imageBytes;
            }

            BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = scaled.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            g2d.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            return imageBytes;
        }
    }

    private int[] parseSize(String size) {
        if (size == null || !size.contains("x")) {
            return new int[] { 1280, 720 };
        }
        String[] parts = size.split("x");
        try {
            return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
        } catch (NumberFormatException e) {
            return new int[] { 1280, 720 };
        }
    }

    private int normalizeVideoSeconds(Integer duration) {
        if (duration == null) {
            return 4;
        }
        if (duration <= 4) {
            return 4;
        }
        if (duration <= 8) {
            return 8;
        }
        return 12;
    }

    /**
     * 解析即梦错误码，返回用户友好的提示
     *
     * @param code 错误码
     * @param message 原始错误消息
     * @return 用户友好的错误消息
     */
    private static String parseJimengErrorCode(Integer code, String message) {
        if (code == null) {
            return "图片生成失败：" + message;
        }
        
        return switch (code) {
            case 1001 -> "图片生成服务认证失败，请检查API Key配置";
            case 1002 -> "图片生成请求失败，可能是API Key无效或账户余额不足，请检查配置";
            case 1003 -> "图片生成参数错误，请检查描述和配置";
            case 1004 -> "图片生成服务繁忙，请稍后重试";
            case 1005 -> "图片生成超时，请重试";
            case 2001 -> "描述内容可能违规，请修改后重试";
            case 2002 -> "参考图片可能违规，请更换图片后重试";
            default -> "图片生成失败(错误码:" + code + ")，请稍后重试";
        };
    }

}
