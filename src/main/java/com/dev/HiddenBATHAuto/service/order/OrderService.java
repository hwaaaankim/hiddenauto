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

	public void updateDeliveryStatusAndImages(Long orderId, String status, List<MultipartFile> files)
			throws IOException {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new IllegalArgumentException("해당 주문이 존재하지 않습니다."));

		// ✅ 상태 변경 로직
		if (status != null && "DELIVERY_DONE".equals(status)) {
			if (order.getStatus() == OrderStatus.PRODUCTION_DONE) {
				order.setStatus(OrderStatus.DELIVERY_DONE);
				order.setUpdatedAt(LocalDateTime.now());
				orderRepository.save(order);
			} else if (order.getStatus() == OrderStatus.DELIVERY_DONE) {
				// 이미 완료된 경우는 무시
			} else {
				throw new IllegalStateException("배송 상태는 PRODUCTION_DONE 상태에서만 변경할 수 있습니다.");
			}
		}

		// ✅ 이미지 업로드는 상태 상관없이 허용
		if (files != null && !files.isEmpty()) {
			Long memberId = order.getTask().getRequestedBy().getId();
			String datePath = LocalDate.now().toString(); // yyyy-MM-dd
			String subPath = "order/order/" + memberId + "/" + datePath + "/delivery";

			Path saveDir = Paths.get(baseUploadPath, subPath);
			Files.createDirectories(saveDir);

			for (MultipartFile file : files) {
				if (!file.isEmpty()) {
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
	}

	private String getExtension(String filename) {
		if (filename == null)
			return null;
		int dotIndex = filename.lastIndexOf(".");
		return (dotIndex != -1) ? filename.substring(dotIndex + 1) : null;
	}
}
