<template>
  <div v-if="data">
    <!-- 抬头 -->
    <el-card shadow="never" class="head-card">
      <el-alert v-if="expired" type="error" show-icon :closable="false" style="margin-bottom:10px"
        title="处方已过有效期：按规定不得审方、医保核验与结算；须请医生重新开具处方后重新提交，或改为线下复诊。" />
      <div class="head">
        <div>
          <div class="title-line">
            <span class="mono no">{{ data.order.orderNo }}</span>
            <el-tag :type="STATUS_TYPE[data.order.status]" size="large">{{ STATUS_NAMES[data.order.status] }}</el-tag>
            <el-tag v-if="data.order.decision==='PAUSE'" type="danger" effect="dark">暂停配药中</el-tag>
            <el-tag v-if="data.order.decision==='OFFLINE'" type="info" effect="dark">已转线下复诊</el-tag>
            <el-tag v-if="data.order.coldChainRequired" type="primary" effect="plain">冷链单</el-tag>
            <el-tag v-if="data.order.needsDelivery" type="success" effect="plain">需配送</el-tag>
          </div>
          <div class="meta">
            患者：{{ data.order.patient?.displayName }}（{{ data.order.contactPhone }}） ·
            提交：{{ fmt(data.order.createdAt) }} ·
            药师：{{ data.order.pharmacist?.displayName || '未分配' }}
            <template v-if="data.order.handedTo"> · 交接：{{ data.order.handedFrom?.displayName }}
              → {{ data.order.handedTo?.displayName }}</template>
          </div>
        </div>
        <div>
          <el-button @click="$router.push('/orders')">返回列表</el-button>
          <el-button type="primary" plain :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="12">
      <el-col :span="15">
        <!-- 患者提交信息 -->
        <el-card shadow="never" class="block">
          <template #header><div class="card-h"><el-icon><Document /></el-icon>患者/家属深夜提交信息</div></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="病症" :span="2">{{ data.order.symptoms }}</el-descriptions-item>
            <el-descriptions-item label="过敏史">{{ data.order.allergies }}</el-descriptions-item>
            <el-descriptions-item label="既往用药">{{ data.order.currentMedication }}</el-descriptions-item>
            <el-descriptions-item label="开方医生">{{ data.order.prescribingDoctor }}（{{ data.order.doctorHospital }}）</el-descriptions-item>
            <el-descriptions-item label="处方日期/有效期">
              {{ data.order.prescriptionDate }} 至 {{ data.order.prescriptionValidUntil }}
            </el-descriptions-item>
            <el-descriptions-item label="医保凭证" :span="2">
              {{ data.order.insuranceNo || '无（全自费）' }}
              <el-tag v-if="data.order.chronicFlag" size="small" type="warning">门诊慢病</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="配送信息" :span="2">
              {{ data.order.needsDelivery
                ? (data.order.address + '，' + data.order.recipient + '，' + data.order.contactPhone)
                : '到店自取' }}
              <el-button v-if="canAddress" link type="primary" @click="addressDialog = true">改地址/收件人</el-button>
            </el-descriptions-item>
          </el-descriptions>
          <div v-if="data.order.prescriptionImagePath" class="rx">
            <el-image :src="data.order.prescriptionImagePath" :preview-src-list="[data.order.prescriptionImagePath]"
                      fit="cover" style="width:200px;height:128px;border-radius:6px" />
            <span class="hint">电子处方（点击放大核对医生签名/字迹）</span>
          </div>
          <el-upload v-if="role==='PATIENT' && data.order.status==='PENDING_SUPPLEMENT'"
                     :show-file-list="false" :http-request="resubmitUpload" style="margin-top:8px">
            <el-button type="warning" :icon="Upload">重新上传清晰处方（药师已退回补充）</el-button>
          </el-upload>
        </el-card>

        <!-- 药品明细与出库 -->
        <el-card shadow="never" class="block">
          <template #header><div class="card-h"><el-icon><Box /></el-icon>药品明细 / 审方 / 批号出库</div></template>
          <el-table :data="data.items" size="small" border>
            <el-table-column label="药品" min-width="170">
              <template #default="{row}">
                <div><b>{{ row.drug.name }}</b>
                  <el-tag v-if="row.drug.controlCategory==='CONTROLLED'" size="small" type="danger">管制</el-tag>
                  <el-tag v-if="row.drug.coldChain" size="small" type="primary">冷藏</el-tag>
                </div>
                <div class="hint">{{ row.drug.spec }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="55" />
            <el-table-column prop="dosage" label="用法用量" min-width="150" show-overflow-tooltip />
            <el-table-column label="配发" width="90">
              <template #default="{row}">
                <el-tag size="small" :type="fulfillType(row.fulfillStatus)">{{ fulfillName(row.fulfillStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审方/仓管意见与批号" min-width="200">
              <template #default="{row}">
                <div>{{ row.reviewNote }}</div>
                <div v-if="row.outBatchNo" class="hint">
                  出库批号 {{ row.outBatchNo }} / 效期 {{ row.outExpiryDate }}
                  <el-tag v-if="row.nearExpiryFlagDate" size="small" type="warning">临期</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="canWarehouse" label="缺药处置" width="130">
              <template #default="{row}">
                <el-button link type="primary" :disabled="!['PAID','PICKING','PAUSED'].includes(data.order.status)
                    || row.fulfillStatus==='REMOVED'" @click="openSub(row)">替代</el-button>
                <el-button link type="danger" :disabled="!['PAID','PICKING','PAUSED'].includes(data.order.status)
                    || row.fulfillStatus==='REMOVED'" @click="removeItem(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="data.order.outBatchSnapshot" class="snapshot">
            出库批号快照：{{ data.order.outBatchSnapshot }}（{{ fmt(data.order.outAt) }}，{{ data.order.outBy?.displayName }}）
          </div>
        </el-card>

        <!-- 药师审方 -->
        <el-card shadow="never" class="block">
          <template #header><div class="card-h"><el-icon><Stamp /></el-icon>药师审方（签名/剂量/禁忌/重复用药/替代药）</div></template>
          <el-alert v-if="!data.order.pharmacistSignedAt && data.order.status==='DOCTOR_VERIFY'" type="warning"
                    :closable="false" show-icon title="含管制药品，须先完成急诊医生电话核实后才能通过审方" style="margin-bottom:8px"/>
          <div v-if="data.order.pharmacistOpinion" class="opinion white-pre">{{ data.order.pharmacistOpinion }}</div>
          <div v-if="canPharmacist" class="actions">
            <el-button type="primary" :icon="Check" @click="doReview">审方通过并签名</el-button>
            <el-button type="warning" @click="doReturn">处方不清·退回补充</el-button>
            <el-button @click="doContact">联系患者确认关键药品</el-button>
            <el-button type="warning" plain @click="doctorDialog = true">急诊医生电话核实</el-button>
          </div>
        </el-card>

        <!-- 医保与费用 -->
        <el-card shadow="never" class="block">
          <template #header><div class="card-h"><el-icon><Money /></el-icon>医保核验与费用拆分</div></template>
          <div class="ins-row">
            <el-tag :type="insType(data.order.insuranceStatus)" size="large">
              {{ INSURANCE_NAMES[data.order.insuranceStatus] }}
            </el-tag>
            <span class="hint">{{ data.order.insuranceMessage || '尚未核验（审方通过后自动核验）' }}</span>
            <span v-if="canCashierish && ['ERROR','REJECTED'].includes(data.order.insuranceStatus)">
              <el-button size="small" type="primary" plain @click="retryInsurance">重试医保接口</el-button>
              <el-popconfirm title="经患者同意后将统筹/慢病清零转全自费，确认医保回退？" @confirm="rollback">
                <template #reference>
                  <el-button size="small" type="warning">医保回退（转全自费）</el-button>
                </template>
              </el-popconfirm>
            </span>
          </div>
          <el-row :gutter="10" class="fees" v-if="Number(data.order.drugTotal)>0">
            <el-col :span="4"><div class="fee"><label>药品费</label><b>¥{{ num(data.order.drugTotal) }}</b></div></el-col>
            <el-col :span="5"><div class="fee"><label>统筹支付</label><b class="green">-¥{{ num(data.order.poolingPay) }}</b></div></el-col>
            <el-col :span="5"><div class="fee"><label>门诊慢病额度</label><b class="green">-¥{{ num(data.order.chronicPay) }}</b></div></el-col>
            <el-col :span="5"><div class="fee"><label>夜间服务费</label><b>¥{{ num(data.order.nightFee) }}</b></div></el-col>
            <el-col :span="5"><div class="fee total"><label>患者应付</label><b>¥{{ num(data.order.totalAmount) }}</b></div></el-col>
          </el-row>
          <div v-if="data.order.paidAt" class="hint">
            已于 {{ fmt(data.order.paidAt) }} 通过{{ data.order.paymentMethod }}支付；
            发票抬头：{{ data.order.invoiceTitle || '个人' }}{{ data.order.invoiceTaxNo ? ' 税号'+data.order.invoiceTaxNo : '' }}
          </div>
          <div v-if="canCashier && data.order.status==='WAIT_PAYMENT'" class="actions">
            <el-button type="primary" :icon="Wallet" @click="payDialog = true">收银确认到账 / 开发票</el-button>
          </div>
        </el-card>

        <!-- 仓配协同 -->
        <el-card shadow="never" class="block">
          <template #header><div class="card-h"><el-icon><Van /></el-icon>出库 · 冷链 · 配送签收</div></template>
          <div class="actions">
            <template v-if="role==='RIDER' && data.order.needsDelivery && ['PAID','PICKING','DELIVERING','PAUSED'].includes(data.order.status)">
              <el-popconfirm title="确认已配备冷链箱+冰排，可全程 2-8℃ 保冷？" @confirm="cold(true)">
                <template #reference><el-button type="primary" plain :disabled="!data.order.coldChainRequired">骑手确认可保冷</el-button></template>
              </el-popconfirm>
              <el-button type="danger" plain :disabled="!data.order.coldChainRequired" @click="cold(false)">骑手无法保冷</el-button>
            </template>
            <el-tag v-if="data.order.riderColdCapable===true" type="primary">骑手已确认冷链能力</el-tag>
            <el-tag v-if="data.order.riderColdCapable===false" type="danger">骑手无法保冷·已暂停</el-tag>
            <el-button v-if="canOutbound" type="primary" :icon="Sell"
                       :disabled="data.order.status!=='PAID'" @click="outbound">仓管复核出库（冻结批号/扣库存）</el-button>
            <el-button v-if="canDeliver" type="success" :icon="Position"
                       :disabled="!['DELIVERING','PICKING'].includes(data.order.status)" @click="deliverDialog = true">
              {{ data.order.status==='DELIVERING' ? '配送到达·签收归档' : '到店自取·签收归档' }}
            </el-button>
          </div>
          <div v-if="data.order.deliveredAt" class="snapshot">
            签收人：{{ data.order.signedBy }}（{{ data.order.signRelation || '本人' }}） ·
            签收时间：{{ fmt(data.order.deliveredAt) }}
            <template v-if="data.order.coldTemperature"> · 到件温控 {{ data.order.coldTemperature }}℃</template>
            <template v-if="data.order.coldPhotoPath">
              · <el-link :href="data.order.coldPhotoPath" target="_blank" type="primary">温控照片</el-link>
            </template>
            <div class="hint">用药提醒：{{ data.order.medicationReminder }}</div>
          </div>
        </el-card>

        <!-- 投诉与追溯 -->
        <el-card shadow="never" class="block">
          <template #header><div class="card-h"><el-icon><Service /></el-icon>次日反馈与同一档案追溯</div></template>
          <el-table :data="data.complaints" size="small">
            <el-table-column label="类型" width="130">
              <template #default="{row}">{{ COMPLAINT_NAMES[row.category] }}</template>
            </el-table-column>
            <el-table-column prop="content" label="反馈内容" min-width="220" show-overflow-tooltip />
            <el-table-column label="追溯环节" width="120">
              <template #default="{row}">
                <el-tag size="small">{{ stageName(row.tracedStage) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{row}">
                <el-tag size="small" :type="row.status==='RESOLVED'?'success':row.status==='REJECTED'?'info':'warning'">
                  {{ row.status==='OPEN'?'处理中':row.status==='RESOLVED'?'已解决':'不成立' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="handlingNote" label="客服核查结论" min-width="180" show-overflow-tooltip />
          </el-table>
          <div class="actions" style="margin-top:8px">
            <template v-if="role==='PATIENT' || role==='CUSTOMER_SERVICE'">
              <el-button size="small" @click="fileComplaint('ADVERSE_REACTION')">反馈不良反应</el-button>
              <el-button size="small" @click="fileComplaint('MISSING_DRUG')">反馈漏发药品</el-button>
              <el-button size="small" @click="fileComplaint('INSURANCE_DISPUTE')">医保扣费争议</el-button>
            </template>
          </div>
        </el-card>
      </el-col>

      <!-- 右列：决策操作 + 时间线 -->
      <el-col :span="9">
        <el-card shadow="never" class="block">
          <template #header><div class="card-h"><el-icon><Flag /></el-icon>配药决策（每次修改必须说明原因）</div></template>
          <div class="decision" :class="'decision-'+data.order.decision">
            当前结论：<b>{{ DECISION_NAMES[data.order.decision] }}</b>
          </div>
          <div class="actions">
            <el-button v-if="canStaff && data.order.status!=='PAUSED'" type="danger" plain @click="pause">暂停配药</el-button>
            <el-button v-if="canStaff && data.order.status==='PAUSED'" type="success" @click="resume">恢复配药</el-button>
            <el-button v-if="canStaff" type="info" plain @click="offline">改为线下复诊</el-button>
          </div>
          <el-descriptions :column="1" border size="small" style="margin-top:8px">
            <el-descriptions-item label="医生电话核实">
              <template v-if="data.order.doctorVerifiedAt">
                {{ data.order.doctorVerifiedByName }} {{ data.order.doctorVerifiedPhone }} · {{ fmt(data.order.doctorVerifiedAt) }}
                <div class="hint">{{ data.order.doctorVerifyNote }}</div>
              </template>
              <span v-else class="hint">未核实（管制药品必需）</span>
            </el-descriptions-item>
            <el-descriptions-item label="值班交接">
              <template v-if="data.order.handedOverAt">
                {{ data.order.handedFrom?.displayName }} → {{ data.order.handedTo?.displayName }}
                · {{ fmt(data.order.handedOverAt) }}
                <div class="hint white-pre">{{ data.order.handoverNote }}</div>
              </template>
              <span v-else class="hint">本单未发生交接</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header><div class="card-h"><el-icon><Timer /></el-icon>全过程时间线（可解释、可追溯）</div></template>
          <el-timeline>
            <el-timeline-item v-for="e in data.events" :key="e.id" :timestamp="fmt(e.createdAt)" placement="top"
                              :type="timelineType(e.decision)">
              <el-tag size="small" :type="DECISION_TYPE[e.decision]" effect="plain">{{ eventTypeName(e.type) }}</el-tag>
              <el-tag size="small" v-if="e.actorName" style="margin-left:4px">{{ e.actorName }}·{{ ROLE_NAMES[e.actorRole] }}</el-tag>
              <div class="reason white-pre">{{ e.reason }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>

    <!-- 医生电话核实话窗 -->
    <el-dialog v-model="doctorDialog" title="急诊医生电话核实（管制药品必需）" width="480px">
      <el-form :model="doctor" label-width="92px">
        <el-form-item label="医生姓名"><el-input v-model="doctor.doctorName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="doctor.phone" /></el-form-item>
        <el-form-item label="核实内容"><el-input v-model="doctor.note" type="textarea" :rows="3"
          placeholder="核实处方真实性、管制药品用药依据与剂量" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="doctorDialog=false">取消</el-button>
        <el-button type="primary" @click="submitDoctor">核实通过</el-button>
      </template>
    </el-dialog>

    <!-- 支付/发票 -->
    <el-dialog v-model="payDialog" title="收银确认到账与发票" width="460px">
      <el-form :model="pay" label-width="92px">
        <el-form-item label="支付方式">
          <el-radio-group v-model="pay.method">
            <el-radio value="医保电子凭证">医保电子凭证</el-radio>
            <el-radio value="微信">微信</el-radio>
            <el-radio value="支付宝">支付宝</el-radio>
            <el-radio value="现金">现金</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发票抬头"><el-input v-model="pay.invoiceTitle" placeholder="个人或单位名称" /></el-form-item>
        <el-form-item label="税号"><el-input v-model="pay.invoiceTaxNo" placeholder="单位发票填写" /></el-form-item>
        <el-alert :title="'患者应付 ¥' + num(data.order.totalAmount)" type="warning" :closable="false" />
      </el-form>
      <template #footer>
        <el-button @click="payDialog=false">取消</el-button>
        <el-button type="primary" @click="doPay">确认收款</el-button>
      </template>
    </el-dialog>

    <!-- 替代药 -->
    <el-dialog v-model="subDialog" title="缺药/临期替代配发" width="560px">
      <el-form :model="sub" label-width="92px">
        <el-form-item label="原药品">{{ sub.originName }}</el-form-item>
        <el-form-item label="替代药品">
          <el-select v-model="sub.replacementDrugId" filterable placeholder="选择替代药">
            <el-option v-for="d in drugs" :key="d.id"
                       :label="d.name + ' ' + d.spec + '（库存' + d.stock + '，' + d.batchNo + '）'" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量"><el-input-number v-model="sub.quantity" :min="1" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="sub.note" type="textarea" :rows="2"
          placeholder="如：原批号临期/缺药，已征得患者同意，药效等同" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subDialog=false">取消</el-button>
        <el-button type="primary" @click="doSubstitute">确认替代并重算费用</el-button>
      </template>
    </el-dialog>

    <!-- 签收 -->
    <el-dialog v-model="deliverDialog" title="签收归档（温控照片/签收人/用药提醒）" width="500px">
      <el-form :model="deliver" label-width="100px">
        <el-form-item v-if="data.order.coldChainRequired && data.order.status==='DELIVERING'" label="温控照片">
          <el-upload :show-file-list="false" :http-request="uploadCold">
            <el-button :icon="Upload">上传温控照片</el-button>
          </el-upload>
          <el-image v-if="deliver.coldPhotoPath" :src="deliver.coldPhotoPath" style="width:140px;margin-top:6px" />
        </el-form-item>
        <el-form-item v-if="data.order.coldChainRequired && data.order.status==='DELIVERING'" label="到件温度℃">
          <el-input-number v-model="deliver.temperature" :min="-5" :max="15" :step="0.1" :precision="1" />
          <span class="hint">须在 2-8℃，否则暂停签收</span>
        </el-form-item>
        <el-form-item label="签收人"><el-input v-model="deliver.signedBy" /></el-form-item>
        <el-form-item label="关系">
          <el-radio-group v-model="deliver.signRelation">
            <el-radio value="本人">本人</el-radio><el-radio value="家属">家属</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="用药提醒">
          <el-input v-model="deliver.medicationReminder" type="textarea" :rows="3"
            placeholder="如：胰岛素2-8℃冷藏勿冷冻；注射后按时进餐" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliverDialog=false">取消</el-button>
        <el-button type="success" @click="doDeliver">确认签收并归档</el-button>
      </template>
    </el-dialog>

    <!-- 改地址 -->
    <el-dialog v-model="addressDialog" title="修改配送信息（全员同单可见）" width="480px">
      <el-form :model="addr" label-width="80px">
        <el-form-item label="地址"><el-input v-model="addr.address" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="addr.contactPhone" /></el-form-item>
        <el-form-item label="收件人"><el-input v-model="addr.recipient" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="addr.note" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addressDialog=false">取消</el-button>
        <el-button type="primary" @click="doAddress">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Check, Upload, Wallet, Sell, Position } from '@element-plus/icons-vue'
import api from '../api'
import { auth, STATUS_NAMES, STATUS_TYPE, DECISION_NAMES, DECISION_TYPE, INSURANCE_NAMES,
         COMPLAINT_NAMES, ROLE_NAMES } from '../store'

const route = useRoute()
const role = computed(() => auth.user.role)
const data = ref(null)
const drugs = ref([])

const canPharmacist = computed(() => role.value === 'PHARMACIST' || role.value === 'ADMIN')
const canCashier = computed(() => role.value === 'CASHIER' || role.value === 'ADMIN')
const canCashierish = computed(() => ['PHARMACIST','CASHIER','ADMIN'].includes(role.value))
const canWarehouse = computed(() => ['WAREHOUSE','PHARMACIST','ADMIN'].includes(role.value))
const canOutbound = computed(() => ['WAREHOUSE','ADMIN'].includes(role.value))
const canDeliver = computed(() => ['RIDER','WAREHOUSE','CUSTOMER_SERVICE','ADMIN'].includes(role.value))
const canStaff = computed(() => role.value !== 'PATIENT')
const canAddress = computed(() => ['PATIENT','CUSTOMER_SERVICE','RIDER','ADMIN'].includes(role.value)
  && !['COMPLETED','CANCELLED','OFFLINE_REFERRAL'].includes(data.value?.order.status))

// 处方是否过期（急诊处方 7 日有效）
const expired = computed(() => {
  const until = data.value?.order?.prescriptionValidUntil
  if (!until) return false
  return new Date(until + 'T23:59:59') < new Date(new Date().toDateString())
})

const doctorDialog = ref(false)
const doctor = reactive({ doctorName: '', phone: '', note: '' })
const payDialog = ref(false)
const pay = reactive({ method: '医保电子凭证', invoiceTitle: '', invoiceTaxNo: '' })
const subDialog = ref(false)
const sub = reactive({ itemId: null, originName: '', replacementDrugId: null, quantity: 1, note: '' })
const deliverDialog = ref(false)
const deliver = reactive({ coldPhotoPath: '', temperature: 4.5, signedBy: '', signRelation: '本人', medicationReminder: '' })
const addressDialog = ref(false)
const addr = reactive({ address: '', contactPhone: '', recipient: '', note: '' })

async function load() {
  try {
    const res = await api.get('/api/orders/' + route.params.id)
    data.value = res.data
  } catch (e) {
    if (e.response?.status === 403 || e.response?.status === 404) {
      setTimeout(() => location.hash = '#/orders', 600)
      return
    }
    throw e
  }
  Object.assign(addr, {
    address: data.value.order.address || '', contactPhone: data.value.order.contactPhone || '',
    recipient: data.value.order.recipient || '', note: ''
  })
}

async function post(action, body = {}) {
  const res = await api.post(`/api/orders/${route.params.id}${action}`, body)
  await load()
  return res
}

// 审方
async function doReview() {
  const { value: opinion } = await ElMessageBox.prompt('补充审方意见（可留空）：', '审方通过',
    { inputType: 'textarea', confirmButtonText: '签名通过', cancelButtonText: '取消' }).catch(() => ({}))
  if (opinion === undefined) return
  const res = await post('/review', { opinion: opinion || '', riskAcknowledged: false })
  ElMessage.success(res.data.order.status === 'DOCTOR_VERIFY'
    ? '已转急诊医生电话核实' : '审方通过，已自动进行医保核验')
}
async function doReturn() {
  const { value: reason } = await ElMessageBox.prompt('退回原因（字迹/图片不清之处）：', '退回补充',
    { inputType: 'textarea', confirmButtonText: '退回患者' }).catch(() => ({}))
  if (valueMissing(reason)) return
  await post('/return', { reason })
  ElMessage.success('已退回患者补充，单据暂停')
}
async function doContact() {
  const { value: note } = await ElMessageBox.prompt('与患者/家属确认关键药品的沟通记录：', '联系患者',
    { inputType: 'textarea' }).catch(() => ({}))
  if (valueMissing(note)) return
  await post('/contact', { note })
  ElMessage.success('沟通已记录')
}
async function submitDoctor() {
  if (!doctor.doctorName || !doctor.phone) return ElMessage.warning('请填写医生姓名与电话')
  await post('/doctor-verify', { ...doctor })
  doctorDialog.value = false
  ElMessage.success('医生电话核实已记录，可继续审方')
}

// 医保/支付
async function retryInsurance() {
  await post('/insurance/retry')
  ElMessage.success('已重新核验')
}
async function rollback() {
  await post('/insurance/rollback', { reason: '医保接口异常/拒付，经患者同意转全自费' })
  ElMessage.warning('已医保回退为全自费')
}
async function doPay() {
  await post('/pay', { ...pay })
  payDialog.value = false
  ElMessage.success('收款成功，转仓管出库')
}

// 出库/替代
function openSub(row) {
  Object.assign(sub, { itemId: row.id, originName: row.drug.name, replacementDrugId: null,
    quantity: row.quantity, note: '' })
  subDialog.value = true
}
async function doSubstitute() {
  if (!sub.replacementDrugId) return ElMessage.warning('请选择替代药')
  await post('/substitute', { ...sub })
  subDialog.value = false
  ElMessage.success('已替代配发并重新拆分费用')
}
async function removeItem(row) {
  await ElMessageBox.confirm(`确认将《${row.drug.name}》从本单剔除（缺药无替代）？`, '移除药品',
    { type: 'warning' })
  await api.post(`/api/orders/${route.params.id}/items/${row.id}/remove`, { reason: '缺药且无替代，患者同意' })
  await load()
}
async function outbound() { await post('/outbound'); ElMessage.success('已出库，批号库存已冻结扣减') }
async function cold(capable) {
  await post('/cold-confirm', { capable, note: capable ? '冷链箱+冰排已配备' : '当班无可用冷链设备' })
  ElMessage[capable ? 'success' : 'warning'](capable ? '保冷能力已确认' : '已暂停，等待改方案')
}

// 签收
async function uploadCold(opt) {
  const fd = new FormData(); fd.append('file', opt.file)
  const res = await api.post('/api/files/upload', fd)
  deliver.coldPhotoPath = res.path
  ElMessage.success('温控照片已上传')
}
async function doDeliver() {
  if (!deliver.signedBy) return ElMessage.warning('请填写签收人')
  await post('/deliver', { ...deliver })
  deliverDialog.value = false
  ElMessage.success('签收完成，全要素进入档案')
}

// 地址
async function doAddress() {
  await post('/address', { ...addr })
  addressDialog.value = false
  ElMessage.success('配送信息已变更并同步全员')
}

// 通用决策
async function pause() {
  const { value: reason } = await ElMessageBox.prompt('为什么暂停配药？', '暂停',
    { inputType: 'textarea', confirmButtonText: '确认暂停', type: 'warning' }).catch(() => ({}))
  if (valueMissing(reason)) return
  await post('/pause', { reason })
}
async function resume() {
  const { value: reason } = await ElMessageBox.prompt('恢复配药的原因：', '恢复',
    { inputType: 'textarea', inputValue: '问题已解决，患者仍需用药' }).catch(() => ({}))
  if (valueMissing(reason)) return
  await post('/resume', { reason })
}
async function offline() {
  const { value: reason } = await ElMessageBox.prompt('为什么改为线下复诊？', '转线下',
    { inputType: 'textarea', confirmButtonText: '确认转线下', type: 'info' }).catch(() => ({}))
  if (valueMissing(reason)) return
  await post('/offline', { reason })
}

// 退回补充后患者重传
async function resubmitUpload(opt) {
  const fd = new FormData(); fd.append('file', opt.file)
  const res = await api.post('/api/files/upload', fd)
  await post('/resubmit', { prescriptionImagePath: res.path, supplement: '已按药师要求重新上传清晰处方照片' })
  ElMessage.success('补充材料已提交，重新进入审方')
}

// 投诉
async function fileComplaint(category) {
  const { value: content } = await ElMessageBox.prompt(COMPLAINT_NAMES[category] + ' 详情：', '次日反馈',
    { inputType: 'textarea' }).catch(() => ({}))
  if (valueMissing(content)) return
  await post('/complaints', { category, content })
  ElMessage.success('已立案，客服可在同一档案追溯审方→出库→支付→签收')
}

// 展示工具
const fmt = t => t ? t.replace('T', ' ').slice(0, 16) : ''
const num = v => Number(v || 0).toFixed(2)
const valueMissing = v => v === undefined || v === null || v.trim() === ''
const fulfillName = s => ({ PENDING: '待审', CONFIRMED: '按方配发', SUBSTITUTED: '替代配发', REMOVED: '已移除' }[s])
const fulfillType = s => ({ PENDING: 'info', CONFIRMED: 'success', SUBSTITUTED: 'warning', REMOVED: 'danger' }[s])
const insType = s => ({ APPROVED: 'success', REJECTED: 'danger', ERROR: 'danger', ROLLED_BACK: 'warning' }[s] || 'info')
const stageName = s => ({ REVIEW: '审方', OUTBOUND: '出库批号', PAYMENT: '医保支付', DELIVERY: '配送签收' }[s] || s)
const timelineType = d => ({ CONTINUE: 'success', PAUSE: 'danger', OFFLINE: 'info' }[d] || 'primary')
const EVENT_NAMES = {
  SUBMIT: '提交/补充', EXPIRED: '处方过期拦截', REVIEW: '药师审方', RETURN: '退回补充', CONTACT: '联系患者',
  DOCTOR_VERIFY: '医生电话核实', INSURANCE: '医保核验', ROLLBACK: '医保回退',
  PAY: '收银支付', SUBSTITUTE: '缺药替代/移除', ADDRESS_CHANGE: '改配送信息',
  COLD_CHAIN: '冷链处置', ISSUE: '出库', DELIVER: '签收归档', HANDOVER: '值班交接',
  COMPLAINT: '投诉反馈', PAUSE: '暂停', RESUME: '恢复', OFFLINE: '转线下'
}
const eventTypeName = t => EVENT_NAMES[t] || t

onMounted(async () => {
  await load()
  drugs.value = await api.get('/api/drugs')
})
</script>

<style scoped>
.head-card { margin-bottom: 12px; }
.head { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
.title-line { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.title-line .no { font-size: 18px; font-weight: 700; color: #1d2b53; margin-right: 6px; }
.meta { color: #6b7689; font-size: 13px; margin-top: 6px; }
.block { margin-bottom: 12px; }
.card-h { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.hint { color: #909399; font-size: 12px; }
.white-pre { white-space: pre-wrap; }
.rx { margin-top: 10px; display: flex; align-items: center; gap: 10px; }
.opinion { background: #f5f8ff; border-left: 3px solid #3a5bbf; padding: 8px 10px; font-size: 13px; border-radius: 4px; }
.actions { margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap; }
.ins-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.fees { margin-top: 12px; }
.fee { background: #f6f8fc; border-radius: 8px; padding: 8px 10px; text-align: center; }
.fee label { display: block; color: #8a93a5; font-size: 12px; }
.fee b { font-size: 16px; color: #1d2b53; }
.fee .green { color: #16a34a; }
.fee.total { background: #fff5e6; }
.snapshot { margin-top: 10px; background: #f6f8fc; padding: 8px 10px; border-radius: 6px; font-size: 13px; }
.decision { padding: 8px 10px; border-radius: 6px; margin-bottom: 6px; }
.decision-CONTINUE { background: #ecfdf3; color: #15803d; }
.decision-PAUSE { background: #fef2f2; color: #b91c1c; }
.decision-OFFLINE { background: #f1f3f7; color: #556; }
.reason { font-size: 13px; margin-top: 4px; color: #3c4356; }
</style>
