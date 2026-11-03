import request from '@/api/interceptor.ts'

export const addDepartment = (department) => {
    return request.post('/department/addDepartment', department)
}

export const getDepartmentPage = (params) => {
    return request.get('/department/page', { params });
};

export const deleteDepartment = (id) =>{
    return request.delete('/department/deleteDepartment', { params: { id } })
}

export const getDepartmentStatistics = (params) => {
    return request.get('/department/statistics', {params})
}

export const editDepartment = (data) => {
    return request.post('/department/editDepartment', data)
}

export const toggleDepartmentStatue = (id,statue)=>{
    return request.post('/department/toggleStatue', null, { params: { id, statue } })
}

export const getDepartmentAll = (hospitalId) => {
    return request.get(`/department/all?id=${hospitalId}`)
};
