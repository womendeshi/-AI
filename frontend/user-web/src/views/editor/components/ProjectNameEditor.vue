<script setup lang="ts">
// {{CODE-Cycle-Integration:
//   Task_ID: [#T-EDITOR-REDESIGN-Step1]
//   Timestamp: 2026-01-04T15:00:00+08:00
//   Phase: [D-Develop]
//   Context-Analysis: "Created ProjectNameEditor component for inline editing project name. Uses click-to-edit pattern with input field and save/cancel buttons."
//   Principle_Applied: "Pixel-Perfect-Mandate, Aether-Aesthetics, KISS"
// }}
// {{START_MODIFICATIONS}}

import { ref, computed, watch } from 'vue'
import { useProjectStore } from '@/stores/project'

const props = defineProps<{
  projectId: number
}>()

const projectStore = useProjectStore()

const isEditing = ref(false)
const editingName = ref('')
const inputRef = ref<HTMLInputElement | null>(null)

const currentProject = computed(() => projectStore.currentProject)
const projectName = computed(() => currentProject.value?.name || '未命名项目')

const startEditing = () => {
  editingName.value = projectName.value
  isEditing.value = true
  // Focus input after DOM update
  setTimeout(() => {
    inputRef.value?.focus()
    inputRef.value?.select()
  }, 50)
}

const cancelEditing = () => {
  isEditing.value = false
  editingName.value = ''
}

const saveEdit = async () => {
  const newName = editingName.value.trim()

  if (!newName) {
    window.$message?.warning('项目名称不能为空')
    return
  }

  if (newName === projectName.value) {
    cancelEditing()
    return
  }

  try {
    await projectStore.updateProject(props.projectId, { name: newName })
    window.$message?.success('项目名称已更新')
    isEditing.value = false
  } catch (error: any) {
    console.error('[ProjectNameEditor] Failed to update project name:', error)
    window.$message?.error(error.message || '更新失败')
  }
}

const handleKeyDown = (e: KeyboardEvent) => {
  if (e.key === 'Enter') {
    saveEdit()
  } else if (e.key === 'Escape') {
    cancelEditing()
  }
}

// {{END_MODIFICATIONS}}
</script>

<template>
  <div class="flex items-center gap-2">
    <!-- Display Mode -->
    <h1
      v-if="!isEditing"
      class="text-lg font-bold text-text-primary cursor-pointer hover:bg-bg-hover px-3 py-1.5 rounded transition-colors truncate max-w-[300px]"
      :title="projectName"
      @click="startEditing"
    >
      {{ projectName }}
    </h1>

    <!-- Edit Mode -->
    <div v-else class="flex items-center gap-2">
      <input
        ref="inputRef"
        v-model="editingName"
        type="text"
        class="bg-bg-hover border border-border-default rounded px-3 py-1.5 text-text-primary text-lg font-bold focus:outline-none focus:border-gray-900 transition-colors w-[250px]"
        placeholder="输入项目名称"
        @keydown="handleKeyDown"
      >

      <!-- Save Button -->
      <button
        class="p-1.5 rounded bg-bg-subtle hover:bg-gray-900/30 transition-colors"
        title="保存 (Enter)"
        @click="saveEdit"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#00FFCC" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="20 6 9 17 4 12"></polyline>
        </svg>
      </button>

      <!-- Cancel Button -->
      <button
        class="p-1.5 rounded bg-bg-hover hover:bg-bg-hover transition-colors"
        title="取消 (Esc)"
        @click="cancelEditing"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-text-secondary">
          <line x1="18" y1="6" x2="6" y2="18"></line>
          <line x1="6" y1="6" x2="18" y2="18"></line>
        </svg>
      </button>
    </div>
  </div>
</template>
