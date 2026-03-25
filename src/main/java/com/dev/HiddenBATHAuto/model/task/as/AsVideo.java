package com.dev.HiddenBATHAuto.model.task.as;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.model.task.AsTask;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_as_video")
@Data
public class AsVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** REQUEST / RESULT */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 100)
    private String contentType;

    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "as_task_id", nullable = false)
    private AsTask asTask;

    private LocalDateTime uploadedAt = LocalDateTime.now();
}