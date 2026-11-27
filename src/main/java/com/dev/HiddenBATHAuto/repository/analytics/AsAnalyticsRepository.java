package com.dev.HiddenBATHAuto.repository.analytics;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
/** Scalar projection avoids fetching histories, attachments and unrelated personal details. */
public interface AsAnalyticsRepository extends Repository<AsTask,Long> {
 interface Entry {
  Long getId(); LocalDateTime getRequestedAt(); String getSubject(); String getProductName();
  Integer getPrice(); Boolean getPaymentCollected(); AsStatus getStatus(); Long getCompanyId(); String getCompanyName();
 }
 @Query("""
 select a.id as id,a.requestedAt as requestedAt,a.subject as subject,a.productName as productName,
 a.price as price,a.paymentCollected as paymentCollected,a.status as status,c.id as companyId,c.companyName as companyName
 from AsTask a left join a.requestedBy m left join m.company c
 where (:from is null or a.requestedAt>=:from) and (:to is null or a.requestedAt<:to) order by a.id
 """)
 List<Entry> findForAnalytics(@Param("from") LocalDateTime from,@Param("to") LocalDateTime to);
}
