import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({ baseURL: '/' })

api.interceptors.request.use(cfg => {
  const token = localStorage.getItem('np_token')
  if (token) cfg.headers['X-Auth-Token'] = token
  return cfg
})

api.interceptors.response.use(
  resp => resp.data,
  err => {
    const status = err.response?.status
    const msg = err.response?.data?.message || err.message || '请求失败'
    if (status === 401) {
      localStorage.removeItem('np_token')
      localStorage.removeItem('np_user')
      if (!location.hash.startsWith('#/login')) location.hash = '#/login'
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export default api
