<script setup lang="ts">
import { ref, watch } from 'vue'
import GlassCard from '@/components/base/GlassCard.vue'
import PillButton from '@/components/base/PillButton.vue'

interface Props {
  show: boolean
  title?: string
  folderName?: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  title: '新建文件夹',
  folderName: '',
  loading: false,
})

const emit = defineEmits<{
  close: []
  confirm: [name: string]
}>()

const localName = ref('')

watch(() => props.show, (show) => {
  if (show) {
    localName.value = props.folderName
  }
})

const handleConfirm = () => {
  if (localName.value.trim()) {
    emit('confirm', localName.value.trim())
  }
}

const handleCancel = () => {
  localName.value = ''
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-200"
      leave-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div
        v-if="show"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 pointer-events-none"
        @click.self="handleCancel"
      >
        <Transition
          enter-active-class="transition-all duration-200"
          leave-active-class="transition-all duration-200"
          enter-from-class="opacity-0 scale-95"
          leave-to-class="opacity-0 scale-95"
        >
          <GlassCard
            v-if="show"
            padding="p-6"
            className="w-full max-w-md pointer-events-auto"
          >
            <h2 class="text-xl font-semibold text-text-primary mb-4">{{ title }}</h2>

            <div class="mb-6">
              <label class="block text-text-secondary text-sm mb-2">文件夹名称</label>
              <input
                v-model="localName"
                type="text"
                placeholder="请输入文件夹名称"
                maxlength="50"
                class="input"
                @keydown.enter="handleConfirm"
                @keydown.esc="handleCancel"
              >
            </div>

            <div class="flex gap-3 justify-end">
              <PillButton
                label="取消"
                variant="secondary"
                :disabled="loading"
                @click="handleCancel"
              />
              <button
                :disabled="!localName.trim() || loading"
                :class="[
                  'px-6 py-2 rounded-lg text-sm font-medium transition-all',
                  localName.trim() && !loading
                    ? 'bg-[#8B5CF6] text-white hover:bg-[#A78BFA]'
                    : 'bg-bg-hover text-text-tertiary cursor-not-allowed'
                ]"
                @click="handleConfirm"
              >
                <span v-if="loading">处理中...</span>
                <span v-else>确认</span>
              </button>
            </div>
          </GlassCard>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
