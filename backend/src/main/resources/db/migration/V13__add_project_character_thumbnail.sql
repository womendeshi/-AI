-- 为项目角色表添加缩略图URL字段，支持自定义角色（不关联角色库）的图片存储
ALTER TABLE project_characters ADD COLUMN thumbnail_url VARCHAR(512) NULL COMMENT '角色缩略图URL（自定义角色用）';
