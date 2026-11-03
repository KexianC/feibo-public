import request from '@/api/interceptor.ts'

export const addTitle = (title) => {
    return request.post('/title/addTitle', title)
}

export const getTitlePage = (params) => {
    return request.get('/title/page', { params });
};

export const deleteTitle = (id) =>{
    return request.delete('/title/deleteTitle', { params: { id } })
}

export const getTitleStatistics = (params) => {
    return request.get('/title/statistics', {params})
}

export const editTitle = (data) => {
    return request.post('/title/editTitle', data)
}

export const toggleTitleStatue = (id,statue)=>{
    return request.post('/title/toggleStatue', null, { params: { id, statue } })
}

export const getTitleAll = () => {
    return request.get('/title/all')
};
