package cn.court.mediation.repository;

import cn.court.mediation.entity.PerformanceFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerformanceFeedbackRepository extends JpaRepository<PerformanceFeedback, Long> {
    List<PerformanceFeedback> findByVisitIdOrderByCreatedAtAsc(Long visitId);
    List<PerformanceFeedback> findByCaseIdOrderByCreatedAtDesc(Long caseId);
}
