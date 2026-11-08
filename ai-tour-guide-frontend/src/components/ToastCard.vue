<script setup lang="ts">
defineProps<{ show: boolean }>()
</script>

<template>
  <Transition name="toast">
    <div v-if="show" class="toast-card" role="status">功能暂未开放，正在火速开发中🚀</div>
  </Transition>
</template>