<script setup lang="ts">
// {{CODE-Cycle-Integration:
//   Task_ID: [#SceneLibrary-Modal]
//   Timestamp: 2026-01-04
//   Phase: [D-Develop]
//   Context-Analysis: "Creating SceneLibraryModal for selecting scenes from library to add to project. Shows categorized scenes with search, supports selecting and adding to project."
//   Principle_Applied: "Pixel-Perfect-Mandate, Aether-Aesthetics-User-Approved, DRY"
// }}
// {{START_MODIFICATIONS}}

import { ref, computed, onMounted } from 'vue'
import { sceneApi } from '@/api/scene'
import { useEditorStore } from '@/stores/editor'
import type { SceneLibraryVO, SceneCategoryVO } from '@/types/api'
import LoadingSpinner from '@/components/base/LoadingSpinner.vue'

interface Props {
  projectId: number
}

const props = defineProps<Props>()
const emit = defineEmits<{
  close: []
  added: []
}>()

const editorStore = useEditorStore()

// State
const loading = ref(false)
const categories = ref<SceneCategoryVO[]>([])
const libraryScenes = ref<SceneLibraryVO[]>([])
const selectedCategoryId = ref<number | null>(null)
const searchQuery = ref('')
const showCreateCategory = ref(false)
const newCategoryName = ref('')
const creatingCategory = ref(false)

// Load data
onMounted(async () => {
  loading.value = true
  try {
    const [categoriesData, scenesData] = await Promise.all([
      sceneApi.getSceneCategories(),
      sceneApi.getLibraryScenes(),
    ])
    categories.value = categoriesData
    libraryScenes.value = scenesData
  } catch (error: any) {
    window.$message?.error(error.message || '加载场景库失败')
  } finally {
    loading.value = false
  }
})

// Filtered scenes
const filteredScenes = computed(() => {
  let scenes = libraryScenes.value

  // Filter by category
  if (selectedCategoryId.value !== null) {
    scenes = scenes.filter((s) => s.categoryId === selectedCategoryId.value)
  }

  // Filter by search query
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    scenes = scenes.filter(
      (s) =>
        s.name.toLowerCase().includes(query) ||
        (s.description && s.description.toLowerCase().includes(query))
    )
  }

  return scenes
})

// Add scene to project
const addingId = ref<number | null>(null)
const handleAddScene = async (scene: SceneLibraryVO) => {
  addingId.value = scene.id
  try {
    await sceneApi.createFromLibrary(props.projectId, scene.id)
    // Refresh project scenes
    await editorStore.fetchScenes()
    window.$message?.success(`已添加场景：${scene.name}`)
    emit('added')
  } catch (error: any) {
    window.$message?.error(error.message || '添加场景失败')
  } finally {
    addingId.value = null
  }
}

// Create category
const handleCreateCategory = async () => {
  if (!newCategoryName.value.trim()) {
    window.$message?.warning('请输入分类名称')
    return
  }
  creatingCategory.value = true
  try {
    const newCategory = await sceneApi.createCategory({ name: newCategoryName.value.trim() })
    categories.value.push(newCategory)
    newCategoryName.value = ''
    showCreateCategory.value = false
    window.$message?.success('分类创建成功')
  } catch (error: any) {
    window.$message?.error(error.message || '创建分类失败')
  } finally {
    creatingCategory.value = false
  }
}

// Close modal
const handleClose = () => {
  emit('close')
}

// Change category
const showCategorySelect = ref<number | null>(null)
const changingCategoryId = ref<number | null>(null)

const handleShowCategorySelect = (sceneId: number) => {
  showCategorySelect.value = sceneId
}

const handleChangeCategory = async (scene: SceneLibraryVO, categoryId: number | null) => {
  changingCategoryId.value = scene.id
  try {
    await sceneApi.updateLibraryScene(scene.id, {
      name: scene.name,
      description: scene.description || '',
      categoryId: categoryId
    })
    // Update local data
    const index = libraryScenes.value.findIndex(s => s.id === scene.id)
    if (index !== -1) {
      libraryScenes.value[index].categoryId = categoryId
    }
    window.$message?.success('分类修改成功')
    showCategorySelect.value = null
  } catch (error: any) {
    window.$message?.error(error.message || '修改分类失败')
  } finally {
    changingCategoryId.value = null
  }
}

// Delete scene
const deletingSceneId = ref<number | null>(null)
const handleDeleteScene = async (scene: SceneLibraryVO) => {
  console.log('[SceneLibraryModal] 删除场景:', scene.name)
  
  // 检查场景是否被分镜绑定（通过名字判断）
  const boundShotNames: string[] = []
  for (const shot of editorStore.shots) {
    if (shot.scene && shot.scene.sceneName === scene.name) {
      boundShotNames.push(`分镜${shot.shotNo}`)
    }
  }
  
  console.log('[SceneLibraryModal] 绑定的分镜:', boundShotNames)
  
  if (boundShotNames.length > 0) {
    window.$message?.error(`场景「${scene.name}」已被绑定到${boundShotNames.join('、')}，请先在分镜表中解绑后再删除`)
    return
  }
  
  // 确认删除
  const confirmed = window.confirm(`确定要删除场景「${scene.name}」吗？此操作不可恢复。`)
  if (!confirmed) {
    console.log('[SceneLibraryModal] 用户取消删除')
    return
  }
  
  deletingSceneId.value = scene.id
  try {
    await sceneApi.deleteLibraryScene(scene.id)
    // Remove from local data
    libraryScenes.value = libraryScenes.value.filter(s => s.id !== scene.id)
    // 清除该场景的本地图片历史记录
    editorStore.clearLocalImageHistoryByName(scene.name)
    // 刷新项目场景列表（同步场景库的删除）
    await editorStore.fetchScenes()
    window.$message?.success('场景删除成功')
  } catch (error: any) {
    console.error('[SceneLibraryModal] 删除失败:', error)
    window.$message?.error(error.message || '删除场景失败')
  } finally {
    deletingSceneId.value = null
  }
}
</script>

<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center pointer-events-none" @click.self="handleClose">
    <!-- Modal Container -->
    <div class="bg-bg-elevated border border-border-default rounded w-[900px] max-h-[80vh] flex flex-col shadow-2xl pointer-events-auto" @click.stop>
      <!-- Header -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-border-default">
        <h2 class="text-lg font-bold text-text-primary">从场景库中选择</h2>
        <button
          class="p-2 rounded-lg text-text-tertiary hover:bg-bg-subtle hover:text-text-primary transition-colors"
          @click="handleClose"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- Content -->
      <div class="flex flex-1 overflow-hidden">
        <!-- Left Sidebar: Categories -->
        <div class="w-48 border-r border-border-default p-4 overflow-y-auto">
          <h3 class="text-xs font-bold text-text-secondary mb-3">分类</h3>
          <div class="space-y-1">
            <!-- All Category -->
            <button
              class="w-full text-left px-3 py-2 rounded-lg text-sm transition-colors"
              :class="selectedCategoryId === null ? 'bg-bg-hover text-text-primary font-medium' : 'text-text-secondary hover:bg-bg-subtle'"
              @click="selectedCategoryId = null"
            >
              全部
            </button>

            <!-- Category List -->
            <button
              v-for="category in categories"
              :key="category.id"
              class="w-full text-left px-3 py-2 rounded-lg text-sm transition-colors"
              :class="selectedCategoryId === category.id ? 'bg-bg-hover text-text-primary font-medium' : 'text-text-secondary hover:bg-bg-subtle'"
              @click="selectedCategoryId = category.id"
            >
              {{ category.name }}
            </button>

            <!-- Add Category Button -->
            <div class="mt-3 pt-3 border-t border-border-default">
              <div v-if="showCreateCategory" class="space-y-2">
                <input
                  v-model="newCategoryName"
                  type="text"
                  placeholder="分类名称..."
                  class="w-full px-3 py-2 bg-bg-subtle border border-border-default rounded-lg text-text-primary text-sm placeholder-text-tertiary focus:outline-none focus:border-gray-900/50"
                  @keyup.enter="handleCreateCategory"
                  @keyup.esc="showCreateCategory = false"
                >
                <div class="flex gap-2">
                  <button
                    class="flex-1 px-3 py-1.5 bg-bg-subtle text-text-secondary text-xs font-medium rounded-lg hover:bg-bg-hover transition-colors disabled:opacity-50"
                    :disabled="creatingCategory"
                    @click="handleCreateCategory"
                  >
                    {{ creatingCategory ? '创建中...' : '确定' }}
                  </button>
                  <button
                    class="px-3 py-1.5 bg-bg-subtle text-text-secondary text-xs rounded-lg hover:bg-bg-hover transition-colors"
                    @click="showCreateCategory = false; newCategoryName = ''"
                  >
                    取消
                  </button>
                </div>
              </div>
              <button
                v-else
                class="w-full text-left px-3 py-2 rounded-lg text-sm text-text-tertiary hover:bg-bg-subtle hover:text-text-primary transition-colors flex items-center gap-2"
                @click="showCreateCategory = true"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
                </svg>
                添加分类
              </button>
            </div>
          </div>
        </div>

        <!-- Right Content: Scenes -->
        <div class="flex-1 flex flex-col overflow-hidden">
          <!-- Search Bar -->
          <div class="px-6 py-4 border-b border-border-default">
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索场景名称或描述..."
              class="w-full px-4 py-2 bg-bg-subtle border border-border-default rounded-lg text-text-primary placeholder-text-tertiary focus:outline-none focus:border-gray-900/50"
            >
          </div>

          <!-- Scene Grid -->
          <div class="flex-1 overflow-y-auto px-6 py-4">
            <!-- Loading State -->
            <div v-if="loading" class="flex items-center justify-center h-64">
              <LoadingSpinner size="medium" />
            </div>

            <!-- Scene Cards -->
            <div v-else-if="filteredScenes.length > 0" class="grid grid-cols-4 gap-4">
                <div
                  v-for="scene in filteredScenes"
                  :key="scene.id"
                  class="bg-bg-subtle border border-border-default rounded-lg p-3 hover:border-gray-400 hover:shadow-sm transition-all group"
                >
                <!-- Thumbnail -->
                <div class="aspect-square rounded-lg overflow-hidden mb-2 relative">
                  <img
                    v-if="scene.thumbnailUrl"
                    :src="scene.thumbnailUrl"
                    :alt="scene.name"
                    class="w-full h-full object-cover"
                  >
                  <div
                    v-else
                    class="w-full h-full bg-bg-base flex items-center justify-center"
                  >
                    <svg class="w-12 h-12 text-text-disabled" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                    </svg>
                  </div>

                  <!-- Add Button Overlay -->
                  <div class="absolute inset-0 bg-bg-elevated/90 flex flex-col items-center justify-center gap-2 opacity-0 group-hover:opacity-100 transition-all">
                    <!-- Category Select Dropdown -->
                    <div v-if="showCategorySelect === scene.id" class="w-full px-2">
                      <div class="bg-bg-subtle rounded-lg p-2 space-y-1 max-h-32 overflow-y-auto">
                        <button
                          class="w-full text-left px-2 py-1.5 rounded text-xs transition-colors"
                          :class="scene.categoryId === null ? 'bg-bg-hover text-text-primary font-medium' : 'text-text-secondary hover:bg-bg-hover'"
                          :disabled="changingCategoryId === scene.id"
                          @click="handleChangeCategory(scene, null)"
                        >
                          未分类
                        </button>
                        <button
                          v-for="cat in categories"
                          :key="cat.id"
                          class="w-full text-left px-2 py-1.5 rounded text-xs transition-colors"
                          :class="scene.categoryId === cat.id ? 'bg-bg-hover text-text-primary font-medium' : 'text-text-secondary hover:bg-bg-hover'"
                          :disabled="changingCategoryId === scene.id"
                          @click="handleChangeCategory(scene, cat.id)"
                        >
                          {{ cat.name }}
                        </button>
                      </div>
                      <button
                        class="w-full mt-2 px-3 py-1.5 rounded-lg border border-border-default text-text-tertiary text-xs hover:bg-bg-subtle"
                        @click="showCategorySelect = null"
                      >
                        取消
                      </button>
                    </div>
                    <!-- Action Buttons -->
                    <template v-else>
                      <div class="flex flex-col gap-2">
                        <button
                          class="px-3 py-1.5 rounded-lg border border-border-default text-text-secondary text-xs hover:bg-bg-hover transition-colors"
                          @click.stop="handleShowCategorySelect(scene.id)"
                        >
                          {{ scene.categoryId ? '修改分类' : '添加到分类' }}
                        </button>
                        <!-- 删除场景按钮 -->
                        <button
                          class="px-3 py-1.5 rounded-lg border border-red-400/50 text-red-400 text-xs hover:bg-red-400/10 transition-colors disabled:opacity-50"
                          :disabled="deletingSceneId === scene.id"
                          @click.stop="handleDeleteScene(scene)"
                        >
                          {{ deletingSceneId === scene.id ? '删除中...' : '删除场景' }}
                        </button>
                      </div>
                    </template>
                  </div>
                </div>

                <!-- Scene Info -->
                <h4 class="text-sm font-medium text-text-primary truncate mb-1">{{ scene.name }}</h4>
                <p class="text-xs text-text-tertiary line-clamp-2 h-8">{{ scene.description || '暂无描述' }}</p>
              </div>
            </div>

            <!-- Empty State -->
            <div v-else class="flex flex-col items-center justify-center h-64 text-text-tertiary">
              <svg class="w-16 h-16 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              <p class="text-sm">{{ searchQuery ? '未找到匹配的场景' : '场景库为空' }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-border-default">
        <button
          class="px-4 py-2 rounded-lg border border-border-default text-text-secondary hover:bg-bg-subtle transition-colors text-sm"
          @click="handleClose"
        >
          关闭
        </button>
      </div>
    </div>
  </div>
</template>

// {{END_MODIFICATIONS}}
