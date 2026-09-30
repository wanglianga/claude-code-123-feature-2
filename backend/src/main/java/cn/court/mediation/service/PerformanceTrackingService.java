package cn.court.mediation.service;

import cn.court.mediation.common.BizException;
import cn.court.mediation.entity.*;
import cn.court.mediation.enums.CaseStatus;
import cn.court.mediation.enums.Role;
import cn.court.mediation.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 分期履行跟踪闭环：
 * 按期提醒付款 → 付款方上传凭证 → 收款方确认收款/提出收款异议 →
 * 某期逾期调解员发起回访 → 双方提交未付款说明/收款异议 → 调解员据反馈决定继续履行观察（观察期随反馈重新计算）→
 * 观察期届满未付恢复逾期 → 法官助理判断司法确认/后续执行线索。
 * 全过程写入案件时间线，法官助理可同步查看。
 */
@Service
public class PerformanceTrackingService {

    private final PerformancePlanRepository planRepository;
    private final PerformanceFeedbackRepository feedbackRepository;
    private final OverdueVisitRepository visitRepository;
    private final ExecutionClueRepository clueRepository;
    private final CaseRepository caseRepository;
    private final TimelineService timelineService;
    private final PerformanceService performanceService;
    private final AttachmentStorage attachmentStorage;

    /** 到期前 N 天开始按期提醒 */
    @Value("${app.performance.remind-days:3}")
    private int remindDays;
    /** 履行观察期默认天数（随反馈重新计算） */
    @Value("${app.performance.observe-days:7}")
    private int observeDays;

    public PerformanceTrackingService(PerformancePlanRepository planRepository,
                                      PerformanceFeedbackRepository feedbackRepository,
                                      OverdueVisitRepository visitRepository,
                                      ExecutionClueRepository clueRepository,
                                      CaseRepository caseRepository,
                                      TimelineService timelineService,
                                      PerformanceService performanceService,
                                      AttachmentStorage attachmentStorage) {
        this.planRepository = planRepository;
        this.feedbackRepository = feedbackRepository;
        this.visitRepository = visitRepository;
        this.clueRepository = clueRepository;
        this.caseRepository = caseRepository;
        this.timelineService = timelineService;
        this.performanceService = performanceService;
        this.attachmentStorage = attachmentStorage;
    }

    // ============================ 按期提醒 ============================

    /**
     * 每日按期提醒：到期日在未来 N 天内（含当日）、尚未提醒且未结清的节点，
     * 平台向付款方发出付款及上传凭证提醒并留痕。启动后 40 秒执行一次，之后每日 8:30。
     */
    @Scheduled(initialDelay = 40_000L, fixedDelay = 24L * 60 * 60 * 1000)
    @Transactional
    public int remindDueInstallments() {
        LocalDate today = LocalDate.now();
        List<PerformancePlan> due = planRepository.findByRemindedFalseAndStatusInAndDueDateBefore(
                List.of("PENDING", "PARTIAL"), today.plusDays(remindDays + 1L));
        for (PerformancePlan p : due) {
            p.setReminded(true);
            p.setRemindedAt(LocalDateTime.now());
            planRepository.save(p);
            boolean todayDue = !p.getDueDate().isAfter(today);
            String title = "第" + p.getInstallmentNo() + "期付款" + (todayDue ? "到期提醒" : "临期提醒");
            String detail = String.format(
                    "平台按期提醒：第%d期应于%s支付%s元（已付%s元）。请付款方按期付款并上传付款凭证，收款方收到后及时确认收款。",
                    p.getInstallmentNo(), p.getDueDate(), p.getDueAmount().toPlainString(),
                    p.getPaidAmount() == null ? "0" : p.getPaidAmount().toPlainString());
            timelineService.record(p.getCaseId(), "PERFORMANCE", title, detail, null);
        }
        return due.size();
    }

    /** 手动触发按期提醒（演示用），返回本次新提醒的节点数 */
    @Transactional
    public int runRemindNow() {
        return remindDueInstallments();
    }

    // ============================ 付款凭证 / 收款确认 / 异议 ============================

    /**
     * 当事人（付款方/收款方）或工作人员提交履行反馈：
     * PAYMENT_VOUCHER 付款凭证、NONPAYMENT_EXPLANATION 未付款说明、RECEIPT_OBJECTION 收款异议。
     * 观察期内提交的反馈会触发履行观察期重新计算。
     */
    @Transactional
    public PerformanceFeedback submitFeedback(Long caseId, Map<String, String> form, MultipartFile file, User operator) {
        MediationCase c = caseRepository.findById(caseId).orElseThrow(() -> new BizException("案件不存在"));
        String type = form.getOrDefault("type", "PAYMENT_VOUCHER");
        if (!List.of("PAYMENT_VOUCHER", "NONPAYMENT_EXPLANATION", "RECEIPT_OBJECTION").contains(type)) {
            throw new BizException("未知的反馈类型");
        }

        PerformanceFeedback f = new PerformanceFeedback();
        f.setCaseId(caseId);
        f.setType(type);
        f.setSubmitterId(operator.getId());
        f.setSubmitterName(operator.getRealName());
        f.setSubmitterSide(form.getOrDefault("side", defaultSide(c, operator)));
        if (form.get("planId") != null && !form.get("planId").isBlank()) {
            f.setPlanId(Long.valueOf(form.get("planId")));
        }
        if (form.get("visitId") != null && !form.get("visitId").isBlank()) {
            Long visitId = Long.valueOf(form.get("visitId"));
            visitRepository.findById(visitId).orElseThrow(() -> new BizException("关联的逾期回访不存在"));
            f.setVisitId(visitId);
        }
        if (form.get("amount") != null && !form.get("amount").isBlank()) {
            f.setAmount(new BigDecimal(form.get("amount")));
        }
        f.setContent(form.getOrDefault("content", ""));

        if (file != null && !file.isEmpty()) {
            String[] saved = attachmentStorage.store(file);
            f.setAttachmentName(saved[0]);
            f.setAttachmentPath(saved[1]);
        }
        if (type.equals("PAYMENT_VOUCHER") && f.getAttachmentName() == null
                && (f.getContent() == null || f.getContent().isBlank())) {
            throw new BizException("请上传付款凭证或填写付款说明（流水号/付款时间等）");
        }
        f.setStatus("RECEIPT_OBJECTION".equals(type) ? "DISPUTED" : "SUBMITTED");
        feedbackRepository.save(f);

        timelineService.record(caseId, "PERFORMANCE", feedbackTitle(type),
                buildFeedbackDetail(f), operator, f.getAttachmentName());

        // 履行观察期内收到双方反馈 → 观察期自反馈之日重新计算
        if (c.getStatus() == CaseStatus.OBSERVING) {
            recalculateObservation(c, "收到" + feedbackTypeLabel(type) + "，");
        }
        return f;
    }

    /** 收款方/工作人员依据付款凭证确认收款（等同于确认到账，支持部分到账），凭证标记已采信 */
    @Transactional
    public PerformanceFeedback confirmReceipt(Long feedbackId, Map<String, Object> body, User operator) {
        PerformanceFeedback f = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new BizException("反馈记录不存在"));
        if (!"PAYMENT_VOUCHER".equals(f.getType())) {
            throw new BizException("仅付款凭证可确认收款");
        }
        if (!"SUBMITTED".equals(f.getStatus())) {
            throw new BizException("该凭证已处理，请勿重复确认");
        }
        Long planId = f.getPlanId();
        if (planId == null) {
            throw new BizException("该凭证未关联具体期次，无法确认收款");
        }
        BigDecimal amount = body.get("paidAmount") != null
                ? new BigDecimal(body.get("paidAmount").toString())
                : (f.getAmount() != null ? f.getAmount() : BigDecimal.ZERO);
        String paidDate = body.get("paidDate") != null ? body.get("paidDate").toString()
                : LocalDate.now().toString();
        String note = "凭证「" + (f.getAttachmentName() == null ? "在线说明" : f.getAttachmentName()) + "」收款确认";

        // 复用履行到账确认逻辑（含部分履行/结清推进），保证案件状态一致
        performanceService.confirmPayment(planId, Map.of(
                "paidAmount", amount, "paidDate", paidDate, "note", note), operator);

        f.setStatus("ACKNOWLEDGED");
        feedbackRepository.save(f);
        timelineService.record(f.getCaseId(), "PERFORMANCE", "收款方确认收款",
                "付款方上传的" + (f.getAttachmentName() == null ? "付款说明" : "付款凭证「" + f.getAttachmentName() + "」")
                        + "已经收款方确认，到账" + amount.toPlainString() + "元并计入履行台账。", operator);
        return f;
    }

    // ============================ 逾期回访 ============================

    /** 某期逾期后，调解员发起逾期回访 */
    @Transactional
    public OverdueVisit createVisit(Long caseId, Map<String, Object> body, User operator) {
        MediationCase c = caseRepository.findById(caseId).orElseThrow(() -> new BizException("案件不存在"));
        boolean hasOverduePlan = planRepository.findByCaseIdOrderByInstallmentNo(caseId).stream()
                .anyMatch(p -> "OVERDUE".equals(p.getStatus()) || "PARTIAL".equals(p.getStatus()));
        if (c.getStatus() != CaseStatus.OVERDUE && c.getStatus() != CaseStatus.OBSERVING && !hasOverduePlan) {
            throw new BizException("当前没有逾期或部分未付清的期次，无需发起逾期回访");
        }
        if (operator.getRole() != Role.MEDIATOR && operator.getRole() != Role.ADMIN
                && operator.getRole() != Role.EXECUTION && operator.getRole() != Role.WINDOW) {
            throw new BizException("仅调解员可发起逾期回访");
        }

        OverdueVisit v = new OverdueVisit();
        v.setCaseId(caseId);
        v.setMediatorId(operator.getId());
        v.setMediatorName(operator.getRealName());
        v.setVisitDate(body.get("visitDate") != null ? LocalDate.parse(body.get("visitDate").toString()) : LocalDate.now());
        v.setWay((String) body.getOrDefault("way", "PHONE"));
        v.setNote((String) body.getOrDefault("note", ""));
        v.setSuggestion((String) body.getOrDefault("suggestion", "CONTINUE_OBSERVE"));
        v.setStatus("OPEN");
        if (body.get("planId") != null) {
            Long planId = Long.valueOf(body.get("planId").toString());
            PerformancePlan p = planRepository.findById(planId).orElse(null);
            v.setPlanId(planId);
            if (p != null) v.setInstallmentNo(p.getInstallmentNo());
        }
        visitRepository.save(v);

        timelineService.record(caseId, "OVERDUE", "调解员发起逾期回访",
                "回访日期：" + v.getVisitDate() + "；方式：" + wayLabel(v.getWay())
                        + "；逾期联系情况：" + (v.getNote().isBlank() ? "（待补充）" : v.getNote())
                        + "。回访后双方可提交未付款说明或收款异议，由调解员判断是否继续履行观察；法官助理同步查看。",
                operator);
        return v;
    }

    /** 调解员根据双方反馈决定是否继续履行观察；观察期自决定之日起算 */
    @Transactional
    public OverdueVisit decideObserve(Long visitId, Map<String, Object> body, User operator) {
        OverdueVisit v = mustVisit(visitId);
        MediationCase c = caseRepository.findById(v.getCaseId()).orElseThrow(() -> new BizException("案件不存在"));
        if (operator.getRole() != Role.MEDIATOR && operator.getRole() != Role.ADMIN
                && operator.getRole() != Role.EXECUTION && operator.getRole() != Role.WINDOW) {
            throw new BizException("仅调解员可根据反馈决定是否继续履行观察");
        }
        boolean continueObserve = Boolean.TRUE.equals(body.get("continueObserve"));
        int days = body.get("observeDays") != null ? Integer.parseInt(body.get("observeDays").toString()) : observeDays;
        String mediatorNote = (String) body.getOrDefault("note", "");

        if (!continueObserve) {
            // 不再观察：维持逾期，引导法官助理判断司法确认/执行线索或转立案
            v.setStatus("CLOSED");
            v.setNote(append(v.getNote(), "调解员决定不再继续履行观察：" + mediatorNote));
            visitRepository.save(v);
            timelineService.record(c.getId(), "OVERDUE", "逾期回访结束，未继续履行观察",
                    "调解员综合双方反馈认为不宜继续观察，案件维持逾期状态，转由法官助理判断是否司法确认/移送执行线索或转立案。"
                            + mediatorNote, operator);
            return v;
        }

        v.setStatus("OBSERVING");
        v.setSuggestion("CONTINUE_OBSERVE");
        v.setNote(append(v.getNote(), "调解员根据双方反馈决定继续履行观察（观察期" + days + "天）：" + mediatorNote));
        visitRepository.save(v);

        c.setStatus(CaseStatus.OBSERVING);
        c.setObservationVisitId(v.getId());
        c.setObservationUntil(LocalDate.now().plusDays(days));
        caseRepository.save(c);
        timelineService.record(c.getId(), "OVERDUE", "转入履行观察期",
                "调解员根据双方未付款说明/收款异议反馈，决定继续履行观察。观察期至" + c.getObservationUntil()
                        + "，期间当事人可继续反馈，观察期将随反馈重新计算；届满仍未履行的恢复逾期并移交法官助理判断。",
                operator);
        return v;
    }

    /** 观察期内随反馈重新计算观察期 */
    private void recalculateObservation(MediationCase c, String reasonPrefix) {
        LocalDate until = LocalDate.now().plusDays(observeDays);
        c.setObservationUntil(until);
        caseRepository.save(c);
        timelineService.record(c.getId(), "OVERDUE", "履行观察期重新计算",
                reasonPrefix + "履行观察期自反馈之日重新计算，延长至" + until + "。", null);
    }

    /**
     * 每日扫描观察期届满：观察期截止日已过、案件仍处观察期且未付清的，恢复逾期状态，
     * 并提示法官助理判断司法确认/执行线索。启动后 50 秒执行一次，之后每日 9:00。
     */
    @Scheduled(initialDelay = 50_000L, fixedDelay = 24L * 60 * 60 * 1000)
    @Transactional
    public void scanObservationExpiry() {
        LocalDate today = LocalDate.now();
        List<MediationCase> expired = caseRepository
                .findByStatusAndObservationUntilBefore(CaseStatus.OBSERVING, today);
        for (MediationCase c : expired) {
            boolean allPaid = !planRepository.findByCaseIdOrderByInstallmentNo(c.getId()).isEmpty()
                    && planRepository.findByCaseIdOrderByInstallmentNo(c.getId()).stream()
                    .allMatch(p -> "PAID".equals(p.getStatus()));
            if (allPaid) {
                c.setStatus(CaseStatus.PERFORMED_DONE);
                c.setObservationUntil(null);
                caseRepository.save(c);
                continue;
            }
            c.setStatus(CaseStatus.OVERDUE);
            c.setObservationUntil(null);
            caseRepository.save(c);
            for (OverdueVisit v : visitRepository.findByCaseIdOrderByCreatedAtDesc(c.getId())) {
                if ("OBSERVING".equals(v.getStatus()) || "OPEN".equals(v.getStatus())) {
                    v.setStatus("CLOSED");
                    visitRepository.save(v);
                }
            }
            timelineService.record(c.getId(), "OVERDUE", "履行观察期届满未履行，恢复逾期",
                    "观察期已于此前届满，当事人未足额履行。案件恢复履行逾期状态，请法官助理判断是否申请司法确认或移送后续执行线索，必要时转立案。",
                    null);
        }
    }

    @Transactional
    public int runObservationScanNow() {
        List<MediationCase> before = caseRepository.findByStatusIn(List.of(CaseStatus.OBSERVING));
        scanObservationExpiry();
        List<MediationCase> after = caseRepository.findByStatusIn(List.of(CaseStatus.OBSERVING));
        return Math.max(0, before.size() - after.size());
    }

    // ============================ 法官助理判断 / 执行线索 ============================

    /**
     * 法官助理同步查看逾期回访并判断处置：
     * CONTINUE 继续观察 / JUDICIAL 进入司法确认 / EXECUTION 移送执行线索 / TRANSFER 建议转立案。
     */
    @Transactional
    public OverdueVisit reviewByAssistant(Long visitId, Map<String, Object> body, User operator) {
        if (operator.getRole() != Role.ASSISTANT && operator.getRole() != Role.ADMIN) {
            throw new BizException("仅法官助理可作出司法确认/执行线索判断");
        }
        OverdueVisit v = mustVisit(visitId);
        String route = (String) body.getOrDefault("route", "CONTINUE");
        String opinion = (String) body.getOrDefault("opinion", "");
        v.setAssistantId(operator.getId());
        v.setAssistantName(operator.getRealName());
        v.setReviewedAt(LocalDateTime.now());
        v.setAssistantOpinion("处置判断：" + routeLabel(route) + "。" + opinion);
        MediationCase c = caseRepository.findById(v.getCaseId()).orElseThrow(() -> new BizException("案件不存在"));

        switch (route) {
            case "JUDICIAL" -> {
                v.setStatus("TO_JUDICIAL");
                c.setStatus(CaseStatus.JUDICIAL_CONFIRMING);
                caseRepository.save(c);
                timelineService.record(c.getId(), "TRANSFER", "法官助理判断：进入司法确认",
                        "法官助理同步查看逾期回访及双方反馈后，判断需要对调解协议申请司法确认。意见：" + opinion, operator);
            }
            case "EXECUTION" -> {
                v.setStatus("TO_EXECUTION");
                timelineService.record(c.getId(), "TRANSFER", "法官助理判断：移送后续执行线索",
                        "法官助理认为需进入后续执行程序，请登记财产/收入/担保人等执行线索移送执行局。意见：" + opinion,
                        operator);
                if (body.get("clueType") != null && body.get("clueContent") != null) {
                    registerClue(c.getId(), Map.of(
                            "clueType", body.get("clueType"),
                            "content", body.get("clueContent"),
                            "route", "EXECUTION",
                            "visitId", String.valueOf(v.getId())), operator);
                }
            }
            case "TRANSFER" -> {
                v.setStatus("CLOSED");
                timelineService.record(c.getId(), "TRANSFER", "法官助理判断：建议转立案",
                        "法官助理认为继续履行观察无实质意义，建议通过转立案材料包进入诉讼程序。意见：" + opinion, operator);
            }
            default -> timelineService.record(c.getId(), "FOLLOWUP", "法官助理同步查看逾期回访",
                    "法官助理已同步查看回访与双方反馈，暂维持履行跟踪/观察。意见：" + opinion, operator);
        }
        visitRepository.save(v);
        return v;
    }

    /** 登记后续执行线索（法官助理/执行联络） */
    @Transactional
    public ExecutionClue registerClue(Long caseId, Map<String, Object> body, User operator) {
        MediationCase c = caseRepository.findById(caseId).orElseThrow(() -> new BizException("案件不存在"));
        if (operator.getRole() != Role.ASSISTANT && operator.getRole() != Role.EXECUTION
                && operator.getRole() != Role.ADMIN && operator.getRole() != Role.WINDOW) {
            throw new BizException("仅法官助理/执行联络可登记执行线索");
        }
        ExecutionClue clue = new ExecutionClue();
        clue.setCaseId(caseId);
        clue.setCreatedBy(operator.getId());
        clue.setCreatedByName(operator.getRealName());
        clue.setClueType((String) body.getOrDefault("clueType", "OTHER"));
        clue.setContent((String) body.getOrDefault("content", ""));
        clue.setRoute((String) body.getOrDefault("route", "EXECUTION"));
        clue.setStatus("OPEN");
        if (body.get("visitId") != null && !body.get("visitId").toString().isBlank()) {
            clue.setVisitId(Long.valueOf(body.get("visitId").toString()));
        }
        if (clue.getContent().isBlank()) {
            throw new BizException("请填写执行线索内容");
        }
        clueRepository.save(clue);
        timelineService.record(caseId, "TRANSFER", "登记后续执行线索",
                "线索类型：" + clueTypeLabel(clue.getClueType()) + "；内容：" + clue.getContent()
                        + "；处置去向：" + routeLabel(clue.getRoute()) + "，供执行局跟进。", operator);
        return clue;
    }

    /** 执行联络将线索移送/办结 */
    @Transactional
    public ExecutionClue handleClue(Long clueId, Map<String, Object> body, User operator) {
        ExecutionClue clue = clueRepository.findById(clueId).orElseThrow(() -> new BizException("执行线索不存在"));
        clue.setStatus((String) body.getOrDefault("status", "FORWARDED"));
        clue.setHandleNote((String) body.getOrDefault("handleNote", ""));
        clue.setHandleDate(LocalDate.now());
        clueRepository.save(clue);
        timelineService.record(clue.getCaseId(), "TRANSFER",
                "FORWARDED".equals(clue.getStatus()) ? "执行线索已移送" : "执行线索办结",
                clue.getHandleNote(), operator);
        return clue;
    }

    // ============================ helpers ============================

    private OverdueVisit mustVisit(Long id) {
        return visitRepository.findById(id).orElseThrow(() -> new BizException("逾期回访记录不存在"));
    }

    private String defaultSide(MediationCase c, User operator) {
        if (operator.getRole() == Role.PARTY && operator.getId().equals(c.getApplicantUserId())) {
            return "APPLICANT";
        }
        return "RESPONDENT";
    }

    private String append(String origin, String add) {
        return (origin == null || origin.isBlank() ? "" : origin + "\n") + add;
    }

    private String buildFeedbackDetail(PerformanceFeedback f) {
        StringBuilder sb = new StringBuilder();
        sb.append("提交方：").append("APPLICANT".equals(f.getSubmitterSide()) ? "申请人" : "被申请人")
                .append("（").append(f.getSubmitterName()).append("）");
        if (f.getAmount() != null) sb.append("；金额：").append(f.getAmount().toPlainString()).append("元");
        if (f.getContent() != null && !f.getContent().isBlank()) sb.append("；说明：").append(f.getContent());
        if (f.getAttachmentName() != null) sb.append("；附件：").append(f.getAttachmentName());
        return sb.toString();
    }

    private String feedbackTitle(String type) {
        return switch (type) {
            case "PAYMENT_VOUCHER" -> "付款方上传付款凭证";
            case "NONPAYMENT_EXPLANATION" -> "付款方提交未付款说明";
            case "RECEIPT_OBJECTION" -> "收款方提出收款异议";
            default -> "履行反馈";
        };
    }

    private String feedbackTypeLabel(String type) {
        return switch (type) {
            case "PAYMENT_VOUCHER" -> "付款凭证";
            case "NONPAYMENT_EXPLANATION" -> "未付款说明";
            case "RECEIPT_OBJECTION" -> "收款异议";
            default -> "履行反馈";
        };
    }

    private String wayLabel(String way) {
        return switch (way == null ? "" : way) {
            case "ONLINE" -> "线上";
            case "ONSITE" -> "上门";
            default -> "电话";
        };
    }

    private String routeLabel(String route) {
        return switch (route == null ? "" : route) {
            case "JUDICIAL", "JUDICIAL_CONFIRM" -> "申请司法确认";
            case "EXECUTION" -> "移送执行";
            case "TRANSFER" -> "转立案";
            default -> "继续观察/跟踪";
        };
    }

    private String clueTypeLabel(String t) {
        return switch (t == null ? "" : t) {
            case "PROPERTY" -> "财产线索";
            case "INCOME" -> "收入线索";
            case "GUARANTOR" -> "担保人线索";
            case "CONTACT" -> "行踪/联系方式";
            default -> "其他线索";
        };
    }
}
