package cn.court.mediation.service;

import cn.court.mediation.common.BizException;
import cn.court.mediation.entity.*;
import cn.court.mediation.enums.CaseStatus;
import cn.court.mediation.enums.Role;
import cn.court.mediation.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 分期履行跟踪：
 * 1. 协议达成后按每一期生成「付款 / 上传凭证 / 确认收款」提醒，到期前平台自动发送，逾期发送逾期告知；
 * 2. 当事人上传付款凭证，执行联络/收款人核实后确认收款；
 * 3. 某一期逾期，调解员发起回访并进入履行观察期，法官助理同步研判是否司法确认或移交执行线索；
 * 4. 回访后当事人可提交未付款说明 / 收款异议，观察期随双方反馈重新计算，调解员据此决定是否继续观察。
 */
@Service
public class InstallmentTrackingService {

    /** 到期前 N 天发送付款提醒 */
    public static final int REMIND_BEFORE_DAYS = 3;
    /** 履行观察期默认长度（天） */
    public static final int DEFAULT_OBSERVE_DAYS = 15;

    private final PaymentReminderRepository reminderRepository;
    private final PaymentVoucherRepository voucherRepository;
    private final OverdueVisitRepository visitRepository;
    private final PerformanceFeedbackRepository feedbackRepository;
    private final PerformancePlanRepository planRepository;
    private final CaseRepository caseRepository;
    private final TimelineService timelineService;

    public InstallmentTrackingService(PaymentReminderRepository reminderRepository,
                                      PaymentVoucherRepository voucherRepository,
                                      OverdueVisitRepository visitRepository,
                                      PerformanceFeedbackRepository feedbackRepository,
                                      PerformancePlanRepository planRepository,
                                      CaseRepository caseRepository,
                                      TimelineService timelineService) {
        this.reminderRepository = reminderRepository;
        this.voucherRepository = voucherRepository;
        this.visitRepository = visitRepository;
        this.feedbackRepository = feedbackRepository;
        this.planRepository = planRepository;
        this.caseRepository = caseRepository;
        this.timelineService = timelineService;
    }

    // ============================================================
    // 一、按期提醒
    // ============================================================

    /** 付款计划建立时，为每个履行节点排定三类提醒 */
    @Transactional
    public void initPlanReminders(Long caseId, List<PerformancePlan> plans) {
        MediationCase c = caseRepository.findById(caseId).orElseThrow(() -> new BizException("案件不存在"));
        for (PerformancePlan p : plans) {
            createPlannedReminder(c, p, "BEFORE_DUE", "RESPONDENT", p.getDueDate().minusDays(REMIND_BEFORE_DAYS),
                    String.format("【履行提醒】您与%s的调解协议（%s）第%d期款项%s元将于%s到期，请按时支付至约定账户。",
                            c.getApplicantName(), c.getCaseNo(), p.getInstallmentNo(),
                            p.getDueAmount().toPlainString(), p.getDueDate()));
            createPlannedReminder(c, p, "UPLOAD_VOUCHER", "RESPONDENT", p.getDueDate(),
                    String.format("【凭证提醒】%s第%d期履行日为%s，付款后请及时在平台上传转账/汇款凭证，便于对方核对确认收款。",
                            c.getCaseNo(), p.getInstallmentNo(), p.getDueDate()));
            createPlannedReminder(c, p, "CONFIRM_RECEIPT", "APPLICANT", p.getDueDate(),
                    String.format("【收款提醒】被申请人%s第%d期款项%s元应于%s到账，请留意银行流水并在平台确认收款。",
                            c.getRespondentName(), p.getInstallmentNo(),
                            p.getDueAmount().toPlainString(), p.getDueDate()));
        }
    }

    private void createPlannedReminder(MediationCase c, PerformancePlan p, String stage, String target,
                                       LocalDate plannedDate, String content) {
        PaymentReminder r = new PaymentReminder();
        r.setCaseId(c.getId());
        r.setPlanId(p.getId());
        r.setInstallmentNo(p.getInstallmentNo());
        r.setStageType(stage);
        r.setTargetParty(target);
        r.setChannel("SMS");
        r.setPlannedDate(plannedDate);
        r.setStatus("PLANNED");
        r.setContent(content);
        reminderRepository.save(r);
    }

    /** 逾期扫描后对该节点发送逾期告知（幂等：同一节点只发一次） */
    @Transactional
    public void sendOverdueNotice(PerformancePlan p) {
        MediationCase c = caseRepository.findById(p.getCaseId()).orElse(null);
        if (c == null) return;
        boolean exists = !reminderRepository
                .findByCaseIdAndPlanIdAndStageType(c.getId(), p.getId(), "OVERDUE_NOTICE").isEmpty();
        if (exists) return;
        PaymentReminder r = new PaymentReminder();
        r.setCaseId(c.getId());
        r.setPlanId(p.getId());
        r.setInstallmentNo(p.getInstallmentNo());
        r.setStageType("OVERDUE_NOTICE");
        r.setTargetParty("RESPONDENT");
        r.setChannel("SMS");
        r.setPlannedDate(LocalDate.now());
        r.setStatus("SENT");
        r.setSentAt(LocalDateTime.now());
        r.setOperatorName("系统");
        r.setContent(String.format("【逾期告知】您在%s调解协议项下第%d期款项%s元已于%s到期，截至%s未足额到账，请尽快履行；逾期未付的，法院将组织回访，并可能依法进入司法确认或后续执行程序。",
                c.getCaseNo(), p.getInstallmentNo(), p.getDueAmount().toPlainString(), p.getDueDate(), LocalDate.now()));
        reminderRepository.save(r);
        timelineService.record(c.getId(), "REMIND", "第" + p.getInstallmentNo() + "期逾期告知已发送",
                "平台向被申请人" + c.getRespondentName() + "发送逾期告知短信，提示及时履行及相应法律后果。", null);
    }

    /** 人工立即发送某一期某一环节的提醒（演示/补发用） */
    @Transactional
    public PaymentReminder sendReminderNow(Long caseId, Map<String, Object> body, User operator) {
        MediationCase c = caseRepository.findById(caseId).orElseThrow(() -> new BizException("案件不存在"));
        String stage = (String) body.getOrDefault("stageType", "BEFORE_DUE");
        String target = (String) body.getOrDefault("targetParty", "RESPONDENT");
        String channel = (String) body.getOrDefault("channel", "SMS");
        Long planId = body.get("planId") == null ? null : Long.valueOf(body.get("planId").toString());
        PerformancePlan p = planId == null ? null
                : planRepository.findById(planId).filter(x -> x.getCaseId().equals(caseId))
                .orElseThrow(() -> new BizException("履行节点不存在"));

        String custom = (String) body.get("content");
        // 若该节点该环节已有待发送的计划提醒，直接发送该条（幂等补发），不产生重复记录
        List<PaymentReminder> planned = planId == null ? List.of()
                : reminderRepository.findByCaseIdAndPlanIdAndStageTypeAndStatus(caseId, planId, stage, "PLANNED");
        PaymentReminder r;
        if (!planned.isEmpty()) {
            r = planned.get(0);
            r.setTargetParty(target);
            r.setChannel(channel);
            if (custom != null && !custom.isBlank()) r.setContent(custom);
        } else {
            r = new PaymentReminder();
            r.setCaseId(caseId);
            r.setPlanId(planId);
            r.setInstallmentNo(p == null ? null : p.getInstallmentNo());
            r.setStageType(stage);
            r.setTargetParty(target);
            r.setChannel(channel);
            r.setPlannedDate(LocalDate.now());
            r.setContent(custom != null && !custom.isBlank() ? custom : defaultContent(c, p, stage));
        }
        r.setStatus("SENT");
        r.setSentAt(LocalDateTime.now());
        r.setOperatorId(operator.getId());
        r.setOperatorName(operator.getRealName());
        reminderRepository.save(r);

        timelineService.record(caseId, "REMIND", "平台提醒已发送：" + stageLabel(stage),
                "发送对象：" + ("APPLICANT".equals(target) ? "申请人（收款人）" : "被申请人（付款义务人）")
                        + "；方式：" + channelLabel(channel) + "；内容：" + r.getContent(), operator);
        return r;
    }

    /**
     * 定时发送到期提醒：计划日期 ≤ 今天的待发送提醒逐条发出。
     * 启动 40 秒后执行一次，之后每天执行。
     */
    @Scheduled(initialDelay = 40_000L, fixedDelay = 24L * 60 * 60 * 1000)
    @Transactional
    public void dispatchDueReminders() {
        List<PaymentReminder> due = reminderRepository.findByStatusAndPlannedDateBefore("PLANNED", LocalDate.now().plusDays(1));
        for (PaymentReminder r : due) {
            r.setStatus("SENT");
            r.setSentAt(LocalDateTime.now());
            r.setOperatorName("系统");
            reminderRepository.save(r);
            timelineService.record(r.getCaseId(), "REMIND", "按期提醒已发送：" + stageLabel(r.getStageType()),
                    "第" + (r.getInstallmentNo() == null ? "—" : r.getInstallmentNo()) + "期，"
                            + "对象：" + ("APPLICANT".equals(r.getTargetParty()) ? "申请人" : "被申请人")
                            + "；" + r.getContent(), null);
        }
    }

    private String defaultContent(MediationCase c, PerformancePlan p, String stage) {
        String no = p == null ? "" : "第" + p.getInstallmentNo() + "期";
        String amt = p == null ? "" : p.getDueAmount().toPlainString() + "元，到期日" + p.getDueDate();
        return switch (stage) {
            case "UPLOAD_VOUCHER" -> "【凭证提醒】请在付款后及时上传" + no + "转账凭证。";
            case "CONFIRM_RECEIPT" -> "【收款提醒】请核对" + no + amt + "是否到账并确认收款。";
            case "OVERDUE_NOTICE" -> "【逾期告知】" + no + "款项已逾期，请尽快履行。";
            default -> "【履行提醒】" + c.getCaseNo() + no + "款项" + amt + "即将到期/已到期，请按时履行。";
        };
    }

    public static String stageLabel(String stage) {
        return switch (stage) {
            case "BEFORE_DUE" -> "按期付款提醒";
            case "UPLOAD_VOUCHER" -> "上传付款凭证提醒";
            case "CONFIRM_RECEIPT" -> "确认收款提醒";
            case "OVERDUE_NOTICE" -> "逾期告知";
            default -> stage;
        };
    }

    private String channelLabel(String ch) {
        return switch (ch) {
            case "EMAIL" -> "邮件";
            case "ONLINE" -> "站内平台";
            default -> "短信";
        };
    }

    // ============================================================
    // 二、付款凭证上传与收款确认
    // ============================================================

    /** 上传付款/收款凭证（Base64 文本入库，演示环境） */
    @Transactional
    public PaymentVoucher uploadVoucher(Long caseId, Map<String, Object> body, User user) {
        MediationCase c = caseRepository.findById(caseId).orElseThrow(() -> new BizException("案件不存在"));
        String fileName = (String) body.get("fileName");
        String fileData = (String) body.get("fileData");
        if (fileName == null || fileName.isBlank() || fileData == null || fileData.isBlank()) {
            throw new BizException("请选择凭证文件并上传");
        }
        // 去掉 data:application/pdf;base64, 前缀
        String cleanData = fileData.contains(",") && fileData.startsWith("data:") ? fileData.substring(fileData.indexOf(',') + 1) : fileData;

        Long planId = body.get("planId") == null ? null : Long.valueOf(body.get("planId").toString());
        PerformancePlan p = planId == null ? null
                : planRepository.findById(planId).filter(x -> x.getCaseId().equals(caseId)).orElse(null);

        String partySide = (String) body.getOrDefault("partySide", "RESPONDENT");
        if (user.getRole() == Role.PARTY) {
            // 平台当事人账号为申请人侧，仅可上传收款凭证/收款异议凭证
            partySide = "APPLICANT";
        }
        PaymentVoucher v = new PaymentVoucher();
        v.setCaseId(caseId);
        v.setPlanId(planId);
        v.setInstallmentNo(p == null ? null : p.getInstallmentNo());
        v.setPartySide(partySide);
        v.setUploaderUserId(user.getId());
        v.setUploaderName(user.getRealName());
        v.setUploaderRole(user.getRole().name());
        v.setFileName(fileName);
        v.setFileType((String) body.getOrDefault("fileType", "application/octet-stream"));
        v.setFileData(cleanData);
        v.setAmount(body.get("amount") == null || body.get("amount").toString().isBlank() ? null
                : new java.math.BigDecimal(body.get("amount").toString()));
        v.setPaidDate(body.get("paidDate") == null || body.get("paidDate").toString().isBlank() ? null
                : LocalDate.parse(body.get("paidDate").toString()));
        v.setRemark((String) body.getOrDefault("remark", ""));
        v.setStatus("PENDING");
        voucherRepository.save(v);

        // 凭证已上传，对应的「上传凭证提醒」视为完成
        if (planId != null) {
            reminderRepository.findByCaseIdAndPlanIdAndStageType(caseId, planId, "UPLOAD_VOUCHER")
                    .forEach(r -> { if ("PLANNED".equals(r.getStatus())) { r.setStatus("SENT"); r.setSentAt(LocalDateTime.now()); reminderRepository.save(r); } });
        }

        timelineService.record(caseId, "VOUCHER",
                "APPLICANT".equals(partySide) ? "收款人上传收款凭证" : "付款义务人上传付款凭证",
                (p == null ? "" : "第" + p.getInstallmentNo() + "期，") + "文件：" + fileName
                        + (v.getAmount() != null ? "，凭证金额" + v.getAmount().toPlainString() + "元" : "")
                        + "；请执行联络/收款人核实并确认收款。", user, fileName);
        return v;
    }

    /** 凭证不予采信（如截图不清、金额不符） */
    @Transactional
    public PaymentVoucher rejectVoucher(Long voucherId, String note, User user) {
        PaymentVoucher v = voucherRepository.findById(voucherId).orElseThrow(() -> new BizException("凭证不存在"));
        if ("CONFIRMED".equals(v.getStatus())) {
            throw new BizException("已据此确认收款的凭证不能驳回，如需更正请登记异议或回访说明");
        }
        v.setStatus("REJECTED");
        v.setRemark((v.getRemark() == null ? "" : v.getRemark() + "；") + "驳回原因：" + (note == null ? "" : note));
        voucherRepository.save(v);
        timelineService.record(v.getCaseId(), "VOUCHER", "付款凭证未予采信",
                "文件：" + v.getFileName() + "；原因：" + note + "。已通知当事人重新上传或作出说明。", user, v.getFileName());
        return v;
    }

    /** 凭证元数据列表（不含文件正文，供案件详情展示） */
    @Transactional(readOnly = true)
    public List<PaymentVoucher> listVouchersMeta(Long caseId) {
        return voucherRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream().peek(v -> v.setFileData(null)).toList();
    }

    public PaymentVoucher mustGetVoucher(Long id) {
        return voucherRepository.findById(id).orElseThrow(() -> new BizException("凭证不存在"));
    }

    // ============================================================
    // 三、逾期回访（调解员发起）
    // ============================================================

    @Transactional
    public OverdueVisit createVisit(Long caseId, Map<String, Object> body, User user) {
        MediationCase c = caseRepository.findById(caseId).orElseThrow(() -> new BizException("案件不存在"));
        if (user.getRole() != Role.MEDIATOR && user.getRole() != Role.ADMIN
                && user.getRole() != Role.EXECUTION && user.getRole() != Role.WINDOW) {
            throw new BizException("仅调解员可发起逾期回访");
        }
        List<PerformancePlan> plans = planRepository.findByCaseIdOrderByInstallmentNo(caseId);
        PerformancePlan overduePlan = plans.stream()
                .filter(p -> "OVERDUE".equals(p.getStatus()) || "PARTIAL".equals(p.getStatus())).findFirst().orElse(null);
        if (overduePlan == null) {
            throw new BizException("当前没有逾期或部分履行的期次，无需发起逾期回访");
        }
        int days = body.get("observeDays") == null ? DEFAULT_OBSERVE_DAYS
                : Integer.parseInt(body.get("observeDays").toString());
        if (days < 1 || days > 90) throw new BizException("观察期长度应在1-90天之间");

        OverdueVisit v = new OverdueVisit();
        v.setCaseId(caseId);
        v.setPlanId(overduePlan.getId());
        v.setInstallmentNo(overduePlan.getInstallmentNo());
        v.setWay((String) body.getOrDefault("way", "PHONE"));
        v.setVisitDate(body.get("visitDate") == null ? LocalDate.now() : LocalDate.parse(body.get("visitDate").toString()));
        v.setContent((String) body.getOrDefault("content", ""));
        v.setMediatorId(user.getId());
        v.setMediatorName(user.getRealName());
        v.setStatus("OBSERVING");
        v.setObserveDays(days);
        v.setObserveUntil(LocalDate.now().plusDays(days));
        visitRepository.save(v);

        c.setStatus(CaseStatus.OVERDUE);
        c.setObservationActive(true);
        c.setObserveUntil(v.getObserveUntil());
        caseRepository.save(c);

        timelineService.record(caseId, "OBSERVE", "第" + overduePlan.getInstallmentNo() + "期逾期，调解员发起回访",
                "回访日期：" + v.getVisitDate() + "，方式：" + wayLabel(v.getWay()) + "。回访情况：" + v.getContent()
                        + "。案件进入履行观察期，观察至" + v.getObserveUntil()
                        + "；当事人可提交未付款说明或收款异议，观察期将随反馈重新计算，法官助理同步研判。", user);
        return v;
    }

    // ============================================================
    // 四、当事人反馈：未付款说明 / 收款异议（观察期随反馈重新计算）
    // ============================================================

    @Transactional
    public PerformanceFeedback addFeedback(Long visitId, Map<String, Object> body, User user) {
        OverdueVisit v = visitRepository.findById(visitId).orElseThrow(() -> new BizException("逾期回访不存在"));
        MediationCase c = caseRepository.findById(v.getCaseId()).orElseThrow(() -> new BizException("案件不存在"));
        String type = (String) body.getOrDefault("type", "NON_PAYMENT");
        if (!"NON_PAYMENT".equals(type) && !"RECEIPT_OBJECTION".equals(type)) {
            throw new BizException("反馈类型不正确");
        }
        // 收款异议：申请人侧；未付款说明：被申请人侧（演示环境由调解员/执行联络回访后代为登记）
        boolean staff = user.getRole() != Role.PARTY;
        if ("RECEIPT_OBJECTION".equals(type) && !staff
                && (c.getApplicantUserId() == null || !c.getApplicantUserId().equals(user.getId()))) {
            throw new BizException("仅收款申请人可提交收款异议");
        }
        if ("NON_PAYMENT".equals(type) && !staff) {
            throw new BizException("未付款说明由调解员回访被申请人后代为登记");
        }
        String content = (String) body.getOrDefault("content", "");
        if (content.isBlank()) {
            throw new BizException("请填写反馈内容");
        }

        PerformanceFeedback f = new PerformanceFeedback();
        f.setCaseId(v.getCaseId());
        f.setVisitId(visitId);
        f.setType(type);
        f.setUserId(user.getId());
        f.setUserName(user.getRealName());
        f.setUserRole(user.getRole().name());
        f.setPartySide("RECEIPT_OBJECTION".equals(type) ? "APPLICANT" : "RESPONDENT");
        f.setContent(content);
        if (body.get("fileName") != null && body.get("fileData") != null) {
            String fd = body.get("fileData").toString();
            f.setFileName(body.get("fileName").toString());
            f.setFileType(String.valueOf(body.getOrDefault("fileType", "application/octet-stream")));
            f.setFileData(fd.contains(",") && fd.startsWith("data:") ? fd.substring(fd.indexOf(',') + 1) : fd);
        }
        feedbackRepository.save(f);

        // 观察期随反馈重新计算（届满后又收到反馈的，重新开启观察）
        v.setStatus("OBSERVING");
        v.setObserveUntil(LocalDate.now().plusDays(v.getObserveDays() == null ? DEFAULT_OBSERVE_DAYS : v.getObserveDays()));
        v.setRecomputeCount((v.getRecomputeCount() == null ? 0 : v.getRecomputeCount()) + 1);
        v.setLastFeedbackAt(LocalDateTime.now());
        visitRepository.save(v);

        c.setObservationActive(true);
        c.setObserveUntil(v.getObserveUntil());
        c.setObserveRecomputeCount((c.getObserveRecomputeCount() == null ? 0 : c.getObserveRecomputeCount()) + 1);
        caseRepository.save(c);

        timelineService.record(v.getCaseId(), "FEEDBACK",
                "NON_PAYMENT".equals(type) ? "当事人提交未付款说明" : "收款人提出收款异议",
                ("NON_PAYMENT".equals(type) ? "被申请人（付款义务人）说明：" : "申请人（收款人）异议：") + content
                        + "。履行观察期重新计算至" + v.getObserveUntil() + "（第" + v.getRecomputeCount() + "次重算），请调解员根据双方反馈决定是否继续观察。",
                user, f.getFileName());
        return f;
    }

    /** 调解员根据双方反馈决定是否继续履行观察 */
    @Transactional
    public OverdueVisit handleFeedback(Long visitId, Map<String, Object> body, User user) {
        OverdueVisit v = visitRepository.findById(visitId).orElseThrow(() -> new BizException("逾期回访不存在"));
        if (user.getRole() != Role.MEDIATOR && user.getRole() != Role.ADMIN
                && user.getRole() != Role.EXECUTION && user.getRole() != Role.WINDOW) {
            throw new BizException("仅调解员可作出是否继续观察的决定");
        }
        String note = (String) body.getOrDefault("note", "");
        v.setMediatorHandledAt(LocalDateTime.now());
        boolean continueWatching = Boolean.TRUE.equals(body.get("continueWatching"));
        MediationCase c = caseRepository.findById(v.getCaseId()).orElseThrow();

        if (continueWatching) {
            if (body.get("observeDays") != null) {
                int days = Integer.parseInt(body.get("observeDays").toString());
                if (days < 1 || days > 90) throw new BizException("观察期长度应在1-90天之间");
                v.setObserveDays(days);
            }
            v.setObserveUntil(LocalDate.now().plusDays(v.getObserveDays()));
            v.setStatus("OBSERVING");
            visitRepository.save(v);
            c.setObservationActive(true);
            c.setObserveUntil(v.getObserveUntil());
            caseRepository.save(c);
            timelineService.record(v.getCaseId(), "OBSERVE", "调解员决定继续履行观察",
                    "综合双方反馈，继续观察至" + v.getObserveUntil() + "，督促付款义务人按期补足款项。" + note, user);
        } else {
            v.setStatus("CLOSED");
            visitRepository.save(v);
            boolean stillOverdue = planRepository.findByCaseIdOrderByInstallmentNo(v.getCaseId()).stream()
                    .anyMatch(p -> "OVERDUE".equals(p.getStatus()) || "PARTIAL".equals(p.getStatus()));
            c.setObservationActive(false);
            c.setObserveUntil(null);
            c.setStatus(stillOverdue ? CaseStatus.OVERDUE : CaseStatus.PERFORMING);
            caseRepository.save(c);
            timelineService.record(v.getCaseId(), "OBSERVE", "调解员结束履行观察",
                    "根据双方反馈结束本次履行观察。" + (stillOverdue ? "案件仍存在逾期款项，保持逾期预警，可由法官助理研判司法确认或执行线索。" : "逾期款项已处理。")
                            + note, user);
        }
        return v;
    }

    // ============================================================
    // 五、法官助理研判：司法确认 / 执行线索
    // ============================================================

    @Transactional
    public OverdueVisit assistantDecision(Long visitId, Map<String, Object> body, User user) {
        OverdueVisit v = visitRepository.findById(visitId).orElseThrow(() -> new BizException("逾期回访不存在"));
        if (user.getRole() != Role.ASSISTANT && user.getRole() != Role.ADMIN) {
            throw new BizException("仅法官助理可作出研判");
        }
        String decision = (String) body.getOrDefault("decision", "CONTINUE_WATCH");
        if (!"JUDICIAL_CONFIRM".equals(decision) && !"EXECUTION_CLUE".equals(decision) && !"CONTINUE_WATCH".equals(decision)) {
            throw new BizException("研判结论不正确");
        }
        String clue = (String) body.getOrDefault("executionClue", "");
        if ("EXECUTION_CLUE".equals(decision) && clue.isBlank()) {
            throw new BizException("移交后续执行线索时，请填写财产、住所或收入等执行线索");
        }
        v.setDecision(decision);
        v.setExecutionClue(clue);
        v.setDecisionNote((String) body.getOrDefault("note", ""));
        v.setAssistantId(user.getId());
        v.setAssistantName(user.getRealName());
        v.setDecidedAt(LocalDateTime.now());

        MediationCase c = caseRepository.findById(v.getCaseId()).orElseThrow();
        c.setOverdueDecision(decision);

        String title;
        String detail;
        switch (decision) {
            case "JUDICIAL_CONFIRM" -> {
                title = "法官助理研判：进入司法确认";
                detail = "经审查逾期回访与双方反馈，调解协议符合司法确认条件，决定转入司法确认审查（可在「司法确认/转立案」页生成申请书并报法官确认）。"
                        + v.getDecisionNote();
                v.setStatus("CLOSED");
                c.setObservationActive(false);
                c.setObserveUntil(null);
                c.setStatus(CaseStatus.JUDICIAL_CONFIRMING);
            }
            case "EXECUTION_CLUE" -> {
                title = "法官助理研判：移交后续执行线索";
                detail = "付款义务人经回访、观察仍未履行，已登记执行线索：" + v.getExecutionClue()
                        + "。可据此生成转立案材料包移交立案/执行程序。" + v.getDecisionNote();
            }
            default -> {
                title = "法官助理研判：继续履行观察";
                detail = "现有反馈表明存在履行可能，暂不进入司法确认或执行程序，继续履行观察。" + v.getDecisionNote();
            }
        }
        visitRepository.save(v);
        caseRepository.save(c);
        timelineService.record(v.getCaseId(), "DECISION", title, detail, user);
        return v;
    }

    /**
     * 定时检查观察期：观察期届满仍未结清的，标记届满并提醒研判。
     * 启动 50 秒后执行一次，之后每天执行。
     */
    @Scheduled(initialDelay = 50_000L, fixedDelay = 24L * 60 * 60 * 1000)
    @Transactional
    public void expireObservations() {
        LocalDate today = LocalDate.now();
        for (OverdueVisit v : visitRepository.findByStatus("OBSERVING")) {
            if (v.getObserveUntil() != null && v.getObserveUntil().isBefore(today)) {
                v.setStatus("EXPIRED");
                visitRepository.save(v);
                MediationCase c = caseRepository.findById(v.getCaseId()).orElse(null);
                if (c != null) {
                    c.setObservationActive(false);
                    caseRepository.save(c);
                }
                timelineService.record(v.getCaseId(), "OBSERVE", "履行观察期届满",
                        "第" + v.getInstallmentNo() + "期逾期回访后的履行观察期已于" + v.getObserveUntil()
                                + "届满，逾期款项仍未结清；请调解员视情况重新发起回访，或由法官助理研判是否进入司法确认/后续执行程序。", null);
            }
        }
    }

    /** 逾期款项已全部处理（付款结清）时自动结束在办回访与观察期，由付款确认流程调用 */
    @Transactional
    public void closeObservationIfResolved(Long caseId, User operator) {
        boolean stillOverdue = planRepository.findByCaseIdOrderByInstallmentNo(caseId).stream()
                .anyMatch(p -> "OVERDUE".equals(p.getStatus()) || "PARTIAL".equals(p.getStatus()));
        if (stillOverdue) return;
        List<OverdueVisit> active = visitRepository.findByCaseIdAndStatus(caseId, "OBSERVING");
        active.addAll(visitRepository.findByCaseIdAndStatus(caseId, "EXPIRED"));
        if (active.isEmpty()) return;
        MediationCase c = caseRepository.findById(caseId).orElse(null);
        for (OverdueVisit v : active) {
            v.setStatus("CLOSED");
            visitRepository.save(v);
        }
        if (c != null && Boolean.TRUE.equals(c.getObservationActive())) {
            c.setObservationActive(false);
            c.setObserveUntil(null);
            caseRepository.save(c);
            timelineService.record(caseId, "OBSERVE", "逾期款项已结清，履行观察结束",
                    "逾期节点均已足额到账，系统自动结束履行观察期。", operator);
        }
    }

    public static String wayLabel(String way) {
        return switch (way) {
            case "ONLINE" -> "线上";
            case "ONSITE" -> "上门";
            default -> "电话";
        };
    }

    public PerformanceFeedback mustGetFeedback(Long id) {
        return feedbackRepository.findById(id).orElseThrow(() -> new BizException("反馈记录不存在"));
    }
}
