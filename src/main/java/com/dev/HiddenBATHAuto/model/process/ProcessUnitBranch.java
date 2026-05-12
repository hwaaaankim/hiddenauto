package com.dev.HiddenBATHAuto.model.process;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.process.ProcessBranchTargetMode;
import com.dev.HiddenBATHAuto.enums.process.ProcessBranchType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
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
        name = "tb_process_unit_branch",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_process_branch_key", columnNames = {"source_unit_id", "branch_key"})
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessUnitBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_unit_id", nullable = false)
    private ProcessUnit sourceUnit;

    @Column(name = "branch_key", nullable = false, length = 80)
    private String branchKey;

    @Column(nullable = false, length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "branch_type", nullable = false, length = 40)
    private ProcessBranchType branchType = ProcessBranchType.DEFAULT;

    @Column(name = "answer_option_key", length = 80)
    private String answerOptionKey;

    @Lob
    @Column(name = "condition_json")
    private String conditionJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_mode", nullable = false, length = 40)
    private ProcessBranchTargetMode targetMode = ProcessBranchTargetMode.AUTO_NEXT;

    @Column(name = "target_unit_key", length = 80)
    private String targetUnitKey;

    @Column(nullable = false)
    private int priority;

    @Column(name = "use_yn", nullable = false)
    private boolean useYn = true;

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