<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

interface Option {
  label: string
  value: string | number
  disabled?: boolean
}

interface Props {
  modelValue: string | number
  options: Option[]
  placeholder?: string
}

interface Emits {
  (e: 'update:modelValue', value: string | number): void
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '请选择'
})
const emit = defineEmits<Emits>()

const isOpen = ref(false)
const selectRef = ref<HTMLDivElement>()

const selectedLabel = computed(() => {
  const option = props.options.find(o => o.value === props.modelValue)
  return option?.label || props.placeholder
})

const handleSelect = (option: Option) => {
  if (option.disabled) return
  emit('update:modelValue', option.value)
  isOpen.value = false
}

const handleClickOutside = (e: MouseEvent) => {
  if (selectRef.value && !selectRef.value.contains(e.target as Node)) {
    isOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div ref="selectRef" class="custom-select-wrapper relative">
    <!-- 触发器 -->
    <button
      type="button"
      class="select-trigger"
      @click="isOpen = !isOpen"
    >
      <span class="select-value">{{ selectedLabel }}</span>
      <svg 
        class="select-arrow" 
        :class="{ 'rotate-180': isOpen }"
        fill="none" 
        stroke="currentColor" 
        viewBox="0 0 24 24"
      >
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
      </svg>
    </button>

    <!-- 下拉面板 -->
    <Transition name="dropdown">
      <div v-if="isOpen" class="select-dropdown">
        <div
          v-for="option in options"
          :key="option.value"
          class="select-option"
          :class="{ 
            'is-selected': option.value === modelValue,
            'is-disabled': option.disabled
          }"
          @click="handleSelect(option)"
        >
          {{ option.label }}
          <svg 
            v-if="option.value === modelValue" 
            class="w-3.5 h-3.5 text-[#8B5CF6]" 
            fill="none" 
            stroke="currentColor" 
            viewBox="0 0 24 24"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.custom-select-wrapper {
  display: inline-flex;
}

.select-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px 6px 12px;
  border-radius: 8px;
  background: #2a2d32;
  border: 1px solid #3a3d42;
  color: #e5e7eb;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  min-width: 70px;
}

.select-trigger:hover {
  background: #32363c;
  border-color: #4a4d52;
}

.select-value {
  flex: 1;
  text-align: left;
  white-space: nowrap;
}

.select-arrow {
  width: 14px;
  height: 14px;
  color: #9ca3af;
  transition: transform 0.2s ease;
  flex-shrink: 0;
}

.select-dropdown {
  position: absolute;
  bottom: calc(100% + 4px);
  left: 0;
  min-width: 100%;
  max-height: 200px;
  overflow-y: auto;
  background: #2a2d32;
  border: 1px solid #3a3d42;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
  z-index: 100;
  padding: 4px;
}

.select-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 12px;
  color: #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.select-option:hover {
  background: #3a3d42;
  color: #8B5CF6;
}

.select-option.is-selected {
  background: rgba(139, 92, 246, 0.15);
  color: #8B5CF6;
}

.select-option.is-disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.select-option.is-disabled:hover {
  background: transparent;
  color: #e5e7eb;
}

/* 下拉动画 - 向上展开 */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.2s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

/* 滚动条样式 */
.select-dropdown::-webkit-scrollbar {
  width: 4px;
}

.select-dropdown::-webkit-scrollbar-track {
  background: transparent;
}

.select-dropdown::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
}
</style>
