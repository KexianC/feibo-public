import request from '@/api/interceptor.ts'

export const generateGeoQueries = (data) => {
  return request.post('/geo/queries/generate', data)
}

export const addGeoQuery = (data) => {
  return request.post('/geo/queries', data)
}

export const createGeoWebsiteMonitor = (data) => {
  return request.post('/geo/websites', data)
}

export const getGeoWebsiteMonitorPage = (params) => {
  return request.get('/geo/websites/page', { params })
}

export const updateGeoWebsiteMonitor = (id, data) => {
  return request.put(`/geo/websites/${id}`, data)
}

export const runGeoWebsiteCheck = (id) => {
  return request.post(`/geo/websites/${id}/checks`, null)
}

export const getGeoWebsiteCheckPage = (id, params) => {
  return request.get(`/geo/websites/${id}/checks/page`, { params })
}

export const getGeoWebsiteReport = (id) => {
  return request.get(`/geo/websites/${id}/report`)
}

export const updateGeoQuery = (id, data) => {
  return request.put(`/geo/queries/${id}`, data)
}

export const getGeoQueryPage = (params) => {
  return request.get('/geo/queries/page', { params })
}

export const runGeoMonitor = (data = {}) => {
  return request.post('/geo/monitor/run', data)
}

export const getGeoMonitorRecordPage = (params) => {
  return request.get('/geo/monitor/records/page', { params })
}

export const getGeoMonitorTaskPage = (params) => {
  return request.get('/geo/monitor/tasks/page', { params })
}

export const getGeoPlatformMatrix = (params) => {
  return request.get('/geo/analytics/platform-matrix', { params })
}

export const getGeoTrend = (params) => {
  return request.get('/geo/analytics/trends', { params })
}

export const getGeoSourceAnalysis = (params) => {
  return request.get('/geo/analytics/sources', { params })
}

export const runGeoProbes = (data = {}) => {
  return request.post('/geo/probes/run', data)
}

export const getGeoProbePage = (params) => {
  return request.get('/geo/probes/page', { params })
}

export const getGeoSourceAssetPage = (params) => {
  return request.get('/geo/sources/assets/page', { params })
}

export const rebuildGeoSourceAssets = (limit = 1000) => {
  return request.post('/geo/sources/assets/rebuild', null, { params: { limit } })
}

export const getGeoSiteRecommendationPage = (params) => {
  return request.get('/geo/site-recommendations/page', { params })
}

export const updateGeoSiteRecommendationStatus = (id, data) => {
  return request.put(`/geo/site-recommendations/${id}/status`, data)
}

export const generateGeoFeedback = (data = {}) => {
  return request.post('/geo/feedback/generate', data)
}

export const getGeoFeedbackPage = (params) => {
  return request.get('/geo/feedback/page', { params })
}

export const getGeoFeedbackDetail = (id) => request.get(`/geo/feedback/${id}`)
export const updateGeoFeedbackHandling = (id, data) => request.put(`/geo/feedback/${id}/handling`, data)
export const confirmGeoFeedbackPublication = (id, data) => request.post(`/geo/feedback/${id}/external-publication`, data)
export const requestGeoFeedbackRetest = (id) => request.post(`/geo/feedback/${id}/retest`)

export const auditArticleCompliance = (articleId) => {
  return request.post('/geo/compliance/auditArticle', null, { params: { articleId } })
}

export const auditTextCompliance = (data) => {
  return request.post('/geo/compliance/auditText', data)
}
