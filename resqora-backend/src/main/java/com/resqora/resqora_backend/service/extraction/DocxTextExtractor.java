package com.resqora.resqora_backend.service.extraction;

import com.resqora.resqora_backend.exception.DocumentExtractionException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class DocxTextExtractor {
    public String extract(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> appendLine(text, paragraph.getText()));
            document.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> appendLine(text, cell.getText()))));
            return text.toString();
        } catch (IOException | RuntimeException exception) {
            throw new DocumentExtractionException("Unable to extract text from DOCX", exception);
        }
    }

    private void appendLine(StringBuilder text, String line) {
        if (!line.isBlank()) {
            if (text.length() > 0) {
                text.append(System.lineSeparator());
            }
            text.append(line);
        }
    }
}