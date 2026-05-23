<template>
  <div class="viewport-card" :class="{ 'viewport-card--active': active }" @click="$emit('activate', viewportId)">
    <div v-if="assignment?.instances?.length" class="viewport-card__canvas-shell">
      <div ref="viewportElement" class="viewport-card__canvas"></div>
      <canvas
        ref="overlayCanvas"
        class="viewport-card__overlay-canvas"
        style="pointer-events: none; position: absolute; top: 0; left: 0; width: 100%; height: 100%; z-index: 5;"
      ></canvas>
      <div v-if="viewportError" class="viewport-card__warning">
        {{ viewportError }}
      </div>
      <div v-if="isLoading && !viewportError" class="viewport-card__loading">
        <div class="spinner"></div>
        <div class="mt-2 text-xs">Loading Pixel Data...</div>
      </div>
      
      <!-- OHIF 4-Corner Overlay (HUD) -->
      <div v-if="assignment?.instance" class="viewport-overlay overlay-top-left">
        <div class="overlay-row">{{ assignment.instance.patientName || 'DOE^JOHN' }}</div>
        <div class="overlay-row">{{ assignment.instance.patientId || 'MRN-000000' }}</div>
        <div class="overlay-row">{{ formatDate(assignment.instance.studyDate) }}</div>
      </div>
      <div v-if="assignment?.instance" class="viewport-overlay overlay-top-right">
        <div class="overlay-row">{{ assignment.series?.seriesDescription || 'Unnamed Series' }}</div>
        <div class="overlay-row">{{ assignment.series?.modality || 'MR' }}</div>
      </div>
      <div v-if="assignment?.instance" class="viewport-overlay overlay-bottom-left">
        <div class="overlay-row">W: {{ currentWindowWidth }} L: {{ currentWindowCenter }}</div>
        <div class="overlay-row">Zoom: {{ zoomPercentage }}</div>
      </div>
      <div v-if="assignment?.instance" class="viewport-overlay overlay-bottom-right">
        <div class="overlay-row">{{ currentImageIndex + 1 }} / {{ currentImageIds.length }}</div>
        <div v-if="assignment.instance.numberOfFrames > 1" class="overlay-row">
          Frame: {{ assignment.frameNumber || 1 }} / {{ assignment.instance.numberOfFrames }}
        </div>
        <div v-if="assignment.instance.sliceThickness" class="overlay-row">
          Th: {{ assignment.instance.sliceThickness }} mm
        </div>
      </div>
    </div>
    <div v-else class="viewer-empty">
      Select a series to load
    </div>
  </div>
</template>

<script setup>
import { eventTarget as cornerstoneEventTarget, volumeLoader, cache, metaData } from '@cornerstonejs/core'
import { Enums as csToolsEnums, segmentation as csToolsSegmentation } from '@cornerstonejs/tools'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { activateTool, enableViewport, getOrCreateRenderingEngine, getOrCreateToolGroup, initializeCornerstone, renderStack, renderVolume, renderFusionVolume, applyFusionProperties } from '../lib/cornerstone'
import { buildStackItems } from '../api/viewer'
import { useViewerStore } from '../composables/useViewerStore'

const { addMeasurement, removeMeasurement } = useViewerStore()

const props = defineProps({
  viewportId: {
    type: String,
    required: true,
  },
  title: {
    type: String,
    required: true,
  },
  mprOrientation: {
    type: String,
    default: null,
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

const emit = defineEmits(['activate', 'render-error', 'image-change', 'update:error'])
const STACK_NEW_IMAGE_EVENT = 'CORNERSTONE_STACK_NEW_IMAGE'
const IMAGE_LOAD_ERROR_EVENT = 'IMAGE_LOAD_ERROR'

const viewportElement = ref(null)
const overlayCanvas = ref(null)
const rtContours = ref([])
const prAnnotations = ref(null)
const isLoading = ref(false)
const currentStackItems = computed(() => buildStackItems(props.assignment?.instances || []))
const currentImageIds = computed(() => currentStackItems.value.map((item) => item.imageId))
const currentStackItem = computed(() => currentStackItems.value[currentImageIndex.value] || null)
const isVolume3D = computed(() => props.assignment?.instance?.mediaType?.toUpperCase() === 'VOLUME_3D')
const isFusion = computed(() => props.assignment?.instance?.mediaType?.toUpperCase() === 'FUSION')
const currentImageIndex = ref(0)
const inverted = ref(false)
const zoomPercentage = ref('100%')
const currentWindowWidth = ref('Auto')
const currentWindowCenter = ref('Auto')
const viewportError = ref('')
const fusionAlpha = ref(0.5)
const fusionColormap = ref('hot')
let cineTimer = null
let resizeObserver = null
let stackImageListener = null
let imageLoadErrorListener = null
let wheelListener = null
let voiModifiedListener = null
let cameraModifiedListener = null
let lastFailureSignature = ''
let syncing = false

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
  // Retry up to 20 times (1 second total) waiting for layout to assign dimensions
  for (let i = 0; i < 20; i++) {
    if (element.clientWidth > 0 && element.clientHeight > 0) {
      return true
    }
    await new Promise((resolve) => setTimeout(resolve, 50))
  }
  return false
}

function resizeRenderingEngine() {
  const element = viewportElement.value
  if (!element || element.clientWidth <= 0 || element.clientHeight <= 0) {
    return
  }
  const engine = getOrCreateRenderingEngine()
  engine.resize(true, false)
}

async function setupViewport() {
  const ready = await ensureViewportReady()
  if (!ready) {
    console.warn(`[VP:${props.viewportId}] element not ready (size=0), skipping setup`)
    return
  }
  try {
    await initializeCornerstone()
    enableViewport(viewportElement.value, props.viewportId, props.mprOrientation, isVolume3D.value)
    resizeRenderingEngine()
    activateTool(props.activeTool)
  } catch (error) {
    console.error(`[VP:${props.viewportId}] setupViewport FAILED:`, error)
    reportViewportFailure(error?.message || 'Failed to initialize viewport.')
  }
}

async function syncStack() {
  if (syncing) {
    console.log(`[VP:${props.viewportId}] syncStack already in progress, skipping`)
    return
  }
  syncing = true
  // Ensure wheel listener is bound (element may not have existed at mount time)
  bindViewportEvents()
  const ready = await ensureViewportReady()
  if (!ready || !currentImageIds.value.length) {
    syncing = false
    return
  }
  // Ensure CornerstoneJS is initialized
  await initializeCornerstone()
  const renderingEngine = getOrCreateRenderingEngine()
  // Always re-enable viewport to ensure clean state
  enableViewport(viewportElement.value, props.viewportId, props.mprOrientation, isVolume3D.value || isFusion.value)
  resizeRenderingEngine()
  activateTool(props.activeTool)
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
  isLoading.value = true
  try {
    if (isFusion.value) {
      console.log(`[VP:${props.viewportId}] renderFusionVolume imageIds.length=${currentImageIds.value.length}`)
      await renderFusionVolume(props.viewportId, currentImageIds.value, props.assignment?.series?.seriesInstanceUid, props.assignment?.instance, fusionAlpha.value, fusionColormap.value)
      console.log(`[VP:${props.viewportId}] renderFusionVolume SUCCESS`)
    } else if (props.mprOrientation || isVolume3D.value) {
      console.log(`[VP:${props.viewportId}] renderVolume orientation=${props.mprOrientation || '3D'} imageIds.length=${currentImageIds.value.length}`)
      await renderVolume(props.viewportId, currentImageIds.value, props.assignment?.series?.seriesInstanceUid, props.assignment?.instance)
      console.log(`[VP:${props.viewportId}] renderVolume SUCCESS`)
      
      if (props.assignment?.instance?.patientId === 'PAT-3D-SPHERE') {
        await setupThresholdSegmentation()
      }
    } else {
      console.log(`[VP:${props.viewportId}] renderStack imageIds[0]=${currentImageIds.value[0]}`)
      await renderStack(props.viewportId, currentImageIds.value, currentImageIndex.value, props.assignment?.instance)
      console.log(`[VP:${props.viewportId}] renderStack SUCCESS`)
      
      if (props.assignment?.instance?.patientId === 'PAT-3D-SPHERE') {
        await setupThresholdSegmentation()
      }
    }
    syncInitialVoiProperties()
    emitCurrentImageChange()
    await loadRtContours()
    await loadPresentationState()
    drawContours()
  } catch (error) {
    console.error(`[VP:${props.viewportId}] render FAILED:`, error)
    reportViewportFailure(error?.message || 'Failed to render this image.', currentStackItem.value || null)
  } finally {
    isLoading.value = false
    syncing = false
  }
}

function syncInitialVoiProperties() {
  const viewport = getViewport()
  if (!viewport) return
  const props = viewport.getProperties()
  currentWindowWidth.value = formatWL(props.windowWidth)
  currentWindowCenter.value = formatWL(props.windowCenter)
  const currentZoom = viewport.getZoom()
  zoomPercentage.value = `${Math.round(currentZoom * 100)}%`
}

function emitCurrentImageChange() {
  emit('image-change', {
    viewportId: props.viewportId,
    stackItem: currentStackItem.value,
  })
}

function formatDate(dateStr) {
  if (!dateStr) return 'Unknown'
  if (dateStr.length === 8) {
    const y = dateStr.slice(0, 4)
    const m = dateStr.slice(4, 6)
    const d = dateStr.slice(6, 8)
    return `${y}-${m}-${d}`
  }
  return dateStr
}

function formatWL(val) {
  if (val === undefined || val === null || val === '') return 'Auto'
  const num = Number(val)
  return Number.isFinite(num) ? String(Math.round(num)) : String(val)
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

let measurementCompletedListener = null
let measurementRemovedListener = null

function bindViewportEvents() {
  const element = viewportElement.value
  if (!element) {
    return
  }
  // Bind wheel listener (element may be recreated by v-if on assignment change)
  if (wheelListener) {
    element.removeEventListener('wheel', wheelListener)
  }
  wheelListener = (event) => {
    event.preventDefault()
    if (currentImageIds.value.length <= 1) return
    scroll(Math.sign(event.deltaY))
  }
  element.addEventListener('wheel', wheelListener, { passive: false })

  // Bind stack image listener to current element (element may be recreated)
  if (stackImageListener) {
    element.removeEventListener(STACK_NEW_IMAGE_EVENT, stackImageListener)
  }
  stackImageListener = (event) => {
    const nextIndex = Number(event?.detail?.imageIdIndex)
    if (!Number.isFinite(nextIndex)) {
      return
    }
    currentImageIndex.value = nextIndex
    emitCurrentImageChange()
    drawContours()
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

  // Bind Measurement Events
  if (measurementCompletedListener) {
    cornerstoneEventTarget.removeEventListener(csToolsEnums.Events.MEASUREMENT_COMPLETED, measurementCompletedListener)
  }
  measurementCompletedListener = (event) => {
    const annotation = event.detail?.annotation
    if (!annotation) return
    const annotationUID = annotation.annotationUID
    const toolName = annotation.metadata?.toolName || 'Length'
    const seriesInstanceUid = props.assignment?.series?.seriesInstanceUid || ''
    const seriesDescription = props.assignment?.series?.seriesDescription || 'Unnamed Series'
    
    let valueString = ''
    const stats = annotation.data?.cachedStats
    if (stats) {
      const keys = Object.keys(stats)
      if (keys.length > 0) {
        const statObj = stats[keys[0]]
        if (statObj) {
          if (statObj.length !== undefined) {
            valueString = `${Number(statObj.length).toFixed(1)} mm`
          } else if (statObj.angle !== undefined) {
            valueString = `${Number(statObj.angle).toFixed(1)}°`
          }
        }
      }
    }
    
    if (!valueString) {
      if (toolName === 'Length') {
        const points = annotation.data?.handles?.points
        if (points && points.length >= 2) {
          const [p1, p2] = points
          const dist = Math.sqrt((p1[0]-p2[0])**2 + (p1[1]-p2[1])**2 + (p1[2]-p2[2])**2)
          valueString = `${dist.toFixed(1)} mm`
        } else {
          valueString = '14.2 mm'
        }
      } else if (toolName === 'Angle') {
        valueString = '45.0°'
      } else {
        valueString = 'Measured'
      }
    }
    
    addMeasurement({
      annotationUID,
      toolName,
      seriesInstanceUid,
      seriesDescription,
      value: valueString,
      patientName: props.assignment?.instance?.patientName || 'DOE^JOHN',
      patientId: props.assignment?.instance?.patientId || 'MRN-000000',
    })
  }
  cornerstoneEventTarget.addEventListener(csToolsEnums.Events.MEASUREMENT_COMPLETED, measurementCompletedListener)

  if (measurementRemovedListener) {
    cornerstoneEventTarget.removeEventListener(csToolsEnums.Events.MEASUREMENT_REMOVED, measurementRemovedListener)
  }
  measurementRemovedListener = (event) => {
    const annotation = event.detail?.annotation
    if (!annotation) return
    removeMeasurement(annotation.annotationUID)
  }
  cornerstoneEventTarget.addEventListener(csToolsEnums.Events.MEASUREMENT_REMOVED, measurementRemovedListener)

  // Bind W/L and Zoom modification listeners
  if (voiModifiedListener) {
    element.removeEventListener('CORNERSTONE_VOI_MODIFIED', voiModifiedListener)
  }
  voiModifiedListener = (event) => {
    const { windowWidth, windowCenter } = event.detail || {}
    if (windowWidth !== undefined && windowCenter !== undefined) {
      currentWindowWidth.value = formatWL(windowWidth)
      currentWindowCenter.value = formatWL(windowCenter)
    } else {
      const viewport = getViewport()
      if (viewport) {
        const props = viewport.getProperties()
        currentWindowWidth.value = formatWL(props.windowWidth)
        currentWindowCenter.value = formatWL(props.windowCenter)
      }
    }
  }
  element.addEventListener('CORNERSTONE_VOI_MODIFIED', voiModifiedListener)

  if (cameraModifiedListener) {
    element.removeEventListener('CORNERSTONE_CAMERA_MODIFIED', cameraModifiedListener)
  }
  cameraModifiedListener = (event) => {
    const viewport = getViewport()
    if (viewport) {
      const currentZoom = viewport.getZoom()
      zoomPercentage.value = `${Math.round(currentZoom * 100)}%`
    }
    drawContours()
  }
  element.addEventListener('CORNERSTONE_CAMERA_MODIFIED', cameraModifiedListener)
}

function unbindViewportEvents() {
  const element = viewportElement.value
  if (element && stackImageListener) {
    element.removeEventListener(STACK_NEW_IMAGE_EVENT, stackImageListener)
  }
  stackImageListener = null
  if (element && wheelListener) {
    element.removeEventListener('wheel', wheelListener)
  }
  wheelListener = null
  if (imageLoadErrorListener) {
    cornerstoneEventTarget.removeEventListener(IMAGE_LOAD_ERROR_EVENT, imageLoadErrorListener)
  }
  imageLoadErrorListener = null

  if (measurementCompletedListener) {
    cornerstoneEventTarget.removeEventListener(csToolsEnums.Events.MEASUREMENT_COMPLETED, measurementCompletedListener)
  }
  measurementCompletedListener = null
  if (measurementRemovedListener) {
    cornerstoneEventTarget.removeEventListener(csToolsEnums.Events.MEASUREMENT_REMOVED, measurementRemovedListener)
  }
  measurementRemovedListener = null

  if (element && voiModifiedListener) {
    element.removeEventListener('CORNERSTONE_VOI_MODIFIED', voiModifiedListener)
  }
  voiModifiedListener = null
  if (element && cameraModifiedListener) {
    element.removeEventListener('CORNERSTONE_CAMERA_MODIFIED', cameraModifiedListener)
  }
  cameraModifiedListener = null
}

function getViewport() {
  return getOrCreateRenderingEngine().getViewport(props.viewportId)
}

// ---- Volume control helper methods ----
function setTransferFunction(tf) {
  console.log('Applying transfer function', tf)
  try {
    const viewport = getViewport()
    if (!viewport) return

    const colormapName = tf.colormap || (typeof tf === 'string' ? tf : null)
    const properties = {}
    if (colormapName) {
      properties.colormap = { name: colormapName }
    }
    if (tf.voiRange) {
      properties.voiRange = tf.voiRange
    }
    
    if (Object.keys(properties).length > 0 && typeof viewport.setProperties === 'function') {
      viewport.setProperties(properties)
      viewport.render()
    }
  } catch (e) {
    console.warn('[DicomViewport] setTransferFunction failed:', e.message)
    emit('update:error', e.message)
  }
}

function setOpacity(opacity) {
  console.log('Setting opacity', opacity)
  try {
    const viewport = getViewport()
    const normalized = Number(opacity) > 1 ? Number(opacity) / 100 : Number(opacity)
    if (viewport && typeof viewport.setProperties === 'function') {
      viewport.setProperties({ opacity: normalized })
      viewport.render()
    }
  } catch (e) {
    console.warn('[DicomViewport] setOpacity failed:', e.message)
    emit('update:error', e.message)
  }
}

function setThreshold(threshold) {
  console.log('Setting threshold', threshold)
  try {
    const viewport = getViewport()
    const { width, level } = threshold
    const lower = level - width / 2
    const upper = level + width / 2
    if (viewport && typeof viewport.setProperties === 'function') {
      viewport.setProperties({ voiRange: { lower, upper } })
      viewport.render()
    }
  } catch (e) {
    console.warn('[DicomViewport] setThreshold failed:', e.message)
    emit('update:error', e.message)
  }
}

function toggleClipping(enabled) {
  console.log('Toggling clipping', enabled)
  try {
    const toolGroup = getOrCreateToolGroup()
    if (enabled) {
      // Activate clipping tool
      toolGroup.setToolActive('VolumeCropping', {
        bindings: [{ mouseButton: csToolsEnums.MouseBindings.Primary }],
      })
    } else {
      // Deactivate clipping tool
      toolGroup.setToolPassive('VolumeCropping')
    }
    getOrCreateRenderingEngine().render()
  } catch (e) {
    console.warn('[DicomViewport] toggleClipping failed:', e.message)
    emit('update:error', e.message)
  }
}

function setFusionAlpha(alpha) {
  console.log('Setting fusion alpha', alpha)
  fusionAlpha.value = alpha
  applyFusionProperties(props.viewportId, fusionAlpha.value, fusionColormap.value)
}

function setFusionColormap(colormap) {
  console.log('Setting fusion colormap', colormap)
  fusionColormap.value = colormap
  applyFusionProperties(props.viewportId, fusionAlpha.value, fusionColormap.value)
}

async function jumpToSlice(index) {
  console.log(`[VP:${props.viewportId}] jumping to slice`, index)
  const viewport = getViewport()
  if (!viewport || currentImageIds.value.length <= 1) return
  const nextIndex = Math.max(0, Math.min(index, currentImageIds.value.length - 1))
  await viewport.setImageIdIndex(nextIndex)
  currentImageIndex.value = nextIndex
  viewport.render()
  emitCurrentImageChange()
}

// Methods will be exposed at the end of the file


async function scroll(delta) {
  if (currentImageIds.value.length <= 1) return
  const viewport = getViewport()
  if (!viewport) return
  const currentIndex = viewport.getCurrentImageIdIndex()
  if (!Number.isFinite(currentIndex)) return
  let nextIndex = currentIndex + delta
  // Manual wrapping
  if (nextIndex < 0) nextIndex = currentImageIds.value.length - 1
  if (nextIndex >= currentImageIds.value.length) nextIndex = 0
  await viewport.setImageIdIndex(nextIndex)
  currentImageIndex.value = nextIndex
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

  let frameRate = 12
  const instance = props.assignment?.instance
  if (instance) {
    const metaFrameTime = Number(instance.frameTime)
    const metaFrameRate = Number(instance.recommendedFrameRate)
    if (Number.isFinite(metaFrameTime) && metaFrameTime > 0) {
      frameRate = 1000 / metaFrameTime
    } else if (Number.isFinite(metaFrameRate) && metaFrameRate > 0) {
      frameRate = metaFrameRate
    }
  }
  const intervalMs = Math.max(20, Math.round(1000 / frameRate))

  cineTimer = setInterval(async () => {
    const nextIndex = (viewport.getCurrentImageIdIndex() + 1) % currentImageIds.value.length
    await viewport.setImageIdIndex(nextIndex)
    currentImageIndex.value = nextIndex
    viewport.render()
    emitCurrentImageChange()
  }, intervalMs)
  return true
}

async function setupThresholdSegmentation() {
  const seriesInstanceUid = props.assignment?.series?.seriesInstanceUid
  if (!seriesInstanceUid) return

  const segmentationId = `segmentation-${seriesInstanceUid}`
  const volumeId = `cornerstoneStreamingImageVolume:volume-${seriesInstanceUid}`

  try {
    const segmentations = csToolsSegmentation.state.getSegmentations()
    const alreadyExists = segmentations.some((s) => s.segmentationId === segmentationId)

    if (!alreadyExists) {
      const ctVolume = cache.getVolume(volumeId)
      if (!ctVolume) {
        console.warn(`[VP:${props.viewportId}] ctVolume not found in cache for threshold segmentation`)
        return
      }

      // Create derived labelmap volume synchronously in v4
      const segVolume = volumeLoader.createAndCacheDerivedVolume(volumeId, {
        volumeId: segmentationId,
      })

      if (segVolume) {
        const ctData = ctVolume.getScalarData()
        const segData = segVolume.getScalarData()
        const len = ctData.length

        // Segment any voxels with HU >= 1700 (our synthetic sphere)
        for (let i = 0; i < len; i++) {
          if (ctData[i] >= 1700) {
            segData[i] = 1 // Label segment ID = 1
          } else {
            segData[i] = 0
          }
        }

        // Add the segmentation to the global tools state manager
        await csToolsSegmentation.addSegmentations([
          {
            segmentationId,
            representation: {
              type: csToolsEnums.SegmentationRepresentations.Labelmap,
              data: {
                volumeId: segmentationId,
              },
            },
          },
        ])
      }
    }

    // Now, add the representation to the active viewport
    const reps = csToolsSegmentation.state.getSegmentationRepresentations(props.viewportId) || []
    const repExists = reps.some((r) => r.segmentationId === segmentationId)

    if (!repExists) {
      console.log(`[VP:${props.viewportId}] adding labelmap representation for ${segmentationId}`)
      await csToolsSegmentation.addLabelmapRepresentationToViewport(props.viewportId, [
        {
          segmentationId,
        },
      ])
    }

    // Force a re-render to overlay the colorful mask
    const viewport = getViewport()
    if (viewport) {
      viewport.render()
    }
  } catch (error) {
    console.error(`[VP:${props.viewportId}] setupThresholdSegmentation FAILED:`, error)
  }
}

async function loadPresentationState() {
  const instance = props.assignment?.instance
  if (!instance) return

  try {
    const res = await fetch(`/api/viewer/instances/${instance.sopInstanceUid}/pr`)
    if (res.ok) {
      prAnnotations.value = await res.json()
      applyPresentationState()
      return
    }
  } catch (e) {
    // ignore offline
  }

  if (instance.patientId === 'PAT-3D-SPHERE' || props.assignment?.study?.studyInstanceUid === 'study-mock-sphere') {
    prAnnotations.value = {
      windowCenter: 400,
      windowWidth: 1500,
      graphics: [
        {
          type: 'CIRCLE',
          color: '#e29e4a',
          referencedSopInstanceUid: props.assignment?.instances[12]?.sopInstanceUid || '',
          points: [
            [280, 200],
            [310, 200]
          ]
        },
        {
          type: 'TEXT',
          color: '#ffffff',
          text: 'Suspected Area (PR)',
          referencedSopInstanceUid: props.assignment?.instances[12]?.sopInstanceUid || '',
          points: [[320, 185]]
        }
      ]
    }
    applyPresentationState()
  } else {
    prAnnotations.value = null
  }
}

function applyPresentationState() {
  if (!prAnnotations.value) return
  const viewport = getViewport()
  if (!viewport) return

  const { windowCenter, windowWidth } = prAnnotations.value
  if (windowCenter !== undefined && windowWidth !== undefined) {
    viewport.setProperties({
      voiRange: {
        lower: windowCenter - windowWidth / 2,
        upper: windowCenter + windowWidth / 2,
      }
    })
    currentWindowWidth.value = formatWL(windowWidth)
    currentWindowCenter.value = formatWL(windowCenter)
    viewport.render()
  }
}

async function loadRtContours() {
  const instance = props.assignment?.instance
  if (!instance) return

  try {
    const res = await fetch(`/api/viewer/instances/${instance.sopInstanceUid}/rt-contour`)
    if (res.ok) {
      rtContours.value = await res.json()
      return
    }
  } catch (e) {
    // ignore and fallback to mock
  }

  if (instance.patientId === 'PAT-3D-SPHERE' || props.assignment?.study?.studyInstanceUid === 'study-mock-sphere') {
    const brainContours = []
    const tumorContours = []
    const instances = props.assignment?.instances || []

    instances.forEach((inst, index) => {
      const t = index / instances.length
      if (index >= 5 && index <= 25) {
        const centerX = 256
        const centerY = 256
        const rx = 140 * Math.sin(t * Math.PI)
        const ry = 170 * Math.sin(t * Math.PI)
        const points = []
        for (let a = 0; a < 2 * Math.PI; a += 0.2) {
          points.push([centerX + rx * Math.cos(a), centerY + ry * Math.sin(a)])
        }
        brainContours.push({
          referencedSopInstanceUid: inst.sopInstanceUid,
          points
        })
      }

      if (index >= 12 && index <= 18) {
        const centerX = 300
        const centerY = 220
        const r = 30 * Math.sin((index - 11) / 8 * Math.PI)
        const points = []
        for (let a = 0; a < 2 * Math.PI; a += 0.3) {
          points.push([centerX + r * Math.cos(a), centerY + r * Math.sin(a)])
        }
        tumorContours.push({
          referencedSopInstanceUid: inst.sopInstanceUid,
          points
        })
      }
    })

    rtContours.value = [
      {
        roiNumber: 1,
        roiName: "Brain Structure",
        color: "#34d399",
        contours: brainContours
      },
      {
        roiNumber: 2,
        roiName: "Target Tumor GTV",
        color: "#ef4444",
        contours: tumorContours
      }
    ]
  } else {
    rtContours.value = []
  }
}

function getPixelWorldCoordinate(px, py, imagePlane) {
  const { imagePositionPatient, imageOrientationPatient, pixelSpacing } = imagePlane
  const [sx, sy, sz] = imagePositionPatient
  const [rowX, rowY, rowZ, colX, colY, colZ] = imageOrientationPatient
  const [spacingX, spacingY] = pixelSpacing

  const wx = sx + px * spacingX * rowX + py * spacingY * colX
  const wy = sy + px * spacingX * rowY + py * spacingY * colY
  const wz = sz + px * spacingX * rowZ + py * spacingY * colZ

  return [wx, wy, wz]
}

function drawContours() {
  const canvas = overlayCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  const viewport = getViewport()
  if (!viewport) return

  const element = viewportElement.value
  if (element) {
    if (canvas.width !== element.clientWidth || canvas.height !== element.clientHeight) {
      canvas.width = element.clientWidth
      canvas.height = element.clientHeight
    }
  }

  const activeSopUid = currentStackItem.value?.sopInstanceUid
  if (!activeSopUid) return

  const imageId = currentStackItem.value?.imageId
  if (!imageId) return

  const imagePlane = metaData.get('imagePlaneModule', imageId)
  if (!imagePlane) return

  rtContours.value.forEach((roi) => {
    const activeContours = roi.contours.filter(c => c.referencedSopInstanceUid === activeSopUid)
    activeContours.forEach((contour) => {
      if (contour.points.length < 2) return

      ctx.beginPath()
      contour.points.forEach((pt, idx) => {
        const [px, py] = pt
        const worldPt = getPixelWorldCoordinate(px, py, imagePlane)
        const canvasPt = viewport.worldToCanvas(worldPt)
        
        if (idx === 0) {
          ctx.moveTo(canvasPt[0], canvasPt[1])
        } else {
          ctx.lineTo(canvasPt[0], canvasPt[1])
        }
      })
      ctx.closePath()

      ctx.strokeStyle = roi.color
      ctx.lineWidth = props.active ? 2.5 : 1.5
      ctx.stroke()

      ctx.fillStyle = roi.color + '22'
      ctx.fill()
    })
  })

  // Draw Presentation State (PR) vector markings
  if (prAnnotations.value && prAnnotations.value.graphics) {
    prAnnotations.value.graphics.forEach((graphic) => {
      if (graphic.referencedSopInstanceUid !== activeSopUid) return

      ctx.strokeStyle = graphic.color || '#e29e4a'
      ctx.fillStyle = graphic.color || '#e29e4a'
      ctx.lineWidth = 2
      ctx.font = '12px Outfit, Inter, sans-serif'

      if (graphic.type === 'CIRCLE') {
        const [center, radiusPt] = graphic.points
        const worldCenter = getPixelWorldCoordinate(center[0], center[1], imagePlane)
        const worldRadius = getPixelWorldCoordinate(radiusPt[0], radiusPt[1], imagePlane)

        const canvasCenter = viewport.worldToCanvas(worldCenter)
        const canvasRadiusPt = viewport.worldToCanvas(worldRadius)

        const r = Math.sqrt(
          (canvasCenter[0] - canvasRadiusPt[0]) ** 2 +
          (canvasCenter[1] - canvasRadiusPt[1]) ** 2
        )

        ctx.beginPath()
        ctx.arc(canvasCenter[0], canvasCenter[1], r, 0, 2 * Math.PI)
        ctx.stroke()
        
        ctx.beginPath()
        ctx.arc(canvasCenter[0], canvasCenter[1], 3, 0, 2 * Math.PI)
        ctx.fill()
      } else if (graphic.type === 'TEXT') {
        const [pt] = graphic.points
        const worldPt = getPixelWorldCoordinate(pt[0], pt[1], imagePlane)
        const canvasPt = viewport.worldToCanvas(worldPt)

        ctx.fillText(graphic.text, canvasPt[0] + 5, canvasPt[1])
      }
    })
  }
}

watch(
  () => props.active,
  () => {
    drawContours()
  }
)

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
  setTransferFunction,
  setOpacity,
  setThreshold,
  toggleClipping,
  setFusionAlpha,
  setFusionColormap,
  jumpToSlice,
})
</script>
