package com.dev.HiddenBATHAuto.repository.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationRecipientGroup;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.model.notification.OrderNotificationPolicy;

public interface OrderNotificationPolicyRepository extends JpaRepository<OrderNotificationPolicy, Long> {

    Optional<OrderNotificationPolicy> findBySourceAreaAndActionAndRecipientGroup(
            OrderChangeSourceArea sourceArea,
            OrderNotificationAction action,
            OrderNotificationRecipientGroup recipientGroup
    );

    List<OrderNotificationPolicy> findBySourceAreaAndAction(
            OrderChangeSourceArea sourceArea,
            OrderNotificationAction action
    );
}
