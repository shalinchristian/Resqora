package com.resqora.resqora_backend.service.extraction;

import com.resqora.resqora_backend.exception.DocumentExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class PdfTextExtractor {
    public String extract(InputStream inputStream) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException | RuntimeException exception) {
            throw new DocumentExtractionException("Unable to extract text from PDF", exception);
        }
    }
}