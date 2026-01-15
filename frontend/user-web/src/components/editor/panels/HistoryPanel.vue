<script setup lang="ts">
import { ref, computed } from 'vue'

// Emits定义
const emit = defineEmits<{
  close: []
}>()

// 筛选类型
const filterType = ref<'all' | 'shot_image' | 'video'>('all')

// 排序方式
const sortBy = ref<'date' | 'type'>('date')

// 历史记录数据（模拟）
const historyRecords = ref<Array<{
  id: number
  type: 'shot_image' | 'video'
  shotNo: number
  thumbnailUrl: string
  previewUrl: string
  timestamp: string
  aspectRatio: string
  status: 'active' | 'expired'
}>>([
  {
    id: 1,
    type: 'shot_image',
    shotNo: 1,
    thumbnailUrl: '/placeholder1.jpg',
    previewUrl: '/placeholder1.jpg',
    timestamp: '2024-01-04 10:00',
    aspectRatio: '21:9',
    status: 'active'
  },
  {
    id: 2,
    type: 'shot_image',
    shotNo: 1,
    thumbnailUrl: '/placeholder2.jpg',
    previewUrl: '/placeholder2.jpg',
    timestamp: '2024-01-04 10:05',
    aspectRatio: '16:9',
    status: 'active'
  },
  {
    id: 3,
    type: 'video',
    shotNo: 2,
    thumbnailUrl: '/placeholder3.jpg',
    previewUrl: '/video1.mp4',
    timestamp: '2024-01-04 10:10',
    aspectRatio: '16:9',
    status: 'active'
  },
  {
    id: 4,
    type: 'shot_image',
    shotNo: 3,
    thumbnailUrl: '/placeholder4.jpg',
    previewUrl: '/placeholder4.jpg',
    timestamp: '2024-01-03 15:30',
    aspectRatio: '1:1',
    status: 'expired'
  },
])

// 过滤后的记录
const filteredRecords = computed(() => {
  let records = historyRecords.value

  // 按类型过滤
  if (filterType.value !== 'all') {
    records = records.filter(r => r.type === filterType.value)
  }

  // 排序
  if (sortBy.value === 'date') {
    records = [...records].sort((a, b) =>
      new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
    )
  } else if (sortBy.value === 'type') {
    records = [...records].sort((a, b) => a.type.localeCompare(b.type))
  }

  return records
})

// 预览记录
const handlePreview = (record: any) => {
  console.log('[HistoryPanel] Preview record:', record)
  window.$message?.info('打开预览')
}

// 下载记录
const handleDownload = (record: any) => {
  console.log('[HistoryPanel] Download record:', record)
  window.$message?.success('开始下载')
}

// 删除记录
const handleDelete = (id: number) => {
  console.log('[HistoryPanel] Delete record:', id)
  const index = historyRecords.value.findIndex(r => r.id === id)
  if (index !== -1) {
    historyRecords.value.splice(index, 1)
    window.$message?.success('删除成功')
  }
}

// 批量删除过期记录
const handleDeleteExpired = () => {
  console.log('[HistoryPanel] Delete all expired records')
  const beforeCount = historyRecords.value.length
  historyRecords.value = historyRecords.value.filter(r => r.status !== 'expired')
  const deletedCount = beforeCount - historyRecords.value.length
  window.$message?.success(`已删除 ${deletedCount} 条过期记录`)
}

// 获取类型标签
const getTypeLabel = (type: 'shot_image' | 'video') => {
  return type === 'shot_image' ? '分镜图' : '视频'
}
</script>

<template>
  <div class="flex flex-col h-full bg-bg-elevated">
    <!-- 顶部导航 -->
    <div class="flex items-center gap-3 border-b border-border-default px-4 py-3">
      <button
        @click="$emit('close')"
        class="p-1.5 rounded hover:bg-bg-hover transition-colors"
        title="返回"
      >
        <svg class="w-5 h-5 text-text-secondary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
        </svg>
      </button>
      <h3 class="text-text-primary text-base font-medium">历史记录</h3>
    </div>

    <!-- 筛选和排序栏 -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-border-default">
      <!-- 类型筛选 -->
      <div class="flex items-center gap-2">
        <button
          @click="filterType = 'all'"
          :class="[
            'px-4 py-2 rounded text-sm font-medium transition-all',
            filterType === 'all'
              ? 'bg-bg-hover text-white'
              : 'text-text-tertiary hover:bg-bg-subtle'
          ]"
        >
          全部
        </button>
        <button
          @click="filterType = 'shot_image'"
          :class="[
            'px-4 py-2 rounded text-sm font-medium transition-all',
            filterType === 'shot_image'
              ? 'bg-bg-hover text-white'
              : 'text-text-tertiary hover:bg-bg-subtle'
          ]"
        >
          分镜图
        </button>
        <button
          @click="filterType = 'video'"
          :class="[
            'px-4 py-2 rounded text-sm font-medium transition-all',
            filterType === 'video'
              ? 'bg-bg-hover text-white'
              : 'text-text-tertiary hover:bg-bg-subtle'
          ]"
        >
          视频
        </button>
      </div>

      <!-- 排序和操作 -->
      <div class="flex items-center gap-2">
        <select
          v-model="sortBy"
          class="px-3 py-2 bg-bg-subtle border border-border-default rounded text-text-primary text-xs focus:outline-none focus:border-gray-900/50 cursor-pointer"
        >
          <option value="date" class="bg-bg-elevated">按日期排序</option>
          <option value="type" class="bg-bg-elevated">按类型排序</option>
        </select>

        <button
          @click="handleDeleteExpired"
          class="px-3 py-2 bg-red-500/20 rounded text-red-400 text-xs font-medium hover:bg-red-500/30 transition-colors"
        >
          清理过期
        </button>
      </div>
    </div>

    <!-- 历史记录列表 -->
    <div class="flex-1 overflow-y-auto px-4 py-4">
      <div v-if="filteredRecords.length === 0" class="text-center py-20">
        <svg class="w-16 h-16 mx-auto mb-4 text-text-disabled" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4"></path>
        </svg>
        <p class="text-text-tertiary text-sm">暂无历史记录</p>
      </div>

      <div v-else class="grid grid-cols-3 gap-3">
        <div
          v-for="record in filteredRecords"
          :key="record.id"
          class="group relative rounded overflow-hidden bg-bg-subtle hover:bg-bg-hover transition-all cursor-pointer"
        >
          <!-- 缩略图 -->
          <div class="relative aspect-video" @click="handlePreview(record)">
            <img
              :src="record.thumbnailUrl"
              :alt="`${getTypeLabel(record.type)} #${record.shotNo}`"
              class="w-full h-full object-cover"
            >

            <!-- 视频播放按钮 -->
            <div v-if="record.type === 'video'" class="absolute inset-0 bg-bg-subtle flex items-center justify-center">
              <svg class="w-12 h-12 text-text-secondary" fill="currentColor" viewBox="0 0 24 24">
                <path d="M8 5v14l11-7z"></path>
              </svg>
            </div>

            <!-- 过期标记 -->
            <div
              v-if="record.status === 'expired'"
              class="absolute top-2 right-2 px-2 py-1 bg-red-500/90 rounded text-text-primary text-xs font-medium"
            >
              已过期
            </div>

            <!-- 类型标签 -->
            <div class="absolute top-2 left-2 px-2 py-1 bg-gray-800 rounded text-white text-xs font-medium">
              {{ getTypeLabel(record.type) }}
            </div>

            <!-- 操作按钮（hover显示） -->
            <div class="absolute bottom-2 right-2 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button
                @click.stop="handleDownload(record)"
                class="p-1.5 bg-gray-800 rounded-lg hover:bg-gray-700 transition-colors"
                title="下载"
              >
                <svg class="w-4 h-4 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"></path>
                </svg>
              </button>
              <button
                @click.stop="handleDelete(record.id)"
                class="p-1.5 bg-red-500/80  rounded-lg hover:bg-red-500 transition-colors"
                title="删除"
              >
                <svg class="w-4 h-4 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                </svg>
              </button>
            </div>
          </div>

          <!-- 信息栏 -->
          <div class="px-3 py-2">
            <div class="flex items-center justify-between mb-1">
              <span class="text-text-secondary text-sm font-medium">分镜 #{{ record.shotNo }}</span>
              <span class="text-text-tertiary text-xs">{{ record.aspectRatio }}</span>
            </div>
            <p class="text-text-tertiary text-xs">{{ record.timestamp }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部提示 -->
    <div class="px-4 py-3 border-t border-border-default">
      <p class="text-[#FF6B9D] text-xs text-center">
        未被使用的生成记录仅保留7天，请及时下载文件
      </p>
    </div>
  </div>
</template>
