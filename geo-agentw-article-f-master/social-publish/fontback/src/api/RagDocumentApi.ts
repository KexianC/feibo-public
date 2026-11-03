import request from '@/api/interceptor.ts'

export const convertDocument = (documentId) => {
    return request.post('/rag/documents/convert', null, { params: { documentId } });
}

export const deleteVectorAndDocument = (documentId) => {
    return request.delete('/rag/documents/vector', { params: { documentId } });
}

export const searchVector = (params) => {
    return request.get('/rag/search', { params });
}

export const getVectorPage = (params) => {
    return request.get('/rag/documents/page', { params })
}
