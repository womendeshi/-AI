<script setup lang="ts">
// {{CODE-Cycle-Integration:
//   Task_ID: [#Export-Module]
//   Timestamp: 2026-01-04
//   Phase: [D-Develop]
//   Context-Analysis: "Creating ExportModal component for selecting export options (content types and version mode)."
//   Principle_Applied: "Pixel-Perfect-Mandate, Aether-Aesthetics-User-Approved, DRY"
// }}
// {{START_MODIFICATIONS}}

import { ref, computed } from 'vue'
import type { ExportRequest } from '@/types/api'

interface Emits {
  (e: 'close'): void
  (e: 'confirm', request: ExportRequest): void
}

const emit = defineEmits<Emits>()

// Export options
const exportCharacters = ref(true)
const exportScenes = ref(true)
const exportShotImages = ref(true)
const exportVideos = ref(true)
const mode = ref<'CURRENT' | 'ALL'>('CURRENT')

// Validation
const hasSelection = computed(() => {
  return exportCharacters.value || exportScenes.value || exportShotImages.value || exportVideos.value
})

const handleConfirm = () => {
  if (!hasSelection.value) {
    window.$message?.warning('请至少选择一项导出内容')
    return
  }

  emit('confirm', {
    exportCharacters: exportCharacters.value,
    exportScenes: exportScenes.value,
    exportShotImages: exportShotImages.value,
    exportVideos: exportVideos.value,
    mode: mode.value,
  })
}

const handleClose = () => {
  emit('close')
}
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center pointer-events-none"
    @click.self="handleClose"
  >
    <div
      class="bg-bg-elevated border border-border-default rounded w-[500px] flex flex-col shadow-2xl pointer-events-auto"
      @click.stop
    >
      <!-- Modal Header -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-border-default">
        <h2 class="text-lg font-bold text-text-primary">导出项目</h2>
        <button
          class="p-2 rounded-lg text-text-tertiary hover:bg-bg-subtle hover:text-text-primary transition-colors"
          @click="handleClose"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- Modal Content -->
      <div class="p-6 space-y-6">
        <!-- Export Content Selection -->
        <div>
          <h3 class="text-text-primary font-medium mb-3">选择导出内容</h3>
          <div class="space-y-2">
            <!-- Characters -->
            <label class="flex items-center gap-3 p-3 rounded bg-bg-subtle hover:bg-bg-hover transition-colors cursor-pointer">
              <input
                v-model="exportCharacters"
                type="checkbox"
                class="w-5 h-5 rounded-lg bg-bg-hover border-border-default checked:bg-gray-900 checked:border-gray-900 text-black focus:ring-2 focus:ring-[#00FFCC] focus:ring-offset-0"
              >
              <div class="flex-1">
                <div class="text-text-primary text-sm font-medium">角色画像</div>
                <div class="text-text-tertiary text-xs">导出所有角色的生成图片</div>
              </div>
            </label>

            <!-- Scenes -->
            <label class="flex items-center gap-3 p-3 rounded bg-bg-subtle hover:bg-bg-hover transition-colors cursor-pointer">
              <input
                v-model="exportScenes"
                type="checkbox"
                class="w-5 h-5 rounded-lg bg-bg-hover border-border-default checked:bg-gray-900 checked:border-gray-900 text-black focus:ring-2 focus:ring-[#00FFCC] focus:ring-offset-0"
              >
              <div class="flex-1">
                <div class="text-text-primary text-sm font-medium">场景画像</div>
                <div class="text-text-tertiary text-xs">导出所有场景的生成图片</div>
              </div>
            </label>

            <!-- Shot Images -->
            <label class="flex items-center gap-3 p-3 rounded bg-bg-subtle hover:bg-bg-hover transition-colors cursor-pointer">
              <input
                v-model="exportShotImages"
                type="checkbox"
                class="w-5 h-5 rounded-lg bg-bg-hover border-border-default checked:bg-gray-900 checked:border-gray-900 text-black focus:ring-2 focus:ring-[#00FFCC] focus:ring-offset-0"
              >
              <div class="flex-1">
                <div class="text-text-primary text-sm font-medium">分镜图</div>
                <div class="text-text-tertiary text-xs">导出所有分镜的静态图片</div>
              </div>
            </label>

            <!-- Videos -->
            <label class="flex items-center gap-3 p-3 rounded bg-bg-subtle hover:bg-bg-hover transition-colors cursor-pointer">
              <input
                v-model="exportVideos"
                type="checkbox"
                class="w-5 h-5 rounded-lg bg-bg-hover border-border-default checked:bg-gray-900 checked:border-gray-900 text-black focus:ring-2 focus:ring-[#00FFCC] focus:ring-offset-0"
              >
              <div class="flex-1">
                <div class="text-text-primary text-sm font-medium">分镜视频</div>
                <div class="text-text-tertiary text-xs">导出所有分镜的生成视频</div>
              </div>
            </label>
          </div>
        </div>

        <!-- Version Mode Selection -->
        <div>
          <h3 class="text-text-primary font-medium mb-3">版本选择</h3>
          <div class="space-y-2">
            <!-- Current Version -->
            <label class="flex items-center gap-3 p-3 rounded bg-bg-subtle hover:bg-bg-hover transition-colors cursor-pointer">
              <input
                v-model="mode"
                type="radio"
                value="CURRENT"
                class="w-5 h-5 rounded bg-bg-hover border-border-default checked:bg-gray-900 checked:border-gray-900 text-black focus:ring-2 focus:ring-[#00FFCC] focus:ring-offset-0"
              >
              <div class="flex-1">
                <div class="text-text-primary text-sm font-medium">仅当前版本</div>
                <div class="text-text-tertiary text-xs">只导出每个资产的当前活跃版本</div>
              </div>
            </label>

            <!-- All Versions -->
            <label class="flex items-center gap-3 p-3 rounded bg-bg-subtle hover:bg-bg-hover transition-colors cursor-pointer">
              <input
                v-model="mode"
                type="radio"
                value="ALL"
                class="w-5 h-5 rounded bg-bg-hover border-border-default checked:bg-gray-900 checked:border-gray-900 text-black focus:ring-2 focus:ring-[#00FFCC] focus:ring-offset-0"
              >
              <div class="flex-1">
                <div class="text-text-primary text-sm font-medium">包含所有版本</div>
                <div class="text-text-tertiary text-xs">导出所有历史版本(文件较大)</div>
              </div>
            </label>
          </div>
        </div>

        <!-- Info Notice -->
        <div class="bg-blue-500/10 border border-border-default rounded p-4">
          <div class="flex items-start gap-3">
            <svg class="w-5 h-5 text-text-secondary mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <div class="text-sm text-text-secondary">
              导出将在后台异步处理,完成后自动下载ZIP压缩包。文件按类别组织: 01-角色、02-场景、03-分镜、04-视频。
            </div>
          </div>
        </div>
      </div>

      <!-- Modal Footer -->
      <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-border-default">
        <button
          class="px-6 py-2 rounded border border-border-default text-text-secondary hover:bg-bg-subtle transition-colors text-sm"
          @click="handleClose"
        >
          取消
        </button>
        <button
          class="px-6 py-2 rounded bg-gray-900 text-white font-medium hover:bg-gray-700 transition-colors text-sm disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!hasSelection"
          @click="handleConfirm"
        >
          开始导出
        </button>
      </div>
    </div>
  </div>
</template>

// {{END_MODIFICATIONS}}
