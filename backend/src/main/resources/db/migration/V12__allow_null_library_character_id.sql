-- V12: 允许 project_characters 和 project_scenes 表的库引用ID为空
-- 这样可以在AI解析剧本时创建没有关联库角色/场景的项目角色/场景

-- 直接修改 library_character_id 字段为可空（会自动处理约束）
ALTER TABLE project_characters MODIFY COLUMN library_character_id BIGINT NULL COMMENT '引用的全局角色ID（可为空表示项目内自定义角色）';

-- 直接修改 library_scene_id 字段为可空
ALTER TABLE project_scenes MODIFY COLUMN library_scene_id BIGINT NULL COMMENT '引用的全局场景ID（可为空表示项目内自定义场景）';
