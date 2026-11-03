import axios from "axios";
import { ElMessage } from "element-plus";
import { useTokenStore } from "@/stores/TokenStore.ts";
import {
    GEO_API_BASE,
    USE_HOST_AUTH,
    getClientFingerprint,
    getHostAbortSignal,
    getHostAccessToken,
    redirectToHostLogin,
    tryRefreshHostAccessToken,
} from '@/config/integration'

const CSRF_HEADER = 'X-CSRF-Token'
const unsafeMethods = new Set(['post', 'put', 'patch', 'delete'])

const instance = axios.create({
    baseURL: GEO_API_BASE,
    timeout: 10000,
    withCredentials: true
})

instance.interceptors.request.use(
    async (config) => {
        const method = (config.method || 'get').toLowerCase()
        config.headers = config.headers || {}

        if (USE_HOST_AUTH) {
            const abortSignal = getHostAbortSignal()
            if (abortSignal) {
                config.signal = abortSignal
            }

            let accessToken = getHostAccessToken()
            if (!accessToken) {
                const recovery = await tryRefreshHostAccessToken()
                if (recovery === 'refreshed') {
                    accessToken = getHostAccessToken()
                }
                if (!accessToken) {
                    if (recovery === 'failed') {
                        redirectToHostLogin()
                    }
                    const error = new Error('飞驳登录状态已失效') as Error & { geoAuthHandled?: boolean }
                    error.geoAuthHandled = true
                    throw error
                }
            }
            ;(config.headers as any).Authorization = `Bearer ${accessToken}`
            ;(config.headers as any)['client-fingerprint'] = getClientFingerprint()
        } else if (unsafeMethods.has(method) && config.url !== '/login') {
            const tokenStore = useTokenStore()
            if (!tokenStore.token) {
                throw new Error('未登录或登录状态已过期')
            }
            ;(config.headers as any)[CSRF_HEADER] = tokenStore.token
        }
        return config
    },
    (err) => Promise.reject(err)
)

instance.interceptors.response.use(
    (result) => {
        if (String(result.data?.code) === '200' || result.data?.success === true) {
            return result.data
        }

        ElMessage.error(result.data?.message || result.data?.msg || result.data?.info || '服务异常')
        return Promise.reject(result.data)
    },
    async (err) => {
        if (axios.isCancel(err) || err.code === 'ERR_CANCELED' || err.geoAuthHandled) {
            return Promise.reject(err)
        }

        if (err.response && err.response.status === 401) {
            if (USE_HOST_AUTH) {
                const originalRequest = err.config as typeof err.config & { geoAuthRetried?: boolean }
                if (!originalRequest?.geoAuthRetried) {
                    const currentToken = getHostAccessToken()
                    const sentAuthorization = originalRequest.headers?.Authorization
                    if (currentToken && sentAuthorization !== `Bearer ${currentToken}`) {
                        originalRequest.geoAuthRetried = true
                        return instance.request(originalRequest)
                    }

                    const recovery = await tryRefreshHostAccessToken()
                    if (recovery === 'refreshed') {
                        originalRequest.geoAuthRetried = true
                        return instance.request(originalRequest)
                    }
                    if (recovery === 'detached') {
                        return Promise.reject(err)
                    }

                    const latestToken = getHostAccessToken()
                    if (latestToken && sentAuthorization !== `Bearer ${latestToken}`) {
                        originalRequest.geoAuthRetried = true
                        return instance.request(originalRequest)
                    }
                }
                if (redirectToHostLogin()) {
                    ElMessage.error('登录状态已失效')
                }
            } else {
                ElMessage.error('登录状态已失效')
                const tokenStore = useTokenStore()
                tokenStore.removeToken()
                window.location.href = '/login'
            }
        } else if (err.response && err.response.status === 403) {
            ElMessage.error('权限不足')
        } else {
            ElMessage.error('服务异常')
        }
        return Promise.reject(err)
    }
)

export default instance
