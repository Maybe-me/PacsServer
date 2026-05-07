import { get, resolveBackendUrl } from './client'

const SUPPORTED_BITS_ALLOCATED = new Set([1, 8, 16, 32])
const UNSUPPORTED_SOP_CLASS_REASONS = new Map([
  ['1.2.840.10008.5.1.4.1.1.66.4', 'Segmentation objects are not supported by the stack viewer.'],
  ['1.2.840.10008.5.1.4.1.1.66.5', 'Surface segmentation objects are not supported by the stack viewer.'],
  ['1.2.840.10008.5.1.4.1.1.30', 'Parametric map objects are not supported by the stack viewer.'],
  ['1.2.840.10008.5.1.4.1.1.481.2', 'RT Dose objects are not supported by the stack viewer.'],
  ['1.2.840.10008.5.1.4.1.1.77.1.6', 'Whole slide microscopy images are not supported by the stack viewer.'],
])
const SUPPORTED_TRANSFER_SYNTAXES = new Set([
  '1.2.840.10008.1.2',
  '1.2.840.10008.1.2.1',
  '1.2.840.10008.1.2.2',
  '1.2.840.10008.1.2.1.99',
  '1.2.840.10008.1.2.5',
  '1.2.840.10008.1.2.4.50',
  '1.2.840.10008.1.2.4.51',
  '1.2.840.10008.1.2.4.57',
  '1.2.840.10008.1.2.4.70',
  '1.2.840.10008.1.2.4.80',
  '1.2.840.10008.1.2.4.81',
  '1.2.840.10008.1.2.4.90',
  '1.2.840.10008.1.2.4.91',
  '1.2.840.10008.1.2.4.96',
  '1.2.840.10008.1.2.4.201',
  '1.2.840.10008.1.2.4.202',
  '1.2.840.10008.1.2.4.203',
])
const SUPPORTED_COLOR_PHOTOMETRICS = new Set([
  'RGB',
  'PALETTE COLOR',
  'YBR_FULL',
  'YBR_FULL_422',
  'YBR_PARTIAL_420',
  'YBR_RCT',
  'YBR_ICT',
])
const SUPPORTED_MONO_PHOTOMETRICS = new Set(['MONOCHROME1', 'MONOCHROME2'])

export function listStudies(params) {
  return get('/api/viewer/studies', { params })
}

export function listSeries(studyInstanceUid) {
  return get(`/api/viewer/studies/${studyInstanceUid}/series`)
}

export function listInstances(studyInstanceUid, seriesInstanceUid) {
  return get(`/api/viewer/studies/${studyInstanceUid}/series/${seriesInstanceUid}/instances`).then((instances) =>
    (instances || []).map((instance) => {
      const compatibility = getViewerCompatibility(instance)
      return {
        ...instance,
        failedFrames: Array.isArray(instance.failedFrames) ? instance.failedFrames : [],
        frameIssues: Array.isArray(instance.frameIssues) ? instance.frameIssues : [],
        displayable: compatibility.displayable,
        displayIssue: compatibility.reason,
      }
    })
  )
}

function buildFrameUri(instance, frameNumber) {
  const resolvedUri = resolveBackendUrl(instance.wadoUri)
  if (!frameNumber || frameNumber <= 1 && (!instance?.numberOfFrames || instance.numberOfFrames <= 1)) {
    return resolvedUri
  }
  return `${resolvedUri}&frame=${frameNumber}`
}

export function buildWadoImageId(instance, frameNumber = 1) {
  return `wadouri:${buildFrameUri(instance, frameNumber)}`
}

export function buildStackItems(instances = []) {
  return instances.flatMap((instance) => {
    const totalFrames = Math.max(1, Number(instance?.numberOfFrames) || 1)
    const failedFrames = new Set(
      (instance?.failedFrames || [])
        .map((value) => Number(value))
        .filter((value) => Number.isFinite(value) && value >= 1 && value <= totalFrames)
    )
    return Array.from({ length: totalFrames }, (_, frameOffset) => ({
      imageId: buildWadoImageId(instance, frameOffset + 1),
      sopInstanceUid: instance.sopInstanceUid,
      frameNumber: frameOffset + 1,
      instance,
    })).filter((item) => !failedFrames.has(item.frameNumber))
  })
}

export function resolveWadoUri(instance, frameNumber = null) {
  if (!instance?.wadoUri) {
    return ''
  }
  return buildFrameUri(instance, frameNumber)
}

export function getViewerCompatibility(instance) {
  if (instance?.hasDoubleFloatPixelData) {
    return {
      displayable: false,
      reason: 'Double float pixel data is not supported by the stack viewer.',
    }
  }

  if (instance?.hasFloatPixelData) {
    return {
      displayable: false,
      reason: 'Float pixel data is not supported by the stack viewer.',
    }
  }

  if (!instance?.renderable) {
    return {
      displayable: false,
      reason: 'Missing pixel matrix metadata.',
    }
  }

  const bitsAllocated = Number(instance.bitsAllocated) || 0
  if (bitsAllocated && !SUPPORTED_BITS_ALLOCATED.has(bitsAllocated)) {
    return {
      displayable: false,
      reason: `Unsupported Bits Allocated: ${bitsAllocated}.`,
    }
  }

  const bitsStored = Number(instance.bitsStored) || 0
  if (bitsStored && bitsAllocated && (bitsStored < 1 || bitsStored > bitsAllocated)) {
    return {
      displayable: false,
      reason: `Invalid Bits Stored ${bitsStored} for Bits Allocated ${bitsAllocated}.`,
    }
  }

  const highBit = Number(instance.highBit)
  if (Number.isFinite(highBit) && bitsStored && (highBit < 0 || highBit >= bitsAllocated || highBit + 1 !== bitsStored)) {
    return {
      displayable: false,
      reason: `Invalid High Bit ${highBit} for Bits Stored ${bitsStored}.`,
    }
  }

  const pixelRepresentation = Number(instance.pixelRepresentation)
  if (Number.isFinite(pixelRepresentation) && pixelRepresentation !== 0 && pixelRepresentation !== 1) {
    return {
      displayable: false,
      reason: `Unsupported pixel representation: ${pixelRepresentation}.`,
    }
  }

  const sopClassUid = (instance.sopClassUid || '').trim()
  if (sopClassUid && UNSUPPORTED_SOP_CLASS_REASONS.has(sopClassUid)) {
    return {
      displayable: false,
      reason: UNSUPPORTED_SOP_CLASS_REASONS.get(sopClassUid),
    }
  }

  const transferSyntaxUid = (instance.transferSyntaxUid || '').trim()
  if (transferSyntaxUid && !SUPPORTED_TRANSFER_SYNTAXES.has(transferSyntaxUid)) {
    return {
      displayable: false,
      reason: `Unsupported transfer syntax: ${transferSyntaxUid}.`,
    }
  }

  const photometric = (instance.photometricInterpretation || '').trim().toUpperCase()
  const samplesPerPixel = Math.max(1, Number(instance.samplesPerPixel) || 1)
  const planarConfiguration = Number(instance.planarConfiguration)

  if (samplesPerPixel > 1) {
    if (!photometric) {
      return {
        displayable: false,
        reason: 'Missing photometric interpretation for color image.',
      }
    }
    if (!SUPPORTED_COLOR_PHOTOMETRICS.has(photometric)) {
      return {
        displayable: false,
        reason: `Unsupported photometric interpretation: ${photometric}.`,
      }
    }
    if (
      Number.isFinite(planarConfiguration) &&
      photometric !== 'YBR_FULL_422' &&
      planarConfiguration !== 0 &&
      planarConfiguration !== 1
    ) {
      return {
        displayable: false,
        reason: `Unsupported planar configuration: ${planarConfiguration}.`,
      }
    }
  } else if (photometric && !SUPPORTED_MONO_PHOTOMETRICS.has(photometric) && !SUPPORTED_COLOR_PHOTOMETRICS.has(photometric)) {
    return {
      displayable: false,
      reason: `Unsupported photometric interpretation: ${photometric}.`,
    }
  }

  return {
    displayable: true,
    reason: '',
  }
}
