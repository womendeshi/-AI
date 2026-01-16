<script setup lang="ts">
// {{CODE-Cycle-Integration:
//   Task_ID: [#Export-Module]
//   Timestamp: 2026-01-04
//   Phase: [D-Develop]
//   Context-Analysis: "Creating ExportProgressModal component for displaying export progress and triggering download when complete."
//   Principle_Applied: "Pixel-Perfect-Mandate, Aether-Aesthetics-User-Approved, DRY"
// }}
// {{START_MODIFICATIONS}}

import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { pollJobStatus } from '@/api/job'
import { exportApi } from '@/api/export'
import type { JobVO } from '@/types/api'

interface Props {
  jobId: number
}

interface Emits {
  (e: 'close'): void
  (e: 'complete'): void
  (e: 'failed', error: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// State
const job = ref<JobVO | null>(null)
const polling = ref(false)
const error = ref<string | null>(null)

// Computed
const progressPercentage = computed(() => {
  if (!job.value) return 0
  return job.value.progress || 0
})

const statusText = computed(() => {
  if (!job.value) return '初始化中...'
  switch (job.value.status) {
    case 'PENDING':
      return '等待处理中...'
    case 'RUNNING':
    case 'GENERATING':
      return '正在导出...'
    case 'COMPLETED':
    case 'SUCCEEDED':
      return '导出完成！'
    case 'FAILED':
      return '导出失败'
    default:
      return '处理中...'
  }
})

const canDownload = computed(() => {
  return (job.value?.status === 'COMPLETED' || job.value?.status === 'SUCCEEDED') && !error.value
})

// Start polling
onMounted(async () => {
  polling.value = true
  try {
    const result = await pollJobStatus(
      props.jobId,
      (updatedJob) => {
        job.value = updatedJob
        console.log('[ExportProgressModal] Job progress:', updatedJob.progress, '%')
      },
      3000 // Poll every 3 seconds
    )

    job.value = result
    polling.value = false
    console.log('[ExportProgressModal] Export completed:', result)
    emit('complete')
  } catch (err: any) {
    polling.value = false
    error.value = err.message || '导出失败'
    console.error('[ExportProgressModal] Export failed:', err)
    emit('failed', error.value)
  }
})

onBeforeUnmount(() => {
  polling.value = false
})

// Download handler
const handleDownload = async () => {
  if (!canDownload.value) return

  try {
    await exportApi.downloadExportFile(props.jobId)
    window.$message?.success('开始下载导出文件')

    // Close modal after a short delay
    setTimeout(() => {
      emit('close')
    }, 1500)
  } catch (err: any) {
    window.$message?.error('下载失败: ' + (err.message || '未知错误'))
  }
}

const handleClose = () => {
  emit('close')
}
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
    @click.self="handleClose"
  >
    <div
      class="bg-[#1a1a1a] border border-[#2a2a2a] rounded-2xl w-[480px] flex flex-col shadow-2xl"
      @click.stop
    >
      <!-- Modal Header -->
      <div class="flex items-center justify-between px-6 py-5 border-b border-[#2a2a2a]">
        <h2 class="text-xl font-bold text-white">导出进度</h2>
        <button
          v-if="!polling"
          class="p-2 rounded-lg text-text-tertiary hover:bg-bg-hover hover:text-white transition-all"
          @click="handleClose"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- Modal Content -->
      <div class="px-8 py-10 flex flex-col items-center">
        <!-- Status Icon -->
        <div v-if="polling" class="relative mb-6">
          <div class="w-28 h-28 rounded-2xl bg-gradient-to-br from-purple-500/20 to-blue-500/20 flex items-center justify-center border-2 border-purple-500/30">
            <svg class="w-14 h-14 text-purple-400 animate-pulse" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
            </svg>
          </div>
        </div>

        <div v-else-if="canDownload" class="relative mb-6">
          <div class="w-28 h-28 rounded-2xl bg-gradient-to-br from-green-500/20 to-emerald-500/20 flex items-center justify-center border-2 border-green-500/30">
            <svg class="w-14 h-14 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          </div>
        </div>

        <div v-else-if="error" class="relative mb-6">
          <div class="w-28 h-28 rounded-2xl bg-gradient-to-br from-red-500/20 to-pink-500/20 flex items-center justify-center border-2 border-red-500/30">
            <svg class="w-14 h-14 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </div>
        </div>

        <!-- Status Text -->
        <div class="text-center mb-6">
          <p class="text-white font-semibold text-2xl mb-3">{{ statusText }}</p>
          <p v-if="job" class="text-text-tertiary text-sm">
            <template v-if="job.totalItems > 0">
              已完成 {{ job.doneItems }} / {{ job.totalItems }} 项
            </template>
            <template v-else>
              处理中...
            </template>
          </p>
          <p v-if="error" class="text-red-400 text-sm mt-2 px-4">{{ error }}</p>
        </div>

        <!-- Progress Bar -->
        <div v-if="polling" class="w-full mb-6">
          <div class="w-full h-2.5 bg-[#2a2a2a] rounded-full overflow-hidden">
            <div
              class="h-full bg-gradient-to-r from-purple-500 to-blue-500 transition-all duration-300 rounded-full"
              :style="{ width: `${progressPercentage}%` }"
            ></div>
          </div>
          <p class="text-purple-400 text-sm font-medium text-center mt-3">{{ progressPercentage }}%</p>
        </div>

        <!-- Info Text -->
        <div v-if="polling" class="text-text-tertiary text-xs text-center mb-2">
          导出正在后台处理中,请稍候...
        </div>

        <!-- Download Button -->
        <button
          v-if="canDownload"
          class="w-full px-6 py-3.5 rounded-xl bg-[#8B5CF6] hover:bg-[#A78BFA] text-white font-semibold transition-all shadow-[0_0_20px_rgba(139,92,246,0.4)] hover:shadow-[0_0_30px_rgba(139,92,246,0.6)] flex items-center justify-center gap-3"
          @click="handleDownload"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
          下载导出文件
        </button>

        <!-- Retry Button -->
        <button
          v-if="error"
          class="w-full px-6 py-3 rounded-xl border-2 border-[#2a2a2a] text-text-secondary hover:bg-bg-hover hover:text-white hover:border-[#3a3a3a] transition-all font-medium"
          @click="handleClose"
        >
          关闭
        </button>
      </div>
    </div>
  </div>
</template>

// {{END_MODIFICATIONS}}
