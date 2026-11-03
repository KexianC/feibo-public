<template>
  <div class="title-management-page">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background: #eef2ff; color: #4f6ef7;">
          <el-icon :size="24"><Medal /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ totalCount }}</span>
          <span class="stat-label">职称总数</span>
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
          <el-icon :size="24"><TrendCharts /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ maxLevel }}</span>
          <span class="stat-label">最高级别</span>
        </div>
      </div>
    </div>

    <!-- 操作栏（增加级别筛选） -->
    <div class="action-bar">
      <div class="action-left">
        <el-input
          v-model="queryParams.titleName"
          placeholder="职称名称"
          :prefix-icon="Search"
          clearable
          style="width: 180px"
        />
        <el-select
          v-model="queryParams.titleLevel"
          placeholder="职称级别"
          clearable
          style="width: 120px"
        >
          <el-option label="级别1" :value="1" />
          <el-option label="级别2" :value="2" />
          <el-option label="级别3" :value="3" />
          <el-option label="级别4" :value="4" />
          <el-option label="级别5" :value="5" />
          <el-option label="级别6" :value="6" />
          <el-option label="级别7" :value="7" />
          <el-option label="级别8" :value="8" />
          <el-option label="级别9" :value="9" />
          <el-option label="级别10" :value="10" />
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
        <el-button type="primary" :icon="Plus" @click="handleAdd">添加职称</el-button>
      </div>
    </div>

    <!-- 职称卡片网格 -->
    <div class="title-grid" v-loading="loading">
      <div
        class="title-card"
        v-for="title in titleList"
        :key="title.id"
        :class="{ disabled: title.enableStatus == 0 }"
      >
        <div class="card-header">
          <div class="title-badge" :style="{ backgroundColor: getLevelColor(title.titleLevel) }">
            <span class="level-num">Lv.{{ title.titleLevel }}</span>
          </div>
          <el-dropdown trigger="click" class="card-menu">
            <el-button :icon="MoreFilled" circle size="small" text />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleEdit(title)">编辑</el-dropdown-item>
                <el-dropdown-item v-if="title.enableStatus == 1" @click="toggleStatus(title)">禁用</el-dropdown-item>
                <el-dropdown-item v-else @click="toggleStatus(title)">启用</el-dropdown-item>
                <el-dropdown-item divided @click="handleDeleteTitle(title)" style="color: #ef4444;">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="card-body">
          <h3 class="title-name">{{ title.titleName }}</h3>
          <div class="level-info">
            <el-tag :type="title.titleLevel > 3 ? 'danger' : 'primary'" size="small" effect="plain">
              级别 {{ title.titleLevel }}
            </el-tag>
          </div>
          <div class="desc-box" v-if="title.description">
            <el-icon><InfoFilled /></el-icon>
            <span>{{ title.description }}</span>
          </div>
        </div>
      </div>

      <div class="title-card add-card" @click="handleAdd">
        <div class="add-inner">
          <el-icon :size="36" color="#c0c4cc"><Plus /></el-icon>
          <span>新增职称</span>
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

    <!-- 添加/编辑职称对话框 -->
    <el-dialog
      v-model="showFormDialog"
      :title="editingTitle ? '编辑职称' : '新增职称'"
      width="480px"
      center
      :close-on-click-modal="false"
    >
      <el-form :model="titleForm" label-width="80px">
        <el-form-item label="职称名称" required>
          <el-input v-model="titleForm.titleName" placeholder="例如：主任医师、教授" />
        </el-form-item>
        <el-form-item label="职称级别">
          <el-input-number v-model="titleForm.titleLevel" :min="1" :max="10" controls-position="right" style="width: 100%;" />
          <div class="level-hint">数字越大级别越高</div>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="titleForm.description" type="textarea" :rows="2" placeholder="任职要求、职责描述"/>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="titleForm.enableStatus" active-text="启用" inactive-text="禁用" :active-value="1"
                     :inactive-value="0"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFormDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTitle" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {ref, onMounted} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  Medal,
  CircleCheck,
  RemoveFilled,
  TrendCharts,
  Search,
  Plus,
  MoreFilled,
  InfoFilled
} from '@element-plus/icons-vue'
import {
  getTitlePage,
  addTitle,
  editTitle,
  deleteTitle,
  toggleTitleStatue,
  getTitleStatistics
} from '@/api/TitleManagementApi'

// ---------- 查询参数（增加 titleLevel） ----------
const queryParams = ref({
  titleName: '',
  titleLevel: null,
  status: null
})
const currentPage = ref(1)
const pageSize = ref(8)
const totalCount = ref(0)
const titleList = ref([])
const loading = ref(false)

// 统计数据
const enabledCount = ref(0)
const disabledCount = ref(0)
const maxLevel = ref(0)

// 表单相关
const showFormDialog = ref(false)
const editingTitle = ref(null)
const saving = ref(false)
const titleForm = ref({
  titleName: '',
  titleLevel: 1,
  description: '',
  enableStatus: 1
})

// ---------- 辅助函数 ----------
const getLevelColor = (level) => {
  if (level >= 5) return '#fef2e8'
  if (level >= 3) return '#eef5ff'
  return '#f0fdf4'
}

// ---------- API 调用 ----------
// 加载职称列表（后端分页，支持级别筛选）
const loadTitleList = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      titleName: queryParams.value.titleName || undefined,
      titleLevel: queryParams.value.titleLevel || undefined,
      statue: queryParams.value.status !== null && queryParams.value.status !== '' ? queryParams.value.status : undefined
    }
    const res = await getTitlePage(params)
    if (res.success) {
      titleList.value = res.data || []
      totalCount.value = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '获取职称列表失败')
      titleList.value = []
      totalCount.value = 0
    }
    // 加载统计数据（根据当前筛选条件）
    await loadStatistics()
  } catch (error) {
    console.error('加载职称列表失败:', error)
    ElMessage.error('加载失败，请检查网络')
  } finally {
    loading.value = false
  }
}

// 加载统计卡片数据（支持级别筛选）
const loadStatistics = async () => {
  try {
    const params = {
      titleName: queryParams.value.titleName || undefined,
      titleLevel: queryParams.value.titleLevel || undefined,
      statue: queryParams.value.status !== null && queryParams.value.status !== '' ? queryParams.value.status : undefined
    }
    const res = await getTitleStatistics(params)
    if (res.success) {
      enabledCount.value = res.data.enabled || 0
      disabledCount.value = res.data.disabled || 0
      maxLevel.value = res.data.maxLevel || 0
    } else {
      ElMessage.error('统计接口出错！')
    }
  } catch (error) {
    console.warn('统计接口调用失败，使用前端降级统计（仅当前页数据）')
  }
}

// 重置查询条件（重置级别）
const resetQuery = () => {
  queryParams.value = {
    titleName: '',
    titleLevel: null,
    status: null
  }
  currentPage.value = 1
  loadTitleList()
}

// 查询按钮
const handleSearch = () => {
  currentPage.value = 1
  loadTitleList()
}

// 分页改变
const handleCurrentChange = (page) => {
  currentPage.value = page
  loadTitleList()
}
const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadTitleList()
}

// ---------- 增删改操作 ----------
const resetForm = () => {
  titleForm.value = {
    titleName: '',
    titleLevel: 1,
    description: '',
    enableStatus: 1
  }
  editingTitle.value = null
}
const handleAdd = () => {
  resetForm()
  showFormDialog.value = true
}
const handleEdit = (title) => {
  editingTitle.value = title
  titleForm.value = {...title}
  showFormDialog.value = true
}
const handleSaveTitle = async () => {
  if (!titleForm.value.titleName.trim()) {
    ElMessage.warning('请填写职称名称')
    return
  }

  saving.value = true
  try {
    let res
    if (editingTitle.value) {
      const data = {...titleForm.value, id: editingTitle.value.id}
      res = await editTitle(data)
    } else {
      res = await addTitle(titleForm.value)
    }
    if (res.success) {
      ElMessage.success(editingTitle.value ? '职称已更新' : '职称添加成功')
      showFormDialog.value = false
      loadTitleList()
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
const toggleStatus = (title) => {
  const newStatus = title.enableStatus == 1 ? 0 : 1
  const action = newStatus == 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确定${action}职称“${title.titleName}”吗？`, '确认操作', {type: 'warning'})
      .then(async () => {
        try {
          const res = await toggleTitleStatue(title.id, newStatus)
          if (res.success) {
            title.enableStatus = newStatus
            ElMessage.success(`职称已${action}`)
            loadTitleList()
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
const handleSwitchChange = async (title, newVal) => {
  try {
    const res = await toggleTitleStatue(title.id, newVal)
    if (res.success) {
      title.enableStatus = newVal
      ElMessage.success(`职称状态已${newVal == 1 ? '启用' : '禁用'}`)
      loadTitleList()
    } else {
      title.enableStatus = title.enableStatus == 1 ? 0 : 1 // 回滚
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    title.enableStatus = title.enableStatus == 1 ? 0 : 1 // 回滚
    ElMessage.error('操作失败')
  }
}
const handleDeleteTitle = (title) => {
  ElMessageBox.confirm(`删除职称“${title.titleName}”后，关联医生信息将受影响，确认删除？`, '危险操作', {type: 'error'})
      .then(async () => {
        try {
          const res = await deleteTitle(title.id)
          if (res.success) {
            ElMessage.success('职称已删除')
            loadTitleList()
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
  loadTitleList()
})
</script>

<style scoped>
.title-management-page {
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

.title-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 18px;
}

.title-card {
  background: #fff;
  border-radius: 18px;
  padding: 20px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  transition: all 0.2s;
}

.title-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.08);
}

.disabled {
  opacity: 0.65;
  background: #f9fafb;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.title-badge {
  width: 58px;
  height: 58px;
  border-radius: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.level-num {
  font-size: 16px;
  font-weight: 700;
  color: #334155;
}

.title-name {
  font-size: 20px;
  font-weight: 600;
  margin: 6px 0 8px;
}

.level-info {
  margin-bottom: 12px;
}

.desc-box {
  display: flex;
  gap: 8px;
  font-size: 13px;
  color: #4b5563;
  background: #f8fafc;
  padding: 8px 10px;
  border-radius: 12px;
  margin: 10px 0;
}

.status-area {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.add-card {
  border: 2px dashed #e2e8f0;
  background: #fefefe;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  min-height: 210px;
}

.add-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: #94a3b8;
}

.level-hint {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}
</style>