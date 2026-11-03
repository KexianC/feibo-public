<template>
  <div class="cover-management-page">
    <div class="cover-action-bar">
      <div class="action-left">
        <span class="section-label"><el-icon><PictureFilled /></el-icon> 封面图库</span>
        <span class="cover-count">共 {{ covers.length }} 张封面</span>
      </div>
      <div class="action-right">
        <el-input v-model="coverSearch" placeholder="搜索封面名称" :prefix-icon="Search" clearable size="small" style="width: 240px;" />
        <el-select v-model="coverTagFilter" placeholder="筛选标签" clearable size="small" style="width: 140px;">
          <el-option label="全部" value="" />
          <el-option label="健康科普" value="健康科普" />
          <el-option label="医生形象" value="医生形象" />
          <el-option label="疾病专题" value="疾病专题" />
          <el-option label="节日热点" value="节日热点" />
        </el-select>
        <el-button type="primary" :icon="Upload" @click="triggerUpload">上传封面</el-button>
        <input ref="fileInputRef" type="file" accept="image/*" multiple hidden @change="handleFileInput" />
      </div>
    </div>

    <div class="cover-grid" v-loading="coverLoading">
      <div class="cover-card" v-for="cover in filteredCovers" :key="cover.id">
        <div class="cover-image-wrapper">
          <img :src="cover.url" :alt="cover.name" class="cover-image" />
          <div class="cover-overlay">
            <el-button circle :icon="ZoomIn" size="small" @click="previewCover(cover)" />
            <el-button circle :icon="Download" size="small" @click="downloadCover(cover)" />
            <el-popconfirm title="确定删除该封面吗？" @confirm="deleteCover(cover)">
              <template #reference>
                <el-button circle :icon="Delete" size="small" style="color: #dc2626;" />
              </template>
            </el-popconfirm>
          </div>
          <el-tag size="small" class="cover-tag" :type="cover.tagType">{{ cover.tag }}</el-tag>
        </div>
        <div class="cover-info">
          <span class="cover-name" :title="cover.name">{{ cover.name }}</span>
          <span class="cover-size">{{ cover.size }}</span>
        </div>
      </div>

      <div class="cover-card upload-placeholder" @click="triggerUpload">
        <div class="upload-inner">
          <el-icon :size="40" color="#9ca3af"><Plus /></el-icon>
          <span>上传新封面</span>
          <span class="upload-hint">支持 JPG、PNG、WebP</span>
        </div>
      </div>
    </div>

    <div class="cover-pagination">
      <el-pagination v-model:current-page="coverPage" :page-size="12" :total="filteredCovers.length" layout="total, prev, pager, next" background />
    </div>

    <el-dialog v-model="showPreview" title="封面预览" width="700px" center :close-on-click-modal="true">
      <div class="preview-container" v-if="previewCoverData">
        <img :src="previewCoverData.url" :alt="previewCoverData.name" class="preview-image" />
        <div class="preview-info">
          <span><strong>名称：</strong>{{ previewCoverData.name }}</span>
          <span><strong>标签：</strong>{{ previewCoverData.tag }}</span>
          <span><strong>尺寸：</strong>{{ previewCoverData.size }}</span>
          <span><strong>上传时间：</strong>{{ previewCoverData.uploadTime }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Download, PictureFilled, Plus, Search, Upload, ZoomIn } from '@element-plus/icons-vue'

const coverSearch = ref('')
const coverTagFilter = ref('')
const coverLoading = ref(false)
const coverPage = ref(1)
const showPreview = ref(false)
const previewCoverData = ref(null)
const fileInputRef = ref(null)
const covers = ref([])

const filteredCovers = computed(() => {
  let list = covers.value
  if (coverSearch.value) {
    const kw = coverSearch.value.toLowerCase()
    list = list.filter(c => String(c.name || '').toLowerCase().includes(kw))
  }
  if (coverTagFilter.value) {
    list = list.filter(c => c.tag === coverTagFilter.value)
  }
  return list
})

const triggerUpload = () => {
  fileInputRef.value?.click()
}

const handleFileInput = (e) => {
  const files = e.target.files
  if (files && files.length > 0) {
    for (const file of files) {
      const reader = new FileReader()
      reader.onload = (ev) => {
        covers.value.unshift({
          id: `${Date.now()}-${file.name}`,
          name: file.name,
          url: ev.target.result,
          tag: '健康科普',
          tagType: 'primary',
          size: (file.size / 1024 / 1024).toFixed(1) + ' MB',
          uploadTime: new Date().toISOString().split('T')[0]
        })
      }
      reader.readAsDataURL(file)
    }
    ElMessage.success(`成功上传 ${files.length} 张封面`)
  }
  if (fileInputRef.value) fileInputRef.value.value = ''
}

const previewCover = (cover) => {
  previewCoverData.value = cover
  showPreview.value = true
}

const downloadCover = (cover) => {
  const link = document.createElement('a')
  link.href = cover.url
  link.download = cover.name
  link.click()
  ElMessage.success('开始下载')
}

const deleteCover = (cover) => {
  const idx = covers.value.findIndex(c => c.id === cover.id)
  if (idx > -1) {
    covers.value.splice(idx, 1)
    ElMessage.success('封面已删除')
  }
}
</script>

<style scoped>
.cover-management-page { max-width: 1500px; }
.cover-action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
  background: #fff;
  padding: 16px 20px;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.action-left { display: flex; align-items: center; gap: 12px; }
.section-label { font-size: 16px; font-weight: 600; color: #111827; display: flex; align-items: center; gap: 6px; }
.cover-count { font-size: 13px; color: #6b7280; }
.action-right { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.cover-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
.cover-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition: all 0.2s;
  cursor: pointer;
}
.cover-card:hover { box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12); transform: translateY(-2px); }
.cover-image-wrapper { position: relative; width: 100%; aspect-ratio: 4 / 3; overflow: hidden; background: #f3f4f6; }
.cover-image { width: 100%; height: 100%; object-fit: cover; transition: transform 0.3s; }
.cover-card:hover .cover-image { transform: scale(1.04); }
.cover-overlay {
  position: absolute;
  inset: 0;
  background: rgba(17, 24, 39, 0.46);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  opacity: 0;
  transition: opacity 0.2s;
}
.cover-card:hover .cover-overlay { opacity: 1; }
.cover-tag { position: absolute; top: 8px; left: 8px; }
.cover-info { padding: 10px 14px; display: flex; justify-content: space-between; align-items: center; }
.cover-name { font-size: 13px; font-weight: 500; color: #111827; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 140px; }
.cover-size { font-size: 11px; color: #6b7280; flex-shrink: 0; }
.upload-placeholder {
  border: 2px dashed #d1d5db;
  background: #f9fafb;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  cursor: pointer;
  transition: all 0.2s;
}
.upload-placeholder:hover { border-color: #2563eb; background: #f8fafc; }
.upload-inner { display: flex; flex-direction: column; align-items: center; gap: 8px; color: #6b7280; font-size: 14px; }
.upload-hint { font-size: 11px; color: #9ca3af; }
.cover-pagination { display: flex; justify-content: center; margin-top: 24px; }
.preview-container { text-align: center; }
.preview-image { max-width: 100%; max-height: 450px; border-radius: 8px; object-fit: contain; }
.preview-info { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 16px; text-align: left; font-size: 13px; color: #4b5563; justify-content: center; }
</style>
