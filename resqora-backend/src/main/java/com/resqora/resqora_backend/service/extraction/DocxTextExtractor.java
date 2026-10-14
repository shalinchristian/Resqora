package com.resqora.resqora_backend.service.extraction;

import com.resqora.resqora_backend.exception.DocumentExtractionException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Reads paragraphs and tables in document order. Each table row becomes one line:
 * a two-cell "label | value" row becomes "Label: value" (so "Languages" is a skill group, not a heading),
 * any other row joins its cells with " | " so the parser's pipe fields pick them up.
 */
@Component
public class DocxTextExtractor {
    public String extract(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder text = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendLine(text, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        appendLine(text, rowText(row));
                    }
                }
            }
            return text.toString();
        } catch (IOException | RuntimeException exception) {
            throw new DocumentExtractionException("Unable to extract text from DOCX", exception);
        }
    }

    static String rowText(XWPFTableRow row) {
        List<String> cells = row.getTableCells().stream()
                .map(XWPFTableCell::getText)
                .map(cell -> cell.replaceAll("\\s+", " ").trim())
                .filter(cell -> !cell.isEmpty())
                .toList();
        String label = cells.isEmpty() ? "" : cells.get(0).replaceFirst("\\s*:$", "");
        if (cells.size() == 2 && isLabel(label)) {
            return label + ": " + cells.get(1);
        }
        return String.join(" | ", cells);
    }

    private static boolean isLabel(String cell) {
        return cell.split(" ").length <= 3 && !cell.matches(".*\\d.*");
    }

    private void appendLine(StringBuilder text, String line) {
        if (line != null && !line.isBlank()) {
            if (text.length() > 0) {
                text.append(System.lineSeparator());
            }
            text.append(line);
        }
    }
}
