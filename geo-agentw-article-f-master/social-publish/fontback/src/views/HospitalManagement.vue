<template>
  <div class="hospital-management-page">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background: #e8f4fd; color: #4f6ef7;">
          <el-icon :size="24"><OfficeBuilding /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ totalCount }}</span>
          <span class="stat-label">医院总数</span>
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
          <el-icon :size="24"><Location /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ cityCount }}</span>
          <span class="stat-label">覆盖城市数</span>
        </div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <div class="action-left">
        <el-input
          v-model="queryParams.hospitalName"
          placeholder="医院名称"
          :prefix-icon="Search"
          clearable
          style="width: 180px"
        />
        <el-select
          v-model="queryParams.hospitalLevel"
          placeholder="医院等级"
          clearable
          style="width: 120px"
        >
          <el-option label="三甲" value="三甲" />
          <el-option label="三乙" value="三乙" />
          <el-option label="二甲" value="二甲" />
          <el-option label="二乙" value="二乙" />
        </el-select>
        <el-select
          v-model="queryParams.hospitalType"
          placeholder="医院类型"
          clearable
          style="width: 120px"
        >
          <el-option label="综合医院" value="综合医院" />
          <el-option label="中医院" value="中医院" />
          <el-option label="专科医院" value="专科医院" />
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
        <el-button type="primary" :icon="Plus" @click="handleAdd">添加医院</el-button>
      </div>
    </div>

    <!-- 医院卡片网格 -->
    <div class="hospital-grid" v-loading="loading">
      <div
        class="hospital-card"
        v-for="hospital in hospitalList"
        :key="hospital.id"
        :class="{ disabled: hospital.enableStatus == 0 }"
      >
        <div class="card-header">
          <div class="hospital-logo">
            <el-avatar :size="48" :src="hospital.logoUrl || defaultLogo">
              <el-icon><OfficeBuilding /></el-icon>
            </el-avatar>
          </div>
          <el-dropdown trigger="click" class="card-menu">
            <el-button :icon="MoreFilled" circle size="small" text />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleEdit(hospital)">编辑信息</el-dropdown-item>
                <el-dropdown-item v-if="hospital.enableStatus == 1" @click="toggleStatus(hospital)">禁用</el-dropdown-item>
                <el-dropdown-item v-else @click="toggleStatus(hospital)">启用</el-dropdown-item>
                <el-dropdown-item divided @click="handleDeleteHospital(hospital)" style="color: #ef4444;">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="card-body">
          <h4 class="hospital-name">{{ hospital.hospitalName }}</h4>
          <div class="hospital-meta">
            <span class="level-tag">{{ hospital.hospitalLevel || '未设置' }}</span>
            <span class="type-tag">{{ hospital.hospitalType || '综合医院' }}</span>
          </div>
          <div class="info-row">
            <el-icon><Location /></el-icon>
            <span>{{ hospital.city }} {{ hospital.district }}</span>
          </div>
          <div class="info-row">
            <el-icon><Phone /></el-icon>
            <span>{{ hospital.phone || '未提供' }}</span>
          </div>
          <div class="address-row" :title="hospital.address">
            <el-icon><MapLocation /></el-icon>
            <span>{{ hospital.address || '地址待补充' }}</span>
          </div>
        </div>
      </div>

      <div class="hospital-card add-card" @click="handleAdd">
        <div class="add-inner">
          <el-icon :size="40" color="#c0c4cc"><Plus /></el-icon>
          <span>添加新医院</span>
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

    <!-- 添加/编辑医院对话框 -->
    <el-dialog
      v-model="showFormDialog"
      :title="editingHospital ? '编辑医院信息' : '添加医院'"
      width="600px"
      center
      :close-on-click-modal="false"
    >
      <el-form :model="hospitalForm" label-width="100px" label-position="left">
        <el-form-item label="医院名称" required>
          <el-input v-model="hospitalForm.hospitalName" placeholder="请输入医院完整名称" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="医院等级">
              <el-select v-model="hospitalForm.hospitalLevel" placeholder="请选择" style="width: 100%;" clearable>
                <el-option label="三甲" value="三甲" />
                <el-option label="三乙" value="三乙" />
                <el-option label="二甲" value="二甲" />
                <el-option label="二乙" value="二乙" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="医院类型">
              <el-select v-model="hospitalForm.hospitalType" placeholder="请选择" style="width: 100%;" clearable>
                <el-option label="综合医院" value="综合医院" />
                <el-option label="中医院" value="中医院" />
                <el-option label="专科医院" value="专科医院" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="省份">
              <el-input v-model="hospitalForm.province" placeholder="省份" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="城市">
              <el-input v-model="hospitalForm.city" placeholder="城市" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区县">
              <el-input v-model="hospitalForm.district" placeholder="区县" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="详细地址">
          <el-input v-model="hospitalForm.address" placeholder="街道门牌号等详细信息" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="hospitalForm.phone" placeholder="多个号码请用逗号分隔" />
        </el-form-item>
        <el-form-item label="医院简介">
          <el-input v-model="hospitalForm.description" type="textarea" :rows="2" placeholder="简要介绍医院特色、规模等" />
        </el-form-item>
        <el-form-item label="Logo URL">
          <el-input v-model="hospitalForm.logoUrl" placeholder="请输入图片地址" />
          <div class="logo-preview" v-if="hospitalForm.logoUrl">
            <el-avatar :size="48" :src="hospitalForm.logoUrl" />
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="hospitalForm.enableStatus" active-text="启用" inactive-text="禁用" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFormDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveHospital" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  OfficeBuilding,
  CircleCheck,
  RemoveFilled,
  Location,
  Search,
  Plus,
  MoreFilled,
  Phone,
  MapLocation
} from '@element-plus/icons-vue'
import {
  getHospitalPage,
  addHospital,
  editHospital,
  deleteHospital,
  getHospitalStatistics, toggleHospitalStatue
} from '@/api/HospitalManagementApi'

// ---------- 查询参数 ----------
const queryParams = ref({
  hospitalName: '',
  hospitalLevel: '',
  hospitalType: '',
  status: null
})
const currentPage = ref(1)
const pageSize = ref(8)
const totalCount = ref(0)
const hospitalList = ref([])
const loading = ref(false)

// 统计数据
const enabledCount = ref(0)
const disabledCount = ref(0)
const cityCount = ref(0)

const defaultLogo = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

// 表单相关
const showFormDialog = ref(false)
const editingHospital = ref(null)
const saving = ref(false)
const hospitalForm = ref({
  hospitalName: '',
  hospitalLevel: '',
  hospitalType: '',
  province: '',
  city: '',
  district: '',
  address: '',
  phone: '',
  description: '',
  logoUrl: '',
  enableStatus: 1
})

// ---------- API 调用 ----------
// 加载医院列表（后端分页）
const loadHospitalList = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      hospitalName: queryParams.value.hospitalName || undefined,
      hospitalLevel: queryParams.value.hospitalLevel || undefined,
      hospitalType: queryParams.value.hospitalType || undefined,
      statue: queryParams.value.status !== null && queryParams.value.status !== '' ? queryParams.value.status : undefined
    }
    const res = await getHospitalPage(params)
    if (res.success) {
      hospitalList.value = res.data|| []
      totalCount.value = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '获取医院列表失败')
      hospitalList.value = []
      totalCount.value = 0
    }
    // 加载统计数据（需要后端提供统计接口）
    await loadStatistics()
  } catch (error) {
    console.error('加载医院列表失败:', error)
    ElMessage.error('加载失败，请检查网络')
  } finally {
    loading.value = false
  }
}

// 加载统计卡片数据
const loadStatistics = async () => {
  try {
    // 假设后端提供 /hospital/statistics 接口，返回 { total, enabled, disabled, cityCount }
    const params = {
      hospitalName: queryParams.value.hospitalName || undefined,
      hospitalLevel: queryParams.value.hospitalLevel || undefined,
      hospitalType: queryParams.value.hospitalType || undefined,
      statue: queryParams.value.status !== null && queryParams.value.status !== '' ? queryParams.value.status : undefined
    }
    const res = await getHospitalStatistics(params)
    if (res.success) {
      enabledCount.value = res.data["enabled"] || 0
      disabledCount.value = res.data["disabled"] || 0
      cityCount.value = res.data["city"] || 0
    } else {
      ElMessage.error('统计接口出错！')
    }
  } catch (error) {
    console.warn('统计接口出现错误！')
  }
}

// 重置查询条件
const resetQuery = () => {
  queryParams.value = {
    hospitalName: '',
    hospitalLevel: '',
    hospitalType: '',
    status: null
  }
  currentPage.value = 1
  loadHospitalList()
}

// 查询按钮
const handleSearch = () => {
  currentPage.value = 1
  loadHospitalList()
}

// 分页改变
const handleCurrentChange = (page) => {
  currentPage.value = page
  loadHospitalList()
}
const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadHospitalList()
}

// ---------- 增删改操作 ----------
const resetForm = () => {
  hospitalForm.value = {
    hospitalName: '',
    hospitalLevel: '',
    hospitalType: '',
    province: '',
    city: '',
    district: '',
    address: '',
    phone: '',
    description: '',
    logoUrl: '',
    enableStatus: 1
  }
  editingHospital.value = null
}
const handleAdd = () => {
  resetForm()
  showFormDialog.value = true
}
const handleEdit = (hospital) => {
  editingHospital.value = hospital
  hospitalForm.value = { ...hospital }
  showFormDialog.value = true
}
const handleSaveHospital = async () => {
  if (!hospitalForm.value.hospitalName) {
    ElMessage.warning('请填写医院名称')
    return
  }

  saving.value = true
  try {
    let res
    if (editingHospital.value) {
      // 编辑模式：需要传递医院ID
      const data = { ...hospitalForm.value, id: editingHospital.value.id }
      res = await editHospital(data)
    } else {
      res = await addHospital(hospitalForm.value)
    }
    if (res.success) {
      ElMessage.success(editingHospital.value ? '医院信息已更新' : '医院添加成功')
      showFormDialog.value = false
      // 刷新列表并保持在当前页
      loadHospitalList()
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
const toggleStatus = (hospital) => {
  const newStatus = hospital.enableStatus == 1 ? 0 : 1
  const action = newStatus == 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确定${action}医院“${hospital.hospitalName}”吗？`, '确认操作', { type: 'warning' })
    .then(async () => {
      try {
        const res = await toggleHospitalStatue(hospital.id, newStatus)
        if (res.success) {
          hospital.enableStatus = newStatus
          ElMessage.success(`医院已${action}`)
          loadHospitalList() // 刷新列表
        } else {
          ElMessage.error(res.message || '操作失败')
        }
      } catch (error) {
        ElMessage.error('操作失败')
      }
    })
    .catch(() => {})
}
const handleDeleteHospital = (hospital) => {
  ElMessageBox.confirm(`确定删除医院“${hospital.hospitalName}”吗？`, '删除确认', { type: 'error' })
    .then(async () => {
      try {
        const res = await deleteHospital(hospital.id)
        if (res.success) {
          ElMessage.success('医院已删除')
          loadHospitalList()
        } else {
          ElMessage.error(res.message || '删除失败')
        }
      } catch (error) {
        ElMessage.error('删除失败')
      }
    })
    .catch(() => {})
}

// 初始化加载
onMounted(() => {
  loadHospitalList()
})
</script>

<style scoped>
/* 原有样式保持不变，请保留用户提供的所有样式代码 */
.hospital-management-page {
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
.hospital-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}
.hospital-card {
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
}
.hospital-card:hover {
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}
.hospital-card.disabled {
  opacity: 0.6;
  background: #fafafa;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}
.hospital-logo {
  background: #f3f4f6;
  border-radius: 28px;
  padding: 4px;
}
.card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hospital-name {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 6px;
  color: #1a1d29;
}
.hospital-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.level-tag, .type-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 20px;
  background: #eef0ff;
  color: #4f6ef7;
}
.info-row, .address-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #5b5e73;
}
.address-row {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.add-card {
  border: 2px dashed #e0e3e8;
  background: #fafbfc;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  cursor: pointer;
}
.add-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: #c0c4cc;
}
.logo-preview {
  margin-top: 8px;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>