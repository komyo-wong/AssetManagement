<!-- 登录页面 -->
<template>
  <div class="flex w-full h-screen">
    <LoginLeftView />

    <div class="relative flex-1">
      <AuthTopBar />

      <div class="auth-right-wrap">
        <div class="form">
          <h3 class="title">{{ welcomeTitle }}</h3>
          <p class="sub-title">{{ welcomeSubtitle }}</p>
          <ElForm
            ref="formRef"
            :model="formData"
            :rules="rules"
            :key="formKey"
            autocomplete="off"
            @keyup.enter="handleSubmit"
            style="margin-top: 25px"
          >
            <!-- 干扰浏览器把已保存的 root 凭据灌进登录框 -->
            <input
              type="text"
              name="fake-username"
              autocomplete="username"
              tabindex="-1"
              aria-hidden="true"
              class="autofill-trap"
            />
            <input
              type="password"
              name="fake-password"
              autocomplete="current-password"
              tabindex="-1"
              aria-hidden="true"
              class="autofill-trap"
            />
            <ElFormItem prop="username">
              <ElInput
                class="custom-height"
                :placeholder="$t('login.placeholder.username')"
                v-model.trim="formData.username"
                name="login-account"
                autocomplete="off"
              />
            </ElFormItem>
            <ElFormItem prop="password">
              <ElInput
                class="custom-height"
                :placeholder="$t('login.placeholder.password')"
                v-model.trim="formData.password"
                type="password"
                name="login-secret"
                autocomplete="off"
                show-password
              />
            </ElFormItem>

            <!-- 推拽验证 -->
            <div class="relative pb-5 mt-6">
              <div
                class="relative z-[2] overflow-hidden select-none rounded-lg border border-transparent tad-300"
                :class="{ '!border-[#FF4E4F]': !isPassing && isClickPass }"
              >
                <ArtDragVerify
                  ref="dragVerify"
                  v-model:value="isPassing"
                  :text="$t('login.sliderText')"
                  textColor="var(--art-gray-700)"
                  :successText="$t('login.sliderSuccessText')"
                  progressBarBg="var(--main-color)"
                  :background="isDark ? '#26272F' : '#F1F1F4'"
                  handlerBg="var(--default-box-color)"
                />
              </div>
              <p
                class="absolute top-0 z-[1] px-px mt-2 text-xs text-[#f56c6c] tad-300"
                :class="{ 'translate-y-10': !isPassing && isClickPass }"
              >
                {{ $t('login.placeholder.slider') }}
              </p>
            </div>

            <div class="flex justify-end mt-2 text-sm">
              <RouterLink class="text-theme" :to="{ name: 'ForgetPassword' }">{{
                $t('login.forgetPwd')
              }}</RouterLink>
            </div>

            <div style="margin-top: 30px">
              <ElButton
                class="w-full custom-height"
                type="primary"
                @click="handleSubmit"
                :loading="loading"
                v-ripple
              >
                {{ $t('login.btnText') }}
              </ElButton>
            </div>

            <div class="managed-account-note">
              <ArtSvgIcon icon="ri:shield-user-line" />
              <span>{{ $t('login.managedAccount') }}</span>
            </div>
          </ElForm>
        </div>
      </div>

      <p class="login-legal">
        <span>v{{ appVersion }}</span>
        <span>{{ copyrightText }}</span>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useUserStore } from '@/store/modules/user'
  import { useBrandingStore } from '@/store/modules/branding'
  import { useI18n } from 'vue-i18n'
  import { HttpError } from '@/utils/http/error'
  import { fetchLogin } from '@/api/auth'
  import { ElMessage, ElNotification, type FormInstance, type FormRules } from 'element-plus'
  import { useSettingStore } from '@/store/modules/setting'
  import { storeToRefs } from 'pinia'

  defineOptions({ name: 'Login' })

  const settingStore = useSettingStore()
  const brandingStore = useBrandingStore()
  const { isDark } = storeToRefs(settingStore)
  const { systemName, branding } = storeToRefs(brandingStore)
  const { t, locale } = useI18n()
  const formKey = ref(0)
  const appVersion = __APP_VERSION__

  onMounted(() => {
    void brandingStore.ensureLoaded()
  })

  const welcomeTitle = computed(
    () => branding.value.loginWelcomeTitle?.trim() || t('login.title')
  )
  const welcomeSubtitle = computed(
    () => branding.value.loginWelcomeSubtitle?.trim() || t('login.subTitle')
  )
  const copyrightText = computed(
    () => branding.value.copyrightText?.trim() || t('login.copyright')
  )

  // 监听语言切换，重置表单
  watch(locale, () => {
    formKey.value++
  })

  const dragVerify = ref()

  const userStore = useUserStore()
  const router = useRouter()
  const route = useRoute()
  const isPassing = ref(false)
  const isClickPass = ref(false)

  const formRef = ref<FormInstance>()

  const formData = reactive({
    username: '',
    password: ''
  })

  const rules = computed<FormRules>(() => ({
    username: [{ required: true, message: t('login.placeholder.username'), trigger: 'blur' }],
    password: [{ required: true, message: t('login.placeholder.password'), trigger: 'blur' }]
  }))

  const loading = ref(false)

  // 登录
  const handleSubmit = async () => {
    if (!formRef.value) return

    try {
      // 表单验证
      const valid = await formRef.value.validate()
      if (!valid) return

      // 拖拽验证
      if (!isPassing.value) {
        isClickPass.value = true
        return
      }

      loading.value = true

      // 登录请求
      const { username, password } = formData

      const { accessToken } = await fetchLogin({
        account: username,
        password
      })

      // 验证token
      if (!accessToken) {
        throw new Error('Login failed - no token received')
      }

      // 存储 token 和登录状态
      userStore.setToken(accessToken)
      userStore.setLoginStatus(true)

      // 登录成功处理
      showLoginSuccessNotice()

      // 获取 redirect 参数，如果存在则跳转到指定页面，否则跳转到首页
      const redirect = route.query.redirect as string
      router.push(redirect || '/')
    } catch (error) {
      // 401 在全局拦截里会跳过 toast（避免误登出），登录页需自行提示
      ElMessage.error(resolveLoginErrorMessage(error))
    } finally {
      loading.value = false
      resetDragVerify()
    }
  }

  const resolveLoginErrorMessage = (error: unknown) => {
    if (error instanceof HttpError) {
      if (error.errorCode === 'AUTH_RATE_LIMITED' || error.code === 429) {
        return t('login.rateLimited')
      }
      if (error.errorCode === 'AUTH_UNAUTHORIZED' || error.code === 401) {
        return t('login.invalidCredentials')
      }
      // 后端未启动时，Vite 代理常返回 5xx / 网络错误，避免误报成笼统的“内部错误”
      if (
        error.code === 0 ||
        error.code === 502 ||
        error.code === 503 ||
        error.code === 504 ||
        error.code === 500
      ) {
        return t('login.serverUnavailable')
      }
      if (error.message?.trim()) {
        return error.message
      }
    }
    return t('login.failure')
  }

  // 重置拖拽验证
  const resetDragVerify = () => {
    dragVerify.value?.reset()
  }

  // 登录成功提示
  const showLoginSuccessNotice = () => {
    setTimeout(() => {
      ElNotification({
        title: t('login.success.title'),
        type: 'success',
        duration: 2500,
        zIndex: 10000,
        message: `${t('login.success.message')}, ${systemName.value}!`
      })
    }, 1000)
  }
</script>

<style scoped>
  @import './style.css';

  .autofill-trap {
    position: absolute;
    left: -9999px;
    width: 1px;
    height: 1px;
    opacity: 0;
    pointer-events: none;
  }
</style>

<style lang="scss" scoped>
  .managed-account-note {
    display: flex;
    gap: 8px;
    align-items: flex-start;
    padding: 11px 12px;
    margin-top: 20px;
    font-size: 12px;
    line-height: 1.6;
    color: var(--art-gray-600);
    background: var(--art-gray-100);
    border: 1px solid var(--art-card-border);
    border-radius: 10px;

    .art-svg-icon {
      flex: 0 0 auto;
      margin-top: 2px;
      color: var(--theme-color);
    }
  }

  .login-legal {
    position: absolute;
    right: 0;
    bottom: 22px;
    left: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
    align-items: center;
    padding: 0 16px;
    font-size: 12px;
    line-height: 1.5;
    color: var(--art-gray-500);
    pointer-events: none;
  }
</style>
