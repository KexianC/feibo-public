<template>
  <div class="medical-management-page">
    <div class="management-switcher">
      <div>
        <h2>医务管理</h2>
        <p>统一维护医院、科室和医生信息</p>
      </div>
      <el-tabs v-model="activeTab" class="management-tabs">
        <el-tab-pane label="医院管理" name="hospital" />
        <el-tab-pane label="科室管理" name="department" />
        <el-tab-pane label="医生管理" name="doctor" />
      </el-tabs>
    </div>

    <component :is="activeComponent" :key="activeTab" />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import DepartmentManagement from '@/views/DepartmentManagement.vue'
import DoctorManagement from '@/views/DoctorManagement.vue'
import HospitalManagement from '@/views/HospitalManagement.vue'

const activeTab = ref('hospital')

const managementComponents = {
  hospital: HospitalManagement,
  department: DepartmentManagement,
  doctor: DoctorManagement,
}

const activeComponent = computed(() => managementComponents[activeTab.value])
</script>

<style scoped>
.medical-management-page {
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
}

.management-switcher {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
  padding: 18px 24px 0;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.06);
}

.management-switcher h2 {
  margin: 0 0 4px;
  color: #1f2937;
  font-size: 18px;
}

.management-switcher p {
  margin: 0 0 18px;
  color: #8b8fa3;
  font-size: 13px;
}

.management-tabs {
  min-width: 300px;
}

.management-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.management-tabs :deep(.el-tabs__content) {
  display: none;
}

@media (max-width: 860px) {
  .management-switcher {
    align-items: stretch;
    flex-direction: column;
    gap: 0;
  }

  .management-switcher p {
    margin-bottom: 10px;
  }

  .management-tabs {
    min-width: 0;
  }
}
</style>
