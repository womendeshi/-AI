<script setup lang="ts">
import { ref, computed } from 'vue'
import { useEditorStore } from '@/stores/editor'
import { usePanelManagerStore } from '@/stores/panelManager'
import SceneLibraryModal from './SceneLibraryModal.vue'

const editorStore = useEditorStore()
const panelManagerStore = usePanelManagerStore()

// Search state
const searchQuery = ref('')

// 所有场景
const activeScenes = computed(() => {
  return editorStore.scenes
})

// 过滤场景
const availableScenes = computed(() => {
  const scenes = editorStore.scenes

  if (!searchQuery.value.trim()) {
    return scenes
  }

  const query = searchQuery.value.toLowerCase()
  return scenes.filter((s) => s.displayName.toLowerCase().includes(query))
})

// Show add scene library modal
const showLibraryModal = ref(false)

const handleAddScene = () => {
  showLibraryModal.value = true
}

const handleLibraryModalClose = () => {
  showLibraryModal.value = false
}

const handleBatchGenerate = () => {
  const activeSceneIds = activeScenes.value.map((s) => s.id)
  if (activeSceneIds.length === 0) {
    window.$message?.warning('没有可生成的场景')
    return
  }
  // TODO: Implement batch generation
  console.log('[ScenePanel] Batch generate:', activeSceneIds)
  window.$message?.info(`将为 ${activeSceneIds.length} 个场景批量生成图片`)
}

const handleSceneClick = (sceneId: number) => {
  const scene = editorStore.scenes.find(s => s.id === sceneId)
  if (!scene) return
  
  // 跳转到编辑面板
  panelManagerStore.openPanel('asset-edit', {
    assetType: 'scene',
    assetId: scene.id,
    sceneName: scene.displayName || (scene as any).name,
    existingThumbnailUrl: scene.thumbnailUrl,
    existingDescription: (scene as any).finalDescription || (scene as any).description
  })
}
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- Section: Active Scenes -->
    <div class="flex-grow overflow-y-auto pr-2">
      <h3 class="text-sm font-bold mb-3 text-text-primary">
        作品中场景
        <span class="text-text-tertiary font-normal ml-2">({{ activeScenes.length }})</span>
      </h3>

      <div class="grid grid-cols-4 gap-2 mb-6">
        <!-- Active Scene Cards -->
        <div
          v-for="scene in activeScenes"
          :key="scene.id"
          class="text-center group cursor-pointer relative rounded-lg"
          @click="handleSceneClick(scene.id)"
        >
          <!-- Thumbnail -->
          <img
            v-if="scene.thumbnailUrl"
            :src="scene.thumbnailUrl"
            :alt="scene.displayName"
            class="w-full aspect-square rounded-lg object-cover mb-1 transition-transform duration-300 group-hover:scale-105"
          >
          <div
            v-else
            class="w-full aspect-square rounded-lg bg-bg-subtle flex items-center justify-center mb-1"
          >
            <span class="text-2xl">🏞️</span>
          </div>

          <!-- Active Indicator (Neon Cyan Dot) -->
          <div
            class="absolute top-1 right-1 w-2.5 h-2.5 bg-gray-900 rounded border-2 border-[#1E2025] shadow-[0_0_6px_2px_rgba(0,255,204,0.7)]"
          ></div>

          <!-- Scene Name -->
          <p class="text-center text-xs truncate text-text-secondary">
            {{ scene.displayName }}
          </p>
        </div>

        <!-- Empty State -->
        <div
          v-if="activeScenes.length === 0"
          class="col-span-4 text-center py-8 text-text-tertiary text-xs"
        >
          暂无启用的场景
        </div>
      </div>

      <!-- Section: All Available Scenes -->
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-sm font-bold text-text-primary">
          全部可用场景
          <span class="text-text-tertiary font-normal ml-2">({{ availableScenes.length }})</span>
        </h3>

        <!-- Search Input -->
        <div class="relative">
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索场景..."
            class="px-2 py-1 pr-6 text-xs bg-bg-subtle border border-border-default rounded-lg text-text-primary placeholder-text-tertiary focus:outline-none focus:border-gray-900/50 w-32"
          >
        </div>
      </div>

      <div class="grid grid-cols-4 gap-2">
        <!-- Available Scene Cards -->
        <div
          v-for="scene in availableScenes"
          :key="scene.id"
          class="text-center group cursor-pointer relative rounded-lg"
          @click="handleSceneClick(scene.id)"
        >
          <!-- Thumbnail -->
          <img
            v-if="scene.thumbnailUrl"
            :src="scene.thumbnailUrl"
            :alt="scene.displayName"
            class="w-full aspect-square rounded-lg object-cover mb-1 transition-transform duration-300 group-hover:scale-105"
          >
          <div
            v-else
            class="w-full aspect-square rounded-lg bg-bg-subtle flex items-center justify-center mb-1"
          >
            <span class="text-2xl">🏞️</span>
          </div>

          <!-- Scene Name -->
          <p class="text-center text-xs truncate text-text-secondary">
            {{ scene.displayName }}
          </p>
        </div>

        <!-- Add Scene Button ("+") -->
        <div class="inline-block">
          <button
            class="w-full aspect-square rounded-lg border-2 border-dashed border-border-default flex flex-col items-center justify-center text-text-tertiary hover:bg-bg-subtle hover:border-white/40 transition-colors"
            @click="handleAddScene"
          >
            <svg class="w-6 h-6 mb-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            <span class="text-xs">创建</span>
          </button>
        </div>

        <!-- Empty State -->
        <div
          v-if="availableScenes.length === 0"
          class="col-span-4 text-center py-8 text-text-tertiary text-xs"
        >
          {{ searchQuery ? '未找到匹配的场景' : '暂无可用场景' }}
        </div>
      </div>
    </div>

    <!-- Scene Library Modal -->
    <SceneLibraryModal
      v-if="showLibraryModal && editorStore.projectId"
      :project-id="editorStore.projectId"
      @close="handleLibraryModalClose"
      @added="handleLibraryModalClose"
    />
  </div>
</template>
