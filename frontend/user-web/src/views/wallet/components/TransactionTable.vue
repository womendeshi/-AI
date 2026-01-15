<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { walletApi } from '@/api/wallet'
import type { TransactionVO, PageResult } from '@/types/api'

// State
const transactions = ref<TransactionVO[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filterType = ref<'ALL' | 'RECHARGE' | 'CONSUME' | 'REWARD'>('ALL')

// Computed
const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

// Type badge config
const getTypeBadge = (type: string) => {
  const typeUpper = type.toUpperCase()
  if (typeUpper.includes('RECHARGE') || typeUpper.includes('充值')) {
    return { text: '充值', class: 'bg-green-500/20 text-green-400' }
  } else if (typeUpper.includes('CONSUME') || typeUpper.includes('消费')) {
    return { text: '消费', class: 'bg-red-500/20 text-red-400' }
  } else if (typeUpper.includes('REWARD') || typeUpper.includes('奖励')) {
    return { text: '奖励', class: 'bg-bg-subtle text-text-primary' }
  } else if (typeUpper.includes('REFUND') || typeUpper.includes('退款')) {
    return { text: '退款', class: 'bg-bg-subtle text-text-secondary' }
  }
  return { text: type, class: 'bg-bg-hover text-text-tertiary' }
}

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

// Format amount (positive for income, negative for expense)
const formatAmount = (amount: number) => {
  if (amount > 0) {
    return `+${amount.toLocaleString()}`
  } else if (amount < 0) {
    return amount.toLocaleString()
  }
  return amount.toString()
}

// Format description based on bizType
const formatDescription = (transaction: TransactionVO) => {
  if (!transaction.description || transaction.description === '(无描述)') {
    // Get bizType from transaction
    const bizType = (transaction as any).bizType
    
    if (bizType === 'JOB') {
      // Parse metaJson to get the actual generation type from bizType field in charging
      try {
        const metaStr = (transaction as any).metaJson
        if (metaStr) {
          const meta = typeof metaStr === 'string' ? JSON.parse(metaStr) : metaStr
          
          // Check if this is video generation (has duration and videoUrl)
          if (meta.videoUrl || meta.duration) {
            return '视频生成'
          }
          // Check if this is text generation (has maxTokens)
          else if (meta.maxTokens !== undefined || meta.temperature !== undefined) {
            return '文本生成'
          }
          // Check if this is image generation (has aspectRatio or referenceImage)
          else if (meta.aspectRatio || meta.referenceImage) {
            return '图片生成'
          }
        }
      } catch (e) {
        console.warn('Failed to parse metaJson:', e)
      }
      return '任务消费'
    }
  }
  return transaction.description || '(无描述)'
}

// Fetch transactions
const fetchTransactions = async () => {
  loading.value = true
  error.value = null
  try {
    console.log('[TransactionTable] Fetching transactions, page:', currentPage.value)
    const result: PageResult<TransactionVO> = await walletApi.getTransactionHistory({
      page: currentPage.value,
      size: pageSize.value,
    })

    transactions.value = result.records
    total.value = result.total
    console.log('[TransactionTable] Loaded', result.records.length, 'transactions, total:', result.total)
  } catch (err: any) {
    console.error('[TransactionTable] Failed to load transactions:', err)
    console.error('[TransactionTable] Error details:', {
      message: err.message,
      response: err.response?.data,
      status: err.response?.status
    })
    error.value = err.message || '获取流水记录失败'
    window.$message?.error('获取流水记录失败: ' + (err.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// Filter transactions by type
const filteredTransactions = computed(() => {
  if (filterType.value === 'ALL') {
    return transactions.value
  }
  return transactions.value.filter((t) => {
    const typeUpper = t.type.toUpperCase()
    if (filterType.value === 'RECHARGE') {
      return typeUpper.includes('RECHARGE') || typeUpper.includes('充值')
    } else if (filterType.value === 'CONSUME') {
      return typeUpper.includes('CONSUME') || typeUpper.includes('消费')
    } else if (filterType.value === 'REWARD') {
      return typeUpper.includes('REWARD') || typeUpper.includes('奖励')
    }
    return true
  })
})

// Pagination handlers
const handlePrevPage = () => {
  if (currentPage.value > 1) {
    currentPage.value--
    fetchTransactions()
  }
}

const handleNextPage = () => {
  if (currentPage.value < totalPages.value) {
    currentPage.value++
    fetchTransactions()
  }
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  fetchTransactions()
}

// Lifecycle
onMounted(() => {
  fetchTransactions()
})
</script>

<template>
  <div class="bg-bg-subtle border border-border-default rounded p-6">
    <!-- Header -->
    <div class="flex items-center justify-between mb-6">
      <h2 class="text-lg font-semibold text-text-primary">流水记录</h2>

      <!-- Filter Tabs -->
      <div class="flex items-center gap-2 p-1 bg-bg-elevated rounded">
        <button
          :class="[
            'px-4 py-1.5 text-xs rounded transition-colors',
            filterType === 'ALL'
              ? 'bg-bg-hover text-white'
              : 'text-text-tertiary hover:text-text-primary'
          ]"
          @click="filterType = 'ALL'"
        >
          全部
        </button>
        <button
          :class="[
            'px-4 py-1.5 text-xs rounded transition-colors',
            filterType === 'RECHARGE'
              ? 'bg-bg-hover text-white'
              : 'text-text-tertiary hover:text-text-primary'
          ]"
          @click="filterType = 'RECHARGE'"
        >
          充值
        </button>
        <button
          :class="[
            'px-4 py-1.5 text-xs rounded transition-colors',
            filterType === 'CONSUME'
              ? 'bg-bg-hover text-white'
              : 'text-text-tertiary hover:text-text-primary'
          ]"
          @click="filterType = 'CONSUME'"
        >
          消费
        </button>
        <button
          :class="[
            'px-4 py-1.5 text-xs rounded transition-colors',
            filterType === 'REWARD'
              ? 'bg-bg-hover text-white'
              : 'text-text-tertiary hover:text-text-primary'
          ]"
          @click="filterType = 'REWARD'"
        >
          奖励
        </button>
      </div>
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
        @click="fetchTransactions"
      >
        重试
      </button>
    </div>

    <!-- Empty State -->
    <div v-else-if="filteredTransactions.length === 0" class="text-center py-12">
      <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="mx-auto mb-3 text-text-disabled">
        <rect width="20" height="14" x="2" y="5" rx="2"></rect>
        <line x1="2" x2="22" y1="10" y2="10"></line>
      </svg>
      <p class="text-text-tertiary text-sm">暂无流水记录</p>
    </div>

    <!-- Transaction Table -->
    <div v-else class="overflow-x-auto">
      <table class="w-full">
        <thead>
          <tr class="border-b border-border-default">
            <th class="text-left text-text-tertiary text-xs font-medium pb-3 pr-4">类型</th>
            <th class="text-left text-text-tertiary text-xs font-medium pb-3 pr-4">金额</th>
            <th class="text-left text-text-tertiary text-xs font-medium pb-3 pr-4">余额</th>
            <th class="text-left text-text-tertiary text-xs font-medium pb-3 pr-4">描述</th>
            <th class="text-left text-text-tertiary text-xs font-medium pb-3">时间</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="transaction in filteredTransactions"
            :key="transaction.id"
            class="border-b border-border-subtle last:border-0"
          >
            <!-- Type -->
            <td class="py-4 pr-4">
              <span :class="`px-2 py-0.5 rounded text-xs ${getTypeBadge(transaction.type).class}`">
                {{ getTypeBadge(transaction.type).text }}
              </span>
            </td>
            <!-- Amount -->
            <td class="py-4 pr-4">
              <span
                :class="[
                  'text-sm font-semibold',
                  transaction.amount > 0 ? 'text-green-400' : 'text-red-400'
                ]"
              >
                {{ formatAmount(transaction.amount) }}
              </span>
            </td>
            <!-- Balance -->
            <td class="py-4 pr-4">
              <span class="text-sm text-text-secondary">
                {{ transaction.balance.toLocaleString() }}
              </span>
            </td>
            <!-- Description -->
            <td class="py-4 pr-4">
              <span class="text-sm text-white/70">
                {{ formatDescription(transaction) }}
              </span>
            </td>
            <!-- Time -->
            <td class="py-4">
              <span class="text-xs text-white/50">
                {{ formatDate(transaction.createdAt) }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->
    <div v-if="!loading && filteredTransactions.length > 0" class="flex items-center justify-between mt-6 pt-4 border-t border-border-default">
      <span class="text-text-tertiary text-sm">
        共 {{ total }} 条记录
      </span>

      <div class="flex items-center gap-2">
        <button
          :disabled="currentPage === 1"
          class="px-3 py-1.5 rounded text-xs bg-bg-subtle border border-border-default text-text-tertiary hover:bg-bg-hover transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          @click="handlePrevPage"
        >
          上一页
        </button>

        <span class="text-text-secondary text-sm px-3">
          {{ currentPage }} / {{ totalPages }}
        </span>

        <button
          :disabled="currentPage === totalPages"
          class="px-3 py-1.5 rounded text-xs bg-bg-subtle border border-border-default text-text-tertiary hover:bg-bg-hover transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          @click="handleNextPage"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>
