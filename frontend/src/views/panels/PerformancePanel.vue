<template>
  <div>
    <!-- 履行观察期横幅 -->
    <el-alert v-if="c.status === 'OBSERVING'" class="mb" type="warning" :closable="false" show-icon
              :title="`履行观察期：观察至 ${c.observationUntil || '—'}（剩余 ${observeDaysLeft} 天）`">
      <template #default>
        调解员已根据双方反馈决定继续履行观察；期间当事人上传凭证、未付款说明或收款异议后，
        <b>履行观察期将自反馈之日重新计算</b>。届满仍未足额履行的，案件恢复逾期并移交法官助理判断司法确认/执行线索。
      </template>
    </el-alert>

    <!-- 履行付款计划 -->
    <el-card shadow="never" class="mb">
      <template #header>
        <div class="head">
          <b>履行付款计划与按期提醒</b>
          <div>
            <el-button v-if="canBuild" type="primary" size="small" @click="buildVisible = true">
              依据协议建立付款计划
            </el-button>
            <el-button v-if="canFeedback" size="small" type="success" @click="openFeedback()">
              <el-icon><Upload /></el-icon>上传付款凭证
            </el-button>
            <el-button v-if="canExec" size="small" @click="remindNow">立即发送按期提醒</el-button>
            <el-button v-if="canExec" size="small" @click="scanOverdue">立即扫描逾期</el-button>
            <el-button v-if="canExec" size="small" @click="scanObservation">观察期届满扫描</el-button>
          </div>
        </div>
      </template>
      <el-table :data="data.plans" stripe>
        <el-table-column label="期次" width="70"><template #default="{ row }">第{{ row.installmentNo }}期</template></el-table-column>
        <el-table-column prop="dueAmount" label="应付金额(元)" width="120" align="right" />
        <el-table-column prop="dueDate" label="应付日期" width="115" />
        <el-table-column prop="paidAmount" label="已付(元)" width="100" align="right" />
        <el-table-column prop="paidDate" label="到账日期" width="115" />
        <el-table-column label="提醒" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.reminded" size="small" type="info">已提醒</el-tag>
            <el-tag v-else size="small" type="warning">待提醒</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag size="small" :type="PLAN_STATUS[row.status].tag">{{ PLAN_STATUS[row.status].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="150" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button v-if="canExec && row.status !== 'PAID'" size="small" type="primary" link @click="openPay(row)">确认到账</el-button>
            <el-button v-if="canFeedback && row.status !== 'PAID'" size="small" type="success" link @click="openFeedback(row)">传凭证</el-button>
          </template>
        </el-table-column>
        <template #empty>协议生效后建立履行节点；分期协议按每期生成，平台按期提醒付款并提示上传凭证</template>
      </el-table>
    </el-card>

    <!-- 当事人反馈：凭证 / 未付款说明 / 收款异议 -->
    <el-card shadow="never" class="mb">
      <template #header>
        <div class="head">
          <b>付款凭证 · 未付款说明 · 收款异议</b>
          <el-button v-if="canFeedback" size="small" type="primary" @click="fbVisible = true">提交履行反馈</el-button>
        </div>
      </template>
      <el-table :data="data.feedbacks" stripe>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="FEEDBACK_TYPE_TAG[row.type]">{{ FEEDBACK_TYPES[row.type] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交方" width="130">
          <template #default="{ row }">
            {{ SIDE_LABELS[row.submitterSide] || '—' }}
            <div class="sub">{{ row.submitterName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="金额(元)" width="100" align="right">
          <template #default="{ row }">{{ row.amount ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="content" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="附件" width="120">
          <template #default="{ row }">
            <el-button v-if="row.attachmentName" link type="primary" size="small" @click="downloadAttachment(row)">
              <el-icon><Paperclip /></el-icon>{{ row.attachmentName }}
            </el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="(FEEDBACK_STATUS[row.status] || {tag:'info'}).tag">
              {{ (FEEDBACK_STATUS[row.status] || {label:row.status}).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="145">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button v-if="canConfirmReceipt(row)" size="small" type="success" link @click="openConfirmReceipt(row)">
              确认收款
            </el-button>
          </template>
        </el-table-column>
        <template #empty>暂无反馈；付款方可上传付款凭证，逾期回访后可提交未付款说明，收款方可提出收款异议</template>
      </el-table>
    </el-card>

    <!-- 逾期回访与履行观察 -->
    <el-card shadow="never" class="mb">
      <template #header>
        <div class="head">
          <b>逾期回访与履行观察</b>
          <el-button v-if="canMediator && hasOverdue" size="small" type="warning" @click="visitVisible = true">
            发起逾期回访
          </el-button>
        </div>
      </template>
      <el-alert class="mb" type="info" :closable="false" show-icon
                title="某期逾期后由调解员发起回访；回访后双方可上传未付款说明或收款异议，调解员据反馈决定是否继续履行观察，法官助理同步查看并判断司法确认/执行线索。" />
      <el-table :data="data.overdueVisits" stripe>
        <el-table-column prop="visitDate" label="回访日期" width="105" />
        <el-table-column label="期次" width="70">
          <template #default="{ row }">第{{ row.installmentNo || '—' }}期</template>
        </el-table-column>
        <el-table-column label="方式" width="75">
          <template #default="{ row }">{{ VISIT_WAYS[row.way] }}</template>
        </el-table-column>
        <el-table-column prop="mediatorName" label="调解员" width="100" />
        <el-table-column label="状态" width="125">
          <template #default="{ row }">
            <el-tag size="small" :type="(VISIT_STATUS[row.status] || {tag:'info'}).tag">
              {{ (VISIT_STATUS[row.status] || {label:row.status}).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="联系情况/观察意见" min-width="200" show-overflow-tooltip />
        <el-table-column label="法官助理意见" min-width="180">
          <template #default="{ row }">
            <span v-if="row.assistantOpinion">{{ row.assistantOpinion }}<div class="sub">{{ row.assistantName }} · {{ fmt(row.reviewedAt) }}</div></span>
            <el-tag v-else size="small" type="info">待法官助理同步查看</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canMediator && (row.status==='OPEN'||row.status==='OBSERVING')" size="small" type="warning" link
                       @click="openObserve(row)">继续观察/重算</el-button>
            <el-button v-if="canAssistant && (row.status==='OPEN'||row.status==='OBSERVING')" size="small" type="primary" link
                       @click="openReview(row)">法官判断</el-button>
          </template>
        </el-table-column>
        <template #empty>尚无逾期回访；某期逾期后调解员可发起回访</template>
      </el-table>
    </el-card>

    <!-- 后续执行线索 -->
    <el-card shadow="never" class="mb">
      <template #header>
        <div class="head">
          <b>后续执行线索（法官助理 / 执行联络）</b>
          <el-button v-if="canClue" size="small" type="primary" @click="clueVisible = true">登记执行线索</el-button>
        </div>
      </template>
      <el-table :data="data.executionClues" stripe>
        <el-table-column label="类型" width="110">
          <template #default="{ row }"><el-tag size="small">{{ CLUE_TYPES[row.clueType] }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="content" label="线索内容" min-width="240" show-overflow-tooltip />
        <el-table-column prop="createdByName" label="登记人" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="clueTag(row.status)">{{ clueStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handleNote" label="处置说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-if="canClue && row.status==='OPEN'" size="small" type="primary" link @click="openHandleClue(row)">移送/办结</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无执行线索；法官助理判断需要进入后续执行程序时登记</template>
      </el-table>
    </el-card>

    <!-- 常规履行回访 -->
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <b>履行回访记录</b>
          <el-button v-if="canExec" type="primary" size="small" @click="followVisible = true">新增回访</el-button>
        </div>
      </template>
      <el-table :data="data.followUps" stripe>
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

    <!-- 确认到账（工作人员） -->
    <el-dialog v-model="payVisible" title="确认履行到账（支持部分履行）" width="460px">
      <el-form :model="pay" label-width="110px">
        <el-form-item label="本期应收">¥{{ pay.dueAmount }}</el-form-item>
        <el-form-item label="本次到账(元)">
          <el-input-number v-model="pay.paidAmount" :min="0" :precision="2" :controls="false" style="width:200px" />
        </el-form-item>
        <el-form-item label="到账日期"><el-date-picker v-model="pay.paidDate" value-format="YYYY-MM-DD" style="width:200px" /></el-form-item>
        <el-form-item label="备注/凭证"><el-input v-model="pay.note" placeholder="银行流水号等" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPay">确认</el-button>
      </template>
    </el-dialog>

    <!-- 提交履行反馈（凭证/未付款说明/收款异议，支持附件） -->
    <el-dialog v-model="fbVisible" title="提交履行反馈" width="560px">
      <el-form :model="fb" label-width="110px">
        <el-form-item label="反馈类型">
          <el-radio-group v-model="fb.type" @change="onFbTypeChange">
            <el-radio-button value="PAYMENT_VOUCHER">付款凭证</el-radio-button>
            <el-radio-button value="NONPAYMENT_EXPLANATION">未付款说明</el-radio-button>
            <el-radio-button value="RECEIPT_OBJECTION">收款异议</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="提交方">
          <el-radio-group v-model="fb.side">
            <el-radio value="APPLICANT">申请人</el-radio>
            <el-radio value="RESPONDENT">被申请人</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关联期次">
          <el-select v-model="fb.planId" clearable placeholder="整案反馈可不选" style="width:320px">
            <el-option v-for="p in unpaidPlans" :key="p.id"
                       :label="`第${p.installmentNo}期（应付${p.dueAmount}，${p.dueDate}）`" :value="String(p.id)" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="fb.type !== 'PAYMENT_VOUCHER'" label="关联逾期回访">
          <el-select v-model="fb.visitId" clearable placeholder="逾期回访后提交时选择" style="width:320px">
            <el-option v-for="v in openVisits" :key="v.id"
                       :label="`${v.visitDate} 第${v.installmentNo || '—'}期回访`" :value="String(v.id)" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="fb.type !== 'NONPAYMENT_EXPLANATION'" label="金额(元)">
          <el-input-number v-model="fb.amount" :min="0" :precision="2" :controls="false" style="width:200px" />
        </el-form-item>
        <el-form-item :label="fb.type === 'RECEIPT_OBJECTION' ? '异议内容' : '说明'">
          <el-input v-model="fb.content" type="textarea" :rows="3"
                    :placeholder="fb.type === 'PAYMENT_VOUCHER' ? '付款时间、付款渠道、流水号等（无附件时必填）'
                      : fb.type === 'NONPAYMENT_EXPLANATION' ? '说明未付原因、预计付款时间、还款安排'
                      : '说明未收到款项或到账金额不符等异议情况'" />
        </el-form-item>
        <el-form-item v-if="fb.type === 'PAYMENT_VOUCHER'" label="付款凭证附件">
          <input ref="fileInput" type="file" @change="onFileChange" />
          <div class="sub">支持转账回单、截图等（图片/PDF，≤10MB）</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fbVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>

    <!-- 确认收款（依凭证） -->
    <el-dialog v-model="receiptVisible" title="收款方确认收款" width="440px">
      <el-alert type="success" :closable="false" show-icon class="mb"
                title="确认后该笔到账计入履行台账；不足本期应收的按部分履行处理。" />
      <el-form :model="receipt" label-width="120px">
        <el-form-item label="确认到账(元)">
          <el-input-number v-model="receipt.paidAmount" :min="0" :precision="2" :controls="false" style="width:200px" />
        </el-form-item>
        <el-form-item label="到账日期"><el-date-picker v-model="receipt.paidDate" value-format="YYYY-MM-DD" style="width:200px" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiptVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConfirmReceipt">确认收款</el-button>
      </template>
    </el-dialog>

    <!-- 发起逾期回访 -->
    <el-dialog v-model="visitVisible" title="发起逾期回访" width="500px">
      <el-form :model="visit" label-width="95px">
        <el-form-item label="逾期期次">
          <el-select v-model="visit.planId" style="width:320px">
            <el-option v-for="p in overduePlans" :key="p.id"
                       :label="`第${p.installmentNo}期（应付${p.dueAmount}，${p.dueDate}）`" :value="String(p.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="回访日期"><el-date-picker v-model="visit.visitDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="方式">
          <el-radio-group v-model="visit.way">
            <el-radio-button value="PHONE">电话</el-radio-button>
            <el-radio-button value="ONLINE">线上</el-radio-button>
            <el-radio-button value="ONSITE">上门</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="联系情况"><el-input v-model="visit.note" type="textarea" :rows="3" placeholder="逾期原因、双方态度、还款意愿等" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visitVisible = false">取消</el-button>
        <el-button type="primary" @click="submitVisit">发起回访</el-button>
      </template>
    </el-dialog>

    <!-- 调解员决定继续履行观察 -->
    <el-dialog v-model="observeVisible" title="根据反馈决定是否继续履行观察" width="500px">
      <el-alert type="warning" :closable="false" show-icon class="mb"
                title="继续履行观察的，观察期自决定之日起算；观察期内当事人再次反馈的，观察期随反馈重新计算。" />
      <el-form :model="observe" label-width="110px">
        <el-form-item label="是否继续观察">
          <el-switch v-model="observe.continueObserve" active-text="继续履行观察" inactive-text="不再观察" />
        </el-form-item>
        <el-form-item v-if="observe.continueObserve" label="观察期(天)">
          <el-input-number v-model="observe.observeDays" :min="1" :max="60" :controls="false" style="width:160px" />
        </el-form-item>
        <el-form-item label="处理意见"><el-input v-model="observe.note" type="textarea" :rows="3" placeholder="综合双方未付款说明/收款异议后的判断" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="observeVisible = false">取消</el-button>
        <el-button type="primary" @click="submitObserve">确定</el-button>
      </template>
    </el-dialog>

    <!-- 法官助理判断 -->
    <el-dialog v-model="reviewVisible" title="法官助理判断（司法确认 / 执行线索）" width="540px">
      <el-form :model="review" label-width="110px">
        <el-form-item label="处置判断">
          <el-select v-model="review.route" style="width:340px">
            <el-option v-for="(l,k) in ASSISTANT_ROUTES" :key="k" :label="l" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="意见"><el-input v-model="review.opinion" type="textarea" :rows="2" placeholder="同步查看回访与双方反馈后的意见" /></el-form-item>
        <template v-if="review.route === 'EXECUTION'">
          <el-form-item label="执行线索类型">
            <el-select v-model="review.clueType" style="width:200px">
              <el-option v-for="(l,k) in CLUE_TYPES" :key="k" :label="l" :value="k" />
            </el-select>
          </el-form-item>
          <el-form-item label="线索内容"><el-input v-model="review.clueContent" type="textarea" :rows="2" placeholder="财产/收入/担保人等可供执行的线索" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReview">提交判断</el-button>
      </template>
    </el-dialog>

    <!-- 登记执行线索 -->
    <el-dialog v-model="clueVisible" title="登记后续执行线索" width="500px">
      <el-form :model="clue" label-width="95px">
        <el-form-item label="线索类型">
          <el-select v-model="clue.clueType" style="width:200px">
            <el-option v-for="(l,k) in CLUE_TYPES" :key="k" :label="l" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="线索内容"><el-input v-model="clue.content" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clueVisible = false">取消</el-button>
        <el-button type="primary" @click="submitClue">登记</el-button>
      </template>
    </el-dialog>

    <!-- 移送/办结线索 -->
    <el-dialog v-model="handleClueVisible" title="移送 / 办结执行线索" width="460px">
      <el-form :model="handleClue" label-width="100px">
        <el-form-item label="处置">
          <el-radio-group v-model="handleClue.status">
            <el-radio value="FORWARDED">已移送执行</el-radio>
            <el-radio value="DONE">已办结</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处置说明"><el-input v-model="handleClue.handleNote" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleClueVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHandleClue">确定</el-button>
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
import { ElMessage } from 'element-plus'
import api from '../../api.js'
import { auth } from '../../store.js'
import {
  PLAN_STATUS, FOLLOW_RESULTS, FEEDBACK_TYPES, FEEDBACK_TYPE_TAG, FEEDBACK_STATUS,
  SIDE_LABELS, VISIT_STATUS, VISIT_WAYS, ASSISTANT_ROUTES, CLUE_TYPES
} from '../../constants.js'

const props = defineProps({ data: Object })
const emit = defineEmits(['changed'])
const c = computed(() => props.data.case)
const a = computed(() => props.data.agreement)

const today = () => new Date().toISOString().substring(0, 10)
const canExec = computed(() => auth.is('EXECUTION', 'MEDIATOR', 'WINDOW', 'ADMIN'))
const canMediator = computed(() => auth.is('MEDIATOR', 'ADMIN'))
const canAssistant = computed(() => auth.is('ASSISTANT', 'ADMIN'))
const canClue = computed(() => auth.is('ASSISTANT', 'EXECUTION', 'WINDOW', 'ADMIN'))
// 当事人本人可提交反馈；工作人员也可代为上传/登记
const canFeedback = computed(() => auth.is('PARTY', 'MEDIATOR', 'EXECUTION', 'WINDOW', 'ADMIN'))

const hasOverdue = computed(() => (props.data.plans || []).some(p => ['OVERDUE', 'PARTIAL'].includes(p.status)))
const unpaidPlans = computed(() => (props.data.plans || []).filter(p => p.status !== 'PAID'))
const overduePlans = computed(() => (props.data.plans || []).filter(p => ['OVERDUE', 'PARTIAL'].includes(p.status)))
const openVisits = computed(() => (props.data.overdueVisits || []).filter(v => ['OPEN', 'OBSERVING'].includes(v.status)))
const observeDaysLeft = computed(() => {
  if (!c.value.observationUntil) return 0
  const diff = Math.ceil((new Date(c.value.observationUntil) - new Date(today())) / 86400000)
  return diff
})

const fmt = t => t ? t.replace('T', ' ').substring(0, 16) : ''
const followTag = r => ({ NORMAL: 'success', PARTIAL: 'warning', OVERDUE: 'danger', DONE: 'success', OBJECTION: 'info' }[r])
const clueTag = s => ({ OPEN: 'danger', FORWARDED: 'warning', DONE: 'success' }[s] || 'info')
const clueStatusLabel = s => ({ OPEN: '待跟进', FORWARDED: '已移送', DONE: '已办结' }[s] || s)

// ---------- 建立计划 ----------
const buildVisible = ref(false)
const installments = ref([{ dueAmount: 0, dueDate: '' }, { dueAmount: 0, dueDate: '' }])
const buildPlans = async () => {
  await api.post(`/cases/${c.value.id}/plans`, { installments: installments.value })
  buildVisible.value = false
  ElMessage.success('履行计划已建立，到期未付将自动预警')
  emit('changed')
}
// 兼容“依据协议建立付款计划”入口（计划为空且协议生效时显示）
const canBuild = computed(() => canExec.value && a.value?.effectiveAt && !(props.data.plans || []).length)

// ---------- 工作人员确认到账 ----------
const payVisible = ref(false)
const pay = reactive({ id: null, dueAmount: 0, paidAmount: 0, paidDate: '', note: '' })
const openPay = row => {
  pay.id = row.id
  pay.dueAmount = Number(row.dueAmount) - Number(row.paidAmount || 0)
  pay.paidAmount = pay.dueAmount
  pay.paidDate = today()
  pay.note = ''
  payVisible.value = true
}
const submitPay = async () => {
  await api.post(`/plans/${pay.id}/pay`, { paidAmount: pay.paidAmount, paidDate: pay.paidDate, note: pay.note })
  payVisible.value = false
  ElMessage.success('到账已确认并留痕')
  emit('changed')
}

// ---------- 手动扫描/提醒 ----------
const scanOverdue = async () => {
  const r = await api.post('/overdue/scan')
  ElMessage.success(r.newlyOverdue > 0 ? `扫描完成，新发现 ${r.newlyOverdue} 个逾期节点` : '扫描完成，暂无新增逾期')
  emit('changed')
}
const scanObservation = async () => {
  const r = await api.post('/observation/scan')
  ElMessage[r.expired > 0 ? 'warning' : 'success'](
    r.expired > 0 ? `观察期届满扫描：${r.expired} 件已恢复逾期` : '扫描完成，暂无届满观察期')
  emit('changed')
}
const remindNow = async () => {
  const r = await api.post('/performance/remind-now')
  ElMessage[r.remindedTotal > 0 ? 'success' : 'info'](
    r.remindedTotal > 0 ? `已对 ${r.remindedTotal} 个临期/到期节点发送按期提醒` : '暂无新的临期/到期节点需要提醒')
  emit('changed')
}

// ---------- 履行反馈（凭证/说明/异议） ----------
const fbVisible = ref(false)
const fileInput = ref(null)
const emptyFb = () => ({
  type: 'PAYMENT_VOUCHER', side: 'RESPONDENT', planId: '', visitId: '',
  amount: null, content: '', file: null
})
const fb = reactive(emptyFb())
const openFeedback = row => {
  Object.assign(fb, emptyFb())
  if (row) fb.planId = String(row.id)
  if (auth.is('PARTY')) {
    fb.side = c.value.applicantUserId === auth.user.id ? 'APPLICANT' : 'RESPONDENT'
  }
  fbVisible.value = true
}
const onFbTypeChange = t => {
  // 收款异议一般由收款方（申请人）提出，付款凭证/未付款说明由被申请人提出
  fb.side = t === 'RECEIPT_OBJECTION' ? 'APPLICANT' : 'RESPONDENT'
}
const onFileChange = e => { fb.file = e.target.files[0] || null }
const submitFeedback = async () => {
  const fd = new FormData()
  fd.append('type', fb.type)
  fd.append('side', fb.side)
  if (fb.planId) fd.append('planId', fb.planId)
  if (fb.visitId) fd.append('visitId', fb.visitId)
  if (fb.amount != null && fb.amount !== '') fd.append('amount', fb.amount)
  if (fb.content) fd.append('content', fb.content)
  if (fb.file) fd.append('file', fb.file)
  await api.post(`/cases/${c.value.id}/performance/feedback`, fd)
  fbVisible.value = false
  ElMessage.success('履行反馈已提交并写入案件时间线')
  emit('changed')
}
const canConfirmReceipt = row =>
  canExec.value && row.type === 'PAYMENT_VOUCHER' && row.status === 'SUBMITTED'
const receiptVisible = ref(false)
const receipt = reactive({ id: null, paidAmount: 0, paidDate: today() })
const planRemaining = planId => {
  const p = (props.data.plans || []).find(x => String(x.id) === String(planId))
  return p ? Number(p.dueAmount) - Number(p.paidAmount || 0) : 0
}
const openConfirmReceipt = row => {
  receipt.id = row.id
  receipt.paidAmount = row.amount != null ? Number(row.amount) : planRemaining(row.planId)
  receipt.paidDate = today()
  receiptVisible.value = true
}
const submitConfirmReceipt = async () => {
  await api.post(`/performance/feedbacks/${receipt.id}/confirm-receipt`,
    { paidAmount: receipt.paidAmount, paidDate: receipt.paidDate })
  receiptVisible.value = false
  ElMessage.success('已确认收款并计入履行台账')
  emit('changed')
}
const downloadAttachment = row => {
  fetch(`/api/attachments/${row.id}`, { headers: { Authorization: `Bearer ${localStorage.getItem('token')}` } })
    .then(r => { if (!r.ok) throw new Error('下载失败'); return r.blob() })
    .then(blob => {
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = row.attachmentName || '附件'
      a.click()
    })
    .catch(() => ElMessage.error('附件下载失败'))
}

// ---------- 逾期回访 ----------
const visitVisible = ref(false)
const visit = reactive({ planId: '', visitDate: today(), way: 'PHONE', note: '' })
const submitVisit = async () => {
  await api.post(`/cases/${c.value.id}/overdue-visits`, {
    planId: visit.planId || undefined, visitDate: visit.visitDate, way: visit.way, note: visit.note
  })
  visitVisible.value = false
  ElMessage.success('逾期回访已发起，双方可提交未付款说明或收款异议')
  emit('changed')
}

// ---------- 继续履行观察 ----------
const observeVisible = ref(false)
const observe = reactive({ id: null, continueObserve: true, observeDays: 7, note: '' })
const openObserve = row => {
  observe.id = row.id
  observe.continueObserve = true
  observe.observeDays = 7
  observe.note = ''
  observeVisible.value = true
}
const submitObserve = async () => {
  await api.post(`/overdue-visits/${observe.id}/observe`, {
    continueObserve: observe.continueObserve, observeDays: observe.observeDays, note: observe.note
  })
  observeVisible.value = false
  ElMessage.success(observe.continueObserve ? '已转入履行观察期，观察期随反馈重新计算' : '已结束观察，案件维持逾期待法官助理判断')
  emit('changed')
}

// ---------- 法官助理判断 ----------
const reviewVisible = ref(false)
const review = reactive({ id: null, route: 'CONTINUE', opinion: '', clueType: 'PROPERTY', clueContent: '' })
const openReview = row => {
  review.id = row.id
  review.route = 'CONTINUE'
  review.opinion = ''
  review.clueType = 'PROPERTY'
  review.clueContent = ''
  reviewVisible.value = true
}
const submitReview = async () => {
  const body = { route: review.route, opinion: review.opinion }
  if (review.route === 'EXECUTION') {
    body.clueType = review.clueType
    body.clueContent = review.clueContent
  }
  await api.post(`/overdue-visits/${review.id}/assistant-review`, body)
  reviewVisible.value = false
  ElMessage.success('法官助理判断已记录并同步至案件时间线')
  emit('changed')
}

// ---------- 执行线索 ----------
const clueVisible = ref(false)
const clue = reactive({ clueType: 'PROPERTY', content: '' })
const submitClue = async () => {
  await api.post(`/cases/${c.value.id}/execution-clues`, { clueType: clue.clueType, content: clue.content, route: 'EXECUTION' })
  clueVisible.value = false
  ElMessage.success('执行线索已登记')
  emit('changed')
}
const handleClueVisible = ref(false)
const handleClue = reactive({ id: null, status: 'FORWARDED', handleNote: '' })
const openHandleClue = row => {
  handleClue.id = row.id
  handleClue.status = 'FORWARDED'
  handleClue.handleNote = ''
  handleClueVisible.value = true
}
const submitHandleClue = async () => {
  await api.post(`/execution-clues/${handleClue.id}/handle`, { status: handleClue.status, handleNote: handleClue.handleNote })
  handleClueVisible.value = false
  ElMessage.success('执行线索处置已记录')
  emit('changed')
}

// ---------- 常规回访 ----------
const followVisible = ref(false)
const follow = reactive({ followUpDate: today(), way: 'PHONE', result: 'NORMAL', note: '' })
const submitFollow = async () => {
  await api.post(`/cases/${c.value.id}/follow-ups`, follow)
  followVisible.value = false
  ElMessage.success('回访已记录')
  emit('changed')
}
</script>

<style scoped>
.mb { margin-bottom: 14px; }
.head { display: flex; justify-content: space-between; align-items: center; }
.ins-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.sub { color: #909399; font-size: 12px; }
</style>
