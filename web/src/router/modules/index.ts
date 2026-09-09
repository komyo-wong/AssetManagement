import { AppRouteRecord } from '@/types/router'
import { assetPlatformRoutes } from './asset-platform'

/**
 * 导出所有模块化路由
 */
export const routeModules: AppRouteRecord[] = [...assetPlatformRoutes]
