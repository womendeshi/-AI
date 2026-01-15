<script setup lang="ts">
import { ref, computed } from 'vue'
import { useToolboxStore } from '@/stores/toolbox'
import type { ToolboxHistoryVO } from '@/types/api'

interface Props {
  show: boolean
}

interface Emits {
  (e: 'update:show', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const toolboxStore = useToolboxStore()

const searchQuery = ref('')
const filterType = ref<'ALL' | 'TEXT' | 'IMAGE' | 'VIDEO'>('ALL')

// 筛选后的历史记录
const filteredHistory = computed(() => {
  let items = toolboxStore.history

  // 类型筛选
  if (filterType.value !== 'ALL') {
    items = items.filter((item) => item.type === filterType.value)
  }

  // 搜索筛选
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.trim().toLowerCase()
    items = items.filter((item) => item.prompt.toLowerCase().includes(query))
  }

  return items
})

// 按日期分组
const groupedHistory = computed(() => {
  const groups: Record<string, ToolboxHistoryVO[]> = {}

  filteredHistory.value.forEach((item) => {
    const date = new Date(item.createdAt)
    const today = new Date()
    const yesterday = new Date(today)
    yesterday.setDate(yesterday.getDate() - 1)

    let groupKey = ''

    if (date.toDateString() === today.toDateString()) {
      groupKey = '今天'
    } else if (date.toDateString() === yesterday.toDateString()) {
      groupKey = '昨天'
    } else {
      groupKey = date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
    }

    if (!groups[groupKey]) {
      groups[groupKey] = []
    }

    groups[groupKey].push(item)
  })

  return groups
})

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

// 格式化时间
const formatTime = (dateStr: string) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / (1000 * 60))
  const hours = Math.floor(diff / (1000 * 60 * 60))

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 点击历史记录
const handleHistoryClick = (item: ToolboxHistoryVO) => {
  toolboxStore.loadHistoryToChat(item)
  emit('update:show', false)
  window.$message?.success('历史记录已加载到对话区')
}

// 关闭抽屉
const handleClose = () => {
  emit('update:show', false)
}
</script>

<template>
  <!-- Overlay -->
  <div
    v-if="show"
    class="fixed inset-0 z-40 transition-opacity pointer-events-none"
    @click="handleClose"
  />

  <!-- Drawer -->
  <div
    class="fixed top-0 right-0 h-full w-96 bg-bg-elevated border-l border-border-default shadow-2xl z-50 transform transition-transform duration-300 flex flex-col"
    :class="show ? 'translate-x-0' : 'translate-x-full'"
  >
    <!-- Header -->
    <div class="flex items-center justify-between p-4 border-b border-border-default">
      <h2 class="text-lg font-bold text-text-primary">历史记录</h2>
      <button
        class="w-8 h-8 rounded flex items-center justify-center text-text-tertiary hover:bg-bg-hover hover:text-text-primary transition-all"
        @click="handleClose"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>

    <!-- Search -->
    <div class="p-4 border-b border-border-default">
      <input
        v-model="searchQuery"
        class="w-full px-4 py-2 rounded bg-bg-subtle border border-border-default text-text-primary text-sm placeholder-white/30 focus:border-gray-900 focus:outline-none transition-all"
        placeholder="搜索提示词..."
      />
    </div>

    <!-- Filter Tabs -->
    <div class="flex items-center gap-1.5 px-4 py-2.5 border-b border-border-default">
      <button
        v-for="type in ['ALL', 'TEXT', 'IMAGE', 'VIDEO']"
        :key="type"
        class="px-2.5 py-1 rounded text-xs transition-all"
        :class="filterType === type
          ? 'bg-bg-subtle text-text-primary border border-gray-900/40'
          : 'bg-bg-subtle text-text-tertiary border border-border-default hover:bg-bg-hover'"
        @click="filterType = type as 'ALL' | 'TEXT' | 'IMAGE' | 'VIDEO'"
      >
        {{ type === 'ALL' ? '全部' : type === 'TEXT' ? '文字' : type === 'IMAGE' ? '图片' : '视频' }}
      </button>
    </div>

    <!-- History List -->
    <div class="flex-1 overflow-auto">
      <!-- Empty State -->
      <div v-if="Object.keys(groupedHistory).length === 0" class="flex flex-col items-center justify-center py-20">
        <svg class="w-16 h-16 text-text-disabled mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
        </svg>
        <p class="text-text-tertiary text-sm">暂无历史记录</p>
      </div>

      <!-- Grouped List -->
      <div v-else class="p-3 space-y-3">
        <div v-for="(items, groupKey) in groupedHistory" :key="groupKey" class="space-y-1.5">
          <!-- Group Label -->
          <div class="flex items-center gap-2 mb-1.5">
            <div class="w-1.5 h-1.5 rounded bg-gray-900/60"></div>
            <span class="text-xs font-medium text-text-tertiary">{{ groupKey }}</span>
          </div>

          <!-- Items -->
          <div
            v-for="item in items"
            :key="item.id"
            class="p-2.5 rounded bg-bg-subtle border border-border-default hover:bg-bg-hover hover:border-gray-900/40 cursor-pointer transition-all"
            @click="handleHistoryClick(item)"
          >
            <div class="flex items-start gap-2 mb-1.5">
              <!-- 图片/视频预览 -->
              <div v-if="item.type === 'IMAGE' && item.resultUrl" class="flex-shrink-0 w-12 h-12 rounded-lg overflow-hidden border border-border-default">
                <img :src="item.resultUrl" class="w-full h-full object-cover" />
              </div>
              <div v-else-if="item.type === 'VIDEO' && item.resultUrl" class="flex-shrink-0 w-12 h-12 rounded-lg overflow-hidden border border-border-default bg-bg-subtle flex items-center justify-center relative">
                <video :src="item.resultUrl" class="w-full h-full object-cover" muted />
                <div class="absolute inset-0 flex items-center justify-center bg-bg-subtle">
                  <svg class="w-4 h-4 text-text-secondary" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M8 5v14l11-7z" />
                  </svg>
                </div>
              </div>
              <span v-else v-html="getTypeIcon(item.type)" class="flex-shrink-0 mt-0.5"></span>
              
              <div class="flex-1 min-w-0">
                <p class="text-text-secondary text-xs line-clamp-2 break-words leading-relaxed">
                  {{ item.prompt || '(无提示词)' }}
                </p>
              </div>
            </div>

            <div class="flex items-center justify-between text-xs text-text-tertiary">
              <span>{{ formatTime(item.createdAt) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
