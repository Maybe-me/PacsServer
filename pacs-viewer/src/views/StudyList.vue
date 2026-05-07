<template>
  <a-card title="Studies" size="small" class="fill-height">
    <a-table
      :columns="columns"
      :data-source="studies"
      :pagination="false"
      size="small"
      row-key="studyInstanceUid"
      @row-click="onRowClick"
      :customRow="customRow"
    />
  </a-card>
</template>

<script setup>
const props = defineProps({
  studies: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['select'])

const columns = [
  { title: 'Study UID', dataIndex: 'studyInstanceUid', key: 'studyInstanceUid', ellipsis: true },
  { title: 'Description', dataIndex: 'studyDescription', key: 'studyDescription' },
  { title: 'Modality', dataIndex: 'modalitiesInStudy', key: 'modalitiesInStudy' },
  { title: 'Series', dataIndex: 'numSeries', key: 'numSeries', width: 80 },
]

function customRow(record) {
  return {
    onClick: () => emit('select', record),
    style: 'cursor:pointer',
  }
}

function onRowClick() {
}
</script>
