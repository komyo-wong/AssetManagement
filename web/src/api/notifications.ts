import request from '@/utils/http'

export function fetchNotificationOverview() {
  return request.get<Record<string, unknown>>({
    url: '/api/v1/me/notifications',
    showErrorMessage: false
  })
}

export function createNotificationChannel(data: Record<string, unknown>) {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/me/notifications/channels',
    data
  })
}

export function updateNotificationChannel(id: string, data: Record<string, unknown>) {
  return request.put<Record<string, unknown>>({
    url: `/api/v1/me/notifications/channels/${encodeURIComponent(id)}`,
    data
  })
}

export function deleteNotificationChannel(id: string) {
  return request.del<void>({
    url: `/api/v1/me/notifications/channels/${encodeURIComponent(id)}`
  })
}

export function createNotificationSubscription(data: Record<string, unknown>) {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/me/notifications/subscriptions',
    data
  })
}

export function updateNotificationSubscription(id: string, data: Record<string, unknown>) {
  return request.put<Record<string, unknown>>({
    url: `/api/v1/me/notifications/subscriptions/${encodeURIComponent(id)}`,
    data
  })
}

export function deleteNotificationSubscription(id: string) {
  return request.del<void>({
    url: `/api/v1/me/notifications/subscriptions/${encodeURIComponent(id)}`
  })
}

export function fetchNotificationDeliveries(params: { current?: number; size?: number }) {
  return request.get<Api.Common.PaginatedResponse<Record<string, unknown>>>({
    url: '/api/v1/me/notifications/deliveries',
    params,
    showErrorMessage: true
  })
}
