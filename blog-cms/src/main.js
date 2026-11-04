import {createApp} from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import {createPinia} from 'pinia'
import './styles/index.css'

const app = createApp(App)

// 图标全量注册，模板里可直接 <el-icon><Edit/></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
	app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, {locale: zhCn})
app.mount('#app')
