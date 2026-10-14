package com.resqora.resqora_backend.service.extraction;

import com.resqora.resqora_backend.exception.DocumentExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads PDF text page by page in reading order.
 * Two-column layouts (sidebar templates) are detected by a vertical gutter with no glyphs,
 * and each column is read top to bottom: left column first, then right.
 */
@Component
public class PdfTextExtractor {
    private static final float MIN_GUTTER_WIDTH = 8f;
    private static final int MIN_ONE_SIDED_ROWS = 2;

    public String extract(InputStream inputStream) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            StringBuilder text = new StringBuilder();
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                text.append(extractPage(document, pageIndex));
            }
            return text.toString();
        } catch (IOException | RuntimeException exception) {
            throw new DocumentExtractionException("Unable to extract text from PDF", exception);
        }
    }

    private String extractPage(PDDocument document, int pageIndex) throws IOException {
        PDPage page = document.getPage(pageIndex);
        List<TextPosition> glyphs = collectGlyphs(document, pageIndex + 1);
        PDRectangle box = page.getCropBox();
        Float gutter = findGutter(glyphs, box.getWidth());
        if (gutter == null) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            return stripper.getText(document);
        }
        PDFTextStripperByArea byArea = new PDFTextStripperByArea();
        byArea.setSortByPosition(true);
        byArea.addRegion("left", new Rectangle2D.Float(0, 0, gutter, box.getHeight()));
        byArea.addRegion("right", new Rectangle2D.Float(gutter, 0, box.getWidth() - gutter, box.getHeight()));
        byArea.extractRegions(page);
        return byArea.getTextForRegion("left") + System.lineSeparator() + byArea.getTextForRegion("right");
    }

    private List<TextPosition> collectGlyphs(PDDocument document, int pageNumber) throws IOException {
        List<TextPosition> glyphs = new ArrayList<>();
        PDFTextStripper collector = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition text) {
                if (!text.getUnicode().isBlank()) {
                    glyphs.add(text);
                }
            }
        };
        collector.setStartPage(pageNumber);
        collector.setEndPage(pageNumber);
        collector.getText(document);
        return glyphs;
    }

    /**
     * Returns the x of a column gutter, or null for a single-column page.
     * A gutter is the widest x-band in the middle 20-80% of the page that no glyph touches,
     * and the page must have rows with text only on the left and rows with text only on the right
     * (so right-aligned dates on a single-column resume are not mistaken for a second column).
     */
    static Float findGutter(List<TextPosition> glyphs, float pageWidth) {
        if (glyphs.size() < 40 || pageWidth <= 0) {
            return null;
        }
        int width = (int) Math.ceil(pageWidth);
        boolean[] used = new boolean[width + 1];
        for (TextPosition glyph : glyphs) {
            int from = Math.max(0, (int) Math.floor(glyph.getXDirAdj()));
            int to = Math.min(width, (int) Math.ceil(glyph.getXDirAdj() + glyph.getWidthDirAdj()));
            for (int x = from; x <= to; x++) {
                used[x] = true;
            }
        }
        int bestStart = -1;
        int bestLength = 0;
        int runStart = -1;
        int low = (int) (width * 0.2);
        int high = (int) (width * 0.8);
        for (int x = low; x <= high; x++) {
            if (!used[x]) {
                if (runStart < 0) {
                    runStart = x;
                }
                int length = x - runStart + 1;
                if (length > bestLength) {
                    bestLength = length;
                    bestStart = runStart;
                }
            } else {
                runStart = -1;
            }
        }
        if (bestLength < MIN_GUTTER_WIDTH) {
            return null;
        }
        float gutter = bestStart + bestLength / 2f;

        // rows keyed by rounded baseline: which sides have text?
        Map<Integer, int[]> rows = new TreeMap<>();
        for (TextPosition glyph : glyphs) {
            int[] sides = rows.computeIfAbsent(Math.round(glyph.getYDirAdj()), ignored -> new int[2]);
            sides[glyph.getXDirAdj() < gutter ? 0 : 1]++;
        }
        int leftOnly = 0;
        int rightOnly = 0;
        for (int[] sides : rows.values()) {
            if (sides[0] > 0 && sides[1] == 0) {
                leftOnly++;
            } else if (sides[1] > 0 && sides[0] == 0) {
                rightOnly++;
            }
        }
        // right-aligned dates on a one-column resume: few right-side glyphs and no right-only rows
        double leftShare = glyphs.stream().filter(glyph -> glyph.getXDirAdj() < gutter).count() / (double) glyphs.size();
        boolean bothSidesSubstantial = leftShare >= 0.1 && leftShare <= 0.75;
        return leftOnly >= 1 && rightOnly >= 1 && leftOnly + rightOnly >= MIN_ONE_SIDED_ROWS + 1 && bothSidesSubstantial
                ? gutter : null;
    }
}
