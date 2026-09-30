<template>
  <div>
    <el-row :gutter="12">
      <el-col :span="4" v-for="k in cards" :key="k.label">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-num" :style="{ color: k.color }">{{ k.value }}</div>
          <div class="kpi-label">{{ k.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="mt" shadow="never">
      <template #header>
        <div class="card-head">
          <span>{{ auth.user?.realName }}（{{ auth.user?.roleLabel }}）的待办事项</span>
          <el-button text type="primary" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
        </div>
      </template>
      <el-table :data="todos" stripe>
        <el-table-column prop="caseNo" label="调解编号" width="170" />
        <el-table-column prop="typeLabel" label="案由" width="170" />
        <el-table-column prop="applicantName" label="申请人" width="120" />
        <el-table-column prop="respondentName" label="被申请人" width="140" />
        <el-table-column label="当前状态" width="130">
          <template #default="{ row }">
            <el-tag size="small">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="待办">
          <template #default="{ row }">
            <el-tag :type="row.tag" size="small">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="$router.push(`/cases/${row.caseId}`)">去处理</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无待办，所有案件推进正常</template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '../api.js'
import { auth } from '../store.js'

const todos = ref([])
const kpi = ref({})

const cards = ref([])
const buildCards = k => {
  cards.value = [
    { label: '待分流案件', value: k.pendingTriage ?? 0, color: '#f56c6c' },
    { label: '调解中案件', value: k.mediating ?? 0, color: '#409eff' },
    { label: '履行逾期', value: k.overdue ?? 0, color: '#e6a23c' },
    { label: '履行观察中', value: k.observing ?? 0, color: '#b37feb' },
    { label: '待核实收款凭证', value: k.pendingVoucherCount ?? 0, color: '#36cfc9' },
    { label: '在办失联', value: k.missing ?? 0, color: '#909399' }
  ]
}

const load = async () => {
  const data = await api.get('/todos')
  todos.value = data.todos
  const d = await api.get('/dashboard')
  kpi.value = d.kpi
  buildCards(d.kpi)
}
onMounted(load)
</script>

<style scoped>
.kpi-card { text-align: center; }
.kpi-num { font-size: 30px; font-weight: 700; }
.kpi-label { color: #909399; font-size: 13px; margin-top: 6px; }
.mt { margin-top: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
</style>
