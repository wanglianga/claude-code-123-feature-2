package cn.court.mediation.repository;

import cn.court.mediation.entity.ExecutionClue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionClueRepository extends JpaRepository<ExecutionClue, Long> {
    List<ExecutionClue> findByCaseIdOrderByCreatedAtDesc(Long caseId);
}
