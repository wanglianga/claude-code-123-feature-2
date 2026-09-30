package cn.court.mediation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 付款/收款凭证：付款义务人上传转账凭证后，由收款人或执行联络核对并确认收款。
 * 演示环境文件以 Base64 文本入库（单文件 ≤10MB），生产环境替换为对象存储即可。
 */
@Getter
@Setter
@Entity
@Table(name = "payment_voucher", indexes = @Index(name = "idx_voucher_case", columnList = "caseId"))
public class PaymentVoucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long caseId;
    private Long planId;
    private Integer installmentNo;

    /** RESPONDENT 付款凭证（付款义务人上传）/ APPLICANT 收款凭证（收款人上传） */
    @Column(length = 12)
    private String partySide;

    private Long uploaderUserId;
    @Column(length = 50)
    private String uploaderName;
    @Column(length = 20)
    private String uploaderRole;

    @Column(length = 200)
    private String fileName;
    /** image/png、application/pdf 等，用于下载时还原类型 */
    @Column(length = 100)
    private String fileType;
    /** Base64 编码文件内容（不含 data: 前缀）；LONGTEXT 兼容 H2(MySQL模式)/MySQL，支持 10MB 级凭证 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String fileData;

    /** 凭证载明金额 */
    @Column(precision = 14, scale = 2)
    private BigDecimal amount;
    /** 凭证载明付款日期 */
    private LocalDate paidDate;

    @Column(length = 500)
    private String remark;

    /** PENDING 待核实收款 / CONFIRMED 已据此确认收款 / REJECTED 凭证不予采信 */
    @Column(length = 10)
    private String status = "PENDING";

    private LocalDateTime createdAt = LocalDateTime.now();
}
