<template>
  <div class="register-page">
    <div class="register-card">
      <div class="register-header">
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
          <span class="system-name">创建新账号</span>
        </div>
      </div>

      <el-form :model="registerForm" :rules="rules" ref="registerFormRef" label-position="top" size="large">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="registerForm.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="registerForm.email" placeholder="请输入邮箱" :prefix-icon="Message" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="registerForm.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="registerForm.confirmPassword" type="password" placeholder="请再次输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" class="register-btn" :loading="registering" @click="handleRegister">
          注 册
        </el-button>
      </el-form>

      <div class="register-footer">
        <span>已有账号？</span>
        <el-button link type="primary" @click="$emit('go-login')">返回登录</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { User, Message, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['go-login', 'register-success'])

const registerFormRef = ref(null)
const registering = ref(false)

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 3, max: 20, message: '用户名长度3-20位', trigger: 'blur' }],
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }, { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码长度至少6位', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请确认密码', trigger: 'blur' }, { validator: validateConfirmPassword, trigger: 'blur' }]
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  try {
    await registerFormRef.value.validate()
    registering.value = true
    setTimeout(() => {
      registering.value = false
      ElMessage.success('注册成功，即将跳转到登录')
      emit('register-success', { username: registerForm.username, email: registerForm.email })
    }, 1500)
  } catch {
    ElMessage.warning('请正确填写注册信息')
  }
}
</script>

<style scoped>
.register-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
.register-card {
  width: 440px;
  background: #fff;
  border-radius: 16px;
  padding: 40px 36px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
}
.register-header {
  text-align: center;
  margin-bottom: 28px;
}
.logo-section {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}
.logo-icon svg {
  width: 40px;
  height: 40px;
}
.system-name {
  font-size: 22px;
  font-weight: 700;
  color: #1a1d29;
}
.register-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 2px;
  margin-top: 10px;
}
.register-footer {
  margin-top: 20px;
  text-align: center;
  color: #8b8fa3;
  font-size: 14px;
}
</style>