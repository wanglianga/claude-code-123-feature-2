package cn.court.mediation.repository;

import cn.court.mediation.entity.PaymentReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PaymentReminderRepository extends JpaRepository<PaymentReminder, Long> {
    List<PaymentReminder> findByCaseIdOrderByPlannedDateAscIdAsc(Long caseId);
    List<PaymentReminder> findByStatusAndPlannedDateBefore(String status, LocalDate date);
    List<PaymentReminder> findByCaseIdAndPlanIdAndStageTypeAndStatus(Long caseId, Long planId, String stageType, String status);
    List<PaymentReminder> findByCaseIdAndPlanIdAndStageType(Long caseId, Long planId, String stageType);
    List<PaymentReminder> findByCaseIdAndStageTypeAndStatus(Long caseId, String stageType, String status);
}
