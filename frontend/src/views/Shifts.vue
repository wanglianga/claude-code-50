<template>
  <el-row :gutter="12">
    <el-col :span="10">
      <el-card shadow="never">
        <template #header><div class="card-h"><el-icon><Switch /></el-icon> 夜间值班与药师交接</div></template>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom:10px"
          title="交接后：交班人下班，接班人上岗；其名下在途配药单全部改挂接班人，并在每张单的时间线中记录交接原因。" />
        <div style="margin-bottom:10px">
          <el-tag type="success">当前在岗药师</el-tag>
          <el-tag v-for="s in onDuty" :key="s.id" style="margin-left:6px" size="large">
            {{ s.pharmacist.displayName }}（{{ s.pharmacist.licenseNo }}）
          </el-tag>
          <el-button v-if="!iAmOnDuty && role==='PHARMACIST'" type="primary" size="small" style="margin-left:10px"
                     @click="startShift">我来上班</el-button>
        </div>
        <el-form :model="ho" label-width="92px">
          <el-form-item label="接班药师">
            <el-select v-model="ho.toPharmacistId" placeholder="选择接班药师" style="width:100%">
              <el-option v-for="p in pharmacists" :key="p.id" :label="p.displayName + '（' + p.licenseNo + '）'"
                         :value="p.id" :disabled="onDutyIds.includes(p.id)" />
            </el-select>
          </el-form-item>
          <el-form-item label="交接说明">
            <el-input v-model="ho.summary" type="textarea" :rows="4"
              placeholder="在途管制单、待医生回电、医保异常待重试、冷链设备情况等" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :disabled="!iAmOnDuty" @click="doHandover">执行交接（在途单据随单移交）</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </el-col>
    <el-col :span="14">
      <el-card shadow="never">
        <template #header><div class="card-h"><el-icon><Clock /></el-icon> 班次记录</div></template>
        <el-table :data="shifts" size="small" stripe>
          <el-table-column label="药师" width="120">
            <template #default="{row}">{{ row.pharmacist.displayName }}</template>
          </el-table-column>
          <el-table-column prop="shiftDate" label="日期" width="110" />
          <el-table-column label="状态" width="90">
            <template #default="{row}">
              <el-tag size="small" :type="row.onDuty?'success':'info'">{{ row.onDuty ? '在岗' : '已下班' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="交接" min-width="220">
            <template #default="{row}">
              <template v-if="row.handedTo">
                → {{ row.handedTo.displayName }}
                <div class="hint">{{ row.handoverSummary }}</div>
              </template>
              <span v-else class="hint">—</span>
            </template>
          </el-table-column>
          <el-table-column label="交接时间" width="160">
            <template #default="{row}">{{ fmt(row.handedOverAt) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { auth } from '../store'

const role = computed(() => auth.user.role)
const shifts = ref([])
const onDuty = ref([])
const pharmacists = ref([])
const ho = reactive({ toPharmacistId: null, summary: '' })

const onDutyIds = computed(() => onDuty.value.map(s => s.pharmacist.id))
const iAmOnDuty = computed(() => onDutyIds.value.includes(auth.user.id))
const fmt = t => t ? t.replace('T', ' ').slice(0, 16) : ''

async function load() {
  shifts.value = await api.get('/api/shifts')
  onDuty.value = await api.get('/api/shifts/on-duty')
  const users = await api.get('/api/users')
  pharmacists.value = users.filter(u => u.role === 'PHARMACIST')
}
async function startShift() {
  await api.post('/api/shifts/start')
  ElMessage.success('已上岗值班')
  load()
}
async function doHandover() {
  if (!ho.toPharmacistId) return ElMessage.warning('请选择接班药师')
  const res = await api.post('/api/shifts/handover', { ...ho, orderIds: [] })
  ElMessage.success(`交接完成，移交在途配药单 ${res.data.movedOrders} 张`)
  ho.summary = ''
  load()
}
onMounted(load)
</script>

<style scoped>
.card-h { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.hint { color: #909399; font-size: 12px; }
</style>
