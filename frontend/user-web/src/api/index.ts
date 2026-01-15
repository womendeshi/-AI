// import axios, type { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios'
import axios from 'axios'
import type { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios'
import { API_CONFIG } from '@/constants/config'
import router from '@/router'
import type { Result } from '@/types/api'

// Create axios instance
const api: AxiosInstance = axios.create({
  baseURL: API_CONFIG.baseURL,
  timeout: API_CONFIG.timeout,
})

// 将技术性错误转换为用户友好的提示
const friendlyErrorMessage = (message: string): string => {
  // 网络/IO错误
  if (message.includes('I/O error') || message.includes('GOAWAY') || message.includes('Connection refused')) {
    return 'AI服务暂时不可用，请稍后重试'
  }
  // 超时错误
  if (message.includes('timeout') || message.includes('Timeout')) {
    return '请求超时，请稍后重试'
  }
  // API调用失败
  if (message.includes('调用失败') && message.length > 50) {
    // 截取前面的描述部分
    const colonIndex = message.indexOf(':')
    if (colonIndex > 0 && colonIndex < 30) {
      return message.substring(0, colonIndex) + '，请稍后重试'
    }
    return '服务调用失败，请稍后重试'
  }
  // 网络错误
  if (message.includes('Network Error')) {
    return '网络连接失败，请检查网络'
  }
  return message
}

// Request interceptor - Add JWT token
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

// Response interceptor - Handle Result wrapper & errors
api.interceptors.response.use(
  (response) => {
    const { code, data, message } = response.data as Result<unknown>

    if (code === 200) {
      return data as any // Extract data from Result wrapper
    }

    // Business error - 转换为友好提示
    const friendlyMsg = friendlyErrorMessage(message || '操作失败')
    console.error('[API Error]', message)
    // @ts-expect-error window.$message is provided by Naive UI
    window.$message?.error(friendlyMsg)
    return Promise.reject(new Error(friendlyMsg))
  },
  (error: AxiosError) => {
    // HTTP error
    if (error.response?.status === 401) {
      // Token expired or invalid
      console.error('[Auth Error] Token invalid, redirecting to login')
      localStorage.removeItem('token')
      router.push('/login')
    } else {
      const message = error.message || '网络错误'
      const friendlyMsg = friendlyErrorMessage(message)
      console.error('[Network Error]', message)
      // @ts-expect-error window.$message is provided by Naive UI
      window.$message?.error(friendlyMsg)
    }
    return Promise.reject(error)
  }
)

export default api
