<script setup lang="ts">
// {{CODE-Cycle-Integration:
//   Task_ID: [#Recharge-Module]
//   Timestamp: 2026-01-04
//   Phase: [D-Develop]
//   Context-Analysis: "Creating PaymentModal component for displaying WeChat payment QR code and initiating order status polling."
//   Principle_Applied: "Pixel-Perfect-Mandate, Aether-Aesthetics-User-Approved, DRY"
// }}
// {{START_MODIFICATIONS}}

import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { rechargeApi } from '@/api/recharge'
import type { NativeOrderVO, OrderStatusVO } from '@/types/api'
import QRCode from 'qrcode'

interface Props {
  order: NativeOrderVO
}

interface Emits {
  (e: 'close'): void
  (e: 'success', orderStatus: OrderStatusVO): void
  (e: 'failed', error: string): void
  (e: 'expired'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// State
const qrCodeDataUrl = ref('')
const polling = ref(false)
const pollingInterval = ref<number | null>(null)
const timeRemaining = ref(0)
const timerInterval = ref<number | null>(null)

// 计算价格和积分
const priceYuan = computed(() => (props.order.amountCents / 100).toFixed(2))

// 生成二维码
onMounted(async () => {
  try {
    qrCodeDataUrl.value = await QRCode.toDataURL(props.order.codeUrl, {
      width: 256,
      margin: 2,
      color: {
        dark: '#000000',
        light: '#FFFFFF',
      },
    })
  } catch (error) {
    console.error('[PaymentModal] QR code generation failed:', error)
    window.$message?.error('二维码生成失败')
  }

  // 开始轮询订单状态
  startPolling()

  // 开始倒计时
  startTimer()
})

onBeforeUnmount(() => {
  stopPolling()
  stopTimer()
})

// 开始轮询
const startPolling = () => {
  polling.value = true

  // 立即查询一次
  checkOrderStatus()

  // 每3秒查询一次
  pollingInterval.value = window.setInterval(() => {
    checkOrderStatus()
  }, 3000)
}

// 停止轮询
const stopPolling = () => {
  polling.value = false
  if (pollingInterval.value !== null) {
    clearInterval(pollingInterval.value)
    pollingInterval.value = null
  }
}

// 检查订单状态
const checkOrderStatus = async () => {
  try {
    const status = await rechargeApi.getOrderStatus(props.order.orderNo)

    if (status.status === 'SUCCEEDED') {
      stopPolling()
      emit('success', status)
    } else if (status.status === 'FAILED') {
      stopPolling()
      emit('failed', '支付失败')
    } else if (status.status === 'CANCELED') {
      stopPolling()
      emit('failed', '订单已取消')
    }
  } catch (error: any) {
    console.error('[PaymentModal] Order status check failed:', error)
  }
}

// 开始倒计时
const startTimer = () => {
  const expireAt = new Date(props.order.expireAt).getTime()
  const now = Date.now()
  timeRemaining.value = Math.max(0, Math.floor((expireAt - now) / 1000))

  timerInterval.value = window.setInterval(() => {
    timeRemaining.value = Math.max(0, timeRemaining.value - 1)

    if (timeRemaining.value === 0) {
      stopTimer()
      stopPolling()
      emit('expired')
    }
  }, 1000)
}

// 停止倒计时
const stopTimer = () => {
  if (timerInterval.value !== null) {
    clearInterval(timerInterval.value)
    timerInterval.value = null
  }
}

// 格式化倒计时
const formattedTimeRemaining = computed(() => {
  const hours = Math.floor(timeRemaining.value / 3600)
  const minutes = Math.floor((timeRemaining.value % 3600) / 60)
  const seconds = timeRemaining.value % 60

  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }
  return `${minutes}:${String(seconds).padStart(2, '0')}`
})

const handleClose = () => {
  stopPolling()
  stopTimer()
  emit('close')
}
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center pointer-events-none"
    @click.self="handleClose"
  >
    <div
      class="bg-bg-elevated border border-border-default rounded w-[500px] flex flex-col shadow-2xl pointer-events-auto"
      @click.stop
    >
      <!-- Modal Header -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-border-default">
        <h2 class="text-lg font-bold text-text-primary">扫码支付</h2>
        <button
          class="p-2 rounded-lg text-text-tertiary hover:bg-bg-subtle hover:text-text-primary transition-colors"
          @click="handleClose"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- Modal Content -->
      <div class="p-8 flex flex-col items-center">
        <!-- 订单信息 -->
        <div class="w-full bg-bg-subtle rounded p-4 mb-6">
          <div class="flex items-center justify-between mb-2">
            <span class="text-text-tertiary text-sm">订单金额</span>
            <span class="text-2xl font-bold text-text-primary">¥{{ priceYuan }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-text-tertiary text-sm">获得积分</span>
            <span class="text-lg font-bold text-text-primary">{{ order.points }}积分</span>
          </div>
        </div>

        <!-- 二维码 -->
        <div class="bg-white p-4 rounded mb-4">
          <img
            v-if="qrCodeDataUrl"
            :src="qrCodeDataUrl"
            alt="Payment QR Code"
            class="w-64 h-64"
          >
          <div v-else class="w-64 h-64 flex items-center justify-center">
            <div class="w-12 h-12 border-2 border-gray-900 border-t-transparent rounded animate-spin"></div>
          </div>
        </div>

        <!-- 提示文字 -->
        <p class="text-text-tertiary text-sm text-center mb-2">
          请使用微信扫描二维码完成支付
        </p>

        <!-- 倒计时 -->
        <div class="flex items-center gap-2 text-text-tertiary text-xs">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>订单剩余时间: {{ formattedTimeRemaining }}</span>
        </div>

        <!-- 轮询状态指示 -->
        <div v-if="polling" class="mt-4 flex items-center gap-2 text-text-primary text-xs">
          <div class="w-2 h-2 rounded bg-gray-900 animate-pulse"></div>
          <span>等待支付中...</span>
        </div>
      </div>

      <!-- Modal Footer -->
      <div class="flex items-center justify-center gap-3 px-6 py-4 border-t border-border-default">
        <button
          class="px-6 py-2 rounded border border-border-default text-text-secondary hover:bg-bg-subtle transition-colors text-sm"
          @click="handleClose"
        >
          取消支付
        </button>
      </div>
    </div>
  </div>
</template>

// {{END_MODIFICATIONS}}
