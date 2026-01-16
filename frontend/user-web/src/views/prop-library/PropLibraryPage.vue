<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { propApi } from '@/api/prop'
import type { PropLibraryVO, PropCategoryVO } from '@/types/api'
import CreateEditPropModal from '@/components/prop-library/CreateEditPropModal.vue'
import LoadingSpinner from '@/components/base/LoadingSpinner.vue'

// State
const loading = ref(false)
const categories = ref<PropCategoryVO[]>([])
const props = ref<PropLibraryVO[]>([])
const selectedCategoryId = ref<number | null>(null)
const searchQuery = ref('')
const deletingCategoryId = ref<number | null>(null)

// 批量选择模式
const isSelectionMode = ref(false)
const selectedIds = ref<Set<number>>(new Set())

// 切换选择模式
const toggleSelectionMode = () => {
  isSelectionMode.value = !isSelectionMode.value
  if (!isSelectionMode.value) {
    selectedIds.value.clear()
  }
}

// 切换单个选择
const toggleSelection = (id: number) => {
  if (selectedIds.value.has(id)) {
    selectedIds.value.delete(id)
  } else {
    selectedIds.value.add(id)
  }
}

// 全选/取消全选
const toggleSelectAll = () => {
  if (selectedIds.value.size === filteredProps.value.length) {
    selectedIds.value.clear()
  } else {
    selectedIds.value = new Set(filteredProps.value.map(p => p.id))
  }
}

// Load data
onMounted(async () => {
  await loadData()
})

const loadData = async () => {
  loading.value = true
  try {
    const [categoriesData, propsData] = await Promise.all([
      propApi.getPropCategories(),
      propApi.getLibraryProps(),
    ])
    categories.value = categoriesData
    props.value = propsData
  } catch (error: any) {
    window.$message?.error(error.message || '加载道具库失败')
  } finally {
    loading.value = false
  }
}

// Filtered props
const filteredProps = computed(() => {
  let propList = props.value

  // Filter by category
  if (selectedCategoryId.value !== null) {
    propList = propList.filter((p) => p.categoryId === selectedCategoryId.value)
  }

  // Filter by search query
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    propList = propList.filter(
      (p) =>
        p.name.toLowerCase().includes(query) ||
        (p.description && p.description.toLowerCase().includes(query))
    )
  }

  return propList
})

// View prop details
const selectedProp = ref<PropLibraryVO | null>(null)
const showDetailModal = ref(false)

// Create/Edit modal
const showCreateEditModal = ref(false)
const editingProp = ref<PropLibraryVO | null>(null)

const handlePropClick = (prop: PropLibraryVO) => {
  selectedProp.value = prop
  showDetailModal.value = true
}

const handleCloseDetail = () => {
  showDetailModal.value = false
  selectedProp.value = null
}

// Create/Edit handlers
const handleCreate = () => {
  editingProp.value = null
  showCreateEditModal.value = true
}

const handleEdit = (prop: PropLibraryVO) => {
  editingProp.value = prop
  showCreateEditModal.value = true
  showDetailModal.value = false
}

const handleCreateEditSaved = async () => {
  showCreateEditModal.value = false
  editingProp.value = null
  await loadData()
}

const handleCloseCreateEdit = () => {
  showCreateEditModal.value = false
  editingProp.value = null
}

// Delete handler
const deleting = ref(false)
const batchDeleting = ref(false)

const handleDelete = async (prop: PropLibraryVO) => {
  if (deleting.value) return

  const confirmed = confirm(`确定要删除道具"${prop.name}"吗？此操作不可恢复。`)
  if (!confirmed) return

  deleting.value = true
  try {
    await propApi.deleteLibraryProp(prop.id)
    window.$message?.success('道具删除成功')
    showDetailModal.value = false
    selectedProp.value = null
    await loadData()
  } catch (error: any) {
    window.$message?.error(error.message || '删除道具失败')
  } finally {
    deleting.value = false
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (batchDeleting.value || selectedIds.value.size === 0) return

  const confirmed = confirm(`确定要删除选中的 ${selectedIds.value.size} 个道具吗？此操作不可恢复。`)
  if (!confirmed) return

  batchDeleting.value = true
  try {
    const ids = Array.from(selectedIds.value)
    for (const id of ids) {
      await propApi.deleteLibraryProp(id)
    }
    window.$message?.success(`成功删除 ${ids.length} 个道具`)
    selectedIds.value.clear()
    isSelectionMode.value = false
    await loadData()
  } catch (error: any) {
    window.$message?.error(error.message || '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

// 检查是否为有效的图片URL
const isValidImageUrl = (url: string | null | undefined): boolean => {
  if (!url) return false
  return url.startsWith('http://') || url.startsWith('https://')
}

// 删除分类
const handleDeleteCategory = async (categoryId: number, categoryName: string, event: Event) => {
  event.stopPropagation()
  if (deletingCategoryId.value) return
  
  const count = props.value.filter(p => p.categoryId === categoryId).length
  const msg = count > 0 
    ? `分类"${categoryName}"下有 ${count} 个道具，删除后道具将变为未分类。确定删除吗？`
    : `确定要删除分类"${categoryName}"吗？`
  
  if (!confirm(msg)) return
  
  deletingCategoryId.value = categoryId
  try {
    await propApi.deleteCategory(categoryId)
    categories.value = categories.value.filter(c => c.id !== categoryId)
    if (selectedCategoryId.value === categoryId) {
      selectedCategoryId.value = null
    }
    window.$message?.success('分类删除成功')
  } catch (error: any) {
    window.$message?.error(error.message || '删除分类失败')
  } finally {
    deletingCategoryId.value = null
  }
}
</script>

<template>
  <div class="min-h-screen bg-bg-base text-text-primary">
    <!-- Page Header -->
    <div class="border-b border-border-default bg-bg-elevated">
      <div class="max-w-[1600px] mx-auto px-8 py-6">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold mb-2">道具库</h1>
            <p class="text-sm text-text-tertiary">管理和浏览所有可用道具</p>
          </div>
          <button
            class="btn btn-primary"
            @click="handleCreate"
          >
            + 创建道具
          </button>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="max-w-[1600px] mx-auto px-8 py-6">
      <div class="flex gap-6">
        <!-- Left Sidebar: Categories & Search -->
        <div class="w-64 flex-shrink-0">
          <div class="card p-4 sticky top-6">
            <!-- Search -->
            <div class="mb-6">
              <label class="text-xs font-bold text-text-secondary mb-2 block">搜索</label>
              <input
                v-model="searchQuery"
                type="text"
                placeholder="道具名称或描述..."
                class="input text-sm"
              >
            </div>

            <!-- Categories -->
            <div>
              <label class="text-xs font-bold text-text-secondary mb-2 block">分类</label>
              <div class="space-y-1">
                <!-- All Category -->
                <button
                  class="w-full text-left px-3 py-2 rounded-lg text-sm transition-colors"
                  :class="selectedCategoryId === null ? 'bg-[#8B5CF6] text-white font-medium' : 'text-text-secondary hover:bg-bg-hover'"
                  @click="selectedCategoryId = null"
                >
                  全部道具
                  <span class="float-right text-text-tertiary">{{ props.length }}</span>
                </button>

                <!-- Category List -->
                <div
                  v-for="category in categories"
                  :key="category.id"
                  class="group relative"
                >
                  <button
                    class="w-full text-left px-3 py-2 rounded-lg text-sm transition-colors"
                    :class="selectedCategoryId === category.id ? 'bg-[#8B5CF6] text-white font-medium' : 'text-text-secondary hover:bg-bg-hover'"
                    @click="selectedCategoryId = category.id"
                  >
                    {{ category.name }}
                    <span class="float-right transition-opacity" :class="[selectedCategoryId === category.id ? 'text-white/70' : 'text-text-tertiary', 'group-hover:opacity-0']">
                      {{ props.filter(p => p.categoryId === category.id).length }}
                    </span>
                  </button>
                  <button
                    @click="handleDeleteCategory(category.id, category.name, $event)"
                    :disabled="deletingCategoryId === category.id"
                    class="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded opacity-0 group-hover:opacity-100 text-text-tertiary hover:text-red-400 transition-all disabled:opacity-50"
                    title="删除分类"
                  >
                    <svg v-if="deletingCategoryId !== category.id" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                    </svg>
                    <svg v-else class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Right Content: Prop Grid -->
        <div class="flex-1">
          <!-- Results Header -->
          <div class="flex items-center justify-between mb-6">
            <p class="text-sm text-text-tertiary">
              共 <span class="text-text-primary font-medium">{{ filteredProps.length }}</span> 个道具
              <span v-if="isSelectionMode && selectedIds.size > 0" class="ml-2 text-[#8B5CF6]">
                · 已选 {{ selectedIds.size }} 个
              </span>
            </p>
            <div class="flex items-center gap-2">
              <!-- 批量删除按钮 -->
              <button
                v-if="isSelectionMode && selectedIds.size > 0"
                class="btn text-sm bg-error/10 text-error hover:bg-error/20 border-none"
                :disabled="batchDeleting"
                @click="handleBatchDelete"
              >
                {{ batchDeleting ? '删除中...' : `删除 (${selectedIds.size})` }}
              </button>
              <!-- 全选按钮 -->
              <button
                v-if="isSelectionMode"
                class="btn btn-secondary text-sm"
                @click="toggleSelectAll"
              >
                {{ selectedIds.size === filteredProps.length ? '取消全选' : '全选' }}
              </button>
              <!-- 批量选择切换 -->
              <button
                class="btn btn-secondary text-sm"
                @click="toggleSelectionMode"
              >
                {{ isSelectionMode ? '取消' : '批量选择' }}
              </button>
            </div>
          </div>

          <!-- Loading State -->
          <div v-if="loading" class="flex items-center justify-center h-96">
            <LoadingSpinner size="large" />
          </div>

          <!-- Prop Grid -->
          <div v-else-if="filteredProps.length > 0" class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
            <div
              v-for="prop in filteredProps"
              :key="prop.id"
              class="card p-4 hover:bg-bg-hover transition-all cursor-pointer group relative"
              :class="{ 'ring-2 ring-[#8B5CF6]': isSelectionMode && selectedIds.has(prop.id) }"
              @click="isSelectionMode ? toggleSelection(prop.id) : handlePropClick(prop)"
            >
              <!-- 选择框 -->
              <div
                v-if="isSelectionMode"
                class="absolute top-3 right-3 z-10"
                @click.stop="toggleSelection(prop.id)"
              >
                <div
                  class="w-5 h-5 rounded border-2 flex items-center justify-center transition-all"
                  :class="selectedIds.has(prop.id)
                    ? 'bg-[#8B5CF6] border-[#8B5CF6]'
                    : 'border-text-tertiary bg-bg-base hover:border-[#8B5CF6]'"
                >
                  <svg v-if="selectedIds.has(prop.id)" class="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
                  </svg>
                </div>
              </div>

              <!-- Thumbnail -->
              <div class="aspect-square rounded overflow-hidden mb-3 relative bg-bg-subtle">
                <img
                  v-if="isValidImageUrl(prop.thumbnailUrl)"
                  :src="prop.thumbnailUrl!"
                  :alt="prop.name"
                  class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                >
                <div
                  v-else
                  class="w-full h-full flex items-center justify-center"
                >
                  <svg class="w-16 h-16 text-text-disabled" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                  </svg>
                </div>

                <!-- Category Tag (if exists) -->
                <div v-if="prop.categoryId" class="absolute top-2 left-2 bg-bg-subtle text-text-primary px-2 py-0.5 rounded text-xs font-medium">
                  {{ categories.find(c => c.id === prop.categoryId)?.name || '未分类' }}
                </div>
              </div>

              <!-- Prop Info -->
              <h3 class="text-sm font-medium text-text-primary mb-1 truncate">{{ prop.name }}</h3>
              <p class="text-xs text-text-tertiary line-clamp-2 min-h-[2rem]">
                {{ prop.description || '暂无描述' }}
              </p>
            </div>
          </div>

          <!-- Empty State -->
          <div v-else class="flex flex-col items-center justify-center h-96 text-text-tertiary">
            <svg class="w-20 h-20 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
            </svg>
            <p class="text-lg font-medium mb-2">
              {{ searchQuery ? '未找到匹配的道具' : '道具库为空' }}
            </p>
            <p class="text-sm">
              {{ searchQuery ? '尝试使用不同的关键词搜索' : '开始创建你的第一个道具' }}
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- Prop Detail Modal -->
    <div
      v-if="showDetailModal && selectedProp"
      class="fixed inset-0 z-50 flex items-center justify-center pointer-events-none"
      @click.self="handleCloseDetail"
    >
      <div class="card w-[600px] max-h-[80vh] flex flex-col shadow-2xl pointer-events-auto" @click.stop>
        <!-- Modal Header -->
        <div class="flex items-center justify-between px-6 py-4 border-b border-border-default">
          <h2 class="text-lg font-bold text-text-primary">道具详情</h2>
          <button
            class="p-2 rounded-lg text-text-tertiary hover:bg-bg-hover hover:text-text-primary transition-colors"
            @click="handleCloseDetail"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <!-- Modal Content -->
        <div class="flex-1 overflow-y-auto p-6">
          <!-- Image -->
          <div class="aspect-video rounded overflow-hidden mb-6 bg-bg-subtle">
            <img
              v-if="isValidImageUrl(selectedProp.thumbnailUrl)"
              :src="selectedProp.thumbnailUrl!"
              :alt="selectedProp.name"
              class="w-full h-full object-cover"
            >
            <div
              v-else
              class="w-full h-full flex items-center justify-center"
            >
              <svg class="w-20 h-20 text-text-disabled" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
              </svg>
            </div>
          </div>

          <!-- Info -->
          <div class="space-y-4">
            <div>
              <label class="text-xs font-bold text-text-secondary mb-1 block">道具名称</label>
              <p class="text-text-primary">{{ selectedProp.name }}</p>
            </div>

            <div v-if="selectedProp.categoryId">
              <label class="text-xs font-bold text-text-secondary mb-1 block">分类</label>
              <span class="inline-block bg-bg-subtle text-text-primary px-3 py-1 rounded text-sm font-medium">
                {{ categories.find(c => c.id === selectedProp?.categoryId)?.name || '未分类' }}
              </span>
            </div>

            <div>
              <label class="text-xs font-bold text-text-secondary mb-1 block">描述</label>
              <p class="text-text-secondary whitespace-pre-wrap">{{ selectedProp.description || '暂无描述' }}</p>
            </div>

            <div>
              <label class="text-xs font-bold text-text-secondary mb-1 block">创建时间</label>
              <p class="text-text-tertiary text-sm">{{ new Date(selectedProp.createdAt).toLocaleString('zh-CN') }}</p>
            </div>
          </div>
        </div>

        <!-- Modal Footer -->
        <div class="flex items-center justify-between gap-3 px-6 py-4 border-t border-border-default">
          <button
            class="px-4 py-2 rounded border border-error/50 text-error hover:bg-error/10 transition-colors text-sm"
            :disabled="deleting"
            @click="handleDelete(selectedProp!)"
          >
            {{ deleting ? '删除中...' : '删除' }}
          </button>
          <div class="flex items-center gap-3">
            <button
              class="btn btn-secondary text-sm"
              @click="handleCloseDetail"
            >
              关闭
            </button>
            <button
              class="btn btn-primary text-sm"
              @click="handleEdit(selectedProp!)"
            >
              编辑
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Create/Edit Prop Modal -->
    <CreateEditPropModal
      v-if="showCreateEditModal"
      :prop="editingProp"
      :categories="categories"
      @close="handleCloseCreateEdit"
      @saved="handleCreateEditSaved"
    />
  </div>
</template>
