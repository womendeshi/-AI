import api from './index'
import type { StoryboardShotVO } from '@/types/api'

/**
 * 分镜API模块
 *
 * 提供分镜表（Storyboard Shot）的完整CRUD操作，包括：
 * - 获取项目分镜列表
 * - 创建/更新/删除分镜
 * - 调整分镜顺序
 * - 管理分镜与角色/场景的绑定关系
 */
export const shotApi = {
  /**
   * 获取项目的分镜列表
   *
   * @param projectId 项目ID
   * @returns 分镜列表（按 shotNo 升序排序）
   */
  async getProjectShots(projectId: number): Promise<StoryboardShotVO[]> {
    return api.get(`/projects/${projectId}/shots`)
  },

  /**
   * 创建新分镜
   *
   * @param projectId 项目ID
   * @param scriptText 剧本文本
   * @returns 创建的分镜对象
   */
  async createShot(projectId: number, scriptText: string): Promise<StoryboardShotVO> {
    return api.post(`/projects/${projectId}/shots`, { scriptText })
  },

  /**
   * 更新分镜剧本文本
   *
   * @param projectId 项目ID
   * @param shotId 分镜ID
   * @param scriptText 新的剧本文本
   * @returns void（后端返回 Result<Void>）
   */
  async updateShot(projectId: number, shotId: number, scriptText: string): Promise<void> {
    return api.put(`/projects/${projectId}/shots/${shotId}`, { scriptText })
  },

  /**
   * 删除分镜（软删除）
   *
   * @param projectId 项目ID
   * @param shotId 分镜ID
   */
  async deleteShot(projectId: number, shotId: number): Promise<void> {
    return api.delete(`/projects/${projectId}/shots/${shotId}`)
  },

  /**
   * 调整分镜顺序
   *
   * @param projectId 项目ID
   * @param shotIds 按新顺序排列的分镜ID数组
   */
  async reorderShots(projectId: number, shotIds: number[]): Promise<void> {
    return api.put(`/projects/${projectId}/shots/reorder`, { shotIds })
  },

  /**
   * 创建绑定关系（分镜 ↔ 角色/场景）
   *
   * @param projectId 项目ID
   * @param shotId 分镜ID
   * @param bindType 绑定类型：'CHARACTER' | 'SCENE'
   * @param bindId 绑定对象ID（角色ID或场景ID）
   */
  async createBinding(
    projectId: number,
    shotId: number,
    bindType: 'PCHAR' | 'PSCENE' | 'PPROP',
    bindId: number
  ): Promise<void> {
    return api.post(`/projects/${projectId}/shots/${shotId}/bindings`, {
      bindType,
      bindId,
    })
  },

  /**
   * 删除绑定关系
   *
   * @param projectId 项目ID
   * @param shotId 分镜ID
   * @param bindingId 绑定记录ID
   */
  async deleteBinding(projectId: number, shotId: number, bindingId: number): Promise<void> {
    return api.delete(`/projects/${projectId}/shots/${shotId}/bindings/${bindingId}`)
  },

  /**
   * AI解析剧本并批量创建分镜
   * 注意：AI解析需要较长时间，设置2分钟超时
   *
   * @param projectId 项目ID
   * @param fullScript 完整剧本文本
   * @param signal 可选的 AbortSignal，用于取消请求
   * @returns 创建的分镜列表
   */
  async parseAndCreateShots(projectId: number, fullScript: string, signal?: AbortSignal): Promise<StoryboardShotVO[]> {
    return api.post(`/projects/${projectId}/shots/parse-script`, { fullScript }, {
      timeout: 120000,  // 2分钟超时，AI解析需要更长时间
      signal
    })
  },
}
