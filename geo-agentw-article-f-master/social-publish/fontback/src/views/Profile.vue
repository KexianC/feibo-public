<template>
  <div class="profile-page">
    <div class="profile-card">
      <div class="profile-header">
        <h2><el-icon><User /></el-icon> 个人设置</h2>
      </div>

      <el-tabs v-model="activeTab" class="profile-tabs" tab-position="left">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-form :model="basicForm" label-width="100px" class="profile-form">
            <el-form-item label="头像">
              <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="handleAvatarUpload">
                <el-avatar :size="72" :src="basicForm.avatar" />
                <el-button link type="primary" style="margin-left: 16px;">更换头像</el-button>
              </el-upload>
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="basicForm.username" disabled />
            </el-form-item>
            <el-form-item label="真实姓名">
              <el-input v-model="basicForm.realName" placeholder="请输入真实姓名" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="basicForm.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="basicForm.phone" placeholder="请输入手机号" />
            </el-form-item>
            <el-form-item label="角色">
              <el-tag type="primary" size="small">{{ basicForm.role }}</el-tag>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveBasicInfo" :loading="savingBasic">保存修改</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 修改密码 -->
        <el-tab-pane label="修改密码" name="password">
          <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px" class="profile-form">
            <el-form-item label="当前密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="changePassword" :loading="changingPwd">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 偏好设置 -->
        <el-tab-pane label="偏好设置" name="preference">
          <el-form label-width="120px" class="profile-form">
            <el-form-item label="默认文章风格">
              <el-select v-model="preference.defaultStyle" style="width: 200px;">
                <el-option label="专业科普" value="专业科普" />
                <el-option label="轻松幽默" value="轻松幽默" />
                <el-option label="病例分享" value="病例分享" />
                <el-option label="热点解读" value="热点解读" />
              </el-select>
            </el-form-item>
            <el-form-item label="默认分发平台">
              <el-checkbox-group v-model="preference.defaultPlatforms">
                <el-checkbox label="小红书">小红书</el-checkbox>
                <el-checkbox label="知乎">知乎</el-checkbox>
                <el-checkbox label="微信公众号">微信公众号</el-checkbox>
                <el-checkbox label="抖音">抖音</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item label="通知设置">
              <el-switch v-model="preference.notification" active-text="开启" inactive-text="关闭" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="savePreference">保存偏好</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const activeTab = ref('basic')
const savingBasic = ref(false)
const changingPwd = ref(false)
const passwordFormRef = ref(null)

// 基本信息
const basicForm = reactive({
  avatar: 'https://picsum.photos/100/100?random=avatar',
  username: 'admin',
  realName: '张管理',
  email: 'admin@example.com',
  phone: '13800138000',
  role: '系统管理员'
})

// 密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
  } else {
    callback()
  }
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// 偏好设置
const preference = reactive({
  defaultStyle: '专业科普',
  defaultPlatforms: ['小红书', '知乎'],
  notification: true
})

const handleAvatarUpload = (file) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    basicForm.avatar = e.target.result
  }
  reader.readAsDataURL(file.raw)
  ElMessage.success('头像已更新')
}

const saveBasicInfo = () => {
  savingBasic.value = true
  setTimeout(() => {
    savingBasic.value = false
    ElMessage.success('基本信息已保存')
  }, 600)
}

const changePassword = () => {
  passwordFormRef.value?.validate(async (valid) => {
    if (valid) {
      changingPwd.value = true
      setTimeout(() => {
        changingPwd.value = false
        ElMessage.success('密码修改成功，请重新登录')
        passwordForm.oldPassword = ''
        passwordForm.newPassword = ''
        passwordForm.confirmPassword = ''
        passwordFormRef.value?.resetFields()
      }, 800)
    } else {
      ElMessage.warning('请正确填写密码信息')
    }
  })
}

const savePreference = () => {
  ElMessage.success('偏好设置已保存')
}
</script>

<style scoped>
.profile-page {
  max-width: 800px;
  margin: 0 auto;
}
.profile-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.profile-header h2 {
  margin: 0 0 16px 0;
  font-size: 18px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1a1d29;
}
.profile-tabs {
  min-height: 400px;
}
.profile-form {
  padding: 10px 0;
}
</style>