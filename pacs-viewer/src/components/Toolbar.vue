<template>
  <div class="viewer-toolbar">
    <!-- Navigation Group -->
    <div class="toolbar-group">
      <button
        class="toolbar-btn"
        :class="{ active: activeTool === 'StackScroll' }"
        @click="$emit('tool-change', 'StackScroll')"
        title="Scroll"
      >
        <svg class="svg-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M4 15h16v-2H4v2zm0 4h16v-2H4v2zm0-8h16V9H4v2zm0-6v2h16V5H4z"/>
        </svg>
        <span class="toolbar-label">Scroll</span>
      </button>
      <button
        class="toolbar-btn"
        :class="{ active: activeTool === 'Pan' }"
        @click="$emit('tool-change', 'Pan')"
        title="Pan"
      >
        <svg class="svg-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M10 9h4V6h3l-5-5-5 5h3v3zm-1 1H6V7l-5 5 5-5v-3h3v-4zm14 2l-5-5v3h-3v4h3v3l5-5zm-9 3h-4v3H7l5 5 5-5h-3v-3z"/>
        </svg>
        <span class="toolbar-label">Pan</span>
      </button>
      <button
        class="toolbar-btn"
        :class="{ active: activeTool === 'Zoom' }"
        @click="$emit('tool-change', 'Zoom')"
        title="Zoom"
      >
        <svg class="svg-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
          <line x1="12" y1="9.5" x2="12" y2="9.5" stroke="currentColor" stroke-width="2"/>
        </svg>
        <span class="toolbar-label">Zoom</span>
      </button>
      <button
        class="toolbar-btn"
        :class="{ active: activeTool === 'Crosshairs' }"
        @click="$emit('tool-change', 'Crosshairs')"
        title="Crosshairs Reference Lines"
      >
        <svg class="svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10"/>
          <line x1="22" y1="12" x2="2" y2="12"/>
          <line x1="12" y1="2" x2="12" y2="22"/>
        </svg>
        <span class="toolbar-label">Crosshair</span>
      </button>
    </div>

    <div class="toolbar-divider"></div>

    <!-- Window/Level -->
    <div class="toolbar-group">
      <button
        class="toolbar-btn"
        :class="{ active: activeTool === 'WindowLevel' }"
        @click="$emit('tool-change', 'WindowLevel')"
        title="Window Level"
      >
        <svg class="svg-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-3.31 0-6 2.69-6 6s2.69 6 6 6V6z"/>
        </svg>
        <span class="toolbar-label">W/L</span>
      </button>
    </div>

    <div class="toolbar-divider"></div>

    <!-- Measurements Group -->
    <div class="toolbar-group">
      <button
        class="toolbar-btn"
        :class="{ active: activeTool === 'Length' }"
        @click="$emit('tool-change', 'Length')"
        title="Length Measurement"
      >
        <svg class="svg-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M21 6H3c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 10H3V8h2v4h2V8h2v4h2V8h2v4h2V8h2v4h2V8h2v10z"/>
        </svg>
        <span class="toolbar-label">Length</span>
      </button>
      <button
        class="toolbar-btn"
        :class="{ active: activeTool === 'Angle' }"
        @click="$emit('tool-change', 'Angle')"
        title="Angle Measurement"
      >
        <svg class="svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M4 20L20 4M4 20h10M4 20v-10"/>
        </svg>
        <span class="toolbar-label">Angle</span>
      </button>
    </div>

    <div class="toolbar-divider"></div>

    <!-- Viewport Controls -->
    <div class="toolbar-group">
      <button
        class="toolbar-btn"
        :disabled="!hasViewportData"
        @click="$emit('toggle-invert')"
        title="Invert Colors"
      >
        <svg class="svg-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2a10 10 0 100 20 10 10 0 000-20zm0 18v-8H4.05c.42-3.8 3.32-6.84 7.15-7.61V12h7.95c-.42 3.8-3.32 6.84-7.15 7.61v-7.61H12v8z"/>
        </svg>
        <span class="toolbar-label">Invert</span>
      </button>
      <button
        class="toolbar-btn"
        :class="{ active: cinePlaying }"
        :disabled="!hasViewportData"
        @click="$emit('toggle-cine')"
        title="CINE Play/Pause"
      >
        <svg v-if="!cinePlaying" class="svg-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M8 5v14l11-7z"/>
        </svg>
        <svg v-else class="svg-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
        </svg>
        <span class="toolbar-label">CINE</span>
      </button>
      <button
        class="toolbar-btn"
        :disabled="!hasViewportData"
        @click="$emit('reset-viewport')"
        title="Reset Viewport"
      >
        <svg class="svg-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z"/>
        </svg>
        <span class="toolbar-label">Reset</span>
      </button>
    </div>

    <div class="toolbar-divider"></div>

    <!-- Advanced 3D & Projection Group -->
    <div v-if="layout === 'mpr'" class="toolbar-divider"></div>
    <div v-if="layout === 'mpr'" class="toolbar-group">
      <div class="toolbar-dropdown-shell">
        <label class="dropdown-label">3D Projection:</label>
        <select
          v-model="selectedProjectionMode"
          class="toolbar-select"
          @change="onProjectionModeChange"
          title="Projection Mode (MIP/MinIP/AIP)"
        >
          <option value="NORMAL">Normal Slice</option>
          <option value="MIP">MIP (Max Intensity)</option>
          <option value="MinIP">MinIP (Min Intensity)</option>
          <option value="AIP">AIP (Avg Intensity)</option>
        </select>
      </div>

      <!-- Slab Thickness Slider -->
      <div v-if="selectedProjectionMode !== 'NORMAL'" class="slab-thickness-slider-shell">
        <span class="slab-label">Slab: <span class="text-accent font-bold">{{ slabThickness }}</span> mm</span>
        <input
          type="range"
          min="2"
          max="50"
          step="1"
          v-model="slabThickness"
          class="slab-slider"
          @input="onSlabThicknessChange"
        />
      </div>
    </div>

    <div class="toolbar-divider"></div>

    <!-- Layout Selector -->
    <div class="toolbar-group">
      <button
        class="toolbar-btn"
        @click="cycleLayout"
        :title="`Change Layout (Current: ${layoutLabel})`"
      >
        <svg class="svg-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 0h8v8h-8z"/>
        </svg>
        <span class="toolbar-label">{{ layoutLabel }}</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  layout: { type: String, required: true },
  activeTool: { type: String, required: true },
  cinePlaying: { type: Boolean, default: false },
  selectedInstance: { type: Object, default: null },
  renderedInstance: { type: Object, default: null },
  renderedFrameNumber: { type: Number, default: 1 },
  hasViewportData: { type: Boolean, default: false },
})

const emit = defineEmits([
  'layout-change',
  'tool-change',
  'scroll',
  'toggle-invert',
  'reset-viewport',
  'toggle-cine',
  'projection-change'
])

const layoutOptions = ['single', 'dual', 'quad', 'mpr']
const layoutLabels = { single: '1x1', dual: '1x2', quad: '2x2', mpr: 'MPR' }
const layoutLabel = computed(() => layoutLabels[props.layout] || 'Layout')

const selectedProjectionMode = ref('NORMAL')
const slabThickness = ref(10)

function onProjectionModeChange() {
  emit('projection-change', {
    mode: selectedProjectionMode.value,
    thickness: Number(slabThickness.value)
  })
}

function onSlabThicknessChange() {
  emit('projection-change', {
    mode: selectedProjectionMode.value,
    thickness: Number(slabThickness.value)
  })
}

// Reset projection mode when switching layout away from MPR
watch(
  () => props.layout,
  (newLayout) => {
    if (newLayout !== 'mpr' && selectedProjectionMode.value !== 'NORMAL') {
      selectedProjectionMode.value = 'NORMAL'
      emit('projection-change', {
        mode: 'NORMAL',
        thickness: 10
      })
    }
  }
)

function cycleLayout() {
  const i = layoutOptions.indexOf(props.layout)
  emit('layout-change', layoutOptions[(i + 1) % layoutOptions.length])
}
</script>