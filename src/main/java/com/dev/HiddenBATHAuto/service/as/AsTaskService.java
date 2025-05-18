package com.dev.HiddenBATHAuto.service.as;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.AsImage;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.repository.as.AsImageRepository;
import com.dev.HiddenBATHAuto.repository.as.AsTaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsTaskService {

    private final AsTaskRepository asTaskRepository;
    private final AsImageRepository asImageRepository;

    @Value("${spring.upload.path}")
    private String uploadPath;

    public AsTask submitAsTask(AsTask task, List<MultipartFile> images, Member member) throws IOException {
        task.setRequestedBy(member);
        task.setRequestedAt(LocalDateTime.now());
        task.setStatus(AsStatus.REQUESTED);
        AsTask savedTask = asTaskRepository.save(task);

        // 경로 생성
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Path dir = Paths.get(uploadPath, "as", String.valueOf(member.getId()), dateStr);
        Files.createDirectories(dir);

        for (MultipartFile file : images) {
            if (file.isEmpty()) continue;

            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = dir.resolve(filename);
            file.transferTo(filePath.toFile());

            AsImage image = new AsImage();
            image.setAsTask(savedTask);
            image.setFilename(filename);
            image.setPath(filePath.toString());
            image.setUrl("/uploads/as/" + member.getId() + "/" + dateStr + "/" + filename);
            image.setType("REQUEST");

            asImageRepository.save(image);
        }

        return savedTask;
    }
}

