// API配置文件
export const API_CONFIG = {
  BASE_URL: 'https://api.hzmwmt.com',
  TIMEOUT: 10000,
  ENDPOINTS: {
    LOGIN: '/api/v1/auth/login/password',
    LOGOUT: '/api/v1/auth/logout',
    REFRESH_TOKEN: '/api/v1/auth/refresh/rotate',
    USER_INFO: '/api/admin/user/current', // 修改为正确的管理员用户信息端点
  }
};
