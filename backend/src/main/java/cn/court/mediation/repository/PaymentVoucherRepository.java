package cn.court.mediation.repository;

import cn.court.mediation.entity.PaymentVoucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentVoucherRepository extends JpaRepository<PaymentVoucher, Long> {
    List<PaymentVoucher> findByCaseIdOrderByCreatedAtDesc(Long caseId);
    List<PaymentVoucher> findByPlanIdOrderByCreatedAtDesc(Long planId);
    List<PaymentVoucher> findByCaseIdAndStatusOrderByCreatedAtDesc(Long caseId, String status);
    List<PaymentVoucher> findByStatus(String status);
}
