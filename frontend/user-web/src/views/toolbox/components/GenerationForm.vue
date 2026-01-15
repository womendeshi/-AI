<script setup lang="ts">
import { ref, computed } from 'vue'
import { useToolboxStore } from '@/stores/toolbox'
import { useUserStore } from '@/stores/user'
import PillButton from '@/components/base/PillButton.vue'
import GlassCard from '@/components/base/GlassCard.vue'
import DocumentIcon from '@/components/icons/DocumentIcon.vue'
import ImageIcon from '@/components/icons/ImageIcon.vue'
import VideoIcon from '@/components/icons/VideoIcon.vue'
import type { ToolboxGenerateRequest } from '@/types/api'

// Icon component map
const iconComponents = {
  TEXT: DocumentIcon,
  IMAGE: ImageIcon,
  VIDEO: VideoIcon,
}

const toolboxStore = useToolboxStore()
const userStore = useUserStore()

// Form data
const formData = ref<ToolboxGenerateRequest>({
  type: 'IMAGE',
  prompt: '',
  model: 'jimeng-4.0',
  aspectRatio: '16:9',
  referenceImageUrl: undefined,
})

const loading = ref(false)

// Options
const typeOptions = [
  { label: '文本', value: 'TEXT' },
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
]

const modelOptions = computed(() => {
  if (formData.value.type === 'TEXT') {
    return [
      { label: 'Gemini 3 Pro', value: 'gemini-3-pro-preview' },
    ]
  } else if (formData.value.type === 'IMAGE') {
    return [
      { label: '即梦图片 4.0', value: 'jimeng-4.0' },
      { label: '即梦图片 4.5', value: 'jimeng-4.5' },
      { label: 'Gemini 3 Pro Image', value: 'gemini-3-pro-image-preview' },
    ]
  } else {
    return [
      { label: 'Sora 2', value: 'sora-2' },
    ]
  }
})

const aspectRatioOptions = [
  { label: '1:1', value: '1:1' },
  { label: '16:9', value: '16:9' },
  { label: '9:16', value: '9:16' },
  { label: '21:9', value: '21:9' },
]

// Character count
const promptLength = computed(() => formData.value.prompt.length)
const maxLength = 10000

// Estimated cost
const estimatedCost = computed(() => {
  if (formData.value.type === 'TEXT') {
    return Math.ceil(promptLength.value / 1000) * 0.5
  } else if (formData.value.type === 'IMAGE') {
    return 13.5
  } else {
    return 50
  }
})

// Reference image upload
const referenceImageInput = ref<HTMLInputElement | null>(null)

const handleReferenceImageClick = () => {
  referenceImageInput.value?.click()
}

const handleReferenceImageChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  // TODO: Upload to OSS and get URL
  console.log('TODO: Upload reference image:', file)
  // For now, just use a placeholder
  formData.value.referenceImageUrl = URL.createObjectURL(file)
}

// Submit generation
const handleGenerate = async () => {
  if (!formData.value.prompt.trim()) {
    window.$message?.error('请输入提示词')
    return
  }

  loading.value = true
  try {
    const data: ToolboxGenerateRequest = {
      type: formData.value.type,
      prompt: formData.value.prompt,
      model: formData.value.model,
    }

    // Only add optional fields if applicable
    if (formData.value.type !== 'TEXT') {
      data.aspectRatio = formData.value.aspectRatio
    }

    if (formData.value.referenceImageUrl) {
      data.referenceImageUrl = formData.value.referenceImageUrl
    }

    await toolboxStore.generate(data)

    // Clear form after successful generation
    formData.value.prompt = ''
    formData.value.referenceImageUrl = undefined

    window.$message?.success('生成任务已提交')
  } catch (error: any) {
    console.error('Generate error:', error)
    window.$message?.error(error.message || '生成失败')
  } finally {
    loading.value = false
  }
}

// Update model when type changes
const handleTypeChange = () => {
  const firstModel = modelOptions.value[0]
  if (firstModel) {
    formData.value.model = firstModel.value
  }
}
</script>

<template>
  <div class="space-y-4">
    <!-- Type Selector -->
    <div>
      <div class="text-text-tertiary text-sm mb-2">生成类型</div>
      <div class="flex gap-3">
        <button
          v-for="option in typeOptions"
          :key="option.value"
          class="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded border transition-all"
          :class="
            formData.type === option.value
              ? 'bg-bg-subtle border-gray-900 text-text-primary'
              : 'bg-bg-subtle border-border-default text-text-tertiary hover:bg-bg-hover'
          "
          @click="formData.type = option.value as any; handleTypeChange()"
        >
          <component :is="iconComponents[option.value]" class="w-5 h-5" />
          <span class="text-sm font-medium">{{ option.label }}</span>
        </button>
      </div>
    </div>

    <!-- Model Selector -->
    <div>
      <div class="text-text-tertiary text-sm mb-2">模型选择</div>
      <div class="flex gap-2 flex-wrap">
        <button
          v-for="model in modelOptions"
          :key="model.value"
          class="px-4 py-2 rounded border text-sm transition-all"
          :class="
            formData.model === model.value
              ? 'bg-bg-subtle border-gray-900 text-text-primary'
              : 'bg-bg-subtle border-border-default text-text-tertiary hover:bg-bg-hover'
          "
          @click="formData.model = model.value"
        >
          {{ model.label }}
        </button>
      </div>
    </div>

    <!-- Aspect Ratio (Image/Video only) -->
    <div v-if="formData.type !== 'TEXT'">
      <div class="text-text-tertiary text-sm mb-2">画幅比例</div>
      <div class="flex gap-2">
        <button
          v-for="ratio in aspectRatioOptions"
          :key="ratio.value"
          class="px-4 py-2 rounded border text-sm transition-all"
          :class="
            formData.aspectRatio === ratio.value
              ? 'bg-bg-subtle border-gray-900 text-text-primary'
              : 'bg-bg-subtle border-border-default text-text-tertiary hover:bg-bg-hover'
          "
          @click="formData.aspectRatio = ratio.value as any"
        >
          {{ ratio.label }}
        </button>
      </div>
    </div>

    <!-- Reference Image Upload (Image only) -->
    <div v-if="formData.type === 'IMAGE'">
      <div class="text-text-tertiary text-sm mb-2">参考图（可选）</div>
      <div class="flex gap-3">
        <button
          class="flex items-center gap-2 px-4 py-2 rounded bg-bg-subtle border border-border-default text-text-tertiary hover:bg-bg-hover transition-all"
          @click="handleReferenceImageClick"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
          上传参考图
        </button>
        <input
          ref="referenceImageInput"
          type="file"
          accept="image/*"
          class="hidden"
          @change="handleReferenceImageChange"
        >
        <div v-if="formData.referenceImageUrl" class="relative">
          <img
            :src="formData.referenceImageUrl"
            class="w-16 h-16 object-cover rounded border border-border-default"
          >
          <button
            class="absolute -top-2 -right-2 w-5 h-5 bg-gray-900 rounded flex items-center justify-center text-text-primary text-xs"
            @click="formData.referenceImageUrl = undefined"
          >
            ×
          </button>
        </div>
      </div>
    </div>

    <!-- Prompt Input -->
    <div>
      <div class="text-text-tertiary text-sm mb-2">
        提示词
        <span class="text-text-tertiary">（{{ promptLength }}/{{ maxLength }}）</span>
      </div>
      <textarea
        v-model="formData.prompt"
        :placeholder="`例如：${
          formData.type === 'TEXT' ? '写一个科幻短篇小说' :
          formData.type === 'IMAGE' ? '生成一个荒无人烟的沙漠' :
          '一个机器人在未来城市行走'
        }`"
        :maxlength="maxLength"
        rows="6"
        class="w-full px-4 py-3 rounded bg-bg-subtle border border-border-default text-text-primary placeholder-white/30 focus:outline-none focus:border-gray-900 transition-all resize-none"
      />
    </div>

    <!-- Generate Button -->
    <div class="flex items-center justify-between pt-4 border-t border-border-default">
      <div class="text-text-tertiary text-sm">
        <span>消耗积分: </span>
        <span class="text-text-primary font-medium">{{ estimatedCost }}</span>
        <span class="mx-2">|</span>
        <span>当前余额: </span>
        <span class="text-text-primary">{{ userStore.points }}</span>
      </div>
      <button
        class="px-6 py-2 rounded bg-gray-900 text-text-primary font-medium text-sm hover:bg-gray-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
        :disabled="loading || !formData.prompt.trim()"
        @click="handleGenerate"
      >
        <span v-if="loading">生成中...</span>
        <span v-else>生成</span>
      </button>
    </div>
  </div>
</template>
