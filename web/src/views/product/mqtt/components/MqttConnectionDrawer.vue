<template>
  <ElDrawer
    :model-value="modelValue"
    :title="connection ? t('mqtt.drawer.editTitle') : t('mqtt.drawer.title')"
    size="min(560px, 100%)"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="clearSensitiveDraft"
  >
    <div class="drawer-intro">
      <ArtSvgIcon icon="ri:shield-keyhole-line" />
      <p>{{ t('mqtt.drawer.secretNotice') }}</p>
    </div>

    <ElAlert
      v-if="connection?.credentialsConfigured"
      class="credential-alert"
      type="success"
      :closable="false"
      show-icon
      :title="t('mqtt.drawer.credentialsConfigured')"
    />

    <ElForm
      ref="formRef"
      label-position="top"
      :model="draft"
      :rules="rules"
      class="connection-form"
    >
      <div class="form-grid">
        <ElFormItem :label="t('mqtt.fields.name')" prop="name">
          <ElInput v-model.trim="draft.name" :placeholder="t('mqtt.placeholders.name')" />
        </ElFormItem>
        <ElFormItem :label="t('mqtt.fields.role')" prop="role">
          <ElSelect v-model="draft.role">
            <ElOption value="primary" :label="t('mqtt.roles.primary')" />
            <ElOption value="standby" :label="t('mqtt.roles.standby')" />
          </ElSelect>
        </ElFormItem>
      </div>

      <div class="form-grid">
        <ElFormItem :label="t('mqtt.fields.environment')" prop="environment">
          <ElSelect v-model="draft.environment">
            <ElOption value="development" :label="t('mqtt.environments.development')" />
            <ElOption value="test" :label="t('mqtt.environments.test')" />
            <ElOption value="staging" :label="t('mqtt.environments.staging')" />
            <ElOption value="production" :label="t('mqtt.environments.production')" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('mqtt.fields.version')" prop="mqttVersion">
          <ElSelect v-model="draft.mqttVersion">
            <ElOption value="5.0" label="MQTT 5.0" />
            <ElOption value="3.1.1" label="MQTT 3.1.1" />
          </ElSelect>
        </ElFormItem>
      </div>

      <ElFormItem :label="t('mqtt.fields.endpoint')" prop="brokerUri">
        <ElInput v-model.trim="draft.brokerUri" placeholder="mqtts://broker.example.com:8883" />
      </ElFormItem>

      <div class="form-grid">
        <ElFormItem :label="t('mqtt.fields.clientId')" prop="clientId">
          <ElInput v-model.trim="draft.clientId" :placeholder="t('mqtt.placeholders.clientId')" />
        </ElFormItem>
        <ElFormItem :label="t('mqtt.fields.keepAlive')" prop="keepAliveSeconds">
          <ElInputNumber
            v-model="draft.keepAliveSeconds"
            :min="5"
            :max="65535"
            controls-position="right"
          />
        </ElFormItem>
      </div>

      <div class="form-grid">
        <ElFormItem :label="t('mqtt.fields.username')" prop="username">
          <ElInput
            v-model.trim="draft.username"
            autocomplete="off"
            :placeholder="connection ? t('mqtt.placeholders.leaveUnchanged') : ''"
          />
        </ElFormItem>
        <ElFormItem :label="t('mqtt.fields.password')" prop="password">
          <ElInput
            v-model="draft.password"
            type="password"
            autocomplete="new-password"
            show-password
            :placeholder="connection ? t('mqtt.placeholders.leaveUnchanged') : ''"
          />
        </ElFormItem>
      </div>

      <div class="switch-row">
        <div>
          <strong>TLS</strong>
          <p>{{ t('mqtt.fields.tlsHint') }}</p>
        </div>
        <ElSwitch v-model="draft.tlsEnabled" />
      </div>
      <div class="switch-row">
        <div>
          <strong>Clean Start</strong>
          <p>{{ t('mqtt.fields.cleanStartHint') }}</p>
        </div>
        <ElSwitch v-model="draft.cleanStart" />
      </div>
      <div class="switch-row">
        <div>
          <strong>{{ t('mqtt.fields.enabled') }}</strong>
          <p>{{ t('mqtt.fields.enabledHint') }}</p>
        </div>
        <ElSwitch v-model="draft.enabled" />
      </div>
    </ElForm>

    <template #footer>
      <div class="drawer-footer">
        <ElButton :disabled="saving" @click="emit('update:modelValue', false)">
          {{ t('common.cancel') }}
        </ElButton>
        <ElButton type="primary" :loading="saving" @click="submit">
          {{ connection ? t('mqtt.drawer.update') : t('mqtt.drawer.save') }}
        </ElButton>
      </div>
    </template>
  </ElDrawer>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'
  import type { FormInstance, FormRules } from 'element-plus'

  const props = defineProps<{
    modelValue: boolean
    connection: Api.AssetPlatform.MqttConnection | null
    initialRole: Api.AssetPlatform.MqttConnectionRole
    saving: boolean
  }>()

  const emit = defineEmits<{
    'update:modelValue': [value: boolean]
    submit: [value: Api.AssetPlatform.MqttConnectionInput]
  }>()

  const { t } = useI18n()
  const formRef = ref<FormInstance>()

  const createDraft = (): Api.AssetPlatform.MqttConnectionInput => ({
    name: '',
    environment: 'development',
    role: props.initialRole,
    brokerUri: '',
    mqttVersion: '5.0',
    clientId: '',
    username: '',
    password: '',
    tlsEnabled: true,
    cleanStart: true,
    keepAliveSeconds: 60,
    enabled: false
  })

  const draft = reactive<Api.AssetPlatform.MqttConnectionInput>(createDraft())

  const rules = computed<FormRules>(() => ({
    name: [
      { required: true, message: t('mqtt.validation.nameRequired'), trigger: 'blur' },
      { max: 120, message: t('mqtt.validation.max120'), trigger: 'blur' }
    ],
    role: [{ required: true, message: t('mqtt.validation.roleRequired'), trigger: 'change' }],
    environment: [
      { required: true, message: t('mqtt.validation.environmentRequired'), trigger: 'change' }
    ],
    mqttVersion: [
      { required: true, message: t('mqtt.validation.versionRequired'), trigger: 'change' }
    ],
    brokerUri: [
      { required: true, message: t('mqtt.validation.endpointRequired'), trigger: 'blur' },
      {
        pattern: /^(mqtt|mqtts|ws|wss):\/\/[^\s]+$/i,
        message: t('mqtt.validation.endpointInvalid'),
        trigger: 'blur'
      }
    ],
    clientId: [
      { required: true, message: t('mqtt.validation.clientIdRequired'), trigger: 'blur' },
      { max: 180, message: t('mqtt.validation.max180'), trigger: 'blur' }
    ],
    username: [{ max: 512, message: t('mqtt.validation.max512'), trigger: 'blur' }],
    password: [{ max: 4096, message: t('mqtt.validation.secretTooLong'), trigger: 'blur' }]
  }))

  watch(
    () => props.modelValue,
    (visible) => {
      if (!visible) return
      const connection = props.connection
      Object.assign(
        draft,
        connection
          ? {
              name: connection.name,
              environment: connection.environment,
              role: connection.role,
              brokerUri: connection.brokerUri,
              mqttVersion: connection.mqttVersion,
              clientId: connection.clientId,
              username: '',
              password: '',
              tlsEnabled: connection.tlsEnabled,
              cleanStart: connection.cleanStart,
              keepAliveSeconds: connection.keepAliveSeconds,
              enabled: connection.enabled
            }
          : createDraft()
      )
      nextTick(() => formRef.value?.clearValidate())
    },
    { immediate: true }
  )

  async function submit(): Promise<void> {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return

    const payload: Api.AssetPlatform.MqttConnectionInput = { ...draft }
    if (!payload.username?.trim()) delete payload.username
    if (!payload.password) delete payload.password
    emit('submit', payload)
  }

  function clearSensitiveDraft(): void {
    draft.username = ''
    draft.password = ''
    formRef.value?.clearValidate()
  }
</script>

<style lang="scss" scoped>
  .drawer-intro {
    display: flex;
    gap: 10px;
    padding: 13px 14px;
    margin-bottom: 18px;
    color: #2c6f55;
    background: rgb(34 160 107 / 8%);
    border: 1px solid rgb(34 160 107 / 18%);
    border-radius: 12px;

    .art-svg-icon {
      flex: 0 0 auto;
      margin-top: 1px;
      font-size: 17px;
    }

    p {
      font-size: 11px;
      line-height: 1.6;
    }
  }

  .credential-alert {
    margin-bottom: 16px;
  }

  .form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .connection-form :deep(.el-select),
  .connection-form :deep(.el-input-number) {
    width: 100%;
  }

  .switch-row {
    display: flex;
    gap: 18px;
    align-items: center;
    justify-content: space-between;
    padding: 14px 0;
    border-top: 1px solid var(--art-card-border);

    strong {
      font-size: 12px;
      color: var(--art-gray-800);
    }

    p {
      margin-top: 3px;
      font-size: 10px;
      color: var(--art-gray-500);
    }
  }

  .drawer-footer {
    display: flex;
    gap: 8px;
    justify-content: flex-end;
  }

  @media (width <= 600px) {
    .form-grid {
      grid-template-columns: 1fr;
      gap: 0;
    }
  }
</style>
