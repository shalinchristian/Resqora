package com.resqora.resqora_backend.service.extraction;

import com.resqora.resqora_backend.exception.DocumentExtractionException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfTextExtractorTest {
    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void extractsTextFromMultiplePdfPages() throws Exception {
        byte[] pdf = createPdf("Ada Lovelace", "Software engineer");

        String extractedText = extractor.extract(new ByteArrayInputStream(pdf));

        assertTrue(extractedText.contains("Ada Lovelace"));
        assertTrue(extractedText.contains("Software engineer"));
    }

    @Test
    void returnsEmptyTextForValidTextlessPdf() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }

        assertEquals("", extractor.extract(new ByteArrayInputStream(pdf)).trim());
    }

    @Test
    void rejectsMalformedPdf() {
        assertThrows(
                DocumentExtractionException.class,
                () -> extractor.extract(new ByteArrayInputStream("not a pdf".getBytes())));
    }

    private byte[] createPdf(String firstPageText, String secondPageText) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            addTextPage(document, font, firstPageText);
            addTextPage(document, font, secondPageText);
            document.save(output);
            return output.toByteArray();
        }
    }

    private void addTextPage(PDDocument document, PDType1Font font, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream.setFont(font, 12);
            contentStream.newLineAtOffset(50, 700);
            contentStream.showText(text);
            contentStream.endText();
        }
    }
}