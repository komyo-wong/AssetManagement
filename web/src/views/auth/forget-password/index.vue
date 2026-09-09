<template>
  <div class="flex w-full h-screen">
    <LoginLeftView />

    <div class="relative flex-1">
      <AuthTopBar />

      <div class="auth-right-wrap">
        <div class="form">
          <h3 class="title">{{ $t('forgetPassword.title') }}</h3>
          <p class="sub-title">{{ $t('forgetPassword.subTitle') }}</p>
          <div class="mt-5">
            <ElInput
              class="custom-height"
              :placeholder="$t('forgetPassword.placeholder')"
              v-model.trim="account"
              @keyup.enter="submit"
            />
          </div>

          <div style="margin-top: 15px">
            <ElButton
              class="w-full custom-height"
              type="primary"
              @click="submit"
              :loading="loading"
              v-ripple
            >
              {{ $t('forgetPassword.submitBtnText') }}
            </ElButton>
          </div>

          <div style="margin-top: 15px">
            <ElButton class="w-full custom-height" plain @click="toLogin">
              {{ $t('forgetPassword.backBtnText') }}
            </ElButton>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { fetchForgotPassword } from '@/api/auth'
  import { HttpError } from '@/utils/http/error'
  import { ElMessage } from 'element-plus'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'ForgetPassword' })

  const { t } = useI18n()
  const router = useRouter()

  const account = ref('')
  const loading = ref(false)

  const submit = async () => {
    if (!account.value) {
      ElMessage.warning(t('forgetPassword.accountRequired'))
      return
    }
    loading.value = true
    try {
      await fetchForgotPassword({ account: account.value })
      ElMessage.success(t('forgetPassword.sent'))
      router.push({ name: 'Login' })
    } catch (error) {
      ElMessage.error(resolveErrorMessage(error))
    } finally {
      loading.value = false
    }
  }

  const resolveErrorMessage = (error: unknown) => {
    if (error instanceof HttpError) {
      if (error.errorCode === 'AUTH_RATE_LIMITED' || error.code === 429) {
        return t('forgetPassword.rateLimited')
      }
      if (
        error.code === 0 ||
        error.code === 502 ||
        error.code === 503 ||
        error.code === 504 ||
        error.code === 500
      ) {
        return error.message?.trim() || t('forgetPassword.serverUnavailable')
      }
      if (error.message?.trim()) {
        return error.message
      }
    }
    return t('forgetPassword.serverUnavailable')
  }

  const toLogin = () => {
    router.push({ name: 'Login' })
  }
</script>

<style scoped>
  @import '../login/style.css';
</style>
