<script setup lang="ts">
// {{CODE-Cycle-Integration:
//   Task_ID: [#SceneLibrary-CRUD]
//   Timestamp: 2026-01-04
//   Phase: [D-Develop]
//   Context-Analysis: "Updated SceneLibraryPage to include full CRUD operations with CreateEditSceneModal integration."
//   Principle_Applied: "Pixel-Perfect-Mandate, Aether-Aesthetics, DRY"
// }}
// {{START_MODIFICATIONS}}

import { ref, computed, onMounted } from 'vue'
import { sceneApi } from '@/api/scene'
import type { SceneLibraryVO, SceneCategoryVO } from '@/types/api'
import CreateEditSceneModal from '@/components/scene-library/CreateEditSceneModal.vue'
import LoadingSpinner from '@/components/base/LoadingSpinner.vue'

// State
const loading = ref(false)
const categories = ref<SceneCategoryVO[]>([])
const scenes = ref<SceneLibraryVO[]>([])
const selectedCategoryId = ref<number | null>(null)
const searchQuery = ref('')

// Load data
onMounted(async () => {
  await loadData()
})

const loadData = async () => {
  loading.value = true
  try {
    const [categoriesData, scenesData] = await Promise.all([
      sceneApi.getSceneCategories(),
      sceneApi.getLibraryScenes(),
    ])
    categories.value = categoriesData
    scenes.value = scenesData
  } catch (error: any) {
    window.$message?.error(error.message || '加载场景库失败')
  } finally {
    loading.value = false
  }
}

// Filtered scenes
const filteredScenes = computed(() => {
  let sceneList = scenes.value

  // Filter by category
  if (selectedCategoryId.value !== null) {
    sceneList = sceneList.filter((s) => s.categoryId === selectedCategoryId.value)
  }

  // Filter by search query
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    sceneList = sceneList.filter(
      (s) =>
        s.name.toLowerCase().includes(query) ||
        (s.description && s.description.toLowerCase().includes(query))
    )
  }

  return sceneList
})

// View scene details
const selectedScene = ref<SceneLibraryVO | null>(null)
const showDetailModal = ref(false)

// Create/Edit modal
const showCreateEditModal = ref(false)
const editingScene = ref<SceneLibraryVO | null>(null)

const handleSceneClick = (scene: SceneLibraryVO) => {
  selectedScene.value = scene
  showDetailModal.value = true
}

const handleCloseDetail = () => {
  showDetailModal.value = false
  selectedScene.value = null
}

// Create/Edit handlers
const handleCreate = () => {
  editingScene.value = null
  showCreateEditModal.value = true
}

const handleEdit = (scene: SceneLibraryVO) => {
  editingScene.value = scene
  showCreateEditModal.value = true
  showDetailModal.value = false
}

const handleCreateEditSaved = async () => {
  showCreateEditModal.value = false
  editingScene.value = null
  await loadData()
}

const handleCloseCreateEdit = () => {
  showCreateEditModal.value = false
  editingScene.value = null
}

// Delete handler
const deleting = ref(false)

const handleDelete = async (scene: SceneLibraryVO) => {
  if (deleting.value) return

  const confirmed = confirm(`确定要删除场景“${scene.name}”吗？此操作不可恢复。`)
  if (!confirmed) return

  deleting.value = true
  try {
    await sceneApi.deleteLibraryScene(scene.id)
    window.$message?.success('场景删除成功')
    showDetailModal.value = false
    selectedScene.value = null
    await loadData()
  } catch (error: any) {
    window.$message?.error(error.message || '删除场景失败')
  } finally {
    deleting.value = false
  }
}

// 检查是否为有效的图片URL（只接受 HTTP/HTTPS，不接受 blob URL）
const isValidImageUrl = (url: string | null | undefined): boolean => {
  if (!url) return false
  return url.startsWith('http://') || url.startsWith('https://')
}
</script>

<template>
  <div class="min-h-screen bg-bg-base text-text-primary">
    <!-- Page Header -->
    <div class="border-b border-border-default bg-bg-elevated">
      <div class="max-w-[1600px] mx-auto px-8 py-6">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold mb-2">场景库</h1>
            <p class="text-sm text-text-tertiary">管理和浏览所有可用场景</p>
          </div>
          <button
            class="btn btn-primary"
            @click="handleCreate"
          >
            + 创建场景
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
                placeholder="场景名称或描述..."
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
                  全部场景
                  <span class="float-right text-text-tertiary">{{ scenes.length }}</span>
                </button>

                <!-- Category List -->
                <button
                  v-for="category in categories"
                  :key="category.id"
                  class="w-full text-left px-3 py-2 rounded-lg text-sm transition-colors"
                  :class="selectedCategoryId === category.id ? 'bg-[#8B5CF6] text-white font-medium' : 'text-text-secondary hover:bg-bg-hover'"
                  @click="selectedCategoryId = category.id"
                >
                  {{ category.name }}
                  <span class="float-right text-text-tertiary">
                    {{ scenes.filter(s => s.categoryId === category.id).length }}
                  </span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Right Content: Scene Grid -->
        <div class="flex-1">
          <!-- Results Header -->
          <div class="flex items-center justify-between mb-6">
            <p class="text-sm text-text-tertiary">
              共 <span class="text-text-primary font-medium">{{ filteredScenes.length }}</span> 个场景
            </p>
            <!-- Future: Sort/Filter Options -->
          </div>

          <!-- Loading State -->
          <div v-if="loading" class="flex items-center justify-center h-96">
            <LoadingSpinner size="large" />
          </div>

          <!-- Scene Grid -->
          <div v-else-if="filteredScenes.length > 0" class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
            <div
              v-for="scene in filteredScenes"
              :key="scene.id"
              class="card p-4 hover:bg-bg-hover transition-all cursor-pointer group"
              @click="handleSceneClick(scene)"
            >
              <!-- Thumbnail -->
              <div class="aspect-square rounded overflow-hidden mb-3 relative bg-bg-subtle">
                <img
                  v-if="isValidImageUrl(scene.thumbnailUrl)"
                  :src="scene.thumbnailUrl!"
                  :alt="scene.name"
                  class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                >
                <div
                  v-else
                  class="w-full h-full flex items-center justify-center"
                >
                  <svg class="w-16 h-16 text-text-disabled" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                </div>

                <!-- Category Tag (if exists) -->
                <div v-if="scene.categoryId" class="absolute top-2 left-2 bg-bg-subtle text-text-primary px-2 py-0.5 rounded text-xs font-medium ">
                  {{ categories.find(c => c.id === scene.categoryId)?.name || '未分类' }}
                </div>
              </div>

              <!-- Scene Info -->
              <h3 class="text-sm font-medium text-text-primary mb-1 truncate">{{ scene.name }}</h3>
              <p class="text-xs text-text-tertiary line-clamp-2 min-h-[2rem]">
                {{ scene.description || '暂无描述' }}
              </p>
            </div>
          </div>

          <!-- Empty State -->
          <div v-else class="flex flex-col items-center justify-center h-96 text-text-tertiary">
            <svg class="w-20 h-20 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <p class="text-lg font-medium mb-2">
              {{ searchQuery ? '未找到匹配的场景' : '场景库为空' }}
            </p>
            <p class="text-sm">
              {{ searchQuery ? '尝试使用不同的关键词搜索' : '开始创建你的第一个场景' }}
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- Scene Detail Modal -->
    <div
      v-if="showDetailModal && selectedScene"
      class="fixed inset-0 z-50 flex items-center justify-center pointer-events-none"
      @click.self="handleCloseDetail"
    >
      <div class="card w-[600px] max-h-[80vh] flex flex-col shadow-2xl pointer-events-auto" @click.stop>
        <!-- Modal Header -->
        <div class="flex items-center justify-between px-6 py-4 border-b border-border-default">
          <h2 class="text-lg font-bold text-text-primary">场景详情</h2>
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
              v-if="isValidImageUrl(selectedScene.thumbnailUrl)"
              :src="selectedScene.thumbnailUrl!"
              :alt="selectedScene.name"
              class="w-full h-full object-cover"
            >
            <div
              v-else
              class="w-full h-full flex items-center justify-center"
            >
              <svg class="w-20 h-20 text-text-disabled" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
            </div>
          </div>

          <!-- Info -->
          <div class="space-y-4">
            <div>
              <label class="text-xs font-bold text-text-secondary mb-1 block">场景名称</label>
              <p class="text-text-primary">{{ selectedScene.name }}</p>
            </div>

            <div v-if="selectedScene.categoryId">
              <label class="text-xs font-bold text-text-secondary mb-1 block">分类</label>
              <span class="inline-block bg-bg-subtle text-text-primary px-3 py-1 rounded text-sm font-medium">
                {{ categories.find(c => c.id === selectedScene?.categoryId)?.name || '未分类' }}
              </span>
            </div>

            <div>
              <label class="text-xs font-bold text-text-secondary mb-1 block">描述</label>
              <p class="text-text-secondary whitespace-pre-wrap">{{ selectedScene.description || '暂无描述' }}</p>
            </div>

            <div>
              <label class="text-xs font-bold text-text-secondary mb-1 block">创建时间</label>
              <p class="text-text-tertiary text-sm">{{ new Date(selectedScene.createdAt).toLocaleString('zh-CN') }}</p>
            </div>
          </div>
        </div>

        <!-- Modal Footer -->
        <div class="flex items-center justify-between gap-3 px-6 py-4 border-t border-border-default">
          <button
            class="px-4 py-2 rounded border border-error/50 text-error hover:bg-error/10 transition-colors text-sm"
            :disabled="deleting"
            @click="handleDelete(selectedScene!)"
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
              @click="handleEdit(selectedScene!)"
            >
              编辑
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Create/Edit Scene Modal -->
    <CreateEditSceneModal
      v-if="showCreateEditModal"
      :scene="editingScene"
      :categories="categories"
      @close="handleCloseCreateEdit"
      @saved="handleCreateEditSaved"
    />
  </div>
</template>

// {{END_MODIFICATIONS}}
