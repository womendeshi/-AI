<script setup lang="ts">
import type { ConversationItem } from '@/stores/toolbox'
import { useToolboxStore } from '@/stores/toolbox'
import { watch } from 'vue'

interface Props {
  conversation: ConversationItem
}

const props = defineProps<Props>()
const toolboxStore = useToolboxStore()

// 调试: 监听aiResponse变化
watch(() => props.conversation.aiResponse, (newVal) => {
  if (newVal && props.conversation.contentType === 'IMAGE') {
    console.log('[AIBubble] IMAGE aiResponse updated:', {
      status: newVal.status,
      hasResultUrl: !!newVal.resultUrl,
      hasAllImageUrls: !!newVal.allImageUrls,
      allImageUrlsLength: newVal.allImageUrls?.length || 0,
      allImageUrls: newVal.allImageUrls
    })
  }
}, { deep: true })

// 复制文字
const copyText = async () => {
  if (!props.conversation.aiResponse?.text) return

  try {
    await navigator.clipboard.writeText(props.conversation.aiResponse.text)
    window.$message?.success('已复制到剪贴板')
  } catch (error) {
    console.error('Failed to copy:', error)
    window.$message?.error('复制失败')
  }
}

// 打开图片
const openImage = (url?: string) => {
  const imageUrl = url || props.conversation.aiResponse?.resultUrl
  if (imageUrl) {
    window.open(imageUrl, '_blank')
  }
}

// 下载单个图片
const downloadImage = (url: string, index: number) => {
  const link = document.createElement('a')
  link.href = url
  link.download = `toolbox-image-${Date.now()}-${index + 1}`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)

  window.$message?.success(`开始下载图片 ${index + 1}`)
}

// 下载文件
const downloadFile = () => {
  if (!props.conversation.aiResponse?.resultUrl) return

  const url = props.conversation.aiResponse.resultUrl
  const link = document.createElement('a')
  link.href = url
  link.download = `toolbox-${props.conversation.contentType.toLowerCase()}-${Date.now()}`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)

  window.$message?.success('开始下载')
}

// 保存到角色库
const saveToCharacterLibrary = async () => {
  if (!props.conversation.aiResponse?.historyId) {
    window.$message?.warning('无法保存:缺少历史记录ID')
    return
  }

  try {
    await toolboxStore.saveToAssets(props.conversation.aiResponse.historyId)
    window.$message?.success('已保存到角色库')
  } catch (error: any) {
    window.$message?.error(error.message || '保存失败')
  }
}

// 保存到场景库
const saveToSceneLibrary = async () => {
  if (!props.conversation.aiResponse?.historyId) {
    window.$message?.warning('无法保存:缺少历史记录ID')
    return
  }

  try {
    await toolboxStore.saveToAssets(props.conversation.aiResponse.historyId)
    window.$message?.success('已保存到场景库')
  } catch (error: any) {
    window.$message?.error(error.message || '保存失败')
  }
}
</script>

<template>
  <div class="flex justify-start mb-2 gap-2">
    <!-- AI头像 -->
    <div class="w-8 h-8 rounded-full overflow-hidden flex-shrink-0 bg-[#8B5CF6] flex items-center justify-center">
      <svg class="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9.75 3.104v5.714a2.25 2.25 0 01-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 014.5 0m0 0v5.714c0 .597.237 1.17.659 1.591L19.8 15.3M14.25 3.104c.251.023.501.05.75.082M19.8 15.3l-1.57.393A9.065 9.065 0 0112 15a9.065 9.065 0 00-6.23.693L5 15.5m14.8-.2l.009.009a.75.75 0 01.213.686l-.473 1.893a.75.75 0 01-.635.556l-2.19.274a1.125 1.125 0 00-.768.467l-1.286 1.929a.75.75 0 01-1.24.002l-1.29-1.931a1.125 1.125 0 00-.768-.467l-2.19-.274a.75.75 0 01-.635-.556l-.473-1.893a.75.75 0 01.213-.686l.009-.009" />
      </svg>
    </div>
    <div class="max-w-[75%] bg-bg-subtle border border-border-default rounded p-2.5">
      <!-- GENERATING Status -->
      <div v-if="conversation.aiResponse?.status === 'GENERATING'" class="flex items-center gap-2 py-2 px-1">
        <div class="w-4 h-4 border-2 border-text-tertiary border-t-transparent rounded-full animate-spin"></div>
        <span class="text-text-tertiary text-xs">正在生成中...</span>
      </div>

      <!-- FAILED Status -->
      <div v-else-if="conversation.aiResponse?.status === 'FAILED'" class="flex items-center gap-2 py-1">
        <svg class="w-4 h-4 text-red-400 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
        </svg>
        <span class="text-red-400 text-xs">{{ conversation.aiResponse?.errorMessage || '生成失败' }}</span>
      </div>

      <!-- READY Status - TEXT -->
      <div v-else-if="conversation.contentType === 'TEXT' && conversation.aiResponse?.text" class="group">
        <div class="flex items-start gap-2">
          <div class="flex-1 text-white text-sm leading-relaxed whitespace-pre-wrap break-words select-text">{{ conversation.aiResponse.text }}</div>
          <button
            class="flex-shrink-0 p-1.5 rounded-lg text-white/30 hover:text-text-primary hover:bg-bg-subtle transition-all opacity-0 group-hover:opacity-100"
            title="复制"
            @click="copyText"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
            </svg>
          </button>
        </div>
      </div>

      <!-- READY Status - IMAGE -->
      <div v-else-if="conversation.contentType === 'IMAGE' && conversation.aiResponse?.resultUrl" class="group">
        <!-- 多图展示 -->
        <div v-if="conversation.aiResponse.allImageUrls && conversation.aiResponse.allImageUrls.length > 1" class="grid grid-cols-2 gap-2">
          <div v-for="(url, index) in conversation.aiResponse.allImageUrls" :key="index" class="relative">
            <img
              :src="url"
              class="w-full rounded-lg cursor-pointer hover:opacity-90 transition-opacity"
              @click="openImage(url)"
            />
            <!-- 悬浮操作按钮 -->
            <div class="absolute top-1 right-1 flex gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
              <button
                class="p-1 rounded bg-gray-800 text-text-secondary hover:text-text-primary hover:bg-gray-600 transition-all"
                :title="`下载图片 ${index + 1}`"
                @click.stop="downloadImage(url, index)"
              >
                <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                </svg>
              </button>
            </div>
          </div>
        </div>
        <!-- 单图展示 -->
        <div v-else class="relative inline-block">
          <img
            :src="conversation.aiResponse.resultUrl"
            class="max-w-xs rounded-lg cursor-pointer hover:opacity-90 transition-opacity"
            @click="openImage(conversation.aiResponse.resultUrl)"
          />
          <!-- 悬浮操作按钮 -->
          <div class="absolute top-2 right-2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            <button
              class="p-1.5 rounded-lg bg-gray-800 text-text-secondary hover:text-text-primary hover:bg-gray-600 transition-all"
              title="下载"
              @click.stop="downloadFile"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
            </button>
            <button
              class="p-1.5 rounded-lg bg-gray-800 text-text-secondary hover:text-text-primary hover:bg-gray-600 transition-all"
              title="保存到角色库"
              @click.stop="saveToCharacterLibrary"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
            </button>
            <button
              class="p-1.5 rounded-lg bg-gray-800 text-text-secondary hover:text-text-primary hover:bg-gray-600 transition-all"
              title="保存到场景库"
              @click.stop="saveToSceneLibrary"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      <!-- READY Status - VIDEO -->
      <div v-else-if="conversation.contentType === 'VIDEO' && conversation.aiResponse?.resultUrl" class="group">
        <div class="relative inline-block">
          <video
            :src="conversation.aiResponse.resultUrl"
            class="max-w-xs rounded-lg"
            controls
            preload="metadata"
          />
          <!-- 悬浮操作按钮 -->
          <div class="absolute top-2 right-2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            <button
              class="p-1.5 rounded-lg bg-gray-800 text-text-secondary hover:text-text-primary hover:bg-gray-600 transition-all"
              title="下载"
              @click.stop="downloadFile"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
            </button>
            <button
              class="p-1.5 rounded-lg bg-gray-800 text-text-secondary hover:text-text-primary hover:bg-gray-600 transition-all"
              title="保存到资产库"
              @click.stop="saveToSceneLibrary"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      <!-- Footer: Time (Only for READY status, compact) -->
      <div v-if="conversation.aiResponse?.status === 'READY'" class="mt-1.5 flex items-center gap-3 text-[10px] text-white/30">
        <span>耗时 {{ conversation.aiResponse?.generationTime || 0 }}s</span>
      </div>
    </div>
  </div>
</template>
