<template>
  <div class="system-workbench">
    <el-tabs v-model="activeTab" class="system-tabs">
      <el-tab-pane label="用户管理" name="users">
        <section class="panel">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-input v-model="userFilter.username" placeholder="用户名" clearable class="filter" @keyup.enter="reloadUsers" />
              <el-select v-model="userFilter.role" placeholder="角色" clearable class="filter-sm" @change="loadUsers">
                <el-option label="管理员" value="ADMIN" />
                <el-option label="运营用户" value="OPERATOR" />
              </el-select>
              <el-select v-model="userFilter.enableStatus" placeholder="状态" clearable class="filter-sm" @change="loadUsers">
                <el-option label="启用" :value="1" />
                <el-option label="禁用" :value="0" />
              </el-select>
              <el-button type="primary" :icon="Search" @click="reloadUsers">查询</el-button>
            </div>
            <el-button type="primary" :icon="Plus" @click="openUserDialog()">新增用户</el-button>
          </div>
          <el-table :data="users" stripe v-loading="userLoading" empty-text="暂无用户">
            <el-table-column prop="username" label="用户名" min-width="150" />
            <el-table-column prop="displayName" label="显示名称" min-width="150" />
            <el-table-column label="角色" width="110">
              <template #default="{ row }">
                <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'" size="small">{{ roleText(row.role) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enableStatus === 1 ? 'success' : 'info'" size="small">{{ row.enableStatus === 1 ? '启用' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最近登录" width="170">
              <template #default="{ row }">{{ formatDate(row.lastLoginTime) }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatDate(row.createdTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openUserDialog(row)">编辑</el-button>
                <el-button link type="primary" size="small" @click="openPasswordDialog(row)">重置密码</el-button>
                <el-button link :type="row.enableStatus === 1 ? 'warning' : 'success'" size="small" @click="toggleUser(row)">
                  {{ row.enableStatus === 1 ? '禁用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="userPage.pageNum"
              v-model:page-size="userPage.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="userPage.total"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="reloadUsers"
              @current-change="loadUsers"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="系统设置" name="settings">
        <section class="panel">
          <div class="section-title">运行配置</div>
          <el-table :data="runtimeSettings" stripe v-loading="runtimeLoading" empty-text="暂无配置">
            <el-table-column prop="groupName" label="分组" width="110" />
            <el-table-column prop="name" label="配置项" min-width="180" />
            <el-table-column prop="value" label="当前值" min-width="220" show-overflow-tooltip />
          </el-table>
        </section>
        <section class="panel">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-input v-model="settingFilter.settingKey" placeholder="设置键" clearable class="filter" @keyup.enter="reloadSettings" />
              <el-input v-model="settingFilter.settingGroup" placeholder="分组" clearable class="filter-sm" @keyup.enter="reloadSettings" />
              <el-button type="primary" :icon="Search" @click="reloadSettings">查询</el-button>
            </div>
            <el-button type="primary" :icon="Plus" @click="openSettingDialog()">新增设置</el-button>
          </div>
          <el-table :data="settings" stripe v-loading="settingLoading" empty-text="暂无设置">
            <el-table-column prop="settingGroup" label="分组" width="120" />
            <el-table-column prop="settingKey" label="设置键" min-width="180" />
            <el-table-column prop="settingValue" label="设置值" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.sensitive === 1 ? '******' : row.settingValue }}</template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="90" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openSettingDialog(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="settingPage.pageNum"
              v-model:page-size="settingPage.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="settingPage.total"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="reloadSettings"
              @current-change="loadSettings"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="操作日志" name="logs">
        <section class="panel">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-input v-model="logFilter.operatorName" placeholder="操作人" clearable class="filter-sm" @keyup.enter="reloadLogs" />
              <el-input v-model="logFilter.moduleName" placeholder="模块" clearable class="filter-sm" @keyup.enter="reloadLogs" />
              <el-input v-model="logFilter.actionName" placeholder="动作" clearable class="filter-sm" @keyup.enter="reloadLogs" />
              <el-input v-model="logFilter.status" placeholder="状态码" clearable class="filter-sm" @keyup.enter="reloadLogs" />
              <el-button type="primary" :icon="Search" @click="reloadLogs">查询</el-button>
            </div>
          </div>
          <el-table :data="logs" stripe v-loading="logLoading" empty-text="暂无日志">
            <el-table-column prop="createdTime" label="时间" width="170">
              <template #default="{ row }">{{ formatDate(row.createdTime) }}</template>
            </el-table-column>
            <el-table-column prop="operatorName" label="操作人" width="120" />
            <el-table-column prop="moduleName" label="模块" width="120" />
            <el-table-column prop="actionName" label="动作" width="110" />
            <el-table-column prop="method" label="方法" width="80" />
            <el-table-column prop="path" label="路径" min-width="220" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="80" align="center" />
            <el-table-column prop="durationMs" label="耗时(ms)" width="100" align="right" />
            <el-table-column prop="remoteAddr" label="IP" width="130" />
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="logPage.pageNum"
              v-model:page-size="logPage.pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="logPage.total"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="reloadLogs"
              @current-change="loadLogs"
            />
          </div>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showUserDialog" :title="editingUser ? '编辑用户' : '新增用户'" width="520px" center>
      <el-form :model="userForm" label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="userForm.username" :disabled="Boolean(editingUser)" />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="userForm.displayName" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="userForm.role" style="width: 100%;">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="运营用户" value="OPERATOR" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingUser" label="密码" required>
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="userForm.enableStatus" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUserDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingUser" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPasswordDialog" title="重置密码" width="420px" center>
      <el-form label-width="90px">
        <el-form-item label="用户">
          <span>{{ passwordUser?.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPasswordDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingPassword" @click="savePassword">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showSettingDialog" :title="editingSetting ? '编辑设置' : '新增设置'" width="560px" center>
      <el-form :model="settingForm" label-width="90px">
        <el-form-item label="设置键" required>
          <el-input v-model="settingForm.settingKey" :disabled="Boolean(editingSetting)" />
        </el-form-item>
        <el-form-item label="分组">
          <el-input v-model="settingForm.settingGroup" />
        </el-form-item>
        <el-form-item label="设置值">
          <el-input v-model="settingForm.settingValue" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="settingForm.description" />
        </el-form-item>
        <el-form-item label="敏感项">
          <el-switch v-model="settingForm.sensitive" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSettingDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingSetting" @click="saveSetting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import {
  addSystemUser,
  getOperationLogPage,
  getRuntimeSettings,
  getSystemSettingPage,
  getSystemUserPage,
  resetSystemUserPassword,
  toggleSystemUser,
  updateSystemUser,
  saveSystemSetting as saveSettingApi
} from '@/api/SystemManagementApi'

const activeTab = ref('users')

const users = ref([])
const userLoading = ref(false)
const userFilter = ref({ username: '', role: '', enableStatus: null })
const userPage = ref({ pageNum: 1, pageSize: 10, total: 0 })
const showUserDialog = ref(false)
const editingUser = ref(null)
const savingUser = ref(false)
const userForm = ref({ username: '', displayName: '', role: 'OPERATOR', password: '', enableStatus: 1 })
const showPasswordDialog = ref(false)
const passwordUser = ref(null)
const newPassword = ref('')
const savingPassword = ref(false)

const runtimeSettings = ref([])
const runtimeLoading = ref(false)
const settings = ref([])
const settingLoading = ref(false)
const settingFilter = ref({ settingKey: '', settingGroup: '' })
const settingPage = ref({ pageNum: 1, pageSize: 10, total: 0 })
const showSettingDialog = ref(false)
const editingSetting = ref(null)
const savingSetting = ref(false)
const settingForm = ref({ settingKey: '', settingValue: '', settingGroup: '', description: '', sensitive: 0 })

const logs = ref([])
const logLoading = ref(false)
const logFilter = ref({ operatorName: '', moduleName: '', actionName: '', status: '' })
const logPage = ref({ pageNum: 1, pageSize: 20, total: 0 })

const roleText = (role) => role === 'ADMIN' ? '管理员' : '运营用户'
const formatDate = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'

const loadUsers = async () => {
  userLoading.value = true
  try {
    const res = await getSystemUserPage({
      pageNum: userPage.value.pageNum,
      pageSize: userPage.value.pageSize,
      username: userFilter.value.username || undefined,
      role: userFilter.value.role || undefined,
      enableStatus: userFilter.value.enableStatus === null ? undefined : userFilter.value.enableStatus
    })
    if (res.success) {
      users.value = res.data || []
      userPage.value.total = Number(res.total) || 0
    }
  } catch {
    ElMessage.error('用户列表加载失败')
  } finally {
    userLoading.value = false
  }
}

const reloadUsers = () => {
  userPage.value.pageNum = 1
  loadUsers()
}

const openUserDialog = (row = null) => {
  editingUser.value = row
  userForm.value = row
    ? { id: row.id, username: row.username, displayName: row.displayName, role: row.role, enableStatus: row.enableStatus }
    : { username: '', displayName: '', role: 'OPERATOR', password: '', enableStatus: 1 }
  showUserDialog.value = true
}

const saveUser = async () => {
  if (!userForm.value.username?.trim()) {
    ElMessage.warning('请填写用户名')
    return
  }
  if (!editingUser.value && !userForm.value.password?.trim()) {
    ElMessage.warning('请填写密码')
    return
  }
  savingUser.value = true
  try {
    const res = editingUser.value ? await updateSystemUser(userForm.value) : await addSystemUser(userForm.value)
    if (res.success) {
      ElMessage.success('保存成功')
      showUserDialog.value = false
      loadUsers()
    }
  } catch {
    ElMessage.error('保存失败')
  } finally {
    savingUser.value = false
  }
}

const toggleUser = async (row) => {
  const nextStatus = row.enableStatus === 1 ? 0 : 1
  await ElMessageBox.confirm(`确定${nextStatus === 1 ? '启用' : '禁用'}用户“${row.username}”吗？`, '确认操作', { type: 'warning' })
  const res = await toggleSystemUser(row.id, nextStatus)
  if (res.success) {
    ElMessage.success('状态已更新')
    loadUsers()
  }
}

const openPasswordDialog = (row) => {
  passwordUser.value = row
  newPassword.value = ''
  showPasswordDialog.value = true
}

const savePassword = async () => {
  if (!newPassword.value.trim()) {
    ElMessage.warning('请填写新密码')
    return
  }
  savingPassword.value = true
  try {
    const res = await resetSystemUserPassword({ id: passwordUser.value.id, password: newPassword.value })
    if (res.success) {
      ElMessage.success('密码已更新')
      showPasswordDialog.value = false
    }
  } catch {
    ElMessage.error('密码更新失败')
  } finally {
    savingPassword.value = false
  }
}

const loadRuntimeSettings = async () => {
  runtimeLoading.value = true
  try {
    const res = await getRuntimeSettings()
    runtimeSettings.value = res.success ? (res.data || []) : []
  } catch {
    ElMessage.error('运行配置加载失败')
  } finally {
    runtimeLoading.value = false
  }
}

const loadSettings = async () => {
  settingLoading.value = true
  try {
    const res = await getSystemSettingPage({
      pageNum: settingPage.value.pageNum,
      pageSize: settingPage.value.pageSize,
      settingKey: settingFilter.value.settingKey || undefined,
      settingGroup: settingFilter.value.settingGroup || undefined
    })
    if (res.success) {
      settings.value = res.data || []
      settingPage.value.total = Number(res.total) || 0
    }
  } catch {
    ElMessage.error('系统设置加载失败')
  } finally {
    settingLoading.value = false
  }
}

const reloadSettings = () => {
  settingPage.value.pageNum = 1
  loadSettings()
}

const openSettingDialog = (row = null) => {
  editingSetting.value = row
  settingForm.value = row
    ? { ...row }
    : { settingKey: '', settingValue: '', settingGroup: '', description: '', sensitive: 0 }
  showSettingDialog.value = true
}

const saveSetting = async () => {
  if (!settingForm.value.settingKey?.trim()) {
    ElMessage.warning('请填写设置键')
    return
  }
  savingSetting.value = true
  try {
    const res = await saveSettingApi(settingForm.value)
    if (res.success) {
      ElMessage.success('设置已保存')
      showSettingDialog.value = false
      loadSettings()
    }
  } catch {
    ElMessage.error('设置保存失败')
  } finally {
    savingSetting.value = false
  }
}

const loadLogs = async () => {
  logLoading.value = true
  try {
    const res = await getOperationLogPage({
      pageNum: logPage.value.pageNum,
      pageSize: logPage.value.pageSize,
      operatorName: logFilter.value.operatorName || undefined,
      moduleName: logFilter.value.moduleName || undefined,
      actionName: logFilter.value.actionName || undefined,
      status: logFilter.value.status || undefined
    })
    if (res.success) {
      logs.value = res.data || []
      logPage.value.total = Number(res.total) || 0
    }
  } catch {
    ElMessage.error('操作日志加载失败')
  } finally {
    logLoading.value = false
  }
}

const reloadLogs = () => {
  logPage.value.pageNum = 1
  loadLogs()
}

onMounted(() => {
  loadUsers()
  loadRuntimeSettings()
  loadSettings()
  loadLogs()
})
</script>

<style scoped>
.system-workbench {
  max-width: 1500px;
}

.system-tabs {
  background: #fff;
  border-radius: 8px;
  padding: 16px 18px;
}

.panel {
  margin-top: 14px;
}

.panel + .panel {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid #eef0f4;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.toolbar-left {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.filter {
  width: 220px;
}

.filter-sm {
  width: 150px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
