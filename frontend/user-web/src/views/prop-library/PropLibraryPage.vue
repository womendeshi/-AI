<template>
  <div class="flex h-full bg-bg-base">
    <!-- Sidebar -->
    <div class="w-64 border-r border-border-default bg-bg-elevated flex flex-col">
      <div class="p-4 border-b border-border-default">
        <h1 class="text-text-primary text-lg font-semibold">道具库</h1>
      </div>
      
      <div class="flex-1 overflow-y-auto p-4">
        <div class="space-y-1">
          <button
            class="w-full text-left px-3 py-2 rounded-lg text-sm transition-colors"
            :class="selectedCategory === null ? 'bg-bg-subtle text-text-primary' : 'text-text-secondary hover:bg-bg-subtle'"
            @click="selectedCategory = null"
          >
            全部道具
          </button>
          
          <button
            v-for="category in categories"
            :key="category.id"
            class="w-full text-left px-3 py-2 rounded-lg text-sm transition-colors flex items-center justify-between"
            :class="selectedCategory === category.id ? 'bg-bg-subtle text-text-primary' : 'text-text-secondary hover:bg-bg-subtle'"
            @click="selectedCategory = category.id"
          >
            <span>{{ category.name }}</span>
            <span class="text-xs text-text-tertiary">({{ category.count }})</span>
          </button>
        </div>
      </div>
      
      <div class="p-4 border-t border-border-default">
        <button
          @click="showCreateCategoryModal = true"
          class="w-full px-4 py-2 bg-gray-900 text-white rounded-lg text-sm font-medium hover:bg-gray-700 transition-colors"
        >
          添加分类
        </button>
      </div>
    </div>

    <!-- Main Content -->
    <div class="flex-1 flex flex-col">
      <!-- Toolbar -->
      <div class="p-4 border-b border-border-default bg-bg-elevated">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-4">
            <div class="relative">
              <input
                v-model="searchQuery"
                type="text"
                placeholder="搜索道具..."
                class="w-64 px-4 py-2 pl-10 bg-bg-subtle border border-border-default rounded-lg text-text-primary placeholder-text-tertiary focus:outline-none focus:border-gray-900/50"
              >
              <svg class="absolute left-3 top-2.5 w-5 h-5 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
          </div>
          
          <button
            @click="handleCreateProp"
            class="px-6 py-2 bg-gray-900 text-white rounded-lg font-medium hover:opacity-90 transition-opacity"
          >
            新建道具
          </button>
        </div>
      </div>

      <!-- Content -->
      <div class="flex-1 overflow-y-auto p-6">
        <div v-if="loading" class="flex items-center justify-center h-64">
          <LoadingSpinner size="large" />
        </div>
        
        <div v-else-if="props.length === 0" class="flex flex-col items-center justify-center h-64 text-text-tertiary">
          <svg class="w-16 h-16 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
          </svg>
          <p class="text-sm">暂无道具</p>
          <p class="text-xs mt-1">点击"新建道具"开始创建</p>
        </div>
        
        <div v-else class="grid grid-cols-4 gap-6">
          <div
            v-for="prop in props"
            :key="prop.id"
            class="bg-bg-subtle border border-border-default rounded p-4 hover:bg-bg-hover transition-all group"
          >
            <!-- Thumbnail -->
            <div class="aspect-square rounded-lg overflow-hidden mb-3 relative">
              <img
                v-if="prop.thumbnailUrl"
                :src="prop.thumbnailUrl"
                :alt="prop.name"
                class="w-full h-full object-cover"
              >
              <div
                v-else
                class="w-full h-full bg-bg-subtle flex items-center justify-center"
              >
                <svg class="w-12 h-12 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                </svg>
              </div>
              
              <!-- Actions Overlay -->
              <div class="absolute inset-0 bg-gray-800 flex items-center justify-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                <button
                  @click="handleEditProp(prop)"
                  class="p-2 bg-bg-subtle rounded-lg hover:bg-bg-hover transition-colors"
                >
                  <svg class="w-4 h-4 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                </button>
                <button
                  @click="handleDeleteProp(prop)"
                  class="p-2 bg-red-500/20 rounded-lg hover:bg-red-500/30 transition-colors"
                >
                  <svg class="w-4 h-4 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
              </div>
            </div>

            <!-- Info -->
            <h3 class="font-medium text-text-primary truncate mb-1">{{ prop.name }}</h3>
            <p class="text-xs text-text-tertiary line-clamp-2 h-10">{{ prop.description || '暂无描述' }}</p>
            
            <div class="mt-3 flex items-center justify-between">
              <span class="text-xs text-text-tertiary">{{ prop.referenceCount }} 个项目使用</span>
              <span v-if="prop.categoryName" class="text-xs bg-bg-hover px-2 py-1 rounded text-text-tertiary">
                {{ prop.categoryName }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Create/Edit Modal -->
  <div
    v-if="editingProp"
    class="fixed inset-0 z-50 flex items-center justify-center pointer-events-none"
    @click.self="handleCloseModal"
  >
    <div class="bg-bg-elevated border border-border-default rounded w-[600px] max-h-[80vh] flex flex-col shadow-2xl pointer-events-auto">
      <!-- Header -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-border-default">
        <h2 class="text-lg font-bold text-text-primary">
          {{ editingProp.id ? '编辑道具' : '创建道具' }}
        </h2>
        <button
          class="p-2 rounded-lg text-text-tertiary hover:bg-bg-subtle hover:text-text-primary transition-colors"
          @click="handleCloseModal"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- Content -->
      <div class="flex-1 overflow-y-auto p-6">
        <div class="space-y-4">
          <!-- Name Input -->
          <div>
            <label class="text-xs font-bold text-text-secondary mb-2 block">
              道具名称 <span class="text-red-400">*</span>
            </label>
            <input
              v-model="form.name"
              type="text"
              placeholder="请输入道具名称"
              class="w-full px-4 py-3 bg-bg-subtle border rounded text-text-primary placeholder-text-tertiary focus:outline-none transition-colors"
              :class="errors.name ? 'border-red-400/50' : 'border-border-default focus:border-gray-900/50'"
              :disabled="submitting"
              maxlength="100"
            >
            <p v-if="errors.name" class="text-red-400 text-xs mt-1">{{ errors.name }}</p>
          </div>

          <!-- Category Select -->
          <div>
            <label class="text-xs font-bold text-text-secondary mb-2 block">分类</label>
            <select
              v-model="form.categoryId"
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
              v-model="form.description"
              placeholder="请输入道具描述或AI生成提示词..."
              rows="8"
              class="w-full px-4 py-3 bg-bg-subtle border rounded text-text-primary placeholder-text-tertiary focus:outline-none transition-colors resize-none"
              :class="errors.description ? 'border-red-400/50' : 'border-border-default focus:border-gray-900/50'"
              :disabled="submitting"
              maxlength="2000"
            ></textarea>
            <div class="flex items-center justify-between mt-1">
              <p v-if="errors.description" class="text-red-400 text-xs">{{ errors.description }}</p>
              <p class="text-text-tertiary text-xs ml-auto">
                {{ form.description?.length || 0 }} / 2000
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-border-default">
        <button
          type="button"
          class="px-4 py-2 rounded border border-border-default text-text-secondary hover:bg-bg-subtle transition-colors text-sm"
          :disabled="submitting"
          @click="handleCloseModal"
        >
          取消
        </button>
        <button
          type="submit"
          class="px-4 py-2 rounded bg-gray-900 text-white font-medium hover:bg-gray-700 transition-colors text-sm disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!isFormValid || submitting"
          @click="handleSubmit"
        >
          {{ submitting ? '保存中...' : editingProp.id ? '保存' : '创建' }}
        </button>
      </div>
    </div>
  </div>

  <!-- Create Category Modal -->
  <div
    v-if="showCreateCategoryModal"
    class="fixed inset-0 z-50 flex items-center justify-center pointer-events-none"
    @click.self="showCreateCategoryModal = false"
  >
    <div class="bg-bg-elevated border border-border-default rounded w-[400px] flex flex-col shadow-2xl pointer-events-auto">
      <!-- Header -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-border-default">
        <h2 class="text-lg font-bold text-text-primary">添加分类</h2>
        <button
          class="p-2 rounded-lg text-text-tertiary hover:bg-bg-subtle hover:text-text-primary transition-colors"
          @click="showCreateCategoryModal = false"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- Content -->
      <div class="p-6">
        <input
          v-model="newCategoryName"
          type="text"
          placeholder="输入分类名称..."
          class="w-full px-4 py-3 bg-bg-subtle border border-border-default rounded text-text-primary placeholder-text-tertiary focus:outline-none focus:border-gray-900/50"
          @keyup.enter="handleCreateCategory"
        >
      </div>

      <!-- Footer -->
      <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-border-default">
        <button
          class="px-4 py-2 rounded border border-border-default text-text-secondary hover:bg-bg-subtle transition-colors text-sm"
          @click="showCreateCategoryModal = false"
        >
          取消
        </button>
        <button
          class="px-4 py-2 rounded bg-gray-900 text-white font-medium hover:bg-gray-700 transition-colors text-sm"
          @click="handleCreateCategory"
        >
          确定
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { propApi } from '@/api/prop'
import { useEditorStore } from '@/stores/editor'
import type { PropLibraryVO, PropCategoryVO } from '@/types/api'
import LoadingSpinner from '@/components/base/LoadingSpinner.vue'

const editorStore = useEditorStore()

// State
const loading = ref(false)
const categories = ref<PropCategoryVO[]>([])
const props = ref<PropLibraryVO[]>([])
const selectedCategory = ref<number | null>(null)
const searchQuery = ref('')
const showCreateCategoryModal = ref(false)
const newCategoryName = ref('')
const editingProp = ref<PropLibraryVO | null>(null)
const submitting = ref(false)

// Form state
const form = ref({
  name: '',
  categoryId: null as number | null,
  description: ''
})

const errors = ref({
  name: '',
  description: ''
})

// Computed
const filteredProps = computed(() => {
  let result = props.value
  
  // Filter by category
  if (selectedCategory.value !== null) {
    result = result.filter(p => p.categoryId === selectedCategory.value)
  }
  
  // Filter by search query
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(p => 
      p.name.toLowerCase().includes(query) ||
      (p.description && p.description.toLowerCase().includes(query))
    )
  }
  
  return result
})

const isFormValid = computed(() => {
  return form.value.name.trim() && 
         form.value.name.length <= 100 &&
         (!form.value.description || form.value.description.length <= 2000)
})

// Methods
const loadProps = async () => {
  loading.value = true
  try {
    const [categoriesData, propsData] = await Promise.all([
      propApi.getPropCategories(),
      propApi.getLibraryProps(selectedCategory.value || undefined)
    ])
    
    categories.value = categoriesData
    props.value = propsData
  } catch (error: any) {
    window.$message?.error(error.message || '加载道具失败')
  } finally {
    loading.value = false
  }
}

// Watchers
watch(selectedCategory, () => {
  loadProps()
})

const handleCreateProp = () => {
  editingProp.value = {
    id: 0,
    categoryId: null,
    categoryName: null,
    name: '',
    description: '',
    thumbnailUrl: null,
    referenceCount: 0,
    createdAt: '',
    updatedAt: ''
  } as PropLibraryVO
  
  form.value = {
    name: '',
    categoryId: null,
    description: ''
  }
  errors.value = {
    name: '',
    description: ''
  }
}

const handleEditProp = (prop: PropLibraryVO) => {
  editingProp.value = prop
  form.value = {
    name: prop.name,
    categoryId: prop.categoryId,
    description: prop.description || ''
  }
  errors.value = {
    name: '',
    description: ''
  }
}

const handleDeleteProp = async (prop: PropLibraryVO) => {
  if (!confirm(`确定要删除道具「${prop.name}」吗？此操作不可恢复。`)) {
    return
  }

  try {
    await propApi.deleteLibraryProp(prop.id)
    await loadProps()
    window.$message?.success('道具删除成功')
  } catch (error: any) {
    window.$message?.error(error.message || '删除道具失败')
  }
}

const handleSubmit = async () => {
  if (!isFormValid.value) return
  
  submitting.value = true
  try {
    if (editingProp.value?.id) {
      // Update existing
      await propApi.updateLibraryProp(editingProp.value.id, {
        name: form.value.name,
        categoryId: form.value.categoryId,
        description: form.value.description
      })
      window.$message?.success('道具更新成功')
    } else {
      // Create new
      await propApi.createLibraryProp({
        categoryId: form.value.categoryId,
        name: form.value.name,
        description: form.value.description
      })
      window.$message?.success('道具创建成功')
    }
    
    await loadProps()
    handleCloseModal()
  } catch (error: any) {
    window.$message?.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleCloseModal = () => {
  editingProp.value = null
  form.value = {
    name: '',
    categoryId: null,
    description: ''
  }
  errors.value = {
    name: '',
    description: ''
  }
}

const handleCreateCategory = async () => {
  if (!newCategoryName.value.trim()) return
  
  try {
    const newCategory = await propApi.createCategory({ name: newCategoryName.value.trim() })
    categories.value.push(newCategory)
    newCategoryName.value = ''
    showCreateCategoryModal.value = false
    window.$message?.success('分类创建成功')
  } catch (error: any) {
    window.$message?.error(error.message || '创建分类失败')
  }
}

// Watchers
watch(selectedCategory, () => {
  loadProps()
})

// Lifecycle
onMounted(() => {
  loadProps()
})
</script>

