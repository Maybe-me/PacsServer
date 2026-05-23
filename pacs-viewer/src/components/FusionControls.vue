<template>
  <div class="fusion-controls">
    <!-- Header -->
    <div class="fusion-header">
      <span class="fusion-header__icon">⚛️</span>
      <span class="fusion-header__title">PET/CT 融合双重可视化</span>
    </div>

    <!-- Modality Info badges -->
    <div class="modality-badges">
      <span class="badge ct">CT Grayscale</span>
      <span class="badge pt-plus">+</span>
      <span class="badge pt" :style="{ backgroundColor: getColormapColor(selectedColormap) }">PET {{ selectedColormap.toUpperCase() }}</span>
    </div>

    <!-- Fusion Alpha Opacity Slider -->
    <div class="control-group">
      <div class="label-row">
        <label class="control-label" for="fusion-alpha-slider">融合比例 (PET Alpha)</label>
        <span class="value-badge">{{ Math.round(alpha * 100) }}%</span>
      </div>
      <input
        id="fusion-alpha-slider"
        type="range"
        min="0"
        max="100"
        v-model.number="alphaPercent"
        @input="emitAlpha"
        class="custom-range"
      />
    </div>

    <!-- Colormap Selector -->
    <div class="control-group">
      <label class="control-label" for="colormap-select">PET 伪彩方案 (Colormaps)</label>
      <div class="select-wrapper">
        <select id="colormap-select" v-model="selectedColormap">
          <option v-for="cmap in colormaps" :key="cmap.id" :value="cmap.id">
            {{ cmap.name }}
          </option>
        </select>
      </div>
    </div>

    <!-- SUV Max Indicator -->
    <div class="control-group diagnostic-info">
      <div class="info-row">
        <span class="info-label">PET SUV Max:</span>
        <span class="info-value text-accent font-bold">12.5 (High Uptake)</span>
      </div>
      <div class="info-row">
        <span class="info-label">CT Volume:</span>
        <span class="info-value">32 Slices (PAT-3D-SPHERE)</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue';

const emit = defineEmits([
  'update:alpha',
  'update:colormap',
]);

const alphaPercent = ref(50);
const alpha = computed(() => alphaPercent.value / 100);

const colormaps = [
  { id: 'hot', name: '🔥 Hot Metal (热金属色)' },
  { id: 'jet', name: '🌈 Rainbow Jet (高对比彩虹)' },
  { id: 'coolwarm', name: '❄️ Coolwarm (冷暖双极)' },
  { id: 'hsv', name: '🔮 HSV Spectrum (彩相全谱)' },
];
const selectedColormap = ref('hot');

const emitAlpha = () => {
  emit('update:alpha', alpha.value);
};

watch(selectedColormap, (newColormap) => {
  emit('update:colormap', newColormap);
});

// Helper to style badge based on active colormap
function getColormapColor(cmap) {
  if (cmap === 'hot') return '#ff5a00';
  if (cmap === 'jet') return '#00a4d9';
  if (cmap === 'coolwarm') return '#e29e4a';
  return '#9333ea';
}
</script>

<style scoped>
.fusion-controls {
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

.fusion-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 0.75rem;
  margin-bottom: 0.25rem;
}

.fusion-header__icon {
  font-size: 1.25rem;
  filter: drop-shadow(0 0 6px rgba(0, 164, 219, 0.5));
}

.fusion-header__title {
  font-size: 0.95rem;
  font-weight: 700;
  letter-spacing: 0.5px;
  background: linear-gradient(135deg, #ffffff 0%, #00a4d9 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* Modality Badges */
.modality-badges {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-bottom: 0.25rem;
}

.badge {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 20px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.badge.ct {
  background-color: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #fff;
}

.badge.pt {
  color: #fff;
  transition: all 0.3s ease;
  box-shadow: 0 0 10px rgba(0, 164, 219, 0.2);
}

.badge.pt-plus {
  font-size: 0.85rem;
  color: #a0a0a5;
  padding: 0;
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

/* Diagnostics details list at bottom */
.diagnostic-info {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 0.6rem;
  font-size: 0.75rem;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 2px 0;
}

.info-label {
  color: #8c8c93;
}

.info-value {
  color: #f5f5f7;
  font-weight: 500;
}

.text-accent {
  color: #e11d48; /* Crimson / rose color for high uptake warning */
}

.font-bold {
  font-weight: 700;
}
</style>
