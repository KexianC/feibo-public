<template>
  <div class="article-management-page">
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon stat-total"><el-icon :size="24"><Document /></el-icon></div>
        <div class="stat-info"><span class="stat-value">{{ total }}</span><span class="stat-label">文章总数</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-draft"><el-icon :size="24"><RemoveFilled /></el-icon></div>
        <div class="stat-info"><span class="stat-value">{{ visibleCount(0) }}</span><span class="stat-label">草稿</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-review"><el-icon :size="24"><Clock /></el-icon></div>
        <div class="stat-info"><span class="stat-value">{{ visibleCount(1) }}</span><span class="stat-label">待审核</span></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-pass"><el-icon :size="24"><CircleCheck /></el-icon></div>
        <div class="stat-info"><span class="stat-value">{{ visibleCount(2) + visibleCount(3) }}</span><span class="stat-label">可发布/已发布</span></div>
      </div>
    </div>

    <div class="action-bar">
      <div class="action-left">
        <el-input v-model="query.title" placeholder="搜索文章标题" :prefix-icon="Search" clearable class="filter-input" @keyup.enter="loadArticles" />
        <el-select v-model="query.status" placeholder="文章状态" clearable class="filter-select" @change="reloadFirstPage">
          <el-option label="草稿" :value="0" />
          <el-option label="待审核" :value="1" />
          <el-option label="审核通过" :value="2" />
          <el-option label="已发布" :value="3" />
          <el-option label="审核不通过" :value="4" />
        </el-select>
        <el-select v-model="query.agentType" placeholder="文章主题" clearable class="filter-select" @change="handleQueryAgentTypeChange">
          <el-option v-for="item in agentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.hospitalId" placeholder="所属医院" clearable filterable class="filter-select" @change="handleQueryHospitalChange">
          <el-option v-for="hospital in hospitalOptions" :key="hospital.id" :label="hospital.hospitalName" :value="hospital.id" />
        </el-select>
        <el-select v-model="query.departmentId" placeholder="所属科室" clearable filterable class="filter-select" :disabled="!query.hospitalId" @change="handleQueryDepartmentChange">
          <el-option v-for="department in queryDepartmentOptions" :key="department.id" :label="department.departmentName" :value="department.id" />
        </el-select>
        <el-select v-model="query.doctorId" placeholder="所属医生" clearable filterable class="filter-select" :disabled="!query.departmentId" @change="reloadFirstPage">
          <el-option v-for="doctor in queryDoctorOptions" :key="doctor.id" :label="doctor.doctorName" :value="doctor.id" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="reloadFirstPage">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
      <el-button type="primary" :icon="Plus" @click="handleCreateArticle">新建文章</el-button>
    </div>

    <div class="table-card">
      <el-table :data="articleList" stripe v-loading="loading" empty-text="暂无文章数据">
        <el-table-column prop="id" label="ID" width="190" align="center" />
        <el-table-column prop="title" label="文章标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="文章主题" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">{{ articleScopeLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="wordCount" label="字数" width="90" align="center" />
        <el-table-column label="合规" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="auditTagType(row.statusAudit)" size="small" round>{{ auditText(row.statusAudit) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" round>{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" align="center">
          <template #default="{ row }">{{ formatDate(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="330" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="auditExistingArticle(row)">合规</el-button>
            <el-button link type="warning" size="small" :disabled="row.status !== 1" @click="handleAudit(row)">审核</el-button>
            <el-button link type="success" size="small" :disabled="row.status !== 2" @click="openPublishDialog(row)">发布</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="reloadFirstPage"
          @current-change="loadArticles"
        />
      </div>
    </div>

    <el-dialog v-model="showArticleDialog" :title="editingArticle ? '编辑文章' : '新建文章'" width="820px" center :close-on-click-modal="false">
      <el-form :model="articleForm" label-width="90px">
        <el-form-item label="文章标题" required>
          <el-input v-model="articleForm.title" placeholder="请输入文章标题" />
        </el-form-item>
        <el-form-item label="文章主题" required>
          <el-segmented v-model="articleForm.agentType" :options="agentTypeOptions" @change="handleFormAgentTypeChange" />
        </el-form-item>
        <el-form-item label="所属医院" required>
          <el-select v-model="articleForm.hospitalId" placeholder="请选择医院" style="width: 100%;" filterable @change="handleFormHospitalChange">
            <el-option v-for="hospital in hospitalOptions" :key="hospital.id" :label="hospital.hospitalName" :value="hospital.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="articleForm.agentType >= AGENT_TYPE.DEPARTMENT" label="所属科室" required>
          <el-select v-model="articleForm.departmentId" placeholder="请选择科室" style="width: 100%;" filterable :disabled="!articleForm.hospitalId" @change="handleFormDepartmentChange">
            <el-option v-for="department in formDepartmentOptions" :key="department.id" :label="department.departmentName" :value="department.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="articleForm.agentType === AGENT_TYPE.DOCTOR" label="所属医生" required>
          <el-select v-model="articleForm.doctorId" placeholder="请选择医生" style="width: 100%;" filterable :disabled="!articleForm.departmentId">
            <el-option v-for="doctor in formDoctorOptions" :key="doctor.id" :label="doctor.doctorName" :value="doctor.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="正文内容" required>
          <el-input v-model="articleForm.content" type="textarea" :rows="14" placeholder="请输入文章正文" />
        </el-form-item>
        <el-form-item label="封面URL">
          <el-input v-model="articleForm.coverUrl" placeholder="可选：封面图片 URL" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="articleForm.status" style="width: 180px;">
            <el-option label="草稿" :value="0" />
            <el-option label="待审核" :value="1" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">GEO 与合规</el-divider>
        <el-form-item label="Sources">
          <el-input v-model="articleForm.sources" type="textarea" :rows="3" placeholder="JSON 数组或来源说明" />
        </el-form-item>
        <el-form-item label="Citations">
          <el-input v-model="articleForm.citations" type="textarea" :rows="3" placeholder="JSON 数组或引用说明" />
        </el-form-item>
        <el-form-item label="免责声明">
          <el-input v-model="articleForm.medicalDisclaimer" type="textarea" :rows="2" placeholder="仅用于健康科普，不能替代医生面诊、诊断或治疗建议" />
        </el-form-item>
        <el-form-item label="Schema JSON">
          <el-input v-model="articleForm.schemaJson" type="textarea" :rows="5" placeholder="MedicalWebPage 结构化摘要 JSON" />
        </el-form-item>
        <el-form-item label="合规审核">
          <div class="audit-field">
            <div class="audit-actions">
              <el-tag v-if="articleForm.statusAudit" :type="auditTagType(articleForm.statusAudit)">{{ auditText(articleForm.statusAudit) }}</el-tag>
              <el-button :loading="complianceAuditing" @click="runArticleFormCompliance">运行审核</el-button>
            </div>
            <el-input v-model="articleForm.statusAudit" type="textarea" :rows="4" placeholder="合规审核 JSON" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showArticleDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveArticle">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAuditDialog" title="文章审核" width="520px" center>
      <el-form :model="auditForm" label-width="90px">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="auditForm.status">
            <el-radio :label="2">审核通过</el-radio>
            <el-radio :label="4">审核不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="auditForm.status === 4" label="驳回原因" required>
          <el-input v-model="auditForm.reason" type="textarea" :rows="3" placeholder="请输入驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAuditDialog = false">取消</el-button>
        <el-button type="primary" :loading="auditing" @click="confirmAudit">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPublishDialog" title="发布文章" width="620px" center :close-on-click-modal="false">
      <el-form :model="publishForm" label-width="100px">
        <el-form-item label="文章标题">
          <span>{{ currentArticle?.title }}</span>
        </el-form-item>
        <el-form-item label="发布账号" required>
          <el-select v-model="publishForm.accountId" placeholder="请选择已登录账号" style="width: 100%;" filterable @change="handleAccountChange">
            <el-option
              v-for="account in accountOptions"
              :key="account.id"
              :label="`${platformText(account.type)} - ${account.userName || account.accountName}`"
              :value="account.id"
              :disabled="account.status === 0"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="封面图片">
          <el-input v-model="publishForm.img" placeholder="可选，默认使用文章封面 URL" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="publishForm.tags" multiple filterable allow-create default-first-option style="width: 100%;" placeholder="输入后回车创建标签" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="publishForm.remark" type="textarea" :rows="2" placeholder="可选备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPublishDialog = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="confirmPublish">确认发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Clock, Document, Plus, RemoveFilled, Search } from '@element-plus/icons-vue'
import { getDoctorAll } from '@/api/DoctorManagementApi'
import { getHospitalAll } from '@/api/HospitalManagementApi'
import { getDepartmentPage } from '@/api/DepartmentManagementApi'
import { getAccounts } from '@/api/AccountManagementApi'
import { addArticle, auditArticle, deleteArticle, editArticle, getArticlePage, publishArticle } from '@/api/ArticleManagementApi'
import { auditArticleCompliance, auditTextCompliance } from '@/api/GeoApi'

const AGENT_TYPE = {
  HOSPITAL: 1,
  DEPARTMENT: 2,
  DOCTOR: 3
}

const agentTypeOptions = [
  { label: '医院', value: AGENT_TYPE.HOSPITAL },
  { label: '科室', value: AGENT_TYPE.DEPARTMENT },
  { label: '医生', value: AGENT_TYPE.DOCTOR }
]

const hospitalOptions = ref([])
const departmentOptions = ref([])
const doctorOptions = ref([])
const accountOptions = ref([])
const articleList = ref([])
const loading = ref(false)
const saving = ref(false)
const auditing = ref(false)
const publishing = ref(false)
const complianceAuditing = ref(false)

const query = ref({ title: '', status: '', agentType: '', hospitalId: null, departmentId: null, doctorId: null })
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const showArticleDialog = ref(false)
const editingArticle = ref(null)
const emptyArticleForm = () => ({
  agentType: AGENT_TYPE.HOSPITAL,
  hospitalId: null,
  departmentId: null,
  doctorId: null,
  title: '',
  content: '',
  coverUrl: '',
  status: 0,
  sources: '',
  citations: '',
  medicalDisclaimer: '',
  schemaJson: '',
  statusAudit: ''
})
const articleForm = ref(emptyArticleForm())

const sameId = (left, right) => String(left ?? '') === String(right ?? '')

const filterDepartments = (hospitalId) => {
  if (!hospitalId) return []
  return departmentOptions.value.filter(item => sameId(item.hospitalId, hospitalId))
}

const filterDoctors = (hospitalId, departmentId) => {
  return doctorOptions.value.filter(item => {
    const hospitalMatched = !hospitalId || sameId(item.hospitalId, hospitalId)
    const departmentMatched = !departmentId || sameId(item.departmentId, departmentId)
    return hospitalMatched && departmentMatched
  })
}

const queryDepartmentOptions = computed(() => filterDepartments(query.value.hospitalId))
const queryDoctorOptions = computed(() => filterDoctors(query.value.hospitalId, query.value.departmentId))
const formDepartmentOptions = computed(() => filterDepartments(articleForm.value.hospitalId))
const formDoctorOptions = computed(() => filterDoctors(articleForm.value.hospitalId, articleForm.value.departmentId))

const showAuditDialog = ref(false)
const auditArticleId = ref(null)
const auditForm = ref({ status: 2, reason: '' })

const showPublishDialog = ref(false)
const currentArticle = ref(null)
const publishForm = ref({ accountId: null, type: null, cookieFile: '', img: '', tags: [], remark: '' })

const statusText = (status) => {
  const map = { 0: '草稿', 1: '待审核', 2: '审核通过', 3: '已发布', 4: '审核不通过' }
  return map[status] || '未知'
}

const statusTagType = (status) => {
  const map = { 0: 'info', 1: 'warning', 2: 'success', 3: 'success', 4: 'danger' }
  return map[status] || 'info'
}

const platformText = (type) => {
  const map = { 1: '知乎', 2: '小红书', 3: '今日头条', 4: '百家号' }
  return map[type] || '未知平台'
}

const formatDate = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const hospitalName = (hospitalId) => hospitalOptions.value.find(item => sameId(item.id, hospitalId))?.hospitalName || ''

const departmentName = (departmentId) => departmentOptions.value.find(item => sameId(item.id, departmentId))?.departmentName || ''

const doctorById = (doctorId) => doctorOptions.value.find(item => sameId(item.id, doctorId))

const inferAgentType = (article) => {
  if (article.agentType) return article.agentType
  if (article.doctorId) return AGENT_TYPE.DOCTOR
  if (article.departmentId) return AGENT_TYPE.DEPARTMENT
  if (article.hospitalId) return AGENT_TYPE.HOSPITAL
  return AGENT_TYPE.HOSPITAL
}

const articleScopeLabel = (article) => {
  const type = inferAgentType(article)
  const doctor = article.doctorId ? doctorById(article.doctorId) : null
  const hospital = article.hospitalName || hospitalName(article.hospitalId) || doctor?.hospitalName || (article.hospitalId ? `医院ID ${article.hospitalId}` : '')
  const department = article.departmentName || departmentName(article.departmentId) || doctor?.departmentName || (article.departmentId ? `科室ID ${article.departmentId}` : '')
  const doctorName = article.doctorName || doctor?.doctorName || (article.doctorId ? `医生ID ${article.doctorId}` : '')
  if (type === AGENT_TYPE.HOSPITAL) return hospital || '-'
  if (type === AGENT_TYPE.DEPARTMENT) return [hospital, department].filter(Boolean).join(' / ') || '-'
  return [hospital, department, doctorName].filter(Boolean).join(' / ') || '-'
}

const visibleCount = (status) => articleList.value.filter(item => item.status === status).length

const parseJsonValue = (value) => {
  if (!value) return null
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

const auditText = (statusAudit) => {
  const parsed = parseJsonValue(statusAudit)
  if (!parsed) return '未审核'
  if (parsed.passed === true) return '通过'
  if (parsed.passed === false) return '风险'
  return parsed.level || '已审核'
}

const auditTagType = (statusAudit) => {
  const parsed = parseJsonValue(statusAudit)
  if (!parsed) return 'info'
  return parsed.passed === true ? 'success' : 'danger'
}

const loadHospitals = async () => {
  try {
    const res = await getHospitalAll()
    hospitalOptions.value = res.success ? (res.data || []) : []
  } catch {
    ElMessage.error('医院列表加载失败')
  }
}

const loadDepartments = async () => {
  try {
    const res = await getDepartmentPage({ pageNum: 1, pageSize: 10000 })
    departmentOptions.value = res.success ? (res.data || []) : []
  } catch {
    ElMessage.error('科室列表加载失败')
  }
}

const loadDoctors = async () => {
  try {
    const res = await getDoctorAll()
    doctorOptions.value = res.success ? (res.data || []) : []
  } catch {
    ElMessage.error('医生列表加载失败')
  }
}

const loadAccounts = async () => {
  try {
    const res = await getAccounts()
    accountOptions.value = res.code === 200 ? (res.data || []) : []
  } catch {
    accountOptions.value = []
  }
}

const loadArticles = async () => {
  loading.value = true
  try {
    const res = await getArticlePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      title: query.value.title || undefined,
      status: query.value.status === '' ? undefined : query.value.status,
      agentType: query.value.agentType === '' ? undefined : query.value.agentType,
      hospitalId: query.value.hospitalId || undefined,
      departmentId: query.value.departmentId || undefined,
      doctorId: query.value.doctorId || undefined
    })
    if (res.success) {
      articleList.value = res.data || []
      total.value = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '文章列表加载失败')
    }
  } catch {
    ElMessage.error('文章列表加载失败')
  } finally {
    loading.value = false
  }
}

const reloadFirstPage = () => {
  pageNum.value = 1
  loadArticles()
}

const resetQuery = () => {
  query.value = { title: '', status: '', agentType: '', hospitalId: null, departmentId: null, doctorId: null }
  reloadFirstPage()
}

const handleQueryAgentTypeChange = () => {
  if (query.value.agentType === AGENT_TYPE.HOSPITAL) {
    query.value.departmentId = null
    query.value.doctorId = null
  } else if (query.value.agentType === AGENT_TYPE.DEPARTMENT) {
    query.value.doctorId = null
  }
  reloadFirstPage()
}

const handleQueryHospitalChange = () => {
  query.value.departmentId = null
  query.value.doctorId = null
  reloadFirstPage()
}

const handleQueryDepartmentChange = () => {
  query.value.doctorId = null
  reloadFirstPage()
}

const resetArticleForm = () => {
  editingArticle.value = null
  articleForm.value = emptyArticleForm()
}

const handleCreateArticle = () => {
  resetArticleForm()
  showArticleDialog.value = true
}

const handleFormAgentTypeChange = () => {
  if (articleForm.value.agentType < AGENT_TYPE.DEPARTMENT) {
    articleForm.value.departmentId = null
  }
  if (articleForm.value.agentType !== AGENT_TYPE.DOCTOR) {
    articleForm.value.doctorId = null
  }
}

const handleFormHospitalChange = () => {
  articleForm.value.departmentId = null
  articleForm.value.doctorId = null
}

const handleFormDepartmentChange = () => {
  articleForm.value.doctorId = null
}

const buildArticleForm = (row) => {
  const agentType = inferAgentType(row)
  const doctor = row.doctorId ? doctorById(row.doctorId) : null
  const department = row.departmentId ? departmentOptions.value.find(item => sameId(item.id, row.departmentId)) : null
  const hospitalId = row.hospitalId || doctor?.hospitalId || department?.hospitalId || null
  const departmentId = row.departmentId || doctor?.departmentId || null
  return {
    ...emptyArticleForm(),
    ...row,
    agentType,
    hospitalId,
    departmentId: agentType >= AGENT_TYPE.DEPARTMENT ? departmentId : null,
    doctorId: agentType === AGENT_TYPE.DOCTOR ? row.doctorId : null
  }
}

const handleEdit = (row) => {
  editingArticle.value = row
  articleForm.value = buildArticleForm(row)
  showArticleDialog.value = true
}

const runArticleFormCompliance = async () => {
  if (!articleForm.value.title && !articleForm.value.content) {
    ElMessage.warning('请填写标题或正文')
    return
  }
  complianceAuditing.value = true
  try {
    const res = await auditTextCompliance({
      title: articleForm.value.title,
      content: articleForm.value.content
    })
    if (res.success) {
      articleForm.value.statusAudit = JSON.stringify(res.data || {}, null, 2)
      ElMessage.success(res.data?.passed ? '合规审核通过' : '发现合规风险')
    } else {
      ElMessage.error(res.message || '合规审核失败')
    }
  } catch {
    ElMessage.error('合规审核失败')
  } finally {
    complianceAuditing.value = false
  }
}

const auditExistingArticle = async (row) => {
  try {
    const res = await auditArticleCompliance(row.id)
    if (res.success) {
      ElMessage.success(res.data?.passed ? '合规审核通过' : '发现合规风险')
      const statusAudit = JSON.stringify(res.data || {})
      await editArticle({ ...row, statusAudit })
      await loadArticles()
    } else {
      ElMessage.error(res.message || '合规审核失败')
    }
  } catch {
    ElMessage.error('合规审核失败')
  }
}

const validateArticleForm = () => {
  if (!articleForm.value.title?.trim() || !articleForm.value.content?.trim()) {
    ElMessage.warning('请填写标题和正文')
    return false
  }
  if (!articleForm.value.hospitalId) {
    ElMessage.warning('请选择所属医院')
    return false
  }
  if (articleForm.value.agentType >= AGENT_TYPE.DEPARTMENT && !articleForm.value.departmentId) {
    ElMessage.warning('请选择所属科室')
    return false
  }
  if (articleForm.value.agentType === AGENT_TYPE.DOCTOR && !articleForm.value.doctorId) {
    ElMessage.warning('请选择所属医生')
    return false
  }
  return true
}

const buildArticlePayload = () => ({
  ...articleForm.value,
  agentType: articleForm.value.agentType,
  hospitalId: articleForm.value.hospitalId,
  departmentId: articleForm.value.agentType >= AGENT_TYPE.DEPARTMENT ? articleForm.value.departmentId : null,
  doctorId: articleForm.value.agentType === AGENT_TYPE.DOCTOR ? articleForm.value.doctorId : null
})

const saveArticle = async () => {
  if (!validateArticleForm()) return
  saving.value = true
  try {
    const action = editingArticle.value ? editArticle : addArticle
    const res = await action(buildArticlePayload())
    if (res.success) {
      ElMessage.success(editingArticle.value ? '文章已更新' : '文章已创建')
      showArticleDialog.value = false
      await loadArticles()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleAudit = (row) => {
  auditArticleId.value = row.id
  auditForm.value = { status: 2, reason: '' }
  showAuditDialog.value = true
}

const confirmAudit = async () => {
  if (auditForm.value.status === 4 && !auditForm.value.reason.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  auditing.value = true
  try {
    const res = await auditArticle({
      articleId: auditArticleId.value,
      status: auditForm.value.status,
      reason: auditForm.value.reason
    })
    if (res.success) {
      ElMessage.success(auditForm.value.status === 2 ? '审核已通过' : '文章已驳回')
      showAuditDialog.value = false
      await loadArticles()
    } else {
      ElMessage.error(res.message || '审核失败')
    }
  } catch {
    ElMessage.error('审核失败')
  } finally {
    auditing.value = false
  }
}

const openPublishDialog = async (row) => {
  currentArticle.value = row
  publishForm.value = { accountId: null, type: null, cookieFile: '', img: row.coverUrl || '', tags: [], remark: '' }
  await loadAccounts()
  showPublishDialog.value = true
}

const handleAccountChange = (accountId) => {
  const account = accountOptions.value.find(item => item.id === accountId)
  publishForm.value.type = account?.type || null
  publishForm.value.cookieFile = account?.filePath || account?.cookie_file || ''
}

const confirmPublish = async () => {
  if (!publishForm.value.accountId || !publishForm.value.type || !publishForm.value.cookieFile) {
    ElMessage.warning('请选择可用账号')
    return
  }
  publishing.value = true
  try {
    const res = await publishArticle({
      articleId: currentArticle.value.id,
      type: publishForm.value.type,
      cookieFile: publishForm.value.cookieFile,
      img: publishForm.value.img,
      tags: publishForm.value.tags,
      remark: publishForm.value.remark
    })
    if (res.code === 200) {
      ElMessage.success('文章发布成功')
      showPublishDialog.value = false
      await loadArticles()
    } else {
      ElMessage.error(res.msg || '发布失败')
    }
  } catch {
    ElMessage.error('发布失败，请检查发布服务和账号状态')
  } finally {
    publishing.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除文章“${row.title}”吗？`, '删除确认', { type: 'error' })
    const res = await deleteArticle(row.id)
    if (res.success) {
      ElMessage.success('文章已删除')
      await loadArticles()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(async () => {
  await Promise.all([loadHospitals(), loadDepartments(), loadDoctors()])
  await loadArticles()
})
</script>

<style scoped>
.article-management-page {
  max-width: 1400px;
  margin: 0 auto;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.stat-card,
.action-bar,
.table-card {
  background: #fff;
  border: 1px solid #f0f2f5;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.stat-card {
  min-height: 88px;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-total { background: #e8f4fd; color: #4f6ef7; }
.stat-draft { background: #f3f4f6; color: #6b7280; }
.stat-review { background: #fff7e6; color: #f59e0b; }
.stat-pass { background: #e6f9ee; color: #22c55e; }

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
  margin-top: 4px;
}

.action-bar {
  padding: 14px 16px;
  margin-bottom: 18px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.action-left {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.filter-input {
  width: 240px;
}

.filter-select {
  width: 160px;
}

.table-card {
  padding: 14px 16px 12px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.audit-field {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.audit-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

@media (max-width: 1100px) {
  .stat-cards {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }

  .action-bar {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 720px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }

  .filter-input,
  .filter-select,
  .action-left,
  .action-bar > .el-button {
    width: 100%;
  }
}
</style>
