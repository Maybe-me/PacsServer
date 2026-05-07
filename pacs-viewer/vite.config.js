import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      globalthis: fileURLToPath(new URL('./src/shims/globalthis.js', import.meta.url)),
    },
  },
  optimizeDeps: {
    include: [
      '@cornerstonejs/core',
      '@cornerstonejs/tools',
      '@kitware/vtk.js',
      'dicom-parser',
      'fast-deep-equal',
      '@cornerstonejs/codec-libjpeg-turbo-8bit/decodewasmjs',
      '@cornerstonejs/codec-openjpeg/decodewasmjs',
      '@cornerstonejs/codec-charls/decodewasmjs',
      '@cornerstonejs/codec-openjph/wasmjs',
    ],
    needsInterop: [
      'dicom-parser',
      'fast-deep-equal',
      '@cornerstonejs/codec-libjpeg-turbo-8bit/decodewasmjs',
      '@cornerstonejs/codec-openjpeg/decodewasmjs',
      '@cornerstonejs/codec-charls/decodewasmjs',
      '@cornerstonejs/codec-openjph/wasmjs',
    ],
    exclude: [
      '@cornerstonejs/dicom-image-loader',
    ],
  },
  worker: {
    format: 'es',
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('@cornerstonejs') || id.includes('dicom-parser')) {
            return 'cornerstone'
          }
          if (id.includes('ant-design-vue') || id.includes('@ant-design')) {
            return 'ant-design'
          }
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/qido-rs': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/wado-rs': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
