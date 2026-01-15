<script setup lang="ts">
// {{CODE-Cycle-Integration:
//   Task_ID: [#Phase6-Step4]
//   Timestamp: 2026-01-03T12:37:33+08:00
//   Phase: [D-Develop]
//   Context-Analysis: "Creating AssetCell component for displaying asset status (shot image/video). Supports 4 states: NONE, GENERATING, READY, FAILED. Uses Mochiani theme + Aether rounded."
//   Principle_Applied: "Pixel-Perfect-Mandate, Aether-Aesthetics, Mochiani-Dark-Theme"
// }}
// {{START_MODIFICATIONS}}

import { computed } from 'vue'
import type { AssetStatusVO } from '@/types/api'
import LoadingSpinner from '@/components/base/LoadingSpinner.vue'

interface Props {
  asset: AssetStatusVO
  label: string
  onRegenerate?: () => void
  onDelete?: () => void
  onClick?: () => void
}

const props = defineProps<Props>()

// Status configuration
const statusConfig = computed(() => {
  const configs = {
    NONE: {
      icon: '📷',
      bg: 'bg-bg-subtle',
      text: 'text-text-tertiary',
      label: '待生成',
      border: 'border-border-default',
    },
    GENERATING: {
      icon: '⏳',
      bg: 'bg-bg-subtle',
      text: 'text-text-secondary',
      label: '生成中',
      border: 'border-border-default',
      pulse: false,
      showSpinner: true,
    },
    READY: {
      icon: '✓',
      bg: 'bg-bg-subtle',
      text: 'text-green-600',
      label: '已完成',
      border: 'border-green-500',
    },
    FAILED: {
      icon: '✗',
      bg: 'bg-bg-subtle',
      text: 'text-red-600',
      label: '失败',
      border: 'border-red-500',
    },
  }

  return configs[props.asset.status] || configs.NONE
})

const canRegenerate = computed(() => {
  return props.asset.status === 'READY' || props.asset.status === 'FAILED'
})

const handleClick = () => {
  if (props.onClick) {
    props.onClick()
  } else if (props.asset.status === 'READY' && props.asset.currentUrl) {
    // TODO: Open image/video viewer or show version history
    console.log('View asset:', props.asset.currentUrl)
  } else if (canRegenerate.value && props.onRegenerate) {
    props.onRegenerate()
  }
}
</script>

<template>
  <div
    :class="[
      'group flex items-center justify-center h-16 rounded border-2 transition-all cursor-pointer relative',
      statusConfig.bg,
      statusConfig.border,
      'pulse' in statusConfig && statusConfig.pulse && 'animate-pulse',
      'hover:brightness-110',
    ]"
    :title="`${statusConfig.label} - ${asset.totalVersions} 个版本`"
  >
    <!-- READY: Show thumbnail -->
    <div v-if="asset.status === 'READY' && asset.currentUrl" class="relative w-full h-full p-1" @click="handleClick">
      <img
        :src="asset.currentUrl"
        :alt="label"
        class="w-full h-full object-cover rounded-lg"
      >
      <!-- 删除按钮（悬浮显示） -->
      <button
        v-if="onDelete"
        @click.stop="onDelete"
        class="absolute top-1 left-1 w-5 h-5 rounded bg-red-500/80 flex items-center justify-center hover:bg-red-500 transition-all opacity-0 group-hover:opacity-100 z-10"
        title="删除资产"
      >
        <svg class="w-3 h-3 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M6 18L18 6M6 6l12 12"></path>
        </svg>
      </button>
    </div>

    <!-- GENERATING: Show loading spinner -->
    <div v-else-if="asset.status === 'GENERATING'" class="flex flex-col items-center gap-2" @click="handleClick">
      <LoadingSpinner size="small" />
      <span :class="['text-[10px] font-medium', statusConfig.text]">
        {{ statusConfig.label }}
      </span>
      <span v-if="asset.totalVersions > 0" :class="['text-[10px]', statusConfig.text]">
        ({{ asset.totalVersions }} 版本)
      </span>
    </div>

    <!-- NONE / FAILED: Show status icon + text -->
    <div v-else class="flex flex-col items-center gap-1" @click="handleClick">
      <span :class="['text-2xl', statusConfig.text]">
        {{ statusConfig.icon }}
      </span>
      <span :class="['text-[10px] font-medium', statusConfig.text]">
        {{ statusConfig.label }}
      </span>
      <span v-if="asset.totalVersions > 0" :class="['text-[10px]', statusConfig.text]">
        ({{ asset.totalVersions }} 版本)
      </span>
    </div>
  </div>
</template>
