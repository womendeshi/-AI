<script setup lang="ts">
// Invite rules configuration
const rules = [
  {
    title: '如何邀请',
    content: '点击"复制邀请链接"按钮,将链接分享给好友。好友通过您的链接注册后,即可获得积分奖励。',
  },
  {
    title: '奖励规则',
    content: '好友通过您的邀请码成功注册后,您将获得对应的积分奖励。被邀请的好友也会获得新人礼包积分。',
  },
  {
    title: '使用限制',
    content: '邀请码可以多次使用,每次成功邀请都会获得积分奖励。请勿通过不正当手段获取积分,一经发现将取消资格。',
  },
  {
    title: '积分用途',
    content: '积分可用于在平台上生成文本、图片、视频等AI内容。不同的生成类型消耗的积分不同。',
  },
]
</script>

<template>
  <div class="p-6 rounded bg-bg-subtle border border-border-default">
    <h3 class="text-text-primary font-medium mb-4">邀请规则</h3>

    <div class="space-y-4">
      <div
        v-for="(rule, index) in rules"
        :key="index"
        class="p-4 rounded bg-bg-subtle border border-border-default"
      >
        <h4 class="text-text-primary font-medium text-sm mb-2">{{ rule.title }}</h4>
        <p class="text-text-tertiary text-xs leading-relaxed">{{ rule.content }}</p>
      </div>
    </div>

    <!-- Notice -->
    <div class="mt-4 p-3 rounded bg-bg-subtle border border-gray-900/20">
      <p class="text-text-primary text-xs">
        注意:邀请码有效期为永久,但平台保留最终解释权。
      </p>
    </div>
  </div>
</template>
