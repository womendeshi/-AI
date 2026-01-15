<script setup lang="ts">
// {{CODE-Cycle-Integration:
//   Task_ID: [#问题1]
//   Timestamp: 2026-01-03T08:00:00+08:00
//   Phase: [D-Develop]
//   Context-Analysis: "Creating FolderCard component for homepage folder display. Following ProjectCard pattern with folder-specific design."
//   Principle_Applied: "Pixel-Perfect-Mandate, Aether-Aesthetics, DRY"
// }}
// {{START_MODIFICATIONS}}

import { computed } from 'vue'
import type { FolderVO } from '@/types/api'
import NeonTag from '@/components/base/NeonTag.vue'

interface Props {
  folder: FolderVO
}

const props = defineProps<Props>()

const emit = defineEmits<{
  click: []
  edit: []
  delete: []
}>()

// Format date to relative time
const formatDate = (date: string) => {
  const now = new Date()
  const then = new Date(date)
  const diffMs = now.getTime() - then.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  if (diffHours < 24) return `${diffHours}小时前`
  if (diffDays < 7) return `${diffDays}天前`

  return then.toLocaleDateString('zh-CN')
}

const folderImage = computed(() => {
  return props.folder.coverUrl || null
})

const projectCount = computed(() => {
  return props.folder.projectCount || 0
})
</script>

<template>
  <div
    class="group card cursor-pointer"
    @click="$emit('click')"
  >
    <!-- Folder visual -->
    <div class="relative aspect-video bg-bg-subtle rounded-t-lg overflow-hidden flex items-center justify-center">
      <img
        v-if="folderImage"
        :src="folderImage"
        :alt="folder.name"
        class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
      >

      <div v-else class="flex flex-col items-center gap-3">
        <svg class="w-16 h-16 text-text-tertiary group-hover:text-text-secondary transition-colors" fill="currentColor" viewBox="0 0 24 24">
          <path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" />
        </svg>
        <span class="text-sm text-text-tertiary font-medium">{{ projectCount }} 个项目</span>
      </div>

      <!-- Overlay on hover -->
      <div class="absolute inset-0 bg-gradient-to-t from-black/80 to-transparent opacity-0 group-hover:opacity-100 transition-opacity">
        <div class="absolute bottom-3 left-3 right-3 flex gap-2">
          <button
            class="flex-1 btn btn-primary text-xs"
            @click.stop="$emit('click')"
          >
            打开
          </button>
          <button
            class="btn btn-secondary text-xs"
            @click.stop="$emit('edit')"
          >
            编辑
          </button>
          <button
            class="btn btn-secondary text-xs"
            @click.stop="$emit('delete')"
          >
            删除
          </button>
        </div>
      </div>
    </div>

    <!-- Folder info -->
    <div class="p-4">
      <h3 class="text-sm font-medium text-text-primary mb-2 truncate">{{ folder.name }}</h3>
      <div class="flex items-center justify-between text-xs">
        <span class="text-text-tertiary">{{ formatDate(folder.createdAt) }}</span>
        <span class="px-2 py-1 rounded bg-bg-subtle text-text-secondary font-medium">
          {{ projectCount }}
        </span>
      </div>
    </div>
  </div>
</template>
