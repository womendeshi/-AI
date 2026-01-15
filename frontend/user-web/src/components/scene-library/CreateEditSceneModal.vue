<script setup lang="ts">
// {{CODE-Cycle-Integration:
//   Task_ID: [#SceneLibrary-CRUD]
//   Timestamp: 2026-01-04
//   Phase: [D-Develop]
//   Context-Analysis: "Creating CreateEditSceneModal for scene library CRUD operations. Backend APIs confirmed in SCENE_SERVICE_DEV.md."
//   Principle_Applied: "Pixel-Perfect-Mandate, Aether-Aesthetics-User-Approved, DRY"
// }}
// {{START_MODIFICATIONS}}

import { ref, computed } from 'vue'
import { sceneApi } from '@/api/scene'
import type { SceneLibraryVO, SceneCategoryVO } from '@/types/api'

interface Props {
  scene?: SceneLibraryVO | null
  categories: SceneCategoryVO[]
}

interface Emits {
  (e: 'close'): void
  (e: 'saved'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// Form state
const formData = ref({
  name: props.scene?.name || '',
  description: props.scene?.description || '',
  categoryId: props.scene?.categoryId || null,
})

const submitting = ref(false)
const isEditMode = computed(() => !!props.scene)

// Validation
const nameError = computed(() => {
  if (!formData.value.name.trim()) return '场景名称不能为空'
  if (formData.value.name.length > 100) return '场景名称不能超过100个字符'
  return ''
})

const descriptionError = computed(() => {
  if (formData.value.description && formData.value.description.length > 2000) {
    return '场景描述不能超过2000个字符'
  }
  return ''
})

const isValid = computed(() => {
  return !nameError.value && !descriptionError.value
})

const handleSubmit = async () => {
  if (!isValid.value || submitting.value) return

  submitting.value = true
  try {
    if (isEditMode.value && props.scene) {
      // Update existing scene
      await sceneApi.updateLibraryScene(props.scene.id, {
        name: formData.value.name,
        description: formData.value.description || undefined,
        categoryId: formData.value.categoryId || null,
      })
      window.$message?.success('场景更新成功')
    } else {
      // Create new scene
      await sceneApi.createLibraryScene({
        categoryId: formData.value.categoryId || undefined,
        name: formData.value.name,
        description: formData.value.description || undefined,
      })
      window.$message?.success('场景创建成功')
    }
    emit('saved')
  } catch (error: any) {
    window.$message?.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  if (!submitting.value) {
    emit('close')
  }
}
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center pointer-events-none"
    @click.self="handleClose"
  >
    <div
      class="bg-bg-elevated border border-border-default rounded w-[600px] max-h-[80vh] flex flex-col shadow-2xl pointer-events-auto"
      @click.stop
    >
      <!-- Modal Header -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-border-default">
        <h2 class="text-lg font-bold text-text-primary">
          {{ isEditMode ? '编辑场景' : '创建场景' }}
        </h2>
        <button
          class="p-2 rounded-lg text-text-tertiary hover:bg-bg-subtle hover:text-text-primary transition-colors"
          :disabled="submitting"
          @click="handleClose"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- Modal Content -->
      <form class="flex-1 overflow-y-auto p-6" @submit.prevent="handleSubmit">
        <div class="space-y-4">
          <!-- Name Input -->
          <div>
            <label class="text-xs font-bold text-text-secondary mb-2 block">
              场景名称 <span class="text-red-400">*</span>
            </label>
            <input
              v-model="formData.name"
              type="text"
              placeholder="请输入场景名称"
              class="w-full px-4 py-3 bg-bg-subtle border rounded text-text-primary placeholder-text-tertiary focus:outline-none transition-colors"
              :class="nameError ? 'border-red-400/50' : 'border-border-default focus:border-gray-900/50'"
              :disabled="submitting"
              maxlength="100"
            >
            <p v-if="nameError" class="text-red-400 text-xs mt-1">{{ nameError }}</p>
          </div>

          <!-- Category Select -->
          <div>
            <label class="text-xs font-bold text-text-secondary mb-2 block">分类</label>
            <select
              v-model="formData.categoryId"
              class="w-full px-4 py-3 bg-bg-subtle border border-border-default rounded text-text-primary focus:outline-none focus:border-gray-900/50 transition-colors"
              :disabled="submitting"
            >
              <option :value="null">无分类</option>
              <option
                v-for="category in categories"
                :key="category.id"
                :value="category.id"
              >
                {{ category.name }}
              </option>
            </select>
          </div>

          <!-- Description Textarea -->
          <div>
            <label class="text-xs font-bold text-text-secondary mb-2 block">描述 / 提示词</label>
            <textarea
              v-model="formData.description"
              placeholder="请输入场景描述或AI生成提示词..."
              rows="8"
              class="w-full px-4 py-3 bg-bg-subtle border rounded text-text-primary placeholder-text-tertiary focus:outline-none transition-colors resize-none"
              :class="descriptionError ? 'border-red-400/50' : 'border-border-default focus:border-gray-900/50'"
              :disabled="submitting"
              maxlength="2000"
            ></textarea>
            <div class="flex items-center justify-between mt-1">
              <p v-if="descriptionError" class="text-red-400 text-xs">{{ descriptionError }}</p>
              <p class="text-text-tertiary text-xs ml-auto">
                {{ formData.description.length }} / 2000
              </p>
            </div>
          </div>
        </div>
      </form>

      <!-- Modal Footer -->
      <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-border-default">
        <button
          type="button"
          class="px-4 py-2 rounded border border-border-default text-text-secondary hover:bg-bg-subtle transition-colors text-sm"
          :disabled="submitting"
          @click="handleClose"
        >
          取消
        </button>
        <button
          type="submit"
          class="px-4 py-2 rounded bg-gray-900 text-white font-medium hover:bg-gray-700 transition-colors text-sm disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!isValid || submitting"
          @click="handleSubmit"
        >
          {{ submitting ? '保存中...' : isEditMode ? '保存' : '创建' }}
        </button>
      </div>
    </div>
  </div>
</template>

// {{END_MODIFICATIONS}}
