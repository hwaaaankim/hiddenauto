package com.dev.HiddenBATHAuto.repository.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.enums.notification.AsNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationRecipientGroup;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationSourceArea;
import com.dev.HiddenBATHAuto.model.notification.AsNotificationPolicy;

public interface AsNotificationPolicyRepository extends JpaRepository<AsNotificationPolicy, Long> {
    Optional<AsNotificationPolicy> findBySourceAreaAndActionAndRecipientGroup(
            AsNotificationSourceArea sourceArea,
            AsNotificationAction action,
            AsNotificationRecipientGroup recipientGroup
    );

    List<AsNotificationPolicy> findBySourceAreaAndAction(
            AsNotificationSourceArea sourceArea,
            AsNotificationAction action
    );
}
