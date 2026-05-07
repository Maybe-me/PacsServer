<template>
  <a-card title="Series" size="small" class="fill-height">
    <a-table
      :columns="columns"
      :data-source="series"
      :pagination="false"
      size="small"
      row-key="seriesInstanceUid"
      :customRow="customRow"
    />
  </a-card>
</template>

<script setup>
import { h } from 'vue'
import { Tag } from 'ant-design-vue'

defineProps({
  series: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['select'])

function renderViewerStatus(record) {
  const tags = []
  const incompatibleCount = Number(record?.incompatibleCount) || 0
  const degradedCount = Number(record?.degradedCount) || 0
  const failedFrameCount = Number(record?.failedFrameCount) || 0
  const displayableCount = Number(record?.displayableCount) || 0
  const totalInstances = Number(record?.numInstances) || 0

  let statusLabel = 'Unknown'
  let statusColor = 'default'
  if (totalInstances > 0 && incompatibleCount >= totalInstances) {
    statusLabel = 'Blocked'
    statusColor = 'error'
  } else if (incompatibleCount > 0) {
    statusLabel = 'Partial'
    statusColor = 'warning'
  } else if (degradedCount > 0) {
    statusLabel = 'Degraded'
    statusColor = 'processing'
  } else if (totalInstances > 0) {
    statusLabel = 'Healthy'
    statusColor = 'success'
  }

  tags.push(h(Tag, { color: statusColor }, () => statusLabel))
  if (displayableCount || totalInstances) {
    tags.push(h(Tag, null, () => `OK ${displayableCount}/${totalInstances}`))
  }
  if (degradedCount > 0) {
    tags.push(h(Tag, { color: 'processing' }, () => `Degraded ${degradedCount}`))
  }
  if (failedFrameCount > 0) {
    tags.push(h(Tag, { color: 'gold' }, () => `Skipped ${failedFrameCount}`))
  }
  if (incompatibleCount > 0) {
    tags.push(h(Tag, { color: 'error' }, () => `Blocked ${incompatibleCount}`))
  }

  return h('div', { class: 'series-viewer-status' }, tags)
}

const columns = [
  { title: 'Series UID', dataIndex: 'seriesInstanceUid', key: 'seriesInstanceUid', ellipsis: true },
  { title: 'Description', dataIndex: 'seriesDescription', key: 'seriesDescription' },
  { title: 'Modality', dataIndex: 'modality', key: 'modality', width: 90 },
  { title: 'Instances', dataIndex: 'numInstances', key: 'numInstances', width: 90 },
  {
    title: 'Viewer',
    dataIndex: 'compatibilitySummary',
    key: 'compatibilitySummary',
    width: 280,
    customRender: ({ record }) => renderViewerStatus(record),
  },
]

function customRow(record) {
  return {
    onClick: () => emit('select', record),
    style: 'cursor:pointer',
  }
}
</script>

<style scoped>
.series-viewer-status {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
