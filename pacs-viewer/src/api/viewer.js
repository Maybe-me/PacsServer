import { get, resolveBackendUrl } from './client'

const SUPPORTED_BITS_ALLOCATED = new Set([1, 8, 16, 32])
const UNSUPPORTED_SOP_CLASS_REASONS = new Map([
  ['1.2.840.10008.5.1.4.1.1.30', 'Parametric map objects are not supported by the stack viewer.'],
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

const MOCK_STUDIES = [
  {
    patientName: "MOCK^PDF CLINICAL REPORT",
    patientId: "PAT-MOCK-PDF",
    studyInstanceUid: "study-mock-pdf",
    studyDescription: "Diagnostic PDF Report",
    modalitiesInStudy: "DOC",
    studyDate: "20260520",
    patientBirthDate: "19810520"
  },
  {
    patientName: "MOCK^ENDOSCOPIC VIDEO",
    patientId: "PAT-MOCK-VIDEO",
    studyInstanceUid: "study-mock-video",
    studyDescription: "Gastrointestinal Endoscopy Video",
    modalitiesInStudy: "ES",
    studyDate: "20260520",
    patientBirthDate: "19810520"
  },
  {
    patientName: "MOCK^12-LEAD ECG",
    patientId: "PAT-MOCK-ECG",
    studyInstanceUid: "study-mock-ecg",
    studyDescription: "12-Lead Electrocardiography Waveform",
    modalitiesInStudy: "ECG",
    studyDate: "20260520",
    patientBirthDate: "19810520"
  },
  {
    patientName: "MOCK^STRUCTURED REPORT",
    patientId: "PAT-MOCK-SR",
    studyInstanceUid: "study-mock-sr",
    studyDescription: "Chest CT Structured Diagnosis Report",
    modalitiesInStudy: "SR",
    studyDate: "20260520",
    patientBirthDate: "19810520"
  },
  {
    patientName: "MOCK^PET/CT FUSION",
    patientId: "PAT-MOCK-FUSION",
    studyInstanceUid: "study-mock-fusion",
    studyDescription: "PET/CT Multimodal Fusion & SUV Quantification",
    modalitiesInStudy: "CT/PT",
    studyDate: "20260520",
    patientBirthDate: "19810520"
  }
]

export function listStudies(params) {
  return get('/api/viewer/studies', { params })
    .then((studies) => {
      const apiStudies = Array.isArray(studies) ? studies : []
      // Deduplicate to prevent mock studies from building up
      const filteredApi = apiStudies.filter(s => !s.studyInstanceUid.startsWith('study-mock'))
      return [...MOCK_STUDIES, ...filteredApi]
    })
    .catch((err) => {
      console.warn('[API Client] Backend offline, presenting demo studies:', err.message)
      return MOCK_STUDIES
    })
}

export function listSeries(studyInstanceUid) {
  if (studyInstanceUid === 'study-mock-pdf') {
    return Promise.resolve([
      {
        seriesInstanceUid: 'series-mock-pdf',
        seriesDescription: 'Encapsulated PDF Document',
        seriesNumber: '1',
        modality: 'DOC',
        numInstances: 1
      }
    ])
  }
  if (studyInstanceUid === 'study-mock-video') {
    return Promise.resolve([
      {
        seriesInstanceUid: 'series-mock-video',
        seriesDescription: 'Endoscopy Video Clip',
        seriesNumber: '1',
        modality: 'ES',
        numInstances: 1
      }
    ])
  }
  if (studyInstanceUid === 'study-mock-ecg') {
    return Promise.resolve([
      {
        seriesInstanceUid: 'series-mock-ecg',
        seriesDescription: 'Electrocardiography Waveform Signal',
        seriesNumber: '1',
        modality: 'ECG',
        numInstances: 1
      }
    ])
  }
  if (studyInstanceUid === 'study-mock-sr') {
    return Promise.resolve([
      {
        seriesInstanceUid: 'series-mock-sr',
        seriesDescription: 'DICOM Structured Report Findings',
        seriesNumber: '1',
        modality: 'SR',
        numInstances: 1
      }
    ])
  }
  if (studyInstanceUid === 'study-mock-fusion') {
    return Promise.resolve([
      {
        seriesInstanceUid: 'series-mock-fusion-ct-pt',
        seriesDescription: 'PET/CT Spatial Fusion Series',
        seriesNumber: '1',
        modality: 'CT/PT',
        numInstances: 32
      }
    ])
  }
  return get(`/api/viewer/studies/${studyInstanceUid}/series`)
}

export function listInstances(studyInstanceUid, seriesInstanceUid) {
  if (studyInstanceUid === 'study-mock-pdf') {
    return Promise.resolve([
      {
        sopInstanceUid: 'instance-mock-pdf',
        sopClassUid: '1.2.840.10008.5.1.4.1.1.104.1',
        mediaType: 'PDF',
        displayable: true,
        patientName: 'MOCK^PDF CLINICAL REPORT',
        patientId: 'PAT-MOCK-PDF',
        studyDate: '20260520'
      }
    ])
  }
  if (studyInstanceUid === 'study-mock-video') {
    return Promise.resolve([
      {
        sopInstanceUid: 'instance-mock-video',
        sopClassUid: '1.2.840.10008.5.1.4.1.1.77.1.4.1',
        mediaType: 'VIDEO',
        displayable: true,
        patientName: 'MOCK^ENDOSCOPIC VIDEO',
        patientId: 'PAT-MOCK-VIDEO',
        studyDate: '20260520'
      }
    ])
  }
  if (studyInstanceUid === 'study-mock-ecg') {
    return Promise.resolve([
      {
        sopInstanceUid: 'instance-mock-ecg',
        sopClassUid: '1.2.840.10008.5.1.4.1.1.9.1.1',
        mediaType: 'WAVEFORM',
        displayable: true,
        patientName: 'MOCK^12-LEAD ECG',
        patientId: 'PAT-MOCK-ECG',
        studyDate: '20260520'
      }
    ])
  }
  if (studyInstanceUid === 'study-mock-sr') {
    return Promise.resolve([
      {
        sopInstanceUid: 'instance-mock-sr',
        sopClassUid: '1.2.840.10008.5.1.4.1.1.88.33',
        mediaType: 'SR',
        displayable: true,
        patientName: 'MOCK^STRUCTURED REPORT',
        patientId: 'PAT-MOCK-SR',
        studyDate: '20260520'
      }
    ])
  }
  if (studyInstanceUid === 'study-mock-fusion') {
    const realStudyUid = '1.2.826.0.1.3680043.8.498.35756853433828314476248848933322228221'
    return get(`/api/viewer/studies/${realStudyUid}/series`)
      .then((seriesList) => {
        const firstSeriesUid = seriesList[0]?.seriesInstanceUid
        if (!firstSeriesUid) return []
        return get(`/api/viewer/studies/${realStudyUid}/series/${firstSeriesUid}/instances`)
      })
      .then((instances) => {
        return (instances || []).map((instance) => {
          const compatibility = getViewerCompatibility(instance)
          return {
            ...instance,
            mediaType: 'FUSION',
            displayable: compatibility.displayable,
            displayIssue: compatibility.reason,
            failedFrames: [],
            frameIssues: [],
            patientName: 'MOCK^PET/CT FUSION',
            patientId: 'PAT-MOCK-FUSION',
            studyDate: '20260520'
          }
        })
      })
      .catch((err) => {
        console.error('Failed to load real instances for fusion mock:', err.message)
        return []
      })
  }
  return get(`/api/viewer/studies/${studyInstanceUid}/series/${seriesInstanceUid}/instances`).then((instances) =>
    (instances || []).map((instance) => {
      const compatibility = getViewerCompatibility(instance)
      return {
        ...instance,
        failedFrames: Array.isArray(instance.failedFrames) ? instance.failedFrames : [],
        frameIssues: Array.isArray(instance.frameIssues) ? instance.frameIssues : [],
        displayable: compatibility.displayable,
        displayIssue: compatibility.reason,
        mediaType: instance.patientId === 'PAT-3D-SPHERE' ? 'VOLUME_3D' : instance.mediaType
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
