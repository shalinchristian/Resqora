package com.resqora.resqora_backend.dto.resume;

public record ResumeUploadResponse(
	Long id,
	String message,
	String fileName,
	String documentType,
	String extractedText) {

}
