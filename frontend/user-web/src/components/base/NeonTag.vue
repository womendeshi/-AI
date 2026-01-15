<script setup lang="ts">
interface Props {
  label: string
  variant?: 'default' | 'primary' | 'success' | 'warning' | 'danger'
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
})

const colorClasses = {
  default: 'bg-bg-subtle text-text-secondary border border-border-default',
  primary: 'bg-[#8B5CF6]/20 text-[#8B5CF6] border border-[#8B5CF6]/30',
  success: 'bg-success/20 text-success border border-success/30',
  warning: 'bg-warning/20 text-warning border border-warning/30',
  danger: 'bg-error/20 text-error border border-error/30',
}
</script>

<template>
  <span
    :class="[
      'px-2 py-0.5 rounded text-xs font-medium inline-block',
      colorClasses[variant]
    ]"
  >
    {{ label }}
  </span>
</template>
