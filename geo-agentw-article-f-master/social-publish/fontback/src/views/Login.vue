<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <div class="logo-section">
          <div class="logo-icon">
            <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="40" height="40" rx="10" fill="url(#logo-grad)" />
              <path d="M12 20L18 26L28 14" stroke="white" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round" />
              <defs>
                <linearGradient id="logo-grad" x1="0" y1="0" x2="40" y2="40">
                  <stop offset="0%" stop-color="#4f6ef7" />
                  <stop offset="100%" stop-color="#7b5cf5" />
                </linearGradient>
              </defs>
            </svg>
          </div>
          <span class="system-name">Medtalent GEO平台</span>
        </div>
        <p class="welcome-text">欢迎回来，请登录您的账号</p>
      </div>

      <el-form :model="loginForm" :rules="rules" ref="loginFormRef" label-position="top" size="large">
        <el-form-item label="账号" prop="username">
          <el-input v-model="loginForm.username" placeholder="请输入用户名或邮箱" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <div class="form-extra">
          <el-checkbox v-model="rememberMe">记住我</el-checkbox>
          <el-button link type="primary">忘记密码？</el-button>
        </div>
        <el-button type="primary" native-type="submit" class="login-btn" :loading="logging" @click.prevent="handleLogin">
          登 录
        </el-button>
      </el-form>

      <div class="login-footer">
        <span>还没有账号？</span>
        <el-button link type="primary" @click="$emit('go-register')">立即注册</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {Login} from "@/api/LoginApi.js";
import router from "@/router/index.js";

const emit = defineEmits(['go-register', 'login-success'])

const loginFormRef = ref(null)
const logging = ref(false)
const rememberMe = ref(false)

const loginForm = reactive({
  username: 'admin',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码长度至少6位', trigger: 'blur' }]
}
import {useTokenStore} from "@/stores/TokenStore.js";
const handleLogin = async () => {
  if (!loginFormRef.value) return
  logging.value = true
  try {
    const result = await Login({ ...loginForm, rememberMe: rememberMe.value })
    if(result.success && result.data?.csrfToken){
      ElMessage.success("登录成功！")
      const useToken = useTokenStore();
      useToken.setSession(result.data, rememberMe.value)
      router.push('/')
    }
  } catch {
    ElMessage.warning('请正确填写账号和密码')
  } finally {
    logging.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
.login-card {
  width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 40px 36px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
}
.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.logo-section {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 12px;
}
.logo-icon svg {
  width: 40px;
  height: 40px;
}
.system-name {
  font-size: 22px;
  font-weight: 700;
  color: #1a1d29;
  letter-spacing: 0.5px;
}
.welcome-text {
  color: #8b8fa3;
  font-size: 14px;
  margin: 0;
}
.form-extra {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 2px;
}
.login-footer {
  margin-top: 20px;
  text-align: center;
  color: #8b8fa3;
  font-size: 14px;
}
</style>
