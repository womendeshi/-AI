<script setup lang="ts">
import { onMounted } from 'vue'
import { useInviteStore } from '@/stores/invite'
import MyCodeCard from './components/MyCodeCard.vue'
import RecordsTable from './components/RecordsTable.vue'
import StatsPanel from './components/StatsPanel.vue'
import RulesSection from './components/RulesSection.vue'

const inviteStore = useInviteStore()

onMounted(async () => {
  // Fetch all invite data
  await inviteStore.fetchMyInviteInfo()
  await inviteStore.fetchInviteRecords(1, 10)
})
</script>

<template>
  <div class="h-screen flex flex-col bg-bg-base">
    <!-- Header -->
    <div class="flex-shrink-0 px-8 py-4 border-b border-border-default">
      <h1 class="text-2xl font-bold text-text-primary">邀请好友</h1>
    </div>

    <!-- Main Content -->
    <div class="flex-1 overflow-auto">
      <div class="max-w-5xl mx-auto px-8 py-6 space-y-6">
        <!-- Top Section: Code Card + Stats Panel -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <MyCodeCard />
          <StatsPanel />
        </div>

        <!-- Invite Records -->
        <div class="p-6 rounded bg-bg-subtle border border-border-default">
          <RecordsTable />
        </div>

        <!-- Rules Section -->
        <RulesSection />
      </div>
    </div>
  </div>
</template>
