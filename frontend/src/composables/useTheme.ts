import { ref } from 'vue'

const THEME_KEY = 'pricewatch_theme'
const isDark = ref<boolean>(true)

// Initialize
if (typeof window !== 'undefined') {
  const saved = localStorage.getItem(THEME_KEY)
  if (saved !== null) {
    isDark.value = saved === 'dark'
  } else {
    isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
  }
  document.documentElement.classList.toggle('dark', isDark.value)
}

export function useTheme() {
  function toggleTheme() {
    isDark.value = !isDark.value
    document.documentElement.classList.toggle('dark', isDark.value)
    localStorage.setItem(THEME_KEY, isDark.value ? 'dark' : 'light')
  }

  function setDark(val: boolean) {
    isDark.value = val
    document.documentElement.classList.toggle('dark', isDark.value)
    localStorage.setItem(THEME_KEY, isDark.value ? 'dark' : 'light')
  }

  return {
    isDark,
    toggleTheme,
    setDark,
  }
}
