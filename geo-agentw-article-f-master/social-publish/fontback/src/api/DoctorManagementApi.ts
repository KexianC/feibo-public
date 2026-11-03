import request from '@/api/interceptor.ts'

export const newDoctor = (doctor) => {
    return request.post('/doctor/addDoctor', doctor)
}

/**
 * 分页查询医生信息
 * @param {Object} params - 查询参数
 * @param {number} params.pageNum - 页码（默认1）
 * @param {number} params.pageSize - 每页条数（默认20）
 * @param {string} [params.doctorName] - 医生姓名（可选）
 * @param {number} [params.hospital] - 医院ID（可选）
 * @param {number} [params.department] - 科室ID（可选）
 * @param {number} [params.title] - 职称ID（可选）
 * @param {number} [params.statue] - 启用状态（可选）
 * @returns {Promise} 返回包含分页结果的Promise
 */
export const getDoctorPage = (params) => {
    return request.get('/doctor/page', { params });
};

export const getDoctorPageOther = (params) => {
    return request.get('/doctor/pageOther', { params });
};

export const deleteDoctor = (id) =>{
    return request.delete('/doctor/deleteDoctor', { params: { id } })
}

export const getDoctorStatistics = (params) => {
    return request.get('/doctor/statistics', {params})
}

export const editDoctor = (data) => {
    return request.post('/doctor/editDoctor', data)
}

export const toggleDoctorStatue = (id,statue)=>{
    return request.post('/doctor/toggleStatue', null, { params: { id, statue } })
}

export const getDoctorAll = () => {
    return request.get('/doctor/all')
};
