import api from './index'
import type { ExportRequest, ExportResponse } from '@/types/api'

export const exportApi = {
  /**
   * 提交导出任务
   */
  async submitExportTask(projectId: number, request: ExportRequest): Promise<ExportResponse> {
    return api.post(`/projects/${projectId}/export`, request)
  },

  /**
   * 下载导出文件
   */
  getDownloadUrl(jobId: number): string {
    const baseURL = api.defaults.baseURL || '/api'
    const token = localStorage.getItem('token')
    return `${baseURL}/exports/${jobId}/download?token=${token}`
  },

  /**
   * 触发文件下载
   */
  async downloadExportFile(jobId: number): Promise<void> {
    const baseURL = api.defaults.baseURL || '/api'
    const token = localStorage.getItem('token')
    const url = `${baseURL}/exports/${jobId}/download`

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      })

      if (!response.ok) {
        throw new Error(`下载失败: ${response.status} ${response.statusText}`)
      }

      const blob = await response.blob()
      const downloadUrl = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = downloadUrl
      link.download = `project_export_${jobId}.zip`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(downloadUrl)
    } catch (error) {
      console.error('[Export] Download failed:', error)
      throw error
    }
  },
}
