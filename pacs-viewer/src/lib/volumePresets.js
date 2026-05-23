/**
 * volumePresets.js
 * Transfer function presets for 3D Volume Rendering (VR).
 * Each preset defines color/opacity control points for CT Hounsfield Unit (HU) ranges.
 * These map to Cornerstone3D / VTK.js piecewise linear transfer functions.
 */

export const VOLUME_PRESETS = {
  /** Bone – highlights calcified structures */
  Bone: {
    label: 'Bone',
    icon: '🦴',
    description: 'Calcified structures & cortical bone',
    voiRange: { lower: 400, upper: 1500 },
    colormap: 'bone',
    opacity: [
      { value: -1000, opacity: 0.0 },
      { value: 400,   opacity: 0.0 },
      { value: 600,   opacity: 0.12 },
      { value: 1000,  opacity: 0.7 },
      { value: 1500,  opacity: 1.0 },
    ],
    color: [
      { value: -1000, r: 0,   g: 0,   b: 0 },
      { value: 400,   r: 0,   g: 0,   b: 0 },
      { value: 600,   r: 0.9, g: 0.85,b: 0.75 },
      { value: 1000,  r: 1.0, g: 0.95,b: 0.90 },
      { value: 1500,  r: 1.0, g: 1.0, b: 1.0 },
    ],
  },

  /** Vascular – highlights blood vessels with iodine contrast */
  Vascular: {
    label: 'Vascular',
    icon: '🫀',
    description: 'Blood vessels & iodine contrast',
    voiRange: { lower: 100, upper: 700 },
    colormap: 'hot_iron',
    opacity: [
      { value: -1000, opacity: 0.0 },
      { value: 100,   opacity: 0.0 },
      { value: 200,   opacity: 0.25 },
      { value: 400,   opacity: 0.6 },
      { value: 700,   opacity: 0.85 },
    ],
    color: [
      { value: -1000, r: 0,   g: 0,   b: 0 },
      { value: 100,   r: 0,   g: 0,   b: 0 },
      { value: 200,   r: 0.8, g: 0.2, b: 0.1 },
      { value: 400,   r: 1.0, g: 0.5, b: 0.2 },
      { value: 700,   r: 1.0, g: 0.85,b: 0.5 },
    ],
  },

  /** Chest/Lung – highlights airways and pulmonary parenchyma */
  Lung: {
    label: 'Lung',
    icon: '🫁',
    description: 'Pulmonary parenchyma & airways',
    voiRange: { lower: -1000, upper: -200 },
    colormap: 'cool',
    opacity: [
      { value: -1000, opacity: 0.0 },
      { value: -900,  opacity: 0.2 },
      { value: -700,  opacity: 0.5 },
      { value: -200,  opacity: 0.8 },
      { value: 0,     opacity: 0.0 },
    ],
    color: [
      { value: -1000, r: 0,   g: 0,   b: 0 },
      { value: -900,  r: 0.3, g: 0.5, b: 0.8 },
      { value: -700,  r: 0.5, g: 0.8, b: 0.9 },
      { value: -200,  r: 0.8, g: 0.9, b: 1.0 },
      { value: 0,     r: 0,   g: 0,   b: 0 },
    ],
  },

  /** Muscle/Soft Tissue – highlights muscle, fat, and soft tissue contrast */
  Muscle: {
    label: 'Muscle',
    icon: '💪',
    description: 'Soft tissue & muscle differentiation',
    voiRange: { lower: -200, upper: 400 },
    colormap: 'rainbow',
    opacity: [
      { value: -200, opacity: 0.0 },
      { value: -100, opacity: 0.1 },
      { value: 0,    opacity: 0.3 },
      { value: 100,  opacity: 0.55 },
      { value: 400,  opacity: 0.75 },
    ],
    color: [
      { value: -200, r: 0,   g: 0,   b: 0 },
      { value: -100, r: 0.9, g: 0.7, b: 0.3 },
      { value: 0,    r: 1.0, g: 0.5, b: 0.3 },
      { value: 100,  r: 0.8, g: 0.3, b: 0.2 },
      { value: 400,  r: 0.6, g: 0.2, b: 0.1 },
    ],
  },

  /** Brain – highlights gray/white matter differentiation */
  Brain: {
    label: 'Brain',
    icon: '🧠',
    description: 'Gray/white matter differentiation',
    voiRange: { lower: 0, upper: 100 },
    colormap: 'gray',
    opacity: [
      { value: -100, opacity: 0.0 },
      { value: 0,    opacity: 0.05 },
      { value: 30,   opacity: 0.3 },
      { value: 70,   opacity: 0.6 },
      { value: 100,  opacity: 0.8 },
    ],
    color: [
      { value: -100, r: 0,   g: 0,   b: 0 },
      { value: 0,    r: 0.2, g: 0.15,b: 0.1 },
      { value: 30,   r: 0.6, g: 0.5, b: 0.4 },
      { value: 70,   r: 0.85,g: 0.8, b: 0.75 },
      { value: 100,  r: 0.95,g: 0.95,b: 0.9 },
    ],
  },

  /** Full Body – general overview with all densities visible */
  'Full Body': {
    label: 'Full Body',
    icon: '🩻',
    description: 'General full-body volume overview',
    voiRange: { lower: -1000, upper: 2000 },
    colormap: 'gray',
    opacity: [
      { value: -1000, opacity: 0.0 },
      { value: -500,  opacity: 0.05 },
      { value: -200,  opacity: 0.15 },
      { value: 100,   opacity: 0.35 },
      { value: 500,   opacity: 0.65 },
      { value: 2000,  opacity: 0.9 },
    ],
    color: [
      { value: -1000, r: 0,   g: 0,   b: 0 },
      { value: -500,  r: 0.2, g: 0.2, b: 0.2 },
      { value: 0,     r: 0.5, g: 0.4, b: 0.35 },
      { value: 500,   r: 0.85,g: 0.8, b: 0.75 },
      { value: 2000,  r: 1.0, g: 1.0, b: 1.0 },
    ],
  },
}

export const PRESET_NAMES = Object.keys(VOLUME_PRESETS)
export const DEFAULT_PRESET = 'Bone'
