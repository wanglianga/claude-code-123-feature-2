<template>
  <div>
    <!-- 履行观察期横幅 -->
    <el-alert v-if="observation" class="mb" :closable="false" show-icon :type="observation.expired ? 'error' : 'warning'"
              :title="observation.title" :description="observation.desc" />

    <el-card shadow="never" class="mb">
      <template #header>
        <div class="head">
          <b>履行付款计划</b>
          <div>
            <el-button v-if="canBuild && !data.plans.length" type="primary" size="small" @click="buildVisible = true">
              依据协议建立付款计划
            </el-button>
            <el-button v-if="canExec && data.plans.length" size="small" @click="scanOverdue">立即扫描逾期</el-button>
          </div>
        </div>
      </template>
      <el-table :data="data.plans" stripe>
        <el-table-column label="期次" width="70"><template #default="{ row }">第{{ row.installmentNo }}期</template></el-table-column>
        <el-table-column prop="dueAmount" label="应付金额(元)" width="120" align="right" />
        <el-table-column prop="dueDate" label="应付日期" width="110" />
        <el-table-column prop="paidAmount" label="已付(元)" width="100" align="right" />
        <el-table-column prop="paidDate" label="到账日期" width="110" />
        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <el-tag size="small" :type="PLAN_STATUS[row.status].tag">{{ PLAN_STATUS[row.status].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="凭证/备注" min-width="170">
          <template #default="{ row }">
            <el-tag v-if="row.voucherId" size="small" type="success" class="mr">已关联付款凭证</el-tag>
            <span>{{ row.note }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canExec && row.status !== 'PAID'" size="small" type="primary" link @click="openPay(row)">确认收款</el-button>
            <el-button v-if="canSend" size="small" link @click="openRemind(row)">提醒</el-button>
          </template>
        </el-table-column>
        <template #empty>协议生效后建立履行节点；分期协议按每期生成，平台按期提醒付款、上传凭证与确认收款</template>
      </el-table>
    </el-card>

    <!-- 按期提醒 -->
    <el-card shadow="never" class="mb">
      <template #header>
        <div class="head">
          <b>按期履行提醒（付款 / 上传凭证 / 确认收款）</b>
          <el-tag size="small" type="info">到期前3天自动发送，逾期自动发送逾期告知</el-tag>
        </div>
      </template>
      <el-table :data="data.reminders" stripe size="small">
        <el-table-column label="期次" width="64"><template #default="{ row }">第{{ row.installmentNo || '—' }}期</template></el-table-column>
        <el-table-column label="提醒环节" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="REMIND_STAGE_TAG[row.stageType]">{{ REMIND_STAGES[row.stageType] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="对象" width="150">
          <template #default="{ row }">{{ REMIND_TARGET[row.targetParty] }}</template>
        </el-table-column>
        <el-table-column prop="plannedDate" label="计划日期" width="105" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'SENT' ? 'success' : 'info'">
              {{ row.status === 'SENT' ? '已发送' : '待发送' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="提醒内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="canSend && row.status === 'PLANNED'" size="small" link type="primary"
                       @click="sendPlannedReminder(row)">立即发送</el-button>
          </template>
        </el-table-column>
        <template #empty>建立付款计划后，平台自动按每一期排定提醒</template>
      </el-table>
    </el-card>

    <!-- 付款/收款凭证 -->
    <el-card shadow="never" class="mb">
      <template #header>
        <div class="head">
          <b>付款 / 收款凭证</b>
          <el-button v-if="canUpload" type="primary" size="small" @click="openVoucher()">上传凭证</el-button>
        </div>
      </template>
      <el-table :data="data.vouchers" stripe size="small">
        <el-table-column label="期次" width="64"><template #default="{ row }">第{{ row.installmentNo || '—' }}期</template></el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ row.partySide === 'APPLICANT' ? '收款凭证（申请人）' : '付款凭证（被申请人）' }}</template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件" min-width="200" show-overflow-tooltip />
        <el-table-column prop="amount" label="凭证金额(元)" width="110" align="right" />
        <el-table-column prop="paidDate" label="凭证日期" width="105" />
        <el-table-column prop="uploaderName" label="上传人" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag size="small" :type="VOUCHER_STATUS[row.status].tag">{{ VOUCHER_STATUS[row.status].label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="downloadVoucher(row)">查看/下载</el-button>
            <el-button v-if="canExec && row.status === 'PENDING'" size="small" link type="danger" @click="rejectVoucher(row)">不予采信</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无凭证；付款后由被申请人上传转账凭证，申请人/执行联络核实后确认收款</template>
      </el-table>
    </el-card>

    <!-- 逾期回访与履行观察 -->
    <el-card shadow="never" class="mb">
      <template #header>
        <div class="head">
          <b>逾期回访与履行观察</b>
          <el-button v-if="canMediate && hasOverduePlan" type="warning" size="small" @click="openVisit()">发起逾期回访</el-button>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon class="mb"
                title="某一期逾期后，调解员发起回访并进入履行观察期；当事人可提交未付款说明或收款异议，观察期随反馈重新计算；调解员据此决定是否继续观察，法官助理同步研判是否进入司法确认或后续执行线索。" />
      <el-timeline v-if="data.overdueVisits?.length">
        <el-timeline-item v-for="g in data.overdueVisits" :key="g.visit.id" :timestamp="`回访日期 ${g.visit.visitDate}`"
                          :type="g.visit.status === 'OBSERVING' ? 'warning' : 'success'" placement="top">          <el-card shadow="hover" class="visit-card">
            <div class="visit-head">
              <span>第{{ g.visit.installmentNo }}期逾期回访 · {{ VISIT_WAYS[g.visit.way] }} · {{ g.visit.mediatorName }}</span>
              <el-tag size="small" :type="VISIT_STATUS[g.visit.status].tag">{{ VISIT_STATUS[g.visit.status].label }}</el-tag>
              <el-tag size="small" :type="VISIT_DECISION[g.visit.decision || 'NONE'].tag">
                {{ VISIT_DECISION[g.visit.decision || 'NONE'].label }}
              </el-tag>
            </div>
            <div class="visit-line">回访情况：{{ g.visit.content || '—' }}</div>
            <div class="visit-line">
              履行观察期：至 <b>{{ g.visit.observeUntil }}</b>（{{ g.visit.observeDays }}天/期，已随反馈重算 {{ g.visit.recomputeCount }} 次）
              <el-tag v-if="isVisitOverdue(g.visit)" size="small" type="danger">已届满</el-tag>
            </div>

            <!-- 双方反馈 -->
            <div v-if="g.feedbacks?.length" class="fb-list">
              <div v-for="f in g.feedbacks" :key="f.id" class="fb-item">
                <el-tag size="small" :type="FEEDBACK_TYPES[f.type].tag">{{ FEEDBACK_TYPES[f.type].label }}</el-tag>
                <span class="fb-user">{{ FEEDBACK_TYPES[f.type].side }} · {{ f.userName }}</span>
                <span class="fb-time">{{ fmt(f.createdAt) }}</span>
                <div class="fb-content">{{ f.content }}</div>
                <el-button v-if="f.fileName" size="small" link type="primary" @click="downloadFeedback(f)">附件：{{ f.fileName }}</el-button>
              </div>
            </div>

            <!-- 法官助理研判信息 -->
            <div v-if="g.visit.decision && g.visit.decision !== 'NONE'" class="decision-box">
              <div>研判人：{{ g.visit.assistantName }}（{{ fmt(g.visit.decidedAt) }}）</div>
              <div v-if="g.visit.executionClue">执行线索：{{ g.visit.executionClue }}</div>
              <div v-if="g.visit.decisionNote">说明：{{ g.visit.decisionNote }}</div>
            </div>

            <!-- 操作区 -->
            <div class="visit-actions">
              <template v-if="g.visit.status !== 'CLOSED'">
                <el-button v-if="canMediate" size="small" @click="openFeedback(g.visit, 'NON_PAYMENT')">登记未付款说明</el-button>
                <el-button v-if="canFeedbackObjection(g.visit)" size="small" type="danger" plain
                           @click="openFeedback(g.visit, 'RECEIPT_OBJECTION')">登记/提出收款异议</el-button>
                <el-button v-if="canMediate" size="small" type="primary" plain @click="openHandle(g.visit)">
                  根据反馈决定是否继续观察
                </el-button>
                <el-button v-if="canAssistant && (g.visit.decision === 'NONE' || g.visit.decision === 'CONTINUE_WATCH')"
                           size="small" type="warning" @click="openDecision(g.visit)">法官助理研判</el-button>
              </template>
              <el-tag v-else size="small" type="info">本次观察已结束</el-tag>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无逾期回访；某期逾期后可由调解员发起" :image-size="60" />
    </el-card>

    <!-- 常规履行回访 -->
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>履行回访记录</b>
          <el-button v-if="canExec" type="primary" size="small" @click="followVisible = true">新增回访</el-button>
        </div>
      </template>
      <el-table :data="data.followUps" stripe size="small">
        <el-table-column prop="followUpDate" label="回访日期" width="110" />
        <el-table-column label="方式" width="80">
          <template #default="{ row }">{{ { PHONE: '电话', ONLINE: '线上', ONSITE: '上门' }[row.way] }}</template>
        </el-table-column>
        <el-table-column label="结果" width="105">
          <template #default="{ row }">
            <el-tag size="small" :type="followTag(row.result)">{{ FOLLOW_RESULTS[row.result] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="回访人" width="100" />
        <el-table-column prop="note" label="情况记录" min-width="220" />
        <template #empty>暂无回访记录</template>
      </el-table>
    </el-card>

    <!-- 建立付款计划 -->
    <el-dialog v-model="buildVisible" title="建立履行付款计划" width="640px">
      <el-alert v-if="a && !a.installment" type="info" :closable="false" show-icon class="mb"
                title="一次性履行协议，将自动按协议金额与履行期限生成单个节点" />
      <template v-if="a && a.installment">
        <div v-for="(it, i) in installments" :key="i" class="ins-row">
          <span>第{{ i + 1 }}期</span>
          <el-input-number v-model="it.dueAmount" :min="0" :precision="2" :controls="false" placeholder="金额" style="width:150px" />
          <el-date-picker v-model="it.dueDate" value-format="YYYY-MM-DD" placeholder="到期日" style="width:170px" />
          <el-button link type="danger" @click="installments.splice(i, 1)">删除</el-button>
        </div>
        <el-button size="small" @click="installments.push({ dueAmount: 0, dueDate: '' })">+ 添加一期</el-button>
      </template>
      <template #footer>
        <el-button @click="buildVisible = false">取消</el-button>
        <el-button type="primary" @click="buildPlans">生成</el-button>
      </template>
    </el-dialog>

    <!-- 确认收款 -->
    <el-dialog v-model="payVisible" title="确认履行到账（支持部分履行）" width="480px">
      <el-form :model="pay" label-width="120px">
        <el-form-item label="本期应收">¥{{ pay.dueAmount }}</el-form-item>
        <el-form-item label="本次到账(元)">
          <el-input-number v-model="pay.paidAmount" :min="0" :precision="2" :controls="false" style="width:200px" />
        </el-form-item>
        <el-form-item label="到账日期"><el-date-picker v-model="pay.paidDate" value-format="YYYY-MM-DD" style="width:200px" /></el-form-item>
        <el-form-item v-if="pay.candidateVouchers.length" label="依据付款凭证">
          <el-select v-model="pay.voucherId" clearable placeholder="选择当事人上传的付款凭证（可不选）" style="width:300px">
            <el-option v-for="v in pay.candidateVouchers" :key="v.id"
                       :label="`${v.fileName}（¥${v.amount || '—'}，${v.status === 'CONFIRMED' ? '已确认过' : '待核实'}）`" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="pay.note" placeholder="银行流水号等" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPay">确认收款</el-button>
      </template>
    </el-dialog>

    <!-- 发送提醒 -->
    <el-dialog v-model="remindVisible" title="发送履行提醒" width="500px">
      <el-form :model="remind" label-width="100px">
        <el-form-item label="关联期次">
          <el-select v-model="remind.planId" clearable placeholder="不关联具体期次" style="width:320px">
            <el-option v-for="p in data.plans" :key="p.id"
                       :label="`第${p.installmentNo}期 ¥${p.dueAmount}（${p.dueDate}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="提醒环节">
          <el-select v-model="remind.stageType" style="width:320px">
            <el-option v-for="(l, k) in REMIND_STAGES" :key="k" :label="l" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="提醒对象">
          <el-radio-group v-model="remind.targetParty">
            <el-radio value="RESPONDENT">被申请人（付款义务人）</el-radio>
            <el-radio value="APPLICANT">申请人（收款人）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="方式">
          <el-radio-group v-model="remind.channel">
            <el-radio-button value="SMS">短信</el-radio-button>
            <el-radio-button value="ONLINE">站内</el-radio-button>
            <el-radio-button value="EMAIL">邮件</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="remindVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRemind">立即发送</el-button>
      </template>
    </el-dialog>

    <!-- 上传凭证 -->
    <el-dialog v-model="voucherVisible" title="上传付款 / 收款凭证" width="520px">
      <el-form :model="voucher" label-width="110px">
        <el-form-item label="凭证类型">
          <el-radio-group v-model="voucher.partySide" :disabled="auth.is('PARTY')">
            <el-radio value="RESPONDENT">付款凭证（被申请人已转账）</el-radio>
            <el-radio value="APPLICANT">收款凭证（申请人出具）</el-radio>
          </el-radio-group>
          <div v-if="auth.is('PARTY')" class="tip">当事人账号为申请人侧，将作为收款凭证/收款异议上传</div>
        </el-form-item>
        <el-form-item label="关联期次">
          <el-select v-model="voucher.planId" clearable style="width:320px">
            <el-option v-for="p in data.plans" :key="p.id"
                       :label="`第${p.installmentNo}期 ¥${p.dueAmount}（${p.dueDate}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="凭证金额(元)">
          <el-input-number v-model="voucher.amount" :min="0" :precision="2" :controls="false" style="width:200px" />
        </el-form-item>
        <el-form-item label="付款日期"><el-date-picker v-model="voucher.paidDate" value-format="YYYY-MM-DD" style="width:200px" /></el-form-item>
        <el-form-item label="凭证文件">
          <input ref="fileInputRef" type="file" accept="image/*,application/pdf" @change="onPickFile" />
          <div v-if="voucher.fileName" class="tip">已选择：{{ voucher.fileName }}（{{ voucherFileSize }}）</div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="voucher.remark" type="textarea" :rows="2" placeholder="付款账户、流水号等" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="voucherVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitVoucher">上传</el-button>
      </template>
    </el-dialog>

    <!-- 发起逾期回访 -->
    <el-dialog v-model="visitVisible" title="发起逾期回访（进入履行观察期）" width="520px">
      <el-form :model="visit" label-width="100px">
        <el-form-item label="回访日期"><el-date-picker v-model="visit.visitDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="方式">
          <el-radio-group v-model="visit.way">
            <el-radio-button value="PHONE">电话</el-radio-button>
            <el-radio-button value="ONLINE">线上</el-radio-button>
            <el-radio-button value="ONSITE">上门</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="观察期(天)">
          <el-input-number v-model="visit.observeDays" :min="1" :max="90" :controls="false" style="width:160px" />
          <span class="tip">收到当事人反馈后将按该天数重新计算</span>
        </el-form-item>
        <el-form-item label="回访情况"><el-input v-model="visit.content" type="textarea" :rows="4"
                    placeholder="联系人、付款意愿、未付原因、还款承诺；发现的财产/住所/工作单位等执行线索" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visitVisible = false">取消</el-button>
        <el-button type="warning" @click="submitVisit">发起回访并进入观察</el-button>
      </template>
    </el-dialog>

    <!-- 当事人反馈 -->
    <el-dialog v-model="feedbackVisible"
               :title="feedback.type === 'NON_PAYMENT' ? '登记未付款说明' : '登记/提出收款异议'" width="520px">
      <el-alert type="warning" :closable="false" show-icon class="mb"
                :title="feedback.type === 'NON_PAYMENT'
                  ? '回访被申请人后由调解员代为登记；提交后履行观察期重新计算。'
                  : '收款申请人对到账金额/时间有异议时提出；提交后履行观察期重新计算。'" />
      <el-input v-model="feedback.content" type="textarea" :rows="4"
                :placeholder="feedback.type === 'NON_PAYMENT' ? '未付款原因、筹款计划、预计付款时间…' : '异议事项：未收到/金额不符/时间不符等…'" />
      <div class="mt">
        <input ref="fbFileRef" type="file" accept="image/*,application/pdf" @change="onPickFeedbackFile" />
        <div v-if="feedback.fileName" class="tip">附件：{{ feedback.fileName }}</div>
      </div>
      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitFeedback">提交反馈</el-button>
      </template>
    </el-dialog>

    <!-- 调解员决定是否继续观察 -->
    <el-dialog v-model="handleVisible" title="根据双方反馈决定履行观察" width="500px">
      <el-form label-width="110px">
        <el-form-item label="处理决定">
          <el-radio-group v-model="handle.continueWatching">
            <el-radio :value="true">继续履行观察</el-radio>
            <el-radio :value="false">结束本次观察</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="handle.continueWatching" label="重设观察期(天)">
          <el-input-number v-model="handle.observeDays" :min="1" :max="90" :controls="false" style="width:160px" />
        </el-form-item>
        <el-form-item label="处理意见"><el-input v-model="handle.note" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHandle">确认</el-button>
      </template>
    </el-dialog>

    <!-- 法官助理研判 -->
    <el-dialog v-model="decisionVisible" title="法官助理逾期研判" width="540px">
      <el-alert type="error" :closable="false" show-icon class="mb"
                title="根据逾期回访与双方反馈，判断是否进入司法确认或移交后续执行线索；法官助理可同步查看全部履行与反馈记录。" />
      <el-form label-width="110px">
        <el-form-item label="研判结论">
          <el-radio-group v-model="decision.decision">
            <el-radio value="CONTINUE_WATCH">继续履行观察</el-radio>
            <el-radio value="JUDICIAL_CONFIRM">进入司法确认</el-radio>
            <el-radio value="EXECUTION_CLUE">移交后续执行线索</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="decision.decision === 'EXECUTION_CLUE'" label="执行线索">
          <el-input v-model="decision.executionClue" type="textarea" :rows="3"
                    placeholder="财产（车辆/存款/应收账款）、住所、工作单位、收入、联系方式等" />
        </el-form-item>
        <el-form-item label="研判说明"><el-input v-model="decision.note" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="decisionVisible = false">取消</el-button>
        <el-button type="warning" @click="submitDecision">提交研判</el-button>
      </template>
    </el-dialog>

    <!-- 常规回访 -->
    <el-dialog v-model="followVisible" title="新增履行回访" width="500px">
      <el-form :model="follow" label-width="90px">
        <el-form-item label="回访日期"><el-date-picker v-model="follow.followUpDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="方式">
          <el-radio-group v-model="follow.way">
            <el-radio-button value="PHONE">电话</el-radio-button>
            <el-radio-button value="ONLINE">线上</el-radio-button>
            <el-radio-button value="ONSITE">上门</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="回访结果">
          <el-select v-model="follow.result" style="width:100%">
            <el-option v-for="(l, k) in FOLLOW_RESULTS" :key="k" :label="l" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="情况记录"><el-input v-model="follow.note" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="followVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFollow">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../../api.js'
import { auth } from '../../store.js'
import {
  PLAN_STATUS, FOLLOW_RESULTS, REMIND_STAGES, REMIND_STAGE_TAG, REMIND_TARGET,
  VOUCHER_STATUS, VISIT_STATUS, VISIT_DECISION, FEEDBACK_TYPES, VISIT_WAYS
} from '../../constants.js'

const props = defineProps({ data: Object })
const emit = defineEmits(['changed'])
const c = computed(() => props.data.case)
const a = computed(() => props.data.agreement)

const canExec = computed(() => auth.is('EXECUTION', 'MEDIATOR', 'WINDOW', 'ADMIN'))
const canMediate = computed(() => auth.is('MEDIATOR', 'EXECUTION', 'WINDOW', 'ADMIN'))
const canAssistant = computed(() => auth.is('ASSISTANT', 'ADMIN'))
const canSend = computed(() => canExec.value)
const canBuild = computed(() => canExec.value && a.value?.effectiveAt)
const canUpload = computed(() => (props.data.plans || []).length > 0
  && auth.is('PARTY', 'MEDIATOR', 'EXECUTION', 'WINDOW', 'ADMIN'))
const hasOverduePlan = computed(() => (props.data.plans || []).some(p => p.status === 'OVERDUE' || p.status === 'PARTIAL'))

const today = new Date().toISOString().substring(0, 10)
const fmt = t => t ? t.replace('T', ' ').substring(0, 16) : ''
const isVisitOverdue = v => v.observeUntil && v.observeUntil < today

// 顶部观察期横幅
const observation = computed(() => {
  const groups = props.data.overdueVisits || []
  const active = groups.map(g => g.visit).find(v => v.status === 'OBSERVING')
    || groups.map(g => g.visit).find(v => v.status === 'EXPIRED' && isVisitOverdue(v))
  if (active) {
    return {
      expired: active.status !== 'OBSERVING',
      title: (active.status === 'OBSERVING' ? '履行观察期中（至 ' : '履行观察期已届满（') + active.observeUntil + '，已重算 ' + active.recomputeCount + ' 次）',
      desc: '第' + active.installmentNo + '期逾期回访后进入观察；当事人反馈（未付款说明/收款异议）会重新计算观察期，由调解员决定是否继续观察，法官助理同步研判。'
    }
  }
  if (c.value.observationActive && c.value.observeUntil) {
    return { expired: c.value.observeUntil < today, title: '履行观察期中（至 ' + c.value.observeUntil + '）', desc: '平台跟踪逾期款项履行，届满未付将提示研判司法确认或执行线索。' }
  }
  return null
})

// ---- 建立付款计划 ----
const buildVisible = ref(false)
const installments = ref([{ dueAmount: 0, dueDate: '' }, { dueAmount: 0, dueDate: '' }])
const buildPlans = async () => {
  await api.post(`/cases/${c.value.id}/plans`, { installments: installments.value })
  buildVisible.value = false
  ElMessage.success('履行计划已建立，平台将按期提醒付款、上传凭证与确认收款')
  emit('changed')
}

// ---- 确认收款 ----
const payVisible = ref(false)
const pay = reactive({ id: null, dueAmount: 0, paidAmount: 0, paidDate: '', note: '', voucherId: null, candidateVouchers: [] })
const openPay = row => {
  pay.id = row.id
  pay.dueAmount = Number(row.dueAmount) - Number(row.paidAmount || 0)
  pay.paidAmount = pay.dueAmount
  pay.paidDate = today
  pay.note = ''
  pay.voucherId = row.voucherId || null
  pay.candidateVouchers = (props.data.vouchers || [])
    .filter(v => (!v.planId || v.planId === row.id) && v.partySide === 'RESPONDENT' && v.status !== 'REJECTED')
  payVisible.value = true
}
const submitPay = async () => {
  await api.post(`/plans/${pay.id}/pay`, {
    paidAmount: pay.paidAmount, paidDate: pay.paidDate, note: pay.note,
    voucherId: pay.voucherId || undefined
  })
  payVisible.value = false
  ElMessage.success('收款已确认并留痕')
  emit('changed')
}

const scanOverdue = async () => {
  const r = await api.post('/overdue/scan')
  ElMessage.success(r.newlyOverdue > 0 ? `扫描完成，新发现 ${r.newlyOverdue} 个逾期节点，逾期告知已发送` : '扫描完成，暂无新增逾期')
  emit('changed')
}

// ---- 提醒 ----
const remindVisible = ref(false)
const remind = reactive({ planId: null, stageType: 'BEFORE_DUE', targetParty: 'RESPONDENT', channel: 'SMS' })
const openRemind = row => {
  remind.planId = row?.id || null
  remind.stageType = 'BEFORE_DUE'
  remind.targetParty = 'RESPONDENT'
  remind.channel = 'SMS'
  remindVisible.value = true
}
const submitRemind = async () => {
  await api.post(`/cases/${c.value.id}/reminders/send`, { ...remind })
  remindVisible.value = false
  ElMessage.success('提醒已发送并写入时间线')
  emit('changed')
}
const sendPlannedReminder = async row => {
  await api.post(`/cases/${c.value.id}/reminders/send`, {
    planId: row.planId, stageType: row.stageType, targetParty: row.targetParty, channel: row.channel
  })
  ElMessage.success(`${REMIND_STAGES[row.stageType]}已发送`)
  emit('changed')
}

// ---- 凭证 ----
const fileInputRef = ref(null)
const voucherVisible = ref(false)
const uploading = ref(false)
const voucher = reactive({ planId: null, partySide: 'RESPONDENT', amount: null, paidDate: '', remark: '', fileName: '', fileType: '', fileData: '' })
const voucherFileSize = ref('')
const openVoucher = () => {
  voucher.planId = null
  voucher.partySide = auth.is('PARTY') ? 'APPLICANT' : 'RESPONDENT'
  voucher.amount = null
  voucher.paidDate = today
  voucher.remark = ''
  voucher.fileName = ''
  voucher.fileType = ''
  voucher.fileData = ''
  voucherFileSize.value = ''
  voucherVisible.value = true
}
const onPickFile = e => {
  const file = e.target.files[0]
  if (!file) return
  if (file.size > 10 * 1024 * 1024) { ElMessage.error('凭证文件不能超过 10MB'); return }
  voucher.fileName = file.name
  voucher.fileType = file.type || 'application/octet-stream'
  voucherFileSize.value = (file.size / 1024).toFixed(1) + ' KB'
  const reader = new FileReader()
  reader.onload = () => { voucher.fileData = reader.result }
  reader.readAsDataURL(file)
}
const submitVoucher = async () => {
  if (!voucher.fileName) { ElMessage.warning('请选择凭证文件'); return }
  uploading.value = true
  try {
    await api.post(`/cases/${c.value.id}/vouchers`, { ...voucher })
    voucherVisible.value = false
    ElMessage.success('凭证已上传，待核实后确认收款')
    emit('changed')
  } finally { uploading.value = false }
}
const downloadVoucher = row => downloadFile(`/vouchers/${row.id}/file`, row.fileName)
const rejectVoucher = async row => {
  const { value } = await ElMessageBox.prompt('请填写不予采信原因（如截图不清、金额不符）', '凭证不予采信', {
    confirmButtonText: '确认驳回', cancelButtonText: '取消', inputType: 'textarea'
  })
  await api.post(`/vouchers/${row.id}/reject`, { note: value })
  ElMessage.success('已驳回并通知当事人')
  emit('changed')
}

// ---- 逾期回访 ----
const visitVisible = ref(false)
const visit = reactive({ visitDate: today, way: 'PHONE', observeDays: 15, content: '' })
const openVisit = () => {
  visit.visitDate = today
  visit.way = 'PHONE'
  visit.observeDays = 15
  visit.content = ''
  visitVisible.value = true
}
const submitVisit = async () => {
  if (!visit.content.trim()) { ElMessage.warning('请填写回访情况'); return }
  await api.post(`/cases/${c.value.id}/overdue-visits`, { ...visit })
  visitVisible.value = false
  ElMessage.success('已发起逾期回访，案件进入履行观察期')
  emit('changed')
}

// ---- 当事人反馈 ----
const fbFileRef = ref(null)
const feedbackVisible = ref(false)
const feedback = reactive({ visitId: null, type: 'NON_PAYMENT', content: '', fileName: '', fileType: '', fileData: '' })
const canFeedbackObjection = v => v.status !== 'CLOSED' && auth.is('PARTY', 'MEDIATOR', 'EXECUTION', 'WINDOW', 'ASSISTANT', 'ADMIN')
const openFeedback = (v, type) => {
  feedback.visitId = v.id
  feedback.type = type
  feedback.content = ''
  feedback.fileName = ''
  feedback.fileType = ''
  feedback.fileData = ''
  feedbackVisible.value = true
}
const onPickFeedbackFile = e => {
  const file = e.target.files[0]
  if (!file) return
  if (file.size > 10 * 1024 * 1024) { ElMessage.error('附件不能超过 10MB'); return }
  feedback.fileName = file.name
  feedback.fileType = file.type || 'application/octet-stream'
  const reader = new FileReader()
  reader.onload = () => { feedback.fileData = reader.result }
  reader.readAsDataURL(file)
}
const submitFeedback = async () => {
  if (!feedback.content.trim()) { ElMessage.warning('请填写反馈内容'); return }
  uploading.value = true
  try {
    await api.post(`/overdue-visits/${feedback.visitId}/feedback`, { ...feedback })
    feedbackVisible.value = false
    ElMessage.success('反馈已提交，履行观察期已重新计算')
    emit('changed')
  } finally { uploading.value = false }
}
const downloadFeedback = f => downloadFile(`/feedback/${f.id}/file`, f.fileName)

// ---- 调解员处理反馈 ----
const handleVisible = ref(false)
const handle = reactive({ visitId: null, continueWatching: true, observeDays: 15, note: '' })
const openHandle = v => {
  handle.visitId = v.id
  handle.continueWatching = true
  handle.observeDays = v.observeDays || 15
  handle.note = ''
  handleVisible.value = true
}
const submitHandle = async () => {
  await api.post(`/overdue-visits/${handle.visitId}/handle`, { ...handle })
  handleVisible.value = false
  ElMessage.success(handle.continueWatching ? '已决定继续履行观察' : '已结束本次履行观察')
  emit('changed')
}

// ---- 法官助理研判 ----
const decisionVisible = ref(false)
const decision = reactive({ visitId: null, decision: 'CONTINUE_WATCH', executionClue: '', note: '' })
const openDecision = v => {
  decision.visitId = v.id
  decision.decision = 'CONTINUE_WATCH'
  decision.executionClue = ''
  decision.note = ''
  decisionVisible.value = true
}
const submitDecision = async () => {
  if (decision.decision === 'EXECUTION_CLUE' && !decision.executionClue.trim()) {
    ElMessage.warning('请填写执行线索'); return
  }
  await api.post(`/overdue-visits/${decision.visitId}/decision`, { ...decision })
  decisionVisible.value = false
  ElMessage.success('研判已提交并同步到案件时间线')
  emit('changed')
}

// ---- 常规回访 ----
const followVisible = ref(false)
const follow = reactive({ followUpDate: today, way: 'PHONE', result: 'NORMAL', note: '' })
const submitFollow = async () => {
  await api.post(`/cases/${c.value.id}/follow-ups`, follow)
  followVisible.value = false
  ElMessage.success('回访已记录')
  emit('changed')
}
const followTag = r => ({ NORMAL: 'success', PARTIAL: 'warning', OVERDUE: 'danger', DONE: 'success', OBJECTION: 'info' }[r])

// 鉴权下载
function downloadFile(url, filename) {
  fetch(`/api${url}`, { headers: { Authorization: `Bearer ${localStorage.getItem('token')}` } })
    .then(r => { if (!r.ok) throw new Error('下载失败'); return r.blob() })
    .then(blob => {
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = filename
      a.click()
    })
}
</script>

<style scoped>
.mb { margin-bottom: 14px; }
.mr { margin-right: 6px; }
.mt { margin-top: 10px; }
.head { display: flex; justify-content: space-between; align-items: center; }
.ins-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.tip { color: #909399; font-size: 12px; }
.visit-card :deep(.el-card__body) { padding: 12px 14px; }
.visit-head { display: flex; align-items: center; gap: 8px; font-weight: 600; margin-bottom: 6px; flex-wrap: wrap; }
.visit-line { color: #606266; font-size: 13px; margin: 4px 0; }
.fb-list { margin: 8px 0; border-left: 3px solid #e6a23c; padding-left: 10px; }
.fb-item { background: #fdf6ec; border-radius: 4px; padding: 8px 10px; margin-bottom: 6px; }
.fb-user { font-weight: 600; font-size: 13px; margin-left: 6px; }
.fb-time { color: #909399; font-size: 12px; margin-left: 8px; }
.fb-content { font-size: 13px; margin: 4px 0; white-space: pre-wrap; }
.decision-box { background: #f4f4f5; border-radius: 4px; padding: 8px 10px; font-size: 13px; color: #606266; margin: 6px 0; }
.visit-actions { margin-top: 8px; display: flex; gap: 8px; flex-wrap: wrap; }
</style>
