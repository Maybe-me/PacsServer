<template>
  <div class="sync-admin-shell">
    <div class="card-grid two-columns">
      <a-card title="Sync Jobs" size="small">
        <a-space direction="vertical" style="width: 100%;">
          <a-space wrap>
            <a-button type="primary" @click="resetJob">New Job</a-button>
            <a-button @click="reloadAll">Refresh</a-button>
          </a-space>
          <a-space wrap>
            <a-tag color="blue">Total {{ jobStats.total }}</a-tag>
            <a-tag color="green">Enabled {{ jobStats.enabled }}</a-tag>
            <a-tag color="orange">Paused {{ jobStats.paused }}</a-tag>
            <a-tag>Disabled {{ jobStats.disabled }}</a-tag>
          </a-space>
        </a-space>
        <a-table
          :columns="jobColumns"
          :data-source="jobs"
          row-key="jobName"
          :pagination="false"
          :customRow="jobRow"
          size="small"
          style="margin-top: 12px;"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'enabled'">
              <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? 'Enabled' : 'Disabled' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'paused'">
              <a-tag :color="record.paused ? 'orange' : 'blue'">{{ record.paused ? 'Paused' : 'Ready' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'limits'">
              <span>S{{ record.maxStudiesPerRun || '-' }} / I{{ record.maxInstancesPerRun || '-' }} / {{ record.throttleDelayMs || 0 }}ms</span>
            </template>
            <template v-else-if="column.key === 'lastError'">
              <span>{{ record.lastErrorCategory || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click.stop="runJob(record)">Run</a-button>
                <a-button size="small" danger @click.stop="removeJob(record)">Delete</a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-card title="Edit Sync Job" size="small">
        <a-form layout="vertical">
          <a-form-item label="Job Name">
            <a-input v-model:value="jobForm.jobName" :disabled="!!selectedJobName" />
          </a-form-item>
          <a-form-item label="Job Type">
            <a-select v-model:value="jobForm.jobType" :options="jobTypes" />
          </a-form-item>
          <a-form-item label="Target AET">
            <a-input v-model:value="jobForm.targetAet" />
          </a-form-item>
          <a-form-item v-if="jobForm.jobType === 'PULL'" label="Destination AET">
            <a-input v-model:value="jobForm.destinationAet" />
          </a-form-item>
          <a-form-item label="Patient ID">
            <a-input v-model:value="jobForm.patientId" />
          </a-form-item>
          <a-form-item label="Modality">
            <a-input v-model:value="jobForm.modality" />
          </a-form-item>
          <a-form-item label="Study Date Lookback Days">
            <a-input-number v-model:value="jobForm.studyDateLookbackDays" style="width: 100%;" :min="0" />
          </a-form-item>
          <a-form-item label="Max Studies per Run">
            <a-input-number v-model:value="jobForm.maxStudiesPerRun" style="width: 100%;" :min="0" />
          </a-form-item>
          <a-form-item label="Max Instances per Run">
            <a-input-number v-model:value="jobForm.maxInstancesPerRun" style="width: 100%;" :min="0" />
          </a-form-item>
          <a-form-item label="Throttle Delay (ms)">
            <a-input-number v-model:value="jobForm.throttleDelayMs" style="width: 100%;" :min="0" />
          </a-form-item>
          <a-form-item label="Max Retry Count">
            <a-input-number v-model:value="jobForm.maxRetryCount" style="width: 100%;" :min="0" />
          </a-form-item>
          <a-form-item label="Failure Threshold">
            <a-input-number v-model:value="jobForm.failureThreshold" style="width: 100%;" :min="1" />
          </a-form-item>
          <a-form-item v-if="jobForm.jobType === 'PUSH'">
            <a-checkbox v-model:checked="jobForm.preventLoopToSource">Prevent loop to source</a-checkbox>
          </a-form-item>
          <a-form-item v-if="jobForm.jobType === 'PUSH'">
            <a-checkbox v-model:checked="jobForm.skipRemoteDuplicates">Skip remote duplicates</a-checkbox>
          </a-form-item>
          <a-form-item v-if="jobForm.jobType === 'PUSH'" label="Allowed Source AETs">
            <a-input v-model:value="jobForm.sourceAetAllowListText" placeholder="AET_A,AET_B" />
          </a-form-item>
          <a-form-item v-if="jobForm.jobType === 'PUSH'" label="Blocked Source AETs">
            <a-input v-model:value="jobForm.sourceAetBlockListText" placeholder="AET_X,AET_Y" />
          </a-form-item>
          <a-form-item>
            <a-checkbox v-model:checked="jobForm.paused">Paused</a-checkbox>
          </a-form-item>
          <a-form-item>
            <a-checkbox v-model:checked="jobForm.enabled">Enabled</a-checkbox>
          </a-form-item>
          <a-space>
            <a-button type="primary" @click="saveJob">Save</a-button>
            <a-button @click="resetJob">Reset</a-button>
          </a-space>
        </a-form>
      </a-card>
    </div>

    <a-card title="Sync Executions" size="small" style="margin-top: 16px;">
      <a-space direction="vertical" style="width: 100%;">
        <a-space wrap>
          <a-select v-model:value="executionFilters.status" style="width: 140px;" :options="statusOptions" allow-clear placeholder="Status" />
          <a-input v-model:value="executionFilters.jobName" style="width: 220px;" placeholder="Job name filter" />
          <a-select
            v-model:value="executionFilters.errorCategory"
            style="width: 180px;"
            :options="errorCategoryOptions"
            allow-clear
            placeholder="Error category"
          />
          <a-button type="primary" @click="loadExecutions">Apply</a-button>
          <a-button @click="resetExecutionFilters">Reset</a-button>
        </a-space>
        <a-space wrap>
          <a-tag color="blue">Total {{ executionSummary.totalCount || 0 }}</a-tag>
          <a-tag color="green">Success {{ executionSummary.successCount || 0 }}</a-tag>
          <a-tag color="red">Failed {{ executionSummary.failedCount || 0 }}</a-tag>
          <a-tag v-for="item in summaryCategoryTags" :key="item.key">{{ item.label }}</a-tag>
        </a-space>
      </a-space>
      <a-table :columns="executionColumns" :data-source="executions" row-key="id" size="small" style="margin-top: 12px;">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'SUCCESS' ? 'green' : 'red'">{{ record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'summary'">
            <span>
              studies {{ record.transferredStudyCount }} / instances {{ record.transferredInstanceCount }}
              / loop-skips {{ record.sourceLoopSkipCount }} / attempts {{ record.attemptCount }}
            </span>
          </template>
          <template v-else-if="column.key === 'errorCategory'">
            <span>{{ record.errorCategory || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'errorMessage'">
            <span>{{ record.errorMessage || '-' }}</span>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  createSyncJob,
  deleteSyncJob,
  getSyncExecutionSummary,
  listSyncExecutions,
  listSyncJobs,
  triggerPullJob,
  triggerPushJob,
  updateSyncJob,
} from '../api/admin'

const jobs = ref([])
const executions = ref([])
const executionSummary = ref({
  totalCount: 0,
  successCount: 0,
  failedCount: 0,
  statusCounts: {},
  jobCounts: {},
  errorCategoryCounts: {},
})
const selectedJobName = ref(null)

const jobTypes = [
  { label: 'PULL', value: 'PULL' },
  { label: 'PUSH', value: 'PUSH' },
]

const statusOptions = [
  { label: 'SUCCESS', value: 'SUCCESS' },
  { label: 'FAILED', value: 'FAILED' },
]

const executionFilters = reactive({
  status: undefined,
  jobName: '',
  errorCategory: undefined,
  limit: 50,
})

const jobForm = reactive({
  jobName: '',
  jobType: 'PULL',
  targetAet: '',
  destinationAet: '',
  patientId: '',
  modality: '',
  studyDateLookbackDays: 1,
  maxStudiesPerRun: 0,
  maxInstancesPerRun: 0,
  throttleDelayMs: 0,
  maxRetryCount: 0,
  failureThreshold: 3,
  preventLoopToSource: true,
  skipRemoteDuplicates: true,
  sourceAetAllowListText: '',
  sourceAetBlockListText: '',
  paused: false,
  enabled: true,
})

const jobColumns = [
  { title: 'Job Name', dataIndex: 'jobName', key: 'jobName' },
  { title: 'Type', dataIndex: 'jobType', key: 'jobType', width: 90 },
  { title: 'Target AET', dataIndex: 'targetAet', key: 'targetAet' },
  { title: 'State', dataIndex: 'paused', key: 'paused', width: 100 },
  { title: 'Limits', key: 'limits', width: 170 },
  { title: 'Failures', dataIndex: 'consecutiveFailureCount', key: 'consecutiveFailureCount', width: 90 },
  { title: 'Last Error', key: 'lastError', width: 130 },
  { title: 'Enabled', dataIndex: 'enabled', key: 'enabled', width: 110 },
  { title: 'Actions', key: 'actions', width: 180 },
]

const executionColumns = [
  { title: 'Started At', dataIndex: 'startedAt', key: 'startedAt', width: 220 },
  { title: 'Job Name', dataIndex: 'jobName', key: 'jobName', width: 180 },
  { title: 'Type', dataIndex: 'jobType', key: 'jobType', width: 80 },
  { title: 'Target AET', dataIndex: 'targetAet', key: 'targetAet', width: 120 },
  { title: 'Status', dataIndex: 'status', key: 'status', width: 110 },
  { title: 'Summary', key: 'summary' },
  { title: 'Category', dataIndex: 'errorCategory', key: 'errorCategory', width: 140 },
  { title: 'Error', dataIndex: 'errorMessage', key: 'errorMessage' },
]

const jobStats = computed(() => ({
  total: jobs.value.length,
  enabled: jobs.value.filter((job) => job.enabled).length,
  paused: jobs.value.filter((job) => job.paused).length,
  disabled: jobs.value.filter((job) => !job.enabled).length,
}))

const errorCategoryOptions = computed(() =>
  Object.keys(executionSummary.value.errorCategoryCounts || {})
    .filter((key) => key !== 'NONE')
    .map((key) => ({ label: key, value: key })),
)

const summaryCategoryTags = computed(() =>
  Object.entries(executionSummary.value.errorCategoryCounts || {})
    .filter(([key]) => key !== 'NONE')
    .map(([key, value]) => ({ key, label: `${key} ${value}` })),
)

function jobRow(record) {
  return {
    onClick: () => selectJob(record),
    style: 'cursor:pointer',
  }
}

function selectJob(record) {
  selectedJobName.value = record.jobName
  Object.assign(jobForm, {
    jobName: record.jobName,
    jobType: record.jobType,
    targetAet: record.targetAet || '',
    destinationAet: record.destinationAet || '',
    patientId: record.patientId || '',
    modality: record.modality || '',
    studyDateLookbackDays: record.studyDateLookbackDays ?? 1,
    maxStudiesPerRun: record.maxStudiesPerRun ?? 0,
    maxInstancesPerRun: record.maxInstancesPerRun ?? 0,
    throttleDelayMs: record.throttleDelayMs ?? 0,
    maxRetryCount: record.maxRetryCount ?? 0,
    failureThreshold: record.failureThreshold ?? 3,
    preventLoopToSource: record.preventLoopToSource ?? true,
    skipRemoteDuplicates: record.skipRemoteDuplicates ?? true,
    sourceAetAllowListText: toCsv(record.sourceAetAllowList),
    sourceAetBlockListText: toCsv(record.sourceAetBlockList),
    paused: record.paused ?? false,
    enabled: record.enabled ?? true,
  })
}

function resetJob() {
  selectedJobName.value = null
  Object.assign(jobForm, {
    jobName: '',
    jobType: 'PULL',
    targetAet: '',
    destinationAet: '',
    patientId: '',
    modality: '',
    studyDateLookbackDays: 1,
    maxStudiesPerRun: 0,
    maxInstancesPerRun: 0,
    throttleDelayMs: 0,
    maxRetryCount: 0,
    failureThreshold: 3,
    preventLoopToSource: true,
    skipRemoteDuplicates: true,
    sourceAetAllowListText: '',
    sourceAetBlockListText: '',
    paused: false,
    enabled: true,
  })
}

function resetExecutionFilters() {
  executionFilters.status = undefined
  executionFilters.jobName = ''
  executionFilters.errorCategory = undefined
  void loadExecutions()
}

async function loadJobs() {
  jobs.value = await listSyncJobs()
}

async function loadExecutions() {
  const params = {
    status: executionFilters.status,
    jobName: executionFilters.jobName || undefined,
    errorCategory: executionFilters.errorCategory,
    limit: executionFilters.limit,
  }
  const [executionData, summary] = await Promise.all([
    listSyncExecutions(params),
    getSyncExecutionSummary(params),
  ])
  executions.value = executionData
  executionSummary.value = summary
}

async function reloadAll() {
  await Promise.all([loadJobs(), loadExecutions()])
}

async function saveJob() {
  try {
    const payload = {
      ...jobForm,
      sourceAetAllowList: parseCsv(jobForm.sourceAetAllowListText),
      sourceAetBlockList: parseCsv(jobForm.sourceAetBlockListText),
    }
    delete payload.sourceAetAllowListText
    delete payload.sourceAetBlockListText
    if (selectedJobName.value) {
      await updateSyncJob(selectedJobName.value, payload)
    } else {
      await createSyncJob(payload)
    }
    await reloadAll()
    message.success('Sync job saved')
  } catch (error) {
    message.error(error.message || 'Failed to save sync job')
  }
}

async function removeJob(record) {
  try {
    await deleteSyncJob(record.jobName)
    await reloadAll()
    if (selectedJobName.value === record.jobName) {
      resetJob()
    }
    message.success('Sync job deleted')
  } catch (error) {
    message.error(error.message || 'Failed to delete sync job')
  }
}

async function runJob(record) {
  try {
    if (record.jobType === 'PULL') {
      await triggerPullJob(record.jobName)
    } else {
      await triggerPushJob(record.jobName)
    }
    await reloadAll()
    message.success('Sync job triggered')
  } catch (error) {
    message.error(error.message || 'Failed to trigger sync job')
  }
}

function parseCsv(value) {
  return (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function toCsv(value) {
  return Array.isArray(value) ? value.join(',') : ''
}

onMounted(async () => {
  try {
    await reloadAll()
  } catch (error) {
    message.error(error.message || 'Failed to load sync admin data')
  }
})
</script>
