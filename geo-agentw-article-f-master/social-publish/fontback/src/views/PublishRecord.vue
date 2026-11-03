<template>
  <div class="publish-record-page">
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background: #e8f4fd; color: #2563eb;">
          <el-icon :size="24"><Document /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ records.length }}</span>
          <span class="stat-label">发布总次数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #e6f9ee; color: #16a34a;">
          <el-icon :size="24"><CircleCheck /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ records.filter(r => r.publishStatus === 1).length }}</span>
          <span class="stat-label">成功</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #fef2f2; color: #dc2626;">
          <el-icon :size="24"><RemoveFilled /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ records.filter(r => r.publishStatus === 2).length }}</span>
          <span class="stat-label">失败</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: #fff7e6; color: #d97706;">
          <el-icon :size="24"><Platform /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ new Set(records.map(r => r.platform)).size }}</span>
          <span class="stat-label">覆盖平台数</span>
        </div>
      </div>
    </div>

    <div class="action-bar">
      <div class="action-left">
        <el-input v-model="searchKeyword" placeholder="搜索文章标题或账号" :prefix-icon="Search" clearable style="width: 260px;" />
        <el-select v-model="filterPlatform" placeholder="平台" clearable style="width: 120px;">
          <el-option label="全部平台" value="" />
          <el-option label="小红书" value="小红书" />
          <el-option label="知乎" value="知乎" />
          <el-option label="抖音" value="抖音" />
          <el-option label="微信公众号" value="微信公众号" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 100px;">
          <el-option label="全部" value="" />
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="2" />
        </el-select>
      </div>
      <div class="action-right">
        <el-button :icon="Refresh" @click="loadRecords">刷新</el-button>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="paginatedList" stripe v-loading="loading" empty-text="暂无发布记录">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="articleTitle" label="文章标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="platform" label="平台" width="110" align="center" />
        <el-table-column prop="platformAccount" label="发布账号" width="150" show-overflow-tooltip />
        <el-table-column prop="publishTime" label="发布时间" width="170" align="center" />
        <el-table-column prop="publishStatus" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.publishStatus === 1 ? 'success' : 'danger'" size="small" round effect="light">
              {{ row.publishStatus === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="失败原因" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span style="color: #dc2626;">{{ row.errorMessage || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="外部链接" width="100" align="center">
          <template #default="{ row }">
            <el-link v-if="row.externalUrl" :href="row.externalUrl" target="_blank" type="primary">查看</el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="170" align="center" />
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="deleteRecord(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="filteredList.length"
          layout="total, prev, pager, next"
          background
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Document, Platform, Refresh, RemoveFilled, Search } from '@element-plus/icons-vue'

const records = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const filterPlatform = ref('')
const filterStatus = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

const filteredList = computed(() => {
  let list = records.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(r =>
      String(r.articleTitle || '').toLowerCase().includes(kw) ||
      String(r.platformAccount || '').toLowerCase().includes(kw)
    )
  }
  if (filterPlatform.value) {
    list = list.filter(r => r.platform === filterPlatform.value)
  }
  if (filterStatus.value !== '') {
    list = list.filter(r => r.publishStatus === filterStatus.value)
  }
  return list
})

const paginatedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

const loadRecords = () => {
  loading.value = true
  window.setTimeout(() => {
    loading.value = false
    ElMessage.success('刷新成功')
  }, 300)
}

const deleteRecord = (row) => {
  ElMessageBox.confirm('确定删除这条发布记录吗？', '删除确认', { type: 'error' })
    .then(() => {
      const idx = records.value.findIndex(r => r.id === row.id)
      if (idx > -1) records.value.splice(idx, 1)
      ElMessage.success('记录已删除')
    })
    .catch(() => {})
}
</script>

<style scoped>
.publish-record-page { max-width: 1400px; }
.stat-cards { display: grid; grid-template-columns: repeat(4,1fr); gap: 16px; margin-bottom: 20px; }
.stat-card { background: #fff; border-radius: 8px; padding: 18px 20px; display: flex; align-items: center; gap: 14px; box-shadow: 0 1px 2px rgba(15,23,42,0.04); }
.stat-icon { width: 48px; height: 48px; border-radius: 8px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.stat-info { display: flex; flex-direction: column; }
.stat-value { font-size: 24px; font-weight: 700; color: #111827; }
.stat-label { font-size: 13px; color: #6b7280; margin-top: 2px; }
.action-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.action-left { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.table-card { background: #fff; border-radius: 8px; padding: 4px; box-shadow: 0 1px 2px rgba(15,23,42,0.04); }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; padding: 8px; }
</style>
