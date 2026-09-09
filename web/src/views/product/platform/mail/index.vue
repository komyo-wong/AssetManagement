<script setup lang="ts">
  import { onMounted, reactive, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import {
    fetchPlatformMailSettings,
    testPlatformMail,
    updatePlatformMailSettings
  } from '@/api/asset-platform'

  defineOptions({ name: 'PlatformMailSettings' })

  const { t } = useI18n()
  const loading = ref(false)
  const saving = ref(false)
  const testing = ref(false)
  const passwordConfigured = ref(false)
  const ready = ref(false)
  const testTo = ref('')

  const form = reactive({
    enabled: false,
    host: '',
    port: 587,
    username: '',
    password: '',
    fromAddress: '',
    fromName: '',
    useSsl: false,
    useStarttls: true
  })

  async function load() {
    loading.value = true
    try {
      const data = await fetchPlatformMailSettings()
      form.enabled = !!data.enabled
      form.host = String(data.host || '')
      form.port = Number(data.port || 587)
      form.username = String(data.username || '')
      form.password = ''
      form.fromAddress = String(data.fromAddress || '')
      form.fromName = String(data.fromName || '')
      form.useSsl = !!data.useSsl
      form.useStarttls = data.useStarttls !== false
      passwordConfigured.value = !!data.passwordConfigured
      ready.value = !!data.ready
      if (!testTo.value && form.fromAddress) {
        testTo.value = form.fromAddress
      }
    } finally {
      loading.value = false
    }
  }

  async function save() {
    saving.value = true
    try {
      const payload: Record<string, unknown> = {
        enabled: form.enabled,
        host: form.host.trim() || null,
        port: form.port,
        username: form.username.trim() || null,
        fromAddress: form.fromAddress.trim() || null,
        fromName: form.fromName.trim() || null,
        useSsl: form.useSsl,
        useStarttls: form.useStarttls
      }
      if (form.password.trim()) {
        payload.password = form.password
      }
      const data = await updatePlatformMailSettings(payload)
      passwordConfigured.value = !!data.passwordConfigured
      ready.value = !!data.ready
      form.password = ''
      ElMessage.success(t('platformAdmin.mail.saved'))
      await load()
    } finally {
      saving.value = false
    }
  }

  async function sendTest() {
    if (!testTo.value.trim()) {
      ElMessage.warning(t('platformAdmin.mail.testNeedTo'))
      return
    }
    testing.value = true
    try {
      await testPlatformMail({ to: testTo.value.trim() })
      ElMessage.success(t('platformAdmin.mail.testSent'))
    } finally {
      testing.value = false
    }
  }

  onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page-card art-card mail-page">
    <h2>{{ t('platformAdmin.mail.title') }}</h2>
    <p class="subtitle">
      {{ t('platformAdmin.mail.subtitle') }}
      <span :class="ready ? 'ok' : 'warn'">
        {{ ready ? t('platformAdmin.mail.ready') : t('platformAdmin.mail.notReady') }}
      </span>
    </p>

    <ElForm label-width="140px">
      <ElFormItem :label="t('platformAdmin.mail.enabled')">
        <ElSwitch v-model="form.enabled" />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.mail.host')">
        <ElInput v-model="form.host" placeholder="smtp.example.com" />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.mail.port')">
        <ElInputNumber v-model="form.port" :min="1" :max="65535" />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.mail.username')">
        <ElInput v-model="form.username" autocomplete="off" />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.mail.password')">
        <ElInput
          v-model="form.password"
          type="password"
          show-password
          autocomplete="new-password"
          :placeholder="
            passwordConfigured
              ? t('platformAdmin.mail.passwordHintConfigured')
              : t('platformAdmin.mail.passwordPh')
          "
        />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.mail.fromAddress')">
        <ElInput v-model="form.fromAddress" :placeholder="t('platformAdmin.mail.fromAddressPh')" />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.mail.fromName')">
        <ElInput v-model="form.fromName" :placeholder="t('platformAdmin.mail.fromNamePh')" />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.mail.useSsl')">
        <ElSwitch v-model="form.useSsl" />
        <span class="field-hint">{{ t('platformAdmin.mail.sslHint') }}</span>
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.mail.useStarttls')">
        <ElSwitch v-model="form.useStarttls" />
        <span class="field-hint">{{ t('platformAdmin.mail.starttlsHint') }}</span>
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :loading="saving" @click="save">
          {{ t('platformAdmin.mail.save') }}
        </ElButton>
      </ElFormItem>
    </ElForm>

    <ElDivider />
    <h3 class="section-title">{{ t('platformAdmin.mail.testSection') }}</h3>
    <div class="test-row">
      <ElInput
        v-model="testTo"
        :placeholder="t('platformAdmin.mail.testToPh')"
        style="max-width: 320px"
      />
      <ElButton type="success" :loading="testing" @click="sendTest">
        {{ t('platformAdmin.mail.sendTestMail') }}
      </ElButton>
    </div>
  </div>
</template>

<style scoped>
  .mail-page {
    padding: 20px;
    max-width: 720px;
  }

  .subtitle {
    color: var(--el-text-color-secondary);
    margin: 4px 0 16px;
  }

  .ok {
    color: var(--el-color-success);
    margin-left: 8px;
  }

  .warn {
    color: var(--el-color-warning);
    margin-left: 8px;
  }

  .field-hint {
    margin-left: 12px;
    color: var(--el-text-color-secondary);
  }

  .section-title {
    margin-bottom: 12px;
  }

  .test-row {
    display: flex;
    gap: 12px;
    align-items: center;
  }
</style>
