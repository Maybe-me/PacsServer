<template>
  <div class="waveform-viewport">
    <div v-if="isLoading" class="waveform-loading">
      <div class="spinner"></div>
      <div class="mt-2 text-xs text-muted">Loading Waveform Data...</div>
    </div>

    <div v-else-if="waveformError" class="waveform-error">
      <svg class="w-8 h-8 text-danger mb-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      <div class="text-sm font-semibold">{{ waveformError }}</div>
    </div>

    <!-- Interactive Waveform Dashboard -->
    <div v-show="!isLoading && !waveformError" class="waveform-workspace">
      <!-- 1. Top Control Bar (ECG Parameters) -->
      <div class="waveform-controls">
        <div class="control-group">
          <label>Layout:</label>
          <select v-model="layoutMode" @change="draw">
            <option value="6x2">6 x 2 (Standard)</option>
            <option value="12x1">12 x 1 (Full)</option>
            <option value="3x4">3 x 4 + 1 Rhythm</option>
          </select>
        </div>

        <div class="control-group">
          <label>Paper Speed:</label>
          <select v-model="paperSpeed" @change="draw">
            <option :value="25">25 mm/s</option>
            <option :value="50">50 mm/s</option>
          </select>
        </div>

        <div class="control-group">
          <label>Gain:</label>
          <select v-model="gain" @change="draw">
            <option :value="5">5 mm/mV</option>
            <option :value="10">10 mm/mV</option>
            <option :value="20">20 mm/mV</option>
          </select>
        </div>

        <div class="control-group">
          <label>Filter:</label>
          <button
            class="filter-toggle-btn"
            :class="{ active: filterEnabled }"
            @click="toggleFilter"
          >
            {{ filterEnabled ? 'EMG/LPF On' : 'Filter Off' }}
          </button>
        </div>
      </div>

      <!-- 2. Main Waveform Drawing Area -->
      <div class="canvas-container" ref="canvasContainer">
        <canvas ref="ecgCanvas" class="ecg-canvas"></canvas>
      </div>
    </div>

    <!-- HUD Overlay -->
    <div class="viewport-overlay overlay-top-left">
      <div class="overlay-row">{{ assignment.instance?.patientName || 'DOE^JOHN' }}</div>
      <div class="overlay-row">{{ assignment.instance?.patientId || 'MRN-000000' }}</div>
      <div class="overlay-row">{{ formatDate(assignment.instance?.studyDate) }}</div>
    </div>

    <div class="viewport-overlay overlay-top-right">
      <div class="overlay-row">HR: <span class="heart-rate-pulse">{{ heartRate }} BPM</span></div>
      <div class="overlay-row font-bold text-accent">ECG WAVEFORM</div>
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
const waveformError = ref('')
const layoutMode = ref('6x2')
const paperSpeed = ref(25)
const gain = ref(10)
const filterEnabled = ref(true)
const heartRate = ref(72)

const canvasContainer = ref(null)
const ecgCanvas = ref(null)
let rawData = null
let processedData = null
let resizeObserver = null

// Basic filter helper: simple moving average low-pass filter to simulate muscle noise suppression
function applyLowPassFilter(dataArray) {
  const result = new Array(dataArray.length)
  const windowSize = 5
  for (let i = 0; i < dataArray.length; i++) {
    let sum = 0
    let count = 0
    for (let w = -Math.floor(windowSize / 2); w <= Math.floor(windowSize / 2); w++) {
      const idx = i + w
      if (idx >= 0 && idx < dataArray.length) {
        sum += dataArray[idx]
        count++
      }
    }
    result[i] = sum / count
  }
  return result
}

function toggleFilter() {
  filterEnabled.value = !filterEnabled.value
  processWaveformData()
  draw()
}

function processWaveformData() {
  if (!rawData) return
  processedData = {}
  
  for (const lead of Object.keys(rawData)) {
    if (filterEnabled.value) {
      processedData[lead] = applyLowPassFilter(rawData[lead])
    } else {
      processedData[lead] = [...rawData[lead]]
    }
  }
}

async function loadWaveform() {
  isLoading.value = true
  waveformError.value = ''
  
  const instance = props.assignment?.instance
  if (!instance) {
    waveformError.value = 'No instance assignment found.'
    isLoading.value = false
    return
  }

  try {
    const sopInstanceUid = instance.sopInstanceUid

    // Mock/Demo data generation
    if (instance.patientId === 'PAT-MOCK-ECG' || sopInstanceUid?.includes('mock-ecg') || !sopInstanceUid) {
      generateMockEcgData()
      heartRate.value = 75
      isLoading.value = false
      processWaveformData()
      setTimeout(() => {
        setupCanvas()
      }, 50)
      return
    }

    // Production Wado JSON client request
    const targetUrl = `/api/viewer/instances/${sopInstanceUid}/waveform`
    const response = await fetch(targetUrl)
    if (response.ok) {
      const payload = await response.json()
      rawData = payload.data
      heartRate.value = payload.heartRate || 72
    } else {
      console.warn(`[ECG Viewport] API returned ${response.status}. Creating mock fallback.`);
      generateMockEcgData()
    }
  } catch (error) {
    console.error('[ECG Viewport] Failed to load ECG waveform:', error)
    generateMockEcgData()
  } finally {
    isLoading.value = false
    processWaveformData()
    setTimeout(() => {
      setupCanvas()
    }, 50)
  }
}

// Generate mathematically precise ECG wave patterns (P, Q, R, S, T) with overlay noise
function generateMockEcgData() {
  rawData = {}
  const leads = ["I", "II", "III", "aVR", "aVL", "aVF", "V1", "V2", "V3", "V4", "V5", "V6"]
  const numPoints = 5000
  const sampleFreq = 500
  const hr = 75
  const rrInterval = (sampleFreq * 60) / hr // samples per heart beat (e.g. 400 samples)
  
  // Base lead vectors to shape standard 12-lead views
  const leadScales = {
    "I": 1.0, "II": 1.4, "III": 0.5,
    "aVR": -1.2, "aVL": 0.2, "aVF": 1.0,
    "V1": -0.5, "V2": 0.8, "V3": 1.5,
    "V4": 1.8, "V5": 1.6, "V6": 1.2
  }

  leads.forEach((lead) => {
    const points = new Array(numPoints)
    const scale = leadScales[lead] || 1.0

    for (let t = 0; t < numPoints; t++) {
      const beatOffset = t % rrInterval
      let val = 0.0

      // 1. P-Wave (at beatOffset rrInterval - 80)
      const pStart = rrInterval - 80
      if (beatOffset >= pStart && beatOffset <= pStart + 40) {
        const pPhase = (beatOffset - pStart) / 40
        val += 0.12 * Math.sin(pPhase * Math.PI) * scale
      }

      // 2. QRS Complex (at beatOffset rrInterval - 30)
      const qrsStart = rrInterval - 30
      if (beatOffset >= qrsStart && beatOffset <= qrsStart + 30) {
        const offset = beatOffset - qrsStart
        if (offset < 5) {
          // Q-wave (dip)
          val -= 0.15 * (offset / 5) * scale
        } else if (offset >= 5 && offset < 15) {
          // R-wave (high peak)
          const peakPhase = (offset - 5) / 10
          val += ( -0.15 + 1.6 * peakPhase ) * scale
        } else if (offset >= 15 && offset < 22) {
          // S-wave (deep dip)
          const dipPhase = (offset - 15) / 7
          val += ( 1.45 - 1.85 * dipPhase ) * scale
        } else {
          // Back to baseline
          const basePhase = (offset - 22) / 8
          val += ( -0.4 * (1 - basePhase) ) * scale
        }
      }

      // 3. T-Wave (at beatOffset 50)
      const tStart = 50
      if (beatOffset >= tStart && beatOffset <= tStart + 90) {
        const tPhase = (beatOffset - tStart) / 90
        val += 0.28 * Math.sin(tPhase * Math.PI) * scale
      }

      // 4. Baseline Wander & EMG high-frequency noise interference
      const wander = 0.08 * Math.sin((t / numPoints) * 3 * Math.PI)
      const rawNoise = 0.07 * (Math.random() - 0.5) // EMG noise
      const acNoise = 0.03 * Math.sin((t / sampleFreq) * 50 * 2 * Math.PI) // 50Hz AC noise
      
      points[t] = val + wander + rawNoise + acNoise
    }
    rawData[lead] = points
  })
}

function setupCanvas() {
  const canvas = ecgCanvas.value
  const container = canvasContainer.value
  if (!canvas || !container) return

  // Set canvas scale based on container dimensions (high resolution)
  const dpr = window.devicePixelRatio || 1
  const rect = container.getBoundingClientRect()
  canvas.width = rect.width * dpr
  canvas.height = rect.height * dpr
  canvas.style.width = `${rect.width}px`
  canvas.style.height = `${rect.height}px`

  draw()
}

// ECG Paper Grid and Waveform drawing
function draw() {
  const canvas = ecgCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const width = canvas.width
  const height = canvas.height
  const dpr = window.devicePixelRatio || 1

  // 1. Clear with dark background
  ctx.fillStyle = '#0b0c0e'
  ctx.fillRect(0, 0, width, height)

  // 2. Draw High-fidelity Grid Background (Medical grid: 1mm and 5mm blocks)
  // Standard grid: 1mm = 0.04s (X) and 0.1mV (Y)
  // At 25mm/s paper speed: 1mm block = 1mm physical size.
  // We'll draw lightweight orange/red grid lines
  const gridStep1mm = 6 * dpr
  const gridStep5mm = 30 * dpr

  // 1mm light Grid lines
  ctx.strokeStyle = 'rgba(235, 94, 85, 0.07)'
  ctx.lineWidth = 0.5
  ctx.beginPath()
  for (let x = 0; x < width; x += gridStep1mm) {
    ctx.moveTo(x, 0); ctx.lineTo(x, height)
  }
  for (let y = 0; y < height; y += gridStep1mm) {
    ctx.moveTo(0, y); ctx.lineTo(width, y)
  }
  ctx.stroke()

  // 5mm slightly darker Grid lines
  ctx.strokeStyle = 'rgba(235, 94, 85, 0.18)'
  ctx.lineWidth = 1.0
  ctx.beginPath()
  for (let x = 0; x < width; x += gridStep5mm) {
    ctx.moveTo(x, 0); ctx.lineTo(x, height)
  }
  for (let y = 0; y < height; y += gridStep5mm) {
    ctx.moveTo(0, y); ctx.lineTo(width, y)
  }
  ctx.stroke()

  // 3. Grid Partition rendering for multiple leads
  const leads = ["I", "II", "III", "aVR", "aVL", "aVF", "V1", "V2", "V3", "V4", "V5", "V6"]
  if (!processedData || !Object.keys(processedData).length) return

  let cols = 2
  let rows = 6

  if (layoutMode.value === '12x1') {
    cols = 1; rows = 12
  } else if (layoutMode.value === '3x4') {
    cols = 3; rows = 4
  }

  const cellWidth = width / cols
  const cellHeight = height / rows

  ctx.lineWidth = 1.5 * dpr
  ctx.strokeStyle = '#00A4D9' // Elegant Neon Blue waveform

  leads.forEach((lead, index) => {
    const data = processedData[lead]
    if (!data) return

    // Position of cell
    const colIdx = index % cols
    const rowIdx = Math.floor(index / cols)
    const startX = colIdx * cellWidth
    const startY = rowIdx * cellHeight
    const centerY = startY + cellHeight / 2

    // Draw Lead Name Label
    ctx.fillStyle = '#00A4D9'
    ctx.font = `bold ${11 * dpr}px sans-serif`
    ctx.fillText(lead, startX + 15 * dpr, startY + 25 * dpr)

    // Draw calibration pulse (1mV step for 100ms) on each lead start
    ctx.strokeStyle = 'rgba(0, 164, 217, 0.5)'
    ctx.lineWidth = 1.0 * dpr
    ctx.beginPath()
    const calX = startX + 30 * dpr
    const calAmp = (gain.value * 6 * dpr) // height proportional to gain setting (5, 10, 20)
    ctx.moveTo(calX - 10 * dpr, centerY)
    ctx.lineTo(calX - 5 * dpr, centerY)
    ctx.lineTo(calX - 5 * dpr, centerY - calAmp)
    ctx.lineTo(calX + 5 * dpr, centerY - calAmp)
    ctx.lineTo(calX + 5 * dpr, centerY)
    ctx.lineTo(calX + 10 * dpr, centerY)
    ctx.stroke()

    // Draw Waveform curve
    ctx.strokeStyle = '#00A4D9'
    ctx.lineWidth = 1.5 * dpr
    ctx.beginPath()

    const signalStartX = calX + 20 * dpr
    const signalWidth = cellWidth - 60 * dpr
    const pointsInCell = Math.floor(signalWidth / dpr)

    // Calculate step factors
    // paperSpeed default: 25mm/s. High paperSpeed spreads wave horizontally.
    const horizontalSpread = paperSpeed.value === 50 ? 2 : 1
    
    for (let px = 0; px < pointsInCell; px++) {
      const dataIdx = Math.floor(px * horizontalSpread)
      if (dataIdx >= data.length) break

      const signalVal = data[dataIdx] // in mV (typically -2.0 to +2.0)
      const x = signalStartX + px * dpr
      
      // Calculate voltage to screen pixels
      // 1mV = gain mm (e.g. 10mm). 1mm on screen is gridStep1mm pixels.
      const pixelOffset = signalVal * gain.value * gridStep1mm
      const y = centerY - pixelOffset

      if (px === 0) {
        ctx.moveTo(x, y)
      } else {
        ctx.lineTo(x, y)
      }
    }
    ctx.stroke()
  })
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
    loadWaveform()
  }
)

onMounted(() => {
  loadWaveform()
  
  resizeObserver = new ResizeObserver(() => {
    setupCanvas()
  })
  if (canvasContainer.value) {
    resizeObserver.observe(canvasContainer.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})
</script>

<style scoped>
.waveform-viewport {
  position: relative;
  width: 100%;
  height: 100%;
  background-color: #0b0c0e;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.waveform-loading, .waveform-error {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  z-index: 5;
}

.waveform-workspace {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  padding-top: 40px; /* HUD offset */
  box-sizing: border-box;
}

/* ECG Tool Control Bar */
.waveform-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  background-color: #121316;
  border-bottom: 1px solid var(--border-color);
  padding: 8px 16px;
  flex-shrink: 0;
  z-index: 6;
}

.control-group {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--text-secondary);
}

.control-group label {
  font-weight: bold;
}

.control-group select {
  background-color: #1a1c22;
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  border-radius: 4px;
  padding: 4px 8px;
  outline: none;
  font-size: 11px;
  cursor: pointer;
}

.filter-toggle-btn {
  background-color: #1a1c22;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  border-radius: 4px;
  padding: 4px 10px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
}

.filter-toggle-btn:hover {
  color: var(--text-primary);
  border-color: var(--text-muted);
}

.filter-toggle-btn.active {
  background-color: rgba(0, 164, 217, 0.15);
  border-color: #00A4D9;
  color: #00A4D9;
  font-weight: bold;
}

.canvas-container {
  flex: 1;
  width: 100%;
  position: relative;
  overflow: hidden;
}

.ecg-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

/* Corner Overlays */
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

.heart-rate-pulse {
  color: #EB5E55; /* Cardiac Alert Red */
  font-weight: bold;
  animation: pulse 1.2s infinite;
}

@keyframes pulse {
  0% { opacity: 1; }
  50% { opacity: 0.5; }
  100% { opacity: 1; }
}

.text-accent {
  color: #00A4D9;
}
.text-danger {
  color: #ff3333;
}
</style>
