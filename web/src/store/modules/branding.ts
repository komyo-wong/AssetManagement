import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import request from '@/utils/http'
import AppConfig from '@/config'
import defaultFavicon from '@imgs/favicon.ico'

export type PlatformBranding = {
  systemName?: string | null
  loginTitle?: string | null
  loginSubtitle?: string | null
  loginWelcomeTitle?: string | null
  loginWelcomeSubtitle?: string | null
  loginBackgroundData?: string | null
  titleLogoData?: string | null
  copyrightText?: string | null
  canEditCopyright?: boolean
  hasLoginBackground?: boolean
  hasTitleLogo?: boolean
}

export const useBrandingStore = defineStore('brandingStore', () => {
  const branding = ref<PlatformBranding>({})
  const loaded = ref(false)
  let inflight: Promise<void> | null = null

  const systemName = computed(
    () => branding.value.systemName?.trim() || AppConfig.systemInfo.name
  )
  const titleLogo = computed(() => branding.value.titleLogoData || '')
  const loginBackground = computed(() => branding.value.loginBackgroundData || '')

  function mimeFromHref(href: string) {
    const dataType = href.match(/^data:(image\/[a-zA-Z0-9.+-]+)/i)
    if (dataType) return dataType[1]
    if (href.includes('.svg')) return 'image/svg+xml'
    if (href.includes('.png')) return 'image/png'
    if (href.includes('.webp')) return 'image/webp'
    if (href.includes('.gif')) return 'image/gif'
    return 'image/x-icon'
  }

  function applyFavicon() {
    const href = titleLogo.value || defaultFavicon
    const type = mimeFromHref(href)
    document
      .querySelectorAll<HTMLLinkElement>(
        "link[rel='icon'], link[rel='shortcut icon'], link[rel='apple-touch-icon']"
      )
      .forEach((el) => el.remove())
    for (const rel of ['icon', 'shortcut icon', 'apple-touch-icon'] as const) {
      const link = document.createElement('link')
      link.rel = rel
      link.type = type
      link.href = href
      document.head.appendChild(link)
    }
  }

  function syncDocumentTitle() {
    const name = systemName.value
    const sep = ' - '
    const current = document.title || ''
    const idx = current.lastIndexOf(sep)
    document.title = idx >= 0 ? `${current.slice(0, idx)}${sep}${name}` : name
  }

  function applyDocumentChrome() {
    if (typeof document === 'undefined') return
    applyFavicon()
    syncDocumentTitle()
  }

  async function ensureLoaded(force = false) {
    if (loaded.value && !force) return
    if (inflight) return inflight
    inflight = (async () => {
      try {
        const data = await request.get<PlatformBranding>({
          url: '/api/v1/public/branding',
          showErrorMessage: false,
          skipAuthRedirect: true
        })
        branding.value = data || {}
        loaded.value = true
        applyDocumentChrome()
      } catch {
        branding.value = {}
        loaded.value = true
        applyDocumentChrome()
      } finally {
        inflight = null
      }
    })()
    return inflight
  }

  function patchLocal(partial: PlatformBranding) {
    branding.value = { ...branding.value, ...partial }
    applyDocumentChrome()
  }

  return {
    branding,
    loaded,
    systemName,
    titleLogo,
    loginBackground,
    ensureLoaded,
    patchLocal,
    applyDocumentChrome
  }
})
