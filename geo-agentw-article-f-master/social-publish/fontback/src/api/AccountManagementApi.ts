import request from '@/api/interceptor.ts'

export const getAccounts = () =>{
    return request.get('/account/getAccounts')
}

export const verifyAccount = (id)=>{
    return request.post('/account/verifyAccount', null, { params: { id } })
}

export const deleteAccount = (id) => {
    return request.delete('/account/deleteAccount', { params: { id } })
}
