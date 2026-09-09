<template>
  <div class="module-page">
    <section class="module-hero art-card">
      <div class="module-icon">
        <ArtSvgIcon :icon="String(route.meta.icon || 'ri:apps-2-line')" />
      </div>
      <div>
        <ElTag size="small" type="info" effect="plain" round>{{
          t('modulePlaceholder.status')
        }}</ElTag>
        <h1>{{ pageTitle }}</h1>
        <p>{{ t('modulePlaceholder.description') }}</p>
      </div>
    </section>

    <section class="module-content art-card">
      <div class="contract-heading">
        <div>
          <span>{{ t('modulePlaceholder.contractEyebrow') }}</span>
          <h2>{{ t('modulePlaceholder.contractTitle') }}</h2>
        </div>
        <ArtSvgIcon icon="ri:braces-line" />
      </div>
      <div class="contract-grid">
        <div v-for="item in contractItems" :key="item.title" class="contract-item">
          <ArtSvgIcon :icon="item.icon" />
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </div>
        </div>
      </div>
      <ElAlert type="info" :closable="false" show-icon :title="t('modulePlaceholder.notice')" />
    </section>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'ProductModulePlaceholder' })

  const route = useRoute()
  const { t } = useI18n()

  const pageTitle = computed(() => t(String(route.meta.title)))
  const contractItems = computed(() => [
    {
      title: t('modulePlaceholder.contract.scope'),
      description: t('modulePlaceholder.contract.scopeHint'),
      icon: 'ri:folder-shield-2-line'
    },
    {
      title: t('modulePlaceholder.contract.permission'),
      description: t('modulePlaceholder.contract.permissionHint'),
      icon: 'ri:key-2-line'
    },
    {
      title: t('modulePlaceholder.contract.audit'),
      description: t('modulePlaceholder.contract.auditHint'),
      icon: 'ri:file-history-line'
    }
  ])
</script>

<style lang="scss" scoped>
  .module-page {
    display: grid;
    gap: 18px;
  }

  .module-hero {
    display: flex;
    gap: 20px;
    align-items: flex-start;
    padding: 28px;

    h1 {
      margin-top: 10px;
      font-size: 26px;
      font-weight: 650;
      color: var(--art-gray-900);
    }

    p {
      max-width: 680px;
      margin-top: 8px;
      font-size: 14px;
      line-height: 1.7;
      color: var(--art-gray-600);
    }
  }

  .module-icon {
    display: grid;
    flex: 0 0 auto;
    place-items: center;
    width: 58px;
    height: 58px;
    font-size: 25px;
    color: var(--theme-color);
    background: color-mix(in srgb, var(--theme-color) 10%, transparent);
    border: 1px solid color-mix(in srgb, var(--theme-color) 18%, transparent);
    border-radius: 18px;
  }

  .module-content {
    padding: 28px;
  }

  .contract-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;

    span {
      font-size: 11px;
      font-weight: 600;
      color: var(--theme-color);
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }

    h2 {
      margin-top: 5px;
      font-size: 18px;
      font-weight: 600;
      color: var(--art-gray-900);
    }

    > .art-svg-icon {
      font-size: 28px;
      color: var(--art-gray-400);
    }
  }

  .contract-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
    margin: 22px 0;
  }

  .contract-item {
    display: flex;
    gap: 12px;
    padding: 18px;
    background: var(--art-gray-100);
    border: 1px solid var(--art-card-border);
    border-radius: 14px;

    > .art-svg-icon {
      flex: 0 0 auto;
      margin-top: 2px;
      font-size: 19px;
      color: var(--theme-color);
    }

    strong {
      font-size: 13px;
      color: var(--art-gray-800);
    }

    p {
      margin-top: 5px;
      font-size: 12px;
      line-height: 1.6;
      color: var(--art-gray-500);
    }
  }

  @media (width <= 760px) {
    .module-hero {
      padding: 22px;
    }

    .contract-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
