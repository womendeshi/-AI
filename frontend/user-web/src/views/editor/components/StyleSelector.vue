<script setup lang="ts">
// {{CODE-Cycle-Integration:
//   Task_ID: [#T-EDITOR-REDESIGN-Step1]
//   Timestamp: 2026-01-04T15:05:00+08:00
//   Phase: [D-Develop]
//   Context-Analysis: "Created StyleSelector component for selecting style presets. Features dropdown menu with thumbnails, fetches styles from API, updates project on selection."
//   Principle_Applied: "Pixel-Perfect-Mandate, Aether-Aesthetics, KISS"
// }}
// {{START_MODIFICATIONS}}

import { ref, computed, onMounted } from 'vue'
import { useProjectStore } from '@/stores/project'
import { styleApi } from '@/api/style'
import type { StylePresetVO } from '@/types/api'

const props = defineProps<{
  projectId: number
}>()

const projectStore = useProjectStore()

const isOpen = ref(false)
const stylePresets = ref<StylePresetVO[]>([])
const loading = ref(false)

const currentProject = computed(() => projectStore.currentProject)
const selectedStyle = computed(() => {
  const styleCode = currentProject.value?.styleCode
  return stylePresets.value.find(s => s.code === styleCode) || null
})

const selectedStyleName = computed(() => selectedStyle.value?.name || '未知风格')

const fetchStyles = async () => {
  try {
    loading.value = true
    stylePresets.value = await styleApi.getStylePresets()
    console.log('[StyleSelector] Loaded style presets:', stylePresets.value.length)
  } catch (error) {
    console.error('[StyleSelector] Failed to fetch style presets:', error)
    window.$message?.error('加载风格列表失败')
  } finally {
    loading.value = false
  }
}

const selectStyle = async (style: StylePresetVO) => {
  if (style.code === currentProject.value?.styleCode) {
    isOpen.value = false
    return
  }

  try {
    await projectStore.updateProject(props.projectId, { styleCode: style.code })
    // 重新获取完整项目数据以确保所有字段都是最新的
    await projectStore.fetchProjectDetail(props.projectId)
    window.$message?.success(`风格已切换为「${style.name}」`)
    isOpen.value = false
  } catch (error: any) {
    console.error('[StyleSelector] Failed to update style:', error)
    window.$message?.error(error.message || '切换风格失败')
  }
}

const toggleDropdown = () => {
  isOpen.value = !isOpen.value
  if (isOpen.value && stylePresets.value.length === 0) {
    fetchStyles()
  }
}

// Click outside to close
const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (!target.closest('.style-selector-wrapper')) {
    isOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

// {{END_MODIFICATIONS}}
</script>

<template>
  <div class="relative style-selector-wrapper">
    <!-- Selector Button -->
    <button
      class="flex items-center gap-2 text-sm text-text-tertiary border border-border-default rounded px-3 py-1.5 hover:bg-bg-subtle transition-colors"
      @click.stop="toggleDropdown"
    >
      <span>风格</span>
      <span class="font-semibold text-text-primary max-w-[120px] truncate">{{ selectedStyleName }}</span>
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        :class="['transition-transform', isOpen ? 'rotate-180' : '']"
      >
        <path d="m6 9 6 6 6-6"></path>
      </svg>
    </button>

    <!-- Dropdown Menu -->
    <Transition
      enter-active-class="transition-all duration-200 ease-out"
      enter-from-class="opacity-0 scale-95 -translate-y-2"
      enter-to-class="opacity-100 scale-100 translate-y-0"
      leave-active-class="transition-all duration-150 ease-in"
      leave-from-class="opacity-100 scale-100 translate-y-0"
      leave-to-class="opacity-0 scale-95 -translate-y-2"
    >
      <div
        v-if="isOpen"
        class="absolute top-full left-0 mt-2 w-[280px] bg-bg-elevated border border-border-default rounded shadow-2xl overflow-hidden z-50"
        @click.stop
      >
        <!-- Loading State -->
        <div v-if="loading" class="p-6 text-center">
          <div class="animate-spin rounded h-8 w-8 border-b-2 border-gray-900 mx-auto mb-2"></div>
          <p class="text-text-tertiary text-sm">加载中...</p>
        </div>

        <!-- Style List -->
        <div v-else-if="stylePresets.length > 0" class="max-h-[400px] overflow-y-auto p-2">
          <button
            v-for="style in stylePresets"
            :key="style.id"
            class="w-full flex items-center gap-3 p-2.5 rounded hover:bg-bg-subtle transition-colors text-left"
            :class="[
              style.code === currentProject?.styleCode
                ? 'bg-bg-hover ring-1 ring-[#00FFCC]'
                : ''
            ]"
            @click="selectStyle(style)"
          >
            <!-- Thumbnail -->
            <div class="w-12 h-12 rounded-lg bg-bg-subtle flex-shrink-0 overflow-hidden">
              <img
                v-if="style.thumbnailUrl"
                :src="style.thumbnailUrl"
                :alt="style.name"
                class="w-full h-full object-cover"
              >
              <div v-else class="w-full h-full flex items-center justify-center">
                <svg class="w-6 h-6 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01"></path>
                </svg>
              </div>
            </div>

            <!-- Info -->
            <div class="flex-1 min-w-0">
              <div class="font-medium text-text-primary text-sm truncate">{{ style.name }}</div>
              <div class="text-xs text-text-tertiary truncate">{{ style.code }}</div>
            </div>

            <!-- Selected Indicator -->
            <div
              v-if="style.code === currentProject?.styleCode"
              class="flex-shrink-0"
            >
              <svg class="w-5 h-5 text-text-primary" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"></path>
              </svg>
            </div>
          </button>
        </div>

        <!-- Empty State -->
        <div v-else class="p-6 text-center">
          <p class="text-text-tertiary text-sm">暂无风格预设</p>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
/* Custom scrollbar for dropdown */
.max-h-\[400px\]::-webkit-scrollbar {
  width: 6px;
}

.max-h-\[400px\]::-webkit-scrollbar-track {
  background: transparent;
}

.max-h-\[400px\]::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
}

.max-h-\[400px\]::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.2);
}
</style>
