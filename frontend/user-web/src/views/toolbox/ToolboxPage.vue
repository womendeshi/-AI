<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useToolboxStore } from '@/stores/toolbox'
import UserBubble from './components/UserBubble.vue'
import AIBubble from './components/AIBubble.vue'
import ChatInput from './components/ChatInput.vue'
import HistoryDrawer from './components/HistoryDrawer.vue'

const toolboxStore = useToolboxStore()

const showHistoryDrawer = ref(false)
const chatArea = ref<HTMLDivElement>()

// 自动滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (chatArea.value) {
      chatArea.value.scrollTop = chatArea.value.scrollHeight
    }
  })
}

// 监听对话列表变化,自动滚动
watch(() => toolboxStore.conversations.length, () => {
  scrollToBottom()
})

onMounted(async () => {
  console.log('[ToolboxPage] Component mounted - Chat style')
  // 初始化 store，加载持久化的对话和历史记录
  await toolboxStore.init()
})

onUnmounted(() => {
  toolboxStore.stopJobPolling()
})
</script>

<template>
  <div class="h-screen flex flex-col bg-bg-base">
    <!-- Header -->
    <div class="flex-shrink-0 flex items-center justify-between px-8 py-4 border-b border-border-default bg-bg-elevated">
      <div class="flex items-center gap-3">
        <h1 class="text-2xl font-bold text-text-primary">工具箱</h1>
      </div>

      <!-- History Button -->
      <button
        class="w-10 h-10 rounded flex items-center justify-center text-text-tertiary hover:bg-bg-hover hover:text-text-primary transition-all border border-border-default"
        title="历史记录"
        @click="showHistoryDrawer = true"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </button>
    </div>

    <!-- Notice -->
    <div class="flex-shrink-0 px-8 py-3 bg-bg-subtle border-b border-border-default">
      <p class="text-text-tertiary text-sm">
        *仅保留最近7天的生成记录,有效素材请及时引用或保存
      </p>
    </div>

    <!-- Chat Area (Scrollable) -->
    <div ref="chatArea" class="flex-1 overflow-auto">
      <div class="max-w-5xl mx-auto px-8 py-4">
        <!-- Empty State -->
        <div v-if="toolboxStore.conversations.length === 0" class="flex flex-col items-center justify-center py-20">
          <div class="w-20 h-20 rounded bg-bg-subtle border border-border-default flex items-center justify-center mb-4">
            <svg class="w-10 h-10 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </div>
          <h3 class="text-text-secondary text-lg font-medium mb-2">开始你的AI创作</h3>
          <p class="text-text-tertiary text-sm">选择类型,输入提示词,即可生成文字或图片</p>
        </div>

        <!-- Conversation Bubbles -->
        <div v-else class="space-y-2">
          <template v-for="conversation in toolboxStore.conversations" :key="conversation.id">
            <UserBubble v-if="conversation.role === 'user'" :conversation="conversation" />
            <AIBubble v-else :conversation="conversation" />
          </template>
        </div>
      </div>
    </div>

    <!-- Input Area (Fixed Bottom) -->
    <div class="flex-shrink-0">
      <ChatInput />
    </div>

    <!-- History Drawer -->
    <HistoryDrawer v-model:show="showHistoryDrawer" />
  </div>
</template>
