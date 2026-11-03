<template>
  <div class="not-found-container">
    <el-result icon="warning" title="404" sub-title="抱歉，您访问的页面不存在">
      <template #extra>
        <el-button type="primary" @click="goHome">
          <el-icon><Back /></el-icon>
          返回首页
        </el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Back } from '@element-plus/icons-vue'

const router = useRouter()
const goHome = () => router.push('/')
</script>

<style scoped>
.not-found-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  width: 100%;
  background-color: #f5f7fa;
  padding: 20px;
  box-sizing: border-box;
}

/* 覆盖 el-result 默认样式使其更美观 */
:deep(.el-result) {
  background: #fff;
  border-radius: 24px;
  padding: 48px 32px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.08);
  max-width: 480px;
  width: 100%;
}

:deep(.el-result__title) {
  font-size: 28px;
  font-weight: 600;
  color: #1f2d3d;
}

:deep(.el-result__subtitle) {
  font-size: 16px;
  color: #6b7280;
  margin-top: 8px;
}

:deep(.el-result__extra) {
  margin-top: 32px;
}

:deep(.el-button) {
  padding: 10px 24px;
  font-size: 15px;
}
</style>