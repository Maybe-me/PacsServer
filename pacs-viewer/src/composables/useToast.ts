import { ref } from 'vue';

export interface Toast {
  id: number;
  message: string;
  type?: 'info' | 'error' | 'success' | 'warning';
}

const toasts = ref<Toast[]>([]);
let nextId = 0;

export function useToast() {
  const add = (message: string, type: Toast['type'] = 'info', duration = 3000) => {
    const toast: Toast = { id: nextId++, message, type };
    toasts.value.push(toast);
    setTimeout(() => {
      toasts.value = toasts.value.filter(t => t.id !== toast.id);
    }, duration);
  };
  const clear = () => {
    toasts.value = [];
  };
  return { toasts, add, clear };
}
