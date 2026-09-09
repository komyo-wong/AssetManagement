<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ResourceCrudPage from '@/views/product/shared/ResourceCrudPage.vue'
import { assetTypesApi } from '@/api/asset-platform'

const { t } = useI18n()
const columns = computed(() => [
  { prop: 'code', label: t('common.code'), width: 140 },
  { prop: 'name', label: t('common.name'), width: 180 },
  { prop: 'description', label: t('common.description') },
  { prop: 'updatedAt', label: t('common.updatedAt'), width: 180 }
])
</script>
<template>
  <ResourceCrudPage
    :title="t('product.assets.types.title')"
    :subtitle="t('product.assets.types.subtitle')"
    table-key="asset-types"
    :list="assetTypesApi.list"
    :create="assetTypesApi.create"
    :update="assetTypesApi.update"
    :remove="assetTypesApi.remove"
    :columns="columns"
    :search-keys="['code', 'name', 'description']"
  />
</template>
