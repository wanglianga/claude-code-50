<template>
  <div class="login-bg">
    <el-card class="login-card" shadow="always">
      <div class="logo"><el-icon :size="30" color="#27408b"><MoonNight /></el-icon></div>
      <h2 class="title">夜间药房急症配药平台</h2>
      <p class="subtitle">处方核验 · 医保结算 · 多角色协同 · 全程追溯</p>
      <el-form :model="form" @keyup.enter="doLogin">
        <el-form-item>
          <el-input v-model="form.username" size="large" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" size="large" placeholder="密码"
                    :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="doLogin">
          登 录
        </el-button>
      </el-form>
      <el-divider>演示账号（点击填充，密码均为 123456）</el-divider>
      <div class="accounts">
        <el-tag v-for="a in accounts" :key="a.u" class="acc" :type="a.type" effect="plain"
                @click="fill(a.u)">{{ a.label }}：{{ a.u }}</el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import api from '../api'
import { auth } from '../store'

const router = useRouter()
const form = reactive({ username: '', password: '123456' })
const loading = ref(false)

const accounts = [
  { u: 'lifang', label: '患者', type: 'success' },
  { u: 'wangyaoshi', label: '药师', type: 'primary' },
  { u: 'chenyin', label: '收银', type: 'warning' },
  { u: 'laocang', label: '仓管', type: 'warning' },
  { u: 'xiaoma', label: '骑手', type: 'info' },
  { u: 'kefu_an', label: '客服', type: 'danger' },
  { u: 'admin', label: '管理员', type: 'info' }
]
const fill = u => { form.username = u; form.password = u === 'admin' ? 'admin123' : '123456' }

async function doLogin() {
  if (!form.username) return ElMessage.warning('请输入用户名')
  loading.value = true
  try {
    const res = await api.post('/api/auth/login', form)
    localStorage.setItem('np_token', res.token)
    auth.setUser(res.user)
    ElMessage.success('欢迎，' + res.user.displayName)
    router.push('/orders')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-bg {
  height: 100vh; display: flex; align-items: center; justify-content: center;
  background: radial-gradient(1200px 600px at 20% -10%, #3a5bbf 0%, #1d2b53 55%, #101a38 100%);
}
.login-card { width: 460px; padding: 18px 26px 24px; border-radius: 14px; }
.logo { text-align: center; }
.title { text-align: center; margin: 10px 0 4px; color: #1d2b53; }
.subtitle { text-align: center; color: #7a869f; font-size: 13px; margin: 0 0 14px; }
.accounts { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.acc { cursor: pointer; }
</style>
