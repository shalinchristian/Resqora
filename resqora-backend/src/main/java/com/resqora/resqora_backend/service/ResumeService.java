package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.dto.resume.ResumeUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeService {
    public ResumeUploadResponse uploadResponse(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return new ResumeUploadResponse("Resume uploaded successfully", filename);
    }
}
