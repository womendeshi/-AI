package com.ym.ai_story_studio_server.controller;

import com.ym.ai_story_studio_server.common.Result;
import com.ym.ai_story_studio_server.dto.export.ExportRequest;
import com.ym.ai_story_studio_server.dto.export.ExportResponse;
import com.ym.ai_story_studio_server.service.ExportService;
import com.ym.ai_story_studio_server.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 导出控制器
 *
 * <p>提供项目数据导出相关的API接口,包括:
 * <ul>
 *   <li>提交导出任务</li>
 *   <li>下载导出文件</li>
 * </ul>
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    /**
     * 提交项目导出任务
     *
     * <p>异步执行导出任务,将选中的资产按分类文件夹组织并打包为ZIP文件
     *
     * @param projectId 项目ID
     * @param request 导出请求
     * @return 导出任务响应
     */
    @PostMapping("/api/projects/{projectId}/export")
    public Result<ExportResponse> submitExportTask(
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody ExportRequest request) {
        Long userId = UserContext.getUserId();
        log.info("提交导出任务: userId={}, projectId={}, request={}", userId, projectId, request);

        Long jobId = exportService.submitExportTask(userId, projectId, request);

        ExportResponse response = new ExportResponse(
                jobId,
                "PENDING",
                "导出任务已提交,正在处理中..."
        );

        return Result.success("导出任务已提交", response);
    }

    /**
     * 下载导出文件
     *
     * <p>根据导出任务ID下载生成的ZIP文件
     *
     * @param jobId 导出任务ID
     * @return 文件资源
     */
    @GetMapping("/api/exports/{jobId}/download")
    public ResponseEntity<Resource> downloadExportFile(@PathVariable("jobId") Long jobId) {
        Long userId = UserContext.getUserId();
        log.info("下载导出文件: userId={}, jobId={}", userId, jobId);

        Resource resource = exportService.getExportFile(userId, jobId);
        String fileName = exportService.getExportFileName(jobId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }
}
