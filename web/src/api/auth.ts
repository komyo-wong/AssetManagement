import request from '@/utils/http'

/**
 * 登录
 * @param params 登录参数
 * @returns 登录响应
 */
export function fetchLogin(params: Api.Auth.LoginParams) {
  return request.post<Api.Auth.LoginResponse>({
    url: '/api/v1/auth/login',
    data: params,
    // 登录失败的 401 不应触发全局登出/跳转
    skipAuthRedirect: true
  })
}

/** 向对应用户邮箱发送新的随机密码。不存在的账号也返回成功，避免枚举。 */
export function fetchForgotPassword(params: { account: string }) {
  return request.post<void>({
    url: '/api/v1/auth/forgot-password',
    data: params,
    showErrorMessage: false,
    skipAuthRedirect: true
  })
}

/**
 * 使用后端 HttpOnly Secure SameSite Cookie 中的刷新凭证换取短期访问令牌。
 * 刷新凭证不会进入 JavaScript 响应或 Web Storage。
 */
export function fetchRefreshAccessToken() {
  return request.post<Api.Auth.LoginResponse>({
    url: '/api/v1/auth/refresh',
    showErrorMessage: false,
    skipAuthRedirect: true
  })
}

/**
 * 获取用户信息
 * @returns 用户信息
 */
export function fetchGetUserInfo() {
  return request.get<Api.Auth.UserInfo>({
    url: '/api/v1/auth/me'
    // 自定义请求头
    // headers: {
    //   'X-Custom-Header': 'your-custom-value'
    // }
  })
}

/** 撤销当前会话并让服务端清理刷新 Cookie。 */
export function fetchLogout() {
  return request.post<void>({
    url: '/api/v1/auth/logout',
    showErrorMessage: false,
    skipAuthRedirect: true
  })
}

export function updateMyProfile(data: { displayName: string; email: string }) {
  return request.put<{
    userId: string
    userName: string
    email: string
    displayName?: string
    avatar?: string | null
    alertPopupEnabled?: boolean
    alertSoundEnabled?: boolean
  }>({
    url: '/api/v1/me/profile',
    data
  })
}

export function updateMyAvatar(data: { avatar: string | null }) {
  return request.put<{
    avatar?: string | null
    displayName?: string
    alertPopupEnabled?: boolean
    alertSoundEnabled?: boolean
  }>({
    url: '/api/v1/me/avatar',
    data
  })
}

export function changeMyPassword(data: { currentPassword: string; newPassword: string }) {
  return request.put<void>({
    url: '/api/v1/me/password',
    data
  })
}

export function updateMyUiPreferences(data: {
  alertPopupEnabled: boolean
  alertSoundEnabled: boolean
}) {
  return request.put<{
    alertPopupEnabled?: boolean
    alertSoundEnabled?: boolean
    avatar?: string | null
    displayName?: string
    hasCustomAlertSound?: boolean
    alertSoundFileName?: string | null
  }>({
    url: '/api/v1/me/ui-preferences',
    data
  })
}

export function fetchMyAlertSound() {
  return request.get<{
    hasCustomAlertSound?: boolean
    fileName?: string | null
    sound?: string | null
  }>({
    url: '/api/v1/me/alert-sound',
    showErrorMessage: false
  })
}

export function updateMyAlertSound(data: { sound: string | null; fileName?: string | null }) {
  return request.put<{
    hasCustomAlertSound?: boolean
    fileName?: string | null
    sound?: string | null
  }>({
    url: '/api/v1/me/alert-sound',
    data
  })
}
