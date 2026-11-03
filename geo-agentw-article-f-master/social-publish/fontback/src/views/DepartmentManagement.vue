<template>
  <div class="department-management-page">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background: #e8f4fd; color: #4f6ef7;">
          <el-icon :size="24"><Grid /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ totalCount }}</span>
          <span class="stat-label">科室总数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #e6f9ee; color: #22c55e;">
          <el-icon :size="24"><CircleCheck /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ enabledCount }}</span>
          <span class="stat-label">已启用</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #fef2f2; color: #ef4444;">
          <el-icon :size="24"><RemoveFilled /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ disabledCount }}</span>
          <span class="stat-label">已禁用</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #fff7e6; color: #f59e0b;">
          <el-icon :size="24"><OfficeBuilding /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ hospitalCount }}</span>
          <span class="stat-label">覆盖医院数</span>
        </div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <div class="action-left">
        <el-input
          v-model="queryParams.departmentName"
          placeholder="科室名称"
          :prefix-icon="Search"
          clearable
          style="width: 180px"
        />
        <el-select
          v-model="queryParams.hospitalId"
          placeholder="所属医院"
          clearable
          style="width: 160px"
        >
          <el-option
            v-for="h in hospitalOptions"
            :key="h.id"
            :label="h.hospitalName"
            :value="h.id"
          />
        </el-select>
        <el-select
          v-model="queryParams.status"
          placeholder="启用状态"
          clearable
          style="width: 100px"
        >
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
      <div class="action-right">
        <el-button type="primary" :icon="Plus" @click="handleAdd">添加科室</el-button>
      </div>
    </div>

    <!-- 科室卡片网格 -->
    <div class="dept-grid" v-loading="loading">
      <div
        class="dept-card"
        v-for="dept in departmentList"
        :key="dept.id"
        :class="{ disabled: dept.enableStatus == 0 }"
      >
        <div class="card-header">
          <div class="dept-icon" :style="{ background: getRandomColor(dept.id) }">
            <el-icon :size="28"><Document /></el-icon>
          </div>
          <el-dropdown trigger="click" class="card-menu">
            <el-button :icon="MoreFilled" circle size="small" text />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleEdit(dept)">编辑科室</el-dropdown-item>
                <el-dropdown-item v-if="dept.enableStatus == 1" @click="toggleStatus(dept)">禁用</el-dropdown-item>
                <el-dropdown-item v-else @click="toggleStatus(dept)">启用</el-dropdown-item>
                <el-dropdown-item divided @click="handleDeleteDept(dept)" style="color: #ef4444;">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="card-body">
          <h4 class="dept-name" :title="getDepartmentDisplayName(dept)">
            {{ getDepartmentDisplayName(dept) }}
          </h4>
          <div class="hospital-info">
            <el-icon><OfficeBuilding /></el-icon> <span>{{ dept.hospitalName }}</span>
          </div>
          <div class="desc-row" v-if="dept.departmentDesc">
            <el-icon><Memo /></el-icon>
            <span class="desc-text">{{ dept.departmentDesc }}</span>
          </div>
          <div class="status-badge">
            <el-tag :type="dept.enableStatus == 1 ? 'success' : 'info'" size="small" effect="light">
              {{ dept.enableStatus == 1 ? '启用' : '禁用' }}
            </el-tag>
          </div>
        </div>
      </div>

      <div class="dept-card add-card" @click="handleAdd">
        <div class="add-inner">
          <el-icon :size="40" color="#c0c4cc"><Plus /></el-icon>
          <span>新建科室</span>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[6, 8, 12, 20]"
        :total="totalCount"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 添加/编辑科室对话框 -->
    <el-dialog
      v-model="showFormDialog"
      :title="editingDept ? '编辑科室' : '新增科室'"
      width="500px"
      center
      :close-on-click-modal="false"
    >
      <el-form :model="deptForm" label-width="90px">
        <el-form-item label="科室名称" required>
          <el-input v-model="deptForm.departmentName" placeholder="例如：心内科" />
        </el-form-item>
        <el-form-item label="所属医院" required>
          <el-select
            v-model="deptForm.hospitalId"
            placeholder="请选择医院"
            style="width: 100%;"
          >
            <el-option
              v-for="h in hospitalOptions"
              :key="h.id"
              :label="h.hospitalName"
              :value="h.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="科室简介">
          <el-input
            v-model="deptForm.departmentDesc"
            type="textarea"
            :rows="3"
            placeholder="介绍科室诊疗范围、特色技术等"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="deptForm.enableStatus"
            active-text="启用"
            inactive-text="禁用"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFormDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveDept" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Grid,
  CircleCheck,
  RemoveFilled,
  OfficeBuilding,
  Search,
  Plus,
  MoreFilled,
  Document,
  Memo
} from '@element-plus/icons-vue'
import {
  getDepartmentPage,
  addDepartment,
  editDepartment,
  deleteDepartment,
  toggleDepartmentStatue,
  getDepartmentStatistics
} from '@/api/DepartmentManagementApi'

import {getHospitalAll} from "@/api/HospitalManagementApi.js";

// ---------- 查询参数 ----------
const queryParams = ref({
  departmentName: '',
  hospitalId: null,
  status: null
})
const currentPage = ref(1)
const pageSize = ref(8)
const totalCount = ref(0)
const departmentList = ref([])
const loading = ref(false)

// 统计数据
const enabledCount = ref(0)
const disabledCount = ref(0)
const hospitalCount = ref(0)

// 医院选项（动态获取）
const hospitalOptions = ref([])

// 表单相关
const showFormDialog = ref(false)
const editingDept = ref(null)
const saving = ref(false)
const deptForm = ref({
  departmentName: '',
  hospitalId: null,
  departmentDesc: '',
  enableStatus: 1
})

// ---------- 辅助函数 ----------
const getRandomColor = (id) => {
  const colors = ['#eef2ff', '#e6f9ee', '#fff0e6', '#f3e8ff', '#ffe4e6']
  return colors[id % colors.length]
}

const escapeRegExp = (value = '') => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

const cleanupDepartmentName = (name, hospitalName) => {
  const text = String(name || '').trim()
  if (!text) return ''
  if (hospitalName && text.startsWith(hospitalName)) {
    return text.slice(hospitalName.length).trim()
  }
  return text
}

const getDepartmentDisplayName = (dept) => {
  const directName = cleanupDepartmentName(dept?.departmentName, dept?.hospitalName)
  if (directName) return directName

  const desc = String(dept?.departmentDesc || '').trim()
  if (!desc) return '未命名科室'

  const explicitName = desc.match(/科室名称[：:]\s*([^,，。；;\n]+)/)
  if (explicitName?.[1]) {
    return cleanupDepartmentName(explicitName[1], dept?.hospitalName) || '未命名科室'
  }

  if (dept?.hospitalName) {
    const hospitalPattern = escapeRegExp(dept.hospitalName)
    const hospitalNameMatch = desc.match(new RegExp(`${hospitalPattern}(.{2,30}?)(?:隶属于|是|为|，|,|。|\\s)`))
    if (hospitalNameMatch?.[1]) {
      return cleanupDepartmentName(hospitalNameMatch[1], dept.hospitalName) || '未命名科室'
    }
  }

  return '未命名科室'
}

// ---------- API 调用 ----------
// 加载科室列表（后端分页）
const loadDepartmentList = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      departmentName: queryParams.value.departmentName || undefined,
      hospitalId: queryParams.value.hospitalId || undefined,
      statue: queryParams.value.status !== null && queryParams.value.status !== '' ? queryParams.value.status : undefined
    }
    const res = await getDepartmentPage(params)
    if (res.success) {
      departmentList.value = res.data || []
      totalCount.value = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '获取科室列表失败')
      departmentList.value = []
      totalCount.value = 0
    }
    // 加载统计数据（根据当前筛选条件）
    await loadStatistics()
  } catch (error) {
    console.error('加载科室列表失败:', error)
    ElMessage.error('加载失败，请检查网络')
  } finally {
    loading.value = false
  }
}

// 加载统计卡片数据
const loadStatistics = async () => {
  try {
    const params = {
      departmentName: queryParams.value.departmentName || undefined,
      hospitalId: queryParams.value.hospitalId || undefined,
      statue: queryParams.value.status !== null && queryParams.value.status !== '' ? queryParams.value.status : undefined
    }
    const res = await getDepartmentStatistics(params)
    if (res.success) {
      enabledCount.value = res.data["enabled"] || 0
      disabledCount.value = res.data["disabled"] || 0
      hospitalCount.value = res.data["hospital"] || 0
    } else {
      ElMessage.error('统计接口出错！')
    }
  } catch (error) {
    console.warn('统计接口调用失败，使用前端降级统计（仅当前页数据）')
  }
}

// 加载医院下拉选项
const loadHospitalOptions = async () => {
  try {
    const res = await getHospitalAll()
    if (res.success) {
      hospitalOptions.value = res.data
    } else {
      ElMessage.error(res.message || "系统异常！")
    }
  } catch (error) {
    console.warn('获取医院列表失败')
  }
}

// 重置查询条件
const resetQuery = () => {
  queryParams.value = {
    departmentName: '',
    hospitalId: null,
    status: null
  }
  currentPage.value = 1
  loadDepartmentList()
}

// 查询按钮
const handleSearch = () => {
  currentPage.value = 1
  loadDepartmentList()
}

// 分页改变
const handleCurrentChange = (page) => {
  currentPage.value = page
  loadDepartmentList()
}
const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadDepartmentList()
}

// ---------- 增删改操作 ----------
const resetForm = () => {
  deptForm.value = {
    departmentName: '',
    hospitalId: null,
    departmentDesc: '',
    enableStatus: 1
  }
  editingDept.value = null
}
const handleAdd = () => {
  resetForm()
  showFormDialog.value = true
}
const handleEdit = (dept) => {
  editingDept.value = dept
  deptForm.value = {...dept, hospitalId: dept.hospitalId}
  showFormDialog.value = true
}
const handleSaveDept = async () => {
  if (!deptForm.value.departmentName || !deptForm.value.hospitalId) {
    ElMessage.warning('请填写科室名称和所属医院')
    return
  }

  // 获取对应的医院名称
  const hospital = hospitalOptions.value.find(h => h.id === deptForm.value.hospitalId)
  if (!hospital) {
    ElMessage.error('医院数据无效，请重新选择')
    return
  }

  saving.value = true
  try {
    let res
    if (editingDept.value) {
      // 编辑模式
      const data = {
        ...deptForm.value,
        id: editingDept.value.id,
        hospitalName: hospital.hospitalName
      }
      res = await editDepartment(data)
    } else {
      // 新增模式
      const data = {
        ...deptForm.value,
        hospitalName: hospital.hospitalName
      }
      res = await addDepartment(data)
    }
    if (res.success) {
      ElMessage.success(editingDept.value ? '科室信息已更新' : '科室添加成功')
      showFormDialog.value = false
      loadDepartmentList()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败，请检查网络')
  } finally {
    saving.value = false
  }
}
const toggleStatus = (dept) => {
  const newStatus = dept.enableStatus == 1 ? 0 : 1
  const action = newStatus == 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确定${action}科室“${getDepartmentDisplayName(dept)}”吗？`, '确认操作', {type: 'warning'})
      .then(async () => {
        try {
          const res = await toggleDepartmentStatue(dept.id, newStatus)
          if (res.success) {
            dept.enableStatus = newStatus
            ElMessage.success(`科室已${action}`)
            loadDepartmentList() // 刷新列表
          } else {
            ElMessage.error(res.message || '操作失败')
          }
        } catch (error) {
          ElMessage.error('操作失败')
        }
      })
      .catch(() => {
      })
}
const handleDeleteDept = (dept) => {
  ElMessageBox.confirm(`确定删除科室“${getDepartmentDisplayName(dept)}”吗？`, '删除确认', {type: 'error'})
      .then(async () => {
        try {
          const res = await deleteDepartment(dept.id)
          if (res.success) {
            ElMessage.success('科室已删除')
            loadDepartmentList()
          } else {
            ElMessage.error(res.message || '删除失败')
          }
        } catch (error) {
          ElMessage.error('删除失败')
        }
      })
      .catch(() => {
      })
}

// 初始化加载
onMounted(() => {
  loadHospitalOptions()
  loadDepartmentList()
})
</script>

<style scoped>
.department-management-page {
  max-width: 1400px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
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

.stat-info {
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
  margin-top: 2px;
}

.action-bar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.action-left {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.dept-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 18px;
}

.dept-card {
  background: #fff;
  border-radius: 16px;
  padding: 18px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  transition: all 0.2s;
  min-height: 190px;
  max-height: 230px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.dept-card:hover {
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.disabled {
  opacity: 0.6;
  background: #fafafa;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.dept-icon {
  width: 48px;
  height: 48px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-body {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.dept-name {
  font-size: 18px;
  line-height: 1.4;
  font-weight: 600;
  color: #111827;
  flex-shrink: 0;
  min-height: 25px;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hospital-info {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #4b5563;
  margin-bottom: 8px;
  min-width: 0;
}

.hospital-info span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.desc-row {
  display: flex;
  gap: 6px;
  font-size: 12px;
  color: #6c7283;
  margin-top: 8px;
  align-items: flex-start;
}

.desc-text {
  flex: 1;
  line-height: 1.4;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  word-break: break-word;
}

.status-badge {
  flex-shrink: 0;
  margin-top: 8px;
  text-align: right;
}

.add-card {
  border: 2px dashed #e0e3e8;
  background: #f9fafb;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  min-height: 190px;
}

.add-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.pagination-wrap {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
</style>
