import { fileURLToPath, URL } from 'node:url'
// vitest/config rather than vite/config so the `test` block below is actually type-checked.
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // The dev server proxies /api so the browser talks to one origin in development too.
    // It keeps dev and the Docker setup behaving the same way.
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND_URL ?? 'http://localhost:8080',
        changeOrigin: true,
        // changeOrigin rewrites Host but forwards the browser's Origin header untouched, so the
        // backend saw "Origin: http://localhost:5175" and refused it as cross-origin the moment
        // the dev server ran on any port but the one in its allowlist. Through the proxy the
        // request is same-origin from the browser's point of view; make it look that way.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.spec.ts'],
  },
})
