import { defineStore } from 'pinia'
import { toolboxApi, jobApi } from '@/api/apis'
import type {
  ToolboxGenerateRequest,
  ToolboxGenerateResponse,
  ToolboxHistoryVO,
  JobVO,
} from '@/types/api'
import {
  TOOLBOX_MODELS,
  calculateEstimatedCost,
  getDefaultModel,
  getDefaultAspectRatio,
} from '@/constants/toolboxModels'

// 对话项接口
export interface ConversationItem {
  id: string
  timestamp: number
  role: 'user' | 'ai'
  contentType: 'TEXT' | 'IMAGE' | 'VIDEO'

  // 用户气泡数据
  userInput?: {
    type: 'TEXT' | 'IMAGE' | 'VIDEO'
    model: string
    aspectRatio?: string
    prompt: string
    estimatedCost: number
    duration?: number // 视频时长
  }

  // AI气泡数据
  aiResponse?: {
    status: 'GENERATING' | 'READY' | 'FAILED'
    text?: string // 文字结果
    resultUrl?: string // 图片/视频URL(主图片)
    allImageUrls?: string[] // 所有生成的图片URL列表
    costPoints: number // 实际消耗
    generationTime: number // 生成时间(秒)
    errorMessage?: string
    historyId?: number // 关联的历史记录ID
  }
}

interface ToolboxState {
  history: ToolboxHistoryVO[]
  loading: boolean
  currentGeneration: ToolboxGenerateResponse | null
  pollingJobId: number | null
  pollingTimer: ReturnType<typeof setInterval> | null
  pollingConversationId: string | null // 新增: 轮询任务对应的对话 ID

  // 新增: 对话记录
  conversations: ConversationItem[]

  // 新增: 当前输入状态
  currentInput: {
    type: 'TEXT' | 'IMAGE' | 'VIDEO'
    model: string
    aspectRatio: string
    prompt: string
    duration: number // 视频时长
  }
}

export const useToolboxStore = defineStore('toolbox', {
  state: (): ToolboxState => ({
    history: [],
    loading: false,
    currentGeneration: null,
    pollingJobId: null,
    pollingTimer: null,
    pollingConversationId: null,
    conversations: [],
    currentInput: {
      type: 'TEXT',
      model: getDefaultModel('TEXT'),
      aspectRatio: '',
      prompt: '',
      duration: 5,
    },
  }),

  getters: {
    // Get history filtered by type
    historyByType: (state) => (type: 'TEXT' | 'IMAGE' | 'VIDEO' | 'ALL') => {
      if (type === 'ALL') return state.history
      return state.history.filter((item) => item.type === type)
    },

    // Check if currently generating
    isGenerating: (state) => state.pollingJobId !== null || state.loading,

    // Get models config
    models: () => TOOLBOX_MODELS,

    // Calculate current estimated cost
    estimatedCost: (state) => {
      return calculateEstimatedCost(state.currentInput.type, state.currentInput.model, {
        promptLength: state.currentInput.prompt.length,
        duration: state.currentInput.duration,
      })
    },
  },

  actions: {
    /**
     * Generate content (text/image/video)
     */
    async generate(data: ToolboxGenerateRequest) {
      this.loading = true
      try {
        console.log('[ToolboxStore] Generating:', data)
        const response = await toolboxApi.generate(data)
        console.log('[ToolboxStore] Generation response:', response)
        console.log('[ToolboxStore] Response structure:', {
          hasJobId: !!response.jobId,
          hasText: !!response.text,
          hasResultUrl: !!response.resultUrl,
          status: response.status,
          type: data.type,
          fullResponse: JSON.stringify(response, null, 2)
        })

        this.currentGeneration = response

        // If async job (video), start polling
        if (response.jobId) {
          this.startJobPolling(response.jobId)
        } else {
          // Sync result (text/image), refresh history after 500ms delay
          // to avoid race condition with backend DB write
          console.log('[ToolboxStore] Sync generation complete, waiting 500ms before fetching history')
          setTimeout(async () => {
            await this.fetchHistory()
          }, 500)
        }

        return response
      } catch (error) {
        console.error('[ToolboxStore] Generate failed:', error)
        throw error
      } finally {
        this.loading = false
      }
    },

    /**
     * Fetch generation history (last 7 days)
     */
    async fetchHistory() {
      this.loading = true
      try {
        console.log('[ToolboxStore] Fetching history')
        const history = await toolboxApi.getHistory({ page: 1, size: 100 })
        console.log('[ToolboxStore] API returned:', history)
        this.history = Array.isArray(history) ? history : []
        console.log('[ToolboxStore] History loaded:', this.history.length, 'items')

        // Log first 3 items for inspection
        if (this.history.length > 0) {
          console.log('[ToolboxStore] Sample history items:', this.history.slice(0, 3).map(item => ({
            id: item.id,
            type: item.type,
            status: item.status,
            hasText: !!item.text,
            hasResultUrl: !!item.resultUrl,
            textLength: item.text?.length || 0,
            prompt: item.prompt ? item.prompt.substring(0, 50) : '(null)'
          })))
        }
      } catch (error) {
        console.error('[ToolboxStore] Fetch history failed:', error)
        this.history = []
      } finally {
        this.loading = false
      }
    },

    /**
     * Delete history item
     */
    async deleteHistory(id: number) {
      try {
        console.log('[ToolboxStore] Deleting history:', id)
        await toolboxApi.deleteHistory(id)
        this.history = this.history.filter((item) => item.id !== id)
        console.log('[ToolboxStore] History deleted')
      } catch (error) {
        console.error('[ToolboxStore] Delete history failed:', error)
        throw error
      }
    },

    /**
     * Save history item to asset library
     */
    async saveToAssets(id: number) {
      try {
        console.log('[ToolboxStore] Saving to assets:', id)
        await toolboxApi.saveToAssets(id)
        console.log('[ToolboxStore] Saved to assets')
        // Optionally update the history item status
        await this.fetchHistory()
      } catch (error) {
        console.error('[ToolboxStore] Save to assets failed:', error)
        throw error
      }
    },

    /**
     * Start job polling for async tasks (video generation)
     */
    startJobPolling(jobId: number, conversationId?: string) {
      // Clear existing timer if any
      this.stopJobPolling()

      console.log('[ToolboxStore] Starting job polling for:', jobId, 'conversationId:', conversationId)
      this.pollingJobId = jobId
      this.pollingConversationId = conversationId || null

      this.pollingTimer = setInterval(async () => {
        try {
          const job: JobVO = await jobApi.getJobStatus(jobId)
          console.log('[ToolboxStore] Job status:', job.status, 'Progress:', job.progress)

          if (job.status === 'COMPLETED' || job.status === 'SUCCEEDED') {
            console.log('[ToolboxStore] Job completed!')
            console.log('[ToolboxStore] Job data:', {
              resultUrl: job.resultUrl,
              allImageUrls: job.allImageUrls,
              allImageUrlsLength: job.allImageUrls?.length || 0
            })
            
            // 如果有关联的对话ID,更新对话状态
            if (this.pollingConversationId) {
              this.updateAIResponse(this.pollingConversationId, {
                status: 'READY',
                resultUrl: job.resultUrl || undefined,
                allImageUrls: job.allImageUrls || undefined,
                costPoints: job.costPoints || 0,
              })
            }
            
            this.stopJobPolling()
            await this.fetchHistory()
            // Notify user
            window.$message?.success('生成完成!')
          } else if (job.status === 'FAILED') {
            console.error('[ToolboxStore] Job failed:', job.errorMessage)
            
            // 如果有关联的对话ID,更新对话状态
            if (this.pollingConversationId) {
              this.updateAIResponse(this.pollingConversationId, {
                status: 'FAILED',
                errorMessage: job.errorMessage || '生成失败',
              })
            }
            
            this.stopJobPolling()
            window.$message?.error(`生成失败: ${job.errorMessage || '未知错误'}`)
          }
        } catch (error) {
          console.error('[ToolboxStore] Polling error:', error)
          // Don't stop polling on network errors, retry
        }
      }, 3000) // Poll every 3 seconds
    },

    /**
     * Stop job polling
     */
    stopJobPolling() {
      if (this.pollingTimer) {
        console.log('[ToolboxStore] Stopping job polling')
        clearInterval(this.pollingTimer)
        this.pollingTimer = null
        this.pollingJobId = null
        this.pollingConversationId = null
      }
    },

    /**
     * Reset store state
     */
    reset() {
      this.stopJobPolling()
      this.history = []
      this.loading = false
      this.currentGeneration = null
      this.conversations = []
      this.currentInput = {
        type: 'TEXT',
        model: getDefaultModel('TEXT'),
        aspectRatio: '',
        prompt: '',
        duration: 5,
      }
    },

    /**
     * 更新当前输入类型,自动切换默认模型和比例
     */
    updateInputType(type: 'TEXT' | 'IMAGE' | 'VIDEO') {
      this.currentInput.type = type
      this.currentInput.model = getDefaultModel(type)
      this.currentInput.aspectRatio = getDefaultAspectRatio(type)
      if (type === 'VIDEO') {
        this.currentInput.duration = 5
      }
    },

    /**
     * 添加对话到列表
     */
    addConversation(item: ConversationItem) {
      this.conversations.push(item)
      console.log('[ToolboxStore] Conversation added:', item)
      this.saveConversationsToStorage()
    },

    /**
     * 更新AI响应状态
     */
    updateAIResponse(conversationId: string, response: Partial<ConversationItem['aiResponse']>) {
      const conversation = this.conversations.find((c) => c.id === conversationId)
      if (conversation && conversation.aiResponse) {
        Object.assign(conversation.aiResponse, response)
        console.log('[ToolboxStore] AI response updated:', conversationId, response)
        this.saveConversationsToStorage()
      }
    },

    /**
     * 保存对话到 localStorage
     */
    saveConversationsToStorage() {
      try {
        // 只保存最近的50条对话
        const toSave = this.conversations.slice(-50)
        localStorage.setItem('toolbox_conversations', JSON.stringify(toSave))
      } catch (e) {
        console.warn('[ToolboxStore] Failed to save conversations to localStorage:', e)
      }
    },

    /**
     * 从 localStorage 恢复对话
     */
    loadConversationsFromStorage() {
      try {
        const saved = localStorage.getItem('toolbox_conversations')
        if (saved) {
          const parsed = JSON.parse(saved) as ConversationItem[]
          // 过滤掉 GENERATING 状态的对话（别人可能无法继续）
          this.conversations = parsed.filter(c => 
            c.role === 'user' || c.aiResponse?.status !== 'GENERATING'
          )
          console.log('[ToolboxStore] Loaded conversations from storage:', this.conversations.length)
        }
      } catch (e) {
        console.warn('[ToolboxStore] Failed to load conversations from localStorage:', e)
      }
    },

    /**
     * 初始化 store，加载持久化数据
     */
    async init() {
      // 从 localStorage 恢复对话
      this.loadConversationsFromStorage()
      // 加载历史记录
      await this.fetchHistory()
    },

    /**
     * 从历史记录加载到对话区
     */
    loadHistoryToChat(historyItem: ToolboxHistoryVO) {
      console.log('[ToolboxStore] Loading history to chat:', historyItem)
      console.log('[ToolboxStore] History item details:', {
        id: historyItem.id,
        type: historyItem.type,
        status: historyItem.status,
        hasText: !!historyItem.text,
        hasResultUrl: !!historyItem.resultUrl,
        resultUrl: historyItem.resultUrl,
        text: historyItem.text?.substring(0, 50),
      })

      // 清空现有对话
      this.conversations = []

      // 创建用户气泡
      const userConversationId = `user-${Date.now()}`
      this.addConversation({
        id: userConversationId,
        timestamp: new Date(historyItem.createdAt).getTime(),
        role: 'user',
        contentType: historyItem.type as 'TEXT' | 'IMAGE' | 'VIDEO',
        userInput: {
          type: historyItem.type as 'TEXT' | 'IMAGE' | 'VIDEO',
          model: historyItem.model || 'unknown',
          aspectRatio: historyItem.aspectRatio || '',
          prompt: historyItem.prompt || '(无提示词)',
          estimatedCost: historyItem.costPoints || 0,
        },
      })

      // 创建AI气泡
      const aiConversationId = `ai-${Date.now()}`
      // 将后端的SUCCEEDED状态转换为READY(前端AIBubble识别的状态)
      const status = historyItem.status === 'SUCCEEDED' ? 'READY' : historyItem.status as 'GENERATING' | 'READY' | 'FAILED'
      
      const aiResponse = {
        status,
        text: historyItem.text || undefined,
        resultUrl: historyItem.resultUrl || undefined,
        costPoints: historyItem.costPoints || 0,
        generationTime: 0, // 历史记录没有这个字段
        historyId: historyItem.id,
      }
      
      console.log('[ToolboxStore] Creating AI conversation with aiResponse:', aiResponse)
      
      this.addConversation({
        id: aiConversationId,
        timestamp: new Date(historyItem.createdAt).getTime() + 1,
        role: 'ai',
        contentType: historyItem.type as 'TEXT' | 'IMAGE' | 'VIDEO',
        aiResponse,
      })
      
      console.log('[ToolboxStore] Conversations after loading:', this.conversations)
    },

    /**
     * 使用当前输入发起生成 (改造generate方法为对话式)
     */
    async generateWithConversation(referenceImageUrl?: string) {
      const startTime = Date.now()

      // 1. 创建用户气泡
      const userConversationId = `user-${Date.now()}`
      this.addConversation({
        id: userConversationId,
        timestamp: Date.now(),
        role: 'user',
        contentType: this.currentInput.type,
        userInput: {
          type: this.currentInput.type,
          model: this.currentInput.model,
          aspectRatio: this.currentInput.aspectRatio,
          prompt: this.currentInput.prompt,
          estimatedCost: this.estimatedCost,
          duration: this.currentInput.type === 'VIDEO' ? this.currentInput.duration : undefined,
        },
      })

      // 2. 创建AI气泡(GENERATING状态)
      const aiConversationId = `ai-${Date.now()}`
      this.addConversation({
        id: aiConversationId,
        timestamp: Date.now() + 1,
        role: 'ai',
        contentType: this.currentInput.type,
        aiResponse: {
          status: 'GENERATING',
          costPoints: 0,
          generationTime: 0,
        },
      })

      // 3. 调用生成API
      const request: ToolboxGenerateRequest = {
        type: this.currentInput.type,
        prompt: this.currentInput.prompt,
        model: this.currentInput.model,
        aspectRatio: this.currentInput.aspectRatio || undefined,
        duration: this.currentInput.type === 'VIDEO' ? this.currentInput.duration : undefined,
        referenceImageUrl: referenceImageUrl || undefined, // 添加参考图片URL
      }

      try {
        const response = await this.generate(request)

        const generationTime = Math.round((Date.now() - startTime) / 1000)

        // 4. 更新AI气泡状态
        // 如果是异步任务(有jobId且状态不是SUCCEEDED),保持GENERATING状态,等待轮询完成
        if (response.jobId && response.status !== 'SUCCEEDED') {
          console.log('[ToolboxStore] Async job started, keeping GENERATING state')
          // 开始轮询,并传递conversationId
          this.startJobPolling(response.jobId, aiConversationId)
        } else {
          // 同步任务(文本生成)或已完成任务,立即更新为READY
          console.log('[ToolboxStore] Sync task or already succeeded, updating to READY')
          this.updateAIResponse(aiConversationId, {
            status: 'READY',
            text: response.text || undefined,
            resultUrl: response.resultUrl || undefined,
            costPoints: response.costPoints || 0,
            generationTime,
          })
        }

        // 5. 清空输入框
        this.currentInput.prompt = ''

        return { success: true, aiConversationId }
      } catch (error: any) {
        // 更新为失败状态
        this.updateAIResponse(aiConversationId, {
          status: 'FAILED',
          errorMessage: error.message || '生成失败',
          generationTime: Math.round((Date.now() - startTime) / 1000),
        })

        throw error
      }
    },
  },
})
