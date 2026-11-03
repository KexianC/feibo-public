import { createRouter, createWebHashHistory } from 'vue-router'

import AccountManagement from "@/views/AccountManagement.vue";
import ArticleGenerator from "@/views/ArticleGenerator.vue";
import ArticleManagement from "@/views/ArticleManagement.vue";
import CoverManagement from "@/views/CoverManagement.vue";
import DepartmentManagement from "@/views/DepartmentManagement.vue";
import DoctorManagement from "@/views/DoctorManagement.vue";
import HospitalManagement from "@/views/HospitalManagement.vue";
import MedicalManagement from "@/views/MedicalManagement.vue";
import KnowledgeBase from "@/views/KnowledgeBase.vue";
import GeoWorkbench from "@/views/GeoWorkbench.vue";
import Login from "@/views/Login.vue";
import MainLayout from "@/views/MainLayout.vue";
import Profile from "@/views/Profile.vue";
import PublishRecord from "@/views/PublishRecord.vue";
import Register from "@/views/Register.vue";
import TitleManagement from "@/views/TitleManagement.vue";
import NotFound from "@/views/NotFound.vue";
import ContentPublishWorkbench from '@/views/ContentPublishWorkbench.vue'
import SystemWorkbench from '@/views/SystemWorkbench.vue'
import { USE_HOST_AUTH } from '@/config/integration'

// 定义路由配置数组
const routes = [
  // 主布局 - 所有需要登录的后台页面都放在 MainLayout 下
  {
    path: '/',
    component: MainLayout,
    redirect: '/geoWorkbench',
    children: [
      {
        path: 'accountManagement',
        component: AccountManagement,
        meta: { title: '账号管理' }
      },
      {
        path: 'articleGenerator',
        component: ArticleGenerator,
        meta: { title: '文章生成' }
      },
      {
        path: 'articleManagement',
        component: ArticleManagement,
        meta: { title: '文章管理' }
      },
      {
        path: 'coverManagement',
        component: CoverManagement,
        meta: { title: '封面管理' }
      },
      {
        path: 'medicalManagement',
        component: MedicalManagement,
        meta: { title: '医务管理' }
      },
      {
        path: 'departmentManagement',
        component: DepartmentManagement,
        meta: { title: '科室管理' }
      },
      {
        path: 'doctorManagement',
        component: DoctorManagement,
        meta: { title: '医生管理' }
      },
      {
        path: 'hospitalManagement',
        component: HospitalManagement,
        meta: { title: '医院管理' }
      },
      {
        path: 'knowledgeBase',
        component: KnowledgeBase,
        meta: { title: '知识库' }
      },
      {
        path: 'vectorConversion',
        component: KnowledgeBase,
        meta: { title: '向量转换' }
      },
      {
        path: 'geoWorkbench',
        component: GeoWorkbench,
        meta: { title: 'GEO运营' }
      },
      {
        path: 'profile',
        component: Profile,
        meta: { title: '个人中心' }
      },
      {
        path: 'publishRecord',
        component: PublishRecord,
        meta: { title: '发布记录' }
      },
      {
        path: 'titleManagement',
        component: TitleManagement,
        meta: { title: '职称管理' }
      },
      {
        path: 'contentPublish',
        component: ContentPublishWorkbench,
        meta: { title: '内容发布' }
      },
      {
        path: 'systemWorkbench',
        component: SystemWorkbench,
        meta: { title: '系统管理' }
      }
    ]
  },
  // 404 兜底路由
  {
    path: '/:pathMatch(.*)*',
    component: NotFound,
    meta: { requiresAuth: false }
  },
    // 独立页面（无布局）
  {
    path: '/login',
    component: Login,
  },
  {
    path: '/register',
    component: Register,
  },
]

// 创建路由实例
const router = createRouter({
  history: createWebHashHistory(),
  routes, // 使用上面定义的路由配置数组
})

router.beforeEach((to) => {
  if (USE_HOST_AUTH && ['/login', '/register', '/systemWorkbench'].includes(to.path)) {
    return { path: '/geoWorkbench' }
  }
  return true
})

export default router
