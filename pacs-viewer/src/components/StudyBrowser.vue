<template>
  <div class="thumbnail-list">
    <div v-if="instances.length === 0" class="viewer-empty">
      <div>
        <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <rect x="2" y="2" width="20" height="20" rx="2"/>
          <circle cx="8.5" cy="8.5" r="1.5"/>
          <path d="M21 15l-5-5L5 21"/>
        </svg>
        <div>No images available</div>
        <div class="empty-sub">Select a series from the study list</div>
      </div>
    </div>

    <div class="thumbnail-grid">
      <div
        v-for="(item, index) in instances"
        :key="item.sopInstanceUid"
        class="thumbnail-card"
        :class="{
          active: item.sopInstanceUid === selectedInstanceUid,
          error: item.displayable === false
        }"
        @click="$emit('select', item)"
        :title="`${item.seriesDescription || 'Instance'} - #${item.instanceNumber || index + 1}`"
      >
        <div class="thumbnail-header">
          <span class="thumbnail-series-number">#{{ item.instanceNumber || index + 1 }}</span>
          <span class="thumbnail-series-desc">{{ item.seriesDescription || 'No Description' }}</span>
          <span class="thumbnail-modality">{{ item.modality || 'OT' }}</span>
        </div>

        <div class="thumbnail-preview">
          <div class="preview-placeholder">
            <svg class="preview-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
              <rect x="2" y="2" width="20" height="20" rx="2"/>
              <circle cx="8.5" cy="8.5" r="1.5"/>
              <path d="M21 15l-5-5L5 21"/>
            </svg>
          </div>
        </div>

        <div class="thumbnail-meta">
          <span class="meta-instances">{{ item.numberOfFrames || 1 }} frame{{ (item.numberOfFrames || 1) > 1 ? 's' : '' }}</span>
          <span v-if="item.failedFrames?.length" class="meta-warning">
            {{ item.failedFrames.length }} failed
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  instances: {
    type: Array,
    default: () => [],
  },
  selectedInstanceUid: {
    type: String,
    default: '',
  },
})

defineEmits(['select'])
</script>