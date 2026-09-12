<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px">
          <div class="card-h"><el-icon><Tickets /></el-icon> 配药单协同台（患者·药师·收银·仓管·骑手·客服共用同一张单）</div>
          <el-radio-group v-model="filter" size="small" @change="load">
            <el-radio-button value="all">全部</el-radio-button>
            <el-radio-button value="todo">与我相关待办</el-radio-button>
            <el-radio-button value="active">在途</el-radio-button>
            <el-radio-button value="done">已完成/终结</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <el-table :data="filtered" stripe @row-click="open" style="cursor:pointer" size="default">
        <el-table-column prop="orderNo" label="配药单号" width="190">
          <template #default="{row}"><span class="mono">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column label="患者" width="130">
          <template #default="{row}">{{ row.patient?.displayName }}</template>
        </el-table-column>
        <el-table-column prop="symptoms" label="病症" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="150">
          <template #default="{row}">
            <el-tag :type="STATUS_TYPE[row.status]" size="small">{{ STATUS_NAMES[row.status] }}</el-tag>
            <el-tag v-if="row.decision==='PAUSE'" type="danger" size="small" effect="dark" style="margin-left:4px">暂停</el-tag>
            <el-tag v-if="row.decision==='OFFLINE'" type="info" size="small" effect="dark" style="margin-left:4px">线下</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="医保" width="100">
          <template #default="{row}">
            <el-tag size="small" :type="insType(row.insuranceStatus)">{{ INSURANCE_NAMES[row.insuranceStatus] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="费用(自费)" width="110" align="right">
          <template #default="{row}">
            <span v-if="row.totalAmount">¥{{ Number(row.totalAmount).toFixed(2) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="药师/骑手" width="170">
          <template #default="{row}">
            <div>药：{{ row.pharmacist?.displayName || '—' }}</div>
            <div>骑：{{ row.rider?.displayName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="170">
          <template #default="{row}">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" @click.stop="open(row)">打开</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { auth, STATUS_NAMES, STATUS_TYPE, INSURANCE_NAMES } from '../store'

const router = useRouter()
const orders = ref([])
const filter = ref('all')

const DONE = ['COMPLETED', 'OFFLINE_REFERRAL', 'CANCELLED']
const TODO_STATUS = {
  PHARMACIST: ['SUBMITTED', 'PENDING_SUPPLEMENT', 'DOCTOR_VERIFY', 'INSURANCE_CHECK', 'PAUSED'],
  CASHIER: ['WAIT_PAYMENT', 'INSURANCE_CHECK'],
  WAREHOUSE: ['PAID', 'PICKING'],
  RIDER: ['PICKING', 'DELIVERING', 'PAID'],
  CUSTOMER_SERVICE: ['PAUSED'],
  PATIENT: ['PENDING_SUPPLEMENT', 'WAIT_PAYMENT', 'DOCTOR_VERIFY'],
  ADMIN: []
}

const filtered = computed(() => {
  if (filter.value === 'active') return orders.value.filter(o => !DONE.includes(o.status))
  if (filter.value === 'done') return orders.value.filter(o => DONE.includes(o.status))
  if (filter.value === 'todo') {
    const statuses = TODO_STATUS[auth.user.role] || []
    return orders.value.filter(o => {
      if (auth.user.role === 'PATIENT') return true
      if (auth.user.role === 'ADMIN') return true
      if (statuses.includes(o.status)) return true
      if (o.status === 'PAUSED') return true
      return false
    })
  }
  return orders.value
})

const insType = s => ({ APPROVED: 'success', REJECTED: 'danger', ERROR: 'danger', ROLLED_BACK: 'warning' }[s] || 'info')
const fmt = t => t ? t.replace('T', ' ').slice(0, 16) : ''
const open = row => router.push('/orders/' + row.id)

async function load() {
  const res = await api.get('/api/orders')
  orders.value = res.data || []
}
onMounted(load)
</script>

<style scoped>
.card-h { display: flex; align-items: center; gap: 6px; font-weight: 600; }
</style>
