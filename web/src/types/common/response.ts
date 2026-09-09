/**
 * API 响应类型定义模块
 *
 * 提供统一的 API 响应结构类型定义
 *
 * ## 主要功能
 *
 * - 基础响应结构定义
 * - 泛型支持（适配不同数据类型）
 * - 统一的响应格式约束
 *
 * ## 使用场景
 *
 * - API 请求响应类型约束
 * - 接口数据类型定义
 * - 响应数据解析
 *
 * @module types/common/response
 * @author Art Design Pro Team
 */

/** 基础 API 响应结构 */
export interface BaseResponse<T = unknown> {
  /** 请求是否成功 */
  success: boolean
  /** 稳定的业务结果码，例如 OK、AUTH_FORBIDDEN */
  code: string
  /** 面向用户或调用方的消息 */
  message: string
  /** 数据 */
  data: T
  /** 服务端链路追踪 ID */
  traceId: string | null
  /** 服务端生成响应的 UTC 时间 */
  timestamp: string
}
