package cn.court.mediation.repository;

import cn.court.mediation.entity.PerformancePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PerformancePlanRepository extends JpaRepository<PerformancePlan, Long> {
    List<PerformancePlan> findByCaseIdOrderByInstallmentNo(Long caseId);
    List<PerformancePlan> findByStatus(String status);
    List<PerformancePlan> findByStatusAndDueDateBefore(String status, LocalDate date);
    /** 到期提醒：未结清且到期日落在给定区间内（含端点） */
    List<PerformancePlan> findByStatusInAndDueDateBetween(List<String> statuses, LocalDate from, LocalDate to);
}
