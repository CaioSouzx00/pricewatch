<script setup lang="ts">
import { useRouter } from 'vue-router'
import Button from 'primevue/button'

const router = useRouter()

function reloadPage() {
  window.location.reload()
}
</script>

<template>
  <div class="terminal-500-container">
    <div class="terminal-window">
      <div class="terminal-bar">
        <div class="dots">
          <span class="dot red" />
          <span class="dot yellow" />
          <span class="dot green" />
        </div>
        <span class="tech-label">ERR_CODE: 500 // TERMINAL_INTERNAL_FAULT</span>
      </div>

      <div class="terminal-content">
        <div class="glitch-title">500</div>
        <h1 class="headline">Instabilidade no Terminal de Dados</h1>
        <p class="description">
          Ocorreu uma falha inesperada no processamento dos fluxos do terminal.
          Os parâmetros de segurança foram preservados e o incidente foi registrado nos logs do servidor.
        </p>

        <div class="debug-box">
          <span class="tech-label">DIAGNÓSTICO // TELEMETRIA:</span>
          <code>SUBSYSTEM: API_GATEWAY • STATUS: FAILSAFE_TRIGGERED • RECOVERY: MANUAL_RETRY</code>
        </div>

        <div class="actions">
          <Button
            label="Recarregar Terminal"
            icon="pi pi-refresh"
            class="p-button-primary cursor-hover"
            @click="reloadPage"
          />
          <Button
            label="Retornar ao Dashboard"
            icon="pi pi-home"
            severity="secondary"
            class="cursor-hover"
            @click="router.push('/')"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.terminal-500-container {
  min-height: calc(100vh - 120px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.terminal-window {
  background: var(--surface-1);
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  max-width: 580px;
  width: 100%;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
  overflow: hidden;
}

.terminal-bar {
  background: var(--surface-2);
  border-bottom: 1px solid var(--border-subtle);
  padding: 0.75rem 1.25rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dots {
  display: flex;
  gap: 0.375rem;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.dot.red {
  background: var(--red);
}

.dot.yellow {
  background: var(--yellow);
}

.dot.green {
  background: var(--accent);
}

.terminal-content {
  padding: 2.5rem 2rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.glitch-title {
  font-family: var(--font-mono);
  font-size: 5rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.05em;
  color: var(--red);
  margin-bottom: 0.5rem;
  text-shadow: 0 0 20px rgba(239, 68, 68, 0.3);
}

.headline {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 0.75rem;
}

.description {
  font-size: 0.875rem;
  color: var(--text-muted);
  line-height: 1.6;
  max-width: 440px;
  margin: 0 0 1.75rem;
}

.debug-box {
  background: var(--bg);
  border: 1px solid var(--border-subtle);
  border-left: 3px solid var(--red);
  border-radius: 4px;
  padding: 0.75rem 1rem;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.35rem;
  width: 100%;
  margin-bottom: 2rem;
  text-align: left;
}

.debug-box code {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  color: var(--text-secondary);
}

.actions {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  justify-content: center;
}
</style>
