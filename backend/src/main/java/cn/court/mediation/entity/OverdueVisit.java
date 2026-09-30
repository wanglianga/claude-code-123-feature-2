package cn.court.mediation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 逾期回访：某一期逾期后由调解员发起回访，法官助理同步查看并研判
 * 是否需要申请司法确认或移交后续执行线索。
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
    /** 触发回访的逾期节点（可空，表示针对整体逾期情况） */
    private Long planId;
    private Integer installmentNo;

    /** PHONE 电话 / ONLINE 线上 / ONSITE 上门 */
    @Column(length = 10)
    private String way = "PHONE";

    private LocalDate visitDate;

    /** 回访情况（联系人、双方态度、付款意愿、困难原因等） */
    @Column(length = 1000)
    private String content;

    // ---------- 发起：调解员 ----------
    private Long mediatorId;
    @Column(length = 50)
    private String mediatorName;

    // ---------- 法官助理研判 ----------
    /** NONE 待研判 / JUDICIAL_CONFIRM 建议司法确认 / EXECUTION_CLUE 执行线索 / CONTINUE_WATCH 继续履行观察 */
    @Column(length = 20)
    private String decision = "NONE";
    /** 执行线索：财产/住所/工作单位/收入/联系方式等 */
    @Column(length = 1000)
    private String executionClue;
    @Column(length = 1000)
    private String decisionNote;

    private Long assistantId;
    @Column(length = 50)
    private String assistantName;
    private LocalDateTime decidedAt;

    /** OBSERVING 履行观察中 / CLOSED 已结束观察（付款结清或转入司法/执行程序） */
    @Column(length = 10)
    private String status = "OBSERVING";
    /** 履行观察期截止日：每次收到双方反馈后重新计算 */
    private LocalDate observeUntil;
    /** 观察期长度（天），默认 15 天 */
    private Integer observeDays = 15;
    /** 已重新计算次数（随反馈滚动） */
    private Integer recomputeCount = 0;

    /** 最近一次当事人反馈时间 */
    private LocalDateTime lastFeedbackAt;
    /** 调解员最近一次处理反馈时间（早于 lastFeedbackAt 表示有新反馈待处理） */
    private LocalDateTime mediatorHandledAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
