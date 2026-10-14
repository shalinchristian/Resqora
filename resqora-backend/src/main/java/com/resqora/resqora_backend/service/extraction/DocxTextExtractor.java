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
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Node;

/**
 * Reads paragraphs, text boxes and tables (including nested ones) in document order.
 * Layout tables (nested tables or several paragraphs in a cell) are read cell by cell, one line per paragraph. Each table row becomes one line:
 * a two-cell "label | value" row becomes "Label: value" (so "Languages" is a skill group, not a heading),
 * any other row joins its cells with " | " so the parser's pipe fields pick them up.
 */
@Component
public class DocxTextExtractor {
    public String extract(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder text = new StringBuilder();
            appendElements(text, document.getBodyElements());
            return text.toString();
        } catch (IOException | RuntimeException exception) {
            throw new DocumentExtractionException("Unable to extract text from DOCX", exception);
        }
    }

    private void appendElements(StringBuilder text, List<IBodyElement> elements) {
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph paragraph) {
                for (String line : paragraphLines(paragraph)) {
                    appendLine(text, line);
                }
            } else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    if (isLayoutRow(row)) {
                        // a layout table (nested tables or several paragraphs per cell): read cell by cell
                        for (XWPFTableCell cell : row.getTableCells()) {
                            appendElements(text, cell.getBodyElements());
                        }
                    } else {
                        appendLine(text, rowText(row));
                    }
                }
            }
        }
    }

    // A simple data row has one paragraph per cell and no nested table.
    private static boolean isLayoutRow(XWPFTableRow row) {
        for (XWPFTableCell cell : row.getTableCells()) {
            long paragraphs = cell.getParagraphs().stream().filter(p -> !p.getText().isBlank()).count();
            if (!cell.getTables().isEmpty() || paragraphs > 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * The paragraph's own text as one line, plus one line per paragraph inside any text box (w:txbxContent).
     * Walks the XML so text-box wrappers (VML/DrawingML) add nothing, and mc:Fallback copies are skipped.
     */
    static List<String> paragraphLines(XWPFParagraph paragraph) {
        List<String> boxLines = new ArrayList<>();
        StringBuilder own = new StringBuilder();
        walk(paragraph.getCTP().getDomNode(), own, boxLines);
        List<String> lines = new ArrayList<>();
        lines.add(own.toString());
        lines.addAll(boxLines);
        return lines;
    }

    private static void walk(Node node, StringBuilder own, List<String> boxLines) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            String name = child.getLocalName();
            if (name == null) {
                continue;
            }
            switch (name) {
                case "Fallback" -> { }
                case "txbxContent" -> {
                    for (Node p = child.getFirstChild(); p != null; p = p.getNextSibling()) {
                        if ("p".equals(p.getLocalName())) {
                            StringBuilder line = new StringBuilder();
                            walk(p, line, boxLines);
                            boxLines.add(line.toString());
                        } else if ("tbl".equals(p.getLocalName())) {
                            StringBuilder line = new StringBuilder();
                            walk(p, line, boxLines);
                            boxLines.add(line.toString());
                        }
                    }
                }
                case "t" -> {
                    for (Node text = child.getFirstChild(); text != null; text = text.getNextSibling()) {
                        if (text.getNodeType() == Node.TEXT_NODE) {
                            own.append(text.getNodeValue());
                        }
                    }
                }
                case "tab" -> own.append(' ');
                default -> walk(child, own, boxLines);
            }
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
