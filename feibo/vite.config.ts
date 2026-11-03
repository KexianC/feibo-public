import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tsconfigPaths from 'vite-tsconfig-paths';

export default defineConfig(({ mode }) => ({
  base: './',
  esbuild: mode === 'production'
    ? { drop: ['console' as const, 'debugger' as const] }
    : undefined,
  server: {
    host: '0.0.0.0',
    port: 5173,
    watch: {
      ignored: ['**/public/img/**/*.png', '**/.local-config/**'],
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8091',
        changeOrigin: true,
      },
    },
  },
  build: {
    sourcemap: mode === 'production' ? false : 'hidden',
    assetsInlineLimit: 0,
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          editor: ['konva', 'react-konva', 'zustand'],
          ui: ['antd', 'lucide-react', '@headlessui/react', '@heroicons/react'],
          data: ['xlsx'],
        },
      },
    },
  },
  plugins: [react(), tsconfigPaths()],
}));
