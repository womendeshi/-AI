<script setup lang="ts">
import { computed } from 'vue'
import { useToolboxStore } from '@/stores/toolbox'
import DocumentIcon from '@/components/icons/DocumentIcon.vue'
import ImageIcon from '@/components/icons/ImageIcon.vue'
import VideoIcon from '@/components/icons/VideoIcon.vue'
import type { ToolboxHistoryVO } from '@/types/api'

const toolboxStore = useToolboxStore()

// Format date
const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    return '今天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } else if (days === 1) {
    return '昨天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } else {
    return date.toLocaleDateString('zh-CN') + ' ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
}

// Get status badge
const getStatusBadge = (status: string) => {
  switch (status) {
    case 'GENERATING':
      return { text: '生成中', class: 'bg-bg-subtle text-text-secondary' }
    case 'READY':
      return { text: '已完成', class: 'bg-bg-subtle text-text-primary' }
    case 'FAILED':
      return { text: '失败', class: 'bg-bg-subtle text-red-400' }
    default:
      return { text: '未知', class: 'bg-bg-hover text-text-tertiary' }
  }
}

// Get type icon component
const getTypeIconComponent = (type: string) => {
  switch (type) {
    case 'TEXT':
      return DocumentIcon
    case 'IMAGE':
      return ImageIcon
    case 'VIDEO':
      return VideoIcon
    default:
      return DocumentIcon
  }
}

// Handle delete
const handleDelete = async (id: number) => {
  if (!confirm('确定要删除这条历史记录吗？')) return

  try {
    await toolboxStore.deleteHistory(id)
    window.$message?.success('删除成功')
  } catch (error: any) {
    window.$message?.error(error.message || '删除失败')
  }
}

// Handle save to assets
const handleSaveToAssets = async (id: number) => {
  try {
    await toolboxStore.saveToAssets(id)
    window.$message?.success('已保存到资产库')
  } catch (error: any) {
    window.$message?.error(error.message || '保存失败')
  }
}

// Handle preview
const handlePreview = (item: ToolboxHistoryVO) => {
  console.log('[HistoryList] Preview clicked for item:', {
    id: item.id,
    type: item.type,
    status: item.status,
    hasText: !!item.text,
    textLength: item.text?.length || 0,
    hasResultUrl: !!item.resultUrl,
    resultUrl: item.resultUrl
  })

  if (item.type === 'TEXT') {
    // For TEXT type, show in a browser dialog (simple implementation)
    // TODO: Replace with a proper modal component in future
    if (item.text) {
      const formattedText = item.text.replace(/\n/g, '\n')
      alert(formattedText)
    } else {
      console.warn('[HistoryList] TEXT item has no text field:', item)
      window.$message?.warning('文本内容不可用')
    }
  } else if (item.resultUrl) {
    // For IMAGE/VIDEO, open in new tab
    window.open(item.resultUrl, '_blank')
  } else {
    console.warn('[HistoryList] Item has no resultUrl:', item)
    window.$message?.warning('内容不可用')
  }
}

// Computed history
const sortedHistory = computed(() => {
  return [...toolboxStore.history].sort((a, b) => {
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  })
})
</script>

<template>
  <div class="space-y-3">
    <!-- Empty State -->
    <div
      v-if="toolboxStore.history.length === 0"
      class="text-center py-12"
    >
      <DocumentIcon class="w-16 h-16 text-text-disabled mx-auto mb-3" />
      <p class="text-text-tertiary text-sm">暂无生成记录</p>
    </div>

    <!-- History List -->
    <div v-else class="space-y-3 max-h-[500px] overflow-auto">
      <div
        v-for="item in sortedHistory"
        :key="item.id"
        class="p-4 rounded bg-bg-subtle border border-border-default hover:bg-bg-hover transition-all"
      >
        <div class="flex gap-3">
          <!-- Icon -->
          <div class="flex-shrink-0">
            <component :is="getTypeIconComponent(item.type)" class="w-6 h-6 text-text-tertiary" />
          </div>

          <!-- Content -->
          <div class="flex-1 min-w-0">
            <!-- Header -->
            <div class="flex items-start justify-between mb-2">
              <div class="flex items-center gap-2">
                <span :class="`px-2 py-0.5 rounded text-xs ${getStatusBadge(item.status).class}`">
                  {{ getStatusBadge(item.status).text }}
                </span>
                <span class="text-text-tertiary text-xs">{{ item.model }}</span>
              </div>
              <div class="text-text-tertiary text-xs whitespace-nowrap">
                {{ formatDate(item.createdAt) }}
              </div>
            </div>

            <p class="text-white/70 text-sm mb-3 line-clamp-2">
              {{ item.prompt }}
            </p>

            <!-- Preview: TEXT type -->
            <div v-if="item.type === 'TEXT' && item.text" class="mb-3 p-3 rounded bg-bg-subtle border border-border-default">
              <p class="text-text-secondary text-sm line-clamp-3 whitespace-pre-wrap">
                {{ item.text }}
              </p>
            </div>

            <!-- Preview: IMAGE type -->
            <div v-if="item.resultUrl && item.type === 'IMAGE'" class="mb-3">
              <img
                :src="item.resultUrl"
                class="max-w-[180px] rounded border border-border-default cursor-pointer hover:border-gray-900 transition-all"
                @click="handlePreview(item)"
              >
            </div>

            <!-- Preview: VIDEO type -->
            <div v-if="item.resultUrl && item.type === 'VIDEO'" class="mb-3">
              <video
                :src="item.resultUrl"
                class="max-w-[240px] rounded border border-border-default cursor-pointer hover:border-gray-900 transition-all"
                controls
                preload="metadata"
              />
            </div>

            <!-- Actions -->
            <div class="flex items-center gap-2">
              <button
                v-if="item.status === 'READY' && (item.resultUrl || (item.type === 'TEXT' && item.text))"
                class="px-3 py-1 rounded text-xs bg-bg-subtle border border-border-default text-text-tertiary hover:border-gray-900 hover:text-text-primary transition-all"
                @click="handlePreview(item)"
              >
                预览
              </button>
              <button
                v-if="item.status === 'READY'"
                class="px-3 py-1 rounded text-xs bg-bg-subtle border border-border-default text-text-tertiary hover:border-gray-900 hover:text-text-primary transition-all"
                @click="handleSaveToAssets(item.id)"
              >
                保存
              </button>
              <button
                class="px-3 py-1 rounded text-xs bg-bg-subtle border border-border-default text-text-tertiary hover:border-mochi-pink hover:text-red-400 transition-all"
                @click="handleDelete(item.id)"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
