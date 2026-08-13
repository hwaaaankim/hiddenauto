package com.dev.HiddenBATHAuto.repository.notification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationCategory;
import com.dev.HiddenBATHAuto.model.notification.OrderNotification;

public interface OrderNotificationRepository extends JpaRepository<OrderNotification, Long> {

    @Query("""
            select count(n)
              from OrderNotification n
             where n.recipient.id = :recipientId
               and n.readAt is null
               and (n.webEnabled = true or n.importantEnabled = true)
            """)
    long countBellUnread(@Param("recipientId") Long recipientId);

    @Query("""
            select count(n)
              from OrderNotification n
             where n.recipient.id = :recipientId
               and n.importantEnabled = true
               and n.readAt is null
            """)
    long countImportantUnread(@Param("recipientId") Long recipientId);

    @Query("""
            select count(n)
              from OrderNotification n
             where n.recipient.id = :recipientId
               and n.importantEnabled = true
               and n.importantConfirmedAt is null
            """)
    long countPendingImportantConfirmation(@Param("recipientId") Long recipientId);

    @Query("""
            select n.category, count(n)
            from OrderNotification n
            where n.recipient.id = :recipientId
              and n.webEnabled = true
              and n.readAt is null
            group by n.category
            """)
    List<Object[]> countUnreadByCategory(@Param("recipientId") Long recipientId);

    /**
     * 일반 종 알림 조회입니다.
     * category가 없으면 종 알림 또는 중요알림 중 하나라도 활성화된 모든 미확인 알림을 노출하고,
     * category가 있으면 기존 동작과 동일하게 webEnabled=true인 해당 카테고리만 노출합니다.
     */
    @Query("""
            select n.id
              from OrderNotification n
             where n.recipient.id = :recipientId
               and n.readAt is null
               and (
                    (:category is null and (n.webEnabled = true or n.importantEnabled = true))
                    or
                    (:category is not null and n.webEnabled = true and n.category = :category)
               )
               and (:cursor is null or n.id < :cursor)
             order by n.id desc
            """)
    List<Long> findUnreadIds(
            @Param("recipientId") Long recipientId,
            @Param("category") OrderNotificationCategory category,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @Query("""
            select n.id
              from OrderNotification n
             where n.recipient.id = :recipientId
               and n.importantEnabled = true
               and n.readAt is null
               and (:cursor is null or n.id < :cursor)
             order by n.id desc
            """)
    List<Long> findUnreadImportantIds(
            @Param("recipientId") Long recipientId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @Query("""
            select n.id
              from OrderNotification n
             where n.recipient.id = :recipientId
               and n.importantEnabled = true
               and n.importantConfirmedAt is null
             order by n.id desc
            """)
    List<Long> findPendingImportantIds(
            @Param("recipientId") Long recipientId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "event", "event.fields", "order", "task", "recipient", "recipient.team"
    })
    @Query("""
            select distinct n
              from OrderNotification n
             where n.id in :ids
               and (n.webEnabled = true or n.importantEnabled = true)
            """)
    List<OrderNotification> findPageDetailsByIdIn(@Param("ids") Collection<Long> ids);

    @EntityGraph(attributePaths = {
            "event", "event.fields", "order", "task", "recipient", "recipient.team"
    })
    @Query("""
            select distinct n
              from OrderNotification n
             where n.id = :id
               and n.recipient.id = :recipientId
               and (n.webEnabled = true or n.importantEnabled = true)
            """)
    Optional<OrderNotification> findReadableByIdAndRecipient(
            @Param("id") Long id,
            @Param("recipientId") Long recipientId
    );

    @EntityGraph(attributePaths = {
            "event", "event.fields", "event.order", "event.order.task", "recipient", "recipient.team"
    })
    List<OrderNotification> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = {
            "event", "event.fields", "event.order", "event.order.task",
            "event.order.task.managedBy", "order", "task", "recipient", "recipient.team"
    })
    @Query("select n from OrderNotification n where n.id = :id")
    Optional<OrderNotification> findDeliveryTargetById(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "event", "event.fields", "event.order", "event.order.task",
            "event.order.task.managedBy", "order", "task", "recipient", "recipient.team"
    })
    @Query("""
            select distinct n
              from OrderNotification n
             where n.kakaoBatchKey = :batchKey
               and n.recipient.id = :recipientId
               and n.kakaoEnabled = true
             order by n.id asc
            """)
    List<OrderNotification> findKakaoBatch(
            @Param("batchKey") String kakaoBatchKey,
            @Param("recipientId") Long recipientId
    );

    @Query("""
            select min(n.id)
              from OrderNotification n
             where n.kakaoBatchKey = :batchKey
               and n.recipient.id = :recipientId
               and n.kakaoEnabled = true
            """)
    Long findKakaoBatchLeaderId(
            @Param("batchKey") String batchKey,
            @Param("recipientId") Long recipientId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrderNotification n
               set n.readAt = current_timestamp
             where n.recipient.id = :recipientId
               and (n.webEnabled = true or n.importantEnabled = true)
               and n.readAt is null
               and n.id in :notificationIds
            """)
    int markReadByIds(
            @Param("recipientId") Long recipientId,
            @Param("notificationIds") Collection<Long> notificationIds
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrderNotification n
               set n.importantConfirmedAt = current_timestamp
             where n.recipient.id = :recipientId
               and n.importantEnabled = true
               and n.importantConfirmedAt is null
               and n.id in :notificationIds
            """)
    int markImportantConfirmedByIds(
            @Param("recipientId") Long recipientId,
            @Param("notificationIds") Collection<Long> notificationIds
    );
}
