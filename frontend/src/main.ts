import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import './styles.css'
import { analyzerDataKey } from './data/analyzerData'
import { createApiAnalyzerData } from './data/apiAdapter'

createApp(App).provide(analyzerDataKey, createApiAnalyzerData()).use(ElementPlus).mount('#app')
