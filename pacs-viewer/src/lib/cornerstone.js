import {
  Enums as csCoreEnums,
  RenderingEngine,
  getRenderingEngine,
  imageLoader,
  init as initCornerstone,
} from '@cornerstonejs/core'
import {
  addTool,
  Enums as csToolsEnums,
  init as initCornerstoneTools,
  LengthTool,
  PanTool,
  StackScrollTool,
  ToolGroupManager,
  WindowLevelTool,
  ZoomTool,
} from '@cornerstonejs/tools'
import dicomImageLoader, { init as initDicomImageLoader } from '@cornerstonejs/dicom-image-loader'
import * as dicomParserModule from 'dicom-parser'
import { buildWadoImageId } from '../api/viewer'

const renderingEngineId = 'viewer-rendering-engine'
const toolGroupId = 'viewer-tool-group'

let initialized = false
const dicomParser = dicomParserModule.default ?? dicomParserModule

function registerTools() {
  ;[WindowLevelTool, PanTool, ZoomTool, LengthTool, StackScrollTool].forEach((toolClass) => addTool(toolClass))
}

export async function initializeCornerstone() {
  if (initialized) {
    return
  }

  await initCornerstone()
  await initCornerstoneTools()

  dicomImageLoader.external ||= {}
  dicomImageLoader.external.cornerstone = {
    metaData: dicomImageLoader.wadouri.metaData,
  }
  dicomImageLoader.external.dicomParser = dicomParser
  initDicomImageLoader({
    maxWebWorkers: 1,
  })

  registerTools()
  initialized = true
}

export function getOrCreateRenderingEngine() {
  return getRenderingEngine(renderingEngineId) || new RenderingEngine(renderingEngineId)
}

export function getOrCreateToolGroup() {
  return ToolGroupManager.getToolGroup(toolGroupId) || ToolGroupManager.createToolGroup(toolGroupId)
}

export function enableViewport(element, viewportId) {
  const renderingEngine = getOrCreateRenderingEngine()
  renderingEngine.enableElement({
    viewportId,
    type: csCoreEnums.ViewportType.STACK,
    element,
    defaultOptions: {
      background: [0, 0, 0],
    },
  })

  const toolGroup = getOrCreateToolGroup()
  ;['WindowLevel', 'Pan', 'Zoom', 'Length', 'StackScroll'].forEach((toolName) => {
    try {
      toolGroup.addTool(toolName)
    } catch {
      // ignore duplicate registration for shared group
    }
  })
  try {
    toolGroup.addViewport(viewportId, renderingEngineId)
  } catch {
    // ignore duplicate viewport binding
  }
}

export function activateTool(toolName) {
  const toolGroup = getOrCreateToolGroup()
  ;['WindowLevel', 'Pan', 'Zoom', 'Length', 'StackScroll'].forEach((name) => {
    toolGroup.setToolPassive(name)
  })

  if (toolName === 'StackScroll') {
    toolGroup.setToolActive(toolName, {
      bindings: [{ mouseButton: csToolsEnums.MouseBindings.Primary }],
    })
    return
  }

  toolGroup.setToolActive(toolName, {
    bindings: [{ mouseButton: csToolsEnums.MouseBindings.Primary }],
  })
}

function buildDisplayProperties(instance) {
  const center = Number(instance?.windowCenter)
  const width = Number(instance?.windowWidth)
  if (!Number.isFinite(center) || !Number.isFinite(width) || width <= 0) {
    return {}
  }

  return {
    voiRange: {
      lower: center - width / 2,
      upper: center + width / 2,
    },
  }
}

export async function renderStack(viewportId, imageIds, initialImageIndex = 0, instance = null) {
  const renderingEngine = getOrCreateRenderingEngine()
  const viewport = renderingEngine.getViewport(viewportId)
  if (!viewport || !imageIds?.length) {
    return
  }

  renderingEngine.resize(true, false)
  await viewport.setStack(imageIds, initialImageIndex)
  const displayProperties = buildDisplayProperties(instance)
  if (displayProperties.voiRange) {
    viewport.setProperties(displayProperties)
  }
  viewport.resetCamera()
  viewport.render()

  imageIds.slice(0, Math.min(imageIds.length, 4)).forEach((imageId) => {
    imageLoader.loadAndCacheImage(imageId).catch(() => null)
  })
}

export function buildImageIds(instances) {
  return (instances || []).map((instance) => buildWadoImageId(instance))
}
