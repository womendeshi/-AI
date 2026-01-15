<script setup lang="ts">
interface Props {
  padding?: string
  borderRadius?: string
  className?: string
}

withDefaults(defineProps<Props>(), {
  padding: 'p-4',
  borderRadius: 'rounded-lg',
  className: '',
})
</script>

<template>
  <div
    :class="[
      'bg-bg-elevated border border-border-default shadow-md',
      borderRadius,
      padding,
      className
    ]"
  >
    <slot />
  </div>
</template>

<style scoped>
/* 深色主题卡片组件 */
</style>
