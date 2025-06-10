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
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderImageRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderUpdateService {

	private final OrderRepository orderRepository;
	private final DeliveryMethodRepository deliveryMethodRepository;
	private final MemberRepository memberRepository;
	private final TeamCategoryRepository teamCategoryRepository;
	private final DeliveryOrderIndexService deliveryOrderIndexService;
	private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
	private final OrderImageRepository orderImageRepository;

	@Value("${spring.upload.path}")
	private String uploadRootPath;

	public void updateOrder(Long orderId, int productCost, LocalDate preferredDeliveryDate, String statusStr,
			Optional<Long> deliveryMethodId, Optional<Long> deliveryHandlerId, Optional<Long> productCategoryId,
			List<MultipartFile> adminImages) {

		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalArgumentException("Order not found"));

		OrderStatus status = OrderStatus.valueOf(statusStr);

		order.setProductCost(productCost);
		order.setPreferredDeliveryDate(preferredDeliveryDate.atStartOfDay());
		order.setStatus(status);

		deliveryMethodId.ifPresentOrElse(id -> {
			DeliveryMethod method = deliveryMethodRepository.findById(id)
					.orElseThrow(() -> new IllegalArgumentException("Invalid deliveryMethodId"));
			order.setDeliveryMethod(method);
		}, () -> order.setDeliveryMethod(null));

		deliveryHandlerId.ifPresentOrElse(id -> {
			Member member = memberRepository.findById(id)
					.orElseThrow(() -> new IllegalArgumentException("Invalid deliveryHandlerId"));
			order.setAssignedDeliveryHandler(member);
		}, () -> order.setAssignedDeliveryHandler(null));

		productCategoryId.ifPresentOrElse(id -> {
			TeamCategory category = teamCategoryRepository.findById(id)
					.orElseThrow(() -> new IllegalArgumentException("Invalid productCategoryId"));
			order.setProductCategory(category);
		}, () -> order.setProductCategory(null));

		order.setUpdatedAt(LocalDateTime.now());

		// ✅ 이미지 저장 로직
		saveAdminImages(order, adminImages);

		if (status == OrderStatus.CANCELED) {
			deliveryOrderIndexRepository.findByOrder(order).ifPresent(deliveryOrderIndexRepository::delete);
		} else {
			deliveryOrderIndexService.ensureIndex(order);
		}
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
