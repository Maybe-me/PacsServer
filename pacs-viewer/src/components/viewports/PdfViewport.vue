<template>
  <div class="pdf-viewport">
    <div v-if="isLoading" class="pdf-loading">
      <div class="spinner"></div>
      <div class="mt-2 text-xs text-muted">Loading Encapsulated PDF...</div>
    </div>
    
    <div v-else-if="pdfError" class="pdf-error">
      <svg class="w-8 h-8 text-danger mb-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      <div class="text-sm font-semibold">{{ pdfError }}</div>
    </div>

    <!-- Embedded PDF Viewer -->
    <div v-else class="pdf-frame-wrapper">
      <iframe
        v-if="pdfUrl"
        :src="pdfUrl"
        class="pdf-iframe"
        frameborder="0"
        title="DICOM PDF Report"
      ></iframe>
    </div>

    <!-- Corner Overlays (OHIF v3 Style) -->
    <div class="viewport-overlay overlay-top-left">
      <div class="overlay-row">{{ assignment.instance?.patientName || 'DOE^JOHN' }}</div>
      <div class="overlay-row">{{ assignment.instance?.patientId || 'MRN-000000' }}</div>
      <div class="overlay-row">{{ formatDate(assignment.instance?.studyDate) }}</div>
    </div>
    
    <div class="viewport-overlay overlay-top-right">
      <div class="overlay-row">{{ assignment.series?.seriesDescription || 'Encapsulated Report' }}</div>
      <div class="overlay-row font-bold text-accent">PDF REPORT</div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

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

const isLoading = ref(true)
const pdfError = ref('')
const pdfUrl = ref('')

async function loadPdf() {
  isLoading.value = true
  pdfError.value = ''
  
  if (pdfUrl.value) {
    URL.revokeObjectURL(pdfUrl.value)
    pdfUrl.value = ''
  }

  const instance = props.assignment?.instance
  if (!instance) {
    pdfError.value = 'No instance assignment found.'
    isLoading.value = false
    return
  }

  try {
    const sopInstanceUid = instance.sopInstanceUid
    
    // Mock / Demo handling: Check if it's mock patient or contains mock indicators
    if (instance.patientId === 'PAT-MOCK-PDF' || sopInstanceUid?.includes('mock-pdf') || !sopInstanceUid) {
      // Use mock pdf path or construct a synthetic base64 pdf if not exists
      pdfUrl.value = '/assets/mocks/test.pdf'
      isLoading.value = false
      return
    }

    // Production logic: Direct fetch from backend contract API
    const targetUrl = `/api/viewer/instances/${sopInstanceUid}/pdf`
    
    // Test if we can fetch it. If backend not yet implemented or 404, fallback to simulated PDF.
    const response = await fetch(targetUrl, { method: 'HEAD' })
    if (response.ok) {
      pdfUrl.value = targetUrl
    } else {
      console.warn(`[PDF Viewport] Backend API ${targetUrl} returned ${response.status}. Falling back to demo PDF.`);
      pdfUrl.value = '/assets/mocks/test.pdf'
    }
  } catch (error) {
    console.error('[PDF Viewport] Failed to load PDF:', error)
    pdfError.value = 'Failed to load PDF document.'
    emit('render-error', {
      viewportId: props.viewportId,
      message: 'Failed to retrieve encapsulated PDF document.'
    })
  } finally {
    isLoading.value = false
  }
}

function formatDate(dateStr) {
  if (!dateStr) return 'Unknown Date'
  if (dateStr.length === 8) {
    return `${dateStr.slice(0,4)}-${dateStr.slice(4,6)}-${dateStr.slice(6,8)}`
  }
  return dateStr
}

watch(
  () => props.assignment?.instance?.sopInstanceUid,
  () => {
    loadPdf()
  }
)

onMounted(() => {
  loadPdf()
})

onBeforeUnmount(() => {
  if (pdfUrl.value && pdfUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(pdfUrl.value)
  }
})
</script>

<style scoped>
.pdf-viewport {
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

.pdf-loading, .pdf-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  z-index: 5;
}

.pdf-frame-wrapper {
  width: 100%;
  height: 100%;
  position: relative;
  padding: 40px 10px 10px; /* Offset for HUD overlays */
  box-sizing: border-box;
}

.pdf-iframe {
  width: 100%;
  height: 100%;
  border: none;
  background-color: #1e1e1e;
  border-radius: 4px;
}

/* Corner Overlays (OHIF v3) */
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

.text-accent {
  color: #00A4D9;
}
.text-danger {
  color: #ff3333;
}
</style>
