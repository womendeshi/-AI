<script setup lang="ts">
import { useInviteStore } from '@/stores/invite'

const inviteStore = useInviteStore()

// Format date
const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// Handle page change
const handlePageChange = (page: number) => {
  inviteStore.fetchInviteRecords(page, 10)
}
</script>

<template>
  <div class="space-y-4">
    <h3 class="text-text-primary font-medium">邀请记录</h3>

    <!-- Empty State -->
    <div
      v-if="inviteStore.records.length === 0"
      class="text-center py-12 rounded bg-bg-subtle border border-border-default"
    >
      <div class="text-text-tertiary text-sm">暂无邀请记录</div>
    </div>

    <!-- Records List -->
    <div v-else class="space-y-3">
      <div
        v-for="record in inviteStore.records"
        :key="record.id"
        class="p-4 rounded bg-bg-subtle border border-border-default hover:bg-bg-hover transition-all"
      >
        <div class="flex items-center justify-between">
          <div class="flex-1">
            <div class="flex items-center gap-3 mb-2">
              <span class="text-text-primary font-medium">{{ record.inviteeNickname }}</span>
              <span class="text-text-tertiary text-sm">{{ record.inviteePhone }}</span>
            </div>
            <div class="text-text-tertiary text-xs">
              {{ formatDate(record.registeredAt) }}
            </div>
          </div>
          <div class="text-right">
            <div class="text-text-primary font-medium">+{{ record.rewardPoints }}</div>
            <div class="text-text-tertiary text-xs">积分</div>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div
        v-if="inviteStore.recordsTotal > 10"
        class="flex items-center justify-center gap-2 pt-4"
      >
        <button
          v-for="page in Math.ceil(inviteStore.recordsTotal / 10)"
          :key="page"
          class="px-3 py-1 rounded text-sm transition-all"
          :class="
            page === inviteStore.recordsPage
              ? 'bg-gray-900 text-text-primary'
              : 'bg-bg-subtle text-text-tertiary hover:bg-bg-hover'
          "
          @click="handlePageChange(page)"
        >
          {{ page }}
        </button>
      </div>
    </div>
  </div>
</template>
