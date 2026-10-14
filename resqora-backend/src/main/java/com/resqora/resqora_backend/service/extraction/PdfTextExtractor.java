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
 * Multi-column layouts (sidebar templates, 3-column designs) are detected by vertical gutters with no glyphs,
 * and each column is read top to bottom, columns left to right.
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
        List<Float> gutters = findGutters(glyphs, box.getWidth());
        if (gutters.isEmpty()) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            return stripper.getText(document);
        }
        // one region per column, read left to right
        PDFTextStripperByArea byArea = new PDFTextStripperByArea();
        byArea.setSortByPosition(true);
        List<Float> edges = new ArrayList<>();
        edges.add(0f);
        edges.addAll(gutters);
        edges.add(box.getWidth());
        for (int column = 0; column + 1 < edges.size(); column++) {
            byArea.addRegion("c" + column, new Rectangle2D.Float(edges.get(column), 0,
                    edges.get(column + 1) - edges.get(column), box.getHeight()));
        }
        byArea.extractRegions(page);
        StringBuilder text = new StringBuilder();
        for (int column = 0; column + 1 < edges.size(); column++) {
            text.append(byArea.getTextForRegion("c" + column)).append(System.lineSeparator());
        }
        return text.toString();
    }

    /**
     * All column gutters on the page, left to right (empty for a single-column page).
     * Recursive cut: find one gutter, then look for further gutters inside each side,
     * so 3-column and sidebar + 2-column layouts are split too.
     */
    static List<Float> findGutters(List<TextPosition> glyphs, float pageWidth) {
        List<Float> gutters = new ArrayList<>();
        cut(glyphs, 0, pageWidth, 40, gutters);
        gutters.sort(Float::compare);
        return gutters;
    }

    private static void cut(List<TextPosition> glyphs, float from, float to, int minGlyphs, List<Float> gutters) {
        List<TextPosition> inside = glyphs.stream()
                .filter(glyph -> glyph.getXDirAdj() >= from && glyph.getXDirAdj() < to)
                .toList();
        Float gutter = findGutter(inside, from, to, minGlyphs);
        if (gutter == null) {
            return;
        }
        gutters.add(gutter);
        cut(inside, from, gutter, 15, gutters);
        cut(inside, gutter, to, 15, gutters);
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
     * A gutter is the widest passing x-band in the middle 20-80% of the page that no glyph touches,
     * and the page must have rows with text only on the left and rows with text only on the right
     * (so right-aligned dates on a single-column resume are not mistaken for a second column).
     */
    static Float findGutter(List<TextPosition> glyphs, float pageWidth) {
        return findGutter(glyphs, 0, pageWidth, 40);
    }

    // the same test inside the x-range [left, right) of the page
    static Float findGutter(List<TextPosition> glyphs, float left, float right, int minGlyphs) {
        float pageWidth = right - left;
        if (glyphs.size() < minGlyphs || pageWidth <= 0) {
            return null;
        }
        int width = (int) Math.ceil(pageWidth);
        boolean[] used = new boolean[width + 1];
        for (TextPosition glyph : glyphs) {
            int from = Math.max(0, (int) Math.floor(glyph.getXDirAdj() - left));
            int to = Math.min(width, (int) Math.ceil(glyph.getXDirAdj() - left + glyph.getWidthDirAdj()));
            for (int x = from; x <= to; x++) {
                used[x] = true;
            }
        }
        // every empty band in the middle 20-80%, widest first; the first one that passes the column test wins
        List<int[]> bands = new ArrayList<>();
        int runStart = -1;
        int low = (int) (width * 0.2);
        int high = (int) (width * 0.8);
        for (int x = low; x <= high + 1; x++) {
            boolean free = x <= high && !used[x];
            if (free && runStart < 0) {
                runStart = x;
            } else if (!free && runStart >= 0) {
                if (x - runStart >= MIN_GUTTER_WIDTH) {
                    bands.add(new int[] {runStart, x - runStart});
                }
                runStart = -1;
            }
        }
        bands.sort((first, second) -> Integer.compare(second[1], first[1]));
        for (int[] band : bands) {
            float gutter = left + band[0] + band[1] / 2f;
            if (isColumnGutter(glyphs, gutter)) {
                return gutter;
            }
        }
        return null;
    }

    private static boolean isColumnGutter(List<TextPosition> glyphs, float gutter) {
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
        return leftOnly >= 1 && rightOnly >= 1 && leftOnly + rightOnly >= MIN_ONE_SIDED_ROWS + 1 && bothSidesSubstantial;
    }
}
