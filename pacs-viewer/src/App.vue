<template>
  <div class="app-root">
    <div class="app-tabs">
      <div 
        v-for="tab in tabs" 
        :key="tab.key" 
        class="app-tab" 
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </div>
    </div>
    
    <div class="app-content">
      <Viewer v-if="activeTab === 'viewer'" />
      <div v-else class="admin-pane p-4">
        <DicomOperationsView v-if="activeTab === 'operations'" />
        <AetAdminView v-if="activeTab === 'admin'" />
        <SyncAdminView v-if="activeTab === 'sync'" />
      </div>
    </div>
  </div>
  <ToastContainer />
</template>

<script setup>
import { ref } from 'vue'
import ToastContainer from './components/ToastContainer.vue'
import Viewer from './views/Viewer.vue'
import DicomOperationsView from './views/DicomOperationsView.vue'
import AetAdminView from './views/AetAdminView.vue'
import SyncAdminView from './views/SyncAdminView.vue'

const activeTab = ref('viewer')
const tabs = [
  { key: 'viewer', label: 'Viewer' },
  { key: 'operations', label: 'Remote Ops' },
  { key: 'admin', label: 'AE Admin' },
  { key: 'sync', label: 'Sync Admin' },
]
</script>

<style>
.app-root {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--primary-bg);
}

.app-tabs {
  height: 32px;
  background-color: var(--secondary-bg);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  padding: 0 8px;
  gap: 4px;
}

.app-tab {
  padding: 0 16px;
  display: flex;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.app-tab:hover {
  color: var(--text-primary);
}

.app-tab.active {
  color: var(--active-color);
  border-bottom-color: var(--active-color);
}

.app-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.admin-pane {
  flex: 1;
  overflow-y: auto;
  background-color: var(--primary-bg);
}
</style>
