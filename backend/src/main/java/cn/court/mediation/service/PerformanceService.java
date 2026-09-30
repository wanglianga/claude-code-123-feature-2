package cn.court.mediation.service;

import cn.court.mediation.common.BizException;
import cn.court.mediation.entity.MediationAgreement;
import cn.court.mediation.entity.MediationCase;
import cn.court.mediation.entity.PaymentVoucher;
import cn.court.mediation.entity.PerformancePlan;
import cn.court.mediation.enums.CaseStatus;
import cn.court.mediation.repository.AgreementRepository;
import cn.court.mediation.repository.CaseRepository;
import cn.court.mediation.repository.PaymentReminderRepository;
import cn.court.mediation.repository.PaymentVoucherRepository;
import cn.court.mediation.repository.PerformancePlanRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PerformanceService {

    private final PerformancePlanRepository planRepository;
    private final AgreementRepository agreementRepository;
    private final CaseRepository caseRepository;
    private final TimelineService timelineService;
    private final PaymentVoucherRepository voucherRepository;
    private final PaymentReminderRepository reminderRepository;
    private final InstallmentTrackingService trackingService;

    public PerformanceService(PerformancePlanRepository planRepository, AgreementRepository agreementRepository,
                              CaseRepository caseRepository, TimelineService timelineService,
                              PaymentVoucherRepository voucherRepository, PaymentReminderRepository reminderRepository,
                              InstallmentTrackingService trackingService) {
        this.planRepository = planRepository;
        this.agreementRepository = agreementRepository;
        this.caseRepository = caseRepository;
        this.timelineService = timelineService;
        this.voucherRepository = voucherRepository;
        this.reminderRepository = reminderRepository;
        this.trackingService = trackingService;
    }

    /** 根据协议生成/重建履行节点（一次性=1期，分期按传入明细生成） */
    @Transactional
    public List<PerformancePlan> generatePlans(Long caseId, Map<String, Object> body, cn.court.mediation.entity.User operator) {
        MediationCase c = caseRepository.findById(caseId).orElseThrow(() -> new BizException("案件不存在"));
        MediationAgreement a = agreementRepository.findByCaseId(caseId)
                .orElseThrow(() -> new BizException("请先起草并签署协议"));
        if (a.getEffectiveAt() == null) {
            throw new BizException("协议尚未双方签署生效");
        }

        List<PerformancePlan> old = planRepository.findByCaseIdOrderByInstallmentNo(caseId);
        boolean hasPaid = old.stream().anyMatch(p -> p.getPaidAmount() != null && p.getPaidAmount().compareTo(BigDecimal.ZERO) > 0);
        if (hasPaid) {
            throw new BizException("已有履行记录，不能重建付款计划");
        }
        planRepository.deleteAll(old);

        List<PerformancePlan> plans = new ArrayList<>();
        if (Boolean.TRUE.equals(a.getInstallment()) && body.get("installments") != null) {
            List<?> list = (List<?>) body.get("installments");
            int no = 1;
            for (Object item : list) {
                Map<?, ?> m = (Map<?, ?>) item;
                PerformancePlan p = new PerformancePlan();
                p.setCaseId(caseId);
                p.setInstallmentNo(no++);
                p.setDueAmount(new BigDecimal(m.get("dueAmount").toString()));
                p.setDueDate(LocalDate.parse(m.get("dueDate").toString()));
                p.setStatus("PENDING");
                p.setPaidAmount(BigDecimal.ZERO);
                plans.add(planRepository.save(p));
            }
        } else {
            PerformancePlan p = new PerformancePlan();
            p.setCaseId(caseId);
            p.setInstallmentNo(1);
            p.setDueAmount(a.getTotalAmount());
            p.setDueDate(a.getDeadline() == null ? LocalDate.now().plusDays(30) : a.getDeadline());
            p.setStatus("PENDING");
            p.setPaidAmount(BigDecimal.ZERO);
            plans.add(planRepository.save(p));
        }

        c.setStatus(CaseStatus.PERFORMING);
        caseRepository.save(c);
        timelineService.record(caseId, "PERFORMANCE", "履行付款计划已建立",
                "共" + plans.size() + "期，首期应于" + plans.get(0).getDueDate() + "履行。平台将按期提醒付款、上传凭证并提示确认收款。", operator);

        // 按每一期排定付款/上传凭证/确认收款提醒
        trackingService.initPlanReminders(caseId, plans);
        return plans;
    }

    /** 确认到账：支持全额/部分；可依据当事人上传的付款凭证确认；部分履行与逾期均留痕 */
    @Transactional
    public PerformancePlan confirmPayment(Long planId, Map<String, Object> body, cn.court.mediation.entity.User operator) {
        PerformancePlan p = planRepository.findById(planId).orElseThrow(() -> new BizException("履行节点不存在"));
        BigDecimal paid = new BigDecimal(body.get("paidAmount").toString());
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("确认金额必须大于0");
        }
        String note = (String) body.getOrDefault("note", "");
        LocalDate paidDate = body.get("paidDate") != null ? LocalDate.parse(body.get("paidDate").toString()) : LocalDate.now();

        // 可关联当事人上传的付款凭证
        if (body.get("voucherId") != null && !body.get("voucherId").toString().isBlank()) {
            Long voucherId = Long.valueOf(body.get("voucherId").toString());
            PaymentVoucher v = voucherRepository.findById(voucherId)
                    .orElseThrow(() -> new BizException("付款凭证不存在"));
            if (!v.getCaseId().equals(p.getCaseId()) || (v.getPlanId() != null && !v.getPlanId().equals(planId))) {
                throw new BizException("凭证与本期次不匹配");
            }
            if ("REJECTED".equals(v.getStatus())) {
                throw new BizException("该凭证已被驳回，不能作为收款依据");
            }
            v.setStatus("CONFIRMED");
            voucherRepository.save(v);
            p.setVoucherId(voucherId);
        }

        BigDecimal newTotal = (p.getPaidAmount() == null ? BigDecimal.ZERO : p.getPaidAmount()).add(paid);
        p.setPaidAmount(newTotal);
        p.setPaidDate(paidDate);
        p.setConfirmedBy(operator.getId());
        p.setConfirmedAt(LocalDateTime.now());
        int cmp = newTotal.compareTo(p.getDueAmount());
        if (cmp >= 0) {
            p.setStatus("PAID");
        } else {
            p.setStatus("PARTIAL");
        }
        if (!note.isBlank()) {
            p.setNote(note);
        }
        planRepository.save(p);

        // 收款已确认：该节点「确认收款提醒」闭环
        reminderRepository.findByCaseIdAndPlanIdAndStageType(p.getCaseId(), planId, "CONFIRM_RECEIPT")
                .forEach(r -> { if ("PLANNED".equals(r.getStatus())) { r.setStatus("SENT"); r.setSentAt(LocalDateTime.now()); reminderRepository.save(r); } });

        MediationCase c = caseRepository.findById(p.getCaseId()).orElseThrow();
        List<PerformancePlan> all = planRepository.findByCaseIdOrderByInstallmentNo(p.getCaseId());
        boolean allPaid = all.stream().allMatch(x -> "PAID".equals(x.getStatus()));
        String title = "第" + p.getInstallmentNo() + "期" + (cmp >= 0 ? "足额履行" : "部分履行");
        String detail = String.format("本期应收%s元，本次到账%s元，累计%s元；%s",
                p.getDueAmount().toPlainString(), paid.toPlainString(), newTotal.toPlainString(),
                cmp >= 0 ? "本期已结清。" : "尚欠" + p.getDueAmount().subtract(newTotal).toPlainString() + "元，继续跟踪。");
        if (allPaid) {
            c.setStatus(CaseStatus.PERFORMED_DONE);
            caseRepository.save(c);
            detail += "全部期次履行完毕。";
        }
        timelineService.record(p.getCaseId(), "PERFORMANCE", title, detail
                + (p.getVoucherId() == null ? "" : "本次确认依据当事人上传的付款凭证。"), operator);

        // 逾期款项全部处理后自动结束履行观察
        trackingService.closeObservationIfResolved(p.getCaseId(), operator);
        return p;
    }

    /**
     * 每日扫描：到期未付/未付清的节点置为 OVERDUE，案件进入履行逾期状态并预警留痕。
     * 启动后 30 秒执行一次，之后每天 8 点执行。
     */
    @Scheduled(initialDelay = 30_000L, fixedDelay = 24L * 60 * 60 * 1000)
    @Transactional
    public void scanOverdue() {
        LocalDate today = LocalDate.now();
        List<PerformancePlan> overdue = planRepository.findByStatusAndDueDateBefore("PENDING", today);
        overdue.addAll(planRepository.findByStatusAndDueDateBefore("PARTIAL", today));
        for (PerformancePlan p : overdue) {
            p.setStatus("OVERDUE");
            planRepository.save(p);
            MediationCase c = caseRepository.findById(p.getCaseId()).orElse(null);
            if (c != null && c.getStatus() != CaseStatus.TRANSFERRED && c.getStatus() != CaseStatus.CLOSED) {
                c.setStatus(CaseStatus.OVERDUE);
                caseRepository.save(c);
                timelineService.record(p.getCaseId(), "OVERDUE",
                        "第" + p.getInstallmentNo() + "期付款逾期",
                        String.format("应于%s支付%s元，截至%s未足额到账（已付%s元），平台自动预警并通知执行联络人。",
                                p.getDueDate(), p.getDueAmount().toPlainString(), today,
                                p.getPaidAmount() == null ? "0" : p.getPaidAmount().toPlainString()),
                        null);
                // 发送逾期告知，并提示调解员可发起回访
                trackingService.sendOverdueNotice(p);
            }
        }
    }

    /** 手动触发逾期检查（演示用，避免等待定时任务） */
    @Transactional
    public int runOverdueScanNow() {
        int before = planRepository.findByStatus("OVERDUE").size();
        scanOverdue();
        return planRepository.findByStatus("OVERDUE").size() - before;
    }
}
