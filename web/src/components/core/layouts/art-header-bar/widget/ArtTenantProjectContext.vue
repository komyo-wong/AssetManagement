<template>
  <div class="scope-switcher" :aria-label="t('context.title')">
    <div class="scope-selects">
      <ElSelect
        :model-value="currentTenantId"
        class="tenant-select"
        :placeholder="t('context.selectTenant')"
        :disabled="activeTenants.length === 0"
        filterable
        @change="changeTenant"
      >
        <ElOption
          v-for="tenant in activeTenants"
          :key="tenant.tenantId"
          :value="tenant.tenantId"
          :label="tenant.tenantName"
        >
          <div class="option-row">
            <span>{{ tenant.tenantName }}</span>
            <small>{{ tenant.tenantCode }}</small>
          </div>
        </ElOption>
      </ElSelect>

      <span class="scope-separator">/</span>

      <ElSelect
        :model-value="currentProjectId"
        class="project-select"
        :placeholder="t('context.selectProject')"
        :disabled="!currentTenantId || activeProjects.length === 0"
        filterable
        @change="changeProject"
      >
        <ElOption
          v-for="project in activeProjects"
          :key="project.projectId"
          :value="project.projectId"
          :label="project.projectName"
        >
          <div class="option-row">
            <span>{{ project.projectName }}</span>
            <small>{{ project.projectCode }}</small>
          </div>
        </ElOption>
      </ElSelect>
    </div>

    <ElPopover placement="bottom-end" :width="300" trigger="click">
      <template #reference>
        <ElButton class="scope-mobile-button" circle :aria-label="t('context.switch')">
          <ArtSvgIcon icon="ri:organization-chart" />
        </ElButton>
      </template>
      <div class="scope-mobile-panel">
        <strong>{{ t('context.title') }}</strong>
        <label>{{ t('context.tenant') }}</label>
        <ElSelect
          :model-value="currentTenantId"
          :placeholder="t('context.selectTenant')"
          :disabled="activeTenants.length === 0"
          @change="changeTenant"
        >
          <ElOption
            v-for="tenant in activeTenants"
            :key="tenant.tenantId"
            :value="tenant.tenantId"
            :label="tenant.tenantName"
          />
        </ElSelect>
        <label>{{ t('context.project') }}</label>
        <ElSelect
          :model-value="currentProjectId"
          :placeholder="t('context.selectProject')"
          :disabled="!currentTenantId || activeProjects.length === 0"
          @change="changeProject"
        >
          <ElOption
            v-for="project in activeProjects"
            :key="project.projectId"
            :value="project.projectId"
            :label="project.projectName"
          />
        </ElSelect>
        <ElEmpty
          v-if="activeTenants.length === 0"
          :description="t('context.noActiveMembership')"
          :image-size="54"
        />
      </div>
    </ElPopover>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'
  import { useTenantContextStore } from '@/store/modules/tenant-context'

  defineOptions({ name: 'ArtTenantProjectContext' })

  const { t } = useI18n()
  const contextStore = useTenantContextStore()
  const { activeTenants, activeProjects, currentTenantId, currentProjectId } =
    storeToRefs(contextStore)

  const changeTenant = (tenantId: string) => contextStore.setTenant(tenantId)
  const changeProject = (projectId: string) => contextStore.setProject(projectId)
</script>

<style lang="scss" scoped>
  .scope-switcher,
  .scope-selects {
    display: flex;
    align-items: center;
  }

  .scope-switcher {
    min-width: 0;
    margin-left: auto;
  }

  .scope-selects {
    gap: 5px;
    padding: 4px 7px;
    background: var(--art-gray-100);
    border: 1px solid var(--art-card-border);
    border-radius: 10px;
  }

  .scope-selects :deep(.el-select__wrapper) {
    min-height: 28px;
    background: transparent;
    box-shadow: none;
  }

  .tenant-select {
    width: 142px;
  }

  .project-select {
    width: 154px;
  }

  .scope-separator {
    color: var(--art-gray-400);
  }

  .scope-mobile-button {
    display: none;
  }

  .option-row {
    display: flex;
    gap: 18px;
    align-items: center;
    justify-content: space-between;
    width: 100%;

    small {
      font-size: 10px;
      color: var(--art-gray-500);
    }
  }

  .scope-mobile-panel {
    display: flex;
    flex-direction: column;
    gap: 9px;

    strong {
      margin-bottom: 3px;
      color: var(--art-gray-900);
    }

    label {
      margin-top: 3px;
      font-size: 11px;
      color: var(--art-gray-500);
    }
  }

  @media (width <= 1180px) {
    .tenant-select,
    .project-select {
      width: 118px;
    }
  }

  @media (width <= 820px) {
    .scope-selects {
      display: none;
    }

    .scope-mobile-button {
      display: inline-flex;
    }
  }
</style>
