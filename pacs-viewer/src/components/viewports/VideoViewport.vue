<template>
  <div class="video-viewport">
    <div v-if="isLoading" class="video-loading">
      <div class="spinner"></div>
      <div class="mt-2 text-xs text-muted">Loading Encapsulated Video...</div>
    </div>

    <div v-else-if="videoError" class="video-error">
      <svg class="w-8 h-8 text-danger mb-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      <div class="text-sm font-semibold">{{ videoError }}</div>
    </div>

    <!-- HTML5 Video Player -->
    <div v-show="!isLoading && !videoError" class="video-wrapper">
      <video
        ref="videoElement"
        class="video-player"
        controls
        playsinline
        @play="onPlayStateChange(true)"
        @pause="onPlayStateChange(false)"
        @error="onVideoError"
      >
        <source v-if="videoUrl" :src="videoUrl" type="video/mp4" />
        Your browser does not support the video tag.
      </video>
    </div>

    <!-- HUD Overlay -->
    <div class="viewport-overlay overlay-top-left">
      <div class="overlay-row">{{ assignment.instance?.patientName || 'DOE^JOHN' }}</div>
      <div class="overlay-row">{{ assignment.instance?.patientId || 'MRN-000000' }}</div>
      <div class="overlay-row">{{ formatDate(assignment.instance?.studyDate) }}</div>
    </div>

    <div class="viewport-overlay overlay-top-right">
      <div class="overlay-row">{{ assignment.series?.seriesDescription || 'Endoscopy Video' }}</div>
      <div class="overlay-row font-bold text-accent">VIDEO INSTANCE</div>
    </div>

    <div class="viewport-overlay overlay-bottom-left">
      <div class="overlay-row">Duration: {{ videoDuration }}s</div>
      <div class="overlay-row">FPS: 25</div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useViewerStore } from '../../composables/useViewerStore'

const props = defineProps({
  viewportId: {
    type: String,
    required: true,
  },
  assignment: {
    type: Object,
    required: true,
  },
  active: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['render-error'])

const { setCinePlaying } = useViewerStore()

const videoElement = ref(null)
const isLoading = ref(true)
const videoError = ref('')
const videoUrl = ref('')
const videoDuration = ref('0.0')

function onPlayStateChange(isPlaying) {
  if (props.active) {
    setCinePlaying(isPlaying)
  }
}

function onVideoError() {
  console.error('[Video Viewport] Native Video element error occurred');
  videoError.value = 'Failed to play video. Unsupported codec or stream error.'
  isLoading.value = false
}

async function loadVideo() {
  isLoading.value = true
  videoError.value = ''
  videoUrl.value = ''
  
  const instance = props.assignment?.instance
  if (!instance) {
    videoError.value = 'No instance assignment found.'
    isLoading.value = false
    return
  }

  try {
    const sopInstanceUid = instance.sopInstanceUid

    // Mock handling
    if (instance.patientId === 'PAT-MOCK-VIDEO' || sopInstanceUid?.includes('mock-video') || !sopInstanceUid) {
      videoUrl.value = '/assets/mocks/test.mp4'
      isLoading.value = false
      
      // Delay video initialization to next frame
      setTimeout(() => {
        setupVideoMeta()
      }, 100)
      return
    }

    // Direct fetch from backend contract API (supports Range Requests)
    const targetUrl = `/api/viewer/instances/${sopInstanceUid}/video`
    
    // HEAD request check
    const response = await fetch(targetUrl, { method: 'HEAD' })
    if (response.ok) {
      videoUrl.value = targetUrl
    } else {
      console.warn(`[Video Viewport] Backend API ${targetUrl} returned ${response.status}. Falling back to demo Video.`);
      videoUrl.value = '/assets/mocks/test.mp4'
    }
  } catch (error) {
    console.error('[Video Viewport] Error fetching video:', error)
    videoUrl.value = '/assets/mocks/test.mp4' // fallback
  } finally {
    isLoading.value = false
    setTimeout(() => {
      setupVideoMeta()
    }, 100)
  }
}

function setupVideoMeta() {
  const video = videoElement.value
  if (!video) return
  
  video.load()
  
  const onLoadedMetadata = () => {
    videoDuration.value = video.duration.toFixed(1)
    video.removeEventListener('loadedmetadata', onLoadedMetadata)
  }
  video.addEventListener('loadedmetadata', onLoadedMetadata)
}

function formatDate(dateStr) {
  if (!dateStr) return 'Unknown Date'
  if (dateStr.length === 8) {
    return `${dateStr.slice(0,4)}-${dateStr.slice(4,6)}-${dateStr.slice(6,8)}`
  }
  return dateStr
}

// Support synchronization with main toolbar CINE controls
function toggleCine() {
  const video = videoElement.value
  if (!video) return false
  
  if (video.paused) {
    video.play()
    return true
  } else {
    video.pause()
    return false
  }
}

watch(
  () => props.assignment?.instance?.sopInstanceUid,
  () => {
    loadVideo()
  }
)

onMounted(() => {
  loadVideo()
})

onBeforeUnmount(() => {
  const video = videoElement.value
  if (video) {
    video.pause()
  }
})

// Expose toggleCine so the ViewportContainer/Viewer can trigger play/pause from Toolbar CINE button
defineExpose({
  toggleCine,
})
</script>

<style scoped>
.video-viewport {
  position: relative;
  width: 100%;
  height: 100%;
  background-color: #0b0c0e;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.video-loading, .video-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  z-index: 5;
}

.video-wrapper {
  width: 100%;
  height: 100%;
  padding: 40px 10px 10px; /* Offset for HUD overlays */
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-player {
  max-width: 100%;
  max-height: 100%;
  background-color: #000000;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
  outline: none;
}

/* Corner Overlays (OHIF v3 Style) */
.viewport-overlay {
  position: absolute;
  color: #00A4D9;
  font-family: monospace;
  font-size: 11px;
  line-height: 1.4;
  pointer-events: none;
  z-index: 5;
  background-color: rgba(11, 12, 14, 0.6);
  padding: 4px 8px;
  border-radius: 4px;
}

.overlay-top-left {
  top: 10px;
  left: 10px;
}

.overlay-top-right {
  top: 10px;
  right: 10px;
  text-align: right;
}

.overlay-bottom-left {
  bottom: 10px;
  left: 10px;
}

.text-accent {
  color: #00A4D9;
}
.text-danger {
  color: #ff3333;
}
</style>
