// 枚举标签（与后端 enums 保持一致）
export const CASE_TYPES = {
  LOAN: '民间借贷纠纷',
  PROPERTY_FEE: '物业费纠纷',
  TRAFFIC: '交通事故纠纷',
  NEIGHBOR: '邻里纠纷',
  SALES: '小额买卖合同纠纷'
}

export const CASE_STATUS = {
  PENDING_TRIAGE: '待分流',
  TRIAGED: '已分流待排期',
  SCHEDULED: '已排期待调解',
  MEDIATING: '调解中',
  MEDIATION_SUCCESS: '调解成功',
  MEDIATION_FAILED: '调解失败',
  JUDICIAL_CONFIRMING: '司法确认审查中',
  PERFORMING: '履行中',
  PERFORMED_DONE: '履行完毕',
  OVERDUE: '履行逾期',
  OBSERVING: '履行观察期',
  TRANSFERRED: '已转立案',
  CLOSED: '已归档'
}

export const STATUS_TAG = {
  PENDING_TRIAGE: 'danger',
  TRIAGED: 'warning',
  SCHEDULED: 'primary',
  MEDIATING: 'primary',
  MEDIATION_SUCCESS: 'success',
  MEDIATION_FAILED: 'info',
  JUDICIAL_CONFIRMING: 'warning',
  PERFORMING: '',
  PERFORMED_DONE: 'success',
  OVERDUE: 'danger',
  OBSERVING: 'warning',
  TRANSFERRED: 'info',
  CLOSED: 'info'
}

export const URGENCY = { NORMAL: '普通', URGENT: '加急', EMERGENCY: '特急' }
export const URGENCY_TAG = { NORMAL: 'info', URGENT: 'warning', EMERGENCY: 'danger' }
export const CHANNEL = { ONLINE: '线上调解', OFFLINE: '线下调解' }

export const RISK_LABELS = {
  RESPONDENT_MISSING: '被申请人失联',
  EVIDENCE_MISSING: '证据材料缺失',
  REPEAT_RESCHEDULE: '多次改期',
  INSTALLMENT: '分期履行',
  OVERDUE: '付款逾期/部分履行',
  OBSERVING: '履行观察期',
  TRANSFER_REQUESTED: '已申请转立案'
}

export const RISK_TAG = {
  RESPONDENT_MISSING: 'danger',
  EVIDENCE_MISSING: 'danger',
  REPEAT_RESCHEDULE: 'warning',
  INSTALLMENT: 'info',
  OVERDUE: 'danger',
  OBSERVING: 'warning',
  TRANSFER_REQUESTED: 'warning'
}

export const PLAN_STATUS = {
  PENDING: { label: '待履行', tag: 'info' },
  PAID: { label: '已履行', tag: 'success' },
  PARTIAL: { label: '部分履行', tag: 'warning' },
  OVERDUE: { label: '已逾期', tag: 'danger' }
}

export const TRANSFER_REASONS = {
  MISSING: '被申请人失联，调解无法进行',
  EVIDENCE: '证据材料争议较大，需诉讼查明',
  REJECT: '一方明确拒绝调解',
  OVERDUE: '协议达成后付款逾期/拒不履行',
  OTHER: '其他不适宜继续调解情形'
}

export const FOLLOW_RESULTS = {
  NORMAL: '履约正常', PARTIAL: '部分履行', OVERDUE: '仍处逾期', DONE: '已履行完毕', OBJECTION: '有异议'
}

// 分期履行跟踪：当事人反馈
export const FEEDBACK_TYPES = {
  PAYMENT_VOUCHER: '付款凭证',
  NONPAYMENT_EXPLANATION: '未付款说明',
  RECEIPT_OBJECTION: '收款异议'
}
export const FEEDBACK_TYPE_TAG = {
  PAYMENT_VOUCHER: 'success',
  NONPAYMENT_EXPLANATION: 'warning',
  RECEIPT_OBJECTION: 'danger'
}
export const FEEDBACK_STATUS = {
  SUBMITTED: { label: '待确认', tag: 'warning' },
  ACKNOWLEDGED: { label: '已采信/已确认收款', tag: 'success' },
  DISPUTED: { label: '异议待核', tag: 'danger' }
}
export const SIDE_LABELS = { APPLICANT: '申请人', RESPONDENT: '被申请人' }

// 逾期回访处置状态
export const VISIT_STATUS = {
  OPEN: { label: '待双方反馈/处理', tag: 'danger' },
  OBSERVING: { label: '履行观察中', tag: 'warning' },
  TO_JUDICIAL: { label: '已送司法确认', tag: 'primary' },
  TO_EXECUTION: { label: '已转执行线索', tag: 'warning' },
  CLOSED: { label: '已关闭', tag: 'info' }
}
export const VISIT_WAYS = { PHONE: '电话', ONLINE: '线上', ONSITE: '上门' }

// 法官助理判断处置
export const ASSISTANT_ROUTES = {
  CONTINUE: '维持跟踪/观察',
  JUDICIAL: '进入司法确认',
  EXECUTION: '移送后续执行线索',
  TRANSFER: '建议转立案'
}
export const CLUE_TYPES = {
  PROPERTY: '财产线索',
  INCOME: '收入线索',
  GUARANTOR: '担保人线索',
  CONTACT: '行踪/联系方式',
  OTHER: '其他线索'
}

export const hasRole = (user, ...roles) => roles.includes(user?.role)
