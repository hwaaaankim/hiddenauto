package com.dev.HiddenBATHAuto.model.process;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.process.ProcessInputValueType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_process_answer_field",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_process_answer_field_key", columnNames = {"question_id", "field_key"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessAnswerField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private ProcessQuestion question;

    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    @Column(nullable = false, length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_value_type", nullable = false, length = 30)
    private ProcessInputValueType inputValueType = ProcessInputValueType.TEXT;

    @Column(length = 200)
    private String placeholder;

    @Column(name = "unit_text", length = 30)
    private String unitText;

    @Column(name = "required_yn", nullable = false)
    private boolean requiredYn = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}