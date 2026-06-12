package com.dev.HiddenBATHAuto.rag.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

public class RagStoredMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final Path path;
    private final long size;

    public RagStoredMultipartFile(String name,
                                  String originalFilename,
                                  String contentType,
                                  Path path,
                                  long size) {
        this.name = StringUtils.hasText(name) ? name : "file";
        this.originalFilename = StringUtils.hasText(originalFilename) ? originalFilename : "upload";
        this.contentType = contentType;
        this.path = path;
        this.size = size;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return size <= 0;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
        Files.copy(path, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public Path path() {
        return path;
    }
}
