package com.resqora.resqora_backend.service.extraction;

import com.resqora.resqora_backend.exception.DocumentExtractionException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Service
public class DocumentTextExtractionService {
    private final PdfTextExtractor pdfTextExtractor;
    private final DocxTextExtractor docxTextExtractor;

    public DocumentTextExtractionService(
            PdfTextExtractor pdfTextExtractor,
            DocxTextExtractor docxTextExtractor) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.docxTextExtractor = docxTextExtractor;
    }

    public String extract(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("Only PDF and DOCX files are supported");
        }

        // Both formats go through the same normaliser, so the saved text of a PDF and a DOCX
        // of the same resume is the same text (and gets the same score).
        try (InputStream inputStream = file.getInputStream()) {
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                return ResumeTextNormalizer.normalize(pdfTextExtractor.extract(inputStream));
            }
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
                return ResumeTextNormalizer.normalize(docxTextExtractor.extract(inputStream));
            }
            throw new IllegalArgumentException("Only PDF and DOCX files are supported");
        } catch (IOException exception) {
            throw new DocumentExtractionException("Unable to read uploaded document", exception);
        }
    }
}