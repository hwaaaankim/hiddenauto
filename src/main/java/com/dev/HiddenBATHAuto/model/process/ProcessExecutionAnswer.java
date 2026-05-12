package com.dev.HiddenBATHAuto.model.process;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.dev.HiddenBATHAuto.enums.process.ProcessAnswerType;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
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
        name = "tb_process_execution_answer",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_process_execution_answer_session_unit",
                        columnNames = {"session_id", "unit_key"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessExecutionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ProcessExecutionSession session;

    @Column(name = "unit_key", nullable = false, length = 80)
    private String unitKey;

    @Column(name = "question_text_snapshot", length = 500)
    private String questionTextSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type", nullable = false, length = 40)
    private ProcessAnswerType answerType;

    @Column(name = "selected_option_key", length = 80)
    private String selectedOptionKey;

    @Column(name = "selected_option_label", length = 100)
    private String selectedOptionLabel;

    @Lob
    @Column(name = "answer_value_json")
    private String answerValueJson;

    @Column(name = "display_answer_text", length = 1000)
    private String displayAnswerText;

    @OneToMany(mappedBy = "answer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @BatchSize(size = 50)
    private List<ProcessExecutionFile> files = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void addFile(ProcessExecutionFile file) {
        this.files.add(file);
        file.setAnswer(this);
    }

    public void clearFiles() {
        this.files.clear();
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