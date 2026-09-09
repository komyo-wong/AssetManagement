<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import {
    fetchPlatformBrandingSettings,
    updatePlatformBrandingSettings
  } from '@/api/asset-platform'
  import { useBrandingStore } from '@/store/modules/branding'

  defineOptions({ name: 'PlatformBrandingSettings' })

  const { t } = useI18n()
  const brandingStore = useBrandingStore()
  const loading = ref(false)
  const saving = ref(false)
  const bgBusy = ref(false)
  const logoBusy = ref(false)

  const form = reactive({
    systemName: '',
    loginTitle: '',
    loginSubtitle: '',
    loginWelcomeTitle: '',
    loginWelcomeSubtitle: '',
    loginBackgroundData: '' as string | null,
    titleLogoData: '' as string | null,
    copyrightText: '',
    canEditCopyright: false
  })

  const bgPreview = computed(() => form.loginBackgroundData || '')
  const logoPreview = computed(() => form.titleLogoData || '')

  async function load() {
    loading.value = true
    try {
      const data = await fetchPlatformBrandingSettings()
      form.systemName = String(data.systemName || '')
      form.loginTitle = String(data.loginTitle || '')
      form.loginSubtitle = String(data.loginSubtitle || '')
      form.loginWelcomeTitle = String(data.loginWelcomeTitle || '')
      form.loginWelcomeSubtitle = String(data.loginWelcomeSubtitle || '')
      form.loginBackgroundData = (data.loginBackgroundData as string) || null
      form.titleLogoData = (data.titleLogoData as string) || null
      form.copyrightText = String(data.copyrightText || '')
      form.canEditCopyright = !!data.canEditCopyright
    } finally {
      loading.value = false
    }
  }

  async function save() {
    saving.value = true
    try {
      const data = await updatePlatformBrandingSettings({
        systemName: form.systemName.trim() || null,
        loginTitle: form.loginTitle.trim() || null,
        loginSubtitle: form.loginSubtitle.trim() || null,
        loginWelcomeTitle: form.loginWelcomeTitle.trim() || null,
        loginWelcomeSubtitle: form.loginWelcomeSubtitle.trim() || null,
        loginBackgroundData: form.loginBackgroundData,
        titleLogoData: form.titleLogoData,
        ...(form.canEditCopyright ? { copyrightText: form.copyrightText.trim() || null } : {})
      })
      brandingStore.patchLocal(data)
      ElMessage.success(t('platformAdmin.branding.saved'))
      await load()
    } finally {
      saving.value = false
    }
  }

  function readAsDataUrl(file: File) {
    return new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result || ''))
      reader.onerror = () => reject(new Error('read failed'))
      reader.readAsDataURL(file)
    })
  }

  async function onBackgroundPick(file: File) {
    if (!file.type.startsWith('image/')) {
      ElMessage.warning(t('platformAdmin.branding.pickImage'))
      return false
    }
    if (file.size > 500 * 1024) {
      ElMessage.warning(t('platformAdmin.branding.bgTooLarge'))
      return false
    }
    bgBusy.value = true
    try {
      form.loginBackgroundData = await readAsDataUrl(file)
      ElMessage.success(t('platformAdmin.branding.bgPicked'))
    } finally {
      bgBusy.value = false
    }
    return false
  }

  async function onLogoPick(file: File) {
    if (!file.type.startsWith('image/')) {
      ElMessage.warning(t('platformAdmin.branding.pickImage'))
      return false
    }
    if (file.size > 50 * 1024) {
      ElMessage.warning(t('platformAdmin.branding.logoTooLarge'))
      return false
    }
    logoBusy.value = true
    try {
      form.titleLogoData = await readAsDataUrl(file)
      ElMessage.success(t('platformAdmin.branding.logoPicked'))
    } finally {
      logoBusy.value = false
    }
    return false
  }

  async function restoreDefaultBackground() {
    bgBusy.value = true
    try {
      await updatePlatformBrandingSettings({ loginBackgroundData: null })
      form.loginBackgroundData = null
      brandingStore.patchLocal({
        loginBackgroundData: null,
        hasLoginBackground: false
      })
      ElMessage.success(t('platformAdmin.branding.bgRestored'))
    } finally {
      bgBusy.value = false
    }
  }

  async function restoreDefaultLogo() {
    logoBusy.value = true
    try {
      await updatePlatformBrandingSettings({ titleLogoData: null })
      form.titleLogoData = null
      brandingStore.patchLocal({
        titleLogoData: null,
        hasTitleLogo: false
      })
      ElMessage.success(t('platformAdmin.branding.logoRestored'))
    } finally {
      logoBusy.value = false
    }
  }

  onMounted(() => {
    void load()
  })
</script>

<template>
  <div class="branding" v-loading="loading">
    <header class="branding__head">
      <div>
        <h1>{{ t('platformAdmin.branding.title') }}</h1>
        <p>{{ t('platformAdmin.branding.subtitle') }}</p>
      </div>
      <ElButton type="primary" :loading="saving" @click="save">
        {{ t('platformAdmin.branding.save') }}
      </ElButton>
    </header>

    <section class="grid">
      <article class="card">
        <h3>{{ t('platformAdmin.branding.text') }}</h3>
        <ElForm label-position="top">
          <ElFormItem :label="t('platformAdmin.branding.systemName')">
            <ElInput
              v-model="form.systemName"
              maxlength="120"
              show-word-limit
              :placeholder="t('platformAdmin.branding.systemNamePh')"
            />
          </ElFormItem>
          <ElFormItem :label="t('platformAdmin.branding.loginTitle')">
            <ElInput
              v-model="form.loginTitle"
              maxlength="200"
              show-word-limit
              :placeholder="t('platformAdmin.branding.loginTitlePh')"
            />
          </ElFormItem>
          <ElFormItem :label="t('platformAdmin.branding.loginSubtitle')">
            <ElInput
              v-model="form.loginSubtitle"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              :placeholder="t('platformAdmin.branding.loginSubtitlePh')"
            />
          </ElFormItem>
          <ElFormItem :label="t('platformAdmin.branding.welcomeTitle')">
            <ElInput
              v-model="form.loginWelcomeTitle"
              maxlength="200"
              show-word-limit
              :placeholder="t('platformAdmin.branding.welcomeTitlePh')"
            />
          </ElFormItem>
          <ElFormItem :label="t('platformAdmin.branding.welcomeSubtitle')">
            <ElInput
              v-model="form.loginWelcomeSubtitle"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              :placeholder="t('platformAdmin.branding.welcomeSubtitlePh')"
            />
          </ElFormItem>
          <ElFormItem :label="t('platformAdmin.branding.copyright')">
            <ElInput
              v-model="form.copyrightText"
              maxlength="300"
              show-word-limit
              :disabled="!form.canEditCopyright"
              :placeholder="t('platformAdmin.branding.copyrightPh')"
            />
            <p v-if="!form.canEditCopyright" class="hint">{{ t('platformAdmin.branding.copyrightLocked') }}</p>
          </ElFormItem>
        </ElForm>
      </article>

      <article class="card">
        <h3>{{ t('platformAdmin.branding.background') }}</h3>
        <p class="hint">{{ t('platformAdmin.branding.backgroundHint') }}</p>
        <div v-if="bgPreview" class="preview bg" :style="{ backgroundImage: `url(${bgPreview})` }" />
        <div v-else class="preview empty">{{ t('platformAdmin.branding.backgroundEmpty') }}</div>
        <div class="actions">
          <ElUpload
            :show-file-list="false"
            accept="image/png,image/jpeg,image/webp,image/gif"
            :before-upload="onBackgroundPick"
            :disabled="bgBusy"
          >
            <ElButton :loading="bgBusy">{{ t('platformAdmin.branding.uploadBackground') }}</ElButton>
          </ElUpload>
          <ElButton
            v-if="bgPreview"
            :loading="bgBusy"
            :disabled="saving"
            @click="restoreDefaultBackground"
          >
            {{ t('platformAdmin.branding.restoreDefault') }}
          </ElButton>
        </div>
      </article>

      <article class="card">
        <h3>{{ t('platformAdmin.branding.logo') }}</h3>
        <p class="hint">{{ t('platformAdmin.branding.logoHint') }}</p>
        <div class="logo-row">
          <img v-if="logoPreview" class="logo-preview" :src="logoPreview" alt="logo" />
          <div v-else class="preview empty compact">{{ t('platformAdmin.branding.logoEmpty') }}</div>
        </div>
        <div class="actions">
          <ElUpload
            :show-file-list="false"
            accept="image/png,image/jpeg,image/webp,image/gif,image/x-icon,image/svg+xml"
            :before-upload="onLogoPick"
            :disabled="logoBusy"
          >
            <ElButton :loading="logoBusy">{{ t('platformAdmin.branding.uploadLogo') }}</ElButton>
          </ElUpload>
          <ElButton
            v-if="logoPreview"
            :loading="logoBusy"
            :disabled="saving"
            @click="restoreDefaultLogo"
          >
            {{ t('platformAdmin.branding.restoreDefault') }}
          </ElButton>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
  .branding {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .branding__head {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: flex-start;
  }

  .branding__head h1 {
    margin: 0;
    font-size: 22px;
    font-weight: 650;
  }

  .branding__head p {
    margin: 6px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  .grid {
    display: grid;
    grid-template-columns: 1.2fr 1fr;
    gap: 12px;
  }

  .card {
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 16px;
    padding: 18px;
  }

  .card:last-child {
    grid-column: 2;
  }

  .card h3 {
    margin: 0 0 12px;
    font-size: 16px;
    font-weight: 650;
  }

  .hint {
    margin: 0 0 12px;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  .preview {
    width: 100%;
    min-height: 160px;
    border-radius: 12px;
    border: 1px solid var(--el-border-color-extra-light);
    background: #f8fafc center / cover no-repeat;
    margin-bottom: 12px;
  }

  .preview.empty {
    display: grid;
    place-items: center;
    color: var(--el-text-color-secondary);
    background: #f8fafc;
  }

  .preview.empty.compact {
    min-height: 72px;
    width: 72px;
  }

  .logo-row {
    margin-bottom: 12px;
  }

  .logo-preview {
    width: 72px;
    height: 72px;
    object-fit: contain;
    border-radius: 12px;
    border: 1px solid var(--el-border-color-extra-light);
    background: #fff;
  }

  .actions {
    display: flex;
    gap: 8px;
  }

  @media (max-width: 960px) {
    .grid {
      grid-template-columns: 1fr;
    }

    .card:last-child {
      grid-column: auto;
    }
  }
</style>
