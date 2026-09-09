<template>
  <ElDrawer
    :model-value="modelValue"
    :title="topicRoute ? t('mqtt.routeDrawer.editTitle') : t('mqtt.routeDrawer.title')"
    size="min(560px, 100%)"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <ElAlert
      class="protocol-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="t('mqtt.routeDrawer.protocolNotice')"
    />

    <ElForm ref="formRef" label-position="top" :model="draft" :rules="rules">
      <ElFormItem :label="t('mqtt.routeFields.connection')" prop="connectionId">
        <ElSelect v-model="draft.connectionId" filterable>
          <ElOption
            v-for="connection in connections"
            :key="connection.id"
            :value="connection.id"
            :label="`${connection.name} · ${t(`mqtt.environments.${connection.environment}`)}`"
          />
        </ElSelect>
      </ElFormItem>

      <div class="form-grid">
        <ElFormItem :label="t('mqtt.routeFields.name')" prop="name">
          <ElInput v-model.trim="draft.name" />
        </ElFormItem>
        <ElFormItem :label="t('mqtt.routeFields.direction')" prop="direction">
          <ElSelect v-model="draft.direction" :placeholder="t('mqtt.routePlaceholders.direction')">
            <ElOption value="uplink" :label="t('mqtt.directions.uplink')" />
            <ElOption value="downlink" :label="t('mqtt.directions.downlink')" />
            <ElOption value="acknowledgement" :label="t('mqtt.directions.acknowledgement')" />
          </ElSelect>
        </ElFormItem>
      </div>

      <ElFormItem :label="t('mqtt.routeFields.topicPattern')" prop="topicPattern">
        <ElInput
          v-model.trim="draft.topicPattern"
          :placeholder="t('mqtt.routePlaceholders.topicPattern')"
        />
        <p class="field-hint">{{ t('mqtt.routeFields.topicHint') }}</p>
      </ElFormItem>

      <div class="form-grid">
        <ElFormItem :label="t('mqtt.routeFields.messageType')" prop="messageType">
          <ElInput
            v-model.trim="draft.messageType"
            :placeholder="t('mqtt.routePlaceholders.messageType')"
          />
        </ElFormItem>
        <ElFormItem :label="t('mqtt.routeFields.parserKey')" prop="parserKey">
          <ElInput
            v-model.trim="draft.parserKey"
            :placeholder="t('mqtt.routePlaceholders.parserKey')"
          />
        </ElFormItem>
      </div>

      <div class="form-grid">
        <ElFormItem label="QoS" prop="qos">
          <ElSelect v-model="draft.qos" :placeholder="t('mqtt.routePlaceholders.qos')">
            <ElOption :value="0" label="0" />
            <ElOption :value="1" label="1" />
            <ElOption :value="2" label="2" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('mqtt.routeFields.retained')">
          <div class="inline-switch">
            <ElSwitch v-model="draft.retained" />
            <span>{{ t('mqtt.routeFields.retainedHint') }}</span>
          </div>
        </ElFormItem>
      </div>

      <div class="route-enabled">
        <div>
          <strong>{{ t('mqtt.routeFields.enabled') }}</strong>
          <p>{{ t('mqtt.routeFields.enabledHint') }}</p>
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
          {{ topicRoute ? t('mqtt.routeDrawer.update') : t('mqtt.routeDrawer.save') }}
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
    topicRoute: Api.AssetPlatform.MqttTopicRoute | null
    connections: Api.AssetPlatform.MqttConnection[]
    saving: boolean
  }>()

  const emit = defineEmits<{
    'update:modelValue': [value: boolean]
    submit: [value: Api.AssetPlatform.MqttTopicRouteInput]
  }>()

  const { t } = useI18n()
  const formRef = ref<FormInstance>()

  type TopicRouteDraft = Omit<Api.AssetPlatform.MqttTopicRouteInput, 'direction' | 'qos'> & {
    direction: Api.AssetPlatform.MqttRouteDirection | ''
    qos: 0 | 1 | 2 | undefined
  }

  const createDraft = (): TopicRouteDraft => ({
    connectionId: props.connections[0]?.id ?? '',
    name: '',
    direction: '',
    topicPattern: '',
    messageType: '',
    parserKey: '',
    qos: undefined,
    retained: false,
    enabled: false
  })

  const draft = reactive<TopicRouteDraft>(createDraft())

  const validateTopicPattern = (
    _rule: unknown,
    value: string,
    callback: (error?: Error) => void
  ) => {
    if (!value?.trim()) {
      callback(new Error(t('mqtt.validation.topicRequired')))
      return
    }
    if (value.includes(String.fromCharCode(0)) || value.length > 500) {
      callback(new Error(t('mqtt.validation.topicInvalid')))
      return
    }
    if (draft.direction === 'downlink' && /[+#]/.test(value)) {
      callback(new Error(t('mqtt.validation.publishWildcard')))
      return
    }
    callback()
  }

  const rules = computed<FormRules>(() => ({
    connectionId: [
      { required: true, message: t('mqtt.validation.connectionRequired'), trigger: 'change' }
    ],
    name: [
      { required: true, message: t('mqtt.validation.routeNameRequired'), trigger: 'blur' },
      { max: 120, message: t('mqtt.validation.max120'), trigger: 'blur' }
    ],
    direction: [
      { required: true, message: t('mqtt.validation.directionRequired'), trigger: 'change' }
    ],
    topicPattern: [{ validator: validateTopicPattern, trigger: 'blur' }],
    messageType: [
      { required: true, message: t('mqtt.validation.messageTypeRequired'), trigger: 'blur' },
      { max: 80, message: t('mqtt.validation.max80'), trigger: 'blur' }
    ],
    parserKey: [
      { required: true, message: t('mqtt.validation.parserKeyRequired'), trigger: 'blur' },
      { max: 120, message: t('mqtt.validation.max120'), trigger: 'blur' }
    ],
    qos: [{ required: true, message: t('mqtt.validation.qosRequired'), trigger: 'change' }]
  }))

  watch(
    () => props.modelValue,
    (visible) => {
      if (!visible) return
      Object.assign(
        draft,
        props.topicRoute
          ? {
              connectionId: props.topicRoute.connectionId,
              name: props.topicRoute.name,
              direction: props.topicRoute.direction,
              topicPattern: props.topicRoute.topicPattern,
              messageType: props.topicRoute.messageType,
              parserKey: props.topicRoute.parserKey,
              qos: props.topicRoute.qos,
              retained: props.topicRoute.retained,
              enabled: props.topicRoute.enabled
            }
          : createDraft()
      )
      nextTick(() => formRef.value?.clearValidate())
    },
    { immediate: true }
  )

  watch(
    () => draft.direction,
    () => formRef.value?.validateField('topicPattern').catch(() => undefined)
  )

  async function submit(): Promise<void> {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid || !draft.direction || draft.qos === undefined) return
    emit('submit', {
      ...draft,
      direction: draft.direction,
      qos: draft.qos
    })
  }
</script>

<style lang="scss" scoped>
  .protocol-alert {
    margin-bottom: 18px;
  }

  .form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  :deep(.el-select) {
    width: 100%;
  }

  .field-hint {
    margin-top: 5px;
    font-size: 10px;
    line-height: 1.5;
    color: var(--art-gray-500);
  }

  .inline-switch {
    display: flex;
    gap: 9px;
    align-items: center;
    min-height: 32px;
    font-size: 11px;
    color: var(--art-gray-500);
  }

  .route-enabled {
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
