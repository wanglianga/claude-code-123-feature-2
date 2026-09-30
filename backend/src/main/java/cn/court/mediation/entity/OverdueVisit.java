package cn.court.mediation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 逾期回访：某一期逾期后由调解员发起。
 * 回访后双方可提交"未付款说明 / 收款异议"，调解员据反馈决定是否继续履行观察；
 * 法官助理同步查看并判断是否需要进入司法确认或后续执行线索。
 */
@Getter
@Setter
@Entity
@Table(name = "overdue_visit", indexes = @Index(name = "idx_visit_case", columnList = "caseId"))
public class OverdueVisit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long caseId;
    /** 触发回访的逾期期次 */
    private Long planId;
    private Integer installmentNo;

    private Long mediatorId;
    @Column(length = 50)
    private String mediatorName;

    private LocalDate visitDate;

    /** 回访方式：PHONE 电话 / ONLINE 线上 / ONSITE 上门 */
    @Column(length = 10)
    private String way = "PHONE";

    /** 逾期原因/联系情况 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String note;

    /** 回访结论（调解员初步判断）：CONTINUE_OBSERVE 继续观察 / JUDICIAL 建议司法确认 / EXECUTION 建议执行线索 / TRANSFER 建议转立案 */
    @Column(length = 20)
    private String suggestion;

    /**
     * 处置状态：
     * OPEN 待双方反馈 / OBSERVING 已转履行观察期 / TO_JUDICIAL 已送司法确认
     * / TO_EXECUTION 已生成执行线索 / CLOSED 已关闭
     */
    @Column(length = 16)
    private String status = "OPEN";

    /** 法官助理同步查看后意见 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String assistantOpinion;
    private Long assistantId;
    @Column(length = 50)
    private String assistantName;
    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
