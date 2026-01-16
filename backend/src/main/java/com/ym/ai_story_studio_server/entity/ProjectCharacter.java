package com.ym.ai_story_studio_server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目角色（引用全局角色库，支持项目内覆盖）
 */
@Data
@TableName("project_characters")
public class ProjectCharacter {

    /**
     * 项目角色引用ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 引用的全局角色ID
     */
    private Long libraryCharacterId;

    /**
     * 项目内显示名（可覆盖全局名称）
     */
    private String displayName;

    /**
     * 项目内覆盖描述/提示词（可为空表示使用全局）
     */
    private String overrideDescription;

    /**
     * 角色缩略图URL（自定义角色用，当library_character_id为空时使用此字段）
     */
    private String thumbnailUrl;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
