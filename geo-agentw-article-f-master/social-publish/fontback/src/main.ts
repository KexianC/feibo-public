//导入VUE初始化VUE工程
import { createApp } from 'vue'
//导入用于存储token的插件
import { createPinia } from 'pinia'
//导入永久存储的插件，服务于上面那个pinia，坚持到底的状态
import {createPersistedState} from 'pinia-persistedstate-plugin'
import App from './App.vue'
//导入scss的样式包
import './assets/main.scss'
//导入路由，前端用于管理路由的组件
import router from '@/router/index.js'
//导入饿了么组件库
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
//设置组件库默认中文
import locale from 'element-plus/dist/locale/zh-cn.mjs'
//导入element-plus icon
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const app = createApp(App)
const pinia = createPinia()
const persist = createPersistedState()
pinia.use(persist)

app.use(pinia)
app.use(router)
app.use(ElementPlus,{locale:locale})
//导入全部的icon
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
