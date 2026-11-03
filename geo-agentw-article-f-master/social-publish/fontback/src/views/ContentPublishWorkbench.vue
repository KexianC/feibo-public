<template>
  <div class="content-publish-workbench">
    <div class="workbench-header">
      <div>
        <h2>内容发布工作台</h2>
      </div>
      <el-tabs v-model="activeTab" class="workbench-tabs">
        <el-tab-pane
          v-for="tab in tabs"
          :key="tab.name"
          :label="tab.label"
          :name="tab.name"
        />
      </el-tabs>
    </div>

    <div class="workbench-body">
      <keep-alive>
        <component :is="currentComponent" />
      </keep-alive>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import AccountManagement from './AccountManagement.vue'
import ArticleGenerator from './ArticleGenerator.vue'
import ArticleManagement from './ArticleManagement.vue'

const tabs = [
  { name: 'article-generator', label: '文章生成', component: ArticleGenerator },
  { name: 'article-management', label: '文章管理', component: ArticleManagement },
  { name: 'account-management', label: '图文媒体账号', component: AccountManagement }
]

const activeTab = ref('article-generator')

const currentComponent = computed(() => {
  return tabs.find(tab => tab.name === activeTab.value)?.component || ArticleGenerator
})
</script>

<style scoped>
.content-publish-workbench {
  width: 100%;
  max-width: 1500px;
  margin: 0 auto;
}

.workbench-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
  padding: 0 2px;
}

.workbench-header h2 {
  margin: 0;
  color: #111827;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0;
  white-space: nowrap;
}

.workbench-tabs {
  flex: 1;
  min-width: 0;
}

.workbench-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.workbench-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: #dbe1ea;
}

.workbench-body {
  min-width: 0;
}

@media (max-width: 900px) {
  .workbench-header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
