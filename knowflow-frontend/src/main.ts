import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus/es/components/dropdown/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElLoading } from 'element-plus/es/components/loading/index.mjs'
import { ElOption, ElSelect } from 'element-plus/es/components/select/index.mjs'
import { ElProgress } from 'element-plus/es/components/progress/index.mjs'
import { ElSkeleton } from 'element-plus/es/components/skeleton/index.mjs'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs'
import { ElTooltip } from 'element-plus/es/components/tooltip/index.mjs'
import {
  ArrowLeft,
  ChatDotRound,
  Check,
  Clock,
  Collection,
  Document,
  DocumentCopy,
  Files,
  Folder,
  Loading,
  MoreFilled,
  Plus,
  Promotion,
  Refresh,
  SwitchButton,
  Upload,
  UploadFilled,
} from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import './assets/main.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

const elementComponents = [
  ElButton,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElOption,
  ElProgress,
  ElSelect,
  ElSkeleton,
  ElTable,
  ElTableColumn,
  ElTooltip,
]

const iconComponents = {
  ArrowLeft,
  ChatDotRound,
  Check,
  Clock,
  Collection,
  Document,
  DocumentCopy,
  Files,
  Folder,
  Loading,
  MoreFilled,
  Plus,
  Promotion,
  Refresh,
  SwitchButton,
  Upload,
  UploadFilled,
}

elementComponents.forEach((component) => app.use(component))

Object.entries(iconComponents).forEach(([name, component]) => {
  app.component(name, component)
})

app.use(createPinia())
app.use(router)
app.use(ElLoading)

app.mount('#app')
