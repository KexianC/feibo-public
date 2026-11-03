import request from "@/api/interceptor.ts";

export const addHospital = (data) => {
    return request.post('/hospital/addHospital', data)
}

export const deleteHospital = (id) => {
    return request.delete('/hospital/deleteHospital', { params: { id } })
}

export const getHospitalPage = (params) => {
    return request.get('/hospital/page', {params})
}

export const editHospital = (data) => {
    return request.post('/hospital/editHospital', data)
}

export const getHospitalStatistics = (params) => {
    return request.get('/hospital/statistics', {params})
}

export const toggleHospitalStatue = (id,statue)=>{
    return request.post('/hospital/toggleStatue', null, { params: { id, statue } })
}

export const getHospitalAll = () => {
    return request.get('/hospital/all')
};
