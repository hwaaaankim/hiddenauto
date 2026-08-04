package com.dev.HiddenBATHAuto.repository.notification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationCategory;
import com.dev.HiddenBATHAuto.model.notification.OrderNotification;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, Long> {

    long countByRecipient_IdAndReadAtIsNull(Long recipientId);

    @Query("""
            select n.category, count(n)
            from OrderNotification n
            where n.recipient.id = :recipientId
              and n.readAt is null
            group by n.category
            """)
    List<Object[]> countUnreadByCategory(@Param("recipientId") Long recipientId);

    @EntityGraph(attributePaths = {
            "event", "order", "task", "recipient"
    })
    Page<OrderNotification> findByRecipient_IdOrderByCreatedAtDescIdDesc(
            Long recipientId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "event", "order", "task", "recipient"
    })
    Page<OrderNotification> findByRecipient_IdAndCategoryOrderByCreatedAtDescIdDesc(
            Long recipientId,
            OrderNotificationCategory category,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "event", "event.fields", "order", "task", "recipient"
    })
    Optional<OrderNotification> findByIdAndRecipient_Id(Long id, Long recipientId);

    @EntityGraph(attributePaths = {
            "event", "event.fields", "event.order", "event.order.task", "recipient"
    })
    List<OrderNotification> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = {
            "event", "event.fields", "event.order", "event.order.task",
            "event.order.task.managedBy", "recipient"
    })
    @Query("select n from OrderNotification n where n.id = :id")
    Optional<OrderNotification> findDeliveryTargetById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrderNotification n
               set n.readAt = current_timestamp
             where n.recipient.id = :recipientId
               and n.readAt is null
               and (:category is null or n.category = :category)
            """)
    int markAllRead(
            @Param("recipientId") Long recipientId,
            @Param("category") OrderNotificationCategory category
    );
}
