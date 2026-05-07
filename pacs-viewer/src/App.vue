<template>
  <a-layout>
    <a-layout-header style="background: #001529; color: white; font-size: 18px;">
      PACS Viewer & Admin
    </a-layout-header>
    <a-layout-content class="page-shell">
      <a-tabs v-model:activeKey="activeTab" size="large">
        <a-tab-pane key="viewer" tab="Viewer">
          <Viewer />
        </a-tab-pane>
        <a-tab-pane key="operations" tab="Remote Ops">
          <DicomOperationsView />
        </a-tab-pane>
        <a-tab-pane key="admin" tab="AE Admin">
          <AetAdminView />
        </a-tab-pane>
        <a-tab-pane key="sync" tab="Sync Admin">
          <SyncAdminView />
        </a-tab-pane>
      </a-tabs>
    </a-layout-content>
  </a-layout>
</template>

<script setup>
import { ref } from 'vue'
import Viewer from './views/Viewer.vue'
import DicomOperationsView from './views/DicomOperationsView.vue'
import AetAdminView from './views/AetAdminView.vue'
import SyncAdminView from './views/SyncAdminView.vue'

const activeTab = ref('viewer')
</script>
