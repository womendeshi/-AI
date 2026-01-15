<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  show: boolean
  loading?: boolean
  folderId?: number | null
}>()

const emit = defineEmits<{
  close: []
  confirm: [data: {
    name: string
    folderId?: number
    rawText: string
  }]
}>()

const projectName = ref('')
const storyText = ref('')

// Char count
const charCount = computed(() => storyText.value.length)
const maxChars = 5000

const handleConfirm = () => {
  if (!projectName.value.trim()) {
    alert('请输入项目名称')
    return
  }

  emit('confirm', {
    name: projectName.value.trim(),
    folderId: props.folderId || undefined,
    rawText: storyText.value,
  })
}

const handleClose = () => {
  // Reset form
  projectName.value = ''
  storyText.value = ''
  emit('close')
}
</script>

<template>
  <!-- Modal Overlay -->
  <Transition
    enter-active-class="transition-opacity duration-200"
    leave-active-class="transition-opacity duration-200"
    enter-from-class="opacity-0"
    leave-to-class="opacity-0"
  >
    <div
      v-if="show"
      class="fixed inset-0 flex items-center justify-center z-50 p-4 pointer-events-none"
      @click.self="handleClose"
    >
      <!-- Modal Container -->
      <div class="bg-bg-elevated w-[500px] max-h-[90vh] rounded-lg flex flex-col shadow-2xl border border-border-default pointer-events-auto">
        <!-- Header -->
        <div class="relative flex items-center justify-center py-4 border-b border-border-subtle">
          <h2 class="text-[16px] font-medium text-text-primary">创建作品</h2>
          <button
            class="absolute right-5 top-4 text-text-tertiary hover:text-text-primary transition-colors"
            @click="handleClose"
          >
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>

        <!-- Body -->
        <div class="flex flex-col gap-6 p-8">
          <!-- Project Name -->
          <div>
            <div class="text-[14px] text-text-tertiary mb-2">项目名称</div>
            <input
              v-model="projectName"
              type="text"
              placeholder="请输入项目名称"
              class="w-full bg-bg-subtle border border-border-default rounded-lg px-3 py-2.5 text-text-primary text-[14px] outline-none placeholder-text-tertiary focus:border-[#8B5CF6] focus:ring-1 focus:ring-[#8B5CF6]"
            />
          </div>

          <!-- Story Script -->
          <div class="flex flex-col">
            <div class="text-[14px] text-text-tertiary mb-2">故事文案（选填）</div>
            <div class="bg-bg-subtle rounded-lg p-3 flex flex-col">
              <textarea
                v-model="storyText"
                placeholder="输入你的故事文案..."
                class="h-32 bg-transparent border-none text-text-primary text-[14px] leading-[1.5] resize-none outline-none placeholder-text-tertiary"
                :maxlength="maxChars"
              ></textarea>
              <div class="flex items-center justify-end mt-2 pt-2 border-t border-border-subtle">
                <span class="text-[12px] text-text-tertiary">{{ charCount }} / {{ maxChars }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="flex items-center justify-center gap-4 px-8 pb-6">
          <button
            class="px-8 py-2.5 bg-[#8B5CF6] text-white text-[14px] font-medium rounded-lg hover:bg-[#A78BFA] transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="loading"
            @click="handleConfirm"
          >
            {{ loading ? '创建中...' : '创建' }}
          </button>
          <button
            class="px-8 py-2.5 bg-transparent border border-border-strong text-text-secondary text-[14px] font-medium rounded-lg hover:border-text-tertiary hover:bg-bg-hover transition-colors"
            @click="handleClose"
          >
            取消
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
/* Custom scrollbar for style grid */
.overflow-y-auto::-webkit-scrollbar {
  width: 4px;
}
.overflow-y-auto::-webkit-scrollbar-track {
  background: transparent;
}
.overflow-y-auto::-webkit-scrollbar-thumb {
  background: #3f4148;
  border-radius: 2px;
}
.overflow-y-auto::-webkit-scrollbar-thumb:hover {
  background: #555;
}
</style>
