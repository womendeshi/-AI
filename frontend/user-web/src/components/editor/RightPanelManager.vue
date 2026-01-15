<script setup lang="ts">
import { computed, markRaw } from 'vue'
import { usePanelManagerStore } from '@/stores/panelManager'
import DefaultPanel from './panels/DefaultPanel.vue'
import AssetEditPanel from './panels/AssetEditPanel.vue'
import HistoryPanel from './panels/HistoryPanel.vue'
import ShotImageGeneratePanel from './panels/ShotImageGeneratePanel.vue'
import VideoGeneratePanel from './panels/VideoGeneratePanel.vue'

const panelManagerStore = usePanelManagerStore()

const panelComponents = {
  'default': markRaw(DefaultPanel),
  'asset-edit': markRaw(AssetEditPanel),
  'history': markRaw(HistoryPanel),
  'shot-image-generate': markRaw(ShotImageGeneratePanel),
  'video-generate': markRaw(VideoGeneratePanel),
}

const currentPanelComponent = computed(() => {
  const type = panelManagerStore.currentPanelType
  if (!type) return null
  return panelComponents[type] || null
})

const handleClosePanel = () => {
  panelManagerStore.closePanel()
}
</script>

<template>
  <aside class="w-[400px] bg-bg-elevated border-l border-border-subtle flex flex-col overflow-hidden flex-shrink-0">
    <Transition
      mode="out-in"
      enter-active-class="transition-all duration-200 ease-out"
      enter-from-class="opacity-0 translate-x-4"
      enter-to-class="opacity-100 translate-x-0"
      leave-active-class="transition-all duration-150 ease-in"
      leave-from-class="opacity-100 translate-x-0"
      leave-to-class="opacity-0 -translate-x-4"
    >
      <component
        v-if="currentPanelComponent"
        :is="currentPanelComponent"
        :key="panelManagerStore.panelKey"
        v-bind="panelManagerStore.panelData"
        @close="handleClosePanel"
      />
      <div v-else class="flex items-center justify-center h-full">
        <div class="text-center">
          <p class="text-text-tertiary text-sm">未选择面板</p>
        </div>
      </div>
    </Transition>
  </aside>
</template>
