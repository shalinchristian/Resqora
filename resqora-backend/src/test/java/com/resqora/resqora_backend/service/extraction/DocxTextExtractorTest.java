package com.resqora.resqora_backend.service.extraction;

import com.resqora.resqora_backend.exception.DocumentExtractionException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxTextExtractorTest {
    private final DocxTextExtractor extractor = new DocxTextExtractor();

    @Test
    void extractsParagraphAndTableTextFromDocx() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Grace Hopper");
            document.createParagraph().createRun().setText("Computer scientist");
            document.createTable(1, 1).getRow(0).getCell(0).setText("Java");
            document.write(output);
            docx = output.toByteArray();
        }

        String extractedText = extractor.extract(new ByteArrayInputStream(docx));

        assertTrue(extractedText.contains("Grace Hopper"));
        assertTrue(extractedText.contains("Computer scientist"));
        assertTrue(extractedText.contains("Java"));
    }

    @Test
    void returnsEmptyTextForValidTextlessDocx() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.write(output);
            docx = output.toByteArray();
        }

        assertEquals("", extractor.extract(new ByteArrayInputStream(docx)));
    }

    @Test
    void rejectsMalformedDocx() {
        assertThrows(
                DocumentExtractionException.class,
                () -> extractor.extract(new ByteArrayInputStream("not a docx".getBytes())));
    }
}