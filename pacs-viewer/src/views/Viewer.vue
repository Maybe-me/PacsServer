<template>
  <div class="viewer-workspace" :style="workspaceGridStyle">
    <Header
      class="grid-header"
      :patient-name="state.selectedStudy?.patientName || ''"
      :mrn="state.selectedStudy?.patientId || ''"
      :study-description="state.selectedStudy?.studyDescription || ''"
      @toggle-sidebars="toggleSidebars"
    />

    <Toolbar
      class="grid-toolbar"
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
      @projection-change="handleProjectionChange"
    />

    <!-- Left Panel - Study Browser -->
    <aside v-show="leftPanelOpen" class="viewer-sidebar grid-left">
      <!-- Mode 1: Study Selection (when no study is active / expandedStudyUid is empty) -->
      <template v-if="!expandedStudyUid">
        <!-- Search Header -->
        <div class="study-browser-search">
          <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            v-model="searchQuery"
            type="text"
            placeholder="Search patient/ID..."
            @keyup.enter="loadStudies({ patientId: searchQuery })"
          />
        </div>

        <!-- Study Browser List -->
        <div class="study-browser-content scrollable-panel">
          <div class="study-list-header">Studies / Patients</div>
          <div v-if="state.studies.length > 0" class="study-tree">
            <div
              v-for="study in state.studies"
              :key="study.studyInstanceUid"
              class="study-list-card"
              @click="toggleStudy(study)"
            >
              <div class="study-card-left">
                <div class="study-card-name truncate">{{ study.patientName || 'Unknown Patient' }}</div>
                <div class="study-card-mrn">{{ study.patientId || 'MRN-00000' }}</div>
                <div class="study-card-desc truncate">{{ study.studyDescription || 'No Description' }}</div>
              </div>
              <div class="study-card-right">
                <span class="study-card-modality">{{ study.modalitiesInStudy || 'MR' }}</span>
                <span class="study-card-date">{{ formatDateShort(study.studyDate) }}</span>
              </div>
            </div>
          </div>
          
          <!-- Empty State -->
          <div v-else-if="!loadingStudies" class="study-browser-empty">
            <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/>
            </svg>
            <div>No studies found</div>
            <div class="empty-sub">Use search above to find patient studies</div>
          </div>

          <div v-if="loadingStudies" class="study-browser-loading">
            <div class="spinner"></div>
            <div class="loading-text">Loading studies...</div>
          </div>
        </div>
      </template>

      <!-- Mode 2: Flat Series Thumbnail Grid (when a study is active/expanded) -->
      <template v-else>
        <!-- Navigation header to go back to Study List -->
        <div class="series-browser-header">
          <button class="back-to-studies-btn" @click="clearActiveStudy">
            <svg style="width: 14px; height: 14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/>
            </svg>
            <span>Studies</span>
          </button>
          <div class="active-patient-summary">
            <div class="patient-name truncate">{{ state.selectedStudy?.patientName || 'DOE^JOHN' }}</div>
            <div class="patient-meta truncate">
              {{ state.selectedStudy?.patientId || '' }} · {{ state.selectedStudy?.modalitiesInStudy || '' }}
            </div>
          </div>
        </div>

        <!-- Series flat grid display -->
        <div class="study-browser-content scrollable-panel">
          <div v-if="loadingSeries" class="study-tree-loading">
            <div class="spinner"></div>
            <div class="loading-text">Loading series...</div>
          </div>
          
          <div v-else class="series-flat-list">
            <div
              v-for="s in state.series"
              :key="s.seriesInstanceUid"
              class="series-flat-card"
              :class="{ active: s.seriesInstanceUid === state.selectedSeries?.seriesInstanceUid }"
              @click="selectSeries(s)"
            >
              <!-- Card Header -->
              <div class="series-flat-card-header">
                <div class="series-number-desc">
                  <span class="series-number">{{ s.seriesNumber || '1' }}</span>
                  <span class="series-desc truncate">{{ s.seriesDescription || 'No Description' }}</span>
                </div>
                <span class="series-modality-badge">{{ s.modality || 'MR' }}</span>
              </div>
              
              <!-- Card Body (Vertical) -->
              <div class="series-flat-card-body">
                <div class="series-flat-thumbnail-shell">
                  <SeriesThumbnail
                    :study-instance-uid="state.selectedStudy?.studyInstanceUid"
                    :series-instance-uid="s.seriesInstanceUid"
                    :modality="s.modality"
                  />
                </div>
                <div class="series-flat-metadata">
                  <div class="meta-row"><span class="highlight-text">{{ s.numInstances || 0 }}</span> Images</div>
                  <div v-if="s.sliceThickness" class="meta-row">TH: {{ s.sliceThickness }}mm</div>
                  <div v-if="s.spacingBetweenSlices" class="meta-row">SP: {{ s.spacingBetweenSlices }}mm</div>
                </div>
              </div>
            </div>
            <div v-if="state.series.length === 0" class="study-tree-empty">
              No series found
            </div>
          </div>
        </div>
      </template>
    </aside>

    <!-- Sidebar Resizer -->
    <div v-show="leftPanelOpen" class="sidebar-resizer grid-resizer-left" @mousedown.prevent="startResize('left', $event)"></div>

    <!-- Main Viewport Area -->
    <main class="viewer-main grid-main">
      <div class="viewer-stage" :class="`viewer-stage--${state.layout}`">
        <ViewportContainer
          v-for="viewport in viewports"
          :key="viewport.id"
          :ref="(element) => setViewportRef(viewport.id, element)"
          :viewport-id="viewport.id"
          :title="viewport.title"
          :mpr-orientation="viewport.mprOrientation"
          :assignment="state.viewportAssignments[viewport.id]"
          :active="viewport.id === state.activeViewportId"
          :active-tool="state.activeTool"
          @activate="setActiveViewport"
          @image-change="handleViewportImageChange"
          @render-error="handleViewportRenderError"
          @sr-node-click="handleSrNodeClick"
        />
      </div>
    </main>

    <!-- Right Panel Resizer -->
    <div v-show="rightPanelOpen" class="sidebar-resizer grid-resizer-right" @mousedown.prevent="startResize('right', $event)"></div>

    <!-- Right Panel (Dual Tabs: Measurements & Info) -->
    <aside v-show="rightPanelOpen" class="viewer-right-panel grid-right">
      <div class="right-panel-tabs">
        <button
          class="right-tab-btn"
          :class="{ active: activeRightTab === 'measurements' }"
          @click="activeRightTab = 'measurements'"
        >
          <span>Measurements</span>
          <span class="tab-badge" v-if="state.measurements.length">{{ state.measurements.length }}</span>
        </button>
        <button
          class="right-tab-btn"
          :class="{ active: activeRightTab === 'info' }"
          @click="activeRightTab = 'info'"
        >
          <span>Info</span>
        </button>
      </div>

      <div class="right-panel-content scrollable-panel">
        <!-- Measurements List Panel -->
        <div v-if="activeRightTab === 'measurements'" class="measurements-tab-panel">
          <div v-if="state.measurements.length === 0" class="empty-panel-state">
            <svg style="width: 32px; height: 32px; opacity: 0.4; margin-bottom: 8px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L6.832 19.82a4.5 4.5 0 01-1.897 1.13l-2.685.8.8-2.685a4.5 4.5 0 011.13-1.897L16.863 4.487zm0 0L19.5 7.125"/>
            </svg>
            <div>No measurements yet</div>
            <div class="sub-text">Use Length or Angle tool on any viewport</div>
          </div>
          
          <div v-else class="measurements-list">
            <div
              v-for="m in state.measurements"
              :key="m.annotationUID"
              class="measurement-list-card"
              @click="jumpToMeasurementSeries(m)"
            >
              <div class="card-left-info">
                <div class="tool-label-row">
                  <span class="tool-badge" :class="m.toolName.toLowerCase()">{{ m.toolName }}</span>
                  <span class="series-name truncate">{{ m.seriesDescription }}</span>
                </div>
                <div class="measurement-value">{{ m.value }}</div>
              </div>
              <button class="delete-measurement-btn" @click.stop="handleDeleteMeasurement(m.annotationUID)">
                <svg style="width: 14px; height: 14px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>
        </div>

        <!-- Info Details Panel -->
        <div v-else class="info-tab-panel">
          <div class="info-panel-section">
            <div class="info-panel-header">Study Details</div>
            <div class="info-panel-content">
              <div class="info-row"><span class="info-label">Patient</span><span class="info-value truncate">{{ state.selectedStudy?.patientName || '-' }}</span></div>
              <div class="info-row"><span class="info-label">MRN</span><span class="info-value">{{ state.selectedStudy?.patientId || '-' }}</span></div>
              <div class="info-row"><span class="info-label">DOB</span><span class="info-value">{{ formatDateShort(state.selectedStudy?.patientBirthDate) }}</span></div>
              <div class="info-row"><span class="info-label">Study Date</span><span class="info-value">{{ formatDateShort(state.selectedStudy?.studyDate) }}</span></div>
              <div class="info-row"><span class="info-label">Modality</span><span class="info-value">{{ state.selectedStudy?.modalitiesInStudy || '-' }}</span></div>
            </div>
          </div>
          
          <div class="info-panel-section">
            <div class="info-panel-header">Series Info</div>
            <div class="info-panel-content">
              <div class="info-row"><span class="info-label">Description</span><span class="info-value truncate">{{ state.selectedSeries?.seriesDescription || '-' }}</span></div>
              <div class="info-row"><span class="info-label">Modality</span><span class="info-value">{{ state.selectedSeries?.modality || '-' }}</span></div>
              <div class="info-row"><span class="info-label">Instances</span><span class="info-value">{{ state.instances.length }}</span></div>
              <div class="info-row"><span class="info-label">Displayable</span><span class="info-value success-text">{{ displayableInstanceCount }}</span></div>
              <div v-if="degradedInstanceCount > 0" class="info-row"><span class="info-label">Degraded</span><span class="info-value warning-text">{{ degradedInstanceCount }}</span></div>
            </div>
          </div>
        </div>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useToast } from '../composables/useToast'
import { listInstances, listSeries, listStudies } from '../api/viewer'
import Header from '../components/Header.vue'
import Toolbar from '../components/Toolbar.vue'
import ViewportContainer from '../components/ViewportContainer.vue'
import SeriesThumbnail from '../components/SeriesThumbnail.vue'
import { useViewerStore } from '../composables/useViewerStore'
import { deleteAnnotation, setViewportProjectionMode } from '../lib/cornerstone'

const {
  state,
  viewports,
  activeViewport,
  setLayout,
  setStudies,
  setSelectedStudy,
  setSeries,
  setSelectedSeries,
  setInstances,
  setDisplayInstances,
  setSelectedInstance,
  assignSeriesToViewport,
  updateViewportRenderedItem,
  setActiveViewport,
  setActiveTool,
  setCinePlaying,
  removeMeasurement,
} = useViewerStore()

const viewportRefs = ref({})
const leftPanelOpen = ref(true)
const rightPanelOpen = ref(true)
const leftPanelWidth = ref(320)
const rightPanelWidth = ref(320)
const searchQuery = ref('')
const expandedStudyUid = ref('')
const loadingStudies = ref(false)
const loadingSeries = ref(false)
const activeRightTab = ref('measurements')

const displayableInstanceCount = computed(() => state.displayInstances.length)
const degradedInstances = computed(() =>
  state.instances.filter((i) => i.displayable !== false && (i.failedFrames?.length || 0) > 0)
)
const degradedInstanceCount = computed(() => degradedInstances.value.length)

const workspaceGridStyle = computed(() => {
  const leftW = leftPanelOpen.value ? `${leftPanelWidth.value}px 4px` : '0px 0px';
  const rightW = rightPanelOpen.value ? `4px ${rightPanelWidth.value}px` : '0px 0px';
  return {
    display: 'grid',
    gridTemplateColumns: `${leftW} 1fr ${rightW}`,
    gridTemplateRows: 'auto auto 1fr',
    gridTemplateAreas: `
      "header header header header header"
      "toolbar toolbar toolbar toolbar toolbar"
      "left rleft main rright right"
    `
  }
})

function setViewportRef(viewportId, element) {
  if (element) viewportRefs.value[viewportId] = element
}

function getDisplayableInstances(instances = []) {
  return instances.filter((i) => i.displayable)
}

function toggleSidebars() {
  const allClosed = !leftPanelOpen.value && !rightPanelOpen.value
  leftPanelOpen.value = allClosed ? true : !leftPanelOpen.value
  rightPanelOpen.value = allClosed ? true : !rightPanelOpen.value
}

function startResize(side, event) {
  const startX = event.clientX
  const startWidth = side === 'left' ? leftPanelWidth.value : rightPanelWidth.value
  function onMove(e) {
    const w = side === 'left' ? startWidth + e.clientX - startX : startWidth - e.clientX + startX
    if (side === 'left') {
      leftPanelWidth.value = Math.max(200, Math.min(600, w))
    } else {
      rightPanelWidth.value = Math.max(200, Math.min(600, w))
    }
  }
  function onUp() {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

async function loadStudies(filters = {}) {
  loadingStudies.value = true
  try {
    const studies = await listStudies(filters)
    setStudies(studies, filters)
    // Auto-select the first study to load into the workspace on mount
    if (studies.length > 0 && !expandedStudyUid.value) {
      toggleStudy(studies[0])
    }
  } catch (error) {
    message.error(error.message || 'Failed to load studies')
  } finally {
    loadingStudies.value = false
  }
}

function clearActiveStudy() {
  expandedStudyUid.value = ''
  setSelectedStudy(null)
}

function formatDateShort(dateStr) {
  if (!dateStr) return '-'
  if (dateStr.length === 8) {
    const y = dateStr.slice(0, 4)
    const m = dateStr.slice(4, 6)
    const d = dateStr.slice(6, 8)
    return `${y}-${m}-${d}`
  }
  return dateStr
}

async function toggleStudy(study) {
  expandedStudyUid.value = study.studyInstanceUid
  setSelectedStudy(study)
  loadingSeries.value = true
  try {
    const series = await listSeries(study.studyInstanceUid)
    setSeries(series)
    // Auto-select the first series to display out-of-the-box
    if (series.length > 0) {
      selectSeries(series[0])
    }
  } catch (error) {
    message.error(error.message || 'Failed to load series')
  } finally {
    loadingSeries.value = false
  }
}

async function selectSeries(series) {
  setSelectedSeries(series)
  try {
    const loadedInstances = await listInstances(state.selectedStudy.studyInstanceUid, series.seriesInstanceUid)
    setInstances(loadedInstances)
    const displayable = getDisplayableInstances(loadedInstances)
    setDisplayInstances(displayable)
    assignSeriesToViewport(series, displayable, state.activeViewportId)
  } catch (error) {
    message.error(error.message || 'Failed to load instances')
  }
}

function handleDeleteMeasurement(uid) {
  deleteAnnotation(uid)
  removeMeasurement(uid)
}

function jumpToMeasurementSeries(m) {
  const foundSeries = state.series.find((s) => s.seriesInstanceUid === m.seriesInstanceUid)
  if (foundSeries) {
    selectSeries(foundSeries)
  }
}

function handleViewportRenderError({ message: renderMessage }) {
  message.warning(renderMessage || 'Failed to render image')
}

function handleViewportImageChange({ viewportId, stackItem }) {
  updateViewportRenderedItem(viewportId, stackItem)
}

function changeLayout(layout) {
  setLayout(layout)
}

function withActiveViewport(callback) {
  const vp = viewportRefs.value[state.activeViewportId]
  if (vp) return callback(vp)
}

function scrollViewport(delta) {
  withActiveViewport((vp) => vp.scroll(delta))
}

function toggleInvert() {
  withActiveViewport((vp) => vp.toggleInvert())
}

function resetViewport() {
  withActiveViewport((vp) => vp.resetViewport())
}

function toggleCine() {
  const r = withActiveViewport((vp) => vp.toggleCine())
  if (typeof r === 'boolean') setCinePlaying(r)
}

function handleProjectionChange({ mode, thickness }) {
  const mprViewportIds = ['viewport-mpr-axial', 'viewport-mpr-sagittal', 'viewport-mpr-coronal']
  mprViewportIds.forEach((vpId) => {
    setViewportProjectionMode(vpId, mode, thickness)
  })
}

function getViewportType(viewportId) {
  const assignment = state.viewportAssignments[viewportId]
  const instance = assignment?.instance
  const series = assignment?.series
  if (!instance) return 'NONE'

  if (instance.mediaType) {
    return instance.mediaType.toUpperCase()
  }

  const sopClassUid = (instance.sopClassUid || '').trim()
  const modality = (series?.modality || instance.modality || '').trim().toUpperCase()

  if (sopClassUid === '1.2.840.10008.5.1.4.1.1.104.1' || modality === 'DOC') {
    return 'PDF'
  }
  
  const videoSopClasses = new Set([
    '1.2.840.10008.5.1.4.1.1.77.1.4.1',
    '1.2.840.10008.5.1.4.1.1.77.1.1.1',
    '1.2.840.10008.5.1.4.1.1.77.1.2.1',
  ])
  if (videoSopClasses.has(sopClassUid) || modality === 'ES' || modality === 'US_VIDEO') {
    return 'VIDEO'
  }

  const waveformSopClasses = new Set([
    '1.2.840.10008.5.1.4.1.1.9.1.1',
    '1.2.840.10008.5.1.4.1.1.9.1.2',
    '1.2.840.10008.5.1.4.1.1.9.1.3',
    '1.2.840.10008.5.1.4.1.1.9.2.1',
  ])
  if (waveformSopClasses.has(sopClassUid) || modality === 'ECG') {
    return 'WAVEFORM'
  }

  const srSopClasses = new Set([
    '1.2.840.10008.5.1.4.1.1.88.11',
    '1.2.840.10008.5.1.4.1.1.88.22',
    '1.2.840.10008.5.1.4.1.1.88.33',
    '1.2.840.10008.5.1.4.1.1.88.34',
  ])
  if (srSopClasses.has(sopClassUid) || modality === 'SR') {
    return 'SR'
  }

  return 'DICOM'
}

function handleSrNodeClick(data) {
  console.log('[Viewer] handleSrNodeClick received:', data)
  if (data?.sliceIndex === undefined) return

  const candidateIds = viewports.value
    .map(vp => vp.id)
    .filter(id => {
      const type = getViewportType(id)
      return type === 'DICOM' || type === 'FUSION' || type === 'VOLUME_3D'
    })

  if (candidateIds.length === 0) {
    console.warn('[Viewer] No standard image viewport available to jump to')
    return
  }

  // Find the active viewport if it's one of the candidates, otherwise default to the first candidate
  let targetId = candidateIds.includes(state.activeViewportId) ? state.activeViewportId : candidateIds[0]

  const targetRef = viewportRefs.value[targetId]
  if (targetRef && typeof targetRef.jumpToSlice === 'function') {
    targetRef.jumpToSlice(data.sliceIndex)
    
    // Show a modern toast notification
    const { add: addToast } = useToast()
    addToast(`已精准联动影像，跳转至切片第 ${data.sliceIndex + 1} 层`, 'success')
  } else {
    console.warn(`[Viewer] Target viewport ref for ${targetId} is missing or doesn't support jumpToSlice`)
  }
}

onMounted(() => {
  loadStudies()
})
</script>

<style scoped>
/* Sidebar Layout & Scrollbar */
.scrollable-panel {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
.scrollable-panel::-webkit-scrollbar {
  width: 6px;
}
.scrollable-panel::-webkit-scrollbar-track {
  background: var(--primary-bg);
}
.scrollable-panel::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 3px;
}

/* Mode 1: Study Selection Cards */
.study-list-header {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--text-muted);
  letter-spacing: 0.5px;
  padding: 12px 16px 6px;
  user-select: none;
}
.study-list-card {
  display: flex;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
  transition: background-color 0.15s;
}
.study-list-card:hover {
  background-color: var(--hover-bg);
}
.study-card-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-width: 75%;
}
.study-card-name {
  color: var(--text-primary);
  font-weight: 600;
  font-size: 13px;
}
.study-card-mrn {
  color: var(--text-secondary);
  font-size: 11px;
}
.study-card-desc {
  color: var(--text-muted);
  font-size: 11px;
}
.study-card-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
}
.study-card-modality {
  background-color: var(--tertiary-bg);
  color: var(--active-light);
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
}
.study-card-date {
  color: var(--text-muted);
  font-size: 10px;
}

/* Mode 2: Flat Series Cards */
.series-browser-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background-color: var(--primary-bg);
  border-bottom: 1px solid var(--border-color);
}
.back-to-studies-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--tertiary-bg);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  border-radius: 4px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 11px;
  font-weight: 600;
  transition: all 0.15s;
}
.back-to-studies-btn:hover {
  color: var(--text-primary);
  border-color: var(--text-muted);
  background-color: var(--hover-bg);
}
.active-patient-summary {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.active-patient-summary .patient-name {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 12px;
  line-height: 1.2;
}
.active-patient-summary .patient-meta {
  color: var(--text-muted);
  font-size: 10px;
}

.series-flat-list {
  display: flex;
  flex-direction: column;
  padding: 12px;
  gap: 12px;
}
.series-flat-card {
  display: flex;
  flex-direction: column;
  background-color: var(--primary-bg);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 8px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}
.series-flat-card:hover {
  border-color: var(--text-muted);
}
.series-flat-card.active {
  border-color: var(--active-color);
}
.series-flat-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.series-number-desc {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}
.series-number {
  background-color: var(--tertiary-bg);
  color: var(--text-primary);
  font-weight: 700;
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
}
.series-desc {
  font-weight: 500;
  color: var(--text-primary);
  font-size: 11px;
}
.series-modality-badge {
  background-color: rgba(0, 164, 217, 0.15);
  color: var(--active-light);
  font-size: 9px;
  font-weight: 700;
  padding: 1px 4px;
  border-radius: 3px;
}
.series-flat-card-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.series-flat-thumbnail-shell {
  width: 100%;
  aspect-ratio: 1;
  background-color: #000000;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.series-flat-metadata {
  display: flex;
  flex-direction: row;
  justify-content: center;
  flex-wrap: wrap;
  font-size: 10px;
  color: var(--text-secondary);
  gap: 8px;
  width: 100%;
}
.highlight-text {
  color: var(--text-primary);
  font-weight: 600;
}

/* Right Panel Double Tabs */
.right-panel-tabs {
  display: flex;
  background-color: var(--secondary-bg);
  border-bottom: 1px solid var(--border-color);
  height: 36px;
  flex-shrink: 0;
}
.right-tab-btn {
  flex: 1;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--text-muted);
  font-weight: 600;
  font-size: 11px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.15s;
  height: 100%;
}
.right-tab-btn:hover {
  color: var(--text-primary);
}
.right-tab-btn.active {
  color: var(--active-light);
  border-bottom-color: var(--active-light);
  background-color: rgba(91, 175, 243, 0.04);
}
.tab-badge {
  background-color: var(--active-color);
  color: #ffffff;
  font-size: 9px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 10px;
}

/* Measurements Panel State & Cards */
.empty-panel-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: var(--text-muted);
  text-align: center;
  height: 100%;
}
.empty-panel-state .sub-text {
  font-size: 10px;
  margin-top: 4px;
}
.measurements-list {
  display: flex;
  flex-direction: column;
  padding: 12px;
  gap: 8px;
}
.measurement-list-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: var(--primary-bg);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  padding: 8px 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.measurement-list-card:hover {
  background-color: var(--hover-bg);
  border-color: var(--text-muted);
}
.card-left-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.tool-label-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.tool-badge {
  font-size: 9px;
  font-weight: 700;
  padding: 1px 4px;
  border-radius: 2px;
  text-transform: uppercase;
}
.tool-badge.length {
  background-color: rgba(0, 164, 217, 0.15);
  color: var(--active-light);
}
.tool-badge.angle {
  background-color: rgba(226, 158, 74, 0.15);
  color: var(--ui-yellow);
}
.series-name {
  font-size: 10px;
  color: var(--text-muted);
}
.measurement-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.delete-measurement-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  margin-left: 8px;
}
.delete-measurement-btn:hover {
  color: #ff4d4f;
  background-color: rgba(255, 77, 79, 0.1);
}

/* Search Box Layout Styles */
.study-browser-search {
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: var(--secondary-bg);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  margin: 12px;
  padding: 6px 10px;
  height: 32px;
  flex-shrink: 0;
}
.search-icon {
  width: 14px;
  height: 14px;
  color: var(--text-muted);
  flex-shrink: 0;
}
.study-browser-search input {
  background: transparent;
  border: none;
  color: var(--text-primary);
  font-size: 12px;
  width: 100%;
  outline: none;
}
.study-browser-search input::placeholder {
  color: var(--text-muted);
}

/* Empty States & Spinner styling */
.study-browser-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: var(--text-muted);
  text-align: center;
}
.empty-icon {
  width: 32px;
  height: 32px;
  color: var(--text-muted);
  opacity: 0.5;
  margin-bottom: 8px;
}
.empty-sub {
  font-size: 10px;
  margin-top: 4px;
}

.study-browser-loading, .study-tree-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: var(--text-muted);
}
.loading-text {
  font-size: 11px;
  margin-top: 8px;
}

.spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--border-color);
  border-top-color: var(--active-light);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>