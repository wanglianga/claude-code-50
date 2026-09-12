<template>
  <el-container v-if="auth.user" style="height:100vh">
    <el-header class="topbar">
      <div class="brand">
        <el-icon :size="22"><MoonNight /></el-icon>
        <span>城市夜间药房急症配药与处方核验平台</span>
      </div>
      <el-menu mode="horizontal" :ellipsis="false" :default-active="$route.path" router
               class="nav" background-color="transparent" text-color="#dfe8ff"
               active-text-color="#ffd666">
        <el-menu-item index="/new-order" v-if="auth.user.role==='PATIENT'">
          <el-icon><EditPen /></el-icon>深夜配药申请
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><Tickets /></el-icon>配药单协同台
        </el-menu-item>
        <el-menu-item index="/drugs" v-if="['WAREHOUSE','ADMIN','PHARMACIST'].includes(auth.user.role)">
          <el-icon><Box /></el-icon>药品批号库存
        </el-menu-item>
        <el-menu-item index="/shifts" v-if="['PHARMACIST','ADMIN'].includes(auth.user.role)">
          <el-icon><Switch /></el-icon>值班交接
        </el-menu-item>
        <el-menu-item index="/complaints" v-if="['CUSTOMER_SERVICE','ADMIN','PHARMACIST'].includes(auth.user.role)">
          <el-icon><Service /></el-icon>投诉追溯台
        </el-menu-item>
      </el-menu>
      <div class="userbox">
        <el-tag effect="dark" type="warning" round>{{ ROLE_NAMES[auth.user.role] || auth.user.role }}</el-tag>
        <span class="uname">{{ auth.user.displayName }}</span>
        <el-button link type="primary" @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-main class="main">
      <router-view v-slot="{ Component }">
        <transition name="fade">
          <component :is="Component" />
        </transition>
      </router-view>
    </el-main>
  </el-container>
  <router-view v-else />
</template>

<script setup>
import { useRouter } from 'vue-router'
import api from './api'
import { auth, ROLE_NAMES } from './store'

const router = useRouter()

async function logout() {
  try { await api.post('/api/auth/logout') } catch {}
  auth.logout()
  router.push('/login')
}
</script>

<style>
html, body, #app { height: 100%; margin: 0; }
body { font-family: "PingFang SC", "Microsoft YaHei", sans-serif; background: #f2f5fb; }
.topbar {
  display: flex; align-items: center; gap: 18px;
  background: linear-gradient(90deg, #1d2b53, #27408b 55%, #31529b);
  color: #fff; padding: 0 18px; height: 58px;
}
.brand { display: flex; align-items: center; gap: 8px; font-weight: 700; font-size: 17px; white-space: nowrap; }
.nav { flex: 1; border-bottom: none !important; }
.nav .el-menu-item { border-bottom: none !important; }
.userbox { display: flex; align-items: center; gap: 10px; white-space: nowrap; }
.uname { font-size: 13px; color: #dfe8ff; }
.main { padding: 16px; overflow-y: auto; background: #f2f5fb; }
.fade-enter-active, .fade-leave-active { transition: opacity .18s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; }
</style>
