<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useEditorStore } from '@/stores/editor'
import { generationApi, uploadApi, toolboxApi, assetApi } from '@/api/apis'
import { jobApi, pollJobStatus } from '@/api/job'
import api from '@/api/index'
import type { StoryboardShotVO, JobVO } from '@/types/api'

const editorStore = useEditorStore()

// Props定义
const props = defineProps<{
  shotId: number
  shotNo: number
}>()

// Emits定义
const emit = defineEmits<{
  close: []
}>()

// 当前分镜数据
const currentShot = computed<StoryboardShotVO | undefined>(() => {
  return editorStore.shots.find(s => s.id === props.shotId)
})

// 数量选择
const quantity = ref(1)

// 比例选择
const aspectRatio = ref('21:9')
const aspectRatioOptions = [
  { label: '21:9', value: '21:9' },
  { label: '16:9', value: '16:9' },
  { label: '1:1', value: '1:1' },
  { label: '9:16', value: '9:16' },
]

// AI参考图（用户手动上传）
const referenceImage = ref<File | null>(null)
const referenceImageUrl = ref<string>('')

// 生成的图片预览
const generatedImageUrl = ref<string>('')

// 是否正在生成
const isGenerating = ref(false)

// 历史记录（持久化到localStorage）
const generationHistory = ref<Array<{
  id: number
  shotId: number
  imageUrl: string
  timestamp: string
  isSelected: boolean
  prompt?: string
  userInput?: string
  expiresAt: number
}>>([])

// 加载历史记录（从 localStorage 加载并过滤过期记录）
const loadHistory = () => {
  try {
    const storageKey = `shot_generation_history_${props.shotId}`
    const stored = localStorage.getItem(storageKey)
    if (stored) {
      const parsed = JSON.parse(stored)
      const now = Date.now()
      // 过滤过期记录
      generationHistory.value = parsed.filter((item: any) => item.expiresAt > now)
      // 如果有过滤，更新 localStorage
      if (generationHistory.value.length !== parsed.length) {
        saveHistory()
      }
    }
  } catch (error) {
    console.error('[加载历史记录失败]', error)
  }
}

// 保存历史记录到 localStorage
const saveHistory = () => {
  try {
    const storageKey = `shot_generation_history_${props.shotId}`
    localStorage.setItem(storageKey, JSON.stringify(generationHistory.value))
  } catch (error) {
    console.error('[保存历史记录失败]', error)
  }
}

// 减少数量
const decreaseQuantity = () => {
  if (quantity.value > 1) {
    quantity.value--
  }
}

// 增加数量
const increaseQuantity = () => {
  if (quantity.value < 10) {
    quantity.value++
  }
}

// 上传AI参考图
const handleReferenceImageUpload = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    referenceImage.value = file
    referenceImageUrl.value = URL.createObjectURL(file)
  }
}

// 触发文件选择
const triggerReferenceImageInput = () => {
  const input = document.getElementById('shot-reference-image-input') as HTMLInputElement
  input?.click()
}

// 本地图片上传
const handleLocalImageUpload = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    generatedImageUrl.value = URL.createObjectURL(file)
    window.$message?.success('图片上传成功')
  }
}

// 触发本地图片上传
const triggerLocalImageInput = () => {
  const input = document.getElementById('local-shot-image-input') as HTMLInputElement
  input?.click()
}

// 拼接角色、场景、道具图片
const mergeAssetImages = async (): Promise<string | null> => {
  if (!currentShot.value) return null
  
  console.log('[ShotImageGeneratePanel] ========== 开始收集图片URL ==========')
  console.log('[ShotImageGeneratePanel] currentShot.value:', currentShot.value)
  
  const imageUrls: string[] = []
  
  // 收集角色图片
  console.log('[ShotImageGeneratePanel] 角色列表:', currentShot.value.characters)
  if (currentShot.value.characters && currentShot.value.characters.length > 0) {
    currentShot.value.characters.forEach((char, index) => {
      console.log(`[ShotImageGeneratePanel] 角色${index + 1}: ${char.characterName}, thumbnailUrl: ${char.thumbnailUrl}`)
      if (char.thumbnailUrl) {
        imageUrls.push(char.thumbnailUrl)
        console.log(`[ShotImageGeneratePanel] ✅ 角色${index + 1}已添加`)
      } else {
        console.log(`[ShotImageGeneratePanel] ❌ 角色${index + 1} thumbnailUrl为空`)
      }
    })
  } else {
    console.log('[ShotImageGeneratePanel] ⚠️ 没有角色')
  }
  
  // 收集场景图片
  console.log('[ShotImageGeneratePanel] 场景:', currentShot.value.scene)
  if (currentShot.value.scene?.thumbnailUrl) {
    console.log(`[ShotImageGeneratePanel] 场景: ${currentShot.value.scene.sceneName}, thumbnailUrl: ${currentShot.value.scene.thumbnailUrl}`)
    imageUrls.push(currentShot.value.scene.thumbnailUrl)
    console.log('[ShotImageGeneratePanel] ✅ 场景已添加')
  } else {
    console.log('[ShotImageGeneratePanel] ❌ 场景thumbnailUrl为空或没有场景')
  }
  
  // 收集道具图片
  console.log('[ShotImageGeneratePanel] 道具列表:', currentShot.value.props)
  if (currentShot.value.props && currentShot.value.props.length > 0) {
    currentShot.value.props.forEach((prop, index) => {
      console.log(`[ShotImageGeneratePanel] 道具${index + 1}: ${prop.propName}, thumbnailUrl: ${prop.thumbnailUrl}`)
      if (prop.thumbnailUrl) {
        imageUrls.push(prop.thumbnailUrl)
        console.log(`[ShotImageGeneratePanel] ✅ 道具${index + 1}已添加`)
      } else {
        console.log(`[ShotImageGeneratePanel] ❌ 道具${index + 1} thumbnailUrl为空`)
      }
    })
  } else {
    console.log('[ShotImageGeneratePanel] ⚠️ 没有道具')
  }
  
  console.log('[ShotImageGeneratePanel] ========== 收集完成 ==========')
  console.log('[ShotImageGeneratePanel] 收集到的图片URL数量:', imageUrls.length)
  console.log('[ShotImageGeneratePanel] 收集到的图片URL:', imageUrls)
  
  if (imageUrls.length === 0) {
    return null
  }
  
  try {
    // 调用后端拼接接口
    const response = await api.post('/utils/images/merge', {
      imageUrls
    })
    
    console.log('[ShotImageGeneratePanel] 图片拼接成功:', response.mergedImageUrl)
    return response.mergedImageUrl
  } catch (error) {
    console.error('[ShotImageGeneratePanel] 图片拼接失败:', error)
    // 如果拼接失败，返回第一张图片作为备用方案
    return imageUrls[0]
  }
}

// AI生成
const handleAIGenerate = async () => {
  
  if (!editorStore.projectId) {
    window.$message?.error('项目ID不存在')
    return
  }
  
  isGenerating.value = true
  
  try {
    // 1. 拼接角色、场景、道具图片（作为参考图，但不显示在UI上）
    const mergedImageUrl = await mergeAssetImages()
    console.log('[ShotImageGeneratePanel] 参考图拼接结果:', mergedImageUrl)
    
    // 2. 构建完整的提示词：内嵌规则 + 分镜剧本
    const fixedTemplate = '你是一个漫画分镜师，擅长在同一画布下，画多镜头的黑白漫画分镜图，要求布局合理，分镜图禁止出现场景、背景和文字，主要以人物和道具为主，排版格式为横向排版，接下来我会给你剧本：'
    
    // 获取当前分镜的剧本内容
    const shotScript = currentShot.value?.scriptText || ''
    
    // 构建prompt：内嵌规则 + 分镜剧本
    let customPrompt = fixedTemplate
    if (shotScript) {
      customPrompt += shotScript
    } else {
      customPrompt += '（无剧本内容）'
    }
    
    console.log('[ShotImageGeneratePanel] 生成参数:', {
      shotId: props.shotId,
      customPrompt,
      aspectRatio: aspectRatio.value
    })
    
    // 3. 调用批量生成接口（支持自定义prompt和参考图）
    const response = await generationApi.generateSingleShot(
      editorStore.projectId,
      props.shotId,
      {
        aspectRatio: aspectRatio.value as '1:1' | '16:9' | '9:16' | '21:9',
        customPrompt: customPrompt,
        referenceImageUrl: mergedImageUrl || undefined // 传入拼接的参考图
      }
    )
    
    console.log('[ShotImageGeneratePanel] 生成响应:', response)
    
    // 4. 轮询Job状态
    if (response.jobId) {
      window.$message?.info('图片生成中，请稍候...')
      
      const finalJob = await pollJobStatus(
        response.jobId,
        (job: JobVO) => {
          console.log('[ShotImageGeneratePanel] Job进度:', job.progress, '%')
        },
        3000 // 3秒轮询一次
      )
      
      console.log('[ShotImageGeneratePanel] Job完成:', finalJob)
      
      // 5. 保存生成结果到历史记录
      if (finalJob.resultUrl) {
        const newHistoryItem = {
          id: Date.now(),
          shotId: props.shotId,
          imageUrl: finalJob.resultUrl,
          timestamp: new Date().toLocaleString('zh-CN'),
          isSelected: false,
          prompt: customPrompt, // 完整的prompt（内嵌规则 + 分镜剧本）
          expiresAt: Date.now() + 7 * 24 * 60 * 60 * 1000 // 7天后过期
        }
        
        generationHistory.value.unshift(newHistoryItem)
        generatedImageUrl.value = newHistoryItem.imageUrl
        
        // 保存到 localStorage
        saveHistory()
        
        // 刷新分镜列表（批量生成接口已自动更新资产）
        await editorStore.fetchShots()
        
        window.$message?.success('分镜图生成成功，已自动应用')
      }
    }
    
  } catch (error: any) {
    console.error('[ShotImageGeneratePanel] 生成失败:', error)
    window.$message?.error(error.message || '生成失败，请重试')
  } finally {
    isGenerating.value = false
  }
}

// 应用图片到分镜（提取为独立函数）
const applyImageToShot = async (imageUrl: string) => {
  console.log('[ShotImageGeneratePanel] applyImageToShot 被调用:', {
    imageUrl,
    currentShot: currentShot.value,
    shotImage: currentShot.value?.shotImage,
    assetId: currentShot.value?.shotImage?.assetId
  })
  
  try {
    if (!currentShot.value?.shotImage?.assetId) {
      console.warn('[ShotImageGeneratePanel] 分镜资产未初始化，assetId为null')
      window.$message?.warning('分镜资产未初始化，请先生成一次分镜图后再使用历史记录')
      return
    }
    
    console.log('[ShotImageGeneratePanel] 开始应用图片:', imageUrl)
    window.$message?.info('正在应用图片到分镜...')
    
    // 直接使用后端API从URL上传，解决CORS问题
    console.log('[ShotImageGeneratePanel] 调用后端API从URL上传:', currentShot.value.shotImage.assetId)
    await assetApi.uploadFromUrl(
      currentShot.value.shotImage.assetId,
      imageUrl
    )
    console.log('[ShotImageGeneratePanel] 上传成功')
    
    // 刷新分镜列表，更新显示
    console.log('[ShotImageGeneratePanel] 刷新分镜列表')
    await editorStore.fetchShots()
    console.log('[ShotImageGeneratePanel] 刷新完成')
    
    console.log('[ShotImageGeneratePanel] 图片应用成功')
    window.$message?.success('图片已应用到分镜')
  } catch (error: any) {
    console.error('[ShotImageGeneratePanel] 应用图片失败:', error)
    window.$message?.error('应用图片失败: ' + (error.message || '未知错误'))
    throw error
  }
}

// 选择历史记录并应用到分镜
const handleSelectHistory = async (id: number) => {
  console.log('[ShotImageGeneratePanel] 选择历史记录:', id)
  
  generationHistory.value.forEach(item => {
    item.isSelected = item.id === id
  })
  
  // 更新大图预览
  const selected = generationHistory.value.find(item => item.id === id)
  if (!selected) return
  
  console.log('[ShotImageGeneratePanel] 设置大图预览:', selected.imageUrl)
  generatedImageUrl.value = selected.imageUrl
  
// 应用图片到分镜
  await applyImageToShot(selected.imageUrl)
  
  window.$message?.success('已加载并应用历史记录')
}

// 删除历史记录
const handleDeleteHistory = (id: number) => {
  try {
    // 从数组中移除
    generationHistory.value = generationHistory.value.filter(item => item.id !== id)
    
    // 保存到localStorage
    saveHistory()
    
    console.log('[ShotImageGeneratePanel] 已删除历史记录:', id)
    window.$message?.success('已删除历史记录')
  } catch (error: any) {
    console.error('[ShotImageGeneratePanel] 删除历史记录失败:', error)
    window.$message?.error('删除失败: ' + (error.message || ''))
  }
}

// 清除大图预览
const handleClearPreview = () => {
  generatedImageUrl.value = null
  // 取消所有历史记录的选中状态
  generationHistory.value.forEach(item => {
    item.isSelected = false
  })
  console.log('[ShotImageGeneratePanel] 已清除预览图')
  window.$message?.success('已清除预览图')
}

// 下载分镜图片（使用a标签直接下载，避免CORS）
const handleDownloadShotImage = () => {
  if (!generatedImageUrl.value) return
  try {
    const link = document.createElement('a')
    link.href = generatedImageUrl.value
    link.download = `shot_${props.shotNo}_${Date.now()}.jpg`
    link.target = '_blank'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.$message?.success('开始下载')
  } catch (error) {
    console.error('下载失败:', error)
    window.$message?.error('下载失败')
  }
}

// 复制分镜图片到剪贴板（通过后端代理避免CORS）
const handleCopyShotImage = async () => {
  if (!generatedImageUrl.value) return
  try {
    window.$message?.info('正在复制...')
    
    // 通过后端下载图片
    const response = await fetch('/api/asset/download-from-url', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify({ url: generatedImageUrl.value })
    })
    
    if (!response.ok) {
      throw new Error('下载图片失败')
    }
    
    const blob = await response.blob()
    
    // 复制到剪贴板
    await navigator.clipboard.write([
      new ClipboardItem({
        [blob.type]: blob
      })
    ])
    
    window.$message?.success('已复制到剪贴板')
  } catch (error) {
    console.error('复制失败:', error)
    window.$message?.error('复制失败，请尝试右键保存')
  }
}

// 组件挂载时初始化
onMounted(() => {
  // 不再自动填入分镜剧本文本，改为用户完全自定义
  console.log('[ShotImageGeneratePanel] 组件挂载:', {
    shotId: props.shotId,
    shotNo: props.shotNo,
    currentShot: currentShot.value,
    shotImage: currentShot.value?.shotImage,
    assetId: currentShot.value?.shotImage?.assetId,
    characters: currentShot.value?.characters,
    scene: currentShot.value?.scene,
    props: currentShot.value?.props
  })
  
  // 加载历史记录
  loadHistory()
  
  // 如果当前分镜已有缩略图，显示在大图预览区
  if (currentShot.value?.shotImage?.thumbnailUrl) {
    generatedImageUrl.value = currentShot.value.shotImage.thumbnailUrl
  }
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
      <!-- 大图预览区 -->
      <div class="group relative w-full aspect-video rounded bg-bg-subtle mb-4 overflow-hidden">
        <template v-if="generatedImageUrl">
          <img
            :src="generatedImageUrl"
            alt="分镜预览"
            class="w-full h-full object-cover"
          >
          <!-- 操作按钮组（悬浮显示） -->
          <div class="absolute top-3 right-3 flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
            <!-- 下载按钮 -->
            <button
              @click="handleDownloadShotImage"
              class="p-2 rounded-lg bg-gray-800 hover:bg-gray-700 transition-colors"
              title="下载图片"
            >
              <svg class="w-5 h-5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
            </button>
            <!-- 复制按钮 -->
            <button
              @click="handleCopyShotImage"
              class="p-2 rounded-lg bg-gray-800 hover:bg-gray-700 transition-colors"
              title="复制图片"
            >
              <svg class="w-5 h-5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
              </svg>
            </button>
            <!-- 删除按钮 -->
            <button
              @click="handleClearPreview"
              class="p-2 rounded-lg bg-gray-800 hover:bg-gray-700 transition-colors"
              title="清除预览图"
            >
              <svg class="w-5 h-5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
              </svg>
            </button>
          </div>
        </template>
        <template v-else>
          <div class="w-full h-full flex items-center justify-center">
            <div class="text-center">
              <div class="w-16 h-16 mx-auto mb-3 rounded bg-bg-subtle flex items-center justify-center">
                <svg class="w-8 h-8 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
                </svg>
              </div>
            </div>
          </div>
        </template>

        <!-- 本地图片按钮 -->
        <button
          @click="triggerLocalImageInput"
          class="absolute bottom-3 right-3 px-4 py-2 bg-[#2a2a2a] hover:bg-[#3a3a3a] rounded-lg text-white text-sm font-medium transition-all border border-border-subtle"
        >
          本地图片
        </button>
        <input
          id="local-shot-image-input"
          type="file"
          accept="image/*"
          @change="handleLocalImageUpload"
          class="hidden"
        >
      </div>

      <!-- 底部控制栏 -->
      <div class="flex items-center justify-end gap-3 mb-8">
        <!-- 数量选择器 -->
        <div class="flex items-center bg-bg-hover rounded-lg">
          <button
            @click="decreaseQuantity"
            class="w-9 h-9 flex items-center justify-center text-text-secondary hover:text-text-primary transition-colors"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 12H4"></path>
            </svg>
          </button>
          <div class="w-8 text-center text-text-primary text-sm font-medium">{{ quantity }}</div>
          <button
            @click="increaseQuantity"
            class="w-9 h-9 flex items-center justify-center text-text-secondary hover:text-text-primary transition-colors"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
            </svg>
          </button>
        </div>

        <!-- 比例选择 -->
        <select
          v-model="aspectRatio"
          class="px-3 py-2 bg-bg-hover border-none rounded-lg text-text-primary text-sm focus:outline-none cursor-pointer"
        >
          <option v-for="option in aspectRatioOptions" :key="option.value" :value="option.value" class="bg-bg-elevated">
            {{ option.label }}
          </option>
        </select>

        <!-- AI生成按钮 -->
        <button
          @click="handleAIGenerate"
          :disabled="isGenerating"
          :class="[
            'px-6 py-2 rounded-lg font-medium text-sm transition-all flex items-center gap-2',
            isGenerating
              ? 'bg-gray-600 cursor-not-allowed opacity-60 text-gray-300'
              : 'bg-purple-600 text-white hover:bg-purple-700'
          ]"
        >
          <template v-if="isGenerating">
            <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            生成中...
          </template>
          <template v-else>
            AI生成
          </template>
        </button>
      </div>

      <!-- 历史记录 -->
      <div class="border-t border-border-default pt-6">
        <h4 class="text-text-primary text-sm font-medium mb-4">生成历史</h4>

        <div v-if="generationHistory.length === 0" class="text-center py-8">
          <p class="text-text-tertiary text-sm">暂无生成记录</p>
        </div>

        <div v-else>
          <div class="grid grid-cols-4 gap-2 mb-3">
            <div
              v-for="item in generationHistory"
              :key="item.id"
              :class="[
                'group relative aspect-square rounded overflow-hidden cursor-pointer transition-all',
                item.isSelected
                  ? 'ring-2 ring-[#00FFCC]'
                  : 'hover:ring-2 hover:ring-[#00FFCC]/50'
              ]"
            >
              <img :src="item.imageUrl" alt="历史记录" class="w-full h-full object-cover" @click="handleSelectHistory(item.id)">

              <!-- 选中标记 -->
              <div
                v-if="item.isSelected"
                class="absolute top-1 right-1 w-6 h-6 bg-gray-900 rounded flex items-center justify-center pointer-events-none"
              >
                <svg class="w-4 h-4 text-black" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7"></path>
                </svg>
              </div>
              
              <!-- 删除按钮（悬浮显示） -->
              <button
                @click.stop="handleDeleteHistory(item.id)"
                class="absolute top-1 left-1 w-6 h-6 rounded bg-red-500/80 flex items-center justify-center hover:bg-red-500 transition-all opacity-0 group-hover:opacity-100 z-10"
                title="删除历史记录"
              >
                <svg class="w-3.5 h-3.5 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </button>
              
              <!-- 过期时间标签 -->
              <div class="absolute bottom-0 left-0 right-0 bg-gray-800 px-1 py-0.5 pointer-events-none">
                <p class="text-text-secondary text-[10px] text-center truncate">
                  {{ item.timestamp }}
                </p>
              </div>
            </div>
          </div>

          <p class="text-[#FF6B9D] text-xs text-center">
            生成记录保留7天，请及时保存或下载
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
