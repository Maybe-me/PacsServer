<template>
  <div class="card-grid two-columns">
    <a-card title="AE Nodes" size="small">
      <a-table :columns="columns" :data-source="aets" row-key="aet" :pagination="false" :customRow="customRow" />
    </a-card>
    <a-card title="Edit Node" size="small">
      <a-form layout="vertical">
        <a-form-item label="AET">
          <a-input v-model:value="form.aet" />
        </a-form-item>
        <a-form-item label="Host">
          <a-input v-model:value="form.host" />
        </a-form-item>
        <a-form-item label="Port">
          <a-input-number v-model:value="form.port" style="width: 100%;" />
        </a-form-item>
        <a-form-item label="Role">
          <a-select v-model:value="form.role" :options="roles" />
        </a-form-item>
        <a-form-item label="Node Name">
          <a-input v-model:value="form.nodeName" />
        </a-form-item>
        <a-form-item label="Description">
          <a-textarea v-model:value="form.description" :rows="3" />
        </a-form-item>
        <a-form-item>
          <a-checkbox v-model:checked="form.enabled">Enabled</a-checkbox>
        </a-form-item>
        <a-space>
          <a-button type="primary" @click="save">Save</a-button>
          <a-button danger :disabled="!selectedAet" @click="removeNode">Delete</a-button>
          <a-button @click="reset">New</a-button>
        </a-space>
      </a-form>
    </a-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { createAet, deleteAet, listAets, updateAet } from '../api/admin'

const aets = ref([])
const selectedAet = ref(null)
const roles = [
  { label: 'REMOTE', value: 'REMOTE' },
  { label: 'LOCAL', value: 'LOCAL' },
  { label: 'BOTH', value: 'BOTH' },
]

const form = reactive({
  aet: '',
  host: '',
  port: 11112,
  role: 'REMOTE',
  nodeName: '',
  description: '',
  enabled: true,
})

const columns = [
  { title: 'AET', dataIndex: 'aet', key: 'aet' },
  { title: 'Host', dataIndex: 'host', key: 'host' },
  { title: 'Port', dataIndex: 'port', key: 'port', width: 90 },
  { title: 'Role', dataIndex: 'role', key: 'role', width: 100 },
]

function customRow(record) {
  return {
    onClick: () => select(record),
    style: 'cursor:pointer',
  }
}

function select(record) {
  selectedAet.value = record.aet
  Object.assign(form, record)
}

function reset() {
  selectedAet.value = null
  Object.assign(form, {
    aet: '',
    host: '',
    port: 11112,
    role: 'REMOTE',
    nodeName: '',
    description: '',
    enabled: true,
  })
}

async function load() {
  aets.value = await listAets()
}

async function save() {
  try {
    const payload = { ...form }
    if (selectedAet.value) {
      await updateAet(selectedAet.value, payload)
    } else {
      await createAet(payload)
    }
    await load()
    message.success('Saved')
  } catch (error) {
    message.error(error.message || 'Failed to save node')
  }
}

async function removeNode() {
  if (!selectedAet.value) return
  try {
    await deleteAet(selectedAet.value)
    await load()
    reset()
    message.success('Deleted')
  } catch (error) {
    message.error(error.message || 'Failed to delete node')
  }
}

onMounted(async () => {
  try {
    await load()
  } catch (error) {
    message.error(error.message || 'Failed to load nodes')
  }
})
</script>
