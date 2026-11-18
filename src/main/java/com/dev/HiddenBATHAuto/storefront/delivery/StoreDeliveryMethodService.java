package com.dev.HiddenBATHAuto.storefront.delivery;

import com.dev.HiddenBATHAuto.model.storefront.StoreDeliveryMethod;
import com.dev.HiddenBATHAuto.repository.storefront.StoreDeliveryMethodRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreDeliveryMethodService {
    private final StoreDeliveryMethodRepository repository;

    @Transactional
    public void save(Long id, Long version, String name, int deliveryFee, int packingFee, String description) {
        String cleanName = name.strip();
        String cleanDescription = description.strip();
        if (cleanName.isEmpty() || cleanName.length() > 100 || cleanDescription.length() > 1000
                || deliveryFee < 0 || packingFee < 0) {
            throw new IllegalArgumentException("배송수단명, 비용 또는 설명의 입력 범위를 확인해 주세요.");
        }
        StoreDeliveryMethod method = id == null ? new StoreDeliveryMethod() : current(id, version);
        method.setName(cleanName);
        method.setDeliveryFee(deliveryFee);
        method.setPackingFee(packingFee);
        method.setDescription(cleanDescription);
        repository.saveAndFlush(method);
    }

    @Transactional
    public void delete(Long id, Long version) {
        repository.delete(current(id, version));
        repository.flush();
    }

    private StoreDeliveryMethod current(Long id, Long version) {
        StoreDeliveryMethod method = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("이미 삭제된 배송수단입니다."));
        if (!Objects.equals(method.getVersion(), version)) {
            throw new IllegalArgumentException("다른 곳에서 수정한 배송수단입니다. 최신 내용을 확인해 주세요.");
        }
        return method;
    }
}
