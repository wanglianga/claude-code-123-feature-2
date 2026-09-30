package cn.court.mediation.controller;

import cn.court.mediation.common.ApiResponse;
import cn.court.mediation.common.BizException;
import cn.court.mediation.entity.ExecutionClue;
import cn.court.mediation.entity.OverdueVisit;
import cn.court.mediation.entity.PerformanceFeedback;
import cn.court.mediation.repository.PerformanceFeedbackRepository;
import cn.court.mediation.security.CurrentUser;
import cn.court.mediation.service.AttachmentStorage;
import cn.court.mediation.service.PerformanceTrackingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/**
 * 分期履行跟踪闭环接口：
 * 按期提醒、付款凭证上传、收款确认/异议、逾期回访、继续履行观察（重算）、
 * 法官助理司法确认/执行线索判断、执行线索登记与移送。
 */
@RestController
@RequestMapping("/api")
public class PerformanceTrackingController {

    private final PerformanceTrackingService trackingService;
    private final PerformanceFeedbackRepository feedbackRepository;
    private final AttachmentStorage attachmentStorage;

    public PerformanceTrackingController(PerformanceTrackingService trackingService,
                                         PerformanceFeedbackRepository feedbackRepository,
                                         AttachmentStorage attachmentStorage) {
        this.trackingService = trackingService;
        this.feedbackRepository = feedbackRepository;
        this.attachmentStorage = attachmentStorage;
    }

    /** 当事人提交付款凭证 / 未付款说明 / 收款异议（支持附件上传） */
    @PostMapping("/cases/{caseId}/performance/feedback")
    public ApiResponse<PerformanceFeedback> feedback(
            @PathVariable Long caseId,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) String planId,
            @RequestParam(required = false) String visitId,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String content,
            HttpServletRequest request) {
        Map<String, String> form = new java.util.HashMap<>();
        putIfNotNull(form, "type", type);
        putIfNotNull(form, "side", side);
        putIfNotNull(form, "planId", planId);
        putIfNotNull(form, "visitId", visitId);
        putIfNotNull(form, "amount", amount);
        putIfNotNull(form, "content", content);
        return ApiResponse.ok(trackingService.submitFeedback(caseId, form, file, CurrentUser.get(request)));
    }

    /** 收款方/工作人员依据凭证确认收款（计入履行台账，支持部分到账） */
    @PostMapping("/performance/feedbacks/{feedbackId}/confirm-receipt")
    public ApiResponse<PerformanceFeedback> confirmReceipt(@PathVariable Long feedbackId,
                                                           @RequestBody Map<String, Object> body,
                                                           HttpServletRequest request) {
        return ApiResponse.ok(trackingService.confirmReceipt(feedbackId, body, CurrentUser.get(request)));
    }

    /** 调解员发起逾期回访 */
    @PostMapping("/cases/{caseId}/overdue-visits")
    public ApiResponse<OverdueVisit> createVisit(@PathVariable Long caseId,
                                                 @RequestBody Map<String, Object> body,
                                                 HttpServletRequest request) {
        return ApiResponse.ok(trackingService.createVisit(caseId, body, CurrentUser.get(request)));
    }

    /** 调解员根据双方反馈决定是否继续履行观察（观察期重新计算） */
    @PostMapping("/overdue-visits/{visitId}/observe")
    public ApiResponse<OverdueVisit> decideObserve(@PathVariable Long visitId,
                                                   @RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(trackingService.decideObserve(visitId, body, CurrentUser.get(request)));
    }

    /** 法官助理同步查看并判断司法确认/执行线索/转立案 */
    @PostMapping("/overdue-visits/{visitId}/assistant-review")
    public ApiResponse<OverdueVisit> assistantReview(@PathVariable Long visitId,
                                                     @RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(trackingService.reviewByAssistant(visitId, body, CurrentUser.get(request)));
    }

    /** 登记后续执行线索 */
    @PostMapping("/cases/{caseId}/execution-clues")
    public ApiResponse<ExecutionClue> registerClue(@PathVariable Long caseId,
                                                   @RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        return ApiResponse.ok(trackingService.registerClue(caseId, body, CurrentUser.get(request)));
    }

    /** 执行联络移送/办结执行线索 */
    @PostMapping("/execution-clues/{clueId}/handle")
    public ApiResponse<ExecutionClue> handleClue(@PathVariable Long clueId,
                                                 @RequestBody Map<String, Object> body,
                                                 HttpServletRequest request) {
        return ApiResponse.ok(trackingService.handleClue(clueId, body, CurrentUser.get(request)));
    }

    /** 手动触发按期提醒（演示用） */
    @PostMapping("/performance/remind-now")
    public ApiResponse<Map<String, Object>> remindNow() {
        int reminded = trackingService.runRemindNow();
        return ApiResponse.ok(Map.of("remindedTotal", reminded));
    }

    /** 手动触发履行观察期届满扫描（演示用） */
    @PostMapping("/observation/scan")
    public ApiResponse<Map<String, Object>> observationScan() {
        int changed = trackingService.runObservationScanNow();
        return ApiResponse.ok(Map.of("expired", changed));
    }

    /** 下载付款凭证/异议材料附件（需登录） */
    @GetMapping("/attachments/{feedbackId}")
    public ResponseEntity<byte[]> download(@PathVariable Long feedbackId) {
        PerformanceFeedback f = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new BizException("反馈记录不存在"));
        if (f.getAttachmentPath() == null) {
            throw new BizException("该反馈没有附件");
        }
        Path file = attachmentStorage.resolve(f.getAttachmentPath());
        String filename = URLEncoder.encode(
                f.getAttachmentName() == null ? "附件" : f.getAttachmentName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String contentType = contentTypeOf(f.getAttachmentName());
        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(java.nio.file.Files.readAllBytes(file));
        } catch (Exception e) {
            throw new BizException("附件读取失败");
        }
    }

    private void putIfNotNull(Map<String, String> map, String key, String value) {
        if (value != null) map.put(key, value);
    }

    private String contentTypeOf(String name) {
        if (name == null) return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String n = name.toLowerCase();
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".txt")) return "text/plain;charset=UTF-8";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
