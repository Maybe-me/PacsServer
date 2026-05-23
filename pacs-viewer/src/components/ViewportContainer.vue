<template>
  <div class="viewport-container" :class="{ 'viewport-container--active': active }" @click="$emit('activate', viewportId)">
    <!-- Active State Overlay Border (OHIF v3 Style) -->
    <div v-if="active" class="active-border-indicator"></div>
    
    <div v-if="assignment?.instance" class="viewport-content-wrapper">
      <!-- 1. Encapsulated PDF Viewport -->
      <PdfViewport
        v-if="viewportType === 'PDF'"
        :viewport-id="viewportId"
        :assignment="assignment"
        :active="active"
        @render-error="handleRenderError"
      />

      <!-- 2. Encapsulated/Multi-frame Video Viewport -->
      <VideoViewport
        v-else-if="viewportType === 'VIDEO'"
        :viewport-id="viewportId"
        :assignment="assignment"
        :active="active"
        @render-error="handleRenderError"
      />

      <!-- 3. ECG Waveform Viewport -->
      <WaveformViewport
        v-else-if="viewportType === 'WAVEFORM'"
        :viewport-id="viewportId"
        :assignment="assignment"
        :active="active"
        @render-error="handleRenderError"
      />

      <!-- 4. Structured Report (SR) Viewport -->
      <SrViewport
        v-else-if="viewportType === 'SR'"
        :viewport-id="viewportId"
        :assignment="assignment"
        :active="active"
        @render-error="handleRenderError"
        @node-click="(data) => $emit('sr-node-click', data)"
      />

      <!-- 5. Default Standard DICOM Viewport (Cornerstone2D/MPR/3D/Fusion) -->
      <DicomViewport
        v-else
        :ref="(el) => setDicomViewportRef(el)"
        :viewport-id="viewportId"
        :title="title"
        :mpr-orientation="mprOrientation"
        :assignment="assignment"
        :active="active"
        :active-tool="activeTool"
        @activate="$emit('activate', viewportId)"
        @image-change="handleImageChange"
        @render-error="handleViewportError"
      />
      <!-- ThreeDControls for volume rendering -->
      <ThreeDControls
        v-if="viewportType === 'VOLUME_3D'"
        @update:transferFunction="handleTransferFunction"
        @update:opacity="handleOpacity"
        @update:threshold="handleThreshold"
        @toggleClipping="handleClipping"
      />
      <!-- FusionControls for multi-modality fusion rendering -->
      <FusionControls
        v-if="viewportType === 'FUSION'"
        @update:alpha="handleFusionAlpha"
        @update:colormap="handleFusionColormap"
      />
    </div>
    
    <!-- Empty State -->
    <div v-else class="viewer-empty">
      Select a series to load
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useToast } from '../composables/useToast'
import ThreeDControls from './ThreeDControls.vue'
import FusionControls from './FusionControls.vue'
import DicomViewport from './DicomViewport.vue'
import PdfViewport from './viewports/PdfViewport.vue'
import VideoViewport from './viewports/VideoViewport.vue'
import WaveformViewport from './viewports/WaveformViewport.vue'
import SrViewport from './viewports/SrViewport.vue'

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

const emit = defineEmits(['activate', 'render-error', 'image-change', 'sr-node-click'])

const dicomViewportRef = ref(null)

function setDicomViewportRef(el) { // store ref for DICOM viewport
  dicomViewportRef.value = el
}

// Handlers for ThreeDControls events
function handleTransferFunction(event) {
  console.log('Transfer function update', event)
  if (dicomViewportRef.value?.setTransferFunction) {
    dicomViewportRef.value.setTransferFunction(event)
  }
}
function handleOpacity(event) {
  console.log('Opacity update', event)
  if (dicomViewportRef.value?.setOpacity) {
    dicomViewportRef.value.setOpacity(event)
  }
}
function handleThreshold(event) {
  console.log('Threshold update', event)
  if (dicomViewportRef.value?.setThreshold) {
    dicomViewportRef.value.setThreshold(event)
  }
}
function handleClipping(event) {
  console.log('Clipping toggle', event)
  if (dicomViewportRef.value?.toggleClipping) {
    dicomViewportRef.value.toggleClipping(event)
  }
}

// Handlers for FusionControls events
function handleFusionAlpha(alpha) {
  console.log('Fusion alpha update', alpha)
  if (dicomViewportRef.value?.setFusionAlpha) {
    dicomViewportRef.value.setFusionAlpha(alpha)
  }
}
function handleFusionColormap(colormap) {
  console.log('Fusion colormap update', colormap)
  if (dicomViewportRef.value?.setFusionColormap) {
    dicomViewportRef.value.setFusionColormap(colormap)
  }
}

// Route and decide which specialized viewport to display
const viewportType = computed(() => {
  const instance = props.assignment?.instance
  const series = props.assignment?.series
  if (!instance) return 'NONE'

  // 1. Explicit MediaType sent from backend contract
  if (instance.mediaType) {
    const mt = instance.mediaType.toUpperCase()
    if (mt === 'VOLUME_3D') return 'VOLUME_3D'
    if (mt === 'FUSION') return 'FUSION'
    return mt
  }

  const sopClassUid = (instance.sopClassUid || '').trim()
  const modality = (series?.modality || instance.modality || '').trim().toUpperCase()

  // 2. Encapsulated PDF SOP Class UID
  if (sopClassUid === '1.2.840.10008.5.1.4.1.1.104.1' || modality === 'DOC') {
    return 'PDF'
  }

  // 3. Encapsulated Video SOP Class UIDs or common multi-frame video modalities
  const videoSopClasses = new Set([
    '1.2.840.10008.5.1.4.1.1.77.1.4.1',
    '1.2.840.10008.5.1.4.1.1.77.1.1.1',
    '1.2.840.10008.5.1.4.1.1.77.1.2.1',
  ])
  if (videoSopClasses.has(sopClassUid) || modality === 'ES' || modality === 'US_VIDEO') {
    return 'VIDEO'
  }

  // 4. Waveform Modalities (ECG)
  const waveformSopClasses = new Set([
    '1.2.840.10008.5.1.4.1.1.9.1.1',
    '1.2.840.10008.5.1.4.1.1.9.1.2',
    '1.2.840.10008.5.1.4.1.1.9.1.3',
    '1.2.840.10008.5.1.4.1.1.9.2.1',
  ])
  if (waveformSopClasses.has(sopClassUid) || modality === 'ECG') {
    return 'WAVEFORM'
  }

  // 5. Structured Reports (SR)
  const srSopClasses = new Set([
    '1.2.840.10008.5.1.4.1.1.88.11',
    '1.2.840.10008.5.1.4.1.1.88.22',
    '1.2.840.10008.5.1.4.1.1.88.33',
    '1.2.840.10008.5.1.4.1.1.88.34',
  ])
  if (srSopClasses.has(sopClassUid) || modality === 'SR') {
    return 'SR'
  }

  // 6. Default to standard pixel viewports
  return 'DICOM'
})

function handleImageChange(data) {
  emit('image-change', data)
}

function handleViewportError(errorMsg) {
  const { add } = useToast()
  add(errorMsg, 'error')
}

function handleRenderError(error) {
  emit('render-error', error)
}

// Forward action triggers to the nested standard DICOM viewport if active
function scroll(delta) {
  if (dicomViewportRef.value?.scroll) {
    dicomViewportRef.value.scroll(delta)
  }
}

function toggleInvert() {
  if (dicomViewportRef.value?.toggleInvert) {
    dicomViewportRef.value.toggleInvert()
  }
}

function resetViewport() {
  if (dicomViewportRef.value?.resetViewport) {
    dicomViewportRef.value.resetViewport()
  }
}

function toggleCine() {
  if (dicomViewportRef.value?.toggleCine) {
    return dicomViewportRef.value.toggleCine()
  }
  return false
}

function jumpToSlice(index) {
  if (dicomViewportRef.value?.jumpToSlice) {
    dicomViewportRef.value.jumpToSlice(index)
  }
}

// Expose internal methods to the Parent Grid stage
defineExpose({
  scroll,
  toggleInvert,
  resetViewport,
  toggleCine,
  jumpToSlice,
})
</script>

<style scoped>
.viewport-container {
  position: relative;
  width: 100%;
  height: 100%;
  background-color: #000000;
  overflow: hidden;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.viewport-content-wrapper {
  width: 100%;
  height: 100%;
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
}

/* OHIF v3 High-contrast active border indicator */
.active-border-indicator {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  border: 2px solid #00A4D9;
  pointer-events: none;
  z-index: 10;
}

.viewer-empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  font-size: 13px;
  user-select: none;
}
</style>
