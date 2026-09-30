package cn.court.mediation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 履行节点（一次性付款为一行；分期为多行） */
@Getter
@Setter
@Entity
@Table(name = "performance_plan")
public class PerformancePlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long caseId;

    /** 期次：1,2,3... */
    private Integer installmentNo;

    /** 应付金额 */
    @Column(precision = 14, scale = 2)
    private BigDecimal dueAmount;

    private LocalDate dueDate;

    /** 状态：PENDING 待履行 / PAID 已履行 / PARTIAL 部分履行 / OVERDUE 已逾期 */
    @Column(length = 10)
    private String status = "PENDING";

    /** 实付金额 */
    @Column(precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    private LocalDate paidDate;

    /** 付款凭证/备注 */
    @Column(length = 500)
    private String note;

    /** 确认收款所依据的付款凭证 ID（当事人上传、执行联络核验收款） */
    private Long voucherId;

    /** 确认人（执行联络/调解员） */
    private Long confirmedBy;
    private LocalDateTime confirmedAt = LocalDateTime.now();
}
