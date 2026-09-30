package cn.court.mediation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 后续执行线索：逾期回访后，法官助理判断需要进入后续执行程序时登记，
 * 记录财产/收入/担保人等可供执行的线索，供执行局跟进；也可标记为转司法确认。
 */
@Getter
@Setter
@Entity
@Table(name = "execution_clue", indexes = @Index(name = "idx_clue_case", columnList = "caseId"))
public class ExecutionClue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long caseId;
    /** 来源逾期回访（可空） */
    private Long visitId;

    private Long createdBy;
    @Column(length = 50)
    private String createdByName;

    /** 线索类型：PROPERTY 财产 / INCOME 收入 / GUARANTOR 担保人 / CONTACT 行踪联系方式 / OTHER 其他 */
    @Column(length = 12)
    private String clueType;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 处置去向：JUDICIAL_CONFIRM 申请司法确认 / EXECUTION 移送执行 / TRANSFER 转立案 / PENDING 待跟进 */
    @Column(length = 18)
    private String route;

    /** 处置状态：OPEN 待跟进 / FORWARDED 已移送 / DONE 已办结 */
    @Column(length = 12)
    private String status = "OPEN";

    @Lob
    @Column(columnDefinition = "TEXT")
    private String handleNote;
    private LocalDate handleDate;

    private LocalDateTime createdAt = LocalDateTime.now();
}
