<template>
  <a-card size="small" class="viewer-toolbar-card" :bordered="false">
    <div class="viewer-toolbar">
      <div class="viewer-toolbar-group">
        <span class="viewer-toolbar-label">Layout</span>
        <a-segmented :options="layoutOptions" :value="layout" @change="$emit('layout-change', $event)" />
      </div>

      <div class="viewer-toolbar-group">
        <span class="viewer-toolbar-label">Tools</span>
        <a-radio-group :value="activeTool" button-style="solid" @change="$emit('tool-change', $event.target.value)">
          <a-radio-button value="WindowLevel">WW/WL</a-radio-button>
          <a-radio-button value="Pan">Pan</a-radio-button>
          <a-radio-button value="Zoom">Zoom</a-radio-button>
          <a-radio-button value="Length">Measure</a-radio-button>
          <a-radio-button value="StackScroll">Scroll</a-radio-button>
        </a-radio-group>
      </div>

      <div class="viewer-toolbar-group">
        <span class="viewer-toolbar-label">Viewport</span>
        <a-space>
          <a-button @click="$emit('scroll', -1)" :disabled="!hasViewportData">Prev</a-button>
          <a-button @click="$emit('scroll', 1)" :disabled="!hasViewportData">Next</a-button>
          <a-button @click="$emit('toggle-invert')" :disabled="!hasViewportData">Invert</a-button>
          <a-button @click="$emit('reset-viewport')" :disabled="!hasViewportData">Reset</a-button>
          <a-button @click="$emit('toggle-cine')" :disabled="!hasViewportData">
            {{ cinePlaying ? 'Stop Cine' : 'Play Cine' }}
          </a-button>
        </a-space>
      </div>

      <div class="viewer-toolbar-group viewer-toolbar-group--end">
        <a-button
          ghost
          :disabled="!renderedInstance"
          :href="resolvedRenderedWadoUri"
          target="_blank"
        >
          Open Rendered WADO
        </a-button>
        <a-button
          type="primary"
          ghost
          :disabled="!selectedInstance"
          :href="resolvedWadoUri"
          target="_blank"
        >
          Open WADO
        </a-button>
      </div>
    </div>
  </a-card>
</template>

<script setup>
import { computed } from 'vue'
import { resolveWadoUri as resolveInstanceWadoUri } from '../api/viewer'

const props = defineProps({
  layout: {
    type: String,
    required: true,
  },
  activeTool: {
    type: String,
    required: true,
  },
  cinePlaying: {
    type: Boolean,
    default: false,
  },
  selectedInstance: {
    type: Object,
    default: null,
  },
  renderedInstance: {
    type: Object,
    default: null,
  },
  renderedFrameNumber: {
    type: Number,
    default: 1,
  },
  hasViewportData: {
    type: Boolean,
    default: false,
  },
})

defineEmits(['layout-change', 'tool-change', 'scroll', 'toggle-invert', 'reset-viewport', 'toggle-cine'])

const resolvedWadoUri = computed(() => resolveInstanceWadoUri(props.selectedInstance))
const resolvedRenderedWadoUri = computed(() => resolveInstanceWadoUri(props.renderedInstance, props.renderedFrameNumber))

const layoutOptions = [
  { label: '1x1', value: 'single' },
  { label: '1x2', value: 'dual' },
  { label: '2x2', value: 'quad' },
]
</script>
