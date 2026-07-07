package com.dev.HiddenBATHAuto.orderExcelUpload.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;

@Service
public class OrderExcelUploadImageStorageService {

    private static final String MANAGEMENT_UPLOAD_TYPE = "MANAGEMENT";
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    @Value("${spring.upload.path}")
    private String uploadPath;

    public List<OrderImage> storeManagementImages(Order order, List<MultipartFile> files) {
        if (order == null || files == null || files.isEmpty()) {
            return List.of();
        }

        if (order.getId() == null) {
            throw new IllegalArgumentException("이미지 저장은 Order 저장 후에만 가능합니다. order.id가 없습니다.");
        }

        if (order.getTask() == null || order.getTask().getId() == null) {
            throw new IllegalArgumentException("이미지 저장은 Task 저장 후에만 가능합니다. task.id가 없습니다.");
        }

        List<OrderImage> stored = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            stored.add(storeOne(order, file));
        }
        return stored;
    }

    private OrderImage storeOne(Order order, MultipartFile file) {
        String originalFilename = StringUtils.cleanPath(
                Objects.requireNonNullElse(file.getOriginalFilename(), "image")
        );

        String extension = extension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다: " + originalFilename);
        }

        Long taskId = order.getTask().getId();
        Long orderId = order.getId();
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String savedFilename = UUID.randomUUID() + "." + extension;

        Path basePath = Paths.get(normalizeDir(uploadPath)).toAbsolutePath().normalize();
        Path uploadDir = basePath
                .resolve("order")
                .resolve("management")
                .resolve(String.valueOf(taskId))
                .resolve(String.valueOf(orderId))
                .resolve(dateFolder)
                .normalize();

        Path targetPath = uploadDir.resolve(savedFilename).normalize();

        if (!targetPath.startsWith(basePath)) {
            throw new IllegalArgumentException("파일 저장 경로가 올바르지 않습니다.");
        }

        try {
            Files.createDirectories(uploadDir);
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 저장 중 오류가 발생했습니다: " + originalFilename);
        }

        OrderImage orderImage = new OrderImage();
        orderImage.setType(MANAGEMENT_UPLOAD_TYPE);
        orderImage.setFilename(originalFilename);
        orderImage.setPath(targetPath.toString().replace("\\", "/"));
        orderImage.setUrl(
                "/upload/order/management/"
                        + taskId
                        + "/"
                        + orderId
                        + "/"
                        + dateFolder
                        + "/"
                        + savedFilename
        );
        orderImage.setUploadedAt(LocalDateTime.now());

        order.addOrderImage(orderImage);
        return orderImage;
    }

    private String extension(String filename) {
        String safeName = filename == null ? "" : filename.trim();
        int dotIndex = safeName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == safeName.length() - 1) {
            return "";
        }
        return safeName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeDir(String dir) {
        if (!StringUtils.hasText(dir)) {
            return dir;
        }

        String normalized = dir.replace("\\", "/").trim();
        String userHome = System.getProperty("user.home");

        if (StringUtils.hasText(userHome)) {
            userHome = userHome.replace("\\", "/");
            normalized = normalized.replace("${user.home}", userHome);

            if (normalized.equals("~")) {
                normalized = userHome;
            } else if (normalized.startsWith("~/")) {
                normalized = userHome + normalized.substring(1);
            }
        }

        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }

        return normalized;
    }
}
