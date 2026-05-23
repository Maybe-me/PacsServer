<template>
  <div class="search-panel">
    <div class="search-grid">
      <div class="search-item">
        <label>Patient ID</label>
        <input v-model="filters.patientId" type="text" placeholder="Patient ID" @keyup.enter="$emit('search', { ...filters })" />
      </div>
      <div class="search-item">
        <label>Modality</label>
        <input v-model="filters.modality" type="text" placeholder="Modality" @keyup.enter="$emit('search', { ...filters })" />
      </div>
    </div>
    <div class="search-actions">
      <button class="action-btn primary" @click="$emit('search', { ...filters })">Search</button>
      <button class="action-btn" @click="reset">Reset</button>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'

const filters = reactive({
  patientId: '',
  issuerOfPatientId: '',
  modality: '',
  studyDateFrom: '',
  studyDateTo: '',
})

const emit = defineEmits(['search'])

function reset() {
  Object.assign(filters, {
    patientId: '',
    issuerOfPatientId: '',
    modality: '',
    studyDateFrom: '',
    studyDateTo: '',
  })
  emit('search', { ...filters })
}
</script>

<style scoped>
.search-panel {
  padding: 12px;
  background-color: var(--secondary-bg);
}
.search-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 12px;
}
.search-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.search-item label {
  font-size: 10px;
  color: var(--text-secondary);
  text-transform: uppercase;
  font-weight: 600;
}
.search-item input {
  background-color: #000000;
  border: 1px solid var(--border-color);
  color: #ffffff;
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
  width: 100%;
}
.search-item input::placeholder {
  color: #666;
}
.search-item input:focus {
  border-color: var(--active-color);
}
.search-actions {
  display: flex;
  gap: 8px;
}
.action-btn {
  flex: 1;
  padding: 8px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
  background: #1e2f47;
  color: #ffffff;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  transition: all 0.2s;
}
.action-btn.primary {
  background-color: var(--active-color);
  border-color: var(--active-color);
}
.action-btn:hover {
  filter: brightness(1.2);
}
</style>
