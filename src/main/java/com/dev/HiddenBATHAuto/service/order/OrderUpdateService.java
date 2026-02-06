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

    private final OrderRepository orderRepository;
    private final DeliveryMethodRepository deliveryMethodRepository;
    private final MemberRepository memberRepository;
    private final TeamCategoryRepository teamCategoryRepository;
    private final CompanyRepository companyRepository;            // ✅ 추가
    private final TaskRepository taskRepository;                  // ✅ 추가
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
            List<MultipartFile> adminImages,

            // ✅✅ 추가
            String adminMemo
    ) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        OrderStatus status = OrderStatus.valueOf(statusStr);

        order.setProductCost(productCost);
        order.setPreferredDeliveryDate(preferredDeliveryDate != null ? preferredDeliveryDate.atStartOfDay() : null);
        order.setStatus(status);

        // ✅✅ 관리자 남김말 업데이트 (NULL 가능)
        // 공백만 입력되면 null 처리(원치 않으면 이 로직 제거하세요)
        String normalizedAdminMemo = (adminMemo == null) ? null : adminMemo.trim();
        order.setAdminMemo((normalizedAdminMemo == null || normalizedAdminMemo.isEmpty()) ? null : normalizedAdminMemo);

        deliveryMethodId.ifPresentOrElse(id -> {
            var method = deliveryMethodRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid deliveryMethodId"));
            order.setDeliveryMethod(method);
        }, () -> order.setDeliveryMethod(null));

        deliveryHandlerId.ifPresentOrElse(id -> {
            var member = memberRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid deliveryHandlerId"));
            order.setAssignedDeliveryHandler(member);
        }, () -> order.setAssignedDeliveryHandler(null));

        productCategoryId.ifPresentOrElse(id -> {
            var category = teamCategoryRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid productCategoryId"));
            order.setProductCategory(category);
        }, () -> order.setProductCategory(null));

        // ✅ 회사/신청자 변경 처리
        if (requesterMemberId.isPresent()) {
            Member newRequester = memberRepository.findById(requesterMemberId.get())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid requesterMemberId"));

            // 회사 소속 검증(회사도 넘어온 경우)
            if (companyId.isPresent()) {
                Company newCompany = companyRepository.findById(companyId.get())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid companyId"));

                if (newRequester.getCompany() == null ||
                        !Objects.equals(newRequester.getCompany().getId(), newCompany.getId())) {
                    throw new IllegalStateException("선택한 멤버가 해당 대리점(Company)에 소속되어 있지 않습니다.");
                }
            }

            Task task = order.getTask();
            if (task == null) throw new IllegalStateException("Order에 Task가 존재하지 않습니다.");

            task.setRequestedBy(newRequester);
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task); // ✅ 명시적 저장
        } else if (companyId.isPresent()) {
            throw new IllegalArgumentException("대리점을 변경하려면 신청자(멤버)도 함께 선택해야 합니다.");
        }

        order.setUpdatedAt(LocalDateTime.now());

        // ✅ 이미지 저장 로직 (기존 유지)
        saveAdminImages(order, adminImages);

        // ✅ 인덱스 처리 (기존 유지)
        if (status == OrderStatus.CANCELED) {
            deliveryOrderIndexRepository.findByOrder(order)
                    .ifPresent(deliveryOrderIndexRepository::delete);
        } else {
            deliveryOrderIndexService.ensureIndex(order);
        }

        // ✅ orderRepository.save(order) 호출이 필요할 수도?
        // - 현재 구조가 JPA 영속 상태에서 트랜잭션 커밋 시 dirty checking으로 반영된다면 생략 가능
        // - 하지만 서비스 구조상 detach 가능성이 있거나, 확실히 하고 싶다면 아래를 활성화 권장
        // orderRepository.save(order);
    }

    private void saveAdminImages(Order order, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;

        LocalDate today = LocalDate.now();
        String dateFolder = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Long memberId = order.getTask().getRequestedBy().getId();

        // ✅ 저장 경로: /.../order/order/{memberId}/{yyyy-MM-dd}/admin/
        Path saveDir = Paths.get(uploadRootPath, "order", "order", String.valueOf(memberId), dateFolder, "admin");

        try {
            if (!Files.exists(saveDir)) {
                Files.createDirectories(saveDir);
            }

            List<OrderImage> imageEntities = new ArrayList<>();

            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;

                String ext = getFileExtension(file.getOriginalFilename());
                String uuidFileName = UUID.randomUUID() + (ext != null ? "." + ext : "");
                Path savedPath = saveDir.resolve(uuidFileName);
                Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);

                // ✅ 웹 접근 경로: /upload/order/order/{memberId}/{yyyy-MM-dd}/admin/{filename}
                String urlPath = "/upload/order/order/" + memberId + "/" + dateFolder + "/admin/" + uuidFileName;

                OrderImage image = new OrderImage();
                image.setOrder(order);
                image.setFilename(file.getOriginalFilename());
                image.setUrl(urlPath);
                image.setType("MANAGEMENT");
                image.setPath(savedPath.toString());

                imageEntities.add(image);
            }

            if (!imageEntities.isEmpty()) {
                orderImageRepository.saveAll(imageEntities);
            }

        } catch (IOException e) {
            throw new RuntimeException("관리자 이미지 저장 중 오류 발생", e);
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return null;
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
