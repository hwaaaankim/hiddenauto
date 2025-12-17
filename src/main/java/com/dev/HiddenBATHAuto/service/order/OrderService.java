package com.dev.HiddenBATHAuto.service.order;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

	@Transactional
	public void updateDeliveryStatusAndImages(Long orderId, String status, List<MultipartFile> files)
			throws IOException {

		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다."));

		// ✅ 0) 최우선 차단: 관리자승인(생산 전) 상태면 어떤 변경도 불가
		// TODO: ADMIN_CONFIRMED 를 실제 enum으로 치환
		if (order.getStatus() == OrderStatus.CONFIRMED) {
			boolean hasStatusRequest = (status != null && !status.isBlank());
			boolean hasFilesRequest = (files != null && files.stream().anyMatch(f -> f != null && !f.isEmpty()));
			if (hasStatusRequest || hasFilesRequest) {
				throw new IllegalStateException("관리자승인(생산 전) 상태에서는 배송완료 처리 및 증빙 업로드가 불가능합니다.");
			}
			return; // 혹시라도 여기까지 오면 종료
		}

		// ✅ 1) 상태 변경 로직: DELIVERY_DONE은 PRODUCTION_DONE에서만
		if (status != null && "DELIVERY_DONE".equals(status)) {
			if (order.getStatus() == OrderStatus.PRODUCTION_DONE) {
				order.setStatus(OrderStatus.DELIVERY_DONE);
				order.setUpdatedAt(LocalDateTime.now());
				orderRepository.save(order);
			} else if (order.getStatus() == OrderStatus.DELIVERY_DONE) {
				// 이미 완료: 변경 없음
			} else {
				// ✅ 생산완료가 아닌데 배송완료로 바꾸려는 시도 차단
				throw new IllegalStateException("배송 상태는 PRODUCTION_DONE 상태에서만 DELIVERY_DONE으로 변경할 수 있습니다.");
			}
		}

		// ✅ 2) 이미지 업로드 (컨펌 상태는 위에서 이미 차단됨)
		if (files != null && !files.isEmpty()) {
			Long memberId = order.getTask().getRequestedBy().getId();
			String datePath = LocalDate.now().toString(); // yyyy-MM-dd
			String subPath = "order/order/" + memberId + "/" + datePath + "/delivery";

			Path saveDir = Paths.get(baseUploadPath, subPath);
			Files.createDirectories(saveDir);

			for (MultipartFile file : files) {
				if (file == null || file.isEmpty())
					continue;

				String uuid = UUID.randomUUID().toString();
				String originalFilename = file.getOriginalFilename();
				String extension = getExtension(originalFilename);
				String storedName = uuid + (extension != null ? "." + extension : "");

				Path fullPath = saveDir.resolve(storedName);
				file.transferTo(fullPath.toFile());

				OrderImage image = new OrderImage();
				image.setType("DELIVERY");
				image.setFilename(originalFilename);
				image.setPath(fullPath.toString());
				image.setUrl("/upload/" + subPath + "/" + storedName);
				image.setOrder(order);

				orderImageRepository.save(image);
			}
		}
	}

	private String getExtension(String filename) {
		if (filename == null)
			return null;
		int idx = filename.lastIndexOf('.');
		if (idx < 0 || idx == filename.length() - 1)
			return null;
		return filename.substring(idx + 1);
	}
}
