package cn.court.mediation.repository;

import cn.court.mediation.entity.MediationCase;
import cn.court.mediation.enums.CaseStatus;
import cn.court.mediation.enums.CaseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CaseRepository extends JpaRepository<MediationCase, Long> {
    Optional<MediationCase> findByCaseNo(String caseNo);

    List<MediationCase> findByStatusIn(List<CaseStatus> statuses);

    /** 履行观察期届满扫描 */
    List<MediationCase> findByStatusAndObservationUntilBefore(CaseStatus status, java.time.LocalDate date);

    List<MediationCase> findByMediatorId(Long mediatorId);

    List<MediationCase> findByApplicantUserId(Long applicantUserId);

    long countByStatus(CaseStatus status);

    long countByCaseType(CaseType caseType);

    @Query("select count(c) from MediationCase c where c.status in :statuses")
    long countByStatusIn(@Param("statuses") List<CaseStatus> statuses);

    @Query("select count(c) from MediationCase c where c.respondentMissing = true and c.status not in :closed")
    long countMissingActive(@Param("closed") List<CaseStatus> closed);

    @Query("select count(c) from MediationCase c where c.evidenceComplete = false and c.status not in :closed")
    long countEvidenceGapActive(@Param("closed") List<CaseStatus> closed);

    List<MediationCase> findTop10ByOrderByCreatedAtDesc();
}
