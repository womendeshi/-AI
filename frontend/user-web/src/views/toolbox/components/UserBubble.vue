<script setup lang="ts">
import type { ConversationItem } from '@/stores/toolbox'
import { useUserStore } from '@/stores/user'

interface Props {
  conversation: ConversationItem
}

const props = defineProps<Props>()
const userStore = useUserStore()

// 获取类型图标SVG
const getTypeIcon = (type: string) => {
  switch (type) {
    case 'TEXT':
      return `<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
      </svg>`
    case 'IMAGE':
      return `<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>`
    case 'VIDEO':
      return `<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
      </svg>`
    default:
      return `<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
      </svg>`
  }
}

// 获取类型名称
const getTypeName = (type: string) => {
  switch (type) {
    case 'TEXT':
      return '文字'
    case 'IMAGE':
      return '图片'
    case 'VIDEO':
      return '视频'
    default:
      return '未知'
  }
}
</script>

<template>
  <div class="flex justify-end mb-2 gap-2">
    <div class="max-w-[70%] bg-bg-subtle border border-border-default rounded p-3">
      <!-- Header: Type | Model | Ratio -->
      <div class="flex items-center gap-1.5 mb-1.5 text-xs">
        <span class="flex items-center gap-1 text-text-primary">
          <span v-html="getTypeIcon(conversation.userInput?.type || '')"></span>
          <span class="font-medium">{{ getTypeName(conversation.userInput?.type || '') }}</span>
        </span>
        <span class="text-text-tertiary">|</span>
        <span class="text-text-tertiary">{{ conversation.userInput?.model }}</span>
        <span v-if="conversation.userInput?.aspectRatio" class="text-text-tertiary">|</span>
        <span v-if="conversation.userInput?.aspectRatio" class="text-text-tertiary">
          {{ conversation.userInput?.aspectRatio }}
        </span>
        <span v-if="conversation.userInput?.duration" class="text-text-tertiary">|</span>
        <span v-if="conversation.userInput?.duration" class="text-text-tertiary">
          {{ conversation.userInput?.duration }}秒
        </span>
      </div>

      <!-- Prompt -->
      <div class="text-white text-sm leading-relaxed whitespace-pre-wrap break-words">
        {{ conversation.userInput?.prompt || '(无提示词)' }}
      </div>
    </div>
    <!-- 用户头像 -->
    <div class="w-8 h-8 rounded-full overflow-hidden flex-shrink-0 bg-bg-hover flex items-center justify-center">
      <img v-if="userStore.user?.avatar" :src="userStore.user.avatar" class="w-full h-full object-cover" />
      <svg v-else class="w-5 h-5 text-text-tertiary" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
      </svg>
    </div>
  </div>
</template>
