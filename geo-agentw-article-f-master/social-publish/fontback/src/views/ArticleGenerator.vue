<template>
  <div class="article-generator-page">
    <section class="generator-main">
      <div class="toolbar">
        <div>
          <h2>AI 医学文章助手</h2>
        </div>
        <div class="scope-controls">
          <el-segmented v-model="selectedAgentType" :options="agentTypeOptions" @change="handleAgentTypeChange" />
          <el-select v-model="selectedHospitalId" placeholder="选择医院" filterable clearable class="scope-select" @change="handleHospitalChange">
            <el-option
              v-for="hospital in hospitalList"
              :key="hospital.id"
              :label="scopeOptionLabel(hospital.hospitalName, hospital.knowledgeStatus)"
              :value="hospital.id"
              :disabled="!isKnowledgeAvailable(hospital.knowledgeStatus)"
            />
          </el-select>
          <el-select
            v-if="selectedAgentType >= AGENT_TYPE.DEPARTMENT"
            v-model="selectedDepartmentId"
            placeholder="选择科室"
            filterable
            clearable
            class="scope-select"
            :disabled="!selectedHospitalId"
            @change="handleDepartmentChange"
          >
            <el-option
              v-for="department in departmentList"
              :key="department.id"
              :label="scopeOptionLabel(department.departmentName, department.knowledgeStatus)"
              :value="department.id"
              :disabled="!isKnowledgeAvailable(department.knowledgeStatus)"
            />
          </el-select>
          <el-select
            v-if="selectedAgentType === AGENT_TYPE.DOCTOR"
            v-model="selectedDoctorId"
            placeholder="选择医生"
            filterable
            clearable
            class="scope-select"
            :disabled="!selectedDepartmentId"
            @change="loadHistoryArticles"
          >
            <el-option
              v-for="doctor in doctorList"
              :key="doctor.id"
              :label="scopeOptionLabel(`${doctor.doctorName} - ${doctor.titleName || '未设置职称'}`, doctor.knowledgeStatus)"
              :value="doctor.id"
              :disabled="!isKnowledgeAvailable(doctor.knowledgeStatus)"
            />
          </el-select>
        </div>
      </div>

      <div class="scope-summary">
        <el-tag size="small" effect="light">{{ agentTypeText(selectedAgentType) }}</el-tag>
        <span>{{ currentScopeLabel || '未选择知识库' }}</span>
      </div>

      <div class="editor-grid">
        <section class="writer-panel">
          <el-input
            v-model="prompt"
            type="textarea"
            :rows="5"
            resize="none"
            placeholder="请输入写作需求，例如：围绕皮肤科夏季防晒写一篇适合公众号发布的科普文章"
          />
          <div class="writer-actions">
            <el-radio-group v-model="saveStatus">
              <el-radio-button :label="0">保存草稿</el-radio-button>
              <el-radio-button :label="1">提交审核</el-radio-button>
            </el-radio-group>
            <el-button type="primary" :loading="generating" :disabled="!canGenerate" @click="handleGenerate">
              <el-icon><Promotion /></el-icon>
              生成文章
            </el-button>
          </div>

          <el-divider />

          <el-form label-width="88px" class="article-form">
            <el-form-item label="标题">
              <el-input v-model="currentArticle.title" placeholder="生成后可继续修改标题" />
            </el-form-item>
            <el-form-item label="正文">
              <el-input v-model="currentArticle.content" type="textarea" :rows="16" placeholder="生成后的文章正文会显示在这里" />
            </el-form-item>
            <el-form-item label="封面">
              <el-input v-model="currentArticle.coverUrl" placeholder="可选：封面图片 URL" />
            </el-form-item>
            <el-form-item label="Sources">
              <el-input v-model="currentArticle.sources" type="textarea" :rows="2" placeholder="来源 JSON 或说明" />
            </el-form-item>
            <el-form-item label="Citations">
              <el-input v-model="currentArticle.citations" type="textarea" :rows="2" placeholder="引用 JSON 或说明" />
            </el-form-item>
            <el-form-item label="免责声明">
              <el-input v-model="currentArticle.medicalDisclaimer" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="Schema">
              <el-input v-model="currentArticle.schemaJson" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item label="合规">
              <div class="audit-field">
                <div class="audit-actions">
                  <el-tag v-if="currentArticle.statusAudit" :type="auditTagType(currentArticle.statusAudit)">
                    {{ auditText(currentArticle.statusAudit) }}
                  </el-tag>
                  <el-button :loading="auditing" @click="runComplianceAudit">运行审核</el-button>
                </div>
                <el-input v-model="currentArticle.statusAudit" type="textarea" :rows="3" />
              </div>
            </el-form-item>
          </el-form>

          <div class="article-actions">
            <el-tag v-if="currentArticle.id" :type="statusTagType(currentArticle.status)">
              {{ statusText(currentArticle.status) }}
            </el-tag>
            <el-button :disabled="!currentArticle.id" :loading="saving" @click="saveCurrentArticle">保存修改</el-button>
            <el-button :disabled="!currentArticle.content" @click="clearCurrent">清空</el-button>
          </div>
        </section>

        <aside class="history-panel">
          <div class="history-header">
            <strong>历史文章</strong>
            <el-button link type="primary" :loading="historyLoading" @click="loadHistoryArticles">
              <el-icon><RefreshRight /></el-icon>
              刷新
            </el-button>
          </div>
          <div class="history-list" v-loading="historyLoading">
            <button
              v-for="article in historyArticles"
              :key="article.id"
              type="button"
              class="history-item"
              @click="loadArticle(article)"
            >
              <span class="history-title">{{ article.title || '未命名文章' }}</span>
              <span class="history-meta">{{ statusText(article.status) }} · {{ formatDate(article.createdTime) }}</span>
              <span class="history-preview">{{ article.content || '' }}</span>
            </button>
            <el-empty v-if="!historyLoading && historyArticles.length === 0" description="暂无历史文章" :image-size="90" />
          </div>
        </aside>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, RefreshRight } from '@element-plus/icons-vue'
import { getHospitalAll } from '@/api/HospitalManagementApi'
import { getDepartmentAll } from '@/api/DepartmentManagementApi'
import { getDoctorPageOther } from '@/api/DoctorManagementApi'
import { editArticle, generateArticle, getArticleHistory } from '@/api/ArticleManagementApi'
import { auditTextCompliance } from '@/api/GeoApi'

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

const hospitalList = ref([])
const departmentList = ref([])
const doctorList = ref([])
const selectedAgentType = ref(AGENT_TYPE.HOSPITAL)
const selectedHospitalId = ref(null)
const selectedDepartmentId = ref(null)
const selectedDoctorId = ref(null)
const prompt = ref('')
const saveStatus = ref(0)
const generating = ref(false)
const saving = ref(false)
const auditing = ref(false)
const historyLoading = ref(false)
const historyArticles = ref([])

const emptyArticle = () => ({
  id: null,
  agentType: selectedAgentType.value,
  hospitalId: selectedHospitalId.value,
  departmentId: selectedDepartmentId.value,
  doctorId: selectedDoctorId.value,
  title: '',
  content: '',
  coverUrl: '',
  sources: '',
  citations: '',
  medicalDisclaimer: '',
  schemaJson: '',
  statusAudit: '',
  status: 0
})

const currentArticle = ref(emptyArticle())

const currentScopeLabel = computed(() => {
  const hospital = hospitalList.value.find(item => item.id === selectedHospitalId.value)
  const department = departmentList.value.find(item => item.id === selectedDepartmentId.value)
  const doctor = doctorList.value.find(item => item.id === selectedDoctorId.value)
  if (selectedAgentType.value === AGENT_TYPE.HOSPITAL) return hospital?.hospitalName || ''
  if (selectedAgentType.value === AGENT_TYPE.DEPARTMENT) return [hospital?.hospitalName, department?.departmentName].filter(Boolean).join(' / ')
  return [hospital?.hospitalName, department?.departmentName, doctor?.doctorName].filter(Boolean).join(' / ')
})

const hasValidScope = computed(() => {
  if (selectedAgentType.value === AGENT_TYPE.HOSPITAL) return Boolean(selectedHospitalId.value)
  if (selectedAgentType.value === AGENT_TYPE.DEPARTMENT) return Boolean(selectedHospitalId.value && selectedDepartmentId.value)
  return Boolean(selectedHospitalId.value && selectedDepartmentId.value && selectedDoctorId.value)
})

const canGenerate = computed(() => hasValidScope.value && prompt.value.trim() && !generating.value)

const scopePayload = computed(() => ({
  agentType: selectedAgentType.value,
  hospitalId: selectedHospitalId.value || undefined,
  departmentId: selectedAgentType.value >= AGENT_TYPE.DEPARTMENT ? selectedDepartmentId.value || undefined : undefined,
  doctorId: selectedAgentType.value === AGENT_TYPE.DOCTOR ? selectedDoctorId.value || undefined : undefined
}))

const agentTypeText = (agentType) => {
  const map = { 1: '医院知识库', 2: '科室知识库', 3: '医生知识库' }
  return map[agentType] || '知识库'
}

const isKnowledgeAvailable = (knowledgeStatus) => Number(knowledgeStatus) === 1

const scopeOptionLabel = (name, knowledgeStatus) => `${name || '-'}${isKnowledgeAvailable(knowledgeStatus) ? '' : '（知识库不可用）'}`

const statusText = (status) => {
  const map = { 0: '草稿', 1: '待审核', 2: '审核通过', 3: '已发布', 4: '审核不通过' }
  return map[status] || '未知'
}

const statusTagType = (status) => {
  const map = { 0: 'info', 1: 'warning', 2: 'success', 3: 'success', 4: 'danger' }
  return map[status] || 'info'
}

const formatDate = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

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

const loadHospitalList = async () => {
  try {
    const res = await getHospitalAll()
    hospitalList.value = res.success ? (res.data || []) : []
  } catch (error) {
    ElMessage.error('医院列表加载失败')
  }
}

const loadDepartmentList = async () => {
  departmentList.value = []
  if (!selectedHospitalId.value) return
  try {
    const res = await getDepartmentAll(selectedHospitalId.value)
    departmentList.value = res.success ? (res.data || []) : []
  } catch (error) {
    ElMessage.error('科室列表加载失败')
  }
}

const loadDoctorList = async () => {
  doctorList.value = []
  if (!selectedHospitalId.value || !selectedDepartmentId.value) return
  try {
    const res = await getDoctorPageOther({
      pageNum: 1,
      pageSize: 1000,
      hospital: selectedHospitalId.value,
      department: selectedDepartmentId.value
    })
    doctorList.value = res.success ? (res.data || []) : []
  } catch (error) {
    ElMessage.error('医生列表加载失败')
  }
}

const handleAgentTypeChange = async () => {
  selectedDepartmentId.value = null
  selectedDoctorId.value = null
  departmentList.value = []
  doctorList.value = []
  if (selectedAgentType.value >= AGENT_TYPE.DEPARTMENT) {
    await loadDepartmentList()
  }
  await loadHistoryArticles()
}

const handleHospitalChange = async () => {
  selectedDepartmentId.value = null
  selectedDoctorId.value = null
  doctorList.value = []
  await loadDepartmentList()
  await loadHistoryArticles()
}

const handleDepartmentChange = async () => {
  selectedDoctorId.value = null
  await loadDoctorList()
  await loadHistoryArticles()
}

const loadHistoryArticles = async () => {
  if (!hasValidScope.value) {
    historyArticles.value = []
    return
  }
  historyLoading.value = true
  try {
    const res = await getArticleHistory(scopePayload.value)
    historyArticles.value = res.success ? (res.data || []) : []
  } catch (error) {
    ElMessage.error('历史文章加载失败')
  } finally {
    historyLoading.value = false
  }
}

const handleGenerate = async () => {
  if (!canGenerate.value) return
  generating.value = true
  try {
    const res = await generateArticle({
      ...scopePayload.value,
      prompt: prompt.value.trim(),
      coverUrl: currentArticle.value.coverUrl || '',
      status: saveStatus.value
    })
    if (res.success) {
      currentArticle.value = { ...emptyArticle(), ...(res.data || {}) }
      ElMessage.success(saveStatus.value === 1 ? '文章已生成并提交审核' : '文章已生成并保存草稿')
      await loadHistoryArticles()
    } else {
      ElMessage.error(res.message || '文章生成失败')
    }
  } catch (error) {
    ElMessage.error('文章生成失败，请检查知识库和模型配置')
  } finally {
    generating.value = false
  }
}

const saveCurrentArticle = async () => {
  if (!currentArticle.value.id) return
  if (!currentArticle.value.title || !currentArticle.value.content) {
    ElMessage.warning('标题和正文不能为空')
    return
  }
  saving.value = true
  try {
    const res = await editArticle({
      ...currentArticle.value,
      ...scopePayload.value
    })
    if (res.success) {
      ElMessage.success('文章已保存')
      await loadHistoryArticles()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const runComplianceAudit = async () => {
  if (!currentArticle.value.title && !currentArticle.value.content) {
    ElMessage.warning('请先生成或填写文章内容')
    return
  }
  auditing.value = true
  try {
    const res = await auditTextCompliance({
      title: currentArticle.value.title,
      content: currentArticle.value.content
    })
    if (res.success) {
      currentArticle.value.statusAudit = JSON.stringify(res.data || {}, null, 2)
      ElMessage.success(res.data?.passed ? '合规审核通过' : '发现合规风险')
    } else {
      ElMessage.error(res.message || '合规审核失败')
    }
  } catch {
    ElMessage.error('合规审核失败')
  } finally {
    auditing.value = false
  }
}

const loadArticle = (article) => {
  currentArticle.value = { ...emptyArticle(), ...article }
}

const clearCurrent = () => {
  currentArticle.value = emptyArticle()
}

onMounted(async () => {
  await loadHospitalList()
})
</script>

<style scoped>
.article-generator-page {
  max-width: 1400px;
  margin: 0 auto;
}

.generator-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar,
.writer-panel,
.history-panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 16px 18px;
}

.toolbar h2 {
  margin: 0;
  font-size: 20px;
  color: #111827;
}

.scope-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.scope-select {
  width: 240px;
}

.scope-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  color: #4b5563;
  font-size: 13px;
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
}

.writer-panel {
  padding: 18px;
}

.writer-actions,
.article-actions,
.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
}

.article-form {
  margin-top: 4px;
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

.history-panel {
  min-height: 640px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.history-header {
  margin: 0;
  padding: 14px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.history-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.history-item {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 5px;
  text-align: left;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 10px;
  cursor: pointer;
}

.history-item:hover {
  border-color: #2563eb;
  background: #f8fafc;
}

.history-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-meta {
  color: #6b7280;
  font-size: 12px;
}

.history-preview {
  color: #4b5563;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1100px) {
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .scope-controls {
    justify-content: flex-start;
  }

  .scope-select {
    width: 100%;
  }

  .editor-grid {
    grid-template-columns: 1fr;
  }

  .history-panel {
    min-height: 360px;
  }
}
</style>
