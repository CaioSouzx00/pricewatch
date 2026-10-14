<script setup lang="ts">
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'
import CustomCursor from './components/CustomCursor.vue'
import { useLenis } from './composables/useLenis'

// Initialize smooth scrolling where appropriate
useLenis()
</script>

<template>
  <CustomCursor />
  <Toast position="bottom-right" />
  <ConfirmDialog />
  <RouterView v-slot="{ Component }">
    <transition name="terminal-route" mode="out-in">
      <component :is="Component" />
    </transition>
  </RouterView>
</template>

<style>
/* Page transition */
.terminal-route-enter-active,
.terminal-route-leave-active {
  transition: opacity 0.2s var(--ease-terminal), transform 0.2s var(--ease-terminal);
}

.terminal-route-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.terminal-route-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
