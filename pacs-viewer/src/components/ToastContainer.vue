<template>
  <teleport to="body">
    <div class="toast-container">
      <div v-for="toast in toasts" :key="toast.id" class="toast" :class="toast.type">
        {{ toast.message }}
      </div>
    </div>
  </teleport>
</template>

<script setup>
import { useToast } from '../composables/useToast';
const { toasts } = useToast();
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 20px;
  right: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  z-index: 10000;
}
.toast {
  padding: 0.75rem 1rem;
  background: #333;
  color: #fff;
  border-radius: 4px;
  min-width: 200px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.3);
  font-size: 0.9rem;
}
.toast.info { background: #2c7be5; }
.toast.success { background: #28a745; }
.toast.warning { background: #ffc107; }
.toast.error { background: #dc3545; }
</style>
