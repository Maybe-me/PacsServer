<template>
  <div class="viewport-card" :class="{ 'viewport-card--active': active }" @click="$emit('activate', viewportId)">
    <div class="viewport-card__header">
      <div>
        <strong>{{ title }}</strong>
        <div v-if="assignment?.series" class="viewport-card__subtle">
          {{ assignment.series.modality || 'N/A' }} · {{ assignment.series.seriesDescription || 'Unnamed series' }}
        </div>
      </div>
      <a-tag :color="active ? 'processing' : 'default'">{{ active ? 'Active' : 'Idle' }}</a-tag>
    </div>

    <div v-if="assignment?.instances?.length" class="viewport-card__canvas-shell">
      <div ref="viewportElement" class="viewport-card__canvas"></div>
      <div v-if="viewportError" class="viewport-card__warning">
        {{ viewportError }}
      </div>
    </div>
    <div v-else class="viewer-empty viewer-empty--dark">
      Select a series or instance to load this viewport.
    </div>

    <div v-if="assignment?.instance" class="viewport-card__overlay">
      <span>{{ currentImageIndex + 1 }}/{{ currentImageIds.length }}</span>
      <span v-if="currentStackItem?.instance?.numberOfFrames > 1">
        Frame {{ currentStackItem.frameNumber }}/{{ currentStackItem.instance.numberOfFrames }}
      </span>
      <span>{{ assignment.instance.sopClassUid || 'Unknown SOP Class' }}</span>
    </div>
  </div>
</template>

<script setup>
import { eventTarget as cornerstoneEventTarget } from '@cornerstonejs/core'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { activateTool, enableViewport, getOrCreateRenderingEngine, initializeCornerstone, renderStack } from '../lib/cornerstone'
import { buildStackItems } from '../api/viewer'

const props = defineProps({
  viewportId: {
    type: String,
    required: true,
  },
  title: {
    type: String,
    required: true,
  },
  assignment: {
    type: Object,
    default: null,
  },
  active: {
    type: Boolean,
    default: false,
  },
  activeTool: {
    type: String,
    required: true,
  },
})

const emit = defineEmits(['activate', 'render-error', 'image-change'])
const STACK_NEW_IMAGE_EVENT = 'CORNERSTONE_STACK_NEW_IMAGE'
const IMAGE_LOAD_ERROR_EVENT = 'IMAGE_LOAD_ERROR'

const viewportElement = ref(null)
const currentStackItems = computed(() => buildStackItems(props.assignment?.instances || []))
const currentImageIds = computed(() => currentStackItems.value.map((item) => item.imageId))
const currentStackItem = computed(() => currentStackItems.value[currentImageIndex.value] || null)
const currentImageIndex = ref(0)
const inverted = ref(false)
const viewportError = ref('')
let cineTimer = null
let resizeObserver = null
let stackImageListener = null
let imageLoadErrorListener = null
let lastFailureSignature = ''

function waitForFrame() {
  return new Promise((resolve) => requestAnimationFrame(() => resolve()))
}

async function ensureViewportReady() {
  await nextTick()
  await waitForFrame()
  const element = viewportElement.value
  if (!element) {
    return false
  }
  return element.clientWidth > 0 && element.clientHeight > 0
}

function resizeRenderingEngine() {
  const element = viewportElement.value
  if (!element || element.clientWidth <= 0 || element.clientHeight <= 0) {
    return
  }
  getOrCreateRenderingEngine().resize(true, false)
}

async function setupViewport() {
  const ready = await ensureViewportReady()
  if (!ready) {
    return
  }
  await initializeCornerstone()
  enableViewport(viewportElement.value, props.viewportId)
  resizeRenderingEngine()
  activateTool(props.activeTool)
}

async function syncStack() {
  const ready = await ensureViewportReady()
  if (!ready || !currentImageIds.value.length) {
    return
  }
  const selectedIndex = currentStackItems.value.findIndex(
    (item) =>
      item.sopInstanceUid === props.assignment?.instance?.sopInstanceUid &&
      item.frameNumber === (props.assignment?.frameNumber || 1)
  )
  const fallbackIndex = currentStackItems.value.findIndex(
    (item) => item.sopInstanceUid === props.assignment?.instance?.sopInstanceUid
  )
  currentImageIndex.value = selectedIndex >= 0 ? selectedIndex : fallbackIndex >= 0 ? fallbackIndex : 0
  viewportError.value = ''
  try {
    await renderStack(props.viewportId, currentImageIds.value, currentImageIndex.value, props.assignment?.instance)
    emitCurrentImageChange()
  } catch (error) {
    reportViewportFailure(error?.message || 'Failed to render this image.', currentStackItem.value || null)
  }
}

function emitCurrentImageChange() {
  emit('image-change', {
    viewportId: props.viewportId,
    stackItem: currentStackItem.value,
  })
}

function resolveStackItem(detail) {
  const imageId = detail?.imageId
  if (imageId) {
    return currentStackItems.value.find((item) => item.imageId === imageId) || null
  }
  const imageIdIndex = Number(detail?.imageIdIndex)
  if (Number.isFinite(imageIdIndex) && imageIdIndex >= 0) {
    return currentStackItems.value[imageIdIndex] || null
  }
  return null
}

function reportViewportFailure(message, stackItem = null) {
  const targetStackItem = stackItem || currentStackItem.value || null
  const normalizedMessage = `${message || 'Failed to render this image.'}`.trim()
  const signature = [
    props.viewportId,
    targetStackItem?.imageId || props.assignment?.instance?.sopInstanceUid || '',
    normalizedMessage,
  ].join('|')
  if (signature === lastFailureSignature) {
    return
  }
  lastFailureSignature = signature
  viewportError.value = normalizedMessage
  emit('render-error', {
    viewportId: props.viewportId,
    instance: targetStackItem?.instance || props.assignment?.instance,
    frameNumber: targetStackItem?.frameNumber || 1,
    message: normalizedMessage,
  })
}

function bindViewportEvents() {
  const element = viewportElement.value
  if (!element || stackImageListener || imageLoadErrorListener) {
    return
  }
  stackImageListener = (event) => {
    const nextIndex = Number(event?.detail?.imageIdIndex)
    if (!Number.isFinite(nextIndex)) {
      return
    }
    currentImageIndex.value = nextIndex
    emitCurrentImageChange()
  }
  element.addEventListener(STACK_NEW_IMAGE_EVENT, stackImageListener)
  imageLoadErrorListener = (event) => {
    const detail = Array.isArray(event?.detail) ? event.detail.find((item) => item?.reason?.imageId || item?.imageId) : event?.detail
    const resolvedDetail = detail?.reason || detail
    const stackItem = resolveStackItem(resolvedDetail)
    if (!stackItem) {
      return
    }
    reportViewportFailure(resolvedDetail?.error?.message || resolvedDetail?.error || 'Failed to load image data.', stackItem)
  }
  cornerstoneEventTarget.addEventListener(IMAGE_LOAD_ERROR_EVENT, imageLoadErrorListener)
}

function unbindViewportEvents() {
  const element = viewportElement.value
  if (element && stackImageListener) {
    element.removeEventListener(STACK_NEW_IMAGE_EVENT, stackImageListener)
  }
  stackImageListener = null
  if (imageLoadErrorListener) {
    cornerstoneEventTarget.removeEventListener(IMAGE_LOAD_ERROR_EVENT, imageLoadErrorListener)
  }
  imageLoadErrorListener = null
}

function getViewport() {
  return getOrCreateRenderingEngine().getViewport(props.viewportId)
}

function scroll(delta) {
  const viewport = getViewport()
  viewport?.scroll(delta, true, false)
  currentImageIndex.value = viewport?.getCurrentImageIdIndex?.() ?? currentImageIndex.value
  emitCurrentImageChange()
}

function toggleInvert() {
  const viewport = getViewport()
  if (!viewport) {
    return
  }
  inverted.value = !inverted.value
  viewport.setProperties({ invert: inverted.value })
  viewport.render()
}

function resetViewport() {
  const viewport = getViewport()
  if (!viewport) {
    return
  }
  inverted.value = false
  viewport.resetProperties()
  viewport.render()
}

function toggleCine() {
  const viewport = getViewport()
  if (!viewport || currentImageIds.value.length <= 1) {
    return
  }
  if (cineTimer) {
    clearInterval(cineTimer)
    cineTimer = null
    return false
  }
  cineTimer = setInterval(async () => {
    const nextIndex = (viewport.getCurrentImageIdIndex() + 1) % currentImageIds.value.length
    await viewport.setImageIdIndex(nextIndex)
    currentImageIndex.value = nextIndex
    viewport.render()
    emitCurrentImageChange()
  }, 1000 / 12)
  return true
}

watch(
  () => props.activeTool,
  (toolName) => {
    activateTool(toolName)
  }
)

watch(
  () => [
    props.assignment?.instance?.sopInstanceUid,
    props.assignment?.frameNumber,
    props.assignment?.instances?.length,
    (props.assignment?.instances || [])
      .map((item) => `${item.sopInstanceUid}:${item.displayable === false ? 1 : 0}:${(item.failedFrames || []).join(',')}`)
      .join('|'),
  ],
  async () => {
    lastFailureSignature = ''
    await nextTick()
    await setupViewport()
    await syncStack()
  }
)

onMounted(async () => {
  resizeObserver = new ResizeObserver(() => {
    resizeRenderingEngine()
  })
  if (viewportElement.value) {
    resizeObserver.observe(viewportElement.value)
  }
  bindViewportEvents()
  await setupViewport()
  await syncStack()
})

onBeforeUnmount(() => {
  if (cineTimer) {
    clearInterval(cineTimer)
    cineTimer = null
  }
  resizeObserver?.disconnect()
  resizeObserver = null
  unbindViewportEvents()
})

defineExpose({
  scroll,
  toggleInvert,
  resetViewport,
  toggleCine,
})
</script>
