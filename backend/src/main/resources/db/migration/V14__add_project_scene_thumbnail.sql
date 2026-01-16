-- 为项目场景添加缩略图字段（支持自定义场景图片）
ALTER TABLE project_scenes
ADD COLUMN thumbnail_url VARCHAR(1024) DEFAULT NULL COMMENT '项目场景缩略图URL' AFTER override_description;
