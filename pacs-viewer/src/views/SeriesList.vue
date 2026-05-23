<template>
  <div class="list-container">
    <div v-if="series.length === 0" class="viewer-empty" style="padding: 20px;">
      No series selected
    </div>
    <div
      v-for="item in series"
      :key="item.seriesInstanceUid"
      class="list-item"
      :class="{ active: item.seriesInstanceUid === selectedSeriesUid }"
      @click="$emit('select', item)"
    >
      <div class="list-item-main">
        <div class="item-title">{{ item.seriesDescription || 'No Description' }}</div>
        <div class="item-sub">{{ item.modality }} · {{ item.numInstances }} instances</div>
      </div>
      <div class="status-indicator" :class="getStatusClass(item)"></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  series: {
    type: Array,
    default: () => [],
  },
  selectedSeriesUid: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['select'])

function getStatusClass(record) {
  const incompatibleCount = Number(record?.incompatibleCount) || 0
  const degradedCount = Number(record?.degradedCount) || 0
  const totalInstances = Number(record?.numInstances) || 0

  if (totalInstances > 0 && incompatibleCount >= totalInstances) return 'status-error'
  if (incompatibleCount > 0) return 'status-warning'
  if (degradedCount > 0) return 'status-processing'
  return 'status-success'
}
</script>

<style scoped>
.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.status-error { background-color: #f87171; }
.status-warning { background-color: #fbbf24; }
.status-processing { background-color: #60a5fa; }
.status-success { background-color: #34d399; }
</style>
