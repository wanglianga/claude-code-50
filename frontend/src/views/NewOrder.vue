<template>
  <div>
    <el-row :gutter="14">
      <el-col :span="15">
        <el-card shadow="never">
          <template #header>
            <div class="card-h"><el-icon><EditPen /></el-icon> 深夜急症配药申请</div>
          </template>
          <el-form :model="form" label-width="110px">
            <el-form-item label="病症描述">
              <el-input v-model="form.symptoms" type="textarea" :rows="2"
                        placeholder="如：深夜高热39℃伴呕吐、腹痛腹泻…" />
            </el-form-item>
            <el-form-item label="过敏史">
              <el-input v-model="form.allergies" placeholder="如：青霉素过敏；无则填“无”" />
            </el-form-item>
            <el-form-item label="既往/当前用药">
              <el-input v-model="form.currentMedication" placeholder="如：二甲双胍 每日2次" />
            </el-form-item>
            <el-form-item label="电子处方">
              <el-upload :show-file-list="false" :http-request="uploadRx" accept="image/*">
                <el-button :icon="Picture">上传处方图片</el-button>
              </el-upload>
              <el-image v-if="form.prescriptionImagePath" :src="form.prescriptionImagePath"
                        style="width:180px;margin-top:8px" fit="cover" />
              <span class="hint">字迹/图片不清会被药师退回补充</span>
            </el-form-item>
            <el-form-item label="开方医生">
              <el-input v-model="form.prescribingDoctor" placeholder="医生姓名" style="width:160px" />
              <el-input v-model="form.doctorHospital" placeholder="急诊医院" style="width:240px;margin-left:8px" />
            </el-form-item>
            <el-form-item label="处方开具日期">
              <el-date-picker v-model="form.prescriptionDate" type="date" value-format="YYYY-MM-DD"
                              :disabled-date="future" placeholder="选择日期" />
              <span class="hint">急诊处方有效期 7 天（至 {{ validUntil }}），过期处方将被拦截不能配药</span>
              <el-tag v-if="rxExpired" type="danger" size="small" style="margin-left:8px">该处方已过期，无法提交</el-tag>
            </el-form-item>
            <el-form-item label="医保凭证号">
              <el-input v-model="form.insuranceNo" placeholder="18位医保电子凭证号" style="width:300px" />
              <el-checkbox v-model="form.chronicFlag" style="margin-left:12px">门诊慢病</el-checkbox>
              <div class="hint">演示：普通号码→通过；尾号 000→拒付；尾号 ERR→接口异常（可回退）</div>
            </el-form-item>
            <el-form-item label="是否配送">
              <el-radio-group v-model="form.needsDelivery">
                <el-radio :value="true">夜间配送</el-radio>
                <el-radio :value="false">到店自取</el-radio>
              </el-radio-group>
            </el-form-item>
            <template v-if="form.needsDelivery">
              <el-form-item label="配送地址">
                <el-input v-model="form.address" placeholder="详细地址" />
              </el-form-item>
              <el-form-item label="联系电话">
                <el-input v-model="form.contactPhone" style="width:200px" />
              </el-form-item>
              <el-form-item label="收件人">
                <el-input v-model="form.recipient" placeholder="患者本人或家属姓名" />
              </el-form-item>
            </template>

            <el-divider>处方药品</el-divider>
            <div v-for="(it, i) in form.items" :key="i" class="item-row">
              <el-select v-model="it.drugId" filterable placeholder="选择药品" style="width:300px"
                         @change="onDrug(i)">
                <el-option v-for="d in drugs" :key="d.id" :label="drugLabel(d)" :value="d.id">
                  <span>{{ d.name }}</span>
                  <el-tag size="small" :type="ctrlType(d.controlCategory)" style="margin:0 6px">
                    {{ CONTROL_NAMES[d.controlCategory] }}
                  </el-tag>
                  <el-tag v-if="d.coldChain" size="small" type="primary">冷藏</el-tag>
                  <span class="stock">存{{ d.stock }} 批号{{ d.batchNo }}</span>
                </el-option>
              </el-select>
              <el-input-number v-model="it.quantity" :min="1" :max="9" />
              <el-input v-model="it.dosage" placeholder="用法用量，如 每次1片每日2次" style="width:260px" />
              <el-button link type="danger" @click="form.items.splice(i,1)">删除</el-button>
            </div>
            <el-button plain type="primary" :icon="Plus" @click="form.items.push({drugId:null,quantity:1,dosage:''})">
              添加药品
            </el-button>

            <el-form-item style="margin-top:18px">
              <el-button type="primary" size="large" :loading="submitting" @click="submit">
                提交夜间配药申请
              </el-button>
              <span class="hint">提交后平台自动核验：处方有效期 / 管制类别 / 库存批号 / 冷藏要求 / 药师值班</span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="9">
        <el-card shadow="never">
          <template #header><div class="card-h"><el-icon><View /></el-icon> 药品提示</div></template>
          <el-timeline v-if="selected.length">
            <el-timeline-item v-for="d in selected" :key="d.id" :timestamp="d.batchNo + ' / 效期 ' + d.expiryDate">
              <b>{{ d.name }}</b>
              <el-tag v-if="d.controlCategory==='CONTROLLED'" size="small" type="danger" style="margin-left:6px">
                管制·夜间限购2
              </el-tag>
              <div class="hint">禁忌：{{ d.contraindication || '无特殊' }}</div>
              <div class="hint">替代药：{{ d.alternative || '无' }}</div>
              <el-tag v-if="nearExpiry(d)" size="small" type="warning">临期批号</el-tag>
              <el-tag v-if="d.stock===0" size="small" type="danger">缺货</el-tag>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="先选择药品" :image-size="70" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Picture, Plus } from '@element-plus/icons-vue'
import api from '../api'
import { CONTROL_NAMES } from '../store'

const router = useRouter()
const drugs = ref([])
const submitting = ref(false)
const form = reactive({
  symptoms: '', allergies: '', currentMedication: '',
  prescriptionImagePath: '',
  prescribingDoctor: '', doctorHospital: '',
  prescriptionDate: new Date().toISOString().slice(0, 10),
  insuranceNo: '', chronicFlag: false,
  needsDelivery: true, address: '', contactPhone: '', recipient: '',
  items: [{ drugId: null, quantity: 1, dosage: '' }]
})

const drugMap = computed(() => Object.fromEntries(drugs.value.map(d => [d.id, d])))
const selected = computed(() => form.items.map(i => drugMap.value[i.drugId]).filter(Boolean))
const validUntil = computed(() => form.prescriptionDate
  ? new Date(new Date(form.prescriptionDate).getTime() + 7 * 864e5).toISOString().slice(0, 10) : '')
const rxExpired = computed(() => form.prescriptionDate
  && new Date(validUntil.value + 'T23:59:59') < new Date(new Date().toDateString()))
const drugLabel = d => `${d.name} ${d.spec||''}`
const nearExpiry = d => d.expiryDate && d.expiryDate <= add90()
function add90() {
  const d = new Date(); d.setDate(d.getDate() + 90)
  return d.toISOString().slice(0, 10)
}
const future = d => d.getTime() > Date.now()
const ctrlType = c => ({ CONTROLLED: 'danger', RX: 'warning', OTC: 'success', NORMAL: 'info' }[c] || 'info')
const onDrug = () => {}

async function uploadRx(opt) {
  const fd = new FormData()
  fd.append('file', opt.file)
  const res = await api.post('/api/files/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  form.prescriptionImagePath = res.path
  ElMessage.success('处方图片已上传')
}

async function submit() {
  if (!form.items.some(i => i.drugId)) return ElMessage.warning('请至少选择一种药品')
  if (!form.prescriptionImagePath) return ElMessage.warning('请上传电子处方图片')
  if (rxExpired.value) return ElMessage.error('处方已过有效期（至 ' + validUntil.value + '），请请医生重新开具处方')
  submitting.value = true
  try {
    const res = await api.post('/api/orders', form)
    ElMessage.success('已提交，配药单号 ' + res.data.order.orderNo)
    router.push('/orders/' + res.data.order.id)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  drugs.value = await api.get('/api/drugs')
})
</script>

<style scoped>
.card-h { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.item-row { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; flex-wrap: wrap; }
.hint { color: #909399; font-size: 12px; margin-left: 8px; }
.stock { color: #909399; font-size: 12px; }
</style>
