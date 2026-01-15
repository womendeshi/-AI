<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useModelConfigStore } from '@/stores/modelConfig'
import CustomSelect from '@/components/base/CustomSelect.vue'
import { modelApi } from '@/api/model'
import type { ModelVO } from '@/api/model'

// Props
interface Props {
  visible: boolean
}

const props = defineProps<Props>()

// Emits
const emit = defineEmits<{
  'update:visible': [value: boolean]
  'close': []
}>()

// Store
const modelConfigStore = useModelConfigStore()

// Local state (for editing)
const localLanguageModel = ref(modelConfigStore.currentLanguageModel)
const localCharacterImageModel = ref(modelConfigStore.currentCharacterImageModel)
const localSceneImageModel = ref(modelConfigStore.currentSceneImageModel)
const localShotImageModel = ref(modelConfigStore.currentShotImageModel)
const localVideoModel = ref(modelConfigStore.currentVideoModel)

// Model options - dynamically fetched from backend API
const languageModelOptions = ref<Array<{ value: string; label: string }>>([])
const imageModelOptions = ref<Array<{ value: string; label: string }>>([])
const videoModelOptions = ref<Array<{ value: string; label: string }>>([])
const loadingModels = ref(false)

// Fetch model options from backend
const fetchModelOptions = async () => {
  try {
    loadingModels.value = true
    const [languageModels, imageModels, videoModels] = await Promise.all([
      modelApi.getModels('LANGUAGE'),
      modelApi.getModels('IMAGE'),
      modelApi.getModels('VIDEO'),
    ])

    languageModelOptions.value = languageModels.map((m: ModelVO) => ({
      value: m.code,
      label: m.name
    }))
    imageModelOptions.value = imageModels.map((m: ModelVO) => ({
      value: m.code,
      label: m.name
    }))
    videoModelOptions.value = videoModels.map((m: ModelVO) => ({
      value: m.code,
      label: m.name
    }))

    console.log('[ModelConfigModal] Loaded models:', {
      language: languageModelOptions.value.length,
      image: imageModelOptions.value.length,
      video: videoModelOptions.value.length
    })
  } catch (error) {
    console.error('[ModelConfigModal] Failed to fetch models:', error)
    window.$message?.error('加载模型列表失败')
  } finally {
    loadingModels.value = false
  }
}

// Watch visibility to reset local state when modal opens
watch(() => props.visible, (newVisible) => {
  if (newVisible) {
    localLanguageModel.value = modelConfigStore.currentLanguageModel
    localCharacterImageModel.value = modelConfigStore.currentCharacterImageModel
    localSceneImageModel.value = modelConfigStore.currentSceneImageModel
    localShotImageModel.value = modelConfigStore.currentShotImageModel
    localVideoModel.value = modelConfigStore.currentVideoModel

    // Fetch models if not already loaded
    if (languageModelOptions.value.length === 0) {
      fetchModelOptions()
    }
  }
})

// Handle save
const handleSave = () => {
  modelConfigStore.updateConfig({
    languageModel: localLanguageModel.value,
    characterImageModel: localCharacterImageModel.value,
    sceneImageModel: localSceneImageModel.value,
    shotImageModel: localShotImageModel.value,
    videoModel: localVideoModel.value,
  })
  window.$message?.success('模型配置已保存')
  handleClose()
}

// Handle close
const handleClose = () => {
  emit('update:visible', false)
  emit('close')
}

// Handle reset to default
const handleReset = () => {
  if (confirm('确定要重置为默认配置吗？')) {
    modelConfigStore.resetToDefault()
    localLanguageModel.value = modelConfigStore.currentLanguageModel
    localCharacterImageModel.value = modelConfigStore.currentCharacterImageModel
    localSceneImageModel.value = modelConfigStore.currentSceneImageModel
    localShotImageModel.value = modelConfigStore.currentShotImageModel
    localVideoModel.value = modelConfigStore.currentVideoModel
    window.$message?.success('已重置为默认配置')
  }
}
</script>

<template>
  <!-- Modal Overlay -->
  <div
    v-if="visible"
    class="fixed inset-0 flex items-center justify-center z-50 pointer-events-none"
    @click="handleClose"
  >
    <!-- Modal Content -->
    <div
      class="bg-bg-elevated border border-border-default rounded p-6 w-full max-w-[600px] shadow-2xl max-h-[90vh] overflow-y-auto pointer-events-auto"
      @click.stop
    >
      <!-- Header -->
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-xl font-bold text-text-primary">模型配置</h2>
        <button
          class="p-2 rounded hover:bg-bg-hover transition-colors"
          @click="handleClose"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-text-tertiary">
            <path d="M18 6 6 18"></path>
            <path d="m6 6 12 12"></path>
          </svg>
        </button>
      </div>

      <!-- Form Content -->
      <div class="space-y-6">
        <!-- Language Model Selector -->
        <div>
          <label class="block text-sm font-medium text-text-secondary mb-3">
            语言模型
            <span class="text-text-tertiary text-xs ml-2 font-normal">(用于文本生成、分析等)</span>
          </label>
          <CustomSelect
            v-model="localLanguageModel"
            :options="languageModelOptions"
            placeholder="选择语言模型"
          />
        </div>

        <!-- Character Image Model Selector -->
        <div>
          <label class="block text-sm font-medium text-text-secondary mb-3">
            角色画像生成模型
            <span class="text-text-tertiary text-xs ml-2 font-normal">(用于生成角色图片)</span>
          </label>
          <CustomSelect
            v-model="localCharacterImageModel"
            :options="imageModelOptions"
            placeholder="选择角色画像模型"
          />
        </div>

        <!-- Scene Image Model Selector -->
        <div>
          <label class="block text-sm font-medium text-text-secondary mb-3">
            场景画像生成模型
            <span class="text-text-tertiary text-xs ml-2 font-normal">(用于生成场景图片)</span>
          </label>
          <CustomSelect
            v-model="localSceneImageModel"
            :options="imageModelOptions"
            placeholder="选择场景画像模型"
          />
        </div>

        <!-- Shot Image Model Selector -->
        <div>
          <label class="block text-sm font-medium text-text-secondary mb-3">
            分镜画面生成模型
            <span class="text-text-tertiary text-xs ml-2 font-normal">(用于生成分镜图)</span>
          </label>
          <CustomSelect
            v-model="localShotImageModel"
            :options="imageModelOptions"
            placeholder="选择分镜画面模型"
          />
        </div>

        <!-- Video Model Selector -->
        <div>
          <label class="block text-sm font-medium text-text-secondary mb-3">
            视频生成模型
            <span class="text-text-tertiary text-xs ml-2 font-normal">(用于生成视频)</span>
          </label>
          <CustomSelect
            v-model="localVideoModel"
            :options="videoModelOptions"
            placeholder="选择视频生成模型"
          />
        </div>

        <!-- Loading State -->
        <div v-if="loadingModels" class="bg-bg-subtle border border-border-default rounded p-4">
          <div class="flex items-center gap-3">
            <div class="animate-spin rounded h-4 w-4 border-b-2 border-gray-900"></div>
            <p class="text-text-tertiary text-xs">正在加载模型列表...</p>
          </div>
        </div>

        <!-- Info Box -->
        <div v-else class="bg-bg-subtle border border-border-default rounded p-4">
          <p class="text-text-tertiary text-xs leading-relaxed">
            💡 提示: 模型配置将应用于编辑器中的相应生成操作。不同步骤可以使用不同的模型，配置会自动保存到本地。模型选项从后端数据库获取。
          </p>
        </div>
      </div>

      <!-- Footer Actions -->
      <div class="flex items-center justify-between mt-6 pt-4 border-t border-border-default">
        <button
          class="px-4 py-2 rounded text-sm text-text-tertiary border border-border-default hover:bg-bg-subtle transition-colors"
          @click="handleReset"
        >
          重置默认
        </button>
        <div class="flex items-center gap-3">
          <button
            class="px-5 py-2 rounded text-sm text-text-tertiary border border-border-default hover:bg-bg-subtle transition-colors"
            @click="handleClose"
          >
            取消
          </button>
          <button
            class="px-5 py-2 rounded text-sm bg-gray-900 text-[#0D0E12] font-semibold hover:bg-gray-700 transition-colors"
            @click="handleSave"
          >
            保存配置
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
