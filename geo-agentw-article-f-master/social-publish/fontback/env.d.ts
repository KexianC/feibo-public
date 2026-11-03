/// <reference types="vite/client" />

import 'pinia'

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module 'pinia-persistedstate-plugin'
declare module 'element-plus/dist/locale/zh-cn.mjs'

declare module 'pinia' {
  export interface DefineStoreOptionsBase<S, Store> {
    persist?: boolean | Record<string, unknown>
  }
}
