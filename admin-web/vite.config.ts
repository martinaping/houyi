import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    fs: {
      // API 文档页直接引用仓库根目录的 API.md，需要允许访问上级目录。
      allow: ['..'],
    },
    proxy: {
      // 用 ^/api/ 避免把前端路由 /api-docs 也代理到后端。
      '^/api/': {
        target: 'http://127.0.0.1:18081',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
});
