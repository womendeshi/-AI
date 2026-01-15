<script setup lang="ts">
// {{CODE-Cycle-Integration:
//   Task_ID: [#Recharge-Module]
//   Timestamp: 2026-01-04
//   Phase: [D-Develop]
//   Context-Analysis: "Creating RechargePage for managing recharge workflow with product selection, payment and success feedback."
//   Principle_Applied: "Pixel-Perfect-Mandate, Aether-Aesthetics-User-Approved, DRY"
// }}
// {{START_MODIFICATIONS}}

import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { rechargeApi } from '@/api/recharge'
import type { RechargeProductVO, NativeOrderVO, OrderStatusVO } from '@/types/api'
import ProductCard from './components/ProductCard.vue'
import PaymentModal from './components/PaymentModal.vue'

const router = useRouter()
const userStore = useUserStore()

// State
const loading = ref(false)
const products = ref<RechargeProductVO[]>([])
const selectedProduct = ref<RechargeProductVO | null>(null)
const showPaymentModal = ref(false)
const currentOrder = ref<NativeOrderVO | null>(null)
const creatingOrder = ref(false)

// Load products
onMounted(async () => {
  await loadProducts()
})

const loadProducts = async () => {
  loading.value = true
  try {
    products.value = await rechargeApi.getProducts()
    console.log('[RechargePage] Products loaded:', products.value.length)
  } catch (error: any) {
    window.$message?.error(error.message || '加载充值套餐失败')
  } finally {
    loading.value = false
  }
}

// 选择套餐
const handleSelectProduct = (product: RechargeProductVO) => {
  selectedProduct.value = product
}

// 确认充值
const handleConfirmRecharge = async () => {
  if (!selectedProduct.value || creatingOrder.value) return

  creatingOrder.value = true
  try {
    const order = await rechargeApi.createOrder({
      productId: selectedProduct.value.id,
    })

    currentOrder.value = order
    showPaymentModal.value = true
    console.log('[RechargePage] Order created:', order.orderNo)
  } catch (error: any) {
    window.$message?.error(error.message || '创建订单失败')
  } finally {
    creatingOrder.value = false
  }
}

// 支付成功
const handlePaymentSuccess = async (orderStatus: OrderStatusVO) => {
  showPaymentModal.value = false
  currentOrder.value = null

  window.$message?.success(`充值成功！获得${orderStatus.points}积分`)

  // 刷新用户积分余额
  await userStore.fetchWalletBalance()

  // 清除选择
  selectedProduct.value = null

  // 可选：跳转到钱包页面
  setTimeout(() => {
    router.push('/wallet')
  }, 1500)
}

// 支付失败
const handlePaymentFailed = (error: string) => {
  showPaymentModal.value = false
  currentOrder.value = null
  window.$message?.error(error)
}

// 订单过期
const handlePaymentExpired = () => {
  showPaymentModal.value = false
  currentOrder.value = null
  window.$message?.warning('订单已过期，请重新下单')
}

// 关闭支付弹窗
const handleClosePayment = () => {
  showPaymentModal.value = false
  currentOrder.value = null
}
</script>

<template>
  <div class="min-h-screen bg-bg-base">
    <!-- Page Header -->
    <div class="border-b border-border-default bg-bg-elevated">
      <div class="max-w-[1400px] mx-auto px-8 py-6">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold mb-2 text-text-primary">
              积分充值
            </h1>
            <p class="text-sm text-text-tertiary">选择充值套餐，使用微信扫码支付</p>
          </div>

          <!-- 当前余额 -->
          <div class="card px-6 py-3">
            <div class="text-xs text-text-tertiary mb-1">当前余额</div>
            <div class="text-2xl font-bold text-text-primary">
              {{ userStore.points }} 积分
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="max-w-[1400px] mx-auto px-8 py-8">
      <!-- Loading State -->
      <div v-if="loading" class="flex items-center justify-center h-96">
        <div class="w-12 h-12 border-2 border-gray-900 border-t-transparent rounded animate-spin"></div>
      </div>

      <!-- Products Grid -->
      <div v-else-if="products.length > 0" class="space-y-8">
        <!-- 套餐选择 -->
        <div>
          <h2 class="text-lg font-bold text-text-primary mb-4">选择充值套餐</h2>
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
            <ProductCard
              v-for="product in products"
              :key="product.id"
              :product="product"
              :selected="selectedProduct?.id === product.id"
              @select="handleSelectProduct"
            />
          </div>
        </div>

        <!-- 购买按钮 -->
        <div v-if="selectedProduct" class="flex items-center justify-center">
          <div class="card p-6 w-full max-w-md">
            <div class="mb-4">
              <div class="flex items-center justify-between mb-2">
                <span class="text-text-tertiary">已选套餐</span>
                <span class="text-text-primary font-medium">{{ selectedProduct.name }}</span>
              </div>
              <div class="flex items-center justify-between mb-2">
                <span class="text-text-tertiary">支付金额</span>
                <span class="text-xl font-bold text-text-primary">
                  ¥{{ (selectedProduct.priceCents / 100).toFixed(2) }}
                </span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-text-tertiary">获得积分</span>
                <span class="text-lg font-bold text-text-primary">
                  {{ selectedProduct.points }}积分
                </span>
              </div>
            </div>

            <button
              class="btn btn-primary w-full"
              :disabled="creatingOrder"
              @click="handleConfirmRecharge"
            >
              {{ creatingOrder ? '创建订单中...' : '立即充值' }}
            </button>
          </div>
        </div>

        <!-- 温馨提示 -->
        <div class="card p-6">
          <h3 class="text-text-primary font-medium mb-3 flex items-center gap-2">
            <svg class="w-5 h-5 text-text-secondary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            温馨提示
          </h3>
          <ul class="space-y-2 text-sm text-text-tertiary">
            <li>• 支付完成后积分将立即到账</li>
            <li>• 订单有效期为2小时，请在有效期内完成支付</li>
            <li>• 如遇支付问题，请联系客服</li>
            <li>• 充值后的积分不可提现，仅可用于平台消费</li>
          </ul>
        </div>
      </div>

      <!-- Empty State -->
      <div v-else class="flex flex-col items-center justify-center h-96 text-text-tertiary">
        <svg class="w-20 h-20 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
        </svg>
        <p class="text-lg font-medium mb-2">暂无充值套餐</p>
        <p class="text-sm">请稍后再试</p>
      </div>
    </div>

    <!-- Payment Modal -->
    <PaymentModal
      v-if="showPaymentModal && currentOrder"
      :order="currentOrder"
      @close="handleClosePayment"
      @success="handlePaymentSuccess"
      @failed="handlePaymentFailed"
      @expired="handlePaymentExpired"
    />
  </div>
</template>

// {{END_MODIFICATIONS}}
