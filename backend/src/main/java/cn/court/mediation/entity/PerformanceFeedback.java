package cn.court.mediation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 逾期回访后当事人反馈：
 * RESPONDENT 上传未付款说明；APPLICANT 提出收款异议。
 * 调解员依据双方反馈决定是否继续履行观察，观察期随反馈重新起算。
 */
@Getter
@Setter
@Entity
@Table(name = "performance_feedback", indexes = @Index(name = "idx_feedback_visit", columnList = "visitId"))
public class PerformanceFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long caseId;
    private Long visitId;

    /** NON_PAYMENT 未付款说明（付款义务人）/ RECEIPT_OBJECTION 收款异议（收款人） */
    @Column(length = 20)
    private String type;

    private Long userId;
    @Column(length = 50)
    private String userName;
    @Column(length = 20)
    private String userRole;
    /** APPLICANT 申请人 / RESPONDENT 被申请人 */
    @Column(length = 12)
    private String partySide;

    @Column(length = 1000)
    private String content;

    /** 附件文件名（图片/转账截图/PDF，Base64 入库） */
    @Column(length = 200)
    private String fileName;
    @Column(length = 100)
    private String fileType;
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String fileData;

    private LocalDateTime createdAt = LocalDateTime.now();
}
