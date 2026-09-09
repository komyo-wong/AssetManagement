<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ResourceCrudPage from '@/views/product/shared/ResourceCrudPage.vue'
import { tasksApi } from '@/api/asset-platform'

const { t } = useI18n()
</script>
<template>
  <ResourceCrudPage
    :title="t('product.projects.tasks.title')"
    table-key="project-tasks"
    :list="tasksApi.list"
    :create="tasksApi.create"
    :update="tasksApi.update"
  />
</template>
