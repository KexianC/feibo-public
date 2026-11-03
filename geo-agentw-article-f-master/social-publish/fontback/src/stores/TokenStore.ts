import { defineStore } from "pinia";
import { computed, ref } from 'vue';

const CSRF_STORAGE_KEY = 'csrfToken';
const USER_STORAGE_KEY = 'loginUser';
const REMEMBER_STORAGE_KEY = 'rememberLogin';

const readRemember = () => localStorage.getItem(REMEMBER_STORAGE_KEY) === 'true';
const storage = () => (readRemember() ? localStorage : sessionStorage);

const readUser = () => {
    const raw = storage().getItem(USER_STORAGE_KEY) || localStorage.getItem(USER_STORAGE_KEY) || sessionStorage.getItem(USER_STORAGE_KEY)
    if (!raw) return {}
    try {
        return JSON.parse(raw)
    } catch {
        return {}
    }
}

export const useTokenStore = defineStore('token', () => {
    const remembered = ref(readRemember())
    const token = ref(storage().getItem(CSRF_STORAGE_KEY) || localStorage.getItem(CSRF_STORAGE_KEY) || sessionStorage.getItem(CSRF_STORAGE_KEY) || '')
    const storedUser = readUser()
    const userId = ref(storedUser.userId || null)
    const username = ref(storedUser.username || '')
    const displayName = ref(storedUser.displayName || '')
    const role = ref(storedUser.role || '')
    const isAdmin = computed(() => role.value === 'ADMIN')

    const clearStorage = () => {
        localStorage.removeItem(CSRF_STORAGE_KEY)
        localStorage.removeItem(USER_STORAGE_KEY)
        sessionStorage.removeItem(CSRF_STORAGE_KEY)
        sessionStorage.removeItem(USER_STORAGE_KEY)
    }

    const persist = () => {
        clearStorage()
        const target = remembered.value ? localStorage : sessionStorage
        if (token.value) {
            target.setItem(CSRF_STORAGE_KEY, token.value)
        }
        if (username.value || role.value) {
            target.setItem(USER_STORAGE_KEY, JSON.stringify({
                userId: userId.value,
                username: username.value,
                displayName: displayName.value,
                role: role.value
            }))
        }
        localStorage.setItem(REMEMBER_STORAGE_KEY, remembered.value ? 'true' : 'false')
    }

    const setToken = (newToken) => {
        token.value = newToken || ''
        persist()
    }

    const setSession = (data, remember = remembered.value) => {
        remembered.value = Boolean(remember)
        token.value = data?.csrfToken || ''
        userId.value = data?.userId || null
        username.value = data?.username || ''
        displayName.value = data?.displayName || ''
        role.value = data?.role || ''
        persist()
    }

    const setUser = (data) => {
        userId.value = data?.userId || null
        username.value = data?.username || ''
        displayName.value = data?.displayName || ''
        role.value = data?.role || ''
        if (data?.csrfToken) {
            token.value = data.csrfToken
        }
        persist()
    }

    const removeToken = () => {
        token.value = ''
        userId.value = null
        username.value = ''
        displayName.value = ''
        role.value = ''
        remembered.value = false
        clearStorage()
        localStorage.removeItem(REMEMBER_STORAGE_KEY)
    }

    return {
        token,
        csrfToken: token,
        userId,
        username,
        displayName,
        role,
        isAdmin,
        remembered,
        setToken,
        setSession,
        setUser,
        removeToken
    }
})
