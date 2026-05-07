<template>
  <div class="card-grid two-columns">
    <a-card title="Remote DICOM Operations" size="small">
      <a-form layout="vertical">
        <a-form-item label="Target AET">
          <a-input v-model:value="targetAet" />
        </a-form-item>
        <a-form-item label="Patient ID">
          <a-input v-model:value="patientId" />
        </a-form-item>
        <a-form-item label="Study UID">
          <a-input v-model:value="studyInstanceUid" />
        </a-form-item>
        <a-space>
          <a-button @click="runEcho">C-ECHO</a-button>
          <a-button type="primary" @click="runFind">Remote C-FIND</a-button>
          <a-button type="dashed" @click="runPull">Remote C-MOVE Pull</a-button>
        </a-space>
      </a-form>
    </a-card>
    <a-card title="Results" size="small">
      <a-alert v-if="echoResult" :message="`Reachable: ${echoResult.reachable}`" type="info" show-icon style="margin-bottom: 12px;" />
      <a-list :data-source="results" :locale="{ emptyText: 'No remote query results' }">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-space direction="vertical" size="small">
              <span class="mono">{{ item['0020000D'] || item['0020000E'] }}</span>
              <span>{{ item['00081030'] || item['0008103E'] || '' }}</span>
            </a-space>
          </a-list-item>
        </template>
      </a-list>
      <a-alert v-if="pullResult" :message="`Pulled ${pullResult.movedCount} instance(s) to ${pullResult.destinationAet}`" type="success" show-icon style="margin-top: 12px;" />
    </a-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { echoAet, remoteFindStudies, remotePull } from '../api/admin'

const targetAet = ref('')
const patientId = ref('')
const studyInstanceUid = ref('')
const results = ref([])
const echoResult = ref(null)
const pullResult = ref(null)

function criteria() {
  const payload = {}
  if (patientId.value) payload['00100020'] = patientId.value
  if (studyInstanceUid.value) payload['0020000D'] = studyInstanceUid.value
  return payload
}

async function runEcho() {
  try {
    echoResult.value = await echoAet(targetAet.value)
    message.success('C-ECHO completed')
  } catch (error) {
    message.error(error.message || 'C-ECHO failed')
  }
}

async function runFind() {
  try {
    results.value = await remoteFindStudies(targetAet.value, criteria())
    pullResult.value = null
    message.success('Remote query completed')
  } catch (error) {
    message.error(error.message || 'Remote query failed')
  }
}

async function runPull() {
  try {
    pullResult.value = await remotePull(targetAet.value, criteria())
    message.success('Remote pull completed')
  } catch (error) {
    message.error(error.message || 'Remote pull failed')
  }
}
</script>
