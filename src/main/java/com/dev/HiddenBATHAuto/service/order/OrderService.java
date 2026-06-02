package com.dev.HiddenBATHAuto.service.order;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final OrderRepository orderRepository;
    private final OrderImageRepository orderImageRepository;

    @Value("${spring.upload.path}") // e.g. /home/ec2-user/upload
    private String baseUploadPath;

    /**
     * 배송완료 처리 + 배송완료 증빙 이미지 저장
     *
     * 규칙:
     * 1. DELIVERY_DONE 요청만 허용
     * 2. 현재 주문 상태가 PRODUCTION_DONE 또는 DISPATCH_DONE일 때만 DELIVERY_DONE 가능
     * 3. 주문 1건당 배송완료 이미지 정확히 1장 필요
     * 4. 이미지 저장 후 상태 변경
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

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다."));

        OrderStatus requestedStatus = parseRequestedStatus(status);

        if (requestedStatus != OrderStatus.DELIVERY_DONE) {
            throw new IllegalStateException("배송팀은 배송완료 상태로만 변경할 수 있습니다.");
        }

        validateCurrentStatusForDeliveryDone(order);

        List<MultipartFile> validFiles = filterValidImageFiles(files);

        if (validFiles.size() != 1) {
            throw new IllegalStateException(
                    "배송완료 이미지가 정확히 1장 필요합니다. 현재 업로드 이미지 수: " + validFiles.size()
            );
        }

        List<Path> savedFilePaths = new ArrayList<>();

        try {
            MultipartFile file = validFiles.get(0);

            Long requesterMemberId = resolveRequesterMemberId(order);
            String datePath = LocalDate.now().toString(); // yyyy-MM-dd
            String subPath = "order/order/" + requesterMemberId + "/" + datePath + "/delivery";

            Path saveDir = Paths.get(baseUploadPath, subPath);
            Files.createDirectories(saveDir);

            String originalFilename = resolveOriginalFilename(file);
            String extension = getExtension(originalFilename);
            String storedName = UUID.randomUUID() + (extension != null ? "." + extension : "");

            Path fullPath = saveDir.resolve(storedName);
            file.transferTo(fullPath.toFile());
            savedFilePaths.add(fullPath);

            OrderImage image = new OrderImage();
            image.setType("DELIVERY");
            image.setFilename(originalFilename);
            image.setPath(fullPath.toString());
            image.setUrl("/upload/" + subPath + "/" + storedName);
            image.setOrder(order);
            image.setUploadedAt(LocalDateTime.now());

            orderImageRepository.save(image);

            order.setStatus(OrderStatus.DELIVERY_DONE);
            order.setUpdatedAt(LocalDateTime.now());

            orderRepository.save(order);

        } catch (IOException | RuntimeException e) {
            deleteSavedFilesQuietly(savedFilePaths);
            throw e;
        }
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

        if (currentStatus != OrderStatus.PRODUCTION_DONE
                && currentStatus != OrderStatus.DISPATCH_DONE) {
            throw new IllegalStateException("생산완료 또는 출고완료 상태의 주문만 배송완료 처리할 수 있습니다.");
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

        return StringUtils.cleanPath(originalFilename);
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
                // 파일 정리 실패는 원래 예외를 가리지 않도록 무시합니다.
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

        return filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}