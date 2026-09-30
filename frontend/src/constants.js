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

// 按期提醒环节
export const REMIND_STAGES = {
  BEFORE_DUE: '按期付款提醒',
  UPLOAD_VOUCHER: '上传凭证提醒',
  CONFIRM_RECEIPT: '确认收款提醒',
  OVERDUE_NOTICE: '逾期告知'
}
export const REMIND_STAGE_TAG = {
  BEFORE_DUE: 'primary',
  UPLOAD_VOUCHER: 'info',
  CONFIRM_RECEIPT: 'success',
  OVERDUE_NOTICE: 'danger'
}
export const REMIND_TARGET = { APPLICANT: '申请人（收款人）', RESPONDENT: '被申请人（付款义务人）' }
export const REMIND_CHANNEL = { SMS: '短信', ONLINE: '站内', EMAIL: '邮件' }

// 凭证状态
export const VOUCHER_STATUS = {
  PENDING: { label: '待核实', tag: 'warning' },
  CONFIRMED: { label: '已确认收款', tag: 'success' },
  REJECTED: { label: '未采信', tag: 'info' }
}

// 逾期回访状态
export const VISIT_STATUS = {
  OBSERVING: { label: '履行观察中', tag: 'warning' },
  EXPIRED: { label: '观察期届满', tag: 'danger' },
  CLOSED: { label: '已结束观察', tag: 'success' }
}

// 法官助理对逾期回访的研判
export const VISIT_DECISION = {
  NONE: { label: '待法官助理研判', tag: 'danger' },
  CONTINUE_WATCH: { label: '继续履行观察', tag: 'primary' },
  JUDICIAL_CONFIRM: { label: '进入司法确认', tag: 'warning' },
  EXECUTION_CLUE: { label: '移交执行线索', tag: 'danger' }
}

// 回访后当事人反馈类型
export const FEEDBACK_TYPES = {
  NON_PAYMENT: { label: '未付款说明', tag: 'warning', side: '被申请人（付款义务人）' },
  RECEIPT_OBJECTION: { label: '收款异议', tag: 'danger', side: '申请人（收款人）' }
}

export const VISIT_WAYS = { PHONE: '电话', ONLINE: '线上', ONSITE: '上门' }

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

export const hasRole = (user, ...roles) => roles.includes(user?.role)
