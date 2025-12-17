package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.model.auth.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tb_as_task_schedule", uniqueConstraints = {
		// 한 태스크는 달력에 1번만 등록 가능
		@UniqueConstraint(name = "uk_as_schedule_task", columnNames = { "as_task_id" }),
		// 같은 날짜 내에서는 index 중복 금지
		@UniqueConstraint(name = "uk_as_schedule_date_order", columnNames = { "scheduled_date",
				"order_index" }) }, indexes = { @Index(name = "idx_as_schedule_date", columnList = "scheduled_date"),
						@Index(name = "idx_as_schedule_created_at", columnList = "created_at") })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsTaskSchedule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 어떤 AS 업무를 달력에 올렸는지
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "as_task_id", nullable = false)
	private AsTask asTask;

	// 달력에 등록된 날짜 (LocalDate)
	@Column(name = "scheduled_date", nullable = false)
	private LocalDate scheduledDate;

	// 해당 날짜 내 순서(0..n)
	@Column(name = "order_index", nullable = false)
	private int orderIndex;

	// 등록한 사람(보통 AS팀원)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_member_id")
	private Member createdBy;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void prePersist() {
		if (createdAt == null)
			createdAt = LocalDateTime.now();
	}
}