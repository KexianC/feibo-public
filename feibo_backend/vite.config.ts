import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tsconfigPaths from "vite-tsconfig-paths";
import { fileURLToPath, URL } from 'node:url'

const projectRoot = fileURLToPath(new URL('.', import.meta.url))

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  root: projectRoot,
  esbuild: mode === 'production'
    ? { drop: ['console' as const, 'debugger' as const] }
    : undefined,
  plugins: [
    react({
      babel: {
        // The locator embeds absolute build-machine paths and thousands of
        // inspector attributes. Keep it available for local development only.
        plugins: mode === 'development' ? ['react-dev-locator'] : [],
      },
    }),
    tsconfigPaths(),
  ],
  // Keep the alias stable when this workspace is reached through its Windows
  // junction path. TypeScript resolves the junction, while Rollup otherwise
  // sees the original path and can fail to match the tsconfig alias.
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0', // 允许局域网访问
    proxy: {
      '/geo-agent-api': {
        target: 'http://localhost:8091',
        changeOrigin: true,
        secure: false,
      },
      '/api': {
        target: 'http://localhost:3001',
        changeOrigin: true,
        secure: false,
        configure: (proxy, _options) => {
          proxy.on('error', (err, _req, _res) => {
            console.log('proxy error', err);
          });
          proxy.on('proxyReq', (proxyReq, req, _res) => {
            console.log('Sending Request to the Target:', req.method, req.url);
          });
          proxy.on('proxyRes', (proxyRes, req, _res) => {
            console.log('Received Response from the Target:', proxyRes.statusCode, req.url);
          });
        },
      }
    }
  }
}))
