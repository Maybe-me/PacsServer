<template>
  <div class="viewer-workspace">
    <div class="viewer-shell">
      <aside class="viewer-sidebar">
        <PatientList @search="loadStudies" />
        <StudyList :studies="state.studies" @select="selectStudy" />
        <SeriesList :series="state.series" @select="selectSeries" />
      </aside>

      <main class="viewer-main">
        <Toolbar
          :layout="state.layout"
          :active-tool="state.activeTool"
          :cine-playing="state.cinePlaying"
          :selected-instance="state.inspectedInstance"
          :rendered-instance="activeViewport?.instance || null"
          :rendered-frame-number="activeViewport?.frameNumber || 1"
          :has-viewport-data="Boolean(activeViewport?.instances?.length)"
          @layout-change="changeLayout"
          @tool-change="setActiveTool"
          @scroll="scrollViewport"
          @toggle-invert="toggleInvert"
          @reset-viewport="resetViewport"
          @toggle-cine="toggleCine"
        />

        <div class="viewer-stage" :class="`viewer-stage--${state.layout}`">
          <DicomViewport
            v-for="viewport in viewports"
            :key="viewport.id"
            :ref="(element) => setViewportRef(viewport.id, element)"
            :viewport-id="viewport.id"
            :title="viewport.title"
            :assignment="state.viewportAssignments[viewport.id]"
            :active="viewport.id === state.activeViewportId"
            :active-tool="state.activeTool"
            @activate="setActiveViewport"
            @image-change="handleViewportImageChange"
            @render-error="handleViewportRenderError"
          />
        </div>
      </main>

      <aside class="viewer-right-panel">
        <StudyBrowser
          :instances="state.instances"
          :selected-instance-uid="state.inspectedInstance?.sopInstanceUid || ''"
          @select="selectInstance"
        />

        <a-card size="small" title="Study Details" class="viewer-panel-card" :bordered="false">
          <a-descriptions bordered :column="1" size="small">
            <a-descriptions-item label="Study UID">
              <span class="mono">{{ state.selectedStudy?.studyInstanceUid || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Series UID">
              <span class="mono">{{ state.selectedSeries?.seriesInstanceUid || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Instance UID">
              <span class="mono">{{ state.inspectedInstance?.sopInstanceUid || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Rendered Instance UID">
              <span class="mono">{{ activeViewport?.instance?.sopInstanceUid || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Rendered Frame">
              <span>{{ activeViewport?.instance ? activeViewport?.frameNumber || 1 : '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Transfer Syntax">
              <span class="mono">{{ state.inspectedInstance?.transferSyntaxUid || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Photometric">
              <span class="mono">{{ state.inspectedInstance?.photometricInterpretation || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Matrix">
              <span>{{ state.inspectedInstance ? `${state.inspectedInstance.rows || 0} x ${state.inspectedInstance.columns || 0}` : '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Frames">
              <span>{{ state.inspectedInstance?.numberOfFrames || 1 }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Failed Frames">
              <span>{{ state.inspectedInstance?.failedFrames?.length || 0 }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Frame Recovery">
              <span class="mono">{{ inspectedFrameIssueSummary }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Samples / Pixel">
              <span>{{ state.inspectedInstance?.samplesPerPixel || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Bits Allocated">
              <span>{{ state.inspectedInstance?.bitsAllocated || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Bits Stored">
              <span>{{ state.inspectedInstance?.bitsStored ?? '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="High Bit">
              <span>{{ state.inspectedInstance?.highBit ?? '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Pixel Representation">
              <span>{{ state.inspectedInstance?.pixelRepresentation ?? '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Displayable Instances">
              <span>{{ displayableInstanceCount }}/{{ state.instances.length || 0 }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Incompatible Instances">
              <span>{{ incompatibleInstanceCount }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="Viewer Status">
              <span class="mono">{{ state.inspectedInstance?.displayIssue || 'Compatible' }}</span>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card size="small" title="Series Diagnostics" class="viewer-panel-card" :bordered="false">
          <template v-if="state.instances.length">
            <div class="viewer-diagnostics-summary">
              <span>Total {{ state.instances.length }}</span>
              <span>Displayable {{ displayableInstanceCount }}</span>
              <span>Degraded {{ degradedInstanceCount }}</span>
              <span>Failed Frames {{ failedFrameCount }}</span>
              <span>Incompatible {{ incompatibleInstanceCount }}</span>
            </div>
            <div v-if="degradedIssueBreakdown.length" class="viewer-diagnostics-list">
              <div v-for="item in degradedIssueBreakdown" :key="item.reason" class="viewer-diagnostics-item">
                <span>{{ item.reason }}</span>
                <a-tag color="warning">{{ item.count }}</a-tag>
              </div>
            </div>
            <div v-if="issueBreakdown.length" class="viewer-diagnostics-list">
              <div v-for="item in issueBreakdown" :key="item.reason" class="viewer-diagnostics-item">
                <span>{{ item.reason }}</span>
                <a-tag color="error">{{ item.count }}</a-tag>
              </div>
            </div>
            <a-empty v-else description="No compatibility issues in this series" />
          </template>
          <a-empty v-else description="Select a series to inspect diagnostics" />
        </a-card>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getViewerCompatibility, listInstances, listSeries, listStudies } from '../api/viewer'
import PatientList from './PatientList.vue'
import StudyList from './StudyList.vue'
import SeriesList from './SeriesList.vue'
import Toolbar from '../components/Toolbar.vue'
import StudyBrowser from '../components/StudyBrowser.vue'
import DicomViewport from '../components/DicomViewport.vue'
import { useViewerStore } from '../composables/useViewerStore'

const {
  state,
  viewports,
  activeViewport,
  setLayout,
  setStudies,
  setSelectedStudy,
  setSeries,
  updateSeriesDiagnostics,
  setSelectedSeries,
  setInstances,
  setDisplayInstances,
  setInspectedInstance,
  setSelectedInstance,
  assignSeriesToViewport,
  assignViewport,
  updateViewportRenderedItem,
  setActiveViewport,
  setActiveTool,
  setCinePlaying,
} = useViewerStore()

const viewportRefs = ref({})
const incompatibleInstanceCount = computed(() => state.instances.filter((instance) => instance.displayable === false).length)
const displayableInstanceCount = computed(() => state.displayInstances.length)
const degradedInstances = computed(() =>
  state.instances.filter((instance) => instance.displayable !== false && (instance.failedFrames?.length || 0) > 0)
)
const degradedInstanceCount = computed(() => degradedInstances.value.length)
const failedFrameCount = computed(() =>
  degradedInstances.value.reduce((total, instance) => total + (instance.failedFrames?.length || 0), 0)
)

function getLatestFrameIssue(instance) {
  const frameIssues = Array.isArray(instance?.frameIssues) ? instance.frameIssues : []
  return frameIssues[frameIssues.length - 1] || null
}

const inspectedFrameIssueSummary = computed(() => {
  const failedFrames = state.inspectedInstance?.failedFrames?.length || 0
  if (!failedFrames) {
    return 'No skipped frames'
  }
  const latestFrameIssue = getLatestFrameIssue(state.inspectedInstance)
  if (!latestFrameIssue?.message) {
    return `${failedFrames} frame(s) skipped`
  }
  return `${failedFrames} frame(s) skipped. Latest issue: ${latestFrameIssue.message}`
})
const degradedIssueBreakdown = computed(() => {
  const counts = new Map()
  degradedInstances.value.forEach((instance) => {
    const issues = instance.frameIssues?.length
      ? instance.frameIssues
      : [{ message: 'Skipped frame(s) after runtime render/load failure.' }]
    issues.forEach((issue) => {
      const reason = issue?.message || 'Skipped frame(s) after runtime render/load failure.'
      counts.set(reason, (counts.get(reason) || 0) + 1)
    })
  })
  return Array.from(counts.entries())
    .map(([reason, count]) => ({ reason, count }))
    .sort((left, right) => right.count - left.count)
})
const issueBreakdown = computed(() => {
  const counts = new Map()
  state.instances
    .filter((instance) => instance.displayable === false)
    .forEach((instance) => {
      const reason = instance.displayIssue || 'Unknown incompatibility'
      counts.set(reason, (counts.get(reason) || 0) + 1)
    })
  return Array.from(counts.entries())
    .map(([reason, count]) => ({ reason, count }))
    .sort((left, right) => right.count - left.count)
})

function setViewportRef(viewportId, element) {
  if (element) {
    viewportRefs.value[viewportId] = element
  }
}

function getDisplayableInstances(instances = []) {
  return instances.filter((instance) => instance.displayable)
}

function buildSeriesDiagnostics(instances = [], compatibleInstances = getDisplayableInstances(instances)) {
  const degradedInstances = instances.filter(
    (instance) => instance.displayable !== false && (instance.failedFrames?.length || 0) > 0
  )
  const failedFrameCount = degradedInstances.reduce((total, instance) => total + (instance.failedFrames?.length || 0), 0)
  const summaryParts = [`${compatibleInstances.length}/${instances.length} ok`]
  if (degradedInstances.length) {
    summaryParts.push(`${degradedInstances.length} degraded`)
  }
  if (failedFrameCount) {
    summaryParts.push(`${failedFrameCount} frames skipped`)
  }
  return {
    displayableCount: compatibleInstances.length,
    degradedCount: degradedInstances.length,
    failedFrameCount,
    incompatibleCount: instances.length - compatibleInstances.length,
    compatibilitySummary: summaryParts.join(' · '),
  }
}

function getRenderableFrameNumbers(instance) {
  const totalFrames = Math.max(1, Number(instance?.numberOfFrames) || 1)
  const failedFrames = new Set((instance?.failedFrames || []).map((value) => Number(value)))
  return Array.from({ length: totalFrames }, (_, index) => index + 1).filter((frameNumber) => !failedFrames.has(frameNumber))
}

function normalizeRenderError(errorMessage, instance) {
  const messageText = `${errorMessage || ''}`.trim()
  if (!messageText) {
    return 'Failed to render this instance.'
  }
  if (messageText.includes('No color space conversion for photometric interpretation')) {
    return `Unsupported photometric interpretation: ${instance?.photometricInterpretation || 'unknown'}.`
  }
  if (messageText.includes('unsupported pixel format')) {
    return `Unsupported pixel format for Bits Allocated ${instance?.bitsAllocated || 'unknown'}, Bits Stored ${instance?.bitsStored || 'unknown'}, High Bit ${instance?.highBit ?? 'unknown'}.`
  }
  if (messageText.includes('frame exceeds size of pixelData')) {
    return 'Invalid multi-frame pixel data layout.'
  }
  if (messageText.includes('The pixel data is missing')) {
    return 'Missing pixel data.'
  }
  return messageText
}

function applyInstanceIssue(sopInstanceUid, displayIssue) {
  const patchedInstances = state.instances.map((item) =>
    item.sopInstanceUid === sopInstanceUid
      ? {
          ...item,
          displayable: false,
          displayIssue,
        }
      : item
  )
  const compatibleInstances = getDisplayableInstances(patchedInstances)
  setInstances(patchedInstances)
  setDisplayInstances(compatibleInstances)
  if (state.selectedSeries?.seriesInstanceUid) {
    updateSeriesDiagnostics(
      state.selectedSeries.seriesInstanceUid,
      buildSeriesDiagnostics(patchedInstances, compatibleInstances)
    )
  }
  return compatibleInstances
}

function applyFrameIssue(sopInstanceUid, frameNumber, displayIssue) {
  let patchedInstance = null
  const patchedInstances = state.instances.map((item) => {
    if (item.sopInstanceUid !== sopInstanceUid) {
      return item
    }
    const totalFrames = Math.max(1, Number(item.numberOfFrames) || 1)
    const failedFrames = Array.from(
      new Set(
        [...(item.failedFrames || []), frameNumber]
          .map((value) => Number(value))
          .filter((value) => Number.isFinite(value) && value >= 1 && value <= totalFrames)
      )
    ).sort((left, right) => left - right)
    const allFramesFailed = failedFrames.length >= totalFrames
    const frameIssues = [
      ...(item.frameIssues || []).filter((entry) => Number(entry?.frameNumber) !== frameNumber),
      {
        frameNumber,
        message: displayIssue,
      },
    ].sort((left, right) => Number(left.frameNumber) - Number(right.frameNumber))
    patchedInstance = {
      ...item,
      failedFrames,
      frameIssues,
      displayable: allFramesFailed ? false : item.displayable,
      displayIssue: allFramesFailed ? displayIssue : item.displayIssue,
    }
    return patchedInstance
  })
  const compatibleInstances = getDisplayableInstances(patchedInstances)
  setInstances(patchedInstances)
  setDisplayInstances(compatibleInstances)
  if (state.selectedSeries?.seriesInstanceUid) {
    updateSeriesDiagnostics(
      state.selectedSeries.seriesInstanceUid,
      buildSeriesDiagnostics(patchedInstances, compatibleInstances)
    )
  }
  return {
    compatibleInstances,
    patchedInstance,
  }
}

async function loadStudies(filters = {}) {
  try {
    setStudies(await listStudies(filters), filters)
  } catch (error) {
    message.error(error.message || 'Failed to load studies')
  }
}

async function selectStudy(study) {
  setSelectedStudy(study)
  try {
    setSeries(await listSeries(study.studyInstanceUid))
  } catch (error) {
    message.error(error.message || 'Failed to load series')
  }
}

async function selectSeries(series) {
  setSelectedSeries(series)
  try {
    const loadedInstances = await listInstances(state.selectedStudy.studyInstanceUid, series.seriesInstanceUid)
    setInstances(loadedInstances)
    const displayableInstances = getDisplayableInstances(loadedInstances)
    setDisplayInstances(displayableInstances)
    if (!displayableInstances.length) {
      updateSeriesDiagnostics(series.seriesInstanceUid, buildSeriesDiagnostics(loadedInstances, displayableInstances))
      const firstIssue = loadedInstances.find((instance) => instance.displayIssue)?.displayIssue
      message.warning(firstIssue || 'This series does not contain viewer-compatible image data.')
      return
    }
    updateSeriesDiagnostics(series.seriesInstanceUid, buildSeriesDiagnostics(loadedInstances, displayableInstances))
    if (displayableInstances.length !== loadedInstances.length) {
      message.info(`Skipped ${loadedInstances.length - displayableInstances.length} incompatible instance(s) in this series.`)
    }
    assignSeriesToViewport(series, displayableInstances, state.activeViewportId)
  } catch (error) {
    message.error(error.message || 'Failed to load instances')
  }
}

function selectInstance(instance) {
  setInspectedInstance(instance)
  const compatibility = getViewerCompatibility(instance)
  if (!compatibility.displayable) {
    message.warning(compatibility.reason || 'The selected instance is not viewer-compatible.')
    return
  }
  setSelectedInstance(instance, state.activeViewportId)
}

function handleViewportRenderError({ viewportId, instance, frameNumber, message: renderMessage }) {
  if (!instance?.sopInstanceUid) {
    return
  }
  const normalizedIssue = normalizeRenderError(renderMessage, instance)
  const frameSuffix = frameNumber && Number(frameNumber) > 1 ? ` (frame ${frameNumber})` : ''
  if ((Number(instance?.numberOfFrames) || 1) > 1 && Number(frameNumber) >= 1) {
    const { compatibleInstances, patchedInstance } = applyFrameIssue(instance.sopInstanceUid, Number(frameNumber), normalizedIssue)
    const remainingFrames = getRenderableFrameNumbers(patchedInstance)
    if (!compatibleInstances.length) {
      message.warning(`${normalizedIssue}${frameSuffix}`)
      return
    }
    if (patchedInstance?.displayable !== false && remainingFrames.length) {
      const nextFrameNumber = remainingFrames.find((value) => value > Number(frameNumber)) ?? remainingFrames[0]
      assignViewport(viewportId, state.selectedSeries, compatibleInstances, patchedInstance, nextFrameNumber)
      message.warning(`${normalizedIssue}${frameSuffix} Skipped the failed frame and kept the remaining frames available.`)
      return
    }
  }
  const compatibleInstances = applyInstanceIssue(instance.sopInstanceUid, normalizedIssue)
  if (!compatibleInstances.length) {
    message.warning(`${normalizedIssue}${frameSuffix}`)
    return
  }
  assignSeriesToViewport(state.selectedSeries, compatibleInstances, viewportId)
  message.warning(`${normalizedIssue}${frameSuffix} Switched to the next compatible instance.`)
}

function handleViewportImageChange({ viewportId, stackItem }) {
  updateViewportRenderedItem(viewportId, stackItem)
}

function changeLayout(layout) {
  setLayout(layout)
}

function withActiveViewport(callback) {
  const viewport = viewportRefs.value[state.activeViewportId]
  if (!viewport) {
    return
  }
  return callback(viewport)
}

function scrollViewport(delta) {
  withActiveViewport((viewport) => viewport.scroll(delta))
}

function toggleInvert() {
  withActiveViewport((viewport) => viewport.toggleInvert())
}

function resetViewport() {
  withActiveViewport((viewport) => viewport.resetViewport())
}

function toggleCine() {
  const result = withActiveViewport((viewport) => viewport.toggleCine())
  if (typeof result === 'boolean') {
    setCinePlaying(result)
  }
}

onMounted(() => {
  loadStudies()
})
</script>
