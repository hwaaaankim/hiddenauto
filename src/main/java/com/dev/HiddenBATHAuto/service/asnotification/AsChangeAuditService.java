package com.dev.HiddenBATHAuto.service.asnotification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.enums.notification.AsNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationSourceArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.AsImage;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.AsTaskSchedule;
import com.dev.HiddenBATHAuto.model.task.as.AsVideo;
import com.dev.HiddenBATHAuto.model.task.as.audit.AsChangeEvent;
import com.dev.HiddenBATHAuto.model.task.as.audit.AsChangeField;
import com.dev.HiddenBATHAuto.repository.as.AsChangeEventRepository;
import com.dev.HiddenBATHAuto.repository.as.AsImageRepository;
import com.dev.HiddenBATHAuto.repository.as.AsTaskScheduleRepository;
import com.dev.HiddenBATHAuto.repository.as.AsVideoRepository;

import lombok.RequiredArgsConstructor;

/**
 * AS 전용 감사이력 진입점입니다.
 * 기존 AsHistory와 섞지 않고, 사용자/관리팀/AS팀의 실제 행위를 정책 제어 가능한 이벤트로 분리합니다.
 */
@Service
@RequiredArgsConstructor
public class AsChangeAuditService {

    private final AsChangeEventRepository eventRepository;
    private final AsTaskScheduleRepository scheduleRepository;
    private final AsImageRepository imageRepository;
    private final AsVideoRepository videoRepository;
    private final AsNotificationService notificationService;

    @Transactional(readOnly = true)
    public Snapshot capture(AsTask task) {
        if (task == null) return Snapshot.empty();
        LocalDate visitDate = task.getId() == null ? null
                : scheduleRepository.findByAsTaskId(task.getId()).map(AsTaskSchedule::getScheduledDate).orElse(null);
        List<AsImage> images = task.getId() == null
                ? (task.getImages() == null ? List.of() : task.getImages())
                : imageRepository.findAllByAsTask_Id(task.getId());
        List<AsVideo> videos = task.getId() == null
                ? (task.getVideos() == null ? List.of() : task.getVideos())
                : videoRepository.findAllByAsTask_Id(task.getId());
        return Snapshot.from(task, visitDate, images, videos);
    }

    @Transactional
    public void recordCustomerCreated(AsTask task, Member actor) {
        Snapshot after = capture(task);
        List<Delta> deltas = new ArrayList<>();
        addCreated(deltas, "status", "처리상태", after.statusLabel(), true);
        addCreated(deltas, "assignedHandler", "AS 담당자", after.handlerLabel(), true);
        addCreated(deltas, "customerName", "고객명", after.customerName(), true);
        addCreated(deltas, "address", "주소", after.address(), true);
        addCreated(deltas, "product", "제품정보", after.productSummary(), true);
        addCreated(deltas, "subject", "AS 증상", after.subject(), true);
        addCreated(deltas, "reason", "증상 상세", after.reason(), true);
        addCreated(deltas, "onsiteContact", "현장연락처", after.onsiteContact(), true);
        addCreated(deltas, "applicantName", "접수 담당자", after.applicantName(), true);
        addCreated(deltas, "applicantPhone", "접수 담당자 연락처", after.applicantPhone(), true);
        addCreated(deltas, "applicantEmail", "접수 담당자 이메일", after.applicantEmail(), true);
        addCreated(deltas, "purchaseDate", "납품일자", text(after.purchaseDate()), true);
        addCreated(deltas, "billingTarget", "청구주체", after.billingTargetLabel(), true);
        if (after.requestImageCount() > 0) {
            addCreated(deltas, "requestImageCount", "요청 이미지 수", String.valueOf(after.requestImageCount()), false);
        }
        if (after.requestVideoCount() > 0) {
            addCreated(deltas, "requestVideoCount", "요청 영상 수", String.valueOf(after.requestVideoCount()), false);
        }
        record(task, AsNotificationSourceArea.CUSTOMER, AsNotificationAction.REQUEST_CREATED,
                null, actor, "CUSTOMER_AS_REQUEST_CREATED", "AS 신청", "/customer/asSubmit", deltas);
    }

    @Transactional
    public void recordCustomerUpdated(AsTask task, Member actor, Snapshot before) {
        Snapshot after = capture(task);
        List<Delta> deltas = differences(before, after);
        deltas.removeIf(d -> Set.of("status", "assignedHandler", "adminMemo", "handlerMemo", "visitDate", "visitTime").contains(d.key()));
        record(task, AsNotificationSourceArea.CUSTOMER, AsNotificationAction.CUSTOMER_UPDATE,
                before != null ? before.handlerId() : null, actor,
                "CUSTOMER_AS_UPDATE", "고객 AS 수정", "/customer/asUpdate/" + task.getId(), deltas);
    }

    /** 물리 삭제 전에 호출하여 수신자/스냅샷을 먼저 확정합니다. */
    @Transactional
    public void recordCustomerCanceled(AsTask task, Member actor) {
        List<Delta> deltas = List.of(new Delta("status", "처리상태",
                statusLabel(task != null ? task.getStatus() : null), "취소", true));
        record(task, AsNotificationSourceArea.CUSTOMER, AsNotificationAction.CUSTOMER_CANCEL,
                task != null && task.getAssignedHandler() != null ? task.getAssignedHandler().getId() : null,
                actor, "CUSTOMER_AS_CANCEL", "고객 AS 취소", "/customer/asDelete/" + (task != null ? task.getId() : ""), deltas);
    }

    @Transactional
    public void recordManagementUpdated(AsTask task, Member actor, Snapshot before) {
        Snapshot after = capture(task);
        List<Delta> all = differences(before, after);
        if (all.isEmpty()) return;

        List<Delta> handler = select(all, Set.of("assignedHandler"));
        if (!handler.isEmpty()) {
            record(task, AsNotificationSourceArea.MANAGEMENT, AsNotificationAction.HANDLER_CHANGE,
                    before != null ? before.handlerId() : null, actor,
                    "MANAGEMENT_AS_HANDLER_CHANGE", "담당자 변경", "/management/asUpdate/" + task.getId(), handler);
        }

        List<Delta> status = select(all, Set.of("status"));
        if (!status.isEmpty()) {
            AsNotificationAction action = switch (task.getStatus()) {
                case IN_PROGRESS -> AsNotificationAction.STATUS_IN_PROGRESS;
                case CANCELED -> AsNotificationAction.STATUS_CANCELED;
                default -> AsNotificationAction.STATUS_CHANGE;
            };
            record(task, AsNotificationSourceArea.MANAGEMENT, action,
                    before != null ? before.handlerId() : null, actor,
                    "MANAGEMENT_AS_" + action.name(), action.getLabel(), "/management/asUpdate/" + task.getId(), status);
        }

        Set<String> excluded = Set.of("assignedHandler", "status", "handlerMemo", "visitDate", "visitTime", "resultImageCount");
        List<Delta> detail = all.stream().filter(d -> !excluded.contains(d.key())).toList();
        if (!detail.isEmpty()) {
            record(task, AsNotificationSourceArea.MANAGEMENT, AsNotificationAction.DETAIL_UPDATE,
                    before != null ? before.handlerId() : null, actor,
                    "MANAGEMENT_AS_DETAIL_UPDATE", "주요내용 변경", "/management/asUpdate/" + task.getId(), detail);
        }
    }

    @Transactional
    public void recordManagementDeleted(AsTask task, Member actor) {
        List<Delta> deltas = List.of(new Delta("deleted", "AS 삭제", "존재", "삭제", true));
        record(task, AsNotificationSourceArea.MANAGEMENT, AsNotificationAction.DELETE,
                task != null && task.getAssignedHandler() != null ? task.getAssignedHandler().getId() : null,
                actor, "MANAGEMENT_AS_DELETE", "AS 삭제", "/management/asDelete/" + (task != null ? task.getId() : ""), deltas);
    }

    @Transactional
    public void recordTeamUpdated(AsTask task, Member actor, Snapshot before) {
        Snapshot after = capture(task);
        List<Delta> all = differences(before, after);
        if (all.isEmpty()) return;

        List<Delta> handler = select(all, Set.of("assignedHandler"));
        if (!handler.isEmpty()) {
            record(task, AsNotificationSourceArea.AS_TEAM, AsNotificationAction.HANDLER_CHANGE,
                    before != null ? before.handlerId() : null, actor,
                    "AS_TEAM_HANDLER_CHANGE", "담당자 변경", "/team/asUpdate/" + task.getId(), handler);
        }

        List<Delta> visit = select(all, Set.of("visitDate", "visitTime"));
        if (!visit.isEmpty()) {
            record(task, AsNotificationSourceArea.AS_TEAM, AsNotificationAction.VISIT_SCHEDULE_UPDATE,
                    before != null ? before.handlerId() : null, actor,
                    "AS_TEAM_VISIT_SCHEDULE_UPDATE", "방문일정 변경", "/team/asUpdate/" + task.getId(), visit);
        }

        List<Delta> status = select(all, Set.of("status"));
        if (!status.isEmpty()) {
            AsNotificationAction action = task.getStatus() == AsStatus.COMPLETED
                    ? AsNotificationAction.COMPLETE : AsNotificationAction.STATUS_CHANGE;
            record(task, AsNotificationSourceArea.AS_TEAM, action,
                    before != null ? before.handlerId() : null, actor,
                    "AS_TEAM_" + action.name(), action.getLabel(), "/team/asUpdate/" + task.getId(), status);
        }

        List<Delta> internal = select(all, Set.of("handlerMemo", "requestImageCount", "requestVideoCount", "resultImageCount"));
        if (!internal.isEmpty()) {
            record(task, AsNotificationSourceArea.AS_TEAM, AsNotificationAction.INTERNAL_UPDATE,
                    before != null ? before.handlerId() : null, actor,
                    "AS_TEAM_INTERNAL_UPDATE", "담당자 메모·첨부 변경", "/team/asUpdate/" + task.getId(), internal);
        }
    }

    @Transactional
    public void recordTeamScheduleChanged(AsTask task, Member actor, LocalDate beforeDate, LocalDate afterDate,
                                          String operationCode, String operationLabel, String requestPath) {
        if (Objects.equals(beforeDate, afterDate)) return;
        List<Delta> deltas = List.of(new Delta("visitDate", "방문예정일",
                text(beforeDate), text(afterDate), true));
        record(task, AsNotificationSourceArea.AS_TEAM, AsNotificationAction.VISIT_SCHEDULE_UPDATE,
                task != null && task.getAssignedHandler() != null ? task.getAssignedHandler().getId() : null,
                actor, operationCode, operationLabel, requestPath, deltas);
    }

    private void record(AsTask task, AsNotificationSourceArea source, AsNotificationAction action,
                        Long previousHandlerId, Member actor,
                        String operationCode, String operationLabel, String requestPath, List<Delta> deltas) {
        List<Delta> actual = deltas == null ? List.of() : deltas.stream()
                .filter(Objects::nonNull)
                .filter(d -> !Objects.equals(normalize(d.beforeValue()), normalize(d.afterValue())))
                .toList();
        if (task == null || task.getId() == null || actual.isEmpty()) return;

        String summary = actual.stream().map(Delta::label).filter(Objects::nonNull).distinct()
                .collect(Collectors.joining(", ")) + " 변경";
        AsChangeEvent event = AsChangeEvent.create(
                task, source, action, previousHandlerId,
                actor != null ? actor.getId() : null,
                actor != null ? actor.getUsername() : null,
                actorLabel(actor),
                operationCode, operationLabel, requestPath, summary
        );
        int order = 0;
        for (Delta d : actual) {
            event.addField(AsChangeField.of(d.key(), d.label(), d.beforeValue(), d.afterValue(), d.customerVisible(), order++));
        }
        AsChangeEvent saved = eventRepository.save(event);
        eventRepository.flush();
        notificationService.createForEvent(saved);
    }

    private List<Delta> differences(Snapshot before, Snapshot after) {
        Snapshot b = before == null ? Snapshot.empty() : before;
        Snapshot a = after == null ? Snapshot.empty() : after;
        List<Delta> out = new ArrayList<>();
        add(out, "status", "처리상태", b.statusLabel(), a.statusLabel(), true);
        add(out, "assignedHandler", "AS 담당자", b.handlerLabel(), a.handlerLabel(), true);
        add(out, "company", "대리점", b.companyLabel(), a.companyLabel(), true);
        add(out, "customerName", "고객명", b.customerName(), a.customerName(), true);
        add(out, "address", "주소", b.address(), a.address(), true);
        add(out, "onsiteContact", "현장연락처", b.onsiteContact(), a.onsiteContact(), true);
        add(out, "product", "제품정보", b.productSummary(), a.productSummary(), true);
        add(out, "subject", "AS 증상", b.subject(), a.subject(), true);
        add(out, "reason", "증상 상세", b.reason(), a.reason(), true);
        add(out, "price", "AS 금액", b.priceText(), a.priceText(), true);
        add(out, "paymentCollected", "수납여부", b.paymentCollectedText(), a.paymentCollectedText(), true);
        add(out, "applicantName", "접수 담당자", b.applicantName(), a.applicantName(), true);
        add(out, "applicantPhone", "접수 담당자 연락처", b.applicantPhone(), a.applicantPhone(), true);
        add(out, "applicantEmail", "접수 담당자 이메일", b.applicantEmail(), a.applicantEmail(), true);
        add(out, "purchaseDate", "납품일자", text(b.purchaseDate()), text(a.purchaseDate()), true);
        add(out, "billingTarget", "청구주체", b.billingTargetLabel(), a.billingTargetLabel(), true);
        add(out, "adminMemo", "관리자 메모", b.adminMemo(), a.adminMemo(), false);
        add(out, "handlerMemo", "담당자 메모", b.handlerMemo(), a.handlerMemo(), false);
        add(out, "visitDate", "방문예정일", text(b.visitDate()), text(a.visitDate()), true);
        add(out, "visitTime", "방문예정시간", text(b.visitTime()), text(a.visitTime()), true);
        add(out, "requestImageCount", "요청 이미지 수", String.valueOf(b.requestImageCount()), String.valueOf(a.requestImageCount()), false);
        add(out, "requestVideoCount", "요청 영상 수", String.valueOf(b.requestVideoCount()), String.valueOf(a.requestVideoCount()), false);
        add(out, "resultImageCount", "결과 이미지 수", String.valueOf(b.resultImageCount()), String.valueOf(a.resultImageCount()), false);
        return out;
    }

    private List<Delta> select(List<Delta> source, Set<String> keys) {
        if (source == null || source.isEmpty()) return List.of();
        return source.stream().filter(d -> keys.contains(d.key())).toList();
    }

    private void addCreated(List<Delta> out, String key, String label, String after, boolean customerVisible) {
        if (normalize(after) != null) out.add(new Delta(key, label, null, after, customerVisible));
    }

    private void add(List<Delta> out, String key, String label, String before, String after, boolean customerVisible) {
        if (!Objects.equals(normalize(before), normalize(after))) out.add(new Delta(key, label, before, after, customerVisible));
    }

    private String actorLabel(Member member) {
        if (member == null) return "시스템";
        String name = normalize(member.getName());
        if (name != null) return name;
        String username = normalize(member.getUsername());
        return username != null ? username : "시스템";
    }

    private static String memberLabel(Member member) {
        if (member == null) return null;
        String name = normalize(member.getName());
        String username = normalize(member.getUsername());
        if (name != null && username != null) return name + "(" + username + ")";
        return name != null ? name : username;
    }

    private static String statusLabel(AsStatus status) {
        return status == null ? null : status.getLabelKr();
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() || "-".equals(v) ? null : v;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record Delta(String key, String label, String beforeValue, String afterValue, boolean customerVisible) {}

    public record Snapshot(
            AsStatus status,
            Long handlerId,
            String handlerLabel,
            Long requestedById,
            String companyLabel,
            String customerName,
            String address,
            String onsiteContact,
            String productSummary,
            String subject,
            String reason,
            int price,
            boolean paymentCollected,
            String applicantName,
            String applicantPhone,
            String applicantEmail,
            LocalDate purchaseDate,
            String billingTargetLabel,
            String adminMemo,
            String handlerMemo,
            LocalDate visitDate,
            LocalTime visitTime,
            int requestImageCount,
            int requestVideoCount,
            int resultImageCount
    ) {
        static Snapshot empty() {
            return new Snapshot(null, null, null, null, null, null, null, null, null, null, null,
                    0, false, null, null, null, null, null, null, null, null, null, 0, 0, 0);
        }

        static Snapshot from(AsTask task, LocalDate visitDate, List<AsImage> imageRows, List<AsVideo> videoRows) {
            Member requester = task.getRequestedBy();
            String company = requester != null && requester.getCompany() != null
                    ? normalize(requester.getCompany().getCompanyName()) : null;
            List<AsImage> images = imageRows == null ? List.of() : imageRows;
            List<AsVideo> videos = videoRows == null ? List.of() : videoRows;
            int requestImages = (int) images.stream().filter(i -> i != null && "REQUEST".equalsIgnoreCase(i.getType())).count();
            int resultImages = (int) images.stream().filter(i -> i != null && "RESULT".equalsIgnoreCase(i.getType())).count();
            int requestVideos = (int) videos.stream().filter(v -> v != null && "REQUEST".equalsIgnoreCase(v.getType())).count();
            return new Snapshot(
                    task.getStatus(),
                    task.getAssignedHandler() != null ? task.getAssignedHandler().getId() : null,
                    memberLabel(task.getAssignedHandler()),
                    requester != null ? requester.getId() : null,
                    company,
                    normalize(task.getCustomerName()),
                    AsChangeAuditService.address(task),
                    normalize(task.getOnsiteContact()),
                    AsChangeAuditService.product(task),
                    normalize(task.getSubject()),
                    normalize(task.getReason()),
                    task.getPrice(),
                    task.isPaymentCollected(),
                    normalize(task.getApplicantName()),
                    normalize(task.getApplicantPhone()),
                    normalize(task.getApplicantEmail()),
                    task.getPurchaseDate(),
                    task.getBillingTarget() != null ? task.getBillingTarget().getLabelKr() : null,
                    normalize(task.getAdminMemo()),
                    normalize(task.getHandlerMemo()),
                    visitDate,
                    task.getVisitPlannedTime(),
                    requestImages,
                    requestVideos,
                    resultImages
            );
        }

        String statusLabel() { return AsChangeAuditService.statusLabel(status); }
        String priceText() { return price + "원"; }
        String paymentCollectedText() { return paymentCollected ? "수납완료" : "미수납"; }
    }

    private static String address(AsTask task) {
        Set<String> parts = new LinkedHashSet<>();
        for (String part : new String[] {
                value(task.getZipCode()), value(task.getDoName()), value(task.getSiName()), value(task.getGuName()),
                value(task.getRoadAddress()), value(task.getDetailAddress())}) {
            if (part != null) parts.add(part);
        }
        return parts.stream().collect(Collectors.joining(" "));
    }

    private static String product(AsTask task) {
        return java.util.Arrays.stream(new String[] {
                        value(task.getProductName()), value(task.getProductSize()), value(task.getProductColor()), value(task.getProductOptions())})
                .filter(Objects::nonNull).distinct().collect(Collectors.joining(" / "));
    }

    private static String value(String value) {
        return normalize(value);
    }
}
