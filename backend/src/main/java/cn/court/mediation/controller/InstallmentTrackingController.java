package cn.court.mediation.controller;

import cn.court.mediation.common.ApiResponse;
import cn.court.mediation.entity.OverdueVisit;
import cn.court.mediation.entity.PaymentReminder;
import cn.court.mediation.entity.PaymentVoucher;
import cn.court.mediation.entity.PerformanceFeedback;
import cn.court.mediation.security.CurrentUser;
import cn.court.mediation.service.InstallmentTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 分期履行跟踪：按期提醒、付款凭证上传/收款确认、逾期回访、
 * 当事人未付款说明/收款异议、履行观察期重算、法官助理司法确认/执行线索研判。
 */
@RestController
@RequestMapping("/api")
public class InstallmentTrackingController {

    private final InstallmentTrackingService trackingService;

    public InstallmentTrackingController(InstallmentTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    /** 人工发送/补发某一期的提醒（付款/上传凭证/确认收款/逾期告知） */
    @PostMapping("/cases/{id}/reminders/send")
    public ApiResponse<PaymentReminder> sendReminder(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(trackingService.sendReminderNow(id, body, CurrentUser.get(request)));
    }

    /** 当事人上传付款/收款凭证（文件以 Base64 文本提交） */
    @PostMapping("/cases/{id}/vouchers")
    public ApiResponse<PaymentVoucher> uploadVoucher(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        PaymentVoucher v = trackingService.uploadVoucher(id, body, CurrentUser.get(request));
        v.setFileData(null);
        return ApiResponse.ok(v);
    }

    /** 凭证驳回（不予采信） */
    @PostMapping("/vouchers/{id}/reject")
    public ApiResponse<PaymentVoucher> rejectVoucher(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(trackingService.rejectVoucher(id, (String) body.get("note"), CurrentUser.get(request)));
    }

    /** 下载凭证原文件 */
    @GetMapping("/vouchers/{id}/file")
    public ResponseEntity<byte[]> voucherFile(@PathVariable Long id) {
        PaymentVoucher v = trackingService.mustGetVoucher(id);
        byte[] bytes = Base64.getDecoder().decode(v.getFileData());
        String filename = URLEncoder.encode(v.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        MediaType type;
        try {
            type = MediaType.parseMediaType(v.getFileType() == null ? "application/octet-stream" : v.getFileType());
        } catch (Exception ex) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + filename)
                .contentType(type)
                .body(bytes);
    }

    /** 调解员对逾期期次发起回访，案件进入履行观察期 */
    @PostMapping("/cases/{id}/overdue-visits")
    public ApiResponse<OverdueVisit> createVisit(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                 HttpServletRequest request) {
        return ApiResponse.ok(trackingService.createVisit(id, body, CurrentUser.get(request)));
    }

    /** 逾期回访后当事人反馈：未付款说明 / 收款异议（观察期重新计算） */
    @PostMapping("/overdue-visits/{id}/feedback")
    public ApiResponse<PerformanceFeedback> feedback(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(trackingService.addFeedback(id, body, CurrentUser.get(request)));
    }

    /** 调解员根据双方反馈决定继续观察或结束观察 */
    @PostMapping("/overdue-visits/{id}/handle")
    public ApiResponse<OverdueVisit> handle(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        return ApiResponse.ok(trackingService.handleFeedback(id, body, CurrentUser.get(request)));
    }

    /** 法官助理研判：司法确认 / 移交执行线索 / 继续观察 */
    @PostMapping("/overdue-visits/{id}/decision")
    public ApiResponse<OverdueVisit> decision(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                             HttpServletRequest request) {
        return ApiResponse.ok(trackingService.assistantDecision(id, body, CurrentUser.get(request)));
    }

    /** 下载当事人反馈（未付款说明/收款异议）附件 */
    @GetMapping("/feedback/{id}/file")
    public ResponseEntity<byte[]> feedbackFile(@PathVariable Long id) {
        PerformanceFeedback f = trackingService.mustGetFeedback(id);
        if (f.getFileData() == null || f.getFileData().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = Base64.getDecoder().decode(f.getFileData());
        String filename = URLEncoder.encode(f.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        MediaType type;
        try {
            type = MediaType.parseMediaType(f.getFileType() == null ? "application/octet-stream" : f.getFileType());
        } catch (Exception ex) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + filename)
                .contentType(type)
                .body(bytes);
    }
}
