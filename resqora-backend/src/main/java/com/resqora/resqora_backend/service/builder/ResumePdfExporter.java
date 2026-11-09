package com.resqora.resqora_backend.service.builder;

import com.resqora.resqora_backend.dto.builder.BuilderExportRequest;
import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.HorizontalHeaderTable;
import org.apache.fontbox.ttf.OS2WindowsMetricsTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.resqora.resqora_backend.service.builder.ResumeDocxExporter.bullets;
import static com.resqora.resqora_backend.service.builder.ResumeDocxExporter.clean;
import static com.resqora.resqora_backend.service.builder.ResumeDocxExporter.has;

// Same templates, section order and type scale as the DOCX, drawn straight to PDF with embedded open fonts
// (Carlito for the Calibri look, Liberation Serif for the Times look). Every word is real, selectable text;
// the header band is a filled rectangle behind it. Content that doesn't fit flows onto the next page.
@Component
public class ResumePdfExporter {
    // A4, 0.5" margins, sizes in points
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 36;
    private static final float TEXT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    private static final float NAME_PT = 24;
    private static final float HEADLINE_PT = 12;
    private static final float HEADING_PT = 12;
    private static final float BODY_PT = 11;
    private static final float SMALL_CAPS_SCALE = 0.8f;

    private static final float UNDER_NAME = 2;
    private static final float BEFORE_HEADING = 10;
    private static final float RULE_GAP = 1;
    private static final float RULE_WIDTH = 0.75f;
    private static final float AFTER_HEADING = 6;
    private static final float FIRST_BULLET = 2;
    private static final float BETWEEN_BULLETS = 3;
    private static final float BETWEEN_ENTRIES = 8;
    private static final float DATE_GAP = 12;

    // bullet sits 6pt in, its text 18pt in (same as the DOCX list indent)
    private static final float BULLET_X = 6;
    private static final float BULLET_TEXT_X = 18;

    private static final float BAND_PAD_X = 9;
    private static final float BAND_PAD_TOP = 8;
    private static final float BAND_PAD_BOTTOM = 10;
    private static final Color BAND_FILL = new Color(0x1F, 0x3A, 0x5F);
    private static final Color TEXT = Color.BLACK;

    private static final Map<String, byte[]> FONT_FILES = new ConcurrentHashMap<>();

    public byte[] export(BuilderExportRequest request) {
        ResumeTemplate template = ResumeTemplate.fromId(request.templateId());
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new Writer(document, template).write(request);
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle(clean(request.name()) + " – Resume");
            info.setAuthor(clean(request.name()));
            info.setCreator("Resqora");
            document.save(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write resume PDF", exception);
        }
    }

    private static byte[] fontFile(String file) {
        return FONT_FILES.computeIfAbsent(file, name -> {
            try (InputStream in = ResumePdfExporter.class.getResourceAsStream("/fonts/" + name)) {
                if (in == null) {
                    throw new IllegalStateException("Missing font resource " + name);
                }
                return in.readAllBytes();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private enum Style { REGULAR, BOLD, ITALIC }

    private enum Align { LEFT, CENTER }

    // One embedded font plus what's needed to measure it and to know which characters it can draw
    private static final class Face {
        final PDType0Font font;
        final CmapLookup cmap;
        final float lineFactor;
        final float ascentFactor;
        private final PDDocument document;
        private final String fallbackFile;
        private Face fallback;

        Face(PDDocument document, String file) throws IOException {
            this(document, file, null);
        }

        Face(PDDocument document, String file, String fallbackFile) throws IOException {
            this.document = document;
            this.fallbackFile = fallbackFile;
            TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBuffer(fontFile(file)));
            // no ligatures: "ft"/"ti" glyphs would extract as junk characters and break ATS keyword matching
            ttf.setEnableGsub(false);
            this.font = PDType0Font.load(document, ttf, true);
            this.cmap = ttf.getUnicodeCmapLookup();
            float unitsPerEm = ttf.getUnitsPerEm();
            OS2WindowsMetricsTable os2 = ttf.getOS2Windows();
            HorizontalHeaderTable hhea = ttf.getHorizontalHeader();
            // Word's "single" line height: the larger of the Windows and the hhea metrics
            float win = os2 == null ? 0 : (os2.getWinAscent() + os2.getWinDescent()) / unitsPerEm;
            float mac = (hhea.getAscender() - hhea.getDescender() + hhea.getLineGap()) / unitsPerEm;
            this.lineFactor = Math.max(win, mac);
            this.ascentFactor = (os2 == null ? hhea.getAscender() : Math.max(os2.getWinAscent(), hhea.getAscender()))
                    / unitsPerEm;
        }

        boolean covers(int codePoint) {
            return cmap.getGlyphId(codePoint) > 0;
        }

        // loaded (and embedded) only when a character actually needs it
        Face fallback() {
            if (fallback == null && fallbackFile != null) {
                try {
                    fallback = new Face(document, fallbackFile);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }
            return fallback;
        }

        float lineHeight(float size) {
            return lineFactor * size;
        }
    }

    private record Chunk(Face face, String text) {
    }

    private static final class Writer {
        private final PDDocument document;
        private final ResumeTemplate template;
        private final Map<Style, Face> faces = new java.util.EnumMap<>(Style.class);
        private PDPageContentStream content;
        private float y; // distance from the top of the page to the top of the next line

        Writer(PDDocument document, ResumeTemplate template) {
            this.document = document;
            this.template = template;
        }

        void write(BuilderExportRequest request) throws IOException {
            loadFonts();
            newPage();
            try {
                writeHeader(request);
                for (ResumeTemplate.Section section : template.sections()) {
                    switch (section) {
                        case SUMMARY -> writeSummary(request.summary());
                        case EXPERIENCE -> writeExperience(request.experience());
                        case EDUCATION -> writeEducation(request.education());
                        case PROJECTS -> writeProjects(request.projects());
                        case SKILLS -> writeSkills(request.skills());
                        case CERTIFICATIONS -> writeCertifications(request.certifications());
                    }
                }
            } finally {
                content.close();
            }
        }

        private void loadFonts() throws IOException {
            if (template.font().equals("Times New Roman")) {
                // Liberation Serif has no ₹ (and a few other signs); those come from Carlito
                faces.put(Style.REGULAR, new Face(document, "LiberationSerif-Regular.ttf", "Carlito-Regular.ttf"));
                faces.put(Style.BOLD, new Face(document, "LiberationSerif-Bold.ttf", "Carlito-Bold.ttf"));
                faces.put(Style.ITALIC, new Face(document, "LiberationSerif-Italic.ttf", "Carlito-Italic.ttf"));
            } else {
                faces.put(Style.REGULAR, new Face(document, "Carlito-Regular.ttf"));
                faces.put(Style.BOLD, new Face(document, "Carlito-Bold.ttf"));
                faces.put(Style.ITALIC, new Face(document, "Carlito-Italic.ttf"));
            }
        }

        private Face face(Style style) {
            return faces.get(style);
        }

        // ---- pages ----

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = MARGIN;
        }

        private boolean atPageTop() {
            return y <= MARGIN + 0.01f;
        }

        // start a new page unless `height` more points fit above the bottom margin
        private void ensureSpace(float height) throws IOException {
            if (y + height > PAGE_HEIGHT - MARGIN && !atPageTop()) {
                newPage();
            }
        }

        private void space(float points) {
            if (!atPageTop()) {
                y += points;
            }
        }

        // ---- header ----

        private void writeHeader(BuilderExportRequest request) throws IOException {
            String contact = ResumeDocxExporter.contactLine(request);
            boolean band = template.headerStyle() == ResumeTemplate.HeaderStyle.BAND;
            Align align = template.headerStyle() == ResumeTemplate.HeaderStyle.CENTERED ? Align.CENTER : Align.LEFT;
            Color color = band ? Color.WHITE : TEXT;

            List<String> name = wrap(face(Style.BOLD), clean(request.name()), NAME_PT, TEXT_WIDTH);
            List<String> headline = has(request.headline())
                    ? wrap(face(Style.REGULAR), clean(request.headline()), HEADLINE_PT, TEXT_WIDTH) : List.of();
            List<String> contactLines = contact.isEmpty()
                    ? List.of() : wrap(face(Style.REGULAR), contact, BODY_PT, TEXT_WIDTH);

            if (band) {
                float height = name.size() * face(Style.BOLD).lineHeight(NAME_PT)
                        + headline.size() * face(Style.REGULAR).lineHeight(HEADLINE_PT)
                        + contactLines.size() * face(Style.REGULAR).lineHeight(BODY_PT);
                float top = MARGIN - 2;
                content.setNonStrokingColor(BAND_FILL);
                content.addRect(MARGIN - BAND_PAD_X, PAGE_HEIGHT - (top + BAND_PAD_TOP + height + BAND_PAD_BOTTOM),
                        TEXT_WIDTH + 2 * BAND_PAD_X, BAND_PAD_TOP + height + BAND_PAD_BOTTOM);
                content.fill();
                y = top + BAND_PAD_TOP;
            }

            lines(face(Style.BOLD), NAME_PT, name, MARGIN, align, color);
            if (!headline.isEmpty()) {
                if (!band) {
                    y += UNDER_NAME;
                }
                lines(face(Style.REGULAR), HEADLINE_PT, headline, MARGIN, align, color);
            }
            if (!contactLines.isEmpty()) {
                if (!band) {
                    y += UNDER_NAME;
                }
                lines(face(Style.REGULAR), BODY_PT, contactLines, MARGIN, align, color);
            }
            if (band) {
                y += BAND_PAD_BOTTOM;
            }
        }

        // ---- sections (same rules as the DOCX writer) ----

        private void writeSummary(String summary) throws IOException {
            if (!has(summary)) {
                return;
            }
            heading("Summary");
            List<String> paragraphs = Arrays.stream(summary.split("\\R")).map(String::strip)
                    .filter(line -> !line.isEmpty()).toList();
            for (int i = 0; i < paragraphs.size(); i++) {
                if (i > 0) {
                    space(BETWEEN_BULLETS);
                }
                paragraph(face(Style.REGULAR), BODY_PT, paragraphs.get(i));
            }
        }

        private void writeExperience(List<ExperienceEntry> experience) throws IOException {
            List<ExperienceEntry> entries = experience.stream().filter(Objects::nonNull)
                    .filter(e -> has(e.title()) || has(e.company()) || has(e.description())).toList();
            if (entries.isEmpty()) {
                return;
            }
            heading("Experience");
            for (int i = 0; i < entries.size(); i++) {
                ExperienceEntry entry = entries.get(i);
                String title = has(entry.title()) ? entry.title() : entry.company();
                splitLine(face(Style.BOLD), clean(title), face(Style.REGULAR), clean(entry.dates()),
                        i == 0 ? 0 : BETWEEN_ENTRIES);
                if (has(entry.title()) && has(entry.company())) {
                    paragraph(face(Style.ITALIC), BODY_PT, clean(entry.company()));
                }
                bulletList(bullets(entry.description()));
            }
        }

        private void writeEducation(List<EducationEntry> education) throws IOException {
            List<EducationEntry> entries = education.stream().filter(Objects::nonNull)
                    .filter(e -> has(e.institution()) || has(e.degree())).toList();
            if (entries.isEmpty()) {
                return;
            }
            heading("Education");
            for (int i = 0; i < entries.size(); i++) {
                EducationEntry entry = entries.get(i);
                String first = has(entry.institution()) ? entry.institution() : entry.degree();
                splitLine(face(Style.BOLD), clean(first), face(Style.REGULAR), clean(entry.dates()),
                        i == 0 ? 0 : BETWEEN_ENTRIES);
                boolean degreeLine = has(entry.institution()) && has(entry.degree());
                if (degreeLine || has(entry.location())) {
                    splitLine(face(Style.ITALIC), degreeLine ? clean(entry.degree()) : "",
                            face(Style.ITALIC), clean(entry.location()), 0);
                }
                bulletList(bullets(entry.details()));
            }
        }

        private void writeProjects(List<ProjectEntry> projects) throws IOException {
            List<ProjectEntry> entries = projects.stream().filter(Objects::nonNull)
                    .filter(p -> has(p.name()) || has(p.description())).toList();
            if (entries.isEmpty()) {
                return;
            }
            heading("Projects");
            for (int i = 0; i < entries.size(); i++) {
                ProjectEntry entry = entries.get(i);
                if (has(entry.name())) {
                    if (i > 0) {
                        space(BETWEEN_ENTRIES);
                    }
                    ensureSpace(face(Style.BOLD).lineHeight(BODY_PT) * 2);
                    paragraph(face(Style.BOLD), BODY_PT, clean(entry.name()));
                }
                bulletList(bullets(entry.description()));
            }
        }

        private void writeSkills(List<String> skills) throws IOException {
            List<String> values = skills.stream().filter(ResumeDocxExporter::has).map(String::strip).toList();
            if (values.isEmpty()) {
                return;
            }
            heading("Skills");
            paragraph(face(Style.REGULAR), BODY_PT, String.join(", ", values));
        }

        private void writeCertifications(List<String> certifications) throws IOException {
            List<String> values = certifications.stream().filter(ResumeDocxExporter::has).map(String::strip).toList();
            if (values.isEmpty()) {
                return;
            }
            heading("Certifications");
            bulletList(values);
        }

        // ---- building blocks ----

        // 12pt bold small caps with a full-width rule; kept on the same page as the line after it
        private void heading(String text) throws IOException {
            Face bold = face(Style.BOLD);
            float lineHeight = bold.lineHeight(HEADING_PT);
            float block = BEFORE_HEADING + lineHeight + RULE_GAP + RULE_WIDTH + AFTER_HEADING
                    + 2 * face(Style.REGULAR).lineHeight(BODY_PT);
            ensureSpace(block);
            space(BEFORE_HEADING);

            float baseline = y + bold.ascentFactor * HEADING_PT;
            float x = MARGIN;
            // small caps: capitals stay full size, lower-case letters become smaller capitals
            int start = 0;
            while (start < text.length()) {
                boolean lower = Character.isLowerCase(text.charAt(start));
                int end = start;
                while (end < text.length() && Character.isLowerCase(text.charAt(end)) == lower) {
                    end++;
                }
                String part = text.substring(start, end).toUpperCase(java.util.Locale.ROOT);
                float size = lower ? HEADING_PT * SMALL_CAPS_SCALE : HEADING_PT;
                draw(bold, part, size, x, baseline, TEXT);
                x += width(bold, part, size);
                start = end;
            }
            float ruleY = y + lineHeight + RULE_GAP + RULE_WIDTH / 2;
            content.setStrokingColor(TEXT);
            content.setLineWidth(RULE_WIDTH);
            content.moveTo(MARGIN, PAGE_HEIGHT - ruleY);
            content.lineTo(MARGIN + TEXT_WIDTH, PAGE_HEIGHT - ruleY);
            content.stroke();
            y += lineHeight + RULE_GAP + RULE_WIDTH + AFTER_HEADING;
        }

        // left text and right-aligned text on one line (title + dates, degree + location); the left side
        // wraps inside the room the right side leaves
        private void splitLine(Face leftFace, String left, Face rightFace, String right, float before)
                throws IOException {
            float lineHeight = leftFace.lineHeight(BODY_PT);
            ensureSpace(before + lineHeight * 2);
            space(before);
            float rightWidth = right.isEmpty() ? 0 : width(rightFace, right, BODY_PT);
            float leftRoom = right.isEmpty() ? TEXT_WIDTH : Math.max(TEXT_WIDTH * 0.4f, TEXT_WIDTH - rightWidth - DATE_GAP);
            List<String> leftLines = left.isEmpty() ? List.of("") : wrap(leftFace, left, BODY_PT, leftRoom);
            for (int i = 0; i < leftLines.size(); i++) {
                ensureSpace(lineHeight);
                float baseline = y + leftFace.ascentFactor * BODY_PT;
                draw(leftFace, leftLines.get(i), BODY_PT, MARGIN, baseline, TEXT);
                if (i == 0 && !right.isEmpty()) {
                    draw(rightFace, right, BODY_PT, MARGIN + TEXT_WIDTH - rightWidth, baseline, TEXT);
                }
                y += lineHeight;
            }
        }

        private void bulletList(List<String> items) throws IOException {
            Face regular = face(Style.REGULAR);
            for (int i = 0; i < items.size(); i++) {
                space(i == 0 ? FIRST_BULLET : BETWEEN_BULLETS);
                List<String> lines = wrap(regular, items.get(i), BODY_PT, TEXT_WIDTH - BULLET_TEXT_X);
                for (int j = 0; j < lines.size(); j++) {
                    ensureSpace(regular.lineHeight(BODY_PT));
                    float baseline = y + regular.ascentFactor * BODY_PT;
                    if (j == 0) {
                        draw(regular, "•", BODY_PT, MARGIN + BULLET_X, baseline, TEXT);
                    }
                    draw(regular, lines.get(j), BODY_PT, MARGIN + BULLET_TEXT_X, baseline, TEXT);
                    y += regular.lineHeight(BODY_PT);
                }
            }
        }

        private void paragraph(Face face, float size, String text) throws IOException {
            lines(face, size, wrap(face, text, size, TEXT_WIDTH), MARGIN, Align.LEFT, TEXT);
        }

        private void lines(Face face, float size, List<String> lines, float x, Align align, Color color)
                throws IOException {
            for (String line : lines) {
                ensureSpace(face.lineHeight(size));
                float lineX = align == Align.CENTER ? x + (TEXT_WIDTH - width(face, line, size)) / 2 : x;
                draw(face, line, size, lineX, y + face.ascentFactor * size, color);
                y += face.lineHeight(size);
            }
        }

        // ---- text ----

        // greedy word wrap; a single word longer than the line (a long URL) is broken by characters
        private List<String> wrap(Face face, String text, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            // keep the spacing the caller used between words (the contact line uses two spaces round "·")
            java.util.regex.Matcher words = java.util.regex.Pattern.compile("( *)([^ ]+)").matcher(printable(text));
            while (words.find()) {
                String word = words.group(2);
                String candidate = line.isEmpty() ? word : line + words.group(1) + word;
                if (width(face, candidate, size) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                String rest = word;
                while (width(face, rest, size) > maxWidth) {
                    int cut = rest.offsetByCodePoints(0, 1);
                    while (cut < rest.length()
                            && width(face, rest.substring(0, rest.offsetByCodePoints(cut, 1)), size) <= maxWidth) {
                        cut = rest.offsetByCodePoints(cut, 1);
                    }
                    lines.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                line.append(rest);
            }
            if (!line.isEmpty() || lines.isEmpty()) {
                lines.add(line.toString());
            }
            return lines;
        }

        // tabs/newlines become spaces and other control characters are dropped
        private static String printable(String text) {
            StringBuilder out = new StringBuilder(text.length());
            text.codePoints().forEach(cp -> {
                if (cp == '\t' || cp == '\n' || cp == '\r' || cp == 0xA0) {
                    out.append(' ');
                } else if (!Character.isISOControl(cp)) {
                    out.appendCodePoint(cp);
                }
            });
            return out.toString();
        }

        // split text into runs the font (or its fallback) can draw; anything neither has becomes '?'
        private static List<Chunk> chunks(Face face, String text) {
            List<Chunk> chunks = new ArrayList<>();
            Face current = null;
            StringBuilder run = new StringBuilder();
            for (int cp : printable(text).codePoints().toArray()) {
                Face use;
                int draw = cp;
                if (face.covers(cp)) {
                    use = face;
                } else if (face.fallback() != null && face.fallback().covers(cp)) {
                    use = face.fallback();
                } else {
                    use = face;
                    draw = '?';
                }
                if (use != current && !run.isEmpty()) {
                    chunks.add(new Chunk(current, run.toString()));
                    run.setLength(0);
                }
                current = use;
                run.appendCodePoint(draw);
            }
            if (!run.isEmpty()) {
                chunks.add(new Chunk(current, run.toString()));
            }
            return chunks;
        }

        private static float width(Face face, String text, float size) throws IOException {
            float width = 0;
            for (Chunk chunk : chunks(face, text)) {
                width += chunk.face().font.getStringWidth(chunk.text()) / 1000f * size;
            }
            return width;
        }

        private void draw(Face face, String text, float size, float x, float baselineFromTop, Color color)
                throws IOException {
            if (text.isEmpty()) {
                return;
            }
            content.setNonStrokingColor(color);
            content.beginText();
            content.newLineAtOffset(x, PAGE_HEIGHT - baselineFromTop);
            for (Chunk chunk : chunks(face, text)) {
                content.setFont(chunk.face().font, size);
                content.showText(chunk.text());
            }
            content.endText();
        }
    }
}
