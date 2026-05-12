package com.dev.HiddenBATHAuto.model.process;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_process_unit",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_process_unit_key", columnNames = {"step_id", "unit_key"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false)
    private ProcessStep step;

    @Column(name = "unit_key", nullable = false, length = 80)
    private String unitKey;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "use_yn", nullable = false)
    private boolean useYn = true;

    @OneToOne(mappedBy = "unit", cascade = CascadeType.ALL, orphanRemoval = true)
    private ProcessQuestion question;

    @OneToMany(mappedBy = "sourceUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC")
    @BatchSize(size = 50)
    private List<ProcessUnitBranch> branches = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void setQuestion(ProcessQuestion question) {
        this.question = question;
        if (question != null) {
            question.setUnit(this);
        }
    }

    public void addBranch(ProcessUnitBranch branch) {
        this.branches.add(branch);
        branch.setSourceUnit(this);
    }

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