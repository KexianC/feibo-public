<template>
  <div class="account-management-page">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon stat-icon-total">
          <el-icon :size="24"><User /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ totalCount }}</span>
          <span class="stat-label">总账号数</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon stat-icon-connected">
          <el-icon :size="24"><CircleCheck /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ connectedCount }}</span>
          <span class="stat-label">已连接</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon stat-icon-pending">
          <el-icon :size="24"><Clock /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ pendingCount }}</span>
          <span class="stat-label">待验证</span>
        </div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <div class="action-left">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索平台或账号..."
          :prefix-icon="Search"
          clearable
          class="filter-input"
        />
        <el-select
          v-model="platformFilter"
          placeholder="筛选平台"
          clearable
          class="filter-select"
        >
          <el-option label="全部平台" value="" />
          <el-option label="知乎" value="知乎" />
          <el-option label="小红书" value="小红书" />
          <el-option label="今日头条" value="今日头条" />
          <el-option label="百家号" value="百家号" />
        </el-select>
      </div>
      <div class="action-right">
        <el-button type="primary" :icon="Plus" @click="openAddDialog">添加账号</el-button>
        <el-button :icon="RefreshRight" @click="refreshAllAccounts" :loading="isRefreshing">刷新状态</el-button>
      </div>
    </div>

    <!-- 账号卡片网格 -->
    <div class="account-grid" v-loading="loading">
      <div
        class="account-card"
        v-for="account in filteredAccounts"
        :key="account.id"
        :class="{ disconnected: account.status === 0 }"
      >
        <div class="card-top">
          <div class="platform-badge" :style="{ background: platformColor(account.platform) }">
            {{ account.platform.charAt(0) }}
          </div>
          <div class="account-status">
            <span class="status-dot" :class="statusClass(account.status)"></span>
            <span class="status-text">{{ statusText(account.status) }}</span>
          </div>
          <el-dropdown trigger="click" class="card-menu">
            <el-button :icon="MoreFilled" circle size="small" text />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleReLogin(account)">重新登录</el-dropdown-item>
                <el-dropdown-item divided @click="handleDeleteAccount(account)" style="color: #ef4444;">
                  删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="card-body">
          <div class="account-avatar">
            <el-avatar :size="52" :src="account.avatar || getDefaultAvatar(account.accountName)" />
          </div>
          <div class="account-info">
            <span class="account-name">{{ account.accountName }}</span>
            <span class="account-platform">{{ account.platform }}</span>
          </div>
        </div>
        <div class="card-footer">
          <el-tag
            :type="account.status == 1 ? 'success' : account.status == 2 ? 'warning' : 'info'"
            size="small"
            round
            effect="light"
          >
            {{ account.status == 1 ? '可用' : account.status == 2 ? '验证中' : '异常' }}
          </el-tag>
        </div>
      </div>

      <!-- 添加账号空位 -->
      <div class="account-card add-card" @click="openAddDialog">
        <el-icon :size="40" color="#c0c4cc"><Plus /></el-icon>
        <span>添加新账号</span>
      </div>
    </div>

    <!-- 添加账号对话框（仅添加模式，无编辑） -->
    <el-dialog
      v-model="showDialog"
      title="添加新账号"
      width="480px"
      center
      :close-on-click-modal="false"
      @close="closeDialog"
    >
      <div v-if="!sseConnecting">
        <el-form :model="accountForm" label-width="90px" label-position="left">
          <el-form-item label="平台选择" required>
            <el-select v-model="accountForm.type" placeholder="请选择平台" style="width: 100%;">
              <el-option label="知乎" :value="1" />
              <el-option label="小红书" :value="2" />
              <el-option label="今日头条" :value="3" />
              <el-option label="百家号" :value="4" />
            </el-select>
          </el-form-item>
          <el-form-item label="账号名称" required>
            <el-input v-model="accountForm.userName" placeholder="输入账号名称" />
          </el-form-item>
        </el-form>
      </div>
      <div v-else class="qr-code-container">
        <div v-if="qrCodeData && !loginStatus" class="qr-code-wrapper">
          <p class="qr-tip">请使用 {{ platformMap[accountForm.type] }} APP 扫描二维码登录</p>
          <img :src="qrCodeData" class="qr-code-img" alt="登录二维码" />
        </div>
        <div v-else-if="!qrCodeData && !loginStatus" class="loading-wrapper">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>请求二维码中...</span>
        </div>
        <div v-else-if="loginStatus === '200'" class="status-wrapper success">
          <el-icon><CircleCheck /></el-icon>
          <span>登录成功</span>
        </div>
        <div v-else-if="loginStatus === '500'" class="status-wrapper error">
          <el-icon><CircleClose /></el-icon>
          <span>登录失败，请重试</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="closeDialog" :disabled="sseConnecting">取消</el-button>
        <el-button
          type="primary"
          @click="submitAccountForm"
          :loading="saving"
          :disabled="(!accountForm.type || !accountForm.userName) && !sseConnecting"
        >
          {{ sseConnecting ? '登录中...' : '确认' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  Search,
  Plus,
  MoreFilled,
  User,
  CircleCheck,
  Clock,
  RefreshRight,
  Loading,
  CircleClose
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { EventSourcePolyfill } from 'event-source-polyfill'
import { getAccounts, deleteAccount, verifyAccount } from '@/api/AccountManagementApi.js'
import { useTokenStore } from '@/stores/TokenStore.js'
import {
  GEO_API_BASE,
  USE_HOST_AUTH,
  getClientFingerprint,
  getHostAccessToken,
} from '@/config/integration'

// 平台映射（与后端 type 对应）
const platformMap = {
  1: '知乎',
  2: '小红书',
  3: '今日头条',
  4: '百家号'
}

// 状态映射：1-正常，2-验证中，0-异常
const statusMap = {
  1: '已连接',
  2: '待验证',
  0: '已断开'
}

// ---------- 数据 ----------
const accounts = ref([])
const loading = ref(false)
const isRefreshing = ref(false)

// 搜索/筛选
const searchKeyword = ref('')
const platformFilter = ref('')

// 对话框相关（仅添加）
const showDialog = ref(false)
const accountForm = ref({ type: null, userName: '' })
const saving = ref(false)

// SSE 相关
const sseConnecting = ref(false)
const qrCodeData = ref('')
const loginStatus = ref('')
let eventSource = null
let loginSuccessTimer = null
let reconnectTimer = null
let disposed = false

// ---------- 辅助计算属性 ----------
const filteredAccounts = computed(() => {
  let list = accounts.value
  const kw = searchKeyword.value.toLowerCase()
  if (kw) {
    list = list.filter(a =>
      a.accountName?.toLowerCase().includes(kw) ||
      a.platform?.toLowerCase().includes(kw)
    )
  }
  if (platformFilter.value) {
    list = list.filter(a => a.platform === platformFilter.value)
  }
  return list
})

const totalCount = computed(() => accounts.value.length)
const connectedCount = computed(() => accounts.value.filter(a => a.status == 1).length)
const pendingCount = computed(() => accounts.value.filter(a => a.status == 2).length)

// ---------- 辅助函数 ----------
const platformColor = (platform) => {
  const map = {
    '知乎': 'linear-gradient(135deg, #0084ff, #0066cc)',
    '小红书': 'linear-gradient(135deg, #ff6b6b, #ee5a24)',
    '今日头条': 'linear-gradient(135deg, #ff4444, #cc0000)',
    '百家号': 'linear-gradient(135deg, #00bcd4, #0097a7)'
  }
  return map[platform] || '#999'
}

const statusClass = (status) => {
  return {
    1: 'dot-green',
    2: 'dot-orange',
    0: 'dot-gray'
  }[status] || 'dot-gray'
}

const statusText = (status) => statusMap[status] || '未知'

const getDefaultAvatar = (name) => `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=random`

// 关闭 SSE 连接
const closeSSEConnection = () => {
  if (loginSuccessTimer) {
    clearTimeout(loginSuccessTimer)
    loginSuccessTimer = null
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (eventSource) {
    try {
      eventSource.close()
    } catch (e) {}
    eventSource = null
  }
  sseConnecting.value = false
}

// 建立 SSE 连接（用于添加账号）
const connectSSE = (type, username) => {
  if (disposed) return
  closeSSEConnection()
  sseConnecting.value = true
  qrCodeData.value = ''
  loginStatus.value = ''

  const url = `${GEO_API_BASE}/account/login?type=${type}&username=${encodeURIComponent(username)}`
  const tokenStore = USE_HOST_AUTH ? null : useTokenStore()
  const headers = USE_HOST_AUTH
    ? {
        Authorization: `Bearer ${getHostAccessToken()}`,
        'client-fingerprint': getClientFingerprint(),
      }
    : tokenStore?.token
      ? { 'X-CSRF-Token': tokenStore.token }
      : {}

  eventSource = new EventSourcePolyfill(url, {
    heartbeatTimeout: 300000,
    withCredentials: true,
    headers
  })

  eventSource.onmessage = (event) => {
    if (disposed) return
    const data = event.data
    console.log('SSE 消息:', data)

    if (data.length > 100) {
      // 二维码图片数据
      if (data.startsWith('data:image')) {
        qrCodeData.value = data
      } else {
        qrCodeData.value = `data:${data}`
      }
    } else if (data === '200' || data === '500') {
      loginStatus.value = data

      if (data === '200') {
        ElMessage.success('登录成功')
        loginSuccessTimer = setTimeout(async () => {
          loginSuccessTimer = null
          if (disposed) return
          closeSSEConnection()
          await loadAccounts()
          if (disposed) return
          showDialog.value = false
        }, 1000)
      } else if (data === '500') {
        ElMessage.error('登录超时或失败')
        closeSSEConnection()
      }
    }
  }

  eventSource.onerror = (error) => {
    if (disposed) return
    console.error('SSE 错误:', error)
    ElMessage.error('连接服务器失败，请稍后重试')
    closeSSEConnection()
  }
}

// 刷新所有账号状态
const refreshAllAccounts = async () => {
  isRefreshing.value = true
  const loadingMsg = ElMessage.info('正在验证所有账号状态...')
  try {
    for (const acc of accounts.value) {
      if (acc.id) {
        try {
          await verifyAccount(acc.id)
        } catch {
          ElMessage.error("刷新失败")
        }
      }
    }
    ElMessage.success('状态刷新完成')
  } catch (error) {
    ElMessage.error('刷新失败')
  } finally {
    await loadAccounts()
    isRefreshing.value = false
    loadingMsg.close()
  }
}

// 加载账号列表
const loadAccounts = async () => {
  loading.value = true
  try {
    const res = await getAccounts()
    if (disposed) return
    if (res.code == 200) {
      const data = res.data || []
      accounts.value = data.map(item => ({
        id: item.id,
        platform: platformMap[item.type] || '未知',
        accountName: item.userName,
        avatar: item.avatar || '',
        status: item.status, // 1正常 2验证中 0异常
        filePath: item.filePath,
        type: item.type,
        addTime: item.addTime || item.createdTime,
        followers: item.followers || '0'
      }))
    } else {
      ElMessage.error(res.msg || '加载账号列表失败')
    }
  } catch (error) {
    if (disposed || error?.code === 'ERR_CANCELED') return
    console.error('加载账号失败', error)
    ElMessage.error('加载账号失败')
  } finally {
    if (!disposed) loading.value = false
  }
}

// 重新登录（用于异常账号）
const handleReLogin = async (account) => {
  accountForm.value = {
    type: account.type,
    userName: account.accountName
  }
  const res = await deleteAccount(account.id)
  if(res.code == 200){
    ElMessage.success("已删除异常账号，正在新建账号...")
  }else{
    ElMessage.error("异常账号删除失败，请重试！")
  }
  showDialog.value = true
  // 延迟启动 SSE
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    if (disposed) return
    connectSSE(account.type, account.accountName)
  }, 300)
}

// 删除账号
const handleDeleteAccount = async (account) => {
  try {
    await ElMessageBox.confirm(`确定删除「${account.accountName}」吗？此操作不可恢复。`, '删除确认', {
      type: 'error',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    const res = await deleteAccount(account.id)
    if (res.code == 200) {
      ElMessage.success('账号已删除')
      await loadAccounts()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') console.error(error)
  }
}

// 提交添加账号
const submitAccountForm = async () => {
  if (sseConnecting.value) return
  if (!accountForm.value.type || !accountForm.value.userName) {
    ElMessage.warning('请填写平台和账号名称')
    return
  }
  connectSSE(accountForm.value.type, accountForm.value.userName)
}

// 关闭对话框
const closeDialog = () => {
  if (sseConnecting.value) {
    closeSSEConnection()
  }
  showDialog.value = false
  accountForm.value = { type: null, userName: '' }
  qrCodeData.value = ''
  loginStatus.value = ''
}

// 打开添加对话框
const openAddDialog = () => {
  accountForm.value = { type: null, userName: '' }
  showDialog.value = true
  closeSSEConnection()
  qrCodeData.value = ''
  loginStatus.value = ''
}

// 生命周期
onMounted(() => {
  disposed = false
  loadAccounts()
})

onUnmounted(() => {
  disposed = true
  closeSSEConnection()
})
</script>
<style scoped>
/* 原样式保持不变，仅补充新增弹窗内部样式 */
.account-management-page {
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
  box-sizing: border-box;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.stat-card {
  min-height: 88px;
  background: #fff;
  border-radius: 12px;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f2f5;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon-total {
  background: #e8f4fd;
  color: #4f6ef7;
}

.stat-icon-connected {
  background: #e6f9ee;
  color: #22c55e;
}

.stat-icon-pending {
  background: #fff7e6;
  color: #f59e0b;
}

.stat-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1a1d29;
}

.stat-label {
  font-size: 13px;
  color: #8b8fa3;
  margin-top: 4px;
}

.action-bar {
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f2f5;
}

.action-left {
  flex: 1;
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.filter-input {
  width: 220px;
}

.filter-select {
  width: 150px;
}

.action-right {
  flex-shrink: 0;
  display: flex;
  gap: 8px;
}

.account-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.account-card {
  background: #fff;
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: all 0.3s;
  cursor: default;
  border: 1px solid #f0f2f5;
}

.account-card:hover {
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.account-card.disconnected {
  opacity: 0.55;
}

.card-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.platform-badge {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  flex-shrink: 0;
}

.account-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8b8fa3;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.dot-green {
  background: #22c55e;
  box-shadow: 0 0 6px rgba(34, 197, 94, 0.5);
}

.dot-orange {
  background: #f59e0b;
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.5);
}

.dot-gray {
  background: #bbb;
}

.card-menu {
  margin-left: auto;
}

.card-body {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.account-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.account-name {
  font-weight: 600;
  font-size: 15px;
  color: #1a1d29;
}

.account-platform {
  font-size: 12px;
  color: #8b8fa3;
}

.account-fans {
  font-size: 12px;
  color: #4f6ef7;
  font-weight: 500;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

.add-time {
  font-size: 11px;
  color: #bbb;
}

.add-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 100%;
  min-height: 200px;
  border: 2px dashed #e0e3e8;
  background: #fafbfc;
  cursor: pointer;
  color: #c0c4cc;
  font-size: 14px;
  transition: all 0.3s;
}

.add-card:hover {
  border-color: #4f6ef7;
  color: #4f6ef7;
  background: #f8f9ff;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 4px 0;
}

.qr-code-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 250px;
}

.qr-code-wrapper {
  text-align: center;
}

.qr-tip {
  margin-bottom: 16px;
  color: #606266;
}

.qr-code-img {
  width: 180px;
  height: 180px;
  border: 1px solid #dcdfe6;
  padding: 8px;
  background: white;
}

.loading-wrapper, .status-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}

.status-wrapper.success {
  color: #67c23a;
}

.status-wrapper.error {
  color: #f56c6c;
}

.captcha-error {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 8px;
}

@media (max-width: 1200px) {
  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .action-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .action-right {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }

  .filter-input,
  .filter-select {
    width: 100%;
  }

  .action-left {
    flex-direction: column;
    align-items: stretch;
  }

  .account-grid {
    grid-template-columns: 1fr;
  }

  .pagination-wrap {
    justify-content: center;
    overflow-x: auto;
  }
}
</style>
