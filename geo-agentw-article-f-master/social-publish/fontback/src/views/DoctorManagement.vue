<template>
  <div class="doctor-management-page">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background: #e8f4fd; color: #4f6ef7;">
          <el-icon :size="24">
            <User/>
          </el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ totalCount }}</span>
          <span class="stat-label">医生总数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #e6f9ee; color: #22c55e;">
          <el-icon :size="24">
            <Document/>
          </el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ knowledgeAvailableCount }}</span>
          <span class="stat-label">知识库可用</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #fff7e6; color: #f59e0b;">
          <el-icon :size="24">
            <EditPen/>
          </el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ enabledCount }}</span>
          <span class="stat-label">已启用</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #fef2f2; color: #ef4444;">
          <el-icon :size="24">
            <RemoveFilled/>
          </el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ disabledCount }}</span>
          <span class="stat-label">已禁用</span>
        </div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <div class="action-left">
        <el-input
            v-model="queryParams.doctorName"
            placeholder="医生姓名"
            :prefix-icon="Search"
            clearable
            style="width: 150px"
        />
        <el-select
            v-model="queryParams.hospital"
            placeholder="所属医院"
            clearable
            style="width: 150px"
            @change="handleHospitalChange"
        >
          <el-option
              v-for="h in hospitalOptions"
              :key="h.id"
              :label="h.hospitalName"
              :value="h.id"
          />
        </el-select>
        <el-select
            v-model="queryParams.department"
            placeholder="所属科室"
            clearable
            style="width: 150px"
            :disabled="!queryParams.hospital"
        >
          <el-option
              v-for="d in departmentOptions"
              :key="d.id"
              :label="d.departmentName"
              :value="d.id"
          />
        </el-select>
        <el-select
            v-model="queryParams.title"
            placeholder="职称"
            clearable
            style="width: 130px"
        >
          <el-option
              v-for="t in titleOptions"
              :key="t.id"
              :label="t.titleName"
              :value="t.id"
          />
        </el-select>
        <el-select
            v-model="queryParams.statue"
            placeholder="启用状态"
            clearable
            style="width: 120px"
        >
          <el-option label="启用" :value="1"/>
          <el-option label="禁用" :value="0"/>
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
      <div class="action-right">
        <el-button type="primary" :icon="Plus" @click="handleAdd">添加医生</el-button>
      </div>
    </div>

    <!-- 医生列表（卡片形式） -->
    <div class="doctor-grid" v-loading="loading">
      <div
          class="doctor-card"
          v-for="doctor in doctorList"
          :key="doctor.id"
          :class="{ disabled: doctor.enableStatus == 0 }"
      >
        <div class="card-header">
          <el-avatar :size="56" :src="doctor.avatarUrl"/>
          <el-dropdown trigger="click" class="card-menu">
            <el-button :icon="MoreFilled" circle size="small" text/>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleEdit(doctor)">编辑信息</el-dropdown-item>
                <el-dropdown-item
                    v-if="doctor.enableStatus == 1"
                    @click="toggleStatus(doctor)"
                >禁用
                </el-dropdown-item>
                <el-dropdown-item v-else @click="toggleStatus(doctor)">启用</el-dropdown-item>
                <el-dropdown-item
                    divided
                    @click="handleDeleteDoctor(doctor)"
                    style="color: #ef4444;"
                >删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="card-body">
          <h4 class="doctor-name">{{ doctor.doctorName }}</h4>
          <div class="doctor-meta">
            <span class="hospital-tag">{{ doctor.hospitalName }}</span>
            <span class="department-tag">{{ doctor.departmentName }}</span>
            <span class="title-tag">{{ doctor.titleName }}</span>
          </div>
          <div class="doctor-info-row">
            <span>专长：{{ doctor.specialty || '未填写' }}</span>
          </div>
          <div class="knowledge-status">
            <span>知识库：</span>
            <el-tag
                :type="
                doctor.knowledgeStatus === 1
                  ? 'success'
                  : doctor.knowledgeStatus === 2
                  ? 'danger'
                  : 'info'
              "
                size="small"
                effect="light"
            >
              {{ knowledgeStatusText(doctor.knowledgeStatus) }}
            </el-tag>
          </div>
          <div class="add-time">添加于 {{ formatDate(doctor.createdTime) }}</div>
        </div>
      </div>

    </div>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[6, 8, 12, 20, 50, 100]"
          :total="totalCount"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
      />
    </div>

    <!-- 添加/编辑医生对话框 -->
    <el-dialog
        v-model="showFormDialog"
        :title="editingDoctor ? '编辑医生信息' : '添加医生'"
        width="550px"
        center
        :close-on-click-modal="false"
    >
      <el-form :model="doctorForm" label-width="100px" label-position="left">
        <el-form-item label="医生姓名" required>
          <el-input v-model="doctorForm.doctorName" placeholder="请输入医生姓名"/>
        </el-form-item>
        <el-form-item label="所属医院" required>
          <el-select
              v-model="doctorForm.hospitalId"
              placeholder="请选择医院"
              style="width: 100%"
              @change="onHospitalChange"
          >
            <el-option
                v-for="h in hospitalOptions"
                :key="h.id"
                :label="h.hospitalName"
                :value="h.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属科室" required>
          <el-select
              v-model="doctorForm.departmentId"
              placeholder="请选择科室"
              style="width: 100%"
              :disabled="!doctorForm.hospitalId"
          >
            <el-option
                v-for="d in departmentOptions"
                :key="d.id"
                :label="d.departmentName"
                :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="职称" required>
          <el-select
              v-model="doctorForm.titleId"
              placeholder="请选择职称"
              style="width: 100%"
          >
            <el-option
                v-for="t in titleOptions"
                :key="t.id"
                :label="t.titleName"
                :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="doctorForm.gender">
            <el-radio :label="0">未知</el-radio>
            <el-radio :label="1">男</el-radio>
            <el-radio :label="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="专业特长">
          <el-input
              v-model="doctorForm.specialty"
              type="textarea"
              :rows="2"
              placeholder="例如：冠心病介入治疗、心律失常"
          />
        </el-form-item>
        <el-form-item label="头像">
          <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="handleAvatarChange"
          >
            <el-avatar v-if="doctorForm.avatarUrl" :size="64" :src="doctorForm.avatarUrl"/>
            <el-icon v-else :size="64" color="#c0c4cc">
              <Plus/>
            </el-icon>
            <template #tip>
              <span class="upload-tip">点击上传头像</span>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="知识库状态">
          <el-select v-model="doctorForm.knowledgeStatus" placeholder="知识库状态" style="width: 100%">
            <el-option label="未上传" :value="0"/>
            <el-option label="可用" :value="1"/>
            <el-option label="存在异常" :value="2"/>
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch
              v-model="doctorForm.enableStatus"
              active-text="启用"
              inactive-text="禁用"
              :active-value="1"
              :inactive-value="0"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="doctorForm.remark" type="textarea" :rows="1" placeholder="可选备注"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFormDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveDoctor" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {ref, onMounted} from 'vue'
import {
  User,
  Document,
  EditPen,
  RemoveFilled,
  Search,
  Plus,
  MoreFilled,
} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox, ElPagination } from 'element-plus'
import {
  getDoctorPage,
  newDoctor,
  deleteDoctor,
  getDoctorStatistics,
  editDoctor,
  toggleDoctorStatue
} from '@/api/DoctorManagementApi'
import {getHospitalAll} from '@/api/HospitalManagementApi.js'
import {getTitleAll} from '@/api/TitleManagementApi.js'
import {getDepartmentAll} from '@/api/DepartmentManagementApi.js'

// ---------- 查询参数 ----------
const queryParams = ref({
  doctorName: '',
  hospital: null,
  department: null,
  title: null,
  statue: null,
})
const currentPage = ref(1)
const pageSize = ref(8)
const totalCount = ref(0)
const doctorList = ref([])
const loading = ref(false)

// 统计数据
const knowledgeAvailableCount = ref(0)
const enabledCount = ref(0)
const disabledCount = ref(0)

// 下拉选项
const hospitalOptions = ref([])
const departmentOptions = ref([])
const titleOptions = ref([])

// 表单相关
const showFormDialog = ref(false)
const editingDoctor = ref(null)
const saving = ref(false)
const doctorForm = ref({
  doctorName: '',
  hospitalId: null,
  departmentId: null,
  titleId: null,
  gender: 0,
  specialty: '',
  avatarUrl: '',
  knowledgeStatus: 0,
  enableStatus: 1,
  remark: '',
})

// ---------- 辅助函数 ----------
const knowledgeStatusText = (status) => {
  if (status === 1) return '可用'
  if (status === 2) return '存在异常'
  return '未上传'
}
const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return dateStr.split(' ')[0]
}

// 获取医生列表（后端分页）
const loadDoctorList = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      doctorName: queryParams.value.doctorName || undefined,
      hospital: queryParams.value.hospital || undefined,
      department: queryParams.value.department || undefined,
      title: queryParams.value.title || undefined,
      statue: queryParams.value.statue !== null && queryParams.value.statue !== '' ? queryParams.value.statue : undefined,
    }
    const res = await getDoctorPage(params)
    if (res.success) {
      doctorList.value = res.data || []
      totalCount.value = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '获取医生列表失败')
      doctorList.value = []
      totalCount.value = 0
    }
    await loadStatistics()
    console.log("totalCount:", totalCount.value)
  } catch (error) {
    console.error('加载医生列表失败:', error)
    ElMessage.error('加载失败，请检查网络')
  } finally {
    loading.value = false
  }
}

// 加载医院列表
const loadHospital = async () => {
  const res = await getHospitalAll()
  if (res.success) {
    hospitalOptions.value = res.data
  }
}

// 加载职称列表
const loadTitle = async () => {
  const res = await getTitleAll()
  if (res.success) {
    titleOptions.value = res.data
  }
}

// 根据医院ID加载科室列表
const loadDepartment = async (hospitalId) => {
  if (!hospitalId) {
    departmentOptions.value = []
    return
  }
  const res = await getDepartmentAll(hospitalId)
  if (res.success) {
    departmentOptions.value = res.data
  } else {
    departmentOptions.value = []
  }
}

// 获取统计数据
const loadStatistics = async () => {
  try {
    const params = {
      doctorName: queryParams.value.doctorName || undefined,
      hospital: queryParams.value.hospital || undefined,
      department: queryParams.value.department || undefined,
      title: queryParams.value.title || undefined,
      statue: queryParams.value.statue !== null && queryParams.value.statue !== '' ? queryParams.value.statue : undefined,
    }
    const res = await getDoctorStatistics(params)
    if (res.success) {
      knowledgeAvailableCount.value = res.data['knowledgeAvailable'] || 0
      enabledCount.value = res.data['enabled'] || 0
      disabledCount.value = res.data['disabled'] || 0
    } else {
      ElMessage.error('统计接口出错！')
    }
  } catch (e) {
    console.warn('统计接口暂不可用')
  }
}

// 重置查询条件
const resetQuery = () => {
  queryParams.value = {
    doctorName: '',
    hospital: null,
    department: null,
    title: null,
    statue: null,
  }
  currentPage.value = 1
  // 重置时清空科室选项
  departmentOptions.value = []
  loadDoctorList()
}

// 查询按钮
const handleSearch = () => {
  currentPage.value = 1
  loadDoctorList()
}

// 分页改变
const handleCurrentChange = (page) => {
  currentPage.value = page
  loadDoctorList()
}
const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadDoctorList()
}

// 查询条件中的医院变化：重新加载科室，并清空科室查询值
const handleHospitalChange = async () => {
  const hospitalId = queryParams.value.hospital
  if (hospitalId) {
    await loadDepartment(hospitalId)
  } else {
    departmentOptions.value = []
  }
  // 清空已选的科室
  queryParams.value.department = null
}

// ---------- 增删改操作 ----------
const resetForm = () => {
  doctorForm.value = {
    doctorName: '',
    hospitalId: null,
    departmentId: null,
    titleId: null,
    gender: 0,
    specialty: '',
    avatarUrl: '',
    knowledgeStatus: 0,
    enableStatus: 1,
    remark: '',
  }
  editingDoctor.value = null
  // 重置表单时清空科室选项
  departmentOptions.value = []
}
const handleAdd = () => {
  resetForm()
  showFormDialog.value = true
}
const handleEdit = (doctor) => {
  editingDoctor.value = doctor
  doctorForm.value = {...doctor}
  // 编辑时，根据当前医生的医院ID加载科室列表
  if (doctor.hospitalId) {
    loadDepartment(doctor.hospitalId)
  }
  showFormDialog.value = true
}
const onHospitalChange = async (hospitalId) => {
  // 表单中医院变化：重新加载科室，并清空科室选择
  if (hospitalId) {
    await loadDepartment(hospitalId)
  } else {
    departmentOptions.value = []
  }
  doctorForm.value.departmentId = null
}
const handleAvatarChange = (file) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    doctorForm.value.avatarUrl = e.target.result
  }
  reader.readAsDataURL(file.raw)
}
const handleSaveDoctor = async () => {
  if (
      !doctorForm.value.doctorName ||
      !doctorForm.value.hospitalId ||
      !doctorForm.value.departmentId ||
      !doctorForm.value.titleId
  ) {
    ElMessage.warning('请填写姓名、医院、科室和职称')
    return
  }

  // 补全关联名称
  const hospital = hospitalOptions.value.find((h) => h.id === doctorForm.value.hospitalId)
  const department = departmentOptions.value.find((d) => d.id === doctorForm.value.departmentId)
  const title = titleOptions.value.find((t) => t.id === doctorForm.value.titleId)
  if (!hospital || !department || !title) {
    ElMessage.error('关联数据错误，请重新选择')
    return
  }

  const doctorData = {
    ...doctorForm.value,
    hospitalName: hospital.hospitalName,
    departmentName: department.departmentName,
    titleName: title.titleName,
  }

  saving.value = true
  try {
    let res
    if (editingDoctor.value) {
      doctorData.id = editingDoctor.value.id
      res = await editDoctor(doctorData)
    } else {
      res = await newDoctor(doctorData)
    }
    if (res.success) {
      ElMessage.success(editingDoctor.value ? '医生信息已更新' : '医生添加成功')
      showFormDialog.value = false
      loadDoctorList()
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

const toggleStatus = (doctor) => {
  const newStatus = doctor.enableStatus == 1 ? 0 : 1
  const action = newStatus == 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确定${action}医生“${doctor.doctorName}”吗？`, '确认操作', {type: 'warning'})
      .then(async () => {
        try {
          const res = await toggleDoctorStatue(doctor.id, newStatus)
          if (res.success) {
            doctor.enableStatus = newStatus
            ElMessage.success(`医生已${action}`)
            loadDoctorList()
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

const handleDeleteDoctor = (doctor) => {
  ElMessageBox.confirm(`确定删除医生“${doctor.doctorName}”吗？此操作不可恢复。`, '删除确认', {
    type: 'error',
  })
      .then(async () => {
        try {
          const res = await deleteDoctor(doctor.id)
          if (res.success) {
            ElMessage.success('医生已删除')
            loadDoctorList()
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
  loadDoctorList()
  loadHospital()
  loadTitle()
})
</script>

<style scoped>
/* 样式保持不变，此处省略，请使用原有样式 */
.doctor-management-page {
  width: 100%;
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
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.action-left {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.action-right {
  display: flex;
  gap: 10px;
}

.doctor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.doctor-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: all 0.3s;
}

.doctor-card:hover {
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.doctor-card.disabled {
  opacity: 0.6;
  background: #fafafa;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}

.card-menu {
  margin-top: 4px;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.doctor-name {
  font-size: 18px;
  font-weight: 600;
  color: #1a1d29;
  margin: 0;
}

.doctor-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hospital-tag, .department-tag, .title-tag {
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.hospital-tag {
  background: #f0fdf4;
  color: #22c55e;
}

.department-tag {
  background: #eef0ff;
  color: #4f6ef7;
}

.title-tag {
  background: #fff7e6;
  color: #f59e0b;
}

.doctor-info-row {
  font-size: 13px;
  color: #555;
  line-height: 1.5;
}

.knowledge-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}

.add-time {
  font-size: 11px;
  color: #bbb;
  margin-top: 6px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding-bottom: 20px;
}

.upload-tip {
  font-size: 12px;
  color: #8b8fa3;
  margin-top: 5px;
  display: block;
}
</style>
