package cn.court.mediation.repository;

import cn.court.mediation.entity.PerformancePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PerformancePlanRepository extends JpaRepository<PerformancePlan, Long> {
    List<PerformancePlan> findByCaseIdOrderByInstallmentNo(Long caseId);
    List<PerformancePlan> findByStatus(String status);
    List<PerformancePlan> findByStatusAndDueDateBefore(String status, LocalDate date);

    /** 按期提醒：到期日早于给定日期、尚未提醒、仍未结清的节点 */
    List<PerformancePlan> findByRemindedFalseAndStatusInAndDueDateBefore(
            List<String> statuses, LocalDate date);
}
