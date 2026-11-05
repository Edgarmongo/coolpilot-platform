import {createApp} from 'vue'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import {createPinia} from 'pinia'
import {i18n} from './i18n'
import './styles/index.css'

const app = createApp(App)

// 图标全量注册，模板里可直接 <el-icon><Edit/></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
	app.component(key, component)
}

app.use(createPinia())
app.use(i18n)
app.use(router)
// Element Plus 的语言包随界面语言走，在 App.vue 里用 el-config-provider 切换
app.use(ElementPlus)
app.mount('#app')
