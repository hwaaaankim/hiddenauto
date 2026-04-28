package com.dev.HiddenBATHAuto.service.order;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderImageRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderUpdateService {

    private static final String ADMIN_IMAGE_TYPE = "MANAGEMENT";

    private final OrderRepository orderRepository;
    private final DeliveryMethodRepository deliveryMethodRepository;
    private final MemberRepository memberRepository;
    private final TeamCategoryRepository teamCategoryRepository;
    private final CompanyRepository companyRepository;
    private final TaskRepository taskRepository;
    private final DeliveryOrderIndexService deliveryOrderIndexService;
    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final OrderImageRepository orderImageRepository;

    @Value("${spring.upload.path}")
    private String uploadRootPath;

    @Transactional
    public void updateOrder(
            Long orderId,
            int productCost,
            LocalDate preferredDeliveryDate,
            String statusStr,
            Optional<Long> deliveryMethodId,
            Optional<Long> deliveryHandlerId,
            Optional<Long> productCategoryId,
            Optional<Long> companyId,
            Optional<Long> requesterMemberId,
            List<Long> deleteAdminImageIds,
            List<MultipartFile> adminImages,
            String adminMemo
    ) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found. orderId=" + orderId));

        OrderStatus status = parseOrderStatus(statusStr);

        order.setProductCost(productCost);
        order.setPreferredDeliveryDate(preferredDeliveryDate != null ? preferredDeliveryDate.atStartOfDay() : null);
        order.setStatus(status);

        String normalizedAdminMemo = normalizeNullableText(adminMemo);
        order.setAdminMemo(normalizedAdminMemo);

        deliveryMethodId.ifPresentOrElse(id -> {
            var method = deliveryMethodRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid deliveryMethodId. id=" + id));
            order.setDeliveryMethod(method);
        }, () -> order.setDeliveryMethod(null));

        deliveryHandlerId.ifPresentOrElse(id -> {
            var member = memberRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid deliveryHandlerId. id=" + id));
            order.setAssignedDeliveryHandler(member);
        }, () -> order.setAssignedDeliveryHandler(null));

        productCategoryId.ifPresentOrElse(id -> {
            var category = teamCategoryRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid productCategoryId. id=" + id));
            order.setProductCategory(category);
        }, () -> order.setProductCategory(null));

        updateRequesterIfNeeded(order, companyId, requesterMemberId);

        order.setUpdatedAt(LocalDateTime.now());

        /*
         * 중요:
         * 1) 기존 이미지 삭제 먼저
         * 2) 새 이미지 저장
         *
         * 사용자가 기존 이미지를 전부 X 처리하고 새 이미지를 추가하는 경우도 이 순서로 정상 동작합니다.
         */
        deleteAdminImages(order, deleteAdminImageIds);
        saveAdminImages(order, adminImages);

        if (status == OrderStatus.CANCELED) {
            deliveryOrderIndexRepository.findByOrder(order)
                    .ifPresent(deliveryOrderIndexRepository::delete);
        } else {
            deliveryOrderIndexService.ensureIndex(order);
        }

        /*
         * 현재 트랜잭션 안에서 order는 영속 상태이므로 dirty checking으로 저장됩니다.
         * 그래도 명시 저장을 원하시면 아래 주석을 해제하셔도 됩니다.
         */
        // orderRepository.save(order);
    }

    private OrderStatus parseOrderStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            throw new IllegalArgumentException("발주상태 값이 비어 있습니다.");
        }

        try {
            return OrderStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("올바르지 않은 발주상태입니다. status=" + statusStr, e);
        }
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void updateRequesterIfNeeded(
            Order order,
            Optional<Long> companyId,
            Optional<Long> requesterMemberId
    ) {
        if (requesterMemberId.isPresent()) {
            Member newRequester = memberRepository.findById(requesterMemberId.get())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Invalid requesterMemberId. id=" + requesterMemberId.get()
                    ));

            if (companyId.isPresent()) {
                Company newCompany = companyRepository.findById(companyId.get())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Invalid companyId. id=" + companyId.get()
                        ));

                if (newRequester.getCompany() == null ||
                        !Objects.equals(newRequester.getCompany().getId(), newCompany.getId())) {
                    throw new IllegalStateException("선택한 멤버가 해당 대리점에 소속되어 있지 않습니다.");
                }
            }

            Task task = order.getTask();

            if (task == null) {
                throw new IllegalStateException("Order에 Task가 존재하지 않습니다. orderId=" + order.getId());
            }

            task.setRequestedBy(newRequester);
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);

            return;
        }

        if (companyId.isPresent()) {
            throw new IllegalArgumentException("대리점을 변경하려면 신청자도 함께 선택해야 합니다.");
        }
    }

    private void deleteAdminImages(Order order, List<Long> deleteAdminImageIds) {
        if (order == null || deleteAdminImageIds == null || deleteAdminImageIds.isEmpty()) {
            return;
        }

        Long orderId = order.getId();

        for (Long imageId : deleteAdminImageIds) {
            if (imageId == null) {
                continue;
            }

            OrderImage image = orderImageRepository
                    .findByIdAndOrder_IdAndTypeIgnoreCase(imageId, orderId, ADMIN_IMAGE_TYPE)
                    .orElse(null);

            if (image == null) {
                continue;
            }

            deletePhysicalFile(image);

            /*
             * 같은 트랜잭션 안에서 order.getOrderImages()가 이미 초기화된 상태일 수 있으므로
             * 컬렉션에서도 제거해 줍니다.
             */
            if (order.getOrderImages() != null) {
                order.getOrderImages().removeIf(orderImage ->
                        orderImage != null && Objects.equals(orderImage.getId(), image.getId())
                );
            }

            orderImageRepository.delete(image);
        }
    }

    private void deletePhysicalFile(OrderImage image) {
        Path filePath = resolveImageFilePath(image);

        if (filePath == null) {
            return;
        }

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            /*
             * 여기서 RuntimeException을 던지면 DB 수정까지 롤백됩니다.
             * 이미지 파일 삭제 실패 때문에 주문 수정 전체를 실패시키고 싶으면 RuntimeException으로 바꾸시면 됩니다.
             */
            throw new RuntimeException("관리자 이미지 파일 삭제 중 오류가 발생했습니다. path=" + filePath, e);
        }
    }

    private Path resolveImageFilePath(OrderImage image) {
        if (image == null) {
            return null;
        }

        if (image.getPath() != null && !image.getPath().isBlank()) {
            return Paths.get(image.getPath()).normalize();
        }

        /*
         * 예외 대비:
         * 과거 데이터 중 path가 없고 url만 있는 경우 /upload/ 뒤 경로를 uploadRootPath와 합칩니다.
         * url 예시: /upload/order/order/{memberId}/{yyyy-MM-dd}/admin/{filename}
         * 실제 경로: {spring.upload.path}/order/order/{memberId}/{yyyy-MM-dd}/admin/{filename}
         */
        String url = image.getUrl();

        if (url == null || url.isBlank()) {
            return null;
        }

        String prefix = "/upload/";

        if (!url.startsWith(prefix)) {
            return null;
        }

        String relativePath = url.substring(prefix.length());

        if (relativePath.isBlank()) {
            return null;
        }

        return Paths.get(uploadRootPath, relativePath).normalize();
    }

    private void saveAdminImages(Order order, List<MultipartFile> files) {
        if (order == null || files == null || files.isEmpty()) {
            return;
        }

        List<MultipartFile> validFiles = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();

        if (validFiles.isEmpty()) {
            return;
        }

        Long memberId = resolveRequesterMemberId(order);

        LocalDate today = LocalDate.now();
        String dateFolder = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        /*
         * 저장 경로:
         * {spring.upload.path}/order/order/{memberId}/{yyyy-MM-dd}/admin/
         */
        Path saveDir = Paths.get(
                uploadRootPath,
                "order",
                "order",
                String.valueOf(memberId),
                dateFolder,
                "admin"
        );

        try {
            Files.createDirectories(saveDir);

            List<OrderImage> imageEntities = new ArrayList<>();

            for (MultipartFile file : validFiles) {
                String originalFilename = file.getOriginalFilename();
                String ext = getFileExtension(originalFilename);
                String uuidFileName = UUID.randomUUID() + (ext != null ? "." + ext : "");

                Path savedPath = saveDir.resolve(uuidFileName).normalize();

                Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);

                /*
                 * 웹 접근 경로:
                 * /upload/order/order/{memberId}/{yyyy-MM-dd}/admin/{filename}
                 */
                String urlPath = "/upload/order/order/"
                        + memberId
                        + "/"
                        + dateFolder
                        + "/admin/"
                        + uuidFileName;

                OrderImage image = new OrderImage();
                image.setOrder(order);
                image.setFilename(originalFilename);
                image.setUrl(urlPath);
                image.setType(ADMIN_IMAGE_TYPE);
                image.setPath(savedPath.toString());
                image.setUploadedAt(LocalDateTime.now());

                imageEntities.add(image);
            }

            if (!imageEntities.isEmpty()) {
                orderImageRepository.saveAll(imageEntities);

                if (order.getOrderImages() != null) {
                    order.getOrderImages().addAll(imageEntities);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("관리자 이미지 저장 중 오류가 발생했습니다.", e);
        }
    }

    private Long resolveRequesterMemberId(Order order) {
        if (order.getTask() == null) {
            throw new IllegalStateException("Order에 Task가 존재하지 않아 이미지 저장 경로를 만들 수 없습니다.");
        }

        if (order.getTask().getRequestedBy() == null) {
            throw new IllegalStateException("Task에 requestedBy가 없어 이미지 저장 경로를 만들 수 없습니다.");
        }

        return order.getTask().getRequestedBy().getId();
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isBlank() || !filename.contains(".")) {
            return null;
        }

        String ext = filename.substring(filename.lastIndexOf('.') + 1).trim();

        if (ext.isBlank()) {
            return null;
        }

        return ext.toLowerCase();
    }
}