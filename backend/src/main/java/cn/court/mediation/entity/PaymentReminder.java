package cn.court.mediation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 分期履行提醒：分期付款协议达成后，平台按每一期的履行节奏
 * 提醒付款、上传付款凭证、确认收款；逾期时发送逾期告知。
 */
@Getter
@Setter
@Entity
@Table(name = "payment_reminder", indexes = @Index(name = "idx_reminder_case", columnList = "caseId"))
public class PaymentReminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long caseId;
    /** 关联履行节点（计划级提醒可为空） */
    private Long planId;
    private Integer installmentNo;

    /**
     * 提醒环节：
     * BEFORE_DUE 按期付款提醒 / UPLOAD_VOUCHER 上传付款凭证提醒 /
     * CONFIRM_RECEIPT 确认收款提醒 / OVERDUE_NOTICE 逾期告知
     */
    @Column(length = 20)
    private String stageType;

    /** 提醒对象：RESPONDENT 付款义务人（一般为被申请人）/ APPLICANT 收款人（申请人） */
    @Column(length = 12)
    private String targetParty;

    /** SMS 短信 / ONLINE 站内 / EMAIL 邮件 */
    @Column(length = 10)
    private String channel = "SMS";

    /** 计划提醒日期（到期前提醒=到期日前3天） */
    private LocalDate plannedDate;
    /** 实际发送时间 */
    private LocalDateTime sentAt;

    /** PLANNED 待发送 / SENT 已发送 */
    @Column(length = 10)
    private String status = "PLANNED";

    @Column(length = 500)
    private String content;

    /** 发送人：为空表示平台系统自动发送 */
    private Long operatorId;
    @Column(length = 50)
    private String operatorName;

    private LocalDateTime createdAt = LocalDateTime.now();
}
