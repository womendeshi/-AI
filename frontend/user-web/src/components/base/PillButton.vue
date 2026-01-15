<script setup lang="ts">
interface Props {
  label?: string
  icon?: string
  variant?: 'ghost' | 'primary' | 'secondary'
  disabled?: boolean
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'secondary',
  disabled: false,
  loading: false,
})

defineEmits<{
  click: []
}>()

const variantClasses = {
  ghost: 'text-text-secondary hover:text-text-primary hover:bg-bg-hover transition-colors',
  primary: 'bg-[#8B5CF6] text-white font-medium hover:bg-[#A78BFA] transition-all',
  secondary: 'border border-border-default text-text-secondary hover:bg-bg-hover hover:text-text-primary transition-colors',
}
</script>

<template>
  <button
    :class="[
      'flex items-center gap-2 px-3 py-1 rounded text-xs',
      variantClasses[variant],
      disabled && 'opacity-50 cursor-not-allowed',
    ]"
    :disabled="disabled || loading"
    @click="$emit('click')"
  >
    <span v-if="icon" v-html="icon" />
    <span v-if="loading">加载中...</span>
    <span v-else-if="label">{{ label }}</span>
    <slot v-else />
  </button>
</template>
