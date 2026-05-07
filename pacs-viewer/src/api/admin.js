import { get, post, put, remove } from './client'

export function listAets() {
  return get('/api/admin/aets')
}

export function createAet(payload) {
  return post('/api/admin/aets', payload)
}

export function updateAet(aet, payload) {
  return put(`/api/admin/aets/${aet}`, payload)
}

export function deleteAet(aet) {
  return remove(`/api/admin/aets/${aet}`)
}

export function echoAet(targetAet) {
  return post('/api/admin/dicom/echo', { targetAet })
}

export function remoteFindStudies(targetAet, criteria) {
  return post('/api/admin/dicom/remote-find/studies', { targetAet, criteria })
}

export function remotePull(targetAet, criteria, destinationAet) {
  return post('/api/admin/dicom/remote-pull', { targetAet, criteria, destinationAet })
}

export function listSyncJobs() {
  return get('/api/admin/sync/jobs')
}

export function createSyncJob(payload) {
  return post('/api/admin/sync/jobs', payload)
}

export function updateSyncJob(jobName, payload) {
  return put(`/api/admin/sync/jobs/${jobName}`, payload)
}

export function deleteSyncJob(jobName) {
  return remove(`/api/admin/sync/jobs/${jobName}`)
}

export function listSyncExecutions(params = {}) {
  return get('/api/admin/sync/executions', { params })
}

export function getSyncExecutionSummary(params = {}) {
  return get('/api/admin/sync/executions/summary', { params })
}

export function triggerPullJob(jobName) {
  return post(`/api/admin/sync/pull/${jobName}`, {})
}

export function triggerPushJob(jobName) {
  return post(`/api/admin/sync/push/${jobName}`, {})
}
