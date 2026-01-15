<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { walletApi } from '@/api/wallet'
import type { WalletVO } from '@/types/api'

const router = useRouter()

const wallet = ref<WalletVO>({
  balance: 0,
  frozenBalance: 0,
})

const loading = ref(false)
const error = ref<string | null>(null)

onMounted(async () => {
  await fetchWallet()
})

const fetchWallet = async () => {
  loading.value = true
  error.value = null
  try {
    console.log('[PointsBalanceCard] Fetching wallet balance...')
    const data = await walletApi.getBalance()
    wallet.value = data
    console.log('[PointsBalanceCard] Wallet data loaded:', data)
  } catch (err: any) {
    console.error('[PointsBalanceCard] Failed to load wallet:', err)
    console.error('[PointsBalanceCard] Error details:', {
      message: err.message,
      response: err.response?.data,
      status: err.response?.status
    })
    error.value = err.message || '获取余额失败'
    window.$message?.error('获取余额失败: ' + (err.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleRecharge = () => {
  router.push('/recharge')
}
</script>

<template>
  <div class="bg-bg-subtle border border-border-default rounded p-6">
    <!-- Card Header -->
    <div class="flex items-center justify-between mb-6">
      <h2 class="text-lg font-semibold text-text-primary">积分余额</h2>
      <button
        class="px-4 py-1.5 rounded text-sm bg-gray-900 text-[#0D0E12] font-semibold hover:bg-gray-700 transition-colors"
        @click="handleRecharge"
      >
        立即充值
      </button>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="animate-spin rounded h-8 w-8 border-b-2 border-gray-900"></div>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="flex flex-col items-center justify-center py-12">
      <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-red-400 mb-3">
        <circle cx="12" cy="12" r="10"></circle>
        <line x1="12" x2="12" y1="8" y2="12"></line>
        <line x1="12" x2="12.01" y1="16" y2="16"></line>
      </svg>
      <p class="text-text-tertiary text-sm mb-3">{{ error }}</p>
      <button
        class="px-4 py-1.5 rounded text-sm bg-bg-subtle border border-border-default text-text-tertiary hover:bg-bg-hover transition-colors"
        @click="fetchWallet"
      >
        重试
      </button>
    </div>

    <!-- Balance Display -->
    <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <!-- Available Balance -->
      <div class="bg-bg-subtle border border-border-default rounded p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded bg-bg-subtle flex items-center justify-center">
            <svg viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" class="text-text-primary">
              <path d="M224 800c0 9.6 3.2 44.8 6.4 54.4 6.4 48-48 76.8-48 76.8s80 41.6 147.2 0S464 796.8 368 736c-22.4-12.8-41.6-19.2-57.6-19.2-51.2 0-83.2 44.8-86.4 83.2z m336-124.8l-32 51.2c-51.2 51.2-83.2 32-83.2 32 25.6 67.2 0 112-12.8 128 25.6 6.4 51.2 9.6 80 9.6 54.4 0 102.4-9.6 150.4-32 3.2 0 3.2-3.2 3.2-3.2 22.4-16 12.8-35.2 6.4-44.8-9.6-12.8-12.8-25.6-12.8-41.6 0-54.4 60.8-99.2 137.6-99.2h22.4c12.8 0 38.4 9.6 48-25.6 0-3.2 0-3.2 3.2-6.4 0-3.2 3.2-6.4 3.2-6.4 6.4-16 6.4-16 6.4-19.2 9.6-35.2 16-73.6 16-115.2 0-105.6-41.6-198.4-108.8-268.8C704 396.8 560 675.2 560 675.2z m-336-256c0-28.8 22.4-51.2 51.2-51.2 28.8 0 51.2 22.4 51.2 51.2 0 28.8-22.4 51.2-51.2 51.2-28.8 0-51.2-22.4-51.2-51.2z m96-134.4c0-22.4 19.2-41.6 41.6-41.6 22.4 0 41.6 19.2 41.6 41.6 0 22.4-19.2 41.6-41.6 41.6-22.4 0-41.6-19.2-41.6-41.6zM457.6 208c0-12.8 12.8-25.6 25.6-25.6s25.6 12.8 25.6 25.6-12.8 25.6-25.6 25.6-25.6-12.8-25.6-25.6zM128 505.6C128 592 153.6 672 201.6 736c28.8-60.8 112-60.8 124.8-60.8-16-51.2 16-99.2 16-99.2l316.8-422.4c-48-19.2-99.2-32-150.4-32-211.2-3.2-380.8 169.6-380.8 384zM764.8 86.4c-22.4 19.2-390.4 518.4-390.4 518.4-22.4 28.8-12.8 76.8 22.4 99.2l9.6 6.4c35.2 22.4 80 12.8 99.2-25.6l9.6-19.2C569.6 560 790.4 140.8 803.2 112c6.4-19.2-3.2-32-19.2-32-6.4-3.2-12.8 0-19.2 6.4z"></path>
            </svg>
          </div>
          <div>
            <p class="text-text-tertiary text-xs">可用积分</p>
            <p class="text-2xl font-bold text-text-primary mt-0.5">
              {{ wallet.balance.toLocaleString() }}
            </p>
          </div>
        </div>
        <p class="text-text-tertiary text-xs">
          可用于 AI 生成、工具箱等功能
        </p>
      </div>

      <!-- Frozen Balance -->
      <div class="bg-bg-subtle border border-border-default rounded p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-10 h-10 rounded bg-bg-hover flex items-center justify-center">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-text-tertiary">
              <rect width="18" height="11" x="3" y="11" rx="2" ry="2"></rect>
              <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
            </svg>
          </div>
          <div>
            <p class="text-text-tertiary text-xs">冻结积分</p>
            <p class="text-2xl font-bold text-text-secondary mt-0.5">
              {{ wallet.frozenBalance.toLocaleString() }}
            </p>
          </div>
        </div>
        <p class="text-text-tertiary text-xs">
          任务进行中或待退款的积分
        </p>
      </div>
    </div>

    <!-- Summary Info -->
    <div class="mt-6 pt-5 border-t border-border-default">
      <div class="flex items-center justify-between">
        <span class="text-text-tertiary text-sm">总计</span>
        <span class="text-text-primary font-semibold">
          {{ (wallet.balance + wallet.frozenBalance).toLocaleString() }} 积分
        </span>
      </div>
    </div>
  </div>
</template>
