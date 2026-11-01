package com.dev.HiddenBATHAuto.service.dispatch;

import java.util.Objects;
import com.dev.HiddenBATHAuto.utils.DeliveryAddressNormalizationUtil;

/** Dispatch-only policy: changing a method must not select a different destination. */
final class DispatchDeliveryAddressPolicy {
    private DispatchDeliveryAddressPolicy() {}

    record AddressInput(String zipCode, String doName, String siName, String guName,
                        String roadAddress, String detailAddress) {
        AddressInput {
            zipCode = clean(zipCode); doName = clean(doName); siName = clean(siName);
            guName = clean(guName); roadAddress = clean(roadAddress); detailAddress = clean(detailAddress);
        }
        boolean hasDaumAddress() { return zipCode != null && roadAddress != null; }
        boolean hasDestination() { return roadAddress != null || detailAddress != null; }
        boolean hasMeaningfulAddress() {
            return DeliveryAddressNormalizationUtil.hasAnyMeaningfulAddressText(
                    doName, siName, guName, roadAddress, detailAddress);
        }
        boolean sameStoredValues(AddressInput other) {
            return sameAddressCore(other) && Objects.equals(detailAddress, other.detailAddress);
        }
        boolean sameAddressCore(AddressInput other) {
            return other != null && Objects.equals(zipCode, other.zipCode)
                    && Objects.equals(doName, other.doName) && Objects.equals(siName, other.siName)
                    && Objects.equals(guName, other.guName) && Objects.equals(roadAddress, other.roadAddress);
        }
        String display() {
            return DeliveryAddressNormalizationUtil.build(
                    zipCode, doName, siName, guName, roadAddress, detailAddress).display();
        }
    }

    static AddressInput actual(AddressInput site, AddressInput general) {
        // Match Order.getActualDeliveryAddressDisplay(), including legacy freight rows.
        return site.hasMeaningfulAddress() ? site : general;
    }

    static AddressInput resolveRequest(AddressInput submitted, AddressInput savedTarget,
                                       AddressInput actual, boolean typeChanged, boolean searched,
                                       Boolean keepDeliveryAddress, Long orderId) {
        // A method-only request never replaces the destination with another storage column
        // or a stale address from an older screen. Validate the current persisted destination.
        if (Boolean.TRUE.equals(keepDeliveryAddress)) {
            return resolve(actual, actual, actual, false, false, orderId);
        }
        return resolve(submitted, savedTarget, actual,
                keepDeliveryAddress == null && typeChanged, searched, orderId);
    }

    static AddressInput resolve(AddressInput submitted, AddressInput savedTarget,
                                AddressInput actual, boolean typeChanged, boolean searched, Long orderId) {
        AddressInput target = submitted;
        // Older clients send the untouched target storage column when switching the method.
        // That is not a request to replace a site destination with the company's address.
        if (typeChanged && !searched && (!submitted.hasMeaningfulAddress()
                || submitted.sameStoredValues(savedTarget))) target = actual;

        if (!target.hasDestination()) {
            throw new IllegalArgumentException("기존 배송지에도 도로명·상세주소가 없습니다. 배송지를 확인해 입력해 주세요. orderId=" + orderId);
        }
        // Retain an existing legacy address even if its postcode was never stored.
        // Editing only its detail does not require searching the same road again.
        if (target.sameAddressCore(actual)) return target;
        if (!target.hasDaumAddress()) {
            throw new IllegalArgumentException("새 배송주소의 우편번호와 도로명주소를 검색해 입력해 주세요. orderId=" + orderId);
        }
        if (!searched) {
            throw new IllegalArgumentException("실제 배송지가 변경되었습니다. Daum 우편번호 검색으로 새 배송지를 선택해 주세요. orderId=" + orderId);
        }
        return target;
    }

    private static String clean(String value) {
        String result = DeliveryAddressNormalizationUtil.cleanAddressComponent(value);
        return result.isBlank() ? null : result;
    }
}
