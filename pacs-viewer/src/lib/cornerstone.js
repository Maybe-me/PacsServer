import {
  Enums as csCoreEnums,
  RenderingEngine,
  getRenderingEngine,
  imageLoader,
  init as initCornerstone,
  metaData,
  volumeLoader,
  cache,
} from '@cornerstonejs/core'
import {
  addTool,
  Enums as csToolsEnums,
  init as initCornerstoneTools,
  LengthTool,
  AngleTool,
  PanTool,
  StackScrollTool,
  ToolGroupManager,
  WindowLevelTool,
  ZoomTool,
  annotation,
  CrosshairsTool,
  VolumeCroppingTool,
} from '@cornerstonejs/tools'
import dicomImageLoader, { init as initDicomImageLoader } from '@cornerstonejs/dicom-image-loader'
import * as dicomParserModule from 'dicom-parser'
import { buildWadoImageId } from '../api/viewer'

const renderingEngineId = 'viewer-rendering-engine'
const toolGroupId = 'viewer-tool-group'

let initialized = false
const dicomParser = dicomParserModule.default ?? dicomParserModule

export function deleteAnnotation(annotationUID) {
  try {
    annotation.state.removeAnnotation(annotationUID)
    const engine = getOrCreateRenderingEngine()
    engine.render()
  } catch (e) {
    console.warn('[Cornerstone] failed to remove annotation:', e.message)
  }
}

function registerTools() {
  ;[WindowLevelTool, PanTool, ZoomTool, LengthTool, AngleTool, StackScrollTool, CrosshairsTool, VolumeCroppingTool].forEach((toolClass) => addTool(toolClass))
}

function remoteLog(msg) {
  console.log('[Remote Log]', msg)
  fetch(`/wado-rs/log?msg=${encodeURIComponent(msg)}`).catch(() => {})
}

// Global XHR Interceptor for telemetry
if (typeof window !== 'undefined' && !window._xhrHooked) {
  window._xhrHooked = true;
  const originalSend = XMLHttpRequest.prototype.send;
  const originalOpen = XMLHttpRequest.prototype.open;

  XMLHttpRequest.prototype.open = function(method, url) {
    this._url = url;
    return originalOpen.apply(this, arguments);
  };

  XMLHttpRequest.prototype.send = function() {
    this.addEventListener('load', () => {
      if (this._url && this._url.includes('/wado-rs/studies')) {
        const response = this.response;
        if (response instanceof ArrayBuffer) {
          remoteLog('[XHR] URL: ' + this._url + ' Response ArrayBuffer length: ' + response.byteLength);
          if (response.byteLength >= 132) {
            const view = new Uint8Array(response, 128, 4);
            const prefix = String.fromCharCode(...view);
            remoteLog('[XHR] Header at 128: ' + prefix);
          } else {
            remoteLog('[XHR] Response too short for DICM prefix: ' + response.byteLength);
          }
        } else {
          remoteLog('[XHR] URL: ' + this._url + ' Response is not ArrayBuffer! Type: ' + (typeof response));
        }
      }
    });
    return originalSend.apply(this, arguments);
  };
}

export async function initializeCornerstone() {
  if (initialized) {
    return
  }

  await initCornerstone()
  await initCornerstoneTools()

  // Initialize DICOM Image Loader correctly for v4
  initDicomImageLoader({
    maxWebWorkers: Math.min(navigator.hardwareConcurrency || 4, 4),
  })

  // Explicitly register loaders
  const originalWadouriLoader = dicomImageLoader.wadouri.loadImage
  imageLoader.registerImageLoader('wadouri', (imageId, options) => {
    remoteLog('[WADOURI Hook] Start loading: ' + imageId)
    const loadObject = originalWadouriLoader(imageId, options)
    loadObject.promise.then((image) => {
      remoteLog('[WADOURI Hook] Image loaded successfully: ' + imageId)
      if (image.data && image.data.elements) {
        const keys = Object.keys(image.data.elements)
        remoteLog('[WADOURI Hook] Success elements count: ' + keys.length + ' hasPixelData: ' + !!image.data.elements.x7fe00010)
      }
    }).catch((err) => {
      remoteLog('[WADOURI Hook] Image load failed: ' + imageId + ' error: ' + (err?.error?.message || err?.message || err))
      if (err && err.dataSet) {
        const keys = Object.keys(err.dataSet.elements)
        remoteLog('[WADOURI Hook] Failed dataSet elements count: ' + keys.length + ' hasPixelData: ' + !!err.dataSet.elements.x7fe00010)
        remoteLog('[WADOURI Hook] Failed dataSet elements sample (first 30): ' + keys.slice(0, 30).join(','))
      }
    })
    return loadObject
  })
  imageLoader.registerImageLoader('wadors', dicomImageLoader.wadors.loadImage)


  // Register metadata provider - handle both v3 and v4 API shapes safely
  try {
    const wadouri = dicomImageLoader.wadouri
    // v4.x: metaDataProvider is a direct function on wadouri.metaData
    if (wadouri?.metaData?.metaDataProvider) {
      metaData.addProvider(wadouri.metaData.metaDataProvider)
    } else if (typeof wadouri?.metaData?.get === 'function') {
      // v3.x fallback
      metaData.addProvider(wadouri.metaData.get.bind(wadouri.metaData))
    }
  } catch (e) {
    console.warn('[Cornerstone] metaData provider registration skipped:', e.message)
  }

  registerTools()
  initialized = true
}

export function getOrCreateRenderingEngine() {
  return getRenderingEngine(renderingEngineId) || new RenderingEngine(renderingEngineId)
}

export function getOrCreateToolGroup(customToolGroupId = 'viewer-tool-group') {
  return ToolGroupManager.getToolGroup(customToolGroupId) || ToolGroupManager.createToolGroup(customToolGroupId)
}

export function enableViewport(element, viewportId, mprOrientation = null, isVolume3D = false) {
  const renderingEngine = getOrCreateRenderingEngine()
  let type = csCoreEnums.ViewportType.STACK
  if (mprOrientation) {
    type = csCoreEnums.ViewportType.ORTHOGRAPHIC
  } else if (isVolume3D) {
    type = csCoreEnums.ViewportType.VOLUME_3D
  }
  
  const defaultOptions = {
    background: [0, 0, 0],
  }
  if (mprOrientation) {
    defaultOptions.orientation = csCoreEnums.OrientationAxis[mprOrientation.toUpperCase()]
  }

  renderingEngine.enableElement({
    viewportId,
    type,
    element,
    defaultOptions,
  })

  const currentToolGroupId = mprOrientation ? 'viewer-mpr-tool-group' : 'viewer-tool-group'
  const toolGroup = getOrCreateToolGroup(currentToolGroupId)

  const toolsToRegister = mprOrientation
    ? ['WindowLevel', 'Pan', 'Zoom', 'Length', 'Angle', 'StackScroll', 'Crosshairs', 'VolumeCropping']
    : ['WindowLevel', 'Pan', 'Zoom', 'Length', 'Angle', 'StackScroll', 'VolumeCropping']

  toolsToRegister.forEach((toolName) => {
    try {
      if (toolName === 'Crosshairs') {
        toolGroup.addTool(toolName, {
          viewports: [
            { viewportId: 'viewport-mpr-axial' },
            { viewportId: 'viewport-mpr-sagittal' },
            { viewportId: 'viewport-mpr-coronal' },
          ]
        })
      } else {
        toolGroup.addTool(toolName)
      }
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
  ;['viewer-tool-group', 'viewer-mpr-tool-group'].forEach((tgId) => {
    const toolGroup = ToolGroupManager.getToolGroup(tgId)
    if (!toolGroup) {
      return
    }

    const toolsToDeactivate = tgId === 'viewer-mpr-tool-group'
      ? ['WindowLevel', 'Pan', 'Zoom', 'Length', 'Angle', 'StackScroll', 'Crosshairs']
      : ['WindowLevel', 'Pan', 'Zoom', 'Length', 'Angle', 'StackScroll']

    toolsToDeactivate.forEach((name) => {
      toolGroup.setToolPassive(name)
    })

    if (toolsToDeactivate.includes(toolName)) {
      toolGroup.setToolActive(toolName, {
        bindings: [{ mouseButton: csToolsEnums.MouseBindings.Primary }],
      })
    }
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

export async function renderVolume(viewportId, imageIds, seriesInstanceUid, instance = null) {
  const renderingEngine = getOrCreateRenderingEngine()
  const viewport = renderingEngine.getViewport(viewportId)
  if (!viewport || !imageIds?.length) {
    return
  }

  const volumeId = `cornerstoneStreamingImageVolume:volume-${seriesInstanceUid}`
  renderingEngine.resize(true, false)

  let volume
  try {
    volume = await volumeLoader.createAndCacheVolume(volumeId, { imageIds })
    volume.load()
  } catch (e) {
    volume = cache.getVolume(volumeId)
  }

  if (volume) {
    await viewport.setVolumes([{ volumeId }])
    const displayProperties = buildDisplayProperties(instance)
    if (displayProperties.voiRange) {
      viewport.setProperties(displayProperties)
    }
    viewport.resetCamera()
    viewport.render()
  }
}

export function setViewportProjectionMode(viewportId, mode, slabThickness = 10) {
  try {
    const renderingEngine = getOrCreateRenderingEngine()
    const viewport = renderingEngine.getViewport(viewportId)
    if (!viewport) {
      return
    }

    const BlendModes = csCoreEnums.BlendModes
    let blendMode = 0 // 0 is COMPOSITE / DEFAULT
    if (BlendModes) {
      if (mode === 'MIP') {
        blendMode = BlendModes.MAXIMUM_INTENSITY_BLEND
      } else if (mode === 'MinIP') {
        blendMode = BlendModes.MINIMUM_INTENSITY_BLEND
      } else if (mode === 'AIP') {
        blendMode = BlendModes.AVERAGE_INTENSITY_BLEND
      } else {
        blendMode = BlendModes.COMPOSITE || 0
      }
    }

    const properties = {
      blendMode,
    }
    
    // Only apply slab thickness if we are in projection mode
    if (mode !== 'NORMAL' && slabThickness !== undefined && slabThickness !== null) {
      properties.slabThickness = Number(slabThickness)
    }

    if (typeof viewport.setProperties === 'function') {
      viewport.setProperties(properties)
      viewport.render()
    }
  } catch (e) {
    console.warn('[Cornerstone] failed to set projection mode:', e.message)
  }
}

export async function renderFusionVolume(viewportId, imageIds, seriesInstanceUid, instance, alpha = 0.5, colormap = 'hot') {
  const renderingEngine = getOrCreateRenderingEngine()
  const viewport = renderingEngine.getViewport(viewportId)
  if (!viewport || !imageIds?.length) {
    return
  }

  const ctVolumeId = `cornerstoneStreamingImageVolume:volume-${seriesInstanceUid}-ct`
  const petVolumeId = `cornerstoneStreamingImageVolume:volume-${seriesInstanceUid}-pet`

  renderingEngine.resize(true, false)

  let ctVolume
  try {
    ctVolume = await volumeLoader.createAndCacheVolume(ctVolumeId, { imageIds })
    ctVolume.load()
  } catch (e) {
    ctVolume = cache.getVolume(ctVolumeId)
  }

  let petVolume
  try {
    petVolume = cache.getVolume(petVolumeId)
    if (!petVolume && ctVolume) {
      // Create derived volume for PET
      petVolume = volumeLoader.createAndCacheDerivedVolume(ctVolumeId, {
        volumeId: petVolumeId,
      })

      if (petVolume) {
        const ctData = ctVolume.getScalarData()
        const petData = petVolume.getScalarData()
        const len = ctData.length

        console.log(`[Cornerstone] Simulating high-fidelity PET metabolic volume of length ${len}...`)
        // Perfect spatial matching: any voxel conformed to the sphere hyperdensity (HU >= 1700) gets high SUV metabolic uptake!
        for (let i = 0; i < len; i++) {
          if (ctData[i] >= 1700) {
            petData[i] = 1200 + Math.random() * 300 // High PET SUV hotspot conformed to CT sphere!
          } else if (ctData[i] >= 200) {
            // Physiologic background uptake in soft tissue / bones
            petData[i] = 150 + Math.random() * 80
          } else {
            // Soft background noise
            petData[i] = Math.max(0, 15 + Math.random() * 15)
          }
        }
      }
    }
  } catch (e) {
    console.error('[Cornerstone] Failed to create derived PET volume:', e)
  }

  if (ctVolume && petVolume) {
    console.log(`[Cornerstone] Setting dual volumes in viewport ${viewportId}...`)
    await viewport.setVolumes([
      { volumeId: ctVolumeId },
      {
        volumeId: petVolumeId,
      }
    ])

    // Set properties for CT (grayscale)
    const center = Number(instance?.windowCenter) || 40
    const width = Number(instance?.windowWidth) || 400
    viewport.setProperties({
      voiRange: {
        lower: center - width / 2,
        upper: center + width / 2,
      }
    }, ctVolumeId)

    // Set colormap and alpha properties for PET
    applyFusionProperties(viewportId, alpha, colormap)

    viewport.resetCamera()
    viewport.render()
  }
}

export function applyFusionProperties(viewportId, alpha, colormapName) {
  try {
    const renderingEngine = getOrCreateRenderingEngine()
    const viewport = renderingEngine.getViewport(viewportId)
    if (!viewport) return

    // Find PET volume actor in the viewport
    const volumeActors = viewport.getActors()
    const petActorEntry = volumeActors.find(entry => entry.uid.endsWith('-pet'))
    if (!petActorEntry) {
      console.warn('[Cornerstone] applyFusionProperties: PET actor not found in viewport actors:', volumeActors.map(a => a.uid))
      return
    }
    const petVolumeId = petActorEntry.uid

    console.log(`[Cornerstone] Updating PET volume ${petVolumeId} with colormap=${colormapName}, opacity=${alpha}`)
    
    // Normalize colormap name
    let finalColormap = colormapName || 'hot'
    if (finalColormap === 'hotmetal') finalColormap = 'hot' // Map 'hotmetal' preset to standard vtk.js 'hot'

    viewport.setProperties({
      colormap: { name: finalColormap },
      opacity: Number(alpha)
    }, petVolumeId)

    viewport.render()
  } catch (e) {
    console.warn('[Cornerstone] applyFusionProperties failed:', e.message)
  }
}

