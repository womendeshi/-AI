<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { NConfigProvider, NMessageProvider, useMessage } from 'naive-ui'
import MainLayout from '@/components/layout/MainLayout.vue'

const route = useRoute()
const layoutType = computed(() => route.meta.layout || 'none')

// 初始化全局消息提示
function setupMessage() {
  // 这个组件会在 NMessageProvider 内部挂载时设置 window.$message
}
</script>

<template>
  <NConfigProvider>
    <NMessageProvider>
      <MessageApiSetup />
      <MainLayout v-if="layoutType === 'main'">
        <RouterView :key="route.fullPath" />
      </MainLayout>
      <RouterView v-else :key="route.fullPath" />
    </NMessageProvider>
  </NConfigProvider>
</template>

<!-- 内部组件用于设置 window.$message -->
<script lang="ts">
import { defineComponent } from 'vue'
import { useMessage } from 'naive-ui'

export const MessageApiSetup = defineComponent({
  name: 'MessageApiSetup',
  setup() {
    const message = useMessage()
    // @ts-expect-error 挂载到 window 对象
    window.$message = message
    return () => null
  }
})
</script>

<style scoped>
/* App styles */
</style>
