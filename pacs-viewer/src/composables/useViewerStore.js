import { computed, reactive } from 'vue'

const viewportDefinitions = {
  single: [
    { id: 'viewport-1', title: 'Viewport 1' },
  ],
  dual: [
    { id: 'viewport-1', title: 'Viewport 1' },
    { id: 'viewport-2', title: 'Viewport 2' },
  ],
  quad: [
    { id: 'viewport-1', title: 'Viewport 1' },
    { id: 'viewport-2', title: 'Viewport 2' },
    { id: 'viewport-3', title: 'Viewport 3' },
    { id: 'viewport-4', title: 'Viewport 4' },
  ],
}

const state = reactive({
  layout: 'single',
  activeViewportId: 'viewport-1',
  activeTool: 'WindowLevel',
  cinePlaying: false,
  studies: [],
  series: [],
  instances: [],
  displayInstances: [],
  inspectedInstance: null,
  selectedStudy: null,
  selectedSeries: null,
  selectedInstance: null,
  filters: {
    patientId: '',
    issuerOfPatientId: '',
    modality: '',
    studyDateFrom: '',
    studyDateTo: '',
  },
  viewportAssignments: {
    'viewport-1': null,
    'viewport-2': null,
    'viewport-3': null,
    'viewport-4': null,
  },
})

function clearViewportAssignments(keepViewportIds = []) {
  Object.keys(state.viewportAssignments).forEach((viewportId) => {
    if (!keepViewportIds.includes(viewportId)) {
      state.viewportAssignments[viewportId] = null
    }
  })
}

function setLayout(layout) {
  state.layout = layout
  const activeViewportIds = viewportDefinitions[layout].map((viewport) => viewport.id)
  if (!activeViewportIds.includes(state.activeViewportId)) {
    state.activeViewportId = activeViewportIds[0]
  }
  clearViewportAssignments(activeViewportIds)
}

function setStudies(studies, filters) {
  state.studies = studies
  state.filters = { ...state.filters, ...filters }
  state.series = []
  state.instances = []
  state.displayInstances = []
  state.inspectedInstance = null
  state.selectedStudy = null
  state.selectedSeries = null
  state.selectedInstance = null
  clearViewportAssignments([])
}

function setSelectedStudy(study) {
  state.selectedStudy = study
  state.series = []
  state.instances = []
  state.displayInstances = []
  state.inspectedInstance = null
  state.selectedSeries = null
  state.selectedInstance = null
  clearViewportAssignments([])
}

function setSeries(series) {
  state.series = (series || []).map((item) => ({
    ...item,
    displayableCount: item.displayableCount ?? null,
    degradedCount: item.degradedCount ?? 0,
    failedFrameCount: item.failedFrameCount ?? 0,
    incompatibleCount: item.incompatibleCount ?? null,
    compatibilitySummary: item.compatibilitySummary ?? '',
  }))
}

function updateSeriesDiagnostics(seriesInstanceUid, diagnostics) {
  state.series = state.series.map((item) => {
    if (item.seriesInstanceUid !== seriesInstanceUid) {
      return item
    }
    return {
      ...item,
      ...diagnostics,
    }
  })
}

function setSelectedSeries(series) {
  state.selectedSeries = series
  state.instances = []
  state.displayInstances = []
  state.inspectedInstance = null
  state.selectedInstance = null
  clearViewportAssignments([])
}

function setInstances(instances) {
  state.instances = instances
}

function setDisplayInstances(instances) {
  state.displayInstances = instances
}

function setInspectedInstance(instance) {
  state.inspectedInstance = instance
}

function setSelectedInstance(instance, viewportId = state.activeViewportId) {
  state.inspectedInstance = instance
  state.selectedInstance = instance
  state.viewportAssignments[viewportId] = {
    study: state.selectedStudy,
    series: state.selectedSeries,
    instance,
    frameNumber: 1,
    instances: [...state.displayInstances],
  }
}

function assignSeriesToViewport(series, instances, viewportId = state.activeViewportId) {
  const targetInstance = instances[0] ?? null
  assignViewport(viewportId, series, instances, targetInstance, 1)
}

function assignViewport(viewportId, series, instances, targetInstance, frameNumber = 1) {
  state.selectedSeries = series
  state.displayInstances = [...instances]
  state.inspectedInstance = targetInstance
  state.selectedInstance = targetInstance
  state.viewportAssignments[viewportId] = {
    study: state.selectedStudy,
    series,
    instance: targetInstance,
    frameNumber,
    instances: [...instances],
  }
}

function updateViewportRenderedItem(viewportId, stackItem) {
  const assignment = state.viewportAssignments[viewportId]
  if (!assignment) {
    return
  }
  state.viewportAssignments[viewportId] = {
    ...assignment,
    instance: stackItem?.instance ?? assignment.instance,
    frameNumber: stackItem?.frameNumber ?? 1,
  }
}

function setActiveViewport(viewportId) {
  state.activeViewportId = viewportId
}

function setActiveTool(toolName) {
  state.activeTool = toolName
}

function setCinePlaying(playing) {
  state.cinePlaying = playing
}

export function useViewerStore() {
  const viewports = computed(() => viewportDefinitions[state.layout])
  const activeViewport = computed(() => state.viewportAssignments[state.activeViewportId])

  return {
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
  }
}
