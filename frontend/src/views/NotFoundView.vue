<script setup lang="ts">
import { useRouter } from 'vue-router'
import Button from 'primevue/button'

const router = useRouter()
</script>

<template>
  <div class="terminal-404-container">
    <div class="terminal-window">
      <div class="terminal-bar">
        <div class="dots">
          <span class="dot red" />
          <span class="dot yellow" />
          <span class="dot green" />
        </div>
        <span class="tech-label">ERR_CODE: 404 // ASSET_NOT_FOUND</span>
      </div>

      <div class="terminal-content">
        <div class="glitch-title">404</div>
        <h1 class="headline">Ativo fora do livro de ofertas</h1>
        <p class="description">
          A rota solicitada não corresponde a nenhum produto, ticker ou tela do PriceWatch Terminal.
          O link pode ter expirado ou o ativo foi descontinuado do monitoramento.
        </p>

        <div class="debug-box">
          <span class="tech-label">SISTEMA STATUS:</span>
          <code>TRACE: ROUTE_UNRESOLVED • PROTOCOL: HTTPS • FALLBACK_MODE: ENGAGED</code>
        </div>

        <div class="actions">
          <Button
            label="Retornar ao Dashboard"
            icon="pi pi-arrow-left"
            class="p-button-primary"
            @click="router.push('/')"
          />
          <Button
            label="Ver Produtos"
            icon="pi pi-box"
            severity="secondary"
            @click="router.push('/products')"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.terminal-404-container {
  min-height: calc(100vh - 120px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.terminal-window {
  width: 100%;
  max-width: 580px;
  background: var(--surface-1);
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  box-shadow: 0 24px 64px -16px rgba(0, 0, 0, 0.75);
  overflow: hidden;
}

.terminal-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  background: var(--surface-2);
  border-bottom: 1px solid var(--border-subtle);
}

.dots {
  display: flex;
  gap: 6px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.dot.red { background: #ef4444; }
.dot.yellow { background: #f59e0b; }
.dot.green { background: #10b981; }

.terminal-content {
  padding: 2.5rem 2rem;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.glitch-title {
  font-family: var(--font-display);
  font-size: clamp(4rem, 10vw, 6.5rem);
  font-weight: 700;
  line-height: 1;
  color: var(--text-primary);
  letter-spacing: -0.05em;
  margin-bottom: 0.5rem;
  font-variant-numeric: tabular-nums;
}

.headline {
  font-family: var(--font-display);
  font-size: 1.375rem;
  font-weight: 600;
  margin: 0 0 0.75rem;
  color: var(--text-primary);
}

.description {
  font-size: 0.875rem;
  color: var(--text-muted);
  line-height: 1.6;
  max-width: 440px;
  margin: 0 0 1.5rem;
}

.debug-box {
  width: 100%;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: 4px;
  padding: 0.75rem 1rem;
  text-align: left;
  margin-bottom: 1.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.debug-box code {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  color: var(--text-secondary);
  letter-spacing: 0.02em;
}

.actions {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  justify-content: center;
}
</style>
