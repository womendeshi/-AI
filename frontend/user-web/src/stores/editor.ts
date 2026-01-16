import { defineStore } from 'pinia'
import { shotApi, characterApi, sceneApi, generationApi, assetApi, propApi } from '@/api/apis'
import type {
  StoryboardShotVO,
  ProjectCharacterVO,
  ProjectSceneVO,
  ProjectPropVO,
  BatchGenerateRequest,
  BatchGenerateResponse,
  AssetVersionVO,
  SetCurrentVersionRequest,
} from '@/types/api'

interface EditorState {
  projectId: number | null
  shots: StoryboardShotVO[]
  characters: ProjectCharacterVO[]
  scenes: ProjectSceneVO[]
  props: ProjectPropVO[]
  selectedShotIds: Set<number>
  loading: boolean
  // 原始剧本文本（AI解析前的文本）
  originalScript: string
  // 角色描述缓存（角色名称 -> 解析后的描述）
  characterDescriptionCache: Map<string, string>
  // Undo/Redo history
  history: StoryboardShotVO[][]
  historyIndex: number
  maxHistorySize: number
  // 本地图片历史记录（按资产类型+ID分组存储，如 'character_123' 或 'scene_456'）
  localImageHistory: Record<string, { id: number; url: string; prompt: string | null; versionNo: number; createdAt: string }[]>
}

export const useEditorStore = defineStore('editor', {
  state: (): EditorState => ({
    projectId: null,
    shots: [],
    characters: [],
    scenes: [],
    props: [],
    selectedShotIds: new Set(),
    loading: false,
    // 原始剧本文本（AI解析前的文本）
    originalScript: '',
    // 角色描述缓存
    characterDescriptionCache: new Map(),
    // Undo/Redo history
    history: [],
    historyIndex: -1,
    maxHistorySize: 20,
    // 本地图片历史记录
    localImageHistory: {},
  }),

  getters: {
    selectedShots: (state) =>
      state.shots.filter((shot) => state.selectedShotIds.has(shot.id)),
    hasSelection: (state) => state.selectedShotIds.size > 0,
    allSelected: (state) =>
      state.shots.length > 0 && state.selectedShotIds.size === state.shots.length,
    activeCharacters: (state) => state.characters.filter((c) => c.isActive),
    activeScenes: (state) => state.scenes, // 场景没有 isActive 字段，返回所有场景
    // Undo/Redo state
    canUndo: (state) => state.historyIndex > 0,
    canRedo: (state) => state.historyIndex < state.history.length - 1,
  },

  actions: {
    /**
     * Initialize editor for a project
     */
    async initProject(projectId: number) {
      this.projectId = projectId
      this.loading = true
      try {
        // 加载本地历史记录
        this.loadLocalImageHistory()
        // 清理过期历史记录
        this.cleanExpiredHistory()

        await Promise.all([
          this.fetchShots(),
          this.fetchCharacters(),
          this.fetchScenes(),
          this.fetchProps(),
        ])
        // Save initial state to history
        this.saveHistory()
        console.log('[EditorStore] Project initialized:', projectId)
      } catch (error) {
        console.error('[EditorStore] Failed to initialize project:', error)
        throw error
      } finally {
        this.loading = false
      }
    },

    /**
     * Fetch all shots for current project
     */
    async fetchShots() {
      if (!this.projectId) return
      try {
        console.log('[EditorStore] Fetching shots for project:', this.projectId)
        this.shots = await shotApi.getProjectShots(this.projectId)
        console.log('[EditorStore] Shots loaded:', this.shots.length)
      } catch (error) {
        console.error('[EditorStore] Failed to fetch shots:', error)
        this.shots = []
      }
    },

    /**
     * Fetch all characters for current project
     */
    async fetchCharacters() {
      if (!this.projectId) return
      try {
        console.log('[EditorStore] Fetching characters for project:', this.projectId)
        this.characters = await characterApi.getProjectCharacters(this.projectId)
        console.log('[EditorStore] Characters loaded:', this.characters.length)
      } catch (error) {
        console.error('[EditorStore] Failed to fetch characters:', error)
        this.characters = []
      }
    },

    /**
     * Fetch all scenes for current project
     */
    async fetchScenes() {
      if (!this.projectId) return
      try {
        console.log('[EditorStore] Fetching scenes for project:', this.projectId)
        this.scenes = await sceneApi.getProjectScenes(this.projectId)
        console.log('[EditorStore] Scenes loaded:', this.scenes.length)
      } catch (error) {
        console.error('[EditorStore] Failed to fetch scenes:', error)
        this.scenes = []
      }
    },

    /**
     * Fetch all props for current project
     */
    async fetchProps() {
      if (!this.projectId) return
      try {
        console.log('[EditorStore] Fetching props for project:', this.projectId)
        this.props = await propApi.getProjectProps(this.projectId)
        console.log('[EditorStore] Props loaded:', this.props.length)
      } catch (error) {
        console.error('[EditorStore] Failed to fetch props:', error)
        this.props = []
      }
    },

    /**
     * Toggle shot selection
     */
    toggleShotSelection(shotId: number) {
      if (this.selectedShotIds.has(shotId)) {
        this.selectedShotIds.delete(shotId)
      } else {
        this.selectedShotIds.add(shotId)
      }
    },

    /**
     * Select all shots
     */
    selectAll() {
      this.selectedShotIds = new Set(this.shots.map((shot) => shot.id))
    },

    /**
     * Deselect all shots
     */
    deselectAll() {
      this.selectedShotIds.clear()
    },

    /**
     * Invert selection
     */
    invertSelection() {
      const allIds = new Set(this.shots.map((shot) => shot.id))
      const newSelection = new Set<number>()
      allIds.forEach((id) => {
        if (!this.selectedShotIds.has(id)) {
          newSelection.add(id)
        }
      })
      this.selectedShotIds = newSelection
    },

    /**
     * Create new shot
     */
    async createShot(scriptText: string) {
      if (!this.projectId) return
      try {
        await shotApi.createShot(this.projectId, scriptText)
        await this.fetchShots()
        this.saveHistory()
        window.$message?.success('分镜添加成功')
      } catch (error: any) {
        console.error('[EditorStore] Failed to create shot:', error)
        window.$message?.error(error.message || '分镜添加失败')
      }
    },

    /**
     * AI解析剧本并批量创建分镜
     * @param fullScript 完整剧本文本
     * @param signal 可选的 AbortSignal，用于取消请求
     */
    async parseAndCreateShots(fullScript: string, signal?: AbortSignal) {
      if (!this.projectId) return
      try {
        console.log('[EditorStore] Parsing script and creating shots...')
        // 保存原始剧本文本
        this.originalScript = fullScript
        await shotApi.parseAndCreateShots(this.projectId, fullScript, signal)
        await this.fetchShots()
        this.saveHistory()
        window.$message?.success('AI解析完成，分镜已创建')
      } catch (error: any) {
        // 如果是用户主动取消，不显示错误消息
        if (error.name === 'CanceledError' || error.message === 'canceled') {
          console.log('[EditorStore] Parse request was cancelled by user')
          return
        }
        console.error('[EditorStore] Failed to parse and create shots:', error)
        window.$message?.error(error.message || 'AI解析失败')
        throw error
      }
    },

    /**
     * Update shot script text
     */
    async updateShot(shotId: number, scriptText: string) {
      if (!this.projectId) return
      try {
        await shotApi.updateShot(this.projectId, shotId, scriptText)
        await this.fetchShots()
        this.saveHistory()
        window.$message?.success('分镜更新成功')
      } catch (error: any) {
        console.error('[EditorStore] Failed to update shot:', error)
        window.$message?.error(error.message || '分镜更新失败')
      }
    },

    /**
     * Delete shot
     */
    async deleteShot(shotId: number) {
      if (!this.projectId) return
      try {
        await shotApi.deleteShot(this.projectId, shotId)
        await this.fetchShots()
        this.selectedShotIds.delete(shotId)
        this.saveHistory()
        window.$message?.success('分镜删除成功')
      } catch (error: any) {
        console.error('[EditorStore] Failed to delete shot:', error)
        window.$message?.error(error.message || '分镜删除失败')
      }
    },

    /**
     * Merge selected shots into one
     * 将选中的多个分镜合并为一个（按序号顺序合并文本）
     */
    async mergeShots(shotIds: number[]) {
      if (!this.projectId || shotIds.length < 2) return
      try {
        // 按 shotNo 顺序排序选中的分镜
        const selectedShots = this.shots
          .filter(shot => shotIds.includes(shot.id))
          .sort((a, b) => a.shotNo - b.shotNo)

        if (selectedShots.length < 2) {
          window.$message?.warning('请至少选择两条分镜进行合并')
          return
        }

        // 合并文本（用换行分隔）
        const mergedText = selectedShots.map(shot => shot.scriptText).join('\n\n')

        // 保留第一个分镜，更新其内容
        const firstShot = selectedShots[0]
        await shotApi.updateShot(this.projectId, firstShot.id, mergedText)

        // 删除其他分镜
        const otherShotIds = selectedShots.slice(1).map(shot => shot.id)
        for (const id of otherShotIds) {
          await shotApi.deleteShot(this.projectId, id)
        }

        // 刷新数据
        await this.fetchShots()
        this.deselectAll()
        this.saveHistory()
        window.$message?.success(`已合并 ${selectedShots.length} 条分镜`)
      } catch (error: any) {
        console.error('[EditorStore] Failed to merge shots:', error)
        window.$message?.error(error.message || '合并分镜失败')
      }
    },

    /**
     * Move shot up
     */
    async moveShotUp(shotId: number) {
      const index = this.shots.findIndex((s) => s.id === shotId)
      if (index <= 0) return

      const newOrder = [...this.shots]
        ;[newOrder[index - 1], newOrder[index]] = [newOrder[index], newOrder[index - 1]]

      await this.reorderShots(newOrder.map((s) => s.id))
    },

    /**
     * Move shot down
     */
    async moveShotDown(shotId: number) {
      const index = this.shots.findIndex((s) => s.id === shotId)
      if (index < 0 || index >= this.shots.length - 1) return

      const newOrder = [...this.shots]
        ;[newOrder[index], newOrder[index + 1]] = [newOrder[index + 1], newOrder[index]]

      await this.reorderShots(newOrder.map((s) => s.id))
    },

    /**
     * Reorder shots
     */
    async reorderShots(shotIds: number[]) {
      if (!this.projectId) return
      try {
        await shotApi.reorderShots(this.projectId, shotIds)
        await this.fetchShots()
        this.saveHistory()
      } catch (error: any) {
        console.error('[EditorStore] Failed to reorder shots:', error)
        window.$message?.error(error.message || '调整顺序失败')
      }
    },

    // ============== Generation Actions ==============

    /**
     * Batch generate shot images (分镜图)
     */
    async batchGenerateShots(request: BatchGenerateRequest): Promise<BatchGenerateResponse> {
      if (!this.projectId) throw new Error('No project selected')
      try {
        console.log('[EditorStore] Batch generating shots:', request)
        const response = await generationApi.generateShotsBatch(this.projectId, request)
        window.$message?.success(`批量生成任务已提交 (Job ID: ${response.jobId})`)
        return response
      } catch (error: any) {
        console.error('[EditorStore] Failed to batch generate shots:', error)
        window.$message?.error(error.message || '批量生成失败')
        throw error
      }
    },

    /**
     * Batch generate videos
     */
    async batchGenerateVideos(request: BatchGenerateRequest): Promise<BatchGenerateResponse> {
      if (!this.projectId) throw new Error('No project selected')
      try {
        console.log('[EditorStore] Batch generating videos:', request)
        const response = await generationApi.generateVideosBatch(this.projectId, request)
        window.$message?.success(`批量生成任务已提交 (Job ID: ${response.jobId})`)
        return response
      } catch (error: any) {
        console.error('[EditorStore] Failed to batch generate videos:', error)
        window.$message?.error(error.message || '批量生成失败')
        throw error
      }
    },

    /**
     * Batch generate character images (角色画像)
     */
    async batchGenerateCharacters(request: BatchGenerateRequest): Promise<BatchGenerateResponse> {
      if (!this.projectId) throw new Error('No project selected')
      try {
        console.log('[EditorStore] Batch generating characters:', request)
        const response = await generationApi.generateCharactersBatch(this.projectId, request)
        window.$message?.success(`批量生成任务已提交 (Job ID: ${response.jobId})`)
        return response
      } catch (error: any) {
        console.error('[EditorStore] Failed to batch generate characters:', error)
        window.$message?.error(error.message || '批量生成失败')
        throw error
      }
    },

    /**
     * Batch generate scene images (场景画像)
     */
    async batchGenerateScenes(request: BatchGenerateRequest): Promise<BatchGenerateResponse> {
      if (!this.projectId) throw new Error('No project selected')
      try {
        console.log('[EditorStore] Batch generating scenes:', request)
        const response = await generationApi.generateScenesBatch(this.projectId, request)
        window.$message?.success(`批量生成任务已提交 (Job ID: ${response.jobId})`)
        return response
      } catch (error: any) {
        console.error('[EditorStore] Failed to batch generate scenes:', error)
        window.$message?.error(error.message || '批量生成失败')
        throw error
      }
    },

    /**
     * Generate single shot image
     */
    async generateShotImage(
      shotId: number,
      params: { aspectRatio?: '1:1' | '16:9' | '9:16' | '21:9'; model?: string },
    ): Promise<BatchGenerateResponse> {
      if (!this.projectId) throw new Error('No project selected')
      try {
        console.log('[EditorStore] Generating shot image:', shotId, params)
        const response = await generationApi.generateSingleShot(this.projectId, shotId, params)
        window.$message?.success(`分镜图生成任务已提交 (Job ID: ${response.jobId})`)
        return response
      } catch (error: any) {
        console.error('[EditorStore] Failed to generate shot image:', error)
        window.$message?.error(error.message || '生成失败')
        throw error
      }
    },

    /**
     * Generate single video
     */
    async generateVideo(
      shotId: number,
      params: { aspectRatio?: '1:1' | '16:9' | '9:16' | '21:9'; model?: string },
    ): Promise<BatchGenerateResponse> {
      if (!this.projectId) throw new Error('No project selected')
      try {
        console.log('[EditorStore] Generating video:', shotId, params)
        const response = await generationApi.generateSingleVideo(this.projectId, shotId, params)
        window.$message?.success(`视频生成任务已提交 (Job ID: ${response.jobId})`)
        return response
      } catch (error: any) {
        console.error('[EditorStore] Failed to generate video:', error)
        window.$message?.error(error.message || '生成失败')
        throw error
      }
    },

    /**
     * Generate single character image
     */
    async generateCharacterImage(
      characterId: number,
      params: { aspectRatio?: '1:1' | '16:9' | '9:16' | '21:9'; model?: string },
    ): Promise<BatchGenerateResponse> {
      if (!this.projectId) throw new Error('No project selected')
      try {
        console.log('[EditorStore] Generating character image:', characterId, params)
        const response = await generationApi.generateSingleCharacter(this.projectId, characterId, params)
        window.$message?.success(`角色画像生成任务已提交 (Job ID: ${response.jobId})`)
        return response
      } catch (error: any) {
        console.error('[EditorStore] Failed to generate character image:', error)
        window.$message?.error(error.message || '生成失败')
        throw error
      }
    },

    /**
     * Generate single scene image
     */
    async generateSceneImage(
      sceneId: number,
      params: { aspectRatio?: '1:1' | '16:9' | '9:16' | '21:9'; model?: string },
    ): Promise<BatchGenerateResponse> {
      if (!this.projectId) throw new Error('No project selected')
      try {
        console.log('[EditorStore] Generating scene image:', sceneId, params)
        const response = await generationApi.generateSingleScene(this.projectId, sceneId, params)
        window.$message?.success(`场景画像生成任务已提交 (Job ID: ${response.jobId})`)
        return response
      } catch (error: any) {
        console.error('[EditorStore] Failed to generate scene image:', error)
        window.$message?.error(error.message || '生成失败')
        throw error
      }
    },

    // ============== Asset Version Actions ==============

    /**
     * Get asset version history
     */
    async getAssetVersions(assetId: number): Promise<AssetVersionVO[]> {
      try {
        console.log('[EditorStore] Fetching asset versions:', assetId)
        return await assetApi.getAssetVersions(assetId)
      } catch (error: any) {
        console.error('[EditorStore] Failed to fetch asset versions:', error)
        window.$message?.error(error.message || '获取版本历史失败')
        throw error
      }
    },

    /**
     * Set current asset version
     */
    async setAssetCurrentVersion(assetId: number, request: SetCurrentVersionRequest): Promise<void> {
      try {
        console.log('[EditorStore] Setting current version:', assetId, request)
        await assetApi.setCurrentVersion(assetId, request)
        // Refresh shots to get updated asset status
        await this.fetchShots()
        window.$message?.success('版本切换成功')
      } catch (error: any) {
        console.error('[EditorStore] Failed to set current version:', error)
        window.$message?.error(error.message || '版本切换失败')
        throw error
      }
    },

    /**
     * Upload new asset version
     */
    async uploadAssetVersion(assetId: number, file: File): Promise<AssetVersionVO> {
      try {
        console.log('[EditorStore] Uploading new asset version:', assetId)
        const version = await assetApi.uploadNewVersion(assetId, file)
        // Refresh shots to get updated asset status
        await this.fetchShots()
        window.$message?.success('版本上传成功')
        return version
      } catch (error: any) {
        console.error('[EditorStore] Failed to upload asset version:', error)
        window.$message?.error(error.message || '版本上传失败')
        throw error
      }
    },

    // ============== Undo/Redo Actions ==============

    /**
     * Save current state to history
     */
    saveHistory() {
      // Deep clone shots to preserve state
      const snapshot = JSON.parse(JSON.stringify(this.shots))

      // Remove future history if we're not at the end
      if (this.historyIndex < this.history.length - 1) {
        this.history = this.history.slice(0, this.historyIndex + 1)
      }

      // Add new snapshot
      this.history.push(snapshot)
      this.historyIndex++

      // Trim history if exceeds max size
      if (this.history.length > this.maxHistorySize) {
        this.history.shift()
        this.historyIndex--
      }

      console.log('[EditorStore] History saved. Index:', this.historyIndex, 'Total:', this.history.length)
    },

    /**
     * Undo last operation
     */
    async undo() {
      if (!this.canUndo) {
        console.warn('[EditorStore] Cannot undo: at beginning of history')
        return
      }

      this.historyIndex--
      const previousState = this.history[this.historyIndex]

      // Restore previous state
      this.shots = JSON.parse(JSON.stringify(previousState))

      // Sync with backend by sending reorder request
      if (this.projectId && this.shots.length > 0) {
        try {
          await shotApi.reorderShots(
            this.projectId,
            this.shots.map((s) => s.id)
          )
          console.log('[EditorStore] Undo successful. Index:', this.historyIndex)
          window.$message?.success('撤销成功')
        } catch (error: any) {
          console.error('[EditorStore] Undo failed:', error)
          window.$message?.error('撤销失败')
          // Revert historyIndex on error
          this.historyIndex++
        }
      }
    },

    /**
     * Redo last undone operation
     */
    async redo() {
      if (!this.canRedo) {
        console.warn('[EditorStore] Cannot redo: at end of history')
        return
      }

      this.historyIndex++
      const nextState = this.history[this.historyIndex]

      // Restore next state
      this.shots = JSON.parse(JSON.stringify(nextState))

      // Sync with backend by sending reorder request
      if (this.projectId && this.shots.length > 0) {
        try {
          await shotApi.reorderShots(
            this.projectId,
            this.shots.map((s) => s.id)
          )
          console.log('[EditorStore] Redo successful. Index:', this.historyIndex)
          window.$message?.success('重做成功')
        } catch (error: any) {
          console.error('[EditorStore] Redo failed:', error)
          window.$message?.error('重做失败')
          // Revert historyIndex on error
          this.historyIndex--
        }
      }
    },

    /**
     * 加载本地历史记录
     */
    loadLocalImageHistory() {
      if (!this.projectId) return

      const storageKey = `ai_story_studio_project_${this.projectId}_history`
      try {
        const stored = localStorage.getItem(storageKey)
        if (stored) {
          this.localImageHistory = JSON.parse(stored)
          console.log('[EditorStore] 已加载本地历史记录:', Object.keys(this.localImageHistory).length, '组')
        }
      } catch (error) {
        console.error('[EditorStore] 加载本地历史记录失败:', error)
      }
    },

    /**
     * 保存本地历史记录
     */
    saveLocalImageHistory() {
      if (!this.projectId) return

      const storageKey = `ai_story_studio_project_${this.projectId}_history`
      try {
        localStorage.setItem(storageKey, JSON.stringify(this.localImageHistory))
      } catch (error) {
        console.error('[EditorStore] 保存本地历史记录失败:', error)
      }
    },

    /**
     * 清理过期的历史记录（7天前）
     */
    cleanExpiredHistory() {
      const now = new Date().getTime()
      const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000
      let hasChanges = false

      Object.keys(this.localImageHistory).forEach(key => {
        const records = this.localImageHistory[key]
        const validRecords = records.filter(record => {
          const createdAt = new Date(record.createdAt).getTime()
          return (now - createdAt) < SEVEN_DAYS_MS
        })

        if (validRecords.length !== records.length) {
          this.localImageHistory[key] = validRecords
          hasChanges = true
        }
      })

      if (hasChanges) {
        console.log('[EditorStore] 清理了过期历史记录')
        this.saveLocalImageHistory()
      }
    },

    /**
     * 获取资产的本地图片历史记录
     * @param assetType 资产类型: 'character' | 'scene' | 'prop'
     * @param assetId 资产ID
     * @param shotId 分镜ID (可选，用于隔离分镜的历史记录)
     */
    getLocalImageHistory(assetType: 'character' | 'scene' | 'prop', assetId: number, shotId?: number) {
      // 如果提供了 shotId，使用带 shotId 的 key
      if (shotId) {
        const key = `${assetType}_${assetId}_shot_${shotId}`
        // 同时合并通用历史记录（不带 shotId 的，为了兼容旧数据）
        const generalKey = `${assetType}_${assetId}`

        const shotHistory = this.localImageHistory[key] || []
        // 只在没有 shotId 特定历史时才混合通用历史，或者根据需求策略调整
        // 这里简单返回特定历史 + 通用历史中不重复的部分
        const generalHistory = this.localImageHistory[generalKey] || []

        // 简单合并，去重
        const combined = [...shotHistory]
        const urls = new Set(shotHistory.map(r => r.url))

        generalHistory.forEach(r => {
          if (!urls.has(r.url)) {
            combined.push(r)
            urls.add(r.url)
          }
        })

        return combined.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      }

      const key = `${assetType}_${assetId}`
      return this.localImageHistory[key] || []
    },

    /**
     * 添加本地图片到历史记录
     * @param assetType 资产类型: 'character' | 'scene' | 'prop'
     * @param assetId 资产ID
     * @param record 历史记录
     * @param shotId 分镜ID (可选，用于隔离分镜的历史记录)
     */
    addLocalImageHistory(
      assetType: 'character' | 'scene' | 'prop',
      assetId: number,
      record: { id: number; url: string; prompt: string | null; versionNo: number; createdAt: string },
      shotId?: number
    ) {
      // 确定存储的 key
      const key = shotId
        ? `${assetType}_${assetId}_shot_${shotId}`
        : `${assetType}_${assetId}`

      const history = this.localImageHistory[key] || []
      history.unshift(record)

      // 重新赋值整个对象以触发响应式更新
      this.localImageHistory = {
        ...this.localImageHistory,
        [key]: history
      }

      // 保存到 localStorage
      this.saveLocalImageHistory()

      console.log('[EditorStore] 添加本地历史记录:', key, this.localImageHistory)
    },

    /**
     * 清除特定资产的本地图片历史记录
     * @param assetType 资产类型: 'character' | 'scene' | 'prop'
     * @param assetId 资产ID
     * @param shotId 分镜ID (可选)
     */
    clearLocalImageHistory(assetType: 'character' | 'scene' | 'prop', assetId: number, shotId?: number) {
      const key = shotId
        ? `${assetType}_${assetId}_shot_${shotId}`
        : `${assetType}_${assetId}`

      delete this.localImageHistory[key]

      // 保存到 localStorage
      this.saveLocalImageHistory()
      console.log('[EditorStore] 已清除资产本地历史记录:', key)
    },

    /**
     * 删除特定资产的单个历史记录
     * @param assetType 资产类型: 'character' | 'scene' | 'prop'
     * @param assetId 资产ID
     * @param recordId 记录ID
     * @param shotId 分镜ID (可选)
     */
    deleteLocalImageHistory(
      assetType: 'character' | 'scene' | 'prop',
      assetId: number,
      recordId: number,
      shotId?: number
    ) {
      const key = shotId
        ? `${assetType}_${assetId}_shot_${shotId}`
        : `${assetType}_${assetId}`

      const history = this.localImageHistory[key] || []
      const newHistory = history.filter(record => record.id !== recordId)

      // 重新赋值整个对象以触发响应式更新
      this.localImageHistory = {
        ...this.localImageHistory,
        [key]: newHistory
      }

      // 保存到 localStorage
      this.saveLocalImageHistory()
      console.log('[EditorStore] 已删除单个历史记录:', key, recordId)
    },

    /**
     * 根据角色名称清除所有同名角色的本地图片历史记录
     */
    clearLocalImageHistoryByName(characterName: string) {
      // 找到所有同名角色的ID
      const matchingCharIds = this.characters
        .filter(c => c.name === characterName || (c as any).displayName === characterName)
        .map(c => c.id)

      // 清除这些角色的本地历史记录
      let hasChanges = false
      matchingCharIds.forEach(id => {
        // 清除通用记录
        const key = `character_${id}`
        if (this.localImageHistory[key]) {
          delete this.localImageHistory[key]
          hasChanges = true
        }

        // 清除带 shotId 的记录（需要遍历所有 key）
        Object.keys(this.localImageHistory).forEach(k => {
          if (k.startsWith(`character_${id}_shot_`)) {
            delete this.localImageHistory[k]
            hasChanges = true
          }
        })
      })

      if (hasChanges) {
        this.saveLocalImageHistory()
      }

      console.log('[EditorStore] 已清除同名角色本地历史记录, characterName:', characterName, 'ids:', matchingCharIds)
    },

    /**
     * Reset store
     */
    reset() {
      this.projectId = null
      this.shots = []
      this.characters = []
      this.scenes = []
      this.selectedShotIds.clear()
      this.loading = false
      // Clear history
      this.history = []
      this.historyIndex = -1
      // Clear local image history
      this.localImageHistory = {}
    },
  },
})
