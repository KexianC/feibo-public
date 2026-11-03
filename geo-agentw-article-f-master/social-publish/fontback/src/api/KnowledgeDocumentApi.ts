import request from '@/api/interceptor.ts'

export const uploadDocument = (formData) => {
    return request.post(`/document/upload`, formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
}

// export const deleteDocument = (id) => {
//     return request.delete('/document/deleteDocument', { params: { id } })
// }

export const getDocumentPage = (params) => {
    return request.get('/document/page', { params });
};
