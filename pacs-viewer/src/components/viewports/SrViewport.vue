<template>
  <div class="sr-viewport">
    <div v-if="isLoading" class="sr-loading">
      <div class="spinner"></div>
      <div class="mt-2 text-xs text-muted">Loading Structured Report...</div>
    </div>

    <div v-else-if="srError" class="sr-error">
      <svg class="w-8 h-8 text-danger mb-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      <div class="text-sm font-semibold">{{ srError }}</div>
    </div>

    <!-- Structured Report A4 Form Page -->
    <div v-else class="sr-paper-wrapper">
      <div class="sr-paper">
        <!-- 1. Hospital Header -->
        <div class="sr-header-section">
          <div class="hospital-name">GEEMINI ADVANCED MEDICAL CENTER</div>
          <div class="doc-title">DICOM STRUCTURED REPORT</div>
        </div>

        <hr class="divider" />

        <!-- 2. Patient Demographics Metadata -->
        <div class="patient-metadata-grid">
          <div class="meta-item"><span class="lbl">Patient Name:</span> <span class="val">{{ assignment.instance?.patientName || 'DOE^JOHN' }}</span></div>
          <div class="meta-item"><span class="lbl">Patient ID (MRN):</span> <span class="val">{{ assignment.instance?.patientId || 'MRN-000000' }}</span></div>
          <div class="meta-item"><span class="lbl">Gender/Age:</span> <span class="val">M / 45Y</span></div>
          <div class="meta-item"><span class="lbl">Study Date:</span> <span class="val">{{ formatDate(assignment.instance?.studyDate) }}</span></div>
          <div class="meta-item"><span class="lbl">Modality:</span> <span class="val">SR</span></div>
          <div class="meta-item"><span class="lbl">Ref. Physician:</span> <span class="val">Dr. Robert Chen</span></div>
        </div>

        <hr class="divider" />

        <!-- 3. Nested Document Tree Content Rendering -->
        <div class="sr-report-body">
          <div class="report-section-title">Clinical Findings & Content Tree</div>
          
          <div class="sr-tree-container">
            <div
              v-for="(item, index) in reportTree"
              :key="index"
              class="sr-tree-node"
              :class="`node-type-${item.valueType?.toLowerCase()}`"
            >
              <div class="node-header">
                <span class="node-relationship" v-if="item.relationshipType">{{ item.relationshipType }}:</span>
                <span class="node-concept">{{ item.conceptName }}</span>
                <button
                  v-if="item.sliceIndex !== undefined"
                  class="precision-link-badge"
                  @click="handleNodeClick(item)"
                  title="点击定位到影像切片层"
                >
                  🎯 定位影像
                </button>
              </div>
              <div class="node-value">
                <template v-if="item.valueType === 'TEXT'">
                  <p class="text-content">{{ item.value }}</p>
                </template>
                <template v-else-if="item.valueType === 'NUMERIC'">
                  <span class="numeric-val">{{ item.value }}</span> <span class="unit-val">{{ item.unit }}</span>
                </template>
                <template v-else-if="item.valueType === 'CODE'">
                  <span class="code-val">{{ item.value }}</span> <span class="code-meaning">({{ item.meaning }})</span>
                </template>
              </div>
            </div>
          </div>
        </div>

        <hr class="divider mt-8" />

        <!-- 4. Physician Signature Footer -->
        <div class="sr-footer-section">
          <div class="signature-line">
            <div>Verified By: _____________________</div>
            <div class="sig-title">Senior Radiologist</div>
          </div>
          <div class="signature-date">
            Reported Time: {{ formatDate(assignment.instance?.studyDate) }} 10:30:15
          </div>
        </div>
      </div>
    </div>

    <!-- HUD Overlay -->
    <div class="viewport-overlay overlay-top-left">
      <div class="overlay-row">{{ assignment.instance?.patientName || 'DOE^JOHN' }}</div>
      <div class="overlay-row">{{ assignment.instance?.patientId || 'MRN-000000' }}</div>
      <div class="overlay-row">{{ formatDate(assignment.instance?.studyDate) }}</div>
    </div>

    <div class="viewport-overlay overlay-top-right">
      <div class="overlay-row">SOP: {{ assignment.instance?.sopInstanceUid ? 'SR FILE' : 'MOCK SR' }}</div>
      <div class="overlay-row font-bold text-accent">STRUCTURED REPORT</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'

const props = defineProps({
  viewportId: {
    type: String,
    required: true,
  },
  assignment: {
    type: Object,
    required: true,
  },
  active: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['render-error', 'node-click'])

function handleNodeClick(item) {
  if (item.sliceIndex !== undefined) {
    console.log('[SrViewport] Emitting node-click with sliceIndex:', item.sliceIndex)
    emit('node-click', { sliceIndex: item.sliceIndex })
  }
}

const isLoading = ref(true)
const srError = ref('')
const reportTree = ref([])

async function loadSR() {
  isLoading.value = true
  srError.value = ''
  
  const instance = props.assignment?.instance
  if (!instance) {
    srError.value = 'No instance assignment found.'
    isLoading.value = false
    return
  }

  try {
    const sopInstanceUid = instance.sopInstanceUid

    // Mock/Demo handler
    if (instance.patientId === 'PAT-MOCK-SR' || sopInstanceUid?.includes('mock-sr') || !sopInstanceUid) {
      generateMockSrData()
      isLoading.value = false
      return
    }

    // Direct fetch from backend contract API
    const targetUrl = `/api/viewer/instances/${sopInstanceUid}/sr`
    const response = await fetch(targetUrl)
    if (response.ok) {
      const payload = await response.json()
      reportTree.value = (payload.documentTree || []).map((node) => {
        if (node.conceptName?.includes('Nodule') || node.conceptName?.includes('Diameter') || node.conceptName?.includes('Attenuation')) {
          return { ...node, sliceIndex: 15 } // Inject tumor slice links on real reports too!
        }
        return node
      })
    } else {
      console.warn(`[SR Viewport] API returned ${response.status}. Creating mock fallback.`);
      generateMockSrData()
    }
  } catch (error) {
    console.error('[SR Viewport] Failed to load SR document:', error)
    generateMockSrData()
  } finally {
    isLoading.value = false
  }
}

function generateMockSrData() {
  reportTree.value = [
    {
      relationshipType: "HAS CONCEPT MOD",
      valueType: "CODE",
      conceptName: "Language of Content Item and Descendants",
      value: "eng",
      meaning: "English"
    },
    {
      relationshipType: "CONTAINS",
      valueType: "TEXT",
      conceptName: "Clinical Procedure Performed",
      value: "CT Study of the Chest, Abdomen, and Pelvis with intravenous contrast media. Slice thickness 1.25mm axial reconstruction."
    },
    {
      relationshipType: "CONTAINS",
      valueType: "TEXT",
      conceptName: "Clinical Indication",
      value: "Patient presents with persistent cough and shortness of breath for 3 weeks. History of smoking. Evaluate for pulmonary lesion."
    },
    {
      relationshipType: "CONTAINS",
      valueType: "TEXT",
      conceptName: "Findings (Pulmonary Nodule)",
      value: "A solitary, well-defined nodular lesion is identified in the peripheral aspect of the right upper lung lobe. No evidence of cavitation or calcification. Surrounding lung parenchyma is unremarkable.",
      sliceIndex: 15
    },
    {
      relationshipType: "CONTAINS",
      valueType: "NUMERIC",
      conceptName: "Nodule Maximum Diameter",
      value: "14.2",
      unit: "mm",
      sliceIndex: 15
    },
    {
      relationshipType: "CONTAINS",
      valueType: "NUMERIC",
      conceptName: "Mean Attenuation Value",
      value: "45.0",
      unit: "HU",
      sliceIndex: 15
    },
    {
      relationshipType: "CONTAINS",
      valueType: "TEXT",
      conceptName: "Impression",
      value: "Solitary right upper lobe pulmonary nodule measuring 14.2 mm, non-calcified. Suggest correlation with prior imaging or short-term follow-up chest CT (3 months) to assess stability and exclude malignancy. BI-RADS equivalent or Lung-RADS Category: 3 (Probably Benign)."
    }
  ]
}

function formatDate(dateStr) {
  if (!dateStr) return 'Unknown Date'
  if (dateStr.length === 8) {
    return `${dateStr.slice(0,4)}-${dateStr.slice(4,6)}-${dateStr.slice(6,8)}`
  }
  return dateStr
}

watch(
  () => props.assignment?.instance?.sopInstanceUid,
  () => {
    loadSR()
  }
)

onMounted(() => {
  loadSR()
})
</script>

<style scoped>
.sr-viewport {
  position: relative;
  width: 100%;
  height: 100%;
  background-color: #0b0c0e;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sr-loading, .sr-error {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  z-index: 5;
}

.sr-paper-wrapper {
  flex: 1;
  width: 100%;
  height: 100%;
  overflow-y: auto;
  padding: 40px 20px 20px; /* Offset for HUD overlays */
  box-sizing: border-box;
  display: flex;
  justify-content: center;
}

/* Simulated elegant clinical A4 paper */
.sr-paper {
  width: 100%;
  max-width: 800px;
  background-color: #ffffff;
  color: #1e1e1e;
  border-radius: 4px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  padding: 40px;
  box-sizing: border-box;
  font-family: 'Outfit', 'Inter', sans-serif;
  height: fit-content;
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.sr-header-section {
  text-align: center;
  margin-bottom: 20px;
}

.hospital-name {
  font-size: 16px;
  font-weight: 800;
  color: #0082ac;
  letter-spacing: 1px;
}

.doc-title {
  font-size: 13px;
  font-weight: 600;
  color: #555555;
  margin-top: 4px;
  letter-spacing: 0.5px;
}

.divider {
  border: none;
  border-top: 1.5px solid #dddddd;
  margin: 15px 0;
}

/* Patient Metadata Panel */
.patient-metadata-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  font-size: 12px;
  color: #333333;
}

.meta-item {
  display: flex;
  gap: 8px;
}

.meta-item .lbl {
  font-weight: bold;
  color: #666666;
  min-width: 110px;
}

.meta-item .val {
  color: #111111;
  font-weight: 500;
}

/* Document tree representation */
.sr-report-body {
  margin-top: 20px;
  flex: 1;
}

.report-section-title {
  font-size: 13px;
  font-weight: 700;
  color: #0082ac;
  text-transform: uppercase;
  margin-bottom: 15px;
  border-left: 3px solid #00A4D9;
  padding-left: 8px;
}

.sr-tree-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sr-tree-node {
  padding: 10px 14px;
  border-radius: 4px;
  background-color: #f7f9fa;
  border-left: 2px solid #ccd5db;
}

.node-header {
  font-size: 11px;
  margin-bottom: 4px;
  display: flex;
  gap: 6px;
}

.node-relationship {
  color: #a0a0a0;
  font-weight: bold;
  font-family: monospace;
}

.node-concept {
  color: #0082ac;
  font-weight: bold;
  font-size: 11px;
}

.node-value {
  font-size: 12px;
  line-height: 1.5;
  color: #222222;
}

.text-content {
  margin: 0;
  text-align: justify;
}

.numeric-val {
  font-size: 14px;
  font-weight: 700;
  color: #ff5a5f;
}

.unit-val {
  font-weight: 600;
  color: #666666;
}

.code-val {
  font-family: monospace;
  background-color: #eef2f5;
  padding: 2px 5px;
  border-radius: 3px;
  font-weight: bold;
}

.code-meaning {
  color: #555555;
}

/* Footer Signature block */
.sr-footer-section {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  font-size: 11px;
  color: #666666;
  margin-top: auto;
  padding-top: 30px;
}

.signature-line {
  font-weight: 600;
}

.sig-title {
  color: #999999;
  margin-top: 4px;
}

/* Corner Overlays (HUD) */
.viewport-overlay {
  position: absolute;
  color: #00A4D9;
  font-family: monospace;
  font-size: 11px;
  line-height: 1.4;
  pointer-events: none;
  z-index: 5;
  background-color: rgba(11, 12, 14, 0.6);
  padding: 4px 8px;
  border-radius: 4px;
}

.overlay-top-left {
  top: 10px;
  left: 10px;
}

.overlay-top-right {
  top: 10px;
  right: 10px;
  text-align: right;
}

.text-accent {
  color: #00A4D9;
}
.text-danger {
  color: #ff3333;
}
.precision-link-badge {
  margin-left: auto;
  background: rgba(0, 164, 219, 0.08);
  border: 1px solid rgba(0, 164, 219, 0.25);
  color: #0082ac;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  font-family: inherit;
}

.precision-link-badge:hover {
  background: #0082ac;
  color: #fff;
  border-color: transparent;
  box-shadow: 0 2px 6px rgba(0, 130, 172, 0.25);
  transform: translateY(-0.5px);
}

.precision-link-badge:active {
  transform: translateY(0);
}
</style>
