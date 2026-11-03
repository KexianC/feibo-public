import request from '@/api/interceptor.ts'

export const Login = (data) => {
    return request.post('/login', {
        username: data.username,
        password: data.password,
        rememberMe: data.rememberMe
    })
}

export const LoginStatus = () => {
    return request.get('/login/status')
}

export const Logout = () => {
    return request.post('/login/logout')
}
