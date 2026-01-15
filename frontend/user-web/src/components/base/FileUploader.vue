<script setup lang="ts">
import { ref, computed } from 'vue'

interface Props {
  accept?: string
  maxSize?: number // in MB
  multiple?: boolean
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  accept: 'image/*',
  maxSize: 10,
  multiple: false,
  disabled: false,
})

const emit = defineEmits<{
  upload: [files: File[]]
  error: [message: string]
}>()

const isDragging = ref(false)
const fileInputRef = ref<HTMLInputElement>()

const acceptText = computed(() => {
  if (props.accept.includes('image')) return '图片'
  if (props.accept.includes('video')) return '视频'
  return '文件'
})

const handleDragOver = (e: DragEvent) => {
  e.preventDefault()
  if (!props.disabled) {
    isDragging.value = true
  }
}

const handleDragLeave = () => {
  isDragging.value = false
}

const handleDrop = (e: DragEvent) => {
  e.preventDefault()
  isDragging.value = false

  if (props.disabled) return

  const files = Array.from(e.dataTransfer?.files || [])
  validateAndEmit(files)
}

const handleFileSelect = (e: Event) => {
  const target = e.target as HTMLInputElement
  const files = Array.from(target.files || [])
  validateAndEmit(files)

  // Reset input value to allow selecting the same file again
  target.value = ''
}

const validateAndEmit = (files: File[]) => {
  if (files.length === 0) return

  // Check file count
  if (!props.multiple && files.length > 1) {
    emit('error', '只能上传一个文件')
    return
  }

  // Check file size
  const oversizedFiles = files.filter(f => f.size > props.maxSize * 1024 * 1024)
  if (oversizedFiles.length > 0) {
    emit('error', `文件大小不能超过 ${props.maxSize}MB`)
    return
  }

  // Check file type
  if (props.accept !== '*') {
    const acceptTypes = props.accept.split(',').map(t => t.trim())
    const invalidFiles = files.filter(f => {
      return !acceptTypes.some(accept => {
        if (accept.endsWith('/*')) {
          const type = accept.replace('/*', '')
          return f.type.startsWith(type)
        }
        return f.type === accept || f.name.endsWith(accept.replace('*', ''))
      })
    })

    if (invalidFiles.length > 0) {
      emit('error', `不支持的${acceptText.value}格式`)
      return
    }
  }

  emit('upload', files)
}

const triggerFileSelect = () => {
  if (!props.disabled) {
    fileInputRef.value?.click()
  }
}
</script>

<template>
  <div
    :class="[
      'relative border-2 border-dashed rounded p-8 transition-all cursor-pointer',
      isDragging
        ? 'border-gray-900 bg-bg-subtle'
        : 'border-border-default bg-bg-subtle hover:border-gray-900/50 hover:bg-bg-hover',
      disabled && 'opacity-50 cursor-not-allowed'
    ]"
    @dragover="handleDragOver"
    @dragleave="handleDragLeave"
    @drop="handleDrop"
    @click="triggerFileSelect"
  >
    <input
      ref="fileInputRef"
      type="file"
      :accept="accept"
      :multiple="multiple"
      :disabled="disabled"
      class="hidden"
      @change="handleFileSelect"
    >

    <div class="flex flex-col items-center gap-3 text-center">
      <!-- Upload icon -->
      <div class="w-12 h-12 rounded bg-bg-subtle flex items-center justify-center">
        <svg class="w-6 h-6 text-text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
        </svg>
      </div>

      <div>
        <p class="text-text-secondary text-sm mb-1">
          <span class="text-text-primary">点击上传</span> 或拖拽{{ acceptText }}到此处
        </p>
        <p class="text-text-tertiary text-xs">
          支持格式：{{ accept }} | 最大 {{ maxSize }}MB
        </p>
      </div>
    </div>

    <slot />
  </div>
</template>
