package com.dev.HiddenBATHAuto.service.order;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.order.OrderImageRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String DELIVERY_IMAGE_TYPE = "DELIVERY";

    private final OrderRepository orderRepository;
    private final OrderImageRepository orderImageRepository;

    @Value("${spring.upload.path}")
    private String baseUploadPath;

    /**
     * 단건 배송완료 처리입니다.
     *
     * 기존 호출부 호환을 위해 시그니처는 유지합니다. 업로드된 MultipartFile은 즉시 byte[]로
     * 복사한 뒤 저장하므로 Servlet 임시파일이 이동되거나 소진되는 구현에 의존하지 않습니다.
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDeliveryStatusAndImages(
            Long orderId,
            String status,
            List<MultipartFile> files
    ) throws IOException {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID가 없습니다.");
        }

        completeOrdersWithSharedImages(List.of(orderId), status, files);
    }

    /**
     * 동일 배송지 일괄완료용 메서드입니다.
     *
     * 중요한 처리 규칙:
     * 1. 사용자가 올린 MultipartFile은 각 파일당 한 번만 byte[]로 읽습니다.
     * 2. 같은 이미지 내용은 완료 대상 모든 주문에 각각 독립된 물리 파일로 저장합니다.
     * 3. 각 주문마다 독립된 OrderImage 행을 생성합니다.
     * 4. 따라서 한 주문의 이미지를 나중에 삭제해도 다른 주문의 증빙 파일이 같이 사라지지 않습니다.
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDeliveryStatusesAndSharedImages(
            List<Long> orderIds,
            String status,
            List<MultipartFile> files
    ) throws IOException {
        completeOrdersWithSharedImages(orderIds, status, files);
    }

    private void completeOrdersWithSharedImages(
            List<Long> orderIds,
            String status,
            List<MultipartFile> files
    ) throws IOException {
        OrderStatus requestedStatus = parseRequestedStatus(status);

        if (requestedStatus != OrderStatus.DELIVERY_DONE) {
            throw new IllegalStateException("배송팀은 배송완료 상태로만 변경할 수 있습니다.");
        }

        List<Long> distinctOrderIds = normalizeOrderIds(orderIds);
        List<Order> orders = loadOrdersInRequestedOrder(distinctOrderIds);

        // 파일 시스템에 손대기 전에 모든 주문 상태를 먼저 검증합니다.
        for (Order order : orders) {
            validateCurrentStatusForDeliveryDone(order);
            resolveRequesterMemberId(order);
        }

        List<DeliveryImagePayload> payloads = readImagePayloads(files);

        if (payloads.isEmpty()) {
            throw new IllegalStateException("배송완료 이미지가 1장 이상 필요합니다.");
        }

        List<Path> savedFilePaths = new ArrayList<>();
        List<OrderImage> imagesToSave = new ArrayList<>();
        LocalDateTime completedAt = LocalDateTime.now();

        try {
            for (Order order : orders) {
                for (DeliveryImagePayload payload : payloads) {
                    SavedDeliveryImage saved = saveIndependentImageFile(order, payload);
                    savedFilePaths.add(saved.path());
                    imagesToSave.add(toOrderImage(order, payload, saved, completedAt));
                }

                order.setStatus(OrderStatus.DELIVERY_DONE);
                order.setUpdatedAt(completedAt);
            }

            orderImageRepository.saveAll(imagesToSave);
            orderRepository.saveAll(orders);

            /*
             * 이 메서드가 더 큰 트랜잭션(예: 인덱스 재분류) 안에서 호출된 뒤 나중에 롤백되어도
             * 이미 생성한 파일이 남지 않도록 트랜잭션 종료 시점 정리를 등록합니다.
             */
            registerRollbackFileCleanup(savedFilePaths);

        } catch (IOException | RuntimeException e) {
            deleteSavedFilesQuietly(savedFilePaths);
            throw e;
        }
    }

    private List<Long> normalizeOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("배송완료 처리할 주문이 없습니다.");
        }

        List<Long> distinct = orderIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();

        if (distinct.isEmpty()) {
            throw new IllegalArgumentException("배송완료 처리할 주문이 없습니다.");
        }

        return distinct;
    }

    private List<Order> loadOrdersInRequestedOrder(List<Long> orderIds) {
        Map<Long, Order> orderMap = new LinkedHashMap<>();

        for (Order order : orderRepository.findAllById(orderIds)) {
            if (order == null || order.getId() == null) {
                continue;
            }

            orderMap.putIfAbsent(order.getId(), order);
        }

        List<Order> result = new ArrayList<>(orderIds.size());

        for (Long orderId : orderIds) {
            Order order = orderMap.get(orderId);

            if (order == null) {
                throw new IllegalArgumentException("해당 주문이 존재하지 않습니다. orderId=" + orderId);
            }

            result.add(order);
        }

        return result;
    }

    private List<DeliveryImagePayload> readImagePayloads(List<MultipartFile> files) throws IOException {
        List<MultipartFile> validFiles = filterValidImageFiles(files);
        List<DeliveryImagePayload> payloads = new ArrayList<>(validFiles.size());

        for (MultipartFile file : validFiles) {
            String originalFilename = resolveOriginalFilename(file);
            String extension = getExtension(originalFilename);
            byte[] content = file.getBytes();

            if (content.length == 0) {
                continue;
            }

            payloads.add(new DeliveryImagePayload(
                    originalFilename,
                    extension,
                    file.getContentType(),
                    content
            ));
        }

        return payloads;
    }

    private SavedDeliveryImage saveIndependentImageFile(
            Order order,
            DeliveryImagePayload payload
    ) throws IOException {
        Long requesterMemberId = resolveRequesterMemberId(order);
        String datePath = LocalDate.now().toString();
        String subPath = "order/order/" + requesterMemberId + "/" + datePath + "/delivery";

        Path saveDir = Paths.get(baseUploadPath, subPath).normalize();
        Files.createDirectories(saveDir);

        String storedName = UUID.randomUUID()
                + (StringUtils.hasText(payload.extension()) ? "." + payload.extension() : "");

        Path fullPath = saveDir.resolve(storedName).normalize();

        if (!fullPath.startsWith(saveDir)) {
            throw new IOException("배송완료 이미지 저장 경로가 올바르지 않습니다.");
        }

        Files.write(
                fullPath,
                payload.content(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );

        String url = "/upload/" + subPath.replace('\\', '/') + "/" + storedName;
        return new SavedDeliveryImage(fullPath, url);
    }

    private OrderImage toOrderImage(
            Order order,
            DeliveryImagePayload payload,
            SavedDeliveryImage saved,
            LocalDateTime uploadedAt
    ) {
        OrderImage image = new OrderImage();
        image.setType(DELIVERY_IMAGE_TYPE);
        image.setFilename(payload.originalFilename());
        image.setPath(saved.path().toString());
        image.setUrl(saved.url());
        image.setOrder(order);
        image.setUploadedAt(uploadedAt);
        return image;
    }

    private OrderStatus parseRequestedStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("변경할 배송 상태가 없습니다.");
        }

        try {
            return OrderStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("올바르지 않은 배송 상태입니다: " + status);
        }
    }

    private void validateCurrentStatusForDeliveryDone(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("주문 정보가 없습니다.");
        }

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == null) {
            throw new IllegalStateException("현재 주문 상태를 확인할 수 없습니다.");
        }

        if (currentStatus == OrderStatus.CONFIRMED) {
            throw new IllegalStateException("승인 완료 상태에서는 배송완료 처리 및 증빙 업로드가 불가능합니다.");
        }

        if (currentStatus == OrderStatus.REQUESTED) {
            throw new IllegalStateException("고객 발주 상태에서는 배송완료 처리 및 증빙 업로드가 불가능합니다.");
        }

        if (currentStatus == OrderStatus.CANCELED) {
            throw new IllegalStateException("취소된 주문은 배송완료 처리할 수 없습니다.");
        }

        if (currentStatus == OrderStatus.DELIVERY_DONE) {
            throw new IllegalStateException("이미 배송완료 처리된 주문입니다.");
        }

        if (currentStatus != OrderStatus.PRODUCTION_DONE) {
            throw new IllegalStateException(
                    "생산완료 상태의 주문만 배송완료 처리할 수 있습니다. 현재 상태="
                            + currentStatus.getLabel()
            );
        }
    }

    private List<MultipartFile> filterValidImageFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        return files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .filter(this::isImageFile)
                .collect(Collectors.toList());
    }

    private boolean isImageFile(MultipartFile file) {
        String contentType = file.getContentType();

        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private Long resolveRequesterMemberId(Order order) {
        if (order == null
                || order.getTask() == null
                || order.getTask().getRequestedBy() == null
                || order.getTask().getRequestedBy().getId() == null) {
            throw new IllegalStateException("주문 요청자 정보를 확인할 수 없어 배송완료 이미지를 저장할 수 없습니다.");
        }

        return order.getTask().getRequestedBy().getId();
    }

    private String resolveOriginalFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            return "delivery-image";
        }

        String cleaned = StringUtils.cleanPath(originalFilename);
        String basename = Paths.get(cleaned).getFileName().toString();
        return basename.isBlank() ? "delivery-image" : basename;
    }

    private void registerRollbackFileCleanup(List<Path> paths) {
        if (paths == null || paths.isEmpty()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        List<Path> snapshot = List.copyOf(paths);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteSavedFilesQuietly(snapshot);
                }
            }
        });
    }

    private void deleteSavedFilesQuietly(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }

        for (Path path : paths) {
            if (path == null) {
                continue;
            }

            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // 파일 정리 실패가 원래 예외를 가리지 않도록 무시합니다.
            }
        }
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return null;
        }

        int idx = filename.lastIndexOf('.');

        if (idx < 0 || idx == filename.length() - 1) {
            return null;
        }

        String extension = filename.substring(idx + 1)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");

        return extension.isBlank() ? null : extension;
    }

    private record DeliveryImagePayload(
            String originalFilename,
            String extension,
            String contentType,
            byte[] content
    ) {
    }

    private record SavedDeliveryImage(Path path, String url) {
    }
}
