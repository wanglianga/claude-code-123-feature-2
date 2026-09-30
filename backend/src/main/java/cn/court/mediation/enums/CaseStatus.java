package cn.court.mediation.enums;

/**
 * 案件全生命周期状态。
 * 调解与立案之间反复补材料的过程通过时间线事件保留，状态本身保持单向推进或回退留痕。
 */
public enum CaseStatus {
    DRAFT("待提交"),
    PENDING_TRIAGE("待分流"),
    TRIAGED("已分流待排期"),
    SCHEDULED("已排期待调解"),
    MEDIATING("调解中"),
    MEDIATION_SUCCESS("调解成功"),
    MEDIATION_FAILED("调解失败"),
    JUDICIAL_CONFIRMING("司法确认审查中"),
    PERFORMING("履行中"),
    PERFORMED_DONE("履行完毕"),
    OVERDUE("履行逾期"),
    OBSERVING("履行观察期"),
    TRANSFERRED("已转立案"),
    CLOSED("已归档");

    private final String label;

    CaseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
