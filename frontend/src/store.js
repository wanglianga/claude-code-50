import { reactive } from 'vue'

export const ROLE_NAMES = {
  PATIENT: '患者/家属',
  PHARMACIST: '值班药师',
  CASHIER: '收银',
  WAREHOUSE: '仓管',
  RIDER: '配送员',
  CUSTOMER_SERVICE: '客服',
  ADMIN: '管理员'
}

export const STATUS_NAMES = {
  SUBMITTED: '待审方',
  PENDING_SUPPLEMENT: '退回待补充',
  PHARMACIST_REVIEW: '审方中',
  DOCTOR_VERIFY: '急诊医生电话核实中',
  INSURANCE_CHECK: '医保核验中',
  WAIT_PAYMENT: '待支付',
  PAID: '已支付',
  PICKING: '待自取签收',
  DELIVERING: '配送中',
  DELIVERED: '已送达',
  COMPLETED: '已完成归档',
  PAUSED: '已暂停',
  OFFLINE_REFERRAL: '转线下复诊',
  CANCELLED: '已取消'
}

export const STATUS_TYPE = {
  SUBMITTED: 'info',
  PENDING_SUPPLEMENT: 'warning',
  PHARMACIST_REVIEW: 'primary',
  DOCTOR_VERIFY: 'warning',
  INSURANCE_CHECK: 'primary',
  WAIT_PAYMENT: 'warning',
  PAID: 'success',
  PICKING: 'primary',
  DELIVERING: 'primary',
  DELIVERED: 'success',
  COMPLETED: 'success',
  PAUSED: 'danger',
  OFFLINE_REFERRAL: 'info',
  CANCELLED: 'info'
}

export const CONTROL_NAMES = {
  NORMAL: '普通', OTC: '非处方', RX: '处方药', CONTROLLED: '管制药品'
}

export const DECISION_NAMES = { CONTINUE: '继续配药', PAUSE: '暂停配药', OFFLINE: '线下复诊', INFO: '过程记录' }
export const DECISION_TYPE = { CONTINUE: 'success', PAUSE: 'danger', OFFLINE: 'info', INFO: '' }

export const INSURANCE_NAMES = {
  PENDING: '待核验', APPROVED: '核验通过', REJECTED: '核验不通过',
  ERROR: '接口异常', ROLLED_BACK: '已回退自费'
}

export const COMPLAINT_NAMES = {
  ADVERSE_REACTION: '疑似不良反应',
  MISSING_DRUG: '漏发药品',
  INSURANCE_DISPUTE: '医保扣费争议',
  OTHER: '其他'
}

export const auth = reactive({
  user: JSON.parse(localStorage.getItem('np_user') || 'null'),
  setUser(u) {
    this.user = u
    localStorage.setItem('np_user', JSON.stringify(u))
  },
  logout() {
    this.user = null
    localStorage.removeItem('np_token')
    localStorage.removeItem('np_user')
  }
})
