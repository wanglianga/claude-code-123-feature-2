package cn.court.mediation.repository;

import cn.court.mediation.entity.OverdueVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OverdueVisitRepository extends JpaRepository<OverdueVisit, Long> {
    List<OverdueVisit> findByCaseIdOrderByVisitDateDescIdDesc(Long caseId);
    List<OverdueVisit> findByCaseIdAndStatus(Long caseId, String status);
    List<OverdueVisit> findByStatus(String status);
}
