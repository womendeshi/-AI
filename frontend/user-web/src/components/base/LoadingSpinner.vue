<script setup lang="ts">
defineProps<{
  size?: 'small' | 'medium' | 'large'
  text?: string
}>()

const sizeClasses = {
  small: 'w-8 h-8',
  medium: 'w-12 h-12',
  large: 'w-16 h-16'
}
</script>

<template>
  <div class="flex flex-col items-center gap-3">
    <!-- 简洁的加载动画 - 单个旋转圆环 -->
    <div class="relative" :class="sizeClasses[size || 'medium']">
      <!-- 旋转圆环 -->
      <div class="absolute inset-0 rounded-full border-4 border-t-gray-900 border-r-gray-700 border-b-gray-500 border-l-gray-300 animate-spin"></div>
    </div>
    <!-- 加载文字 -->
    <p v-if="text" class="text-text-primary text-sm font-medium">{{ text }}</p>
  </div>
</template>
