<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import Menu from 'primevue/menu'

const router = useRouter()

const items = ref([
    {
        label: 'General',
        items: [
            {
                label: 'Dashboard',
                icon: 'pi pi-home',
                command: () => { router.push('/') }
            },
            {
                label: 'Products',
                icon: 'pi pi-box',
                command: () => { router.push('/products') }
            },
            {
                label: 'Watchlist',
                icon: 'pi pi-star',
                command: () => { router.push('/watchlist') }
            },
            {
                label: 'Alerts',
                icon: 'pi pi-bell',
                command: () => { router.push('/alerts') }
            }
        ]
    }
])

function logout() {
    localStorage.removeItem('token')
    router.push('/login')
}
</script>

<template>
  <div class="layout-wrapper">
    <div class="layout-sidebar">
      <div class="flex items-center p-4 gap-2 border-b border-[var(--p-surface-800)]" style="height: 64px;">
        <i class="pi pi-bullseye text-green" style="font-size: 1.5rem"></i>
        <span class="font-bold text-xl">DealRadar</span>
      </div>
      <div class="p-2" style="flex-grow: 1;">
        <Menu :model="items" class="w-full" style="background: transparent; border: none;" />
      </div>
    </div>
    
    <div class="layout-main-container">
      <header class="layout-header">
        <div></div> <!-- left side header -->
        <div class="flex items-center gap-4">
            <button @click="logout" class="flex items-center gap-2" style="background: transparent; border: none; color: var(--p-surface-400); cursor: pointer;">
                <i class="pi pi-sign-out"></i>
                <span>Logout</span>
            </button>
        </div>
      </header>
      
      <main class="layout-main">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style>
/* Reset Menu styles to fit sidebar */
.p-menu {
    width: 100% !important;
}
.p-menuitem-link {
    padding: 0.75rem 1rem !important;
    border-radius: 8px !important;
}
.p-menuitem-link:hover {
    background: var(--p-surface-800) !important;
}
.p-menuitem-text {
    color: var(--p-surface-100) !important;
}
.p-menuitem-icon {
    color: var(--p-surface-400) !important;
}
.p-menuitem-link[data-p-focused="true"] {
    background: var(--p-surface-800) !important;
}
</style>
