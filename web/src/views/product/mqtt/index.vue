<template>
  <div class="mqtt-page">
    <section class="mqtt-header art-card">
      <div class="header-copy">
        <div class="header-icon"><ArtSvgIcon icon="ri:cloud-line" /></div>
        <div>
          <div class="header-kicker">MQTT CONTROL PLANE</div>
          <h1>{{ t('mqtt.title') }}</h1>
          <p>{{ t('mqtt.subtitle') }}</p>
        </div>
      </div>
      <div class="header-actions">
        <ElButton
          :disabled="!projectScope || !canManageConnections"
          @click="openConnectionDrawer('standby')"
        >
          <ArtSvgIcon icon="ri:add-line" class="mr-1" />
          {{ t('mqtt.addStandby') }}
        </ElButton>
        <ElButton
          type="primary"
          :disabled="!projectScope || !canManageConnections"
          @click="openConnectionDrawer('primary')"
        >
          <ArtSvgIcon icon="ri:add-line" class="mr-1" />
          {{ t('mqtt.addConnection') }}
        </ElButton>
      </div>
    </section>

    <div v-if="loadError" class="load-error art-card" role="alert">
      <div>
        <ArtSvgIcon icon="ri:error-warning-line" />
        <div>
          <strong>{{ t('mqtt.loadFailed') }}</strong>
          <p>{{ loadError }}</p>
        </div>
      </div>
      <ElButton :loading="loading" @click="loadAll">
        <ArtSvgIcon icon="ri:refresh-line" class="mr-1" />
        {{ t('common.refresh') }}
      </ElButton>
    </div>

    <section class="mqtt-metrics" :aria-busy="loading">
      <article v-for="metric in metrics" :key="metric.label" class="mqtt-metric art-card">
        <div class="metric-top">
          <span>{{ metric.label }}</span>
          <ArtSvgIcon :icon="metric.icon" />
        </div>
        <ElSkeleton v-if="loading" :rows="0" animated class="metric-skeleton" />
        <strong v-else>{{ metric.value ?? '—' }}</strong>
        <small>{{ metric.hint }}</small>
      </article>
    </section>

    <section class="broker-grid">
      <article v-for="slot in connectionSlots" :key="slot.role" class="broker-card art-card">
        <div class="broker-card-head">
          <div class="broker-role">
            <div class="role-icon" :class="slot.role">
              <ArtSvgIcon :icon="slot.icon" />
            </div>
            <div>
              <span>{{ slot.eyebrow }}</span>
              <h2>{{ slot.connection?.name || slot.label }}</h2>
            </div>
          </div>
          <ElTag :type="statusTagType(slot.connection?.status)" effect="plain" round>
            {{ statusLabel(slot.connection?.status) }}
          </ElTag>
        </div>

        <dl class="broker-details">
          <div>
            <dt>{{ t('mqtt.fields.endpoint') }}</dt>
            <dd>{{ slot.connection?.brokerUri || '—' }}</dd>
          </div>
          <div>
            <dt>{{ t('mqtt.fields.environment') }}</dt>
            <dd>
              {{ slot.connection ? t(`mqtt.environments.${slot.connection.environment}`) : '—' }}
            </dd>
          </div>
          <div>
            <dt>{{ t('mqtt.fields.clientId') }}</dt>
            <dd>{{ slot.connection?.clientId || '—' }}</dd>
          </div>
          <div>
            <dt>{{ t('mqtt.fields.lastConnected') }}</dt>
            <dd>{{ formatDateTime(slot.connection?.lastConnectedAt) }}</dd>
          </div>
        </dl>

        <div class="broker-card-foot">
          <div class="security-hint">
            <ArtSvgIcon icon="ri:shield-keyhole-line" />
            <span>{{ t('mqtt.secretHint') }}</span>
          </div>
          <div class="card-actions">
            <ElButton
              v-if="slot.connection && canManageConnections"
              text
              @click="openEditConnection(slot.connection)"
            >
              {{ t('common.edit') }}
            </ElButton>
            <ElButton
              :disabled="!slot.connection || !canTestConnections"
              :loading="testingConnectionId === slot.connection?.id"
              @click="slot.connection && runConnectionTest(slot.connection)"
            >
              <ArtSvgIcon icon="ri:plug-line" class="mr-1" />
              {{ t('mqtt.testConnection') }}
            </ElButton>
          </div>
        </div>
      </article>
    </section>

    <section class="workspace-card art-card">
      <ElTabs v-model="activeTab" class="mqtt-tabs" @tab-change="handleTabChange">
        <ElTabPane :label="t('mqtt.tabs.connections')" name="connections">
          <div class="tab-toolbar">
            <div>
              <h2>{{ t('mqtt.connections.title') }}</h2>
              <p>{{ t('mqtt.connections.subtitle') }}</p>
            </div>
            <div class="toolbar-actions">
              <ElButton
                type="primary"
                :disabled="!projectScope || !canManageConnections"
                @click="openConnectionDrawer('primary')"
              >
                {{ t('mqtt.addConnection') }}
              </ElButton>
            </div>
          </div>

          <div class="table-chrome art-table-card">
            <ProductTableHeader
              v-model:columns="connColumnChecks"
              :loading="loading"
              full-class="workspace-card"
              @refresh="loadAll"
            >
              <template #left>
                <ElSelect
                  v-model="connectionEnvironment"
                  :placeholder="t('mqtt.allEnvironments')"
                  clearable
                >
                  <ElOption
                    v-for="environment in mqttEnvironments"
                    :key="environment"
                    :value="environment"
                    :label="t(`mqtt.environments.${environment}`)"
                  />
                </ElSelect>
              </template>
            </ProductTableHeader>
          </div>

          <ElTable
            v-loading="loading"
            :data="pagedConnections"
            class="mqtt-table"
            table-layout="fixed"
            :size="tableSize"
            :stripe="isZebra"
            :border="isBorder"
          >
            <ElTableColumn v-if="connColVisible('name')" prop="name" :label="t('mqtt.columns.connection')" min-width="180" />
            <ElTableColumn v-if="connColVisible('environment')" :label="t('mqtt.columns.environment')" width="130">
              <template #default="{ row }">
                {{ t(`mqtt.environments.${row.environment}`) }}
              </template>
            </ElTableColumn>
            <ElTableColumn v-if="connColVisible('brokerUri')" prop="brokerUri" :label="t('mqtt.columns.endpoint')" min-width="220" />
            <ElTableColumn v-if="connColVisible('role')" :label="t('mqtt.columns.role')" width="115">
              <template #default="{ row }">{{ t(`mqtt.roles.${row.role}`) }}</template>
            </ElTableColumn>
            <ElTableColumn v-if="connColVisible('credentials')" :label="t('mqtt.columns.credentials')" width="120" align="center">
              <template #default="{ row }">
                <ElTag :type="row.credentialsConfigured ? 'success' : 'info'" effect="plain">
                  {{ row.credentialsConfigured ? t('common.yes') : t('common.no') }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn v-if="connColVisible('status')" :label="t('mqtt.columns.status')" width="125">
              <template #default="{ row }">
                <ElTag :type="statusTagType(row.status)" effect="plain">
                  {{ statusLabel(row.status) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn v-if="connColVisible('__actions')" :label="t('mqtt.columns.actions')" width="140" fixed="right">
              <template #default="{ row }">
                <ElButton
                  text
                  type="primary"
                  :disabled="!canTestConnections"
                  :loading="testingConnectionId === row.id"
                  @click="runConnectionTest(row)"
                >
                  {{ t('mqtt.testConnection') }}
                </ElButton>
                <ElButton text :disabled="!canManageConnections" @click="openEditConnection(row)">
                  {{ t('common.edit') }}
                </ElButton>
              </template>
            </ElTableColumn>
            <template #empty>
              <div class="table-empty">
                <div><ArtSvgIcon icon="ri:cloud-off-line" /></div>
                <strong>{{ t('mqtt.connections.empty') }}</strong>
                <p>{{ t('mqtt.connections.emptyHint') }}</p>
                <ElButton
                  v-if="projectScope && canManageConnections"
                  type="primary"
                  plain
                  @click="openConnectionDrawer('primary')"
                >
                  {{ t('mqtt.addConnection') }}
                </ElButton>
              </div>
            </template>
          </ElTable>
          <TablePager
            :total="connPageTotal"
            :current="connPageCurrent"
            :size="connPageSize"
            @update:current="onConnPageChange"
            @update:size="onConnSizeChange"
          />
        </ElTabPane>

        <ElTabPane :label="t('mqtt.tabs.topicRules')" name="rules">
          <div class="tab-toolbar">
            <div>
              <h2>{{ t('mqtt.rules.title') }}</h2>
              <p>{{ t('mqtt.rules.subtitle') }}</p>
            </div>
            <div class="toolbar-actions">
              <ElButton
                type="primary"
                :disabled="!projectScope || !canManageRoutes || connections.length === 0"
                @click="openTopicRouteDrawer()"
              >
                {{ t('mqtt.rules.addRule') }}
              </ElButton>
            </div>
          </div>

          <div class="rule-guide">
            <div class="rule-guide-icon"><ArtSvgIcon icon="ri:git-merge-line" /></div>
            <div>
              <strong>{{ t('mqtt.rules.guideTitle') }}</strong>
              <p>{{ t('mqtt.rules.guideText') }}</p>
            </div>
            <ElTag type="info" effect="plain">{{ t('mqtt.rules.serverValidated') }}</ElTag>
          </div>

          <div class="table-chrome art-table-card">
            <ProductTableHeader
              v-model:columns="ruleColumnChecks"
              :loading="loading"
              full-class="workspace-card"
              @refresh="loadAll"
            >
              <template #left>
                <ElInput
                  v-model.trim="ruleSearch"
                  :placeholder="t('mqtt.rules.searchPlaceholder')"
                  clearable
                >
                  <template #prefix><ArtSvgIcon icon="ri:search-line" /></template>
                </ElInput>
              </template>
            </ProductTableHeader>
          </div>

          <ElTable
            v-loading="loading"
            :data="pagedTopicRoutes"
            class="mqtt-table"
            table-layout="fixed"
            :size="tableSize"
            :stripe="isZebra"
            :border="isBorder"
          >
            <ElTableColumn v-if="ruleColVisible('name')" prop="name" :label="t('mqtt.columns.ruleName')" min-width="160" />
            <ElTableColumn v-if="ruleColVisible('topicPattern')" prop="topicPattern" label="Topic" min-width="220" />
            <ElTableColumn v-if="ruleColVisible('direction')" :label="t('mqtt.columns.direction')" width="135">
              <template #default="{ row }">{{ t(`mqtt.directions.${row.direction}`) }}</template>
            </ElTableColumn>
            <ElTableColumn v-if="ruleColVisible('messageType')" prop="messageType" :label="t('mqtt.columns.messageType')" width="150" />
            <ElTableColumn v-if="ruleColVisible('qos')" prop="qos" label="QoS" width="70" />
            <ElTableColumn v-if="ruleColVisible('status')" :label="t('mqtt.columns.status')" width="105">
              <template #default="{ row }">
                <ElTag :type="row.enabled ? 'success' : 'info'" effect="plain">
                  {{ row.enabled ? t('common.enabled') : t('common.disabled') }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn v-if="ruleColVisible('__actions')" :label="t('mqtt.columns.actions')" width="80" fixed="right">
              <template #default="{ row }">
                <ElButton
                  text
                  type="primary"
                  :disabled="!canManageRoutes"
                  @click="openTopicRouteDrawer(row)"
                >
                  {{ t('common.edit') }}
                </ElButton>
              </template>
            </ElTableColumn>
            <template #empty>
              <div class="table-empty compact-empty">
                <div><ArtSvgIcon icon="ri:git-branch-line" /></div>
                <strong>{{ t('mqtt.rules.empty') }}</strong>
                <p>{{ t('mqtt.rules.emptyHint') }}</p>
              </div>
            </template>
          </ElTable>
          <TablePager
            :total="routePageTotal"
            :current="routePageCurrent"
            :size="routePageSize"
            @update:current="onRoutePageChange"
            @update:size="onRouteSizeChange"
          />
        </ElTabPane>

        <ElTabPane :label="t('mqtt.tabs.messages')" name="messages">
          <ElAlert
            v-if="messagesError"
            class="data-notice"
            type="warning"
            show-icon
            :closable="false"
            :title="t('mqtt.messages.loadFailed')"
            :description="messagesError"
          />

          <div class="tab-toolbar message-toolbar">
            <div>
              <h2>{{ t('mqtt.messages.title') }}</h2>
              <p>{{ t('mqtt.messages.subtitle') }}</p>
            </div>
            <div class="monitor-status">
              <span class="monitor-dot"></span>
              {{ t('mqtt.messages.snapshotMode') }}
            </div>
          </div>

          <div class="message-filters art-table-card">
            <ProductTableHeader
              v-model:columns="msgColumnChecks"
              :loading="messagesLoading"
              full-class="workspace-card"
              @refresh="searchMessages"
            >
              <template #left>
                <div class="message-filters-left">
                  <ElSelect
                    v-model="messageQuery.gatewayId"
                    :placeholder="t('mqtt.messages.gateway')"
                    filterable
                    clearable
                  >
                    <ElOption
                      v-for="gateway in gateways"
                      :key="gateway.id"
                      :value="gateway.id"
                      :label="gatewayFilterLabel(gateway)"
                    />
                  </ElSelect>
                  <ElInput
                    v-model.trim="messageQuery.topic"
                    :placeholder="t('mqtt.messages.topic')"
                    clearable
                    @keyup.enter="searchMessages"
                  />
                  <ElSelect
                    v-model="messageQuery.parseStatus"
                    :placeholder="t('mqtt.messages.result')"
                    clearable
                  >
                    <ElOption
                      v-for="status in parseStatuses"
                      :key="status"
                      :value="status"
                      :label="t(`mqtt.parseStatus.${status}`)"
                    />
                  </ElSelect>
                  <ElButton type="primary" :loading="messagesLoading" @click="searchMessages">
                    <ArtSvgIcon icon="ri:search-line" class="mr-1" />
                    {{ t('common.search') }}
                  </ElButton>
                  <ElButton :disabled="messagesLoading" @click="resetMessageFilters">
                    {{ t('common.reset') }}
                  </ElButton>
                </div>
              </template>
            </ProductTableHeader>
          </div>

          <ElTable
            v-loading="messagesLoading"
            :data="messages"
            class="mqtt-table message-table"
            table-layout="fixed"
            :size="tableSize"
            :stripe="isZebra"
            :border="isBorder"
          >
            <ElTableColumn v-if="msgColVisible('time')" :label="t('mqtt.columns.time')" width="180">
              <template #default="{ row }">{{ formatDateTime(row.receivedAt) }}</template>
            </ElTableColumn>
            <ElTableColumn
              v-if="msgColVisible('gatewayName')"
              :label="t('mqtt.columns.gateway')"
              width="180"
            >
              <template #default="{ row }">{{ messageGatewayLabel(row) }}</template>
            </ElTableColumn>
            <ElTableColumn v-if="msgColVisible('topic')" prop="topic" label="Topic" min-width="220" />
            <ElTableColumn v-if="msgColVisible('qos')" prop="qos" label="QoS" width="70" />
            <ElTableColumn v-if="msgColVisible('parseResult')" :label="t('mqtt.columns.parseResult')" width="125">
              <template #default="{ row }">
                <ElTag effect="plain">{{ t(`mqtt.parseStatus.${row.parseStatus}`) }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn v-if="msgColVisible('payload')" :label="t('mqtt.columns.payload')" min-width="420">
              <template #default="{ row }">
                <pre class="payload-full">{{ row.payload || row.payloadPreview || '-' }}</pre>
              </template>
            </ElTableColumn>
            <template #empty>
              <div class="table-empty compact-empty">
                <div><ArtSvgIcon icon="ri:terminal-window-line" /></div>
                <strong>{{ t('mqtt.messages.empty') }}</strong>
                <p>{{ t('mqtt.messages.emptyHint') }}</p>
              </div>
            </template>
          </ElTable>

          <ElPagination
            v-if="messagePage.total > 0"
            class="message-pagination"
            background
            layout="total, sizes, prev, pager, next"
            :current-page="messagePage.current"
            :page-size="messagePage.size"
            :page-sizes="[20, 50, 100]"
            :total="messagePage.total"
            @update:current-page="changeMessagePage"
            @update:page-size="changeMessagePageSize"
          />
        </ElTabPane>
      </ElTabs>
    </section>

    <MqttConnectionDrawer
      v-model="connectionDrawerVisible"
      :connection="editingConnection"
      :initial-role="connectionDrawerRole"
      :saving="connectionSaving"
      @submit="saveConnection"
    />

    <MqttTopicRouteDrawer
      v-model="topicRouteDrawerVisible"
      :topic-route="editingTopicRoute"
      :connections="connections"
      :saving="topicRouteSaving"
      @submit="saveTopicRoute"
    />
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import { useAuth } from '@/hooks/core/useAuth'
  import { HttpError } from '@/utils/http/error'
  import { formatDateTime } from '@/utils/datetime'
  import {
    PermissionCode,
    MQTT_CONNECTION_MANAGE_PERMISSIONS,
    MQTT_CONNECTION_VIEW_PERMISSIONS
  } from '@/constants/permissions'
  import {
    createMqttConnection,
    createMqttTopicRoute,
    fetchMqttConnections,
    fetchMqttMessages,
    fetchMqttTopicRoutes,
    gatewaysApi,
    testMqttConnection,
    updateMqttConnection,
    updateMqttTopicRoute
  } from '@/api/asset-platform'
  import MqttConnectionDrawer from './components/MqttConnectionDrawer.vue'
  import MqttTopicRouteDrawer from './components/MqttTopicRouteDrawer.vue'
  import TablePager from '@/components/business/TablePager.vue'
  import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
  import { useClientPagination } from '@/composables/useClientPagination'
  import { useProductTable } from '@/composables/useProductTable'
  import { useTableColumns } from '@/hooks/core/useTableColumns'
  import type { ColumnOption } from '@/types/component'

  defineOptions({ name: 'MqttManagement' })

  type MqttTab = 'connections' | 'rules' | 'messages'

  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()
  const tenantContextStore = useTenantContextStore()
  const { projectScope } = storeToRefs(tenantContextStore)
  const { hasAuth, hasAnyAuth } = useAuth()

  const activeTab = ref<MqttTab>('connections')
  const { tableSize, isZebra, isBorder } = useProductTable()
  const loading = ref(false)
  const messagesError = ref('')
  const messagesLoading = ref(false)
  const loadError = ref('')
  const connectionEnvironment = ref<Api.AssetPlatform.MqttEnvironment | ''>('')
  const ruleSearch = ref('')

  const {
    columnChecks: connColumnChecks,
    resetColumns: resetConnColumns
  } = useTableColumns(() => {
    const cols: ColumnOption[] = [
      { prop: 'name', label: t('mqtt.columns.connection'), checked: true },
      { prop: 'environment', label: t('mqtt.columns.environment'), checked: true },
      { prop: 'brokerUri', label: t('mqtt.columns.endpoint'), checked: true },
      { prop: 'role', label: t('mqtt.columns.role'), checked: true },
      { prop: 'credentials', label: t('mqtt.columns.credentials'), checked: true },
      { prop: 'status', label: t('mqtt.columns.status'), checked: true },
      { prop: '__actions', label: t('mqtt.columns.actions'), width: 140, fixed: 'right', checked: true, disabled: true }
    ]
    return cols
  })
  const {
    columnChecks: ruleColumnChecks,
    resetColumns: resetRuleColumns
  } = useTableColumns(() => {
    const cols: ColumnOption[] = [
      { prop: 'name', label: t('mqtt.columns.ruleName'), checked: true },
      { prop: 'topicPattern', label: 'Topic', checked: true },
      { prop: 'direction', label: t('mqtt.columns.direction'), checked: true },
      { prop: 'messageType', label: t('mqtt.columns.messageType'), checked: true },
      { prop: 'qos', label: 'QoS', checked: true },
      { prop: 'status', label: t('mqtt.columns.status'), checked: true },
      { prop: '__actions', label: t('mqtt.columns.actions'), width: 80, fixed: 'right', checked: true, disabled: true }
    ]
    return cols
  })
  const {
    columnChecks: msgColumnChecks,
    resetColumns: resetMsgColumns
  } = useTableColumns(() => {
    const cols: ColumnOption[] = [
      { prop: 'time', label: t('mqtt.columns.time'), checked: true },
      { prop: 'gatewayName', label: t('mqtt.columns.gateway'), checked: true },
      { prop: 'topic', label: 'Topic', checked: true },
      { prop: 'qos', label: 'QoS', checked: true },
      { prop: 'parseResult', label: t('mqtt.columns.parseResult'), checked: true },
      { prop: 'payload', label: t('mqtt.columns.payload'), checked: true }
    ]
    return cols
  })
  watch(() => t('mqtt.columns.actions'), () => {
    resetConnColumns()
    resetRuleColumns()
    resetMsgColumns()
  })
  function connColVisible(prop: string) {
    return connColumnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
  }
  function ruleColVisible(prop: string) {
    return ruleColumnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
  }
  function msgColVisible(prop: string) {
    return msgColumnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
  }

  const connections = ref<Api.AssetPlatform.MqttConnection[]>([])
  const gateways = ref<Api.AssetPlatform.NamedResource[]>([])
  const topicRoutes = ref<Api.AssetPlatform.MqttTopicRoute[]>([])
  const messages = ref<Api.AssetPlatform.MqttMessageLog[]>([])
  const messagePage = reactive({ current: 1, size: 20, total: 0 })
  const messageQuery = reactive<{
    gatewayId: string
    topic: string
    parseStatus: Api.AssetPlatform.MqttParseStatus | ''
  }>({ gatewayId: '', topic: '', parseStatus: '' })

  const connectionDrawerVisible = ref(false)
  const connectionDrawerRole = ref<Api.AssetPlatform.MqttConnectionRole>('primary')
  const editingConnection = ref<Api.AssetPlatform.MqttConnection | null>(null)
  const connectionSaving = ref(false)
  const testingConnectionId = ref('')

  const topicRouteDrawerVisible = ref(false)
  const editingTopicRoute = ref<Api.AssetPlatform.MqttTopicRoute | null>(null)
  const topicRouteSaving = ref(false)
  let loadGeneration = 0

  const mqttEnvironments: Api.AssetPlatform.MqttEnvironment[] = [
    'development',
    'test',
    'staging',
    'production'
  ]
  const parseStatuses: Api.AssetPlatform.MqttParseStatus[] = [
    'pending',
    'parsed',
    'rejected',
    'failed'
  ]

  const canReadConnections = computed(() => hasAnyAuth(MQTT_CONNECTION_VIEW_PERMISSIONS))
  const canManageConnections = computed(() => hasAnyAuth(MQTT_CONNECTION_MANAGE_PERMISSIONS))
  const canTestConnections = computed(
    () =>
      hasAuth(PermissionCode.MQTT_CONNECTION_TEST) || hasAnyAuth(MQTT_CONNECTION_MANAGE_PERMISSIONS)
  )
  const canReadRoutes = computed(
    () =>
      hasAuth(PermissionCode.MQTT_TOPIC_ROUTE_READ) ||
      hasAuth(PermissionCode.MQTT_TOPIC_ROUTE_CONFIGURE)
  )
  const canManageRoutes = computed(() => hasAuth(PermissionCode.MQTT_TOPIC_ROUTE_CONFIGURE))
  const canReadMessages = computed(() => hasAuth(PermissionCode.MQTT_MESSAGE_READ))

  const filteredConnections = computed(() =>
    connectionEnvironment.value
      ? connections.value.filter((item) => item.environment === connectionEnvironment.value)
      : connections.value
  )

  const filteredTopicRoutes = computed(() => {
    const query = ruleSearch.value.toLocaleLowerCase().trim()
    if (!query) return topicRoutes.value
    return topicRoutes.value.filter((item) =>
      [item.name, item.topicPattern, item.messageType, item.parserKey].some((value) =>
        value.toLocaleLowerCase().includes(query)
      )
    )
  })

  const {
    current: connPageCurrent,
    size: connPageSize,
    total: connPageTotal,
    pagedRows: pagedConnections,
    onPageChange: onConnPageChange,
    onSizeChange: onConnSizeChange
  } = useClientPagination(filteredConnections, { resetOn: connectionEnvironment })

  const {
    current: routePageCurrent,
    size: routePageSize,
    total: routePageTotal,
    pagedRows: pagedTopicRoutes,
    onPageChange: onRoutePageChange,
    onSizeChange: onRouteSizeChange
  } = useClientPagination(filteredTopicRoutes, { resetOn: ruleSearch })

  const metrics = computed(() => [
    {
      label: t('mqtt.metrics.connections'),
      icon: 'ri:cloud-line',
      value: canReadConnections.value && projectScope.value ? connections.value.length : null,
      hint: t('mqtt.metrics.fromApi')
    },
    {
      label: t('mqtt.metrics.connected'),
      icon: 'ri:link-m',
      value:
        canReadConnections.value && projectScope.value
          ? connections.value.filter((item) => item.status === 'connected').length
          : null,
      hint: t('mqtt.metrics.currentStatus')
    },
    {
      label: t('mqtt.metrics.rules'),
      icon: 'ri:git-branch-line',
      value:
        canReadRoutes.value && projectScope.value
          ? topicRoutes.value.filter((item) => item.enabled).length
          : null,
      hint: t('mqtt.metrics.fromApi')
    },
    {
      label: t('mqtt.metrics.messages'),
      icon: 'ri:swap-box-line',
      value: canReadMessages.value && projectScope.value ? messagePage.total : null,
      hint: t('mqtt.metrics.queryTotal')
    }
  ])

  const connectionSlots = computed(() => {
    const environmentRank: Record<Api.AssetPlatform.MqttEnvironment, number> = {
      production: 0,
      staging: 1,
      test: 2,
      development: 3
    }
    const candidates = [...filteredConnections.value].sort(
      (a, b) => environmentRank[a.environment] - environmentRank[b.environment]
    )
    return [
      {
        role: 'primary' as const,
        eyebrow: t('mqtt.roles.primaryEyebrow'),
        label: t('mqtt.roles.primary'),
        icon: 'ri:flashlight-line',
        connection: candidates.find((item) => item.role === 'primary') ?? null
      },
      {
        role: 'standby' as const,
        eyebrow: t('mqtt.roles.standbyEyebrow'),
        label: t('mqtt.roles.standby'),
        icon: 'ri:shield-flash-line',
        connection: candidates.find((item) => item.role === 'standby') ?? null
      }
    ]
  })

  const tabByRouteName: Record<string, MqttTab> = {
    MqttConnections: 'connections',
    MqttTopicRules: 'rules',
    MqttMessageMonitor: 'messages'
  }
  const routeByTab: Record<MqttTab, string> = {
    connections: 'MqttConnections',
    rules: 'MqttTopicRules',
    messages: 'MqttMessageMonitor'
  }

  watch(
    () => route.name,
    (name) => {
      activeTab.value = tabByRouteName[String(name)] ?? 'connections'
    },
    { immediate: true }
  )

  const scopeKey = computed(() =>
    projectScope.value ? `${projectScope.value.tenantId}/${projectScope.value.projectId}` : ''
  )
  const permissionKey = computed(() => tenantContextStore.currentPermissions.join('|'))

  watch(
    [scopeKey, permissionKey],
    () => {
      connectionDrawerVisible.value = false
      topicRouteDrawerVisible.value = false
      messagePage.current = 1
      void loadAll()
    },
    { immediate: true }
  )

  function handleTabChange(name: string | number): void {
    const tab = String(name) as MqttTab
    const targetRoute = routeByTab[tab]
    if (targetRoute && route.name !== targetRoute) void router.replace({ name: targetRoute })
  }

  function clearData(): void {
    connections.value = []
    gateways.value = []
    topicRoutes.value = []
    messages.value = []
    messagePage.total = 0
  }

  async function loadAll(): Promise<void> {
    const generation = ++loadGeneration
    const scope = projectScope.value
    clearData()
    loadError.value = ''
    messagesError.value = ''
    loading.value = false
    messagesLoading.value = false
    if (!scope) return

    loading.value = true
    messagesLoading.value = canReadMessages.value
    const requests: Promise<void>[] = []

    if (canReadConnections.value) {
      requests.push(
        fetchMqttConnections(scope).then((data) => {
          if (generation === loadGeneration) connections.value = data
        })
      )
    }
    requests.push(
      gatewaysApi
        .list(scope)
        .then((data) => {
          if (generation === loadGeneration) {
            gateways.value = (data || []).filter(
              (item) => String(item.status || '').toUpperCase() !== 'ARCHIVED'
            )
          }
        })
        .catch(() => {
          if (generation === loadGeneration) gateways.value = []
        })
    )
    if (canReadRoutes.value) {
      requests.push(
        fetchMqttTopicRoutes(scope).then((data) => {
          if (generation === loadGeneration) topicRoutes.value = data
        })
      )
    }
    if (canReadMessages.value) requests.push(loadMessages(scope, false, generation))

    const results = await Promise.allSettled(requests)
    if (generation !== loadGeneration) return
    const failure = results.find(
      (item): item is PromiseRejectedResult => item.status === 'rejected'
    )
    if (failure) loadError.value = errorMessage(failure.reason)

    loading.value = false
    messagesLoading.value = false
  }

  async function loadMessages(
    scope: Api.AssetPlatform.ProjectScope = projectScope.value as Api.AssetPlatform.ProjectScope,
    manageLoading = true,
    generation?: number
  ): Promise<void> {
    if (!scope || !canReadMessages.value) return
    const requestedScopeKey = `${scope.tenantId}/${scope.projectId}`
    if (manageLoading) messagesLoading.value = true
    messagesError.value = ''
    try {
      const page = await fetchMqttMessages(scope, {
        current: messagePage.current,
        size: messagePage.size,
        gatewayId: messageQuery.gatewayId || undefined,
        topic: messageQuery.topic || undefined,
        parseStatus: messageQuery.parseStatus || undefined
      })
      if (generation !== undefined && generation !== loadGeneration) return
      if (requestedScopeKey !== scopeKey.value) return
      messages.value = page.records
      messagePage.current = page.current
      messagePage.size = page.size
      messagePage.total = page.total
    } catch (error) {
      if (generation !== undefined && generation !== loadGeneration) return
      if (requestedScopeKey !== scopeKey.value) return
      messages.value = []
      messagePage.total = 0
      messagesError.value = errorMessage(error)
    } finally {
      if (manageLoading) messagesLoading.value = false
    }
  }

  function openConnectionDrawer(role: Api.AssetPlatform.MqttConnectionRole): void {
    if (!projectScope.value || !canManageConnections.value) return
    editingConnection.value = null
    connectionDrawerRole.value = role
    connectionDrawerVisible.value = true
  }

  function openEditConnection(connection: Api.AssetPlatform.MqttConnection): void {
    if (!canManageConnections.value) return
    editingConnection.value = connection
    connectionDrawerRole.value = connection.role
    connectionDrawerVisible.value = true
  }

  async function saveConnection(input: Api.AssetPlatform.MqttConnectionInput): Promise<void> {
    const scope = projectScope.value
    if (!scope) return
    connectionSaving.value = true
    try {
      if (editingConnection.value) {
        await updateMqttConnection(scope, editingConnection.value.id, {
          ...input,
          expectedVersion: editingConnection.value.version
        })
        ElMessage.success(t('mqtt.feedback.connectionUpdated'))
      } else {
        await createMqttConnection(scope, input)
        ElMessage.success(t('mqtt.feedback.connectionCreated'))
      }
      connectionDrawerVisible.value = false
      await loadAll()
    } finally {
      connectionSaving.value = false
    }
  }

  async function runConnectionTest(connection: Api.AssetPlatform.MqttConnection): Promise<void> {
    const scope = projectScope.value
    if (!scope || !canTestConnections.value) return
    testingConnectionId.value = connection.id
    try {
      const result = await testMqttConnection(scope, connection.id)
      if (result.success) {
        const latency = result.latencyMs === null ? '' : ` · ${result.latencyMs} ms`
        ElMessage.success(`${t('mqtt.feedback.testSucceeded')}${latency}`)
      } else {
        ElMessage.error(result.message || t('mqtt.feedback.testFailed'))
      }
      await loadAll()
    } finally {
      testingConnectionId.value = ''
    }
  }

  function openTopicRouteDrawer(topicRoute: Api.AssetPlatform.MqttTopicRoute | null = null): void {
    if (!projectScope.value || !canManageRoutes.value || connections.value.length === 0) return
    editingTopicRoute.value = topicRoute
    topicRouteDrawerVisible.value = true
  }

  async function saveTopicRoute(input: Api.AssetPlatform.MqttTopicRouteInput): Promise<void> {
    const scope = projectScope.value
    if (!scope) return
    topicRouteSaving.value = true
    try {
      if (editingTopicRoute.value) {
        await updateMqttTopicRoute(scope, editingTopicRoute.value.id, {
          ...input,
          expectedVersion: editingTopicRoute.value.version
        })
        ElMessage.success(t('mqtt.feedback.routeUpdated'))
      } else {
        await createMqttTopicRoute(scope, input)
        ElMessage.success(t('mqtt.feedback.routeCreated'))
      }
      topicRouteDrawerVisible.value = false
      await loadAll()
    } finally {
      topicRouteSaving.value = false
    }
  }

  async function searchMessages(): Promise<void> {
    messagePage.current = 1
    await loadMessages()
  }

  async function resetMessageFilters(): Promise<void> {
    messageQuery.gatewayId = ''
    messageQuery.topic = ''
    messageQuery.parseStatus = ''
    messagePage.current = 1
    await loadMessages()
  }

  async function changeMessagePage(page: number): Promise<void> {
    messagePage.current = page
    await loadMessages()
  }

  async function changeMessagePageSize(size: number): Promise<void> {
    messagePage.size = size
    messagePage.current = 1
    await loadMessages()
  }

  function statusLabel(status?: Api.AssetPlatform.MqttConnectionStatus): string {
    return t(`mqtt.status.${status ?? 'not_configured'}`)
  }

  function statusTagType(status?: Api.AssetPlatform.MqttConnectionStatus) {
    if (status === 'connected') return 'success'
    if (status === 'connecting') return 'warning'
    if (status === 'error') return 'danger'
    return 'info'
  }

  function errorMessage(error: unknown): string {
    return error instanceof HttpError ? error.message : t('mqtt.unknownError')
  }

  function gatewayFilterLabel(gateway: Api.AssetPlatform.NamedResource): string {
    const mac = String(gateway.fields?.macAddress || '').trim()
    const title = gatewayTitle(gateway)
    return mac ? `${title} · ${mac}` : title
  }

  function gatewayTitle(gateway: Api.AssetPlatform.NamedResource): string {
    return gateway.code && gateway.name && gateway.code !== gateway.name
      ? `${gateway.code} ${gateway.name}`
      : gateway.name || gateway.code || ''
  }

  function messageGatewayLabel(row: { gatewayName?: string; gatewayMac?: string }): string {
    if (messageQuery.gatewayId) {
      const selected = gateways.value.find((item) => item.id === messageQuery.gatewayId)
      if (selected) {
        return gatewayTitle(selected)
      }
    }
    return row.gatewayName || row.gatewayMac || '—'
  }
</script>

<style lang="scss" scoped>
  .mqtt-page {
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  .mqtt-header {
    display: flex;
    gap: 24px;
    align-items: center;
    justify-content: space-between;
    padding: 26px 28px;
    background: linear-gradient(
      115deg,
      var(--default-box-color) 65%,
      color-mix(in srgb, var(--theme-color) 8%, var(--default-box-color)) 100%
    );
  }

  .header-copy {
    display: flex;
    gap: 16px;
    align-items: center;

    h1 {
      font-size: 24px;
      font-weight: 650;
      color: var(--art-gray-900);
    }

    p {
      margin-top: 6px;
      font-size: 13px;
      color: var(--art-gray-600);
    }
  }

  .header-icon {
    display: grid;
    flex: 0 0 auto;
    place-items: center;
    width: 58px;
    height: 58px;
    font-size: 26px;
    color: var(--theme-color);
    background: color-mix(in srgb, var(--theme-color) 10%, transparent);
    border: 1px solid color-mix(in srgb, var(--theme-color) 18%, transparent);
    border-radius: 18px;
  }

  .header-kicker {
    margin-bottom: 5px;
    font-size: 10px;
    font-weight: 700;
    color: var(--theme-color);
    letter-spacing: 0.12em;
  }

  .header-actions,
  .card-actions,
  .toolbar-actions {
    display: flex;
    gap: 9px;
  }

  .data-notice {
    border-radius: calc(var(--custom-radius) + 2px);
  }

  .load-error {
    display: flex;
    gap: 16px;
    align-items: center;
    justify-content: space-between;
    padding: 16px 18px;
    border: 1px solid color-mix(in srgb, var(--el-color-danger) 28%, var(--art-card-border));

    > div {
      display: flex;
      gap: 10px;
      align-items: center;

      > .art-svg-icon {
        font-size: 21px;
        color: var(--el-color-danger);
      }
    }

    strong {
      font-size: 13px;
      color: var(--art-gray-800);
    }

    p {
      margin-top: 3px;
      font-size: 11px;
      color: var(--art-gray-500);
    }
  }

  .mqtt-metrics {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 14px;
  }

  .mqtt-metric {
    min-height: 112px;
    padding: 18px 20px;

    strong {
      display: block;
      margin-top: 8px;
      font-size: 26px;
      color: var(--art-gray-900);
    }

    small {
      display: block;
      margin-top: 4px;
      font-size: 10px;
      color: var(--art-gray-500);
    }
  }

  .metric-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 12px;
    color: var(--art-gray-600);

    .art-svg-icon {
      font-size: 18px;
      color: var(--theme-color);
    }
  }

  .metric-skeleton {
    width: 72px;
    margin-top: 13px;
  }

  .broker-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }

  .broker-card {
    padding: 20px;
  }

  .broker-card-head,
  .broker-card-foot,
  .tab-toolbar {
    display: flex;
    gap: 16px;
    align-items: center;
    justify-content: space-between;
  }

  .broker-role {
    display: flex;
    gap: 12px;
    align-items: center;

    span {
      font-size: 9px;
      color: var(--art-gray-500);
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }

    h2 {
      margin-top: 3px;
      font-size: 15px;
      color: var(--art-gray-900);
    }
  }

  .role-icon {
    display: grid;
    place-items: center;
    width: 42px;
    height: 42px;
    font-size: 19px;
    border-radius: 13px;

    &.primary {
      color: #3d73dd;
      background: rgb(61 115 221 / 10%);
    }

    &.standby {
      color: #9c6b24;
      background: rgb(193 133 43 / 11%);
    }
  }

  .broker-details {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    padding: 16px;
    margin: 18px 0;
    background: var(--art-gray-100);
    border-radius: 12px;

    dt {
      font-size: 9px;
      color: var(--art-gray-500);
    }

    dd {
      margin-top: 4px;
      overflow: hidden;
      font-size: 11px;
      color: var(--art-gray-800);
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .security-hint {
    display: flex;
    gap: 7px;
    align-items: center;
    font-size: 10px;
    color: var(--art-gray-500);
  }

  .workspace-card {
    padding: 18px 20px 22px;
  }

  .tab-toolbar {
    margin-bottom: 18px;

    h2 {
      font-size: 16px;
      font-weight: 600;
      color: var(--art-gray-900);
    }

    p {
      margin-top: 5px;
      font-size: 12px;
      color: var(--art-gray-500);
    }
  }

  .toolbar-actions :deep(.el-select),
  .toolbar-actions :deep(.el-input) {
    width: 190px;
  }

  .mqtt-table {
    width: 100%;
    border: 1px solid var(--art-card-border);
    border-radius: 12px;
  }

  .table-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 240px;
    padding: 24px;

    > div {
      display: grid;
      place-items: center;
      width: 52px;
      height: 52px;
      margin-bottom: 12px;
      font-size: 23px;
      color: var(--art-gray-500);
      background: var(--art-gray-100);
      border-radius: 16px;
    }

    strong {
      font-size: 13px;
      color: var(--art-gray-800);
    }

    p {
      max-width: 420px;
      margin: 6px 0 14px;
      font-size: 11px;
      line-height: 1.6;
      color: var(--art-gray-500);
      text-align: center;
    }
  }

  .compact-empty {
    min-height: 210px;
  }

  .rule-guide {
    display: grid;
    grid-template-columns: 42px 1fr auto;
    gap: 12px;
    align-items: center;
    padding: 14px 16px;
    margin-bottom: 14px;
    background: color-mix(in srgb, var(--theme-color) 5%, var(--default-box-color));
    border: 1px solid color-mix(in srgb, var(--theme-color) 14%, var(--art-card-border));
    border-radius: 12px;

    strong {
      font-size: 12px;
      color: var(--art-gray-800);
    }

    p {
      margin-top: 4px;
      font-size: 11px;
      line-height: 1.55;
      color: var(--art-gray-500);
    }
  }

  .rule-guide-icon {
    display: grid;
    place-items: center;
    width: 42px;
    height: 42px;
    font-size: 18px;
    color: var(--theme-color);
    background: color-mix(in srgb, var(--theme-color) 10%, transparent);
    border-radius: 13px;
  }

  .monitor-status {
    display: inline-flex;
    gap: 7px;
    align-items: center;
    padding: 7px 10px;
    font-size: 11px;
    color: var(--art-gray-600);
    background: var(--art-gray-100);
    border-radius: 999px;
  }

  .monitor-dot {
    width: 7px;
    height: 7px;
    background: var(--art-gray-400);
    border-radius: 50%;
  }

  .message-filters {
    margin-bottom: 14px;
  }

  .message-filters-left {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 9px;
    width: 100%;
  }

  .message-filters-left :deep(.el-select) {
    width: 180px;
  }

  .message-filters-left :deep(.el-input) {
    width: 220px;
  }

  .payload-full {
    margin: 0;
    max-height: 220px;
    overflow: auto;
    padding: 8px 10px;
    font-size: 11px;
    line-height: 1.45;
    white-space: pre-wrap;
    word-break: break-all;
    color: var(--art-gray-800);
    background: color-mix(in srgb, var(--el-fill-color) 70%, transparent);
    border: 1px solid color-mix(in srgb, var(--el-border-color) 70%, transparent);
    border-radius: 4px;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  }

  .payload-preview {
    display: block;
    overflow: hidden;
    font-size: 10px;
    color: var(--art-gray-700);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .message-pagination {
    justify-content: flex-end;
    margin-top: 16px;
  }

  @media (width <= 1000px) {
    .mqtt-metrics {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .broker-grid {
      grid-template-columns: 1fr;
    }

    .message-filters-left :deep(.el-select),
    .message-filters-left :deep(.el-input) {
      width: min(100%, 220px);
    }
  }

  @media (width <= 680px) {
    .mqtt-header,
    .broker-card-head,
    .broker-card-foot,
    .tab-toolbar,
    .load-error {
      flex-direction: column;
      align-items: flex-start;
    }

    .mqtt-header {
      padding: 22px;
    }

    .header-actions,
    .toolbar-actions {
      flex-direction: column;
      width: 100%;
    }

    .header-actions :deep(.el-button),
    .toolbar-actions :deep(.el-select),
    .toolbar-actions :deep(.el-input),
    .toolbar-actions :deep(.el-button) {
      width: 100%;
      margin-left: 0;
    }

    .mqtt-metrics {
      grid-template-columns: 1fr;
    }

    .message-filters-left :deep(.el-select),
    .message-filters-left :deep(.el-input),
    .message-filters-left :deep(.el-button) {
      width: 100%;
    }

    .broker-details {
      grid-template-columns: 1fr;
    }

    .rule-guide {
      grid-template-columns: 42px 1fr;
    }

    .rule-guide :deep(.el-tag) {
      grid-column: 1 / -1;
      width: fit-content;
    }
  }
</style>
