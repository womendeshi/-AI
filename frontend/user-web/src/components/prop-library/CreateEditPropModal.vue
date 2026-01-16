<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { propApi } from '@/api/prop'
import type { PropLibraryVO, PropCategoryVO } from '@/types/api'

const props = defineProps<{
  prop: PropLibraryVO | null
  categories: PropCategoryVO[]
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

// Form state
const form = ref({
  name: '',
  categoryId: null as number | null,
  description: '',
})

const errors = ref({
  name: '',
})

const submitting = ref(false)

// Initialize form when prop changes
watch(
  () => props.prop,
  (newProp) => {
    if (newProp) {
      form.value = {
        name: newProp.name || '',
        categoryId: newProp.categoryId || null,
        description: newProp.description || '',
      }
    } else {
      form.value = {
        name: '',
        categoryId: null,
        description: '',
      }
    }
    errors.value = { name: '' }
  },
  { immediate: true }
)

const isEditing = computed(() => !!props.prop?.id)

const isFormValid = computed(() => {
  return form.value.name.trim().length > 0
})

const validateForm = () => {
  errors.value = { name: '' }

  if (!form.value.name.trim()) {
    errors.value.name = '请输入道具名称'
    return false
  }

  return true
}

const handleSubmit = async () => {
  if (!validateForm() || submitting.value) return

  submitting.value = true
  try {
    if (isEditing.value && props.prop) {
      // Update existing prop
      await propApi.updateLibraryProp(props.prop.id, {
        name: form.value.name.trim(),
        categoryId: form.value.categoryId,
        description: form.value.description.trim() || undefined,
      })
      window.$message?.success('道具更新成功')
    } else {
      // Create new prop
      await propApi.createLibraryProp({
        name: form.value.name.trim(),
        categoryId: form.value.categoryId,
        description: form.value.description.trim() || undefined,
      })
      window.$message?.success('道具创建成功')
    }
    emit('saved')
  } catch (error: any) {
    window.$message?.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  emit('close')
}
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center pointer-events-none"
    @click.self="handleClose"
  >
    <div class="card w-[500px] max-h-[80vh] flex flex-col shadow-2xl pointer-events-auto" @click.stop>
      <!-- Modal Header -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-border-default">
        <h2 class="text-lg font-bold text-text-primary">
          {{ isEditing ? '编辑道具' : '创建道具' }}
        </h2>
        <button
          class="p-2 rounded-lg text-text-tertiary hover:bg-bg-hover hover:text-text-primary transition-colors"
          @click="handleClose"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- Modal Content -->
      <div class="flex-1 overflow-y-auto p-6">
        <div class="space-y-4">
          <!-- Name Input -->
          <div>
            <label class="text-xs font-bold text-text-secondary mb-2 block">
              道具名称 <span class="text-error">*</span>
            </label>
            <input
              v-model="form.name"
              type="text"
              placeholder="请输入道具名称"
              class="input"
              :class="{ 'border-error': errors.name }"
              :disabled="submitting"
              maxlength="100"
            >
            <p v-if="errors.name" class="text-error text-xs mt-1">{{ errors.name }}</p>
          </div>

          <!-- Category Select -->
          <div>
            <label class="text-xs font-bold text-text-secondary mb-2 block">分类</label>
            <select
              v-model="form.categoryId"
              class="input"
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
            <label class="text-xs font-bold text-text-secondary mb-2 block">描述</label>
            <textarea
              v-model="form.description"
              placeholder="请输入道具描述..."
              rows="4"
              class="input resize-none"
              :disabled="submitting"
              maxlength="2000"
            ></textarea>
            <p class="text-text-tertiary text-xs mt-1 text-right">
              {{ form.description.length }} / 2000
            </p>
          </div>
        </div>
      </div>

      <!-- Modal Footer -->
      <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-border-default">
        <button
          class="btn btn-secondary text-sm"
          :disabled="submitting"
          @click="handleClose"
        >
          取消
        </button>
        <button
          class="btn btn-primary text-sm"
          :disabled="!isFormValid || submitting"
          @click="handleSubmit"
        >
          {{ submitting ? '保存中...' : isEditing ? '保存' : '创建' }}
        </button>
      </div>
    </div>
  </div>
</template>
