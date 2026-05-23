<template>
  <header class="viewer-header">
    <div class="header-left">
      <div class="header-logo">
        <svg class="logo-icon" viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2L2 22h20L12 2zm0 3.8L18.4 19H5.6L12 5.8z"/>
        </svg>
        <span>OHIF</span>
      </div>

      <div class="header-divider"></div>

      <div v-if="patientName" class="header-study-info">
        <span class="header-patient-name">{{ patientName }}</span>
        <span v-if="mrn" class="header-patient-mrn">({{ mrn }})</span>
        <span v-if="studyDescription" class="header-study-desc">{{ studyDescription }}</span>
      </div>
      <span v-else class="header-study-info" style="color: var(--text-muted); font-size: 12px;">
        No study selected
      </span>
    </div>

    <div class="header-right">
      <button class="header-btn" title="Toggle side panels" @click="$emit('toggle-sidebars')">
        <svg class="svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="18" height="18" rx="2"/>
          <line x1="9" y1="3" x2="9" y2="21"/>
        </svg>
      </button>
      <button class="header-btn" title="Settings">
        <svg class="svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="3"/>
          <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"/>
        </svg>
      </button>
    </div>
  </header>
</template>

<script setup>
defineProps({
  patientName: {
    type: String,
    default: '',
  },
  mrn: {
    type: String,
    default: '',
  },
  studyDescription: {
    type: String,
    default: '',
  },
})

defineEmits(['toggle-sidebars'])
</script>