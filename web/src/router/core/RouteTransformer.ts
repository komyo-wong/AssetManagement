/**
 * 路由转换器
 *
 * 负责将菜单数据转换为 Vue Router 路由配置
 *
 * @module router/core/RouteTransformer
 * @author Art Design Pro Team
 */

import type { RouteRecordRaw } from 'vue-router'
import type { AppRouteRecord } from '@/types/router'
import { ComponentLoader } from './ComponentLoader'
import { IframeRouteManager } from './IframeRouteManager'
import { RoutesAlias } from '../routesAlias'

interface ConvertedRoute extends Omit<RouteRecordRaw, 'children'> {
  id?: number
  children?: ConvertedRoute[]
  component?: RouteRecordRaw['component'] | (() => Promise<any>)
}

export class RouteTransformer {
  private componentLoader: ComponentLoader
  private iframeManager: IframeRouteManager

  constructor(componentLoader: ComponentLoader) {
    this.componentLoader = componentLoader
    this.iframeManager = IframeRouteManager.getInstance()
  }

  /**
   * 转换路由配置
   */
  transform(route: AppRouteRecord, depth = 0): ConvertedRoute {
    const { component, children, ...routeConfig } = route

    // 基础路由配置
    const converted: ConvertedRoute = {
      ...routeConfig,
      component: undefined
    }

    // 处理不同类型的路由
    if (route.meta.isIframe) {
      this.handleIframeRoute(converted, route, depth)
    } else if (this.isFirstLevelRoute(route, depth)) {
      this.handleFirstLevelRoute(converted, route, component as string)
    } else {
      this.handleNormalRoute(converted, component as string)
    }

    // 递归处理子路由。菜单侧会把子 path 规范成绝对路径，注册到 Vue Router 时必须改回相对路径，
    // 否则嵌套 Layout 的 RouterView 可能匹配失败，出现点菜单无内容/像“没反应”。
    if (children?.length) {
      const parentPath = String(converted.path || route.path || '')
      converted.children = children.map((child) => {
        const transformed = this.transform(child, depth + 1)
        if (depth === 0) {
          transformed.path = this.toRelativeChildPath(parentPath, String(child.path || ''))
        }
        return transformed
      })
    }

    return converted
  }

  /**
   * 判断是否为一级路由（需要 Layout 包裹）
   */
  private isFirstLevelRoute(route: AppRouteRecord, depth: number): boolean {
    return depth === 0 && (!route.children || route.children.length === 0)
  }

  /**
   * 处理 iframe 类型路由
   */
  private handleIframeRoute(
    targetRoute: ConvertedRoute,
    sourceRoute: AppRouteRecord,
    depth: number
  ): void {
    if (depth === 0) {
      // 顶级 iframe：用 Layout 包裹
      targetRoute.component = this.componentLoader.loadLayout()
      targetRoute.path = this.extractFirstSegment(sourceRoute.path || '')
      targetRoute.name = ''

      targetRoute.children = [
        {
          ...sourceRoute,
          component: this.componentLoader.loadIframe()
        } as ConvertedRoute
      ]
    } else {
      // 非顶级（嵌套）iframe：直接使用 Iframe.vue
      targetRoute.component = this.componentLoader.loadIframe()
    }

    // 记录 iframe 路由
    this.iframeManager.add(sourceRoute)
  }

  /**
   * 处理一级菜单路由
   */
  private handleFirstLevelRoute(
    converted: ConvertedRoute,
    route: AppRouteRecord,
    component: string | undefined
  ): void {
    converted.component = this.componentLoader.loadLayout()
    converted.path = this.extractFirstSegment(route.path || '')
    converted.name = ''
    route.meta.isFirstLevel = true

    converted.children = [
      {
        ...route,
        // 一级页挂到 Layout 下时使用空 path，避免绝对 path 嵌套异常
        path: '',
        component: component ? this.componentLoader.load(component) : undefined
      } as ConvertedRoute
    ]
  }

  /**
   * 处理普通路由
   */
  private handleNormalRoute(converted: ConvertedRoute, component: string | undefined): void {
    if (!component) return
    // 目录型菜单统一走 Layout，保证子页面渲染在同一 RouterView 中
    if (component === RoutesAlias.Layout || component === '/index/index') {
      converted.component = this.componentLoader.loadLayout()
      return
    }
    converted.component = this.componentLoader.load(component)
  }

  /**
   * 提取路径的第一段
   */
  private extractFirstSegment(path: string): string {
    const segments = path.split('/').filter(Boolean)
    return segments.length > 0 ? `/${segments[0]}` : '/'
  }

  /**
   * 将菜单绝对路径还原为父路由下的相对路径
   */
  private toRelativeChildPath(parentPath: string, childPath: string): string {
    if (!childPath) return ''
    if (!childPath.startsWith('/')) return childPath

    const parent = parentPath.startsWith('/') ? parentPath.replace(/\/$/, '') : `/${parentPath}`
    if (childPath === parent) return ''
    if (childPath.startsWith(`${parent}/`)) {
      return childPath.slice(parent.length + 1)
    }
    // 兜底：取最后一段，避免注册成根级绝对路径
    return childPath.split('/').filter(Boolean).pop() || childPath.replace(/^\//, '')
  }
}
