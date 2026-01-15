<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToolboxStore } from '@/stores/toolbox'
import { getModelAspectRatios, getVideoDurations } from '@/constants/toolboxModels'
import CustomSelect from '@/components/base/CustomSelect.vue'
import { uploadApi } from '@/api/upload'

const toolboxStore = useToolboxStore()

// 类型选项 (视频功能暂时禁用)
const typeOptions = [
  { label: '文字', value: 'TEXT' },
  { label: '图片', value: 'IMAGE' },
  // { label: '视频', value: 'VIDEO' }, // 暂时禁用
]

// 当前类型的模型列表
const modelOptions = computed(() => {
  const type = toolboxStore.currentInput.type
  return toolboxStore.models[type].map((model) => ({
    label: model.name,
    value: model.code,
  }))
})

// 当前模型的比例列表
const aspectRatioOptions = computed(() => {
  const type = toolboxStore.currentInput.type
  const model = toolboxStore.currentInput.model
  if (type === 'TEXT') return []

  const ratios = getModelAspectRatios(type, model)
  return ratios.map((ratio) => ({ label: ratio, value: ratio }))
})

// 视频时长选项
const durationOptions = computed(() => {
  if (toolboxStore.currentInput.type !== 'VIDEO') return []

  const model = toolboxStore.currentInput.model
  const durations = getVideoDurations(model)
  return durations.map((duration) => ({ label: `${duration}秒`, value: duration }))
})

// 是否可以发送
const canSend = computed(() => {
  const hasPrompt = toolboxStore.currentInput.prompt.trim().length > 0
  const notGenerating = !toolboxStore.isGenerating
  console.log('[ChatInput] canSend check:', {
    hasPrompt,
    notGenerating,
    isGenerating: toolboxStore.isGenerating,
    loading: toolboxStore.loading,
    pollingJobId: toolboxStore.pollingJobId,
    promptLength: toolboxStore.currentInput.prompt.length,
  })
  return hasPrompt && notGenerating
})

// 处理类型切换
const handleTypeChange = (type: 'TEXT' | 'IMAGE' | 'VIDEO') => {
  toolboxStore.updateInputType(type)
}

// 参考图片上传
const referenceImageUrl = ref<string>('')
const uploading = ref(false)

const handleImageUpload = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    window.$message?.error('请上传图片文件')
    return
  }

  // 验证文件大小 (50MB)
  if (file.size > 50 * 1024 * 1024) {
    window.$message?.error('图片大小不能超过50MB')
    return
  }

  try {
    uploading.value = true
    const result = await uploadApi.upload(file, 'image')
    referenceImageUrl.value = result.url
    window.$message?.success('图片上传成功')
  } catch (error: any) {
    window.$message?.error(error.message || '图片上传失败')
  } finally {
    uploading.value = false
    // 清空input以允许重新上传同一文件
    input.value = ''
  }
}

const removeReferenceImage = () => {
  referenceImageUrl.value = ''
}

// 处理发送
const handleSend = async () => {
  if (!canSend.value) return

  // 视频生成需要参考图片
  if (toolboxStore.currentInput.type === 'VIDEO' && !referenceImageUrl.value) {
    window.$message?.error('视频生成需要上传参考图片')
    return
  }

  try {
    // 如果是视频类型,传入参考图片URL
    if (toolboxStore.currentInput.type === 'VIDEO') {
      await toolboxStore.generateWithConversation(referenceImageUrl.value)
    } else {
      await toolboxStore.generateWithConversation()
    }
    window.$message?.success('生成请求已提交')
    // 清空参考图片
    referenceImageUrl.value = ''
  } catch (error: any) {
    window.$message?.error(error.message || '生成失败')
  }
}

// 处理快捷键 (Ctrl+Enter发送)
const handleKeydown = (e: KeyboardEvent) => {
  if (e.ctrlKey && e.key === 'Enter') {
    e.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="border-t border-border-default bg-bg-elevated p-3">
    <!-- Controls Row -->
    <div class="flex items-center gap-2 mb-2">
      <!-- Type Select -->
      <CustomSelect
        :model-value="toolboxStore.currentInput.type"
        :options="typeOptions"
        @update:model-value="(v) => handleTypeChange(v as 'TEXT' | 'IMAGE' | 'VIDEO')"
      />

      <!-- Model Select -->
      <CustomSelect
        v-model="toolboxStore.currentInput.model"
        :options="modelOptions"
        class="flex-1"
      />

      <!-- Aspect Ratio Select (for IMAGE/VIDEO) -->
      <CustomSelect
        v-if="aspectRatioOptions.length > 0"
        v-model="toolboxStore.currentInput.aspectRatio"
        :options="aspectRatioOptions"
      />

      <!-- Duration Select (for VIDEO) -->
      <CustomSelect
        v-if="durationOptions.length > 0"
        :model-value="toolboxStore.currentInput.duration"
        :options="durationOptions"
        @update:model-value="(v) => toolboxStore.currentInput.duration = Number(v)"
      />

      <!-- Estimated Cost -->
      <div class="flex items-center gap-1 px-3 py-1.5 rounded bg-bg-subtle border border-border-default text-text-primary text-xs whitespace-nowrap">
        <span>预计:</span>
        <span class="font-medium">{{ toolboxStore.estimatedCost }}</span>
        <span>积分</span>
      </div>
    </div>

    <!-- Input Area -->
    <div class="flex items-end gap-2">
      <!-- Debug info -->
      <div class="hidden">
        {{ console.log('[ChatInput RENDER] canSend:', canSend, 'isGenerating:', toolboxStore.isGenerating, 'prompt:', toolboxStore.currentInput.prompt.length) }}
      </div>
      
      <!-- Reference Image Upload (for VIDEO only) -->
      <div v-if="toolboxStore.currentInput.type === 'VIDEO'" class="flex-shrink-0">
        <!-- Upload Button -->
        <label v-if="!referenceImageUrl" class="cursor-pointer">
          <input
            type="file"
            accept="image/*"
            class="hidden"
            @change="handleImageUpload"
          />
          <div
            class="w-16 h-16 rounded border-2 border-dashed flex items-center justify-center transition-all"
            :class="uploading
              ? 'border-gray-900/50 bg-gray-900/5'
              : 'border-border-default bg-bg-subtle hover:border-gray-900/50 hover:bg-gray-900/5'"
          >
            <svg v-if="!uploading" class="w-6 h-6 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <svg v-else class="w-6 h-6 text-text-primary animate-spin" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
          </div>
        </label>
        
        <!-- Preview -->
        <div v-else class="relative w-16 h-16 group">
          <img
            :src="referenceImageUrl"
            class="w-full h-full rounded object-cover"
          />
          <button
            class="absolute -top-1 -right-1 w-5 h-5 rounded bg-red-500 text-text-primary flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
            @click="removeReferenceImage"
          >
            <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </div>
      
      <textarea
        v-model="toolboxStore.currentInput.prompt"
        class="flex-1 px-3 py-2 rounded bg-bg-subtle border border-border-default text-text-primary text-sm resize-none focus:border-gray-900 focus:outline-none transition-all placeholder-white/30"
        placeholder="输入提示词... (Ctrl+Enter发送)"
        rows="2"
        @keydown="handleKeydown"
      />

      <!-- Send Button -->
      <button
        class="px-4 py-2 rounded font-medium text-xs transition-all flex items-center gap-1.5"
        :class="canSend
          ? 'bg-gray-900 text-text-primary hover:bg-gray-700'
          : 'bg-bg-subtle text-white/30 cursor-not-allowed border border-border-default'"
        :disabled="!canSend"
        @click="handleSend"
      >
        <span>发送</span>
        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3" />
        </svg>
      </button>
    </div>

    <!-- Hint -->
    <div class="mt-1.5 text-xs text-white/30 text-right">
      Ctrl+Enter 快速发送
    </div>
  </div>
</template>
