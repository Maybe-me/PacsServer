<template>
  <div class="list-container">
    <div v-if="studies.length === 0" class="viewer-empty" style="padding: 20px;">
      No studies found
    </div>
    <div
      v-for="study in studies"
      :key="study.studyInstanceUid"
      class="list-item"
      :class="{ active: study.studyInstanceUid === selectedStudyUid }"
      @click="$emit('select', study)"
    >
      <div class="list-item-main">
        <div class="item-title">{{ study.studyDescription || 'No Description' }}</div>
        <div class="item-sub">{{ study.studyDate }} · {{ study.modalitiesInStudy }}</div>
      </div>
      <div class="item-meta">
        <div>{{ study.numSeries }} series</div>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  studies: {
    type: Array,
    default: () => [],
  },
  selectedStudyUid: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['select'])
</script>

<style scoped>
</style>
