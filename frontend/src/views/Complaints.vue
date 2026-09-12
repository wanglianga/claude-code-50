<template>
  <el-card shadow="never">
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <div class="card-h"><el-icon><Service /></el-icon> 客服投诉追溯台（同一配药记录 → 审方/出库/支付/签收）</div>
        <el-radio-group v-model="status" size="small" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="OPEN">处理中</el-radio-button>
          <el-radio-button value="RESOLVED">已解决</el-radio-button>
          <el-radio-button value="REJECTED">不成立</el-radio-button>
        </el-radio-group>
      </div>
    </template>
    <el-table :data="list" stripe>
      <el-table-column label="反馈时间" width="160">
        <template #default="{row}">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="130">
        <template #default="{row}">
          <el-tag size="small" :type="catType(row.category)">{{ COMPLAINT_NAMES[row.category] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="配药单" min-width="200">
        <template #default="{row}">
          <el-link type="primary" @click="open(row.order.id)">{{ row.order.orderNo }}</el-link>
          <span class="hint">（{{ row.order.patient.displayName }}）· 追溯环节：{{ stageName(row.tracedStage) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="反馈内容" min-width="240" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{row}">
          <el-tag size="small" :type="row.status==='RESOLVED'?'success':row.status==='REJECTED'?'info':'warning'">
            {{ row.status==='OPEN'?'处理中':row.status==='RESOLVED'?'已解决':'不成立' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="handlingNote" label="核查结论" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="open(row.order.id)">查看全过程档案</el-button>
          <el-button v-if="row.status==='OPEN'" link type="success" @click="handle(row, 'RESOLVED')">标记解决</el-button>
          <el-button v-if="row.status==='OPEN'" link type="info" @click="handle(row, 'REJECTED')">不成立</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { COMPLAINT_NAMES } from '../store'

const router = useRouter()
const list = ref([])
const status = ref('')

const fmt = t => t ? t.replace('T', ' ').slice(0, 16) : ''
const catType = c => ({ ADVERSE_REACTION: 'danger', MISSING_DRUG: 'warning', INSURANCE_DISPUTE: 'primary' }[c] || 'info')
const stageName = s => ({ REVIEW: '审方用药', OUTBOUND: '出库批号', PAYMENT: '医保支付', DELIVERY: '配送签收' }[s] || s)
const open = id => router.push('/orders/' + id)

async function load() {
  list.value = await api.get('/api/complaints', { params: status.value ? { status: status.value } : {} })
}
async function handle(row, st) {
  const { value } = await ElMessageBox.prompt(
    `依据配药档案核查（审方意见/批号快照/付款明细/温控签收），${st==='RESOLVED'?'解决':'驳回'}说明：`,
    st==='RESOLVED' ? '标记已解决' : '标记不成立',
    { inputType: 'textarea' }).catch(() => ({}))
  if (value === undefined) return
  await api.post(`/api/complaints/${row.id}/handle`, { status: st, note: value })
  ElMessage.success('已保存核查结论并写入该单时间线')
  load()
}
onMounted(load)
</script>

<style scoped>
.card-h { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.hint { color: #909399; font-size: 12px; }
</style>
