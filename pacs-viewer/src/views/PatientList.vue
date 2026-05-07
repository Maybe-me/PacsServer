<template>
  <a-card title="Study Search" size="small">
    <a-form layout="vertical">
      <a-form-item label="Patient ID">
        <a-input v-model:value="filters.patientId" allow-clear />
      </a-form-item>
      <a-form-item label="Issuer Of Patient ID">
        <a-input v-model:value="filters.issuerOfPatientId" allow-clear />
      </a-form-item>
      <a-form-item label="Modality">
        <a-input v-model:value="filters.modality" allow-clear />
      </a-form-item>
      <a-form-item label="Study Date From">
        <a-input v-model:value="filters.studyDateFrom" allow-clear placeholder="YYYYMMDD" />
      </a-form-item>
      <a-form-item label="Study Date To">
        <a-input v-model:value="filters.studyDateTo" allow-clear placeholder="YYYYMMDD" />
      </a-form-item>
      <a-space>
        <a-button type="primary" @click="$emit('search', { ...filters })">Search</a-button>
        <a-button @click="reset">Reset</a-button>
      </a-space>
    </a-form>
  </a-card>
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
