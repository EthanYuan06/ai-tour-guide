<script setup lang="ts">
// 纯装饰背景：彩色地标徽章 + 虚线航线，不参与交互
</script>

<template>
  <div class="scenery" aria-hidden="true">
    <svg class="route" viewBox="0 0 1200 800" preserveAspectRatio="none">
      <path d="M120 150 C 320 60, 520 210, 640 130 S 950 60, 1060 140" />
      <path d="M170 640 C 360 720, 620 600, 800 680 S 1050 640, 1130 480" />
      <g class="plane" transform="translate(1052,128) rotate(18)">
        <path d="M0 8 L20 0 L12 16 L10 10 Z" fill="currentColor" stroke="none" />
      </g>
    </svg>
    <div class="landmark lm-pearl">
      <svg viewBox="0 0 100 100"><use href="#lm-pearl-shape" /></svg>
      <span class="lm-name">东方明珠</span>
    </div>
    <div class="landmark lm-skytree">
      <svg viewBox="0 0 100 100"><use href="#lm-skytree-shape" /></svg>
      <span class="lm-name">晴空塔</span>
    </div>
    <div class="landmark lm-tokyo">
      <svg viewBox="0 0 100 100"><use href="#lm-tokyo-shape" /></svg>
      <span class="lm-name">东京塔</span>
    </div>
    <div class="landmark lm-palace">
      <svg viewBox="0 0 100 100"><use href="#lm-palace-shape" /></svg>
      <span class="lm-name">故宫</span>
    </div>
    <div class="landmark lm-bigben">
      <svg viewBox="0 0 100 100"><use href="#lm-bigben-shape" /></svg>
      <span class="lm-name">大本钟</span>
    </div>
    <span class="sparkle sp1">✦</span><span class="sparkle sp2 pink">✦</span>
    <span class="sparkle sp3 pink">✦</span><span class="sparkle sp4">✦</span>
    <span class="sparkle sp5">✦</span>
  </div>
</template>