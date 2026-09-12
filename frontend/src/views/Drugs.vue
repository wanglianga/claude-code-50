<template>
  <el-card shadow="never">
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <div class="card-h"><el-icon><Box /></el-icon> 药品目录 · 库存批号 · 效期 · 冷藏</div>
        <el-input v-model="q" placeholder="按名称搜索" clearable style="width:220px" @keyup.enter="load" />
      </div>
    </template>
    <el-table :data="drugs" stripe>
      <el-table-column prop="name" label="药品" min-width="170">
        <template #default="{row}">
          <b>{{ row.name }}</b>
          <el-tag v-if="row.controlCategory==='CONTROLLED'" size="small" type="danger">管制</el-tag>
          <el-tag v-if="row.coldChain" size="small" type="primary">2-8℃冷藏</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="spec" label="规格" width="150" />
      <el-table-column prop="manufacturer" label="厂家" width="110" />
      <el-table-column label="类别" width="90">
        <template #default="{row}">{{ CONTROL_NAMES[row.controlCategory] }}</template>
      </el-table-column>
      <el-table-column label="医保" width="70">
        <template #default="{row}"><el-tag size="small">{{ row.insuranceCatalog }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="price" label="单价" width="80" align="right">
        <template #default="{row}">¥{{ Number(row.price).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="库存" width="110">
        <template #default="{row}">
          <el-tag :type="row.stock===0?'danger':row.stock<10?'warning':'success'" size="small">
            {{ row.stock }} 盒
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="批号/效期" width="190">
        <template #default="{row}">
          <span class="mono">{{ row.batchNo }}</span>
          <el-tag v-if="near(row)" size="small" type="warning" style="margin-left:4px">临期</el-tag>
          <div class="hint">{{ row.expiryDate }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="contraindication" label="禁忌" min-width="180" show-overflow-tooltip />
      <el-table-column prop="alternative" label="替代药建议" min-width="170" show-overflow-tooltip />
      <el-table-column v-if="editable" label="维护" width="100" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="openEdit(row)">调库存</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editDialog" title="维护库存/批号/效期" width="420px">
      <el-form :model="edit" label-width="90px">
        <el-form-item label="药品">{{ edit.name }}</el-form-item>
        <el-form-item label="库存数量"><el-input-number v-model="edit.stock" :min="0" /></el-form-item>
        <el-form-item label="批号"><el-input v-model="edit.batchNo" /></el-form-item>
        <el-form-item label="效期"><el-date-picker v-model="edit.expiryDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog=false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { auth, CONTROL_NAMES } from '../store'

const drugs = ref([])
const q = ref('')
const editable = computed(() => ['WAREHOUSE', 'ADMIN'].includes(auth.user.role))
const editDialog = ref(false)
const edit = reactive({ id: null, name: '', stock: 0, batchNo: '', expiryDate: '' })

const near = d => d.expiryDate && d.expiryDate <= add90()
function add90() {
  const d = new Date(); d.setDate(d.getDate() + 90)
  return d.toISOString().slice(0, 10)
}

async function load() {
  drugs.value = await api.get('/api/drugs', { params: q.value ? { q: q.value } : {} })
}
function openEdit(row) {
  Object.assign(edit, { id: row.id, name: row.name, stock: row.stock, batchNo: row.batchNo, expiryDate: row.expiryDate })
  editDialog.value = true
}
async function saveEdit() {
  await api.put('/api/drugs/' + edit.id, { stock: edit.stock, batchNo: edit.batchNo, expiryDate: edit.expiryDate })
  editDialog.value = false
  ElMessage.success('库存已更新')
  load()
}
onMounted(load)
</script>

<style scoped>
.card-h { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.hint { color: #909399; font-size: 12px; }
</style>
