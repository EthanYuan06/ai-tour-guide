import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      // 后端 context-path 为 /api，原样转发；SSE 由 http-proxy 天然流式透传
      '/api': {
        target: 'http://localhost:8123',
        changeOrigin: true,
      },
    },
  },
})