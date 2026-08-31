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

import com.dev.HiddenBATHAuto.model.notification.AsNotification;

public interface AsNotificationRepository extends JpaRepository<AsNotification, Long> {

    @Query("""
            select count(n) from AsNotification n
             where n.recipient.id = :recipientId
               and n.readAt is null
               and (n.webEnabled = true or n.importantEnabled = true)
            """)
    long countBellUnread(@Param("recipientId") Long recipientId);

    @Query("""
            select count(n) from AsNotification n
             where n.recipient.id = :recipientId
               and n.importantEnabled = true
               and n.readAt is null
            """)
    long countImportantUnread(@Param("recipientId") Long recipientId);

    @Query("""
            select count(n) from AsNotification n
             where n.recipient.id = :recipientId
               and n.importantEnabled = true
               and n.importantConfirmedAt is null
            """)
    long countPendingImportantConfirmation(@Param("recipientId") Long recipientId);

    @Query("""
            select n.id from AsNotification n
             where n.recipient.id = :recipientId
               and n.readAt is null
               and (:importantOnly = false or n.importantEnabled = true)
               and (:importantOnly = true or n.webEnabled = true or n.importantEnabled = true)
               and (:cursor is null or n.id < :cursor)
             order by n.id desc
            """)
    List<Long> findUnreadIds(
            @Param("recipientId") Long recipientId,
            @Param("importantOnly") boolean importantOnly,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @Query("""
            select n.id from AsNotification n
             where n.recipient.id = :recipientId
               and n.importantEnabled = true
               and n.importantConfirmedAt is null
             order by n.id desc
            """)
    List<Long> findPendingImportantIds(@Param("recipientId") Long recipientId, Pageable pageable);

    @EntityGraph(attributePaths = {"event", "event.fields", "asTask", "asTask.assignedHandler", "recipient", "recipient.team"})
    @Query("""
            select distinct n from AsNotification n
             where n.id in :ids
               and (n.webEnabled = true or n.importantEnabled = true)
            """)
    List<AsNotification> findPageDetailsByIdIn(@Param("ids") Collection<Long> ids);

    @EntityGraph(attributePaths = {"event", "event.fields", "asTask", "asTask.assignedHandler", "recipient", "recipient.team"})
    @Query("""
            select distinct n from AsNotification n
             where n.id = :id
               and n.recipient.id = :recipientId
               and (n.webEnabled = true or n.importantEnabled = true)
            """)
    Optional<AsNotification> findReadableByIdAndRecipient(
            @Param("id") Long id,
            @Param("recipientId") Long recipientId
    );

    @EntityGraph(attributePaths = {"event", "event.fields", "event.asTask", "event.asTask.requestedBy", "event.asTask.assignedHandler", "recipient", "recipient.team"})
    @Query("select n from AsNotification n where n.id = :id")
    Optional<AsNotification> findDeliveryTargetById(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AsNotification n set n.readAt = current_timestamp
             where n.recipient.id = :recipientId
               and (n.webEnabled = true or n.importantEnabled = true)
               and n.readAt is null
               and n.id in :notificationIds
            """)
    int markReadByIds(@Param("recipientId") Long recipientId,
                      @Param("notificationIds") Collection<Long> notificationIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AsNotification n set n.importantConfirmedAt = current_timestamp
             where n.recipient.id = :recipientId
               and n.importantEnabled = true
               and n.importantConfirmedAt is null
               and n.id in :notificationIds
            """)
    int markImportantConfirmedByIds(@Param("recipientId") Long recipientId,
                                    @Param("notificationIds") Collection<Long> notificationIds);
}
