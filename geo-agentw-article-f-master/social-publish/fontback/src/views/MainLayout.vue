<template>
  <div class="main-layout">
    <!-- 侧边栏 -->
    <aside v-if="!isEmbedded" class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-header">
        <div class="logo-container">
          <div class="logo-icon">
            <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="40" height="40" rx="10" fill="url(#logo-grad)"/>
              <path d="M12 20L18 26L28 14" stroke="white" stroke-width="3.5" stroke-linecap="round"
                    stroke-linejoin="round"/>
              <defs>
                <linearGradient id="logo-grad" x1="0" y1="0" x2="40" y2="40">
                  <stop offset="0%" stop-color="#4f6ef7"/>
                  <stop offset="100%" stop-color="#7b5cf5"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <span v-show="!isCollapsed" class="logo-text">Medtalent GEO平台</span>
        </div>
        <button class="collapse-btn" @click="isCollapsed = !isCollapsed">
          <el-icon :size="18">
            <Fold v-if="!isCollapsed"/>
            <Expand v-else/>
          </el-icon>
        </button>
      </div>

      <el-menu :default-active="activeMenu" class="sidebar-menu" :collapse="isCollapsed" background-color="#1a1d29"
               text-color="#8b8fa3" active-text-color="#ffffff" @select="handleMenuSelect">
        <el-menu-item index="/medicalManagement">
          <el-icon>
            <OfficeBuilding/>
          </el-icon>
          <template #title>医务管理</template>
        </el-menu-item>
        <el-menu-item index="/knowledgeBase">
          <el-icon>
            <Document/>
          </el-icon>
          <template #title>知识库管理</template>
        </el-menu-item>
        <el-menu-item index="/geoWorkbench">
          <el-icon>
            <DataAnalysis/>
          </el-icon>
          <template #title>GEO运营</template>
        </el-menu-item>
        <el-menu-item index="/contentPublish">
          <el-icon>
            <PictureFilled/>
          </el-icon>
          <template #title>内容发布</template>
        </el-menu-item>
        <template v-if="false">
        <el-menu-item index="/accountManagement">
          <el-icon>
            <UserFilled/>
          </el-icon>
          <template #title>账号管理</template>
        </el-menu-item>
        <el-menu-item index="/articleGenerator">
          <el-icon>
            <EditPen/>
          </el-icon>
          <template #title>AI文章生成</template>
        </el-menu-item>
        <el-menu-item index="/articleManagement">
          <el-icon>
            <Promotion/>
          </el-icon>
          <template #title>文章管理</template>
        </el-menu-item>
        <el-menu-item index="/publishRecord">
          <el-icon>
            <Promotion/>
          </el-icon>
          <template #title>发布记录</template>
        </el-menu-item>
        <el-menu-item index="/coverManagement">
          <el-icon>
            <PictureFilled/>
          </el-icon>
          <template #title>封面管理</template>
        </el-menu-item>
        <!-- 系统管理 -->
        </template>
        <el-sub-menu v-if="isAdmin && !USE_HOST_AUTH" index="system-management">
          <template #title>
            <el-icon>
              <Setting/>
            </el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/systemWorkbench">
            <el-icon>
              <Setting/>
            </el-icon>
            <template #title>用户与日志</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>

      <div class="sidebar-footer" v-show="!isCollapsed">
        <div class="user-info">
          <el-avatar :size="36" src="https://picsum.photos/100/100?random=avatar"/>
          <div class="user-text">
            <span class="user-name">{{ displayName }}</span>
            <span class="user-role">{{ roleText }}</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <header v-if="!isEmbedded" class="top-bar">
        <div class="top-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/geoWorkbench' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentPageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="top-right">
          <el-badge :value="3" :max="99" class="notification-badge">
            <el-button :icon="Bell" circle/>
          </el-badge>
          <el-dropdown trigger="click" @command="handleUserCommand">
            <el-avatar :size="34" src="https://picsum.photos/100/100?random=avatar" style="cursor: pointer;"/>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人设置</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <div class="content-area">
        <router-view/>
      </div>
    </main>
  </div>
</template>

<script setup>
import {ref, computed, onMounted} from 'vue'
import {
  Document,
  DataAnalysis,
  UserFilled,
  EditPen,
  Promotion,
  PictureFilled,
  Fold,
  Expand,
  Bell,
  Setting,
  OfficeBuilding,
} from '@element-plus/icons-vue'
import { useTokenStore } from '@/stores/TokenStore'
import { LoginStatus, Logout } from '@/api/LoginApi'
import { useRoute, useRouter } from 'vue-router'
import {
  USE_HOST_AUTH,
  clearHostAuthentication,
  getHostAccessToken,
  readHostIdentity,
  redirectToHostLogin,
} from '@/config/integration'

const isCollapsed = ref(false)
const isEmbedded = new URLSearchParams(window.location.search).get('embedded') === '1'
  || window.self !== window.top
const route = useRoute()
const router = useRouter()
const activeMenu = computed(() => route.path)
const tokenStore = useTokenStore()
const isAdmin = computed(() => tokenStore.isAdmin)
const displayName = computed(() => tokenStore.displayName || tokenStore.username || '未登录')
const roleText = computed(() => tokenStore.role === 'ADMIN' ? '系统管理员' : '运营用户')

const currentPageTitle = computed(() => String(route.meta.title || 'GEO运营'))

const handleMenuSelect = (index) => {
  if (typeof index === 'string' && index !== route.path) {
    router.push(index)
  }
}

const restoreLoginStatus = async () => {
  if (USE_HOST_AUTH) {
    if (!getHostAccessToken()) {
      redirectToHostLogin()
      return
    }
    tokenStore.setUser(readHostIdentity())
    return
  }

  try {
    const res = await LoginStatus()
    if (res.success && res.data?.authenticated) {
      tokenStore.setUser(res.data)
    } else {
      tokenStore.removeToken()
      router.push('/login')
    }
  } catch {
    tokenStore.removeToken()
    router.push('/login')
  }
}

const handleUserCommand = async (command) => {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    if (USE_HOST_AUTH) {
      clearHostAuthentication()
      tokenStore.removeToken()
      redirectToHostLogin()
      return
    }
    try {
      await Logout()
    } finally {
      tokenStore.removeToken()
      router.push('/login')
    }
  }
}

onMounted(() => {
  restoreLoginStatus()
})
</script>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* 侧边栏 */
.sidebar {
  width: 256px;
  min-width: 256px;
  background: #1a1d29;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease, min-width 0.3s ease;
  overflow: hidden;
  z-index: 100;
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.15);
}

.sidebar.collapsed {
  width: 64px;
  min-width: 64px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 16px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.logo-container {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.logo-icon {
  flex-shrink: 0;
}

.logo-icon svg {
  width: 36px;
  height: 36px;
}

.logo-text {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  letter-spacing: 0;
}

.collapse-btn {
  background: rgba(255, 255, 255, 0.06);
  border: none;
  color: #8b8fa3;
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.collapse-btn:hover {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  padding-top: 8px;
  overflow-y: auto;
  overflow-x: hidden;
}

.sidebar-menu .el-menu-item {
  margin: 3px 8px;
  border-radius: 8px;
  font-size: 14px;
  height: 44px;
  line-height: 44px;
  transition: all 0.2s;
}

.sidebar-menu .el-menu-item:hover {
  background: rgba(255, 255, 255, 0.08) !important;
}

.sidebar-menu .el-menu-item.is-active {
  background: linear-gradient(135deg, #4f6ef7, #7b5cf5) !important;
  box-shadow: 0 4px 12px rgba(79, 110, 247, 0.4);
}

.sidebar-footer {
  padding: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-text {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.user-name {
  color: #e0e0e0;
  font-size: 13px;
  font-weight: 500;
}

.user-role {
  color: #8b8fa3;
  font-size: 11px;
}

/* 主内容区 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f0f2f5;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  z-index: 50;
  flex-shrink: 0;
}

.top-left .el-breadcrumb {
  font-size: 14px;
}

.top-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.notification-badge {
  cursor: pointer;
}

.content-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

/* 滚动条美化 */
.content-area::-webkit-scrollbar,
.sidebar-menu::-webkit-scrollbar {
  width: 5px;
}

.content-area::-webkit-scrollbar-thumb,
.sidebar-menu::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 10px;
}
</style>
