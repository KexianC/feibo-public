import request from '@/api/interceptor.ts'

export const getSystemUserPage = (params) => {
  return request.get('/system/users/page', { params })
}

export const addSystemUser = (data) => {
  return request.post('/system/users', data)
}

export const updateSystemUser = (data) => {
  return request.post('/system/users/update', data)
}

export const toggleSystemUser = (id, enableStatus) => {
  return request.post('/system/users/toggle', null, { params: { id, enableStatus } })
}

export const resetSystemUserPassword = (data) => {
  return request.post('/system/users/resetPassword', data)
}

export const getRuntimeSettings = () => {
  return request.get('/system/settings/runtime')
}

export const getSystemSettingPage = (params) => {
  return request.get('/system/settings/page', { params })
}

export const saveSystemSetting = (data) => {
  return request.post('/system/settings', data)
}

export const getOperationLogPage = (params) => {
  return request.get('/system/operationLogs/page', { params })
}
