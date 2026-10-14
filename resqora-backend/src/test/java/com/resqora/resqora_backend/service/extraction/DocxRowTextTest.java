package com.resqora.resqora_backend.service.extraction;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocxRowTextTest {
    @Test
    void labelRowBecomesColonLineAndOtherRowsArePiped() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFTable table = document.createTable(2, 3);
            table.getRow(0).getCell(0).setText("Languages:");
            table.getRow(0).getCell(1).setText("Java, SQL");
            table.getRow(1).getCell(0).setText("Swiggy");
            table.getRow(1).getCell(1).setText("Android Developer");
            table.getRow(1).getCell(2).setText("2020 - Present");
            assertEquals("Languages: Java, SQL", DocxTextExtractor.rowText(table.getRow(0)));
            assertEquals("Swiggy | Android Developer | 2020 - Present", DocxTextExtractor.rowText(table.getRow(1)));
        }
    }
}
