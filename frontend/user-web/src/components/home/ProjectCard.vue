<script setup lang="ts">
import { computed } from 'vue'
import type { ProjectVO } from '@/types/api'
import NeonTag from '@/components/base/NeonTag.vue'

interface Props {
  project: ProjectVO
}

const props = defineProps<Props>()

const emit = defineEmits<{
  click: []
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

const coverImage = computed(() => {
  return props.project.coverUrl || 'https://via.placeholder.com/400x225/1E2025/00FFCC?text=No+Cover'
})

const projectTitle = computed(() => {
  return props.project.title || props.project.name
})
</script>

<template>
  <div
    class="group card cursor-pointer"
    @click="$emit('click')"
  >
    <!-- Cover image -->
    <div class="relative aspect-video bg-bg-subtle overflow-hidden">
      <img
        :src="coverImage"
        :alt="projectTitle"
        class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
      >

      <!-- Overlay on hover -->
      <div class="absolute inset-0 bg-gradient-to-t from-gray-900/80 to-transparent opacity-0 group-hover:opacity-100 transition-opacity">
        <div class="absolute bottom-3 left-3 right-3 flex gap-2">
          <button
            class="flex-1 btn btn-primary text-xs"
            @click.stop="$emit('click')"
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

    <!-- Project info -->
    <div class="p-4">
      <h3 class="text-sm font-medium text-text-primary mb-2 truncate">{{ projectTitle }}</h3>
      <div class="flex items-center justify-between text-xs">
        <span class="text-text-tertiary">{{ formatDate(project.updatedAt) }}</span>
        <span
          v-if="project.shotCount"
          class="px-2 py-1 rounded bg-bg-subtle text-text-secondary font-medium"
        >
          {{ project.shotCount }}
        </span>
      </div>
    </div>
  </div>
</template>
