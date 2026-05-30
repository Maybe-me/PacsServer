<template>
  <div class="series-thumbnail-preview">
    <canvas ref="canvasRef" class="thumbnail-canvas"></canvas>
    <div v-if="loading" class="thumbnail-status">
      <div class="spinner" style="width: 16px; height: 16px; border-width: 2px;"></div>
    </div>
    <div v-if="error" class="thumbnail-status thumbnail-error">
      <span class="preview-modality">{{ modality || '?' }}</span>
    </div>
  </div>
</template>

<script setup>
import { imageLoader } from '@cornerstonejs/core'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { buildWadoImageId } from '../api/viewer'
import { listInstances } from '../api/viewer'
import { initializeCornerstone } from '../lib/cornerstone'

const props = defineProps({
  studyInstanceUid: { type: String, required: true },
  seriesInstanceUid: { type: String, required: true },
  modality: { type: String, default: '' },
})

const canvasRef = ref(null)
const loading = ref(true)
const error = ref(false)
let cancelled = false

function renderToCanvas(canvas, image) {
  const pixelData = image.getPixelData()
  const width = image.width
  const height = image.height
  if (!width || !height || !pixelData?.length) return false

  const ww = Number(image.windowWidth) || (Math.max(...pixelData) - Math.min(...pixelData)) || 256
  const wc = Number(image.windowCenter) || (ww / 2) || 128
  const lower = wc - ww / 2
  const upper = wc + ww / 2
  const range = upper - lower || 1

  const tempCanvas = document.createElement('canvas')
  tempCanvas.width = width
  tempCanvas.height = height
  const tempCtx = tempCanvas.getContext('2d')
  const imageData = tempCtx.createImageData(width, height)
  const data = imageData.data

  for (let i = 0; i < pixelData.length; i++) {
    const v = Math.max(0, Math.min(255, ((pixelData[i] - lower) / range) * 255))
    data[i * 4] = v
    data[i * 4 + 1] = v
    data[i * 4 + 2] = v
    data[i * 4 + 3] = 255
  }
  tempCtx.putImageData(imageData, 0, 0)

  const thumbW = 90
  const thumbH = Math.round(90 * (height / width))
  canvas.width = thumbW
  canvas.height = Math.max(thumbH, 1)
  const ctx = canvas.getContext('2d')
  ctx.imageSmoothingEnabled = true
  ctx.drawImage(tempCanvas, 0, 0, thumbW, thumbH)
  return true
}

onMounted(async () => {
  try {
    const normModality = (props.modality || '').trim().toUpperCase()
    const NON_IMAGE_MODALITIES = new Set(['DOC', 'SR', 'ECG', 'ES'])
    if (NON_IMAGE_MODALITIES.has(normModality)) {
      loading.value = false
      error.value = true // Will display modality text placeholder
      return
    }

    const instances = await listInstances(props.studyInstanceUid, props.seriesInstanceUid)
    if (cancelled || !instances?.length) {
      if (!instances?.length) error.value = true
      loading.value = false
      return
    }
    const instance = instances[0]
    
    // Check if instance actually has a valid wadoUri
    if (!instance.wadoUri) {
      error.value = true
      loading.value = false
      return
    }

    const imageId = buildWadoImageId(instance)
    await initializeCornerstone()
    if (cancelled) { loading.value = false; return }
    const image = await imageLoader.loadAndCacheImage(imageId)
    if (cancelled || !canvasRef.value) { loading.value = false; return }
    const ok = renderToCanvas(canvasRef.value, image)
    if (!ok) error.value = true
  } catch (e) {
    console.warn(`[Thumbnail] ${props.seriesInstanceUid}:`, e)
    error.value = true
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => { cancelled = true })
</script>

<style scoped>
.thumbnail-canvas {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumbnail-status {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumbnail-error {
  font-size: 20px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.15);
  letter-spacing: 1px;
}

.series-thumbnail-preview {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  background-color: #000;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
</style>