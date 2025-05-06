package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_as_image")
@Data
public class AsImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type; // "REQUEST", "RESULT"
    private String filename;
    private String path;
    private String url;

    @ManyToOne
    private AsTask asTask;

    private LocalDateTime uploadedAt = LocalDateTime.now();
}