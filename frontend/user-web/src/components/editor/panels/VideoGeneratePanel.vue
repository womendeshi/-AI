<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useEditorStore } from '@/stores/editor'
import api from '@/api'
import { uploadApi } from '@/api/apis'
import * as generationApi from '@/api/generation'
import { jobApi, pollJobStatus } from '@/api/job'

// Props定义
const props = defineProps<{
  shotId: number
  shotNo: number
}>()

// Emits定义
const emit = defineEmits<{
  close: []
}>()

const editorStore = useEditorStore()

// 当前分镜数据
const currentShot = computed(() => {
  return editorStore.shots.find(s => s.id === props.shotId)
})

// 用户自定义内容输入
const scriptDescription = ref('')

// 比例选择
const aspectRatio = ref('16:9')
const aspectRatioOptions = [
  { label: '16:9', value: '16:9' },
  { label: '21:9', value: '21:9' },
  { label: '1:1', value: '1:1' },
  { label: '9:16', value: '9:16' },
]

// 生成状态
const isGenerating = ref(false)

// 视频参考图URL（拼接后的图片）
const videoReferenceUrl = ref<string>('')
const referenceImageFile = ref<File | null>(null)

// 当前显示的缩略图URL（待生成区域）
const generatedVideoThumbnail = ref<string>('')

// 备选素材（从分镜图中选择）
const availableMaterials = ref<Array<{
  id: number
  imageUrl: string
  isSelected: boolean
}>>([])  // 初始为空，从分镜图历史记录加载

// 历史记录
interface VideoHistoryItem {
  id: number
  videoUrl: string
  thumbnailUrl: string
  timestamp: string
  prompt: string
  userInput: string
  expiresAt: number
}

const generationHistory = ref<VideoHistoryItem[]>([])
const loadingHistory = ref(false)

const handleReferenceImageUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  try {
    referenceImageFile.value = file
    const result = await uploadApi.upload(file, 'image')
    videoReferenceUrl.value = result.url
    window.$message?.success('参考图上传成功')
  } catch (error: any) {
    console.error('[VideoGeneratePanel] 参考图上传失败:', error)
    window.$message?.error('参考图上传失败')
  } finally {
    target.value = ''
  }
}

const triggerReferenceImageInput = () => {
  const input = document.getElementById('video-reference-image-input') as HTMLInputElement
  input?.click()
}
// 收集当前分镜的所有资源（不拼接，直接传递）
const collectAssetResources = () => {
  if (!currentShot.value) return null
  
  console.log('[VideoGeneratePanel] ========== 开始收集资源 ==========')
  
  const resources = {
    script: currentShot.value.scriptText || '',
    shotImage: null as { thumbnailUrl: string } | null,
    scene: null as { id: number; name: string; thumbnailUrl: string } | null,
    characters: [] as Array<{ id: number; name: string; thumbnailUrl: string }>,
    props: [] as Array<{ id: number; name: string; thumbnailUrl: string }>
  }
  
  // 收集剧本
  if (resources.script) {
    console.log('[VideoGeneratePanel] ✅ 剧本已收集')
  }
  
  // 收集分镜图
  if (currentShot.value.shotImage?.thumbnailUrl) {
    resources.shotImage = {
      thumbnailUrl: currentShot.value.shotImage.thumbnailUrl
    }
    console.log('[VideoGeneratePanel] ✅ 分镜图已收集')
  }
  
  // 收集场景
  if (currentShot.value.scene?.thumbnailUrl) {
    resources.scene = {
      id: currentShot.value.scene.id,
      name: currentShot.value.scene.sceneName || '场景',
      thumbnailUrl: currentShot.value.scene.thumbnailUrl
    }
    console.log('[VideoGeneratePanel] ✅ 场景已收集:', resources.scene.name)
  }
  
  // 收集角色
  if (currentShot.value.characters && currentShot.value.characters.length > 0) {
    currentShot.value.characters.forEach((char) => {
      if (char.thumbnailUrl) {
        resources.characters.push({
          id: char.id,
          name: char.characterName || '角色',
          thumbnailUrl: char.thumbnailUrl
        })
        console.log('[VideoGeneratePanel] ✅ 角色已收集:', char.characterName)
      }
    })
  }
  
  // 收集道具
  if (currentShot.value.props && currentShot.value.props.length > 0) {
    currentShot.value.props.forEach((prop) => {
      if (prop.thumbnailUrl) {
        resources.props.push({
          id: prop.id,
          name: prop.propName || '道具',
          thumbnailUrl: prop.thumbnailUrl
        })
        console.log('[VideoGeneratePanel] ✅ 道具已收集:', prop.propName)
      }
    })
  }
  
  console.log('[VideoGeneratePanel] 资源收集完成:', {
    hasScript: !!resources.script,
    hasShotImage: !!resources.shotImage,
    hasScene: !!resources.scene,
    characterCount: resources.characters.length,
    propCount: resources.props.length
  })
  
  return resources
}

// 保存历史记录到 localStorage
const saveHistory = () => {
  const key = `video_history_shot_${props.shotId}`
  localStorage.setItem(key, JSON.stringify(generationHistory.value))
  console.log('[VideoGeneratePanel] 历史记录已保存')
}

const updateHistoryVideoUrl = (jobId: number, resultUrl: string) => {
  const index = generationHistory.value.findIndex(item => item.id === jobId)
  if (index === -1) return
  generationHistory.value[index].videoUrl = resultUrl
  saveHistory()
}

const startPollingJobStatus = async (jobId: number) => {
  try {
    const job = await pollJobStatus(jobId)
    if (job.resultUrl) {
      updateHistoryVideoUrl(jobId, job.resultUrl)
    } else {
      window.$message?.warning('Video ready but url missing')
    }
  } catch (error) {
    console.error('[VideoGeneratePanel] poll job failed:', error)
    window.$message?.error('Video generation failed')
  }
}

const hydrateHistoryVideoUrls = async () => {
  const pendingItems = generationHistory.value.filter(item => !item.videoUrl)
  if (pendingItems.length === 0) return
  for (const item of pendingItems) {
    try {
      const job = await jobApi.getJobStatus(item.id)
      if (job.resultUrl) {
        updateHistoryVideoUrl(item.id, job.resultUrl)
      }
    } catch (error) {
      console.error('[VideoGeneratePanel] hydrate job failed:', error)
    }
  }
}

// 加载历史记录
const loadHistory = () => {
  const key = `video_history_shot_${props.shotId}`
  const stored = localStorage.getItem(key)
  if (stored) {
    try {
      const history = JSON.parse(stored) as VideoHistoryItem[]
      // 过滤掉过期的记录（7天）
      generationHistory.value = history.filter((item) => {
        return Date.now() < item.expiresAt
      })
      console.log('[VideoGeneratePanel] 加载历史记录:', generationHistory.value.length, '条')
      // 保存过滤后的结果
      if (generationHistory.value.length !== history.length) {
        saveHistory()
      }
    } catch (error) {
      console.error('[VideoGeneratePanel] 加载历史记录失败:', error)
      generationHistory.value = []
    }
  }
  hydrateHistoryVideoUrls()
}

// AI生成视频
const handleAIGenerate = async () => {
  if (!editorStore.projectId) {
    window.$message?.error('项目ID不存在')
    return
  }
  
  isGenerating.value = true
  
  try {
    // 1. 收集当前分镜的所有资源（剧本、分镜图、场景、角色、道具）
    const resources = collectAssetResources()
    
    if (!resources) {
      window.$message?.error('无法获取分镜资源')
      return
    }
    
    // 2. 构建完整的提示词：内嵌规则 + 分镜剧本
    const fixedTemplate = '根据参考图的设定，使用参考图中的角色、场景、道具，运用合理的构建分镜，合理的动作，合理的运镜，合理的环境渲染，发散你的想象力，生成保持风格一致性的2D动漫视频，要求不要字幕和BGM，没有台词时禁止说话，线条细致，人物画风保持与参考图一致，清晰不模糊，颜色鲜艳，光影效果，超清画质，电影级镜头（cinematicdvnamiccamera）,音质清晰无杂质，第一个镜头0.3秒空境，请忠实原文，不增加原文没有的内容，不减少原文包含的信息，分镜要求如下：'
    
    // 构建prompt：内嵌规则 + 分镜剧本
    let customPrompt = fixedTemplate
    if (resources.script) {
      customPrompt += resources.script
    } else {
      customPrompt += '（无剧本内容）'
    }
    
    console.log('[VideoGeneratePanel] 生成参数:', {
      shotId: props.shotId,
      customPrompt,
      aspectRatio: aspectRatio.value,
      resources
    })
    
    // 3. 调用后端视频生成接口
    const response = await api.post(
      `/projects/${editorStore.projectId}/generate/shot-video/${props.shotId}`,
      {
        prompt: customPrompt,
        aspectRatio: aspectRatio.value || '16:9',
        referenceImageUrl: videoReferenceUrl.value || undefined,
        shotImage: resources.shotImage,
        scene: resources.scene,
        characters: resources.characters,
        props: resources.props
      }
    )
    
    console.log('[VideoGeneratePanel] 生成响应:', response)
    
    // 4. 获取返回的jobId
    const jobId = response.jobId
    
    // 使用第一个可用的图片作为缩略图（优先使用分镜图）
    let mockThumbnailUrl = 'https://via.placeholder.com/400x225'
    if (resources.shotImage?.thumbnailUrl) {
      mockThumbnailUrl = resources.shotImage.thumbnailUrl
    } else if (resources.scene?.thumbnailUrl) {
      mockThumbnailUrl = resources.scene.thumbnailUrl
    } else if (resources.characters.length > 0) {
      mockThumbnailUrl = resources.characters[0].thumbnailUrl
    } else if (resources.props.length > 0) {
      mockThumbnailUrl = resources.props[0].thumbnailUrl
    }
    
    // 5. 保存到历史记录（使用jobId）
    const newHistoryItem: VideoHistoryItem = {
      id: jobId, // 使用后端返回的jobId
      videoUrl: '', // 视频URL将在任务完成后更新
      thumbnailUrl: mockThumbnailUrl,
      timestamp: new Date().toLocaleString('zh-CN'),
      prompt: customPrompt,
      userInput: scriptDescription.value,
      expiresAt: Date.now() + 7 * 24 * 60 * 60 * 1000 // 7天后过期
    }
    
    generationHistory.value.unshift(newHistoryItem)
    generatedVideoThumbnail.value = mockThumbnailUrl
    
    // 仅在未上传参考图时，用素材图做展示
    if (!videoReferenceUrl.value) {
      videoReferenceUrl.value = mockThumbnailUrl
    }
    
    // 保存到 localStorage
    saveHistory()
    
    window.$message?.success(`视频生成任务已提交，任务ID: ${jobId}。请等待生成完成...`)
    
    startPollingJobStatus(jobId)
    
  } catch (error: any) {
    console.error('[VideoGeneratePanel] 生成失败:', error)
    window.$message?.error(error.response?.data?.message || error.message || '生成失败，请重试')
  } finally {
    isGenerating.value = false
  }
}

// 选择备选素材（切换待生成缩略图）
const handleSelectMaterial = (material: any) => {
  generatedVideoThumbnail.value = material.imageUrl
  console.log('[VideoGeneratePanel] 选择备选素材:', material.id)
}

// 点击历史记录（切换待生成缩略图）
const handleHistoryClick = (item: VideoHistoryItem) => {
  generatedVideoThumbnail.value = item.thumbnailUrl
  // 回填用户输入
  scriptDescription.value = item.userInput
  console.log('[VideoGeneratePanel] 选择历史记录:', item.id)
}

// 删除历史记录
const handleDeleteHistory = (id: number) => {
  const index = generationHistory.value.findIndex(item => item.id === id)
  if (index !== -1) {
    generationHistory.value.splice(index, 1)
    saveHistory()
    window.$message?.success('已删除历史记录')
  }
}

// 下载视频
const handleDownloadVideo = async (videoUrl: string, fileName: string = '视频') => {
  if (!videoUrl) {
    window.$message?.warning('视频地址为空')
    return
  }
  try {
    const response = await fetch('/api/assets/download-from-url', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: videoUrl })
    })
    if (!response.ok) {
      throw new Error(`Download failed: ${response.status}`)
    }
    const blob = await response.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `${fileName}_${Date.now()}.mp4`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)
    window.$message?.success('下载成功')
  } catch (error) {
    console.error('[VideoGeneratePanel] 下载失败:', error)
    window.$message?.error('下载失败')
  }
}

// 应用视频到分镜
const handleApplyVideo = async (videoUrl: string) => {
  // TODO: 实现应用视频逻辑
  console.log('[VideoGeneratePanel] 应用视频:', videoUrl)
  window.$message?.info('应用视频功能开发中')
}

// 组件挂载时加载数据
onMounted(() => {
  console.log('[VideoGeneratePanel] 组件挂载, shotId:', props.shotId)
  loadHistory()
  // TODO: 加载备选素材（从分镜图历史记录）
})
</script>

<template>
  <div class="flex flex-col h-full bg-bg-elevated">
    <!-- 顶部导航 -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-border-subtle">
      <button
        @click="$emit('close')"
        class="flex items-center gap-2 text-text-secondary hover:text-text-primary transition-colors"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
        </svg>
        <span class="text-sm font-medium">返回</span>
      </button>
      <h3 class="text-text-primary text-base font-medium">分镜 #{{ shotNo }}</h3>
    </div>

    <!-- 主内容区 -->
    <div class="flex-1 overflow-y-auto px-6 py-6">
      <!-- 视频参考图区域 -->
      <div class="mb-6">
        <div class="flex items-center justify-between mb-3">
          <h4 class="text-text-primary text-base font-medium">视频参考图</h4>
        </div>

        <!-- 大虚线框预览区 -->
        <div
          class="w-full aspect-video rounded border-2 border-dashed border-border-default bg-bg-subtle flex items-center justify-center overflow-hidden cursor-pointer hover:bg-bg-hover transition-colors"
          @click="triggerReferenceImageInput"
        >
          <template v-if="videoReferenceUrl">
            <img :src="videoReferenceUrl" alt="视频参考图" class="w-full h-full object-cover rounded">
          </template>
          <template v-else>
            <p class="text-text-tertiary text-sm">参考图</p>
          </template>
        </div>
        <input
          id="video-reference-image-input"
          type="file"
          accept="image/*"
          class="hidden"
          @change="handleReferenceImageUpload"
        >
      </div>

      <!-- 用户自定义内容输入框 -->
      <div class="mb-6">
        <textarea
          v-model="scriptDescription"
          placeholder="请输入自定义内容（将添加到内嵌规则和分镜剧本之后）"
          class="w-full h-24 px-4 py-3 bg-bg-hover border border-border-default rounded text-text-primary text-sm placeholder-text-tertiary resize-none focus:outline-none focus:border-gray-900/50"
        ></textarea>
      </div>

      <!-- 底部控制栏 -->
      <div class="flex items-center justify-between mb-6">
        <!-- 比例选择 -->
        <select
          v-model="aspectRatio"
          class="px-4 py-2.5 bg-bg-hover border border-border-default rounded text-text-primary text-sm focus:outline-none focus:border-gray-900/50 cursor-pointer"
        >
          <option v-for="option in aspectRatioOptions" :key="option.value" :value="option.value" class="bg-bg-elevated">
            {{ option.label }}
          </option>
        </select>

        <!-- AI生成按钮 -->
        <button
          @click="handleAIGenerate"
          :disabled="isGenerating"
          class="px-10 py-3 bg-bg-subtle rounded text-text-secondary font-medium text-sm hover:bg-bg-hover transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {{ isGenerating ? '生成中...' : 'AI生成' }}
        </button>
      </div>

      <!-- 待生成区域 -->
      <div class="mb-6 border-t border-border-default pt-6">
        <h4 class="text-text-primary text-sm font-medium mb-4">待生成</h4>
        <div class="w-full aspect-video rounded border-2 border-dashed border-border-default bg-bg-subtle flex items-center justify-center overflow-hidden">
          <template v-if="generatedVideoThumbnail">
            <img :src="generatedVideoThumbnail" alt="待生成缩略图" class="w-full h-full object-cover rounded">
          </template>
          <template v-else>
            <div class="text-center">
              <svg class="w-16 h-16 mx-auto text-text-disabled mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"></path>
              </svg>
              <p class="text-text-tertiary text-sm">待生成</p>
            </div>
          </template>
        </div>
      </div>

      <!-- 备选素材 -->
      <div class="border-t border-border-default pt-6 mb-6">
        <div class="flex items-center justify-between mb-4">
          <h4 class="text-text-primary text-sm font-medium">备选素材 ({{ availableMaterials.length }})</h4>
        </div>

        <div v-if="availableMaterials.length === 0" class="text-center py-8">
          <p class="text-text-tertiary text-sm">暂无备选素材</p>
        </div>

        <div v-else class="grid grid-cols-4 gap-2">
          <div
            v-for="material in availableMaterials"
            :key="material.id"
            @click="handleSelectMaterial(material)"
            class="relative aspect-square rounded overflow-hidden cursor-pointer hover:ring-2 hover:ring-[#00FFCC]/50 transition-all group"
          >
            <img :src="material.imageUrl" alt="备选素材" class="w-full h-full object-cover">
            
            <!-- 悬浮按钮 -->
            <div class="absolute top-1 right-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button 
                @click.stop="handleDownloadVideo(material.imageUrl, '备选素材')"
                class="p-1.5 bg-gray-900/80 rounded hover:bg-gray-800 transition-colors"
                title="下载"
              >
                <svg class="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 历史记录 -->
      <div class="border-t border-border-default pt-6">
        <div class="flex items-center justify-between mb-4">
          <h4 class="text-text-primary text-sm font-medium">历史记录</h4>
        </div>

        <div v-if="generationHistory.length === 0" class="text-center py-8">
          <p class="text-text-tertiary text-sm mb-2">暂无生成记录</p>
          <p class="text-[#FF6B9D] text-xs">
            未被使用的生成记录仅保疙7天，请及时下载文件
          </p>
        </div>

        <div v-else>
          <div class="grid grid-cols-4 gap-2 mb-3">
            <div
              v-for="item in generationHistory.slice(0, 8)"
              :key="item.id"
              @click="handleHistoryClick(item)"
              class="relative aspect-square rounded overflow-hidden cursor-pointer hover:ring-2 hover:ring-[#00FFCC]/50 transition-all group"
            >
              <img :src="item.thumbnailUrl" alt="历史记录" class="w-full h-full object-cover">

              <!-- 播放按钮覆盖 -->
              <div class="absolute inset-0 bg-bg-subtle flex items-center justify-center">
                <svg class="w-10 h-10 text-text-secondary" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M8 5v14l11-7z"></path>
                </svg>
              </div>
              
              <!-- 悬浮按钮 -->
              <div class="absolute top-1 right-1 opacity-0 group-hover:opacity-100 transition-opacity flex gap-1">
                <button 
                  @click.stop="handleDownloadVideo(item.videoUrl, `分镜${props.shotNo}_视频`)"
                  class="p-1.5 bg-gray-800 rounded hover:bg-gray-700 transition-colors"
                  title="下载"
                >
                  <svg class="w-3.5 h-3.5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                  </svg>
                </button>
                <button 
                  @click.stop="handleDeleteHistory(item.id)"
                  class="p-1.5 bg-gray-800 rounded hover:bg-gray-700 transition-colors"
                  title="删除"
                >
                  <svg class="w-3.5 h-3.5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                  </svg>
                </button>
              </div>
            </div>
          </div>

          <p class="text-[#FF6B9D] text-xs text-center">
            未被使用的生成记录仅保疙7天，请及时下载文件
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
