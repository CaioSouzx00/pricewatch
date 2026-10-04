<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'

const auth = useAuth()
const router = useRouter()

async function logout() {
  auth.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <header class="app-header">
    <RouterLink to="/" class="brand">MVP</RouterLink>
    <div class="user">
      <span id="user-name">{{ auth.user.value?.name }}</span>
      <button id="logout" class="btn ghost" type="button" @click="logout">Sair</button>
    </div>
  </header>
</template>

<style scoped>
.brand {
  text-decoration: none;
}
</style>
