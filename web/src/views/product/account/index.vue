<script setup lang="ts">
  import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import { storeToRefs } from 'pinia'
  import {
    changeMyPassword,
    fetchGetUserInfo,
    fetchMyAlertSound,
    updateMyAlertSound,
    updateMyAvatar,
    updateMyProfile,
    updateMyUiPreferences
  } from '@/api/auth'
  import { useUserStore } from '@/store/modules/user'
  import { playAlertSound, setCustomAlertSound } from '@/utils/alert-sound'
  import defaultAvatar from '@imgs/user/avatar.webp'

  defineOptions({ name: 'UserCenter' })

  const { t } = useI18n()

  const userStore = useUserStore()
  const { getUserInfo: userInfo } = storeToRefs(userStore)

  const savingProfile = ref(false)
  const savingPwd = ref(false)
  const savingPrefs = ref(false)
  const avatarBusy = ref(false)
  const soundBusy = ref(false)

  const displayName = ref('')
  const email = ref('')
  const alertPopupEnabled = ref(true)
  const alertSoundEnabled = ref(true)
  const soundFileName = ref('')

  const pwdForm = ref({
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  })

  const avatarSrc = computed(() => userInfo.value.avatar || defaultAvatar)
  const titleName = computed(
    () => userInfo.value.displayName || userInfo.value.userName || t('product.account.userFallback')
  )
  const hasCustomSound = computed(
    () => !!userInfo.value.hasCustomAlertSound || !!soundFileName.value
  )

  function syncFromStore() {
    displayName.value = String(userInfo.value.displayName || userInfo.value.userName || '')
    email.value = String(userInfo.value.email || '')
    alertPopupEnabled.value = userInfo.value.alertPopupEnabled !== false
    alertSoundEnabled.value = userInfo.value.alertSoundEnabled !== false
    soundFileName.value = String(userInfo.value.alertSoundFileName || '')
  }

  watch(userInfo, syncFromStore, { immediate: true, deep: true })

  async function refreshMe() {
    const data = await fetchGetUserInfo()
    userStore.setUserInfo(data)
  }

  async function ensureCustomSoundLoaded() {
    if (!userInfo.value.hasCustomAlertSound) {
      setCustomAlertSound(null, userInfo.value.userId)
      return
    }
    try {
      const res = await fetchMyAlertSound()
      if (res?.sound) {
        setCustomAlertSound(res.sound, userInfo.value.userId)
        soundFileName.value = String(res.fileName || soundFileName.value || t('product.account.customSoundName'))
      }
    } catch {
      // ignore
    }
  }

  void ensureCustomSoundLoaded()
  watch(
    () => userInfo.value.hasCustomAlertSound,
    () => {
      void ensureCustomSoundLoaded()
    }
  )

  async function saveProfile() {
    const name = displayName.value.trim()
    const mail = email.value.trim()
    if (!name) {
      ElMessage.warning(t('product.account.needDisplayName'))
      return
    }
    if (!mail) {
      ElMessage.warning(t('product.account.needEmail'))
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mail)) {
      ElMessage.warning(t('product.account.emailInvalid'))
      return
    }
    savingProfile.value = true
    try {
      await updateMyProfile({ displayName: name, email: mail })
      await refreshMe()
      ElMessage.success(t('product.account.profileUpdated'))
    } finally {
      savingProfile.value = false
    }
  }

  async function savePassword() {
    if (!pwdForm.value.currentPassword || !pwdForm.value.newPassword) {
      ElMessage.warning(t('product.account.needFullPassword'))
      return
    }
    if (pwdForm.value.newPassword.length < 8) {
      ElMessage.warning(t('product.account.passwordMin'))
      return
    }
    if (pwdForm.value.newPassword !== pwdForm.value.confirmPassword) {
      ElMessage.warning(t('product.account.passwordMismatch'))
      return
    }
    savingPwd.value = true
    try {
      await changeMyPassword({
        currentPassword: pwdForm.value.currentPassword,
        newPassword: pwdForm.value.newPassword
      })
      pwdForm.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
      ElMessage.success(t('product.account.passwordUpdated'))
    } finally {
      savingPwd.value = false
    }
  }

  async function savePrefs() {
    savingPrefs.value = true
    try {
      await updateMyUiPreferences({
        alertPopupEnabled: alertPopupEnabled.value,
        alertSoundEnabled: alertSoundEnabled.value
      })
      await refreshMe()
      ElMessage.success(t('product.account.prefsSaved'))
    } finally {
      savingPrefs.value = false
    }
  }

  async function testSound() {
    await ensureCustomSoundLoaded()
    const ok = await playAlertSound()
    if (ok) ElMessage.success(t('product.account.testPlayed'))
    else ElMessage.warning(t('product.account.soundBlocked'))
  }

  function isAllowedSoundFile(file: File) {
    const name = file.name.toLowerCase()
    const type = (file.type || '').toLowerCase()
    if (name.endsWith('.mp3') || name.endsWith('.wma')) return true
    return (
      type.includes('mpeg') ||
      type.includes('mp3') ||
      type.includes('wma') ||
      type === 'audio/x-ms-wma'
    )
  }

  async function onSoundPick(file: File) {
    if (!isAllowedSoundFile(file)) {
      ElMessage.warning(t('product.account.soundFormat'))
      return false
    }
    if (file.size > 300 * 1024) {
      ElMessage.warning(t('product.account.soundTooLarge'))
      return false
    }
    soundBusy.value = true
    try {
      const dataUrl = await readAsDataUrl(file)
      const res = await updateMyAlertSound({ sound: dataUrl, fileName: file.name })
      setCustomAlertSound(res.sound || dataUrl, userInfo.value.userId)
      await refreshMe()
      soundFileName.value = String(res.fileName || file.name)
      ElMessage.success(t('product.account.soundSaved'))
      await playAlertSound()
    } finally {
      soundBusy.value = false
    }
    return false
  }

  async function clearCustomSound() {
    soundBusy.value = true
    try {
      await updateMyAlertSound({ sound: null })
      setCustomAlertSound(null, userInfo.value.userId)
      await refreshMe()
      soundFileName.value = ''
      ElMessage.success(t('product.account.soundRestored'))
    } finally {
      soundBusy.value = false
    }
  }

  async function onAvatarPick(file: File) {
    if (!file.type.startsWith('image/')) {
      ElMessage.warning(t('product.account.needImage'))
      return false
    }
    if (file.size > 240 * 1024) {
      ElMessage.warning(t('product.account.avatarTooLarge'))
      return false
    }
    avatarBusy.value = true
    try {
      const dataUrl = await readAsDataUrl(file)
      await updateMyAvatar({ avatar: dataUrl })
      await refreshMe()
      ElMessage.success(t('product.account.avatarUpdated'))
    } finally {
      avatarBusy.value = false
    }
    return false
  }

  async function clearAvatar() {
    avatarBusy.value = true
    try {
      await updateMyAvatar({ avatar: null })
      await refreshMe()
      ElMessage.success(t('product.account.avatarRestored'))
    } finally {
      avatarBusy.value = false
    }
  }

  function readAsDataUrl(file: File) {
    return new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result || ''))
      reader.onerror = () => reject(new Error(t('product.account.readFailed')))
      reader.readAsDataURL(file)
    })
  }
</script>

<template>
  <div class="account" v-loading="avatarBusy">
    <header class="account__head">
      <div>
        <h1>{{ t('product.account.title') }}</h1>
        <p>{{ t('product.account.subtitle') }}</p>
      </div>
    </header>

    <section class="account__grid">
      <article class="card profile-card">
        <div class="avatar-block">
          <img class="avatar" :src="avatarSrc" alt="avatar" />
          <div>
            <h2>{{ titleName }}</h2>
            <p>{{ userInfo.email || '—' }}</p>
            <p class="muted">{{ t('product.account.accountLabel', { name: userInfo.userName || '—' }) }}</p>
            <div class="avatar-actions">
              <ElUpload
                :show-file-list="false"
                accept="image/png,image/jpeg,image/webp,image/gif"
                :before-upload="onAvatarPick"
              >
                <ElButton type="primary">{{ t('product.account.changeAvatar') }}</ElButton>
              </ElUpload>
              <ElButton v-if="userInfo.avatar" @click="clearAvatar">{{ t('product.account.restoreDefault') }}</ElButton>
            </div>
          </div>
        </div>
      </article>

      <article class="card">
        <h3>{{ t('product.account.basicProfile') }}</h3>
        <ElForm label-position="top" @submit.prevent>
          <ElFormItem :label="t('product.account.displayName')">
            <ElInput v-model="displayName" maxlength="120" show-word-limit :placeholder="t('product.account.displayNamePh')" />
          </ElFormItem>
          <ElFormItem :label="t('product.account.email')">
            <ElInput v-model="email" maxlength="254" :placeholder="t('product.account.emailPh')" />
          </ElFormItem>
          <ElButton type="primary" :loading="savingProfile" @click="saveProfile">{{ t('product.account.saveProfile') }}</ElButton>
        </ElForm>
      </article>

      <article class="card">
        <h3>{{ t('product.account.changePassword') }}</h3>
        <ElForm label-position="top" autocomplete="off" @submit.prevent>
          <input
            type="text"
            class="autofill-trap"
            tabindex="-1"
            aria-hidden="true"
            autocomplete="username"
          />
          <input
            type="password"
            class="autofill-trap"
            tabindex="-1"
            aria-hidden="true"
            autocomplete="current-password"
          />
          <ElFormItem :label="t('product.account.currentPassword')">
            <ElInput
              v-model="pwdForm.currentPassword"
              type="password"
              show-password
              name="account-current-password"
              autocomplete="off"
            />
          </ElFormItem>
          <ElFormItem :label="t('product.account.newPassword')">
            <ElInput
              v-model="pwdForm.newPassword"
              type="password"
              show-password
              name="account-new-password"
              autocomplete="new-password"
            />
          </ElFormItem>
          <ElFormItem :label="t('product.account.confirmPassword')">
            <ElInput
              v-model="pwdForm.confirmPassword"
              type="password"
              show-password
              name="account-confirm-password"
              autocomplete="new-password"
            />
          </ElFormItem>
          <ElButton type="primary" :loading="savingPwd" @click="savePassword">{{ t('product.account.updatePassword') }}</ElButton>
        </ElForm>
      </article>

      <article class="card">
        <h3>{{ t('product.account.alertPrefs') }}</h3>
        <p class="hint">{{ t('product.account.alertPrefsHint') }}</p>
        <div class="pref-row">
          <div>
            <strong>{{ t('product.account.popupTitle') }}</strong>
            <p>{{ t('product.account.popupDesc') }}</p>
          </div>
          <ElSwitch v-model="alertPopupEnabled" />
        </div>
        <div class="pref-row">
          <div>
            <strong>{{ t('product.account.soundTitle') }}</strong>
            <p>{{ t('product.account.soundDesc') }}</p>
          </div>
          <ElSwitch v-model="alertSoundEnabled" />
        </div>
        <div class="pref-row sound-upload">
          <div>
            <strong>{{ t('product.account.customSound') }}</strong>
            <p>
              {{ t('product.account.customSoundHint') }}
              <template v-if="hasCustomSound">{{ t('product.account.currentUploaded', { name: soundFileName || t('product.account.uploaded') }) }}</template>
              <template v-else>{{ t('product.account.currentDefault') }}</template>
            </p>
            <p class="muted">{{ t('product.account.browserHint') }}</p>
          </div>
          <div class="sound-actions">
            <ElUpload
              :show-file-list="false"
              accept=".mp3,.wma,audio/mpeg,audio/mp3,audio/x-ms-wma,audio/wma"
              :before-upload="onSoundPick"
              :disabled="soundBusy"
            >
              <ElButton :loading="soundBusy">{{ t('product.account.uploadSound') }}</ElButton>
            </ElUpload>
            <ElButton v-if="hasCustomSound" :disabled="soundBusy" @click="clearCustomSound">{{ t('product.account.restoreDefault') }}</ElButton>
          </div>
        </div>
        <div class="pref-actions">
          <ElButton @click="testSound">{{ t('product.account.testSound') }}</ElButton>
          <ElButton type="primary" :loading="savingPrefs" @click="savePrefs">{{ t('product.account.savePrefs') }}</ElButton>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
  .account {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .account__head h1 {
    margin: 0;
    font-size: 24px;
    font-weight: 650;
  }

  .account__head p {
    margin: 6px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  .account__grid {
    display: grid;
    grid-template-columns: 1.1fr 1fr;
    gap: 12px;
  }

  .card {
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 16px;
    padding: 18px;
  }

  .card h3 {
    margin: 0 0 14px;
    font-size: 16px;
    font-weight: 650;
  }

  .profile-card {
    grid-row: span 1;
  }

  .avatar-block {
    display: flex;
    gap: 16px;
    align-items: center;
  }

  .avatar {
    width: 88px;
    height: 88px;
    border-radius: 50%;
    object-fit: cover;
    border: 3px solid #fff;
    box-shadow: 0 8px 24px rgb(15 23 42 / 10%);
  }

  .avatar-block h2 {
    margin: 0;
    font-size: 20px;
  }

  .avatar-block p {
    margin: 4px 0 0;
    font-size: 13px;
  }

  .muted {
    color: var(--el-text-color-secondary);
  }

  .avatar-actions {
    display: flex;
    gap: 8px;
    margin-top: 12px;
  }

  .hint {
    margin: -4px 0 14px;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  .pref-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 12px 0;
    border-top: 1px solid var(--el-border-color-extra-light);
  }

  .pref-row:first-of-type {
    border-top: 0;
    padding-top: 0;
  }

  .pref-row strong {
    display: block;
    font-size: 14px;
  }

  .pref-row p {
    margin: 4px 0 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .pref-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 12px;
  }

  .sound-upload {
    align-items: flex-start;
  }

  .sound-actions {
    display: flex;
    flex-direction: column;
    gap: 8px;
    align-items: stretch;
    flex-shrink: 0;
  }

  .autofill-trap {
    position: absolute;
    left: -9999px;
    width: 1px;
    height: 1px;
    opacity: 0;
    pointer-events: none;
  }

  @media (max-width: 960px) {
    .account__grid {
      grid-template-columns: 1fr;
    }
  }
</style>
