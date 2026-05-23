<template>
  <div class="three-d-controls">
    <!-- Header -->
    <div class="three-d-header">
      <span class="three-d-header__icon">🧬</span>
      <span class="three-d-header__title">3D VR 体渲染控制</span>
    </div>

    <!-- Transfer Function Preset Selector -->
    <div class="control-group">
      <label class="control-label" for="preset-select">渲染预设 (Presets)</label>
      <div class="select-wrapper">
        <select id="preset-select" v-model="selectedPreset">
          <option v-for="preset in presets" :key="preset.id" :value="preset.id">
            {{ preset.name }}
          </option>
        </select>
      </div>
    </div>

    <!-- Opacity Slider -->
    <div class="control-group">
      <div class="label-row">
        <label class="control-label" for="opacity-slider">不透明度 (Opacity)</label>
        <span class="value-badge">{{ opacity }}%</span>
      </div>
      <input
        id="opacity-slider"
        type="range"
        min="0"
        max="100"
        v-model.number="opacity"
        @input="emitOpacity"
        class="custom-range"
      />
    </div>

    <!-- Window Width slider -->
    <div class="control-group">
      <div class="label-row">
        <label class="control-label" for="window-width">窗宽 (Window Width)</label>
        <span class="value-badge">{{ windowWidth }}</span>
      </div>
      <input
        id="window-width"
        type="range"
        :min="windowWidthRange.min"
        :max="windowWidthRange.max"
        v-model.number="windowWidth"
        @input="emitWindow"
        class="custom-range"
      />
    </div>

    <!-- Window Level slider -->
    <div class="control-group">
      <div class="label-row">
        <label class="control-label" for="window-level">窗位 (Window Level)</label>
        <span class="value-badge">{{ windowLevel }}</span>
      </div>
      <input
        id="window-level"
        type="range"
        :min="windowLevelRange.min"
        :max="windowLevelRange.max"
        v-model.number="windowLevel"
        @input="emitWindow"
        class="custom-range"
      />
    </div>

    <!-- Clipping Toggle -->
    <button @click="toggleClipping" class="clipping-btn" :class="{ 'clipping-btn--active': clippingEnabled }">
      <span class="btn-icon">{{ clippingEnabled ? '✂️' : '📐' }}</span>
      <span>{{ clippingEnabled ? '关闭 3D 裁剪' : '启用 3D 裁剪' }}</span>
    </button>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { VOLUME_PRESETS } from '../lib/volumePresets';

const emit = defineEmits([
  'update:transferFunction',
  'update:opacity',
  'update:threshold',
  'toggleClipping',
]);

// Presets
const presets = Object.entries(VOLUME_PRESETS).map(([id, value]) => ({
  id,
  name: `${value.icon} ${value.label}`,
  ...value
}));
const selectedPreset = ref(presets[0]?.id || null);
watch(selectedPreset, (newId) => {
  const preset = presets.find((p) => p.id === newId);
  if (preset) emit('update:transferFunction', preset);
});

// Opacity
const opacity = ref(100);
const emitOpacity = () => {
  emit('update:opacity', opacity.value / 100);
};

// Window Width / Level (threshold)
const windowWidth = ref(400);
const windowLevel = ref(40);
const windowWidthRange = computed(() => ({ min: 1, max: 2000 }));
const windowLevelRange = computed(() => ({ min: -1000, max: 1000 }));
const emitWindow = () => {
  emit('update:threshold', { width: windowWidth.value, level: windowLevel.value });
};

// Clipping
const clippingEnabled = ref(false);
const toggleClipping = () => {
  clippingEnabled.value = !clippingEnabled.value;
  emit('toggleClipping', clippingEnabled.value);
};
</script>

<style scoped>
.three-d-controls {
  position: fixed;
  bottom: 1.5rem;
  right: 1.5rem;
  background: rgba(18, 18, 18, 0.82);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 1.25rem;
  color: #f5f5f7;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.6);
  width: 320px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.three-d-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 0.75rem;
  margin-bottom: 0.25rem;
}

.three-d-header__icon {
  font-size: 1.25rem;
  filter: drop-shadow(0 0 6px rgba(0, 164, 219, 0.5));
}

.three-d-header__title {
  font-size: 0.95rem;
  font-weight: 700;
  letter-spacing: 0.5px;
  background: linear-gradient(135deg, #ffffff 0%, #00a4d9 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.control-group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.control-label {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.75px;
  color: #a0a0a5;
}

.value-badge {
  font-size: 0.75rem;
  font-weight: 700;
  color: #00a4d9;
  background: rgba(0, 164, 219, 0.12);
  padding: 1px 6px;
  border-radius: 4px;
  font-family: monospace;
}

/* Custom Dropdown Styling */
.select-wrapper {
  position: relative;
  width: 100%;
}

.select-wrapper::after {
  content: "▼";
  font-size: 0.65rem;
  color: #888;
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  pointer-events: none;
  transition: color 0.2s;
}

.select-wrapper:hover::after {
  color: #00a4d9;
}

select {
  width: 100%;
  background: rgba(30, 30, 30, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  color: #fff;
  padding: 0.5rem 2rem 0.5rem 0.75rem;
  font-size: 0.85rem;
  outline: none;
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
  transition: all 0.2s ease;
}

select:focus {
  border-color: #00a4d9;
  box-shadow: 0 0 0 2px rgba(0, 164, 219, 0.2);
}

select option {
  background-color: #1a1a1a;
  color: #fff;
}

/* Custom Range Input */
.custom-range {
  -webkit-appearance: none;
  width: 100%;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.15);
  outline: none;
  margin: 8px 0;
  transition: background 0.2s;
}

.custom-range::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #00a4d9;
  cursor: pointer;
  box-shadow: 0 0 8px rgba(0, 164, 219, 0.6);
  transition: transform 0.1s, background-color 0.2s;
}

.custom-range::-webkit-slider-thumb:hover {
  transform: scale(1.2);
  background: #00b4f0;
}

.custom-range:active::-webkit-slider-thumb {
  transform: scale(1.3);
  background: #ffffff;
}

/* Clipping Button */
.clipping-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 0.5rem;
  width: 100%;
  padding: 0.6rem 0;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #fff;
  font-size: 0.85rem;
  font-weight: 600;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.clipping-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  transform: translateY(-1px);
}

.clipping-btn--active {
  background: linear-gradient(135deg, #0082b3 0%, #00a4d9 100%);
  border-color: transparent;
  box-shadow: 0 4px 15px rgba(0, 164, 219, 0.4);
}

.clipping-btn--active:hover {
  background: linear-gradient(135deg, #0093ca 0%, #00b4f0 100%);
  box-shadow: 0 6px 20px rgba(0, 164, 219, 0.5);
}

.btn-icon {
  font-size: 0.95rem;
}
</style>
