package cn.court.mediation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 履行期当事人反馈：
 * PAYMENT_VOUCHER 付款凭证（付款方上传）/ NONPAYMENT_EXPLANATION 未付款说明（逾期回访后付款方说明）
 * / RECEIPT_OBJECTION 收款异议（收款方主张未收到或金额不符）。
 * 可携带付款凭证、说明等附件；逾期回访后提交的反馈可关联当次回访并据此重算履行观察期。
 */
@Getter
@Setter
@Entity
@Table(name = "performance_feedback", indexes = @Index(name = "idx_feedback_case", columnList = "caseId"))
public class PerformanceFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long caseId;
    /** 关联履行期次（可空，针对整案的反馈） */
    private Long planId;
    /** 逾期回访后提交时关联当次回访 */
    private Long visitId;

    private Long submitterId;
    @Column(length = 50)
    private String submitterName;
    /** 提交方在案件中的身份：APPLICANT 申请人 / RESPONDENT 被申请人 */
    @Column(length = 12)
    private String submitterSide;

    /** PAYMENT_VOUCHER / NONPAYMENT_EXPLANATION / RECEIPT_OBJECTION */
    @Column(length = 24)
    private String type;

    /** 凭证/说明涉及金额（付款凭证、异议金额时填写） */
    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 附件原始文件名（如转账回单、催款截图） */
    @Column(length = 255)
    private String attachmentName;
    /** 服务端存储路径 */
    @Column(length = 500)
    private String attachmentPath;

    /** 处理状态：SUBMITTED 已提交 / ACKNOWLEDGED 已采信（确认收款）/ DISPUTED 异议待核 */
    @Column(length = 12)
    private String status = "SUBMITTED";

    private LocalDateTime createdAt = LocalDateTime.now();
}
