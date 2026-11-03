<template>
  <div class="knowledge-base-page">
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon stat-icon-hospital">
          <el-icon :size="24"><OfficeBuilding /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ hospitalTotal }}</span>
          <span class="stat-label">医院库</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon stat-icon-department">
          <el-icon :size="24"><FirstAidKit /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ departmentTotal }}</span>
          <span class="stat-label">科室库</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon stat-icon-doctor">
          <el-icon :size="24"><User /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ doctorTotal }}</span>
          <span class="stat-label">医生库</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon stat-icon-file">
          <el-icon :size="24"><Document /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ totalFileCount }}</span>
          <span class="stat-label">知识文件</span>
        </div>
      </div>
    </div>

    <div class="action-bar">
      <div class="action-left">
        <el-segmented v-model="activeAgentType" :options="agentTabs" @change="handleAgentTypeChange" />

        <el-input
          v-model="queryParams.keyword"
          :placeholder="keywordPlaceholder"
          :prefix-icon="Search"
          clearable
          class="filter-input"
          @keyup.enter="handleSearch"
        />

        <el-select
          v-if="activeAgentType !== AGENT_TYPE.HOSPITAL"
          v-model="queryParams.hospitalId"
          placeholder="所属医院"
          clearable
          filterable
          class="filter-select"
          @change="handleHospitalChange"
        >
          <el-option v-for="h in hospitalOptions" :key="h.id" :label="h.hospitalName" :value="h.id" />
        </el-select>

        <el-select
          v-if="activeAgentType === AGENT_TYPE.DOCTOR"
          v-model="queryParams.departmentId"
          placeholder="所属科室"
          clearable
          filterable
          class="filter-select"
          :disabled="!queryParams.hospitalId"
        >
          <el-option v-for="d in departmentOptions" :key="d.id" :label="d.departmentName" :value="d.id" />
        </el-select>

        <el-select
          v-if="activeAgentType === AGENT_TYPE.DOCTOR"
          v-model="queryParams.titleId"
          placeholder="职称"
          clearable
          filterable
          class="filter-select filter-select-sm"
        >
          <el-option v-for="t in titleOptions" :key="t.id" :label="t.titleName" :value="t.id" />
        </el-select>

        <el-select
          v-model="queryParams.knowledgeStatus"
          placeholder="知识库状态"
          clearable
          class="filter-select filter-select-sm"
        >
          <el-option label="未上传" :value="0" />
          <el-option label="可用" :value="1" />
          <el-option label="存在异常" :value="2" />
        </el-select>

        <div class="query-buttons">
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </div>
      </div>

      <div class="action-right">
        <el-button type="primary" :icon="Upload" @click="openUploadDialog()">上传文件</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table
        v-if="activeAgentType === AGENT_TYPE.HOSPITAL"
        :data="hospitalList"
        stripe
        v-loading="loading"
        empty-text="暂无医院数据"
        class="knowledge-table"
      >
        <el-table-column prop="hospitalName" label="医院名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="hospitalLevel" label="等级" width="120" align="center">
          <template #default="{ row }">{{ row.hospitalLevel || '-' }}</template>
        </el-table-column>
        <el-table-column prop="hospitalType" label="类型" width="120" align="center">
          <template #default="{ row }">{{ row.hospitalType || '-' }}</template>
        </el-table-column>
        <el-table-column label="地区" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ [row.province, row.city, row.district].filter(Boolean).join(' / ') || '-' }}</template>
        </el-table-column>
        <el-table-column label="文件数量" width="110" align="center">
          <template #default="{ row }"><el-tag size="small" round>{{ row.fileCount || 0 }}</el-tag></template>
        </el-table-column>
        <el-table-column label="知识库状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="knowledgeStatusType(row.knowledgeStatus)" size="small" round effect="light">
              {{ knowledgeStatusText(row.knowledgeStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewScopeFiles(row, AGENT_TYPE.HOSPITAL)">查看文件</el-button>
            <el-button link type="primary" size="small" @click="openUploadDialog(row, AGENT_TYPE.HOSPITAL)">上传</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else-if="activeAgentType === AGENT_TYPE.DEPARTMENT"
        :data="departmentList"
        stripe
        v-loading="loading"
        empty-text="暂无科室数据"
        class="knowledge-table"
      >
        <el-table-column prop="departmentName" label="科室名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="hospitalName" label="所属医院" min-width="170" show-overflow-tooltip />
        <el-table-column prop="departmentDesc" label="科室说明" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.departmentDesc || '-' }}</template>
        </el-table-column>
        <el-table-column label="文件数量" width="110" align="center">
          <template #default="{ row }"><el-tag size="small" round>{{ row.fileCount || 0 }}</el-tag></template>
        </el-table-column>
        <el-table-column label="知识库状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="knowledgeStatusType(row.knowledgeStatus)" size="small" round effect="light">
              {{ knowledgeStatusText(row.knowledgeStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewScopeFiles(row, AGENT_TYPE.DEPARTMENT)">查看文件</el-button>
            <el-button link type="primary" size="small" @click="openUploadDialog(row, AGENT_TYPE.DEPARTMENT)">上传</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else
        :data="doctorList"
        stripe
        v-loading="loading"
        empty-text="暂无医生数据"
        class="knowledge-table"
      >
        <el-table-column prop="doctorName" label="医生姓名" min-width="120" />
        <el-table-column prop="departmentName" label="科室" min-width="130" show-overflow-tooltip />
        <el-table-column prop="titleName" label="职称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="hospitalName" label="所属医院" min-width="170" show-overflow-tooltip />
        <el-table-column prop="specialty" label="擅长" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.specialty || '-' }}</template>
        </el-table-column>
        <el-table-column label="文件数量" width="110" align="center">
          <template #default="{ row }"><el-tag size="small" round>{{ row.fileCount || 0 }}</el-tag></template>
        </el-table-column>
        <el-table-column label="知识库状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="knowledgeStatusType(row.knowledgeStatus)" size="small" round effect="light">
              {{ knowledgeStatusText(row.knowledgeStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewScopeFiles(row, AGENT_TYPE.DOCTOR)">查看文件</el-button>
            <el-button link type="primary" size="small" @click="openUploadDialog(row, AGENT_TYPE.DOCTOR)">上传</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[6, 10, 15, 20]"
          :total="currentTotal"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <el-dialog
      v-model="showFilesDialog"
      :title="`${currentScopeLabel} 的知识库文件`"
      width="min(1080px, 92vw)"
      center
      destroy-on-close
      class="file-dialog"
    >
      <div class="file-dialog-content">
        <div class="file-filters">
          <div class="file-filter-left">
            <el-input
              v-model="fileQuery.fileName"
              placeholder="搜索文件名"
              :prefix-icon="Search"
              clearable
              size="small"
              class="file-search-input"
              @keyup.enter="handleFileSearch"
            />
            <el-select
              v-model="fileQuery.status"
              placeholder="处理状态"
              clearable
              size="small"
              class="file-status-select"
              @change="handleFileSearch"
            >
              <el-option label="待处理" :value="0" />
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="2" />
              <el-option label="处理中" :value="3" />
            </el-select>
            <el-button size="small" :icon="Search" @click="handleFileSearch">查询</el-button>
            <el-button size="small" :icon="Refresh" @click="refreshFileList" :loading="fileLoading" :disabled="fileLoading">
              刷新
            </el-button>
          </div>

          <div class="file-filter-right">
            <el-button size="small" type="primary" :icon="Upload" @click="openUploadDialog(currentScope, currentScopeType)">
              上传文件
            </el-button>
          </div>
        </div>

        <el-table :data="fileList" stripe v-loading="fileLoading" empty-text="暂无文件，请点击上传" class="file-table">
          <el-table-column prop="fileName" label="文件名" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="file-cell">
                <el-icon :size="18" :color="getFileIconColor(row.contentType)"><Document /></el-icon>
                <span class="file-name">{{ row.fileName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="智能体" width="95" align="center">
            <template #default="{ row }"><el-tag size="small" effect="light">{{ agentTypeText(row.agentType) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="归属关系" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ agentScopeText(row) }}</template>
          </el-table-column>
          <el-table-column label="大小" width="100" align="center">
            <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column prop="contentType" label="类型" width="80" align="center">
            <template #default="{ row }"><el-tag size="small">{{ getFileType(row.contentType) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="分块数" width="85" align="center" />
          <el-table-column prop="status" label="处理状态" width="105" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small" round effect="light">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="失败原因" min-width="150" show-overflow-tooltip>
            <template #default="{ row }"><span class="error-text">{{ row.errorMessage || '-' }}</span></template>
          </el-table-column>
          <el-table-column prop="createdTime" label="上传时间" width="170" align="center">
            <template #default="{ row }">{{ formatDateTime(row.createdTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="145" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" :disabled="row.status === 1" @click="handleReprocess(row)">
                重新处理
              </el-button>
              <el-button link type="danger" size="small" @click="handleDeleteFile(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap file-pagination">
          <el-pagination
            v-model:current-page="filePage"
            v-model:page-size="filePageSize"
            :page-sizes="[8, 12, 20, 30]"
            :total="fileTotal"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="handleFileSizeChange"
            @current-change="handleFileCurrentChange"
          />
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="showUploadDialog"
      title="上传知识库文件"
      width="min(560px, 92vw)"
      :close-on-click-modal="false"
      center
      class="upload-dialog"
    >
      <el-form :model="uploadForm" label-width="90px" label-position="left">
        <template v-if="isScopeLocked">
          <el-form-item label="智能体" required>
            <el-input :value="lockedScopeLabel" disabled />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="智能体类型" required>
            <el-radio-group v-model="uploadForm.agentType" @change="handleUploadAgentTypeChange">
              <el-radio-button :label="AGENT_TYPE.HOSPITAL">医院</el-radio-button>
              <el-radio-button :label="AGENT_TYPE.DEPARTMENT">科室</el-radio-button>
              <el-radio-button :label="AGENT_TYPE.DOCTOR">医生</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="所属医院" required>
            <el-select v-model="uploadForm.hospitalId" placeholder="请选择医院" class="full-width" filterable @change="handleUploadHospitalChange">
              <el-option v-for="h in hospitalOptions" :key="h.id" :label="h.hospitalName" :value="h.id" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="uploadForm.agentType >= AGENT_TYPE.DEPARTMENT" label="所属科室" required>
            <el-select
              v-model="uploadForm.departmentId"
              placeholder="请选择科室"
              class="full-width"
              filterable
              :disabled="!uploadForm.hospitalId"
              @change="handleUploadDepartmentChange"
            >
              <el-option v-for="d in uploadDepartmentOptions" :key="d.id" :label="d.departmentName" :value="d.id" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="uploadForm.agentType === AGENT_TYPE.DOCTOR" label="所属医生" required>
            <el-select v-model="uploadForm.doctorId" placeholder="请选择医生" class="full-width" filterable :disabled="!uploadForm.departmentId">
              <el-option v-for="doc in uploadDoctorOptions" :key="doc.id" :label="`${doc.doctorName} - ${doc.departmentName || '未分配'}`" :value="doc.id" />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item label="选择文件" required>
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            accept=".pdf,.doc,.docx,.txt,.md,.zip"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :file-list="uploadForm.fileList"
            class="upload-area"
          >
            <el-icon :size="48" color="#c0c4cc"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处或 <em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 PDF、Word、TXT、MD、ZIP 格式，单个文件不超过 200MB</div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item label="备注说明">
          <el-input v-model="uploadForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="可选备注" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showUploadDialog = false">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="handleUpload">确认上传</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElLoading, ElMessage, ElMessageBox } from 'element-plus'
import { debounce } from 'lodash'
import {
  Clock,
  Document,
  FirstAidKit,
  OfficeBuilding,
  Refresh,
  Search,
  Upload,
  UploadFilled,
  User,
  WarningFilled
} from '@element-plus/icons-vue'

import { getHospitalAll, getHospitalPage } from '@/api/HospitalManagementApi'
import { getDepartmentAll, getDepartmentPage } from '@/api/DepartmentManagementApi'
import { getDoctorPageOther } from '@/api/DoctorManagementApi'
import { getTitleAll } from '@/api/TitleManagementApi'
import { getDocumentPage, uploadDocument } from '@/api/KnowledgeDocumentApi'
import { convertDocument, deleteVectorAndDocument } from '@/api/RagDocumentApi'

const AGENT_TYPE = {
  HOSPITAL: 1,
  DEPARTMENT: 2,
  DOCTOR: 3
}

const agentTabs = [
  { label: '医院库', value: AGENT_TYPE.HOSPITAL },
  { label: '科室库', value: AGENT_TYPE.DEPARTMENT },
  { label: '医生库', value: AGENT_TYPE.DOCTOR }
]

const activeAgentType = ref(AGENT_TYPE.HOSPITAL)
const queryParams = ref({
  keyword: '',
  hospitalId: null,
  departmentId: null,
  titleId: null,
  knowledgeStatus: null
})

const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const hospitalList = ref([])
const departmentList = ref([])
const doctorList = ref([])
const hospitalTotal = ref(0)
const departmentTotal = ref(0)
const doctorTotal = ref(0)
const totalFileCount = ref(0)

const hospitalOptions = ref([])
const departmentOptions = ref([])
const titleOptions = ref([])

const showFilesDialog = ref(false)
const currentScope = ref(null)
const currentScopeType = ref(AGENT_TYPE.HOSPITAL)
const fileLoading = ref(false)
const fileQuery = ref({ fileName: '', status: '' })
const filePage = ref(1)
const filePageSize = ref(8)
const fileTotal = ref(0)
const fileList = ref([])

const showUploadDialog = ref(false)
const uploading = ref(false)
const isScopeLocked = ref(false)
const lockedScopeLabel = ref('')
const uploadDepartmentOptions = ref([])
const uploadDoctorOptions = ref([])
const uploadForm = ref({
  agentType: AGENT_TYPE.HOSPITAL,
  hospitalId: null,
  departmentId: null,
  doctorId: null,
  remark: '',
  fileList: []
})

const currentTotal = computed(() => {
  if (activeAgentType.value === AGENT_TYPE.HOSPITAL) return hospitalTotal.value
  if (activeAgentType.value === AGENT_TYPE.DEPARTMENT) return departmentTotal.value
  return doctorTotal.value
})

const keywordPlaceholder = computed(() => {
  if (activeAgentType.value === AGENT_TYPE.HOSPITAL) return '医院名称'
  if (activeAgentType.value === AGENT_TYPE.DEPARTMENT) return '科室名称'
  return '医生姓名'
})

const currentScopeLabel = computed(() => {
  if (!currentScope.value) return ''
  return scopeLabel(currentScope.value, currentScopeType.value)
})

const knowledgeStatusText = (status) => {
  const map = { 0: '未上传', 1: '可用', 2: '存在异常' }
  return map[status] || '未知'
}

const knowledgeStatusType = (status) => {
  const map = { 0: 'info', 1: 'success', 2: 'danger' }
  return map[status] || 'info'
}

const statusText = (status) => {
  const map = { 0: '待处理', 1: '成功', 2: '失败', 3: '处理中' }
  return map[status] || '未知'
}

const statusTagType = (status) => {
  const map = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'primary' }
  return map[status] || 'info'
}

const agentTypeText = (agentType) => {
  const map = {
    [AGENT_TYPE.HOSPITAL]: '医院',
    [AGENT_TYPE.DEPARTMENT]: '科室',
    [AGENT_TYPE.DOCTOR]: '医生'
  }
  return map[agentType] || '未知'
}

const agentScopeText = (row) => {
  if (row.agentType === AGENT_TYPE.HOSPITAL) return row.hospitalName || row.hospitalId || '-'
  if (row.agentType === AGENT_TYPE.DEPARTMENT) return [row.hospitalName, row.departmentName].filter(Boolean).join(' / ') || '-'
  return [row.hospitalName, row.departmentName, row.doctorName].filter(Boolean).join(' / ') || '-'
}

const scopeLabel = (row, agentType) => {
  if (agentType === AGENT_TYPE.HOSPITAL) return row.hospitalName || row.id
  if (agentType === AGENT_TYPE.DEPARTMENT) return [row.hospitalName, row.departmentName].filter(Boolean).join(' / ')
  return [row.hospitalName, row.departmentName, row.doctorName].filter(Boolean).join(' / ')
}

const getFileType = (contentType) => {
  const type = String(contentType || '').toLowerCase()
  if (type.includes('pdf')) return 'PDF'
  if (type.includes('word') || type.includes('document')) return 'DOC'
  if (type.includes('zip')) return 'ZIP'
  if (type.includes('text') || type.includes('plain')) return 'TXT'
  if (type.includes('markdown')) return 'MD'
  return '文件'
}

const getFileIconColor = (contentType) => {
  const type = String(contentType || '').toLowerCase()
  if (type.includes('pdf')) return '#ef4444'
  if (type.includes('word') || type.includes('document')) return '#3b82f6'
  if (type.includes('zip')) return '#f59e0b'
  if (type.includes('text') || type.includes('markdown')) return '#10b981'
  return '#6b7280'
}

const formatDateTime = (isoString) => {
  if (!isoString) return '-'
  return String(isoString).replace('T', ' ')
}

const formatFileSize = (bytes) => {
  if (!bytes) return '-'
  const size = Number(bytes)
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(2)} MB`
}

const buildListParams = () => {
  const params = {
    pageNum: currentPage.value,
    pageSize: pageSize.value
  }
  if (activeAgentType.value === AGENT_TYPE.HOSPITAL) {
    params.hospitalName = queryParams.value.keyword || undefined
  } else if (activeAgentType.value === AGENT_TYPE.DEPARTMENT) {
    params.departmentName = queryParams.value.keyword || undefined
    params.hospitalId = queryParams.value.hospitalId || undefined
  } else {
    params.doctorName = queryParams.value.keyword || undefined
    params.hospital = queryParams.value.hospitalId || undefined
    params.department = queryParams.value.departmentId || undefined
    params.title = queryParams.value.titleId || undefined
    params.knowledgeStatus = queryParams.value.knowledgeStatus ?? undefined
  }
  return params
}

const loadCurrentList = async () => {
  loading.value = true
  try {
    if (activeAgentType.value === AGENT_TYPE.HOSPITAL) {
      const res = await getHospitalPage(buildListParams())
      hospitalList.value = res.success ? (res.data || []) : []
      hospitalTotal.value = Number(res.total) || 0
      if (!res.success) ElMessage.error(res.message || '获取医院库失败')
    } else if (activeAgentType.value === AGENT_TYPE.DEPARTMENT) {
      const res = await getDepartmentPage(buildListParams())
      const rows = res.success ? (res.data || []) : []
      departmentList.value = filterByKnowledgeStatus(rows)
      departmentTotal.value = Number(res.total) || departmentList.value.length
      if (!res.success) ElMessage.error(res.message || '获取科室库失败')
    } else {
      const res = await getDoctorPageOther(buildListParams())
      doctorList.value = res.success ? (res.data || []) : []
      doctorTotal.value = Number(res.total) || 0
      if (!res.success) ElMessage.error(res.message || '获取医生库失败')
    }
  } catch (error) {
    console.error('加载知识库列表失败:', error)
    ElMessage.error('加载失败，请检查网络')
  } finally {
    loading.value = false
  }
}

const loadSummaryCounts = async () => {
  try {
    const [hospitals, departments, doctors, documents] = await Promise.all([
      getHospitalPage({ pageNum: 1, pageSize: 1 }),
      getDepartmentPage({ pageNum: 1, pageSize: 1 }),
      getDoctorPageOther({ pageNum: 1, pageSize: 1 }),
      getDocumentPage({ pageNum: 1, pageSize: 1 })
    ])
    if (hospitals.success) hospitalTotal.value = Number(hospitals.total) || hospitalTotal.value
    if (departments.success) departmentTotal.value = Number(departments.total) || departmentTotal.value
    if (doctors.success) doctorTotal.value = Number(doctors.total) || doctorTotal.value
    if (documents.success) totalFileCount.value = Number(documents.total) || 0
  } catch (error) {
    console.warn('加载知识库统计失败:', error)
  }
}

const loadDocumentTotal = async () => {
  try {
    const res = await getDocumentPage({ pageNum: 1, pageSize: 1 })
    if (res.success) totalFileCount.value = Number(res.total) || 0
  } catch (error) {
    console.warn('load document total failed:', error)
  }
}

const filterByKnowledgeStatus = (rows) => {
  const status = queryParams.value.knowledgeStatus
  if (status === null || status === '') return rows
  return rows.filter(row => row.knowledgeStatus === status)
}

const handleAgentTypeChange = () => {
  currentPage.value = 1
  queryParams.value.keyword = ''
  queryParams.value.departmentId = null
  queryParams.value.titleId = null
  loadCurrentList()
}

const handleHospitalChange = async () => {
  queryParams.value.departmentId = null
  await loadDepartmentOptions(queryParams.value.hospitalId)
  if (activeAgentType.value !== AGENT_TYPE.HOSPITAL) {
    handleSearch()
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadCurrentList()
}

const resetQuery = () => {
  queryParams.value = {
    keyword: '',
    hospitalId: null,
    departmentId: null,
    titleId: null,
    knowledgeStatus: null
  }
  departmentOptions.value = []
  currentPage.value = 1
  loadCurrentList()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  loadCurrentList()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadCurrentList()
}

const loadHospitalOptions = async () => {
  try {
    const res = await getHospitalAll()
    if (res.success) hospitalOptions.value = res.data || []
  } catch (error) {
    console.warn('加载医院选项失败:', error)
  }
}

const loadDepartmentOptions = async (hospitalId) => {
  if (!hospitalId) {
    departmentOptions.value = []
    return
  }
  try {
    const res = await getDepartmentAll(hospitalId)
    departmentOptions.value = res.success ? (res.data || []) : []
  } catch (error) {
    console.warn('加载科室选项失败:', error)
    departmentOptions.value = []
  }
}

const loadTitleOptions = async () => {
  try {
    const res = await getTitleAll()
    if (res.success) titleOptions.value = res.data || []
  } catch (error) {
    console.warn('加载职称选项失败:', error)
  }
}

const viewScopeFiles = async (row, agentType) => {
  currentScope.value = row
  currentScopeType.value = agentType
  fileQuery.value = { fileName: '', status: '' }
  filePage.value = 1
  showFilesDialog.value = true
  await loadFileList()
}

const buildFileParams = () => {
  const params = {
    pageNum: filePage.value,
    pageSize: filePageSize.value,
    agentType: currentScopeType.value,
    fileName: fileQuery.value.fileName || undefined,
    status: fileQuery.value.status !== null && fileQuery.value.status !== '' ? fileQuery.value.status : undefined
  }
  if (currentScopeType.value === AGENT_TYPE.HOSPITAL) {
    params.hospitalId = currentScope.value?.id
  } else if (currentScopeType.value === AGENT_TYPE.DEPARTMENT) {
    params.departmentId = currentScope.value?.id
  } else {
    params.doctorId = currentScope.value?.id
  }
  return params
}

const loadFileList = async () => {
  if (!currentScope.value) return
  fileLoading.value = true
  try {
    const res = await getDocumentPage(buildFileParams())
    if (res.success) {
      fileList.value = res.data || []
      fileTotal.value = Number(res.total) || 0
    } else {
      ElMessage.error(res.message || '获取文件列表失败')
    }
  } catch (error) {
    console.error('加载文件列表失败:', error)
    ElMessage.error('加载文件列表失败')
  } finally {
    fileLoading.value = false
  }
}

const handleFileSearch = () => {
  filePage.value = 1
  loadFileList()
}

const refreshFileList = debounce(() => {
  loadFileList()
  ElMessage.success('刷新成功')
}, 800)

const handleFileCurrentChange = (page) => {
  filePage.value = page
  loadFileList()
}

const handleFileSizeChange = (size) => {
  filePageSize.value = size
  filePage.value = 1
  loadFileList()
}

const handleReprocess = async (row) => {
  const loadingInstance = ElLoading.service({
    fullscreen: true,
    text: '正在重新处理文件，请稍候...',
    background: 'rgba(0, 0, 0, 0.7)'
  })
  try {
    const res = await convertDocument(row.id)
    if (res.success) {
      ElMessage.success(`文件「${row.fileName}」已重新提交处理`)
      await loadFileList()
      await loadCurrentList()
      await loadDocumentTotal()
    } else {
      ElMessage.error(res.message || '重新处理失败')
    }
  } catch (error) {
    console.error('重新处理失败:', error)
    ElMessage.error('重新处理失败')
  } finally {
    loadingInstance.close()
  }
}

const handleDeleteFile = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除文件「${row.fileName}」吗？`, '删除确认', {
      type: 'error',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    const res = await deleteVectorAndDocument(row.id)
    if (res.success) {
      ElMessage.success('文件已删除')
      await loadFileList()
      await loadCurrentList()
      await loadDocumentTotal()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

const resetUploadForm = (agentType = activeAgentType.value) => {
  uploadForm.value = {
    agentType,
    hospitalId: null,
    departmentId: null,
    doctorId: null,
    remark: '',
    fileList: []
  }
  uploadDepartmentOptions.value = []
  uploadDoctorOptions.value = []
}

const openUploadDialog = async (row = null, agentType = activeAgentType.value) => {
  isScopeLocked.value = Boolean(row)
  lockedScopeLabel.value = row ? scopeLabel(row, agentType) : ''
  resetUploadForm(agentType)
  if (row) {
    uploadForm.value.hospitalId = agentType === AGENT_TYPE.HOSPITAL ? row.id : row.hospitalId
    uploadForm.value.departmentId = agentType === AGENT_TYPE.DEPARTMENT ? row.id : row.departmentId
    uploadForm.value.doctorId = agentType === AGENT_TYPE.DOCTOR ? row.id : null
  }
  if (!row && agentType !== AGENT_TYPE.HOSPITAL && queryParams.value.hospitalId) {
    uploadForm.value.hospitalId = queryParams.value.hospitalId
    await loadUploadDepartmentOptions(uploadForm.value.hospitalId)
  }
  if (!row && agentType === AGENT_TYPE.DOCTOR && queryParams.value.departmentId) {
    uploadForm.value.departmentId = queryParams.value.departmentId
    await loadUploadDoctorOptions()
  }
  showUploadDialog.value = true
}

const handleUploadAgentTypeChange = async () => {
  uploadForm.value.hospitalId = null
  uploadForm.value.departmentId = null
  uploadForm.value.doctorId = null
  uploadDepartmentOptions.value = []
  uploadDoctorOptions.value = []
}

const handleUploadHospitalChange = async () => {
  uploadForm.value.departmentId = null
  uploadForm.value.doctorId = null
  uploadDoctorOptions.value = []
  if (uploadForm.value.agentType >= AGENT_TYPE.DEPARTMENT) {
    await loadUploadDepartmentOptions(uploadForm.value.hospitalId)
  } else {
    uploadDepartmentOptions.value = []
  }
}

const handleUploadDepartmentChange = async () => {
  uploadForm.value.doctorId = null
  if (uploadForm.value.agentType === AGENT_TYPE.DOCTOR) {
    await loadUploadDoctorOptions()
  } else {
    uploadDoctorOptions.value = []
  }
}

const loadUploadDepartmentOptions = async (hospitalId) => {
  if (!hospitalId) {
    uploadDepartmentOptions.value = []
    return
  }
  try {
    const res = await getDepartmentAll(hospitalId)
    uploadDepartmentOptions.value = res.success ? (res.data || []) : []
  } catch (error) {
    console.warn('加载上传科室选项失败:', error)
    uploadDepartmentOptions.value = []
  }
}

const loadUploadDoctorOptions = async () => {
  if (!uploadForm.value.hospitalId || !uploadForm.value.departmentId) {
    uploadDoctorOptions.value = []
    return
  }
  try {
    const res = await getDoctorPageOther({
      pageNum: 1,
      pageSize: 1000,
      hospital: uploadForm.value.hospitalId,
      department: uploadForm.value.departmentId
    })
    uploadDoctorOptions.value = res.success ? (res.data || []) : []
  } catch (error) {
    console.warn('加载上传医生选项失败:', error)
    uploadDoctorOptions.value = []
  }
}

const handleFileChange = (file) => {
  uploadForm.value.fileList = [file]
}

const handleFileRemove = () => {
  uploadForm.value.fileList = []
}

const handleUpload = async () => {
  const { agentType, hospitalId, departmentId, doctorId } = uploadForm.value
  if (!hospitalId) {
    ElMessage.warning('请选择医院')
    return
  }
  if (agentType >= AGENT_TYPE.DEPARTMENT && !departmentId) {
    ElMessage.warning('请选择科室')
    return
  }
  if (agentType === AGENT_TYPE.DOCTOR && !doctorId) {
    ElMessage.warning('请选择医生')
    return
  }
  if (!uploadForm.value.fileList.length) {
    ElMessage.warning('请选择文件')
    return
  }
  const file = uploadForm.value.fileList[0].raw
  if (!file) {
    ElMessage.warning('文件读取失败，请重新选择')
    return
  }
  const maxSize = 200 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.warning('单个文件不能超过 200MB')
    return
  }
  const formData = new FormData()
  formData.append('file', file)
  formData.append('agentType', agentType)
  formData.append('hospitalId', hospitalId)
  if (agentType >= AGENT_TYPE.DEPARTMENT) formData.append('departmentId', departmentId)
  if (agentType === AGENT_TYPE.DOCTOR) formData.append('doctorId', doctorId)
  formData.append('remark', uploadForm.value.remark || '')

  uploading.value = true
  try {
    const res = await uploadDocument(formData)
    if (res.success) {
      ElMessage.success('文件上传成功，正在后台处理...')
      showUploadDialog.value = false
      if (showFilesDialog.value) await loadFileList()
      await loadCurrentList()
      await loadDocumentTotal()
    } else {
      ElMessage.error(res.message || '上传失败')
    }
  } catch (error) {
    console.error('上传失败:', error)
    ElMessage.error('上传失败，请检查网络')
  } finally {
    uploading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadHospitalOptions(), loadTitleOptions(), loadSummaryCounts()])
  await loadCurrentList()
})
</script>

<style scoped>
.knowledge-base-page {
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
  box-sizing: border-box;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.stat-card {
  min-height: 88px;
  background: #fff;
  border-radius: 8px;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-sizing: border-box;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f2f5;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon-hospital {
  background: #e8f4fd;
  color: #2563eb;
}

.stat-icon-department {
  background: #ecfdf3;
  color: #16a34a;
}

.stat-icon-doctor {
  background: #eef2ff;
  color: #4f46e5;
}

.stat-icon-file {
  background: #fff7e6;
  color: #d97706;
}

.stat-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 24px;
  line-height: 1.2;
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
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 18px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  box-sizing: border-box;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f2f5;
}

.action-left {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.action-right {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
}

.filter-input {
  width: 170px;
}

.filter-select {
  width: 155px;
}

.filter-select-sm {
  width: 130px;
}

.query-buttons {
  display: flex;
  gap: 10px;
  align-items: center;
}

.table-card {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px 12px;
  box-sizing: border-box;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f2f5;
}

.knowledge-table {
  width: 100%;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 4px 0;
}

.file-dialog-content {
  min-height: 420px;
}

.file-filters {
  background: #f8fafc;
  border: 1px solid #eef0f5;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.file-filter-left {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.file-filter-right {
  flex-shrink: 0;
}

.file-search-input {
  width: 220px;
}

.file-status-select {
  width: 130px;
}

.file-table {
  width: 100%;
}

.file-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.error-text {
  color: #ef4444;
}

.file-pagination {
  margin-top: 16px;
}

.full-width,
.upload-area {
  width: 100%;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.el-upload__tip {
  font-size: 12px;
  color: #999;
  margin-top: 6px;
}

:deep(.el-table) {
  border-radius: 8px;
}

:deep(.el-table th.el-table__cell) {
  background-color: #f8fafc;
  color: #606266;
  font-weight: 600;
}

:deep(.el-table .cell) {
  line-height: 22px;
}

:deep(.el-dialog) {
  border-radius: 8px;
}

:deep(.el-dialog__header) {
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f2f5;
}

:deep(.el-dialog__body) {
  padding-top: 18px;
}

:deep(.el-upload),
:deep(.el-upload-dragger) {
  width: 100%;
}

:deep(.el-upload-dragger) {
  border-radius: 8px;
}

@media (max-width: 1200px) {
  .stat-cards {
    grid-template-columns: repeat(2, minmax(220px, 1fr));
  }

  .action-bar {
    flex-direction: column;
  }

  .action-right {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .knowledge-base-page {
    max-width: 100%;
  }

  .stat-cards {
    grid-template-columns: 1fr;
  }

  .filter-input,
  .filter-select,
  .filter-select-sm,
  .action-left,
  .query-buttons,
  .action-right,
  .action-right .el-button,
  .file-search-input,
  .file-status-select,
  .file-filter-left,
  .file-filter-right,
  .file-filter-right .el-button {
    width: 100%;
  }

  .query-buttons .el-button {
    flex: 1;
  }

  .pagination-wrap {
    justify-content: center;
    overflow-x: auto;
  }
}
</style>
