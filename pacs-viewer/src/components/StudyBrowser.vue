<template>
  <a-card size="small" title="Thumbnails" class="viewer-panel-card viewer-thumbnail-card" :bordered="false">
    <div v-if="instances.length" class="thumbnail-strip">
      <button
        v-for="item in instances"
        :key="item.sopInstanceUid"
        class="thumbnail-item"
        :class="{
          'thumbnail-item--active': item.sopInstanceUid === selectedInstanceUid,
          'thumbnail-item--unsupported': item.displayable === false,
        }"
        @click="$emit('select', item)"
        >
          <span class="thumbnail-item__index">#{{ item.instanceNumber || 0 }}</span>
          <span v-if="item.numberOfFrames > 1" class="thumbnail-item__meta">{{ item.numberOfFrames }} frames</span>
          <span v-if="item.failedFrames?.length" class="thumbnail-item__meta">{{ item.failedFrames.length }} failed</span>
          <span v-if="resolveIssue(item)" class="thumbnail-item__issue">{{ resolveIssue(item) }}</span>
          <span class="thumbnail-item__uid mono">{{ item.sopInstanceUid }}</span>
        </button>
      </div>
      <a-empty v-else description="No thumbnails" />
    </a-card>
</template>

<script setup>
function resolveIssue(instance) {
  if (instance?.displayIssue) {
    return instance.displayIssue
  }
  const latestFrameIssue = instance?.frameIssues?.[instance.frameIssues.length - 1]
  if (!latestFrameIssue?.message || !instance?.failedFrames?.length) {
    return ''
  }
  return `Skipped ${instance.failedFrames.length} failed frame(s): ${latestFrameIssue.message}`
}

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
