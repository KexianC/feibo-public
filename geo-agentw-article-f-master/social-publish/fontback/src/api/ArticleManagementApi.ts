import request from '@/api/interceptor.ts'

// Article generation includes knowledge retrieval and a full LLM response. Keep
// the global timeout short for ordinary CRUD calls, but allow this endpoint to
// finish without the browser aborting a healthy request after ten seconds.
export const ARTICLE_GENERATION_TIMEOUT_MS = 120_000

export const chat = (params) => {
  return request.get('/chat', { params })
}

export const generateArticle = (data) => {
  return request.post('/article/generate', data, {
    timeout: ARTICLE_GENERATION_TIMEOUT_MS
  })
}

export const getArticlePage = (params) => {
  return request.get('/article/page', { params })
}

export const getArticleHistory = (scope) => {
  const scopeParams = typeof scope === 'object' ? scope : { doctorId: scope }
  return request.get('/article/page', {
    params: {
      pageNum: 1,
      pageSize: 20,
      ...scopeParams
    }
  })
}

export const addArticle = (data) => {
  return request.post('/article/addArticle', data)
}

export const editArticle = (data) => {
  return request.post('/article/editArticle', data)
}

export const auditArticle = (data) => {
  return request.post('/article/audit', data)
}

export const publishArticle = (data) => {
  return request.post('/article/publish', data)
}

export const deleteArticle = (id) => {
  return request.delete('/article/deleteArticle', { params: { id } })
}
