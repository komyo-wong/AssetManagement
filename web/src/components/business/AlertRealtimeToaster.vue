<script setup lang="ts">
  import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { useRouter } from 'vue-router'
  import { storeToRefs } from 'pinia'
  import { assetsApi, beaconsApi, fetchAlerts, gatewaysApi, type NamedResource } from '@/api/asset-platform'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import { useUserStore } from '@/store/modules/user'
  import {
    playAlertSound,
    prepareAlertAudio,
    setCustomAlertSound,
    stopAlertSound,
    stopAlertSoundOnNextGesture
  } from '@/utils/alert-sound'
  import { localizeAlertMessage, localizeAlertTitle } from '@/utils/alert-i18n'
  import { fetchMyAlertSound } from '@/api/auth'

  defineOptions({ name: 'AlertRealtimeToaster' })

  type NoticeItem = {
    key: string
    kind: 'asset' | 'gateway' | 'alert'
    title: string
    detail: string
  }

  const { t } = useI18n()
  const router = useRouter()
  const userStore = useUserStore()
  const { getUserInfo: userInfo } = storeToRefs(userStore)
  const { projectScope } = storeToRefs(useTenantContextStore())

  const visible = ref(false)
  const animating = ref(false)
  const items = ref<NoticeItem[]>([])
  const knownKeys = ref<Set<string>>(new Set())
  const primed = ref(false)
  const soundBlockedHint = ref(false)
  let timer: ReturnType<typeof setInterval> | null = null
  let loadInFlight = false

  const popupEnabled = computed(() => userInfo.value.alertPopupEnabled !== false)
  const soundEnabled = computed(() => userInfo.value.alertSoundEnabled !== false)

  const headline = computed(() => {
    const offline = items.value.filter((i) => i.kind !== 'alert').length
    const alerts = items.value.filter((i) => i.kind === 'alert').length
    if (offline && alerts) return t('product.toaster.offlineAndAlerts', { offline, alerts })
    if (offline) return t('product.toaster.offlineOnly', { offline })
    if (alerts) return t('product.toaster.alertsOnly', { alerts })
    return t('product.toaster.needAttention')
  })

  async function triggerSound(opts?: { stopOnGesture?: boolean }) {
    if (!soundEnabled.value) return
    const ok = await playAlertSound({ loop: true })
    soundBlockedHint.value = !ok
    if (ok && opts?.stopOnGesture) {
      stopAlertSoundOnNextGesture()
    }
  }

  async function showNotice(next: NoticeItem[]) {
    if (!popupEnabled.value || !next.length) return
    items.value = next.slice(0, 8)
    visible.value = true
    animating.value = true
    await triggerSound()
  }

  function dismiss() {
    stopAlertSound()
    visible.value = false
    animating.value = false
    items.value = []
    soundBlockedHint.value = false
  }

  function isOfflineFamilyAlert(alert: NamedResource) {
    const ruleType = String(alert.fields?.ruleType || '').toUpperCase()
    if (ruleType === 'ASSET_OFFLINE' || ruleType === 'GATEWAY_OFFLINE' || ruleType === 'BEACON_OFFLINE') {
      return true
    }
    const title = String(alert.name || '')
    return /(?:资产离线|Asset offline|网关离线|Gateway offline|信标离线|Beacon offline)/i.test(title)
  }

  function offlineAlertStillActive(
    alert: NamedResource,
    offlineAssetIds: Set<string>,
    offlineGatewayIds: Set<string>,
    offlineBeaconIds: Set<string>
  ) {
    if (!isOfflineFamilyAlert(alert)) return true
    const resourceType = String(alert.fields?.resourceType || '').toUpperCase()
    const resourceId = String(alert.fields?.resourceId || '')
    if (!resourceId) return true
    if (resourceType === 'ASSET') return offlineAssetIds.has(resourceId)
    if (resourceType === 'GATEWAY') return offlineGatewayIds.has(resourceId)
    if (resourceType === 'BEACON') return offlineBeaconIds.has(resourceId)
    return true
  }

  function goHandle() {
    const first = items.value[0]
    dismiss()
    if (first?.kind === 'gateway') {
      router.push({ name: 'GatewayManagement' })
      return
    }
    if (first?.kind === 'alert') {
      router.push({ name: 'AlertEvents' })
      return
    }
    router.push({ name: 'AssetStatus' })
  }

  async function enableSoundNow() {
    const ok = await playAlertSound({ loop: true })
    soundBlockedHint.value = !ok
  }

  async function poll() {
    if (!projectScope.value || !userInfo.value.userId || loadInFlight) return
    if (!popupEnabled.value && !soundEnabled.value) return
    loadInFlight = true
    try {
      const scope = projectScope.value
      const [assetRows, gatewayRows, beaconRows, alertPage] = await Promise.all([
        assetsApi.list(scope),
        gatewaysApi.list(scope),
        beaconsApi.list(scope).catch(() => []),
        fetchAlerts(scope, { current: 1, size: 30 })
      ])
      const current: NoticeItem[] = []
      const offlineAssetIds = new Set<string>()
      const offlineGatewayIds = new Set<string>()
      const offlineBeaconIds = new Set<string>()
      for (const asset of assetRows as NamedResource[]) {
        if (asset.status !== 'OFFLINE') continue
        offlineAssetIds.add(asset.id)
        current.push({
          key: `asset:${asset.id}`,
          kind: 'asset',
          title: asset.name,
          detail: t('product.toaster.assetOffline', { code: String(asset.code || '') })
        })
      }
      for (const gw of gatewayRows as NamedResource[]) {
        if (gw.status !== 'OFFLINE') continue
        offlineGatewayIds.add(gw.id)
        current.push({
          key: `gateway:${gw.id}`,
          kind: 'gateway',
          title: gw.name,
          detail: t('product.toaster.gatewayOffline', { code: String(gw.code || '') })
        })
      }
      for (const beacon of beaconRows as NamedResource[]) {
        if (beacon.status === 'OFFLINE') offlineBeaconIds.add(beacon.id)
      }
      for (const alert of alertPage?.records || []) {
        if (String(alert.status || '').toUpperCase() !== 'OPEN') continue
        if (!offlineAlertStillActive(alert, offlineAssetIds, offlineGatewayIds, offlineBeaconIds)) continue
        current.push({
          key: `alert:${alert.id}`,
          kind: 'alert',
          title: localizeAlertTitle(alert.name || alert.code || t('product.toaster.alertFallback'), t),
          detail: localizeAlertMessage(
            alert.fields?.message || alert.fields?.ruleName || t('product.toaster.openAlertFallback'),
            t
          )
        })
      }

      const currentKeys = new Set(current.map((i) => i.key))
      if (!primed.value) {
        knownKeys.value = currentKeys
        primed.value = true
        return
      }

      for (const key of [...knownKeys.value]) {
        if (!currentKeys.has(key)) knownKeys.value.delete(key)
      }

      const fresh = current.filter((item) => !knownKeys.value.has(item.key))
      for (const item of fresh) knownKeys.value.add(item.key)

      if (visible.value) {
        const stillActive = items.value.filter((item) => currentKeys.has(item.key))
        if (fresh.length && popupEnabled.value) {
          const seen = new Set(stillActive.map((item) => item.key))
          await showNotice([...stillActive, ...fresh.filter((item) => !seen.has(item.key))])
        } else if (stillActive.length) {
          items.value = stillActive
        } else {
          dismiss()
        }
      } else if (fresh.length) {
        if (popupEnabled.value) {
          await showNotice(fresh)
        } else if (soundEnabled.value) {
          // 无弹窗时循环到用户任意点击/按键才停
          await triggerSound({ stopOnGesture: true })
        }
      } else if (!current.length) {
        stopAlertSound()
      }
    } catch {
      // silent
    } finally {
      loadInFlight = false
    }
  }

  watch(
    [projectScope, () => userInfo.value.userId],
    () => {
      primed.value = false
      knownKeys.value = new Set()
      dismiss()
      if (timer) clearInterval(timer)
      if (!projectScope.value || !userInfo.value.userId) return
      void poll()
      timer = setInterval(() => void poll(), 10000)
    },
    { immediate: true }
  )

  watch(soundEnabled, (on) => {
    if (!on) stopAlertSound()
  })

  async function loadCustomSoundIfNeeded() {
    const uid = userInfo.value.userId
    if (!uid) {
      setCustomAlertSound(null, null)
      return
    }
    if (!userInfo.value.hasCustomAlertSound) {
      setCustomAlertSound(null, uid)
      return
    }
    try {
      const res = await fetchMyAlertSound()
      setCustomAlertSound(res?.sound || null, uid)
    } catch {
      // keep previous cache
    }
  }

  onMounted(() => {
    prepareAlertAudio()
    void loadCustomSoundIfNeeded()
  })

  watch(
    () => [userInfo.value.userId, userInfo.value.hasCustomAlertSound] as const,
    () => {
      void loadCustomSoundIfNeeded()
    }
  )

  onUnmounted(() => {
    if (timer) clearInterval(timer)
    stopAlertSound()
  })
</script>

<template>
  <Teleport to="body">
    <Transition name="alert-pop">
      <div v-if="visible" class="alert-toast" role="alertdialog" aria-modal="true">
        <div class="alert-toast__backdrop" @click="dismiss" />
        <div class="alert-toast__card" :class="{ pulse: animating }">
          <div class="alert-toast__icon">
            <ArtSvgIcon icon="ri:alarm-warning-line" />
          </div>
          <div class="alert-toast__body">
            <span class="eyebrow">{{ t('product.toaster.eyebrow') }}</span>
            <h3>{{ headline }}</h3>
            <ul>
              <li v-for="item in items" :key="item.key">
                <strong>{{ item.title }}</strong>
                <small>{{ item.detail }}</small>
              </li>
            </ul>
            <button
              v-if="soundEnabled && soundBlockedHint"
              type="button"
              class="sound-unlock"
              @click="enableSoundNow"
            >
              {{ t('product.toaster.enableSound') }}
            </button>
          </div>
          <div class="alert-toast__actions">
            <ElButton @click="dismiss">{{ t('product.toaster.dismiss') }}</ElButton>
            <ElButton type="danger" @click="goHandle">{{ t('product.toaster.goHandle') }}</ElButton>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
  .alert-toast {
    position: fixed;
    inset: 0;
    z-index: 4000;
    display: grid;
    place-items: center;
    padding: 24px;
  }

  .alert-toast__backdrop {
    position: absolute;
    inset: 0;
    background: rgb(15 23 42 / 42%);
    backdrop-filter: blur(2px);
  }

  .alert-toast__card {
    position: relative;
    width: min(440px, 100%);
    border-radius: 20px;
    background: #fff;
    border: 1px solid rgb(248 113 113 / 35%);
    box-shadow: 0 24px 60px rgb(15 23 42 / 22%);
    padding: 22px 22px 18px;
    display: flex;
    flex-direction: column;
    gap: 14px;
    animation: toast-in 0.42s cubic-bezier(0.22, 1, 0.36, 1);
  }

  .alert-toast__card.pulse {
    animation:
      toast-in 0.42s cubic-bezier(0.22, 1, 0.36, 1),
      toast-pulse 1.6s ease-in-out 0.42s 2;
  }

  .alert-toast__icon {
    width: 48px;
    height: 48px;
    border-radius: 14px;
    display: grid;
    place-items: center;
    background: #fef2f2;
    color: #dc2626;
    font-size: 24px;
  }

  .eyebrow {
    display: inline-block;
    font-size: 11px;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: #b91c1c;
  }

  .alert-toast__body h3 {
    margin: 4px 0 10px;
    font-size: 18px;
    font-weight: 700;
  }

  .alert-toast__body ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-height: 220px;
    overflow: auto;
  }

  .alert-toast__body li strong {
    display: block;
    font-size: 13px;
  }

  .alert-toast__body li small {
    color: #64748b;
    font-size: 12px;
  }

  .alert-toast__actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  .sound-unlock {
    margin-top: 10px;
    appearance: none;
    border: 1px dashed #fca5a5;
    background: #fef2f2;
    color: #b91c1c;
    border-radius: 10px;
    padding: 8px 10px;
    font-size: 12px;
    cursor: pointer;
  }

  .sound-unlock:hover {
    background: #fee2e2;
  }

  .alert-pop-enter-active,
  .alert-pop-leave-active {
    transition: opacity 0.2s ease;
  }

  .alert-pop-enter-from,
  .alert-pop-leave-to {
    opacity: 0;
  }

  @keyframes toast-in {
    from {
      opacity: 0;
      transform: translateY(18px) scale(0.94);
    }
    to {
      opacity: 1;
      transform: translateY(0) scale(1);
    }
  }

  @keyframes toast-pulse {
    0%,
    100% {
      box-shadow: 0 24px 60px rgb(15 23 42 / 22%);
    }
    50% {
      box-shadow: 0 24px 60px rgb(220 38 38 / 28%);
    }
  }
</style>
