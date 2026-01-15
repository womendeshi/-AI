<script setup lang="ts">
import { useInviteStore } from '@/stores/invite'

const inviteStore = useInviteStore()
</script>

<template>
  <div class="p-6 rounded bg-bg-subtle border border-border-default">
    <h3 class="text-text-primary font-medium mb-4">邀请统计</h3>

    <div class="grid grid-cols-2 gap-4">
      <!-- Total Invited -->
      <div class="p-4 rounded bg-bg-subtle border border-border-default text-center">
        <div class="text-text-tertiary text-sm mb-2">累计邀请</div>
        <div class="text-text-primary text-3xl font-bold">
          {{ inviteStore.inviteInfo?.totalInvited || 0 }}
        </div>
        <div class="text-text-tertiary text-xs mt-1">人</div>
      </div>

      <!-- Total Rewards -->
      <div class="p-4 rounded bg-bg-subtle border border-border-default text-center">
        <div class="text-text-tertiary text-sm mb-2">累计获得</div>
        <div class="text-text-primary text-3xl font-bold">
          {{ inviteStore.inviteInfo?.totalRewardsEarned || 0 }}
        </div>
        <div class="text-text-tertiary text-xs mt-1">积分</div>
      </div>
    </div>
  </div>
</template>
