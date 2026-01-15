<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { walletApi } from '@/api/wallet'
import PointsBalanceCard from './components/PointsBalanceCard.vue'
import TransactionTable from './components/TransactionTable.vue'

const userStore = useUserStore()

onMounted(async () => {
  // Fetch latest wallet balance on mount
  try {
    const walletData = await walletApi.getBalance()
    userStore.setPoints(walletData.balance)
    console.log('[WalletPage] Wallet balance loaded:', walletData.balance)
  } catch (error) {
    console.error('[WalletPage] Failed to load wallet balance:', error)
  }
})
</script>

<template>
  <div class="min-h-screen bg-bg-base text-text-primary p-6">
    <!-- Page Header -->
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-text-primary ">
        我的钱包
      </h1>
      <p class="text-text-tertiary text-sm mt-1">
        查看积分余额和消费记录
      </p>
    </div>

    <!-- Content Area -->
    <div class="space-y-6">
      <!-- Points Balance Card -->
      <PointsBalanceCard />

      <!-- Transaction History Table -->
      <TransactionTable />
    </div>
  </div>
</template>
