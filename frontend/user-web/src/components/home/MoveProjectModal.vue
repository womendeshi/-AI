<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useProjectStore } from '@/stores/project'
import type { ProjectVO } from '@/types/api'
import GlassCard from '@/components/base/GlassCard.vue'
import PillButton from '@/components/base/PillButton.vue'

interface Props {
  show: boolean
  project: ProjectVO | null
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
})

const emit = defineEmits<{
  close: []
  confirm: [folderId: number | null]
}>()

const projectStore = useProjectStore()
const selectedFolderId = ref<number | null>(null)

watch(() => props.show, (show) => {
  if (show && props.project) {
    selectedFolderId.value = props.project.folderId
  }
})

const folderOptions = computed(() => {
  return [
    { id: null, name: '未分类' },
    ...projectStore.folders,
  ]
})

const handleConfirm = () => {
  emit('confirm', selectedFolderId.value)
}

const handleCancel = () => {
  selectedFolderId.value = null
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-200"
      leave-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div
        v-if="show"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 pointer-events-none"
        @click.self="handleCancel"
      >
        <Transition
          enter-active-class="transition-all duration-200"
          leave-active-class="transition-all duration-200"
          enter-from-class="opacity-0 scale-95"
          leave-to-class="opacity-0 scale-95"
        >
          <GlassCard
            v-if="show"
            padding="p-6"
            className="w-full max-w-md pointer-events-auto"
          >
            <h2 class="text-xl font-semibold text-text-primary mb-4">移动项目</h2>

            <div class="mb-2">
              <p class="text-text-tertiary text-sm mb-4">
                将 <span class="text-text-primary">{{ project?.name }}</span> 移动到：
              </p>

              <div class="space-y-2">
                <label
                  v-for="folder in folderOptions"
                  :key="folder.id || 'uncategorized'"
                  :class="[
                    'flex items-center gap-3 px-4 py-3 rounded border cursor-pointer transition-all',
                    selectedFolderId === folder.id
                      ? 'bg-bg-subtle border-gray-900'
                      : 'bg-bg-subtle border-border-default hover:bg-bg-hover hover:border-border-default'
                  ]"
                >
                  <input
                    v-model="selectedFolderId"
                    type="radio"
                    :value="folder.id"
                    class="w-4 h-4 text-text-primary bg-transparent border-border-default focus:ring-mochi-cyan focus:ring-2"
                  >
                  <div class="flex items-center gap-2 flex-1">
                    <svg
                      v-if="folder.id !== null"
                      class="w-5 h-5 text-text-primary"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
                    </svg>
                    <span class="text-text-primary text-sm">{{ folder.name }}</span>
                  </div>
                </label>
              </div>
            </div>

            <div class="flex gap-3 justify-end mt-6">
              <PillButton
                label="取消"
                variant="secondary"
                :disabled="loading"
                @click="handleCancel"
              />
              <button
                :disabled="loading"
                :class="[
                  'px-6 py-2 rounded text-sm font-medium transition-all',
                  !loading
                    ? 'bg-bg-subtle text-text-primary hover:bg-bg-hover'
                    : 'bg-bg-hover text-white/30 cursor-not-allowed'
                ]"
                @click="handleConfirm"
              >
                <span v-if="loading">移动中...</span>
                <span v-else>确认移动</span>
              </button>
            </div>
          </GlassCard>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
