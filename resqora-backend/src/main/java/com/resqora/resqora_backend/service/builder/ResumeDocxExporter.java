package com.resqora.resqora_backend.service.builder;

import com.resqora.resqora_backend.dto.builder.BuilderExportRequest;
import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabStop;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// Writes the builder form as a single-column DOCX. ATS rules: real text only, no tables, images,
// text boxes or columns; title/date pairs use a right tab stop. Nothing is added that the caller didn't send.
@Component
public class ResumeDocxExporter {
    // A4 with 0.5" margins; sizes in twentieths of a point (twips)
    private static final int PAGE_WIDTH = 11906;
    private static final int PAGE_HEIGHT = 16838;
    private static final int MARGIN = 720;
    private static final int TEXT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    private static final int NAME_PT = 24;
    private static final int HEADING_PT = 12;
    private static final int BODY_PT = 11;

    private static final int AFTER_HEADING = 6 * 20;
    private static final int BEFORE_HEADING = 10 * 20;
    private static final int BETWEEN_BULLETS = 3 * 20;
    private static final int BETWEEN_ENTRIES = 8 * 20;

    private static final String BAND_FILL = "1F3A5F";
    private static final String BAND_TEXT = "FFFFFF";

    public byte[] export(BuilderExportRequest request) {
        ResumeTemplate template = ResumeTemplate.fromId(request.templateId());
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new Writer(document, template).write(request);
            document.write(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write resume document", exception);
        }
    }

    // "Aaron D'Souza" -> "aaron-dsouza-resume.docx"
    public static String fileName(String name) {
        String slug = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("['’]", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-+$", "");
        }
        return (slug.isEmpty() ? "resume" : slug + "-resume") + ".docx";
    }

    // Description text -> bullets: one per line, blank lines dropped, leading bullet marks stripped
    static List<String> bullets(String description) {
        if (description == null) {
            return List.of();
        }
        return Arrays.stream(description.split("\\R"))
                .map(line -> line.strip().replaceFirst("^[•\\-*–·▪●]\\s*", "").strip())
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private static boolean has(String value) {
        return value != null && !value.isBlank();
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static final class Writer {
        private final XWPFDocument document;
        private final ResumeTemplate template;
        private BigInteger bulletNumId;

        Writer(XWPFDocument document, ResumeTemplate template) {
            this.document = document;
            this.template = template;
        }

        void write(BuilderExportRequest request) {
            setUpPage();
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
        }

        private void setUpPage() {
            CTSectPr sectPr = document.getDocument().getBody().isSetSectPr()
                    ? document.getDocument().getBody().getSectPr()
                    : document.getDocument().getBody().addNewSectPr();
            CTPageSz size = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
            size.setW(BigInteger.valueOf(PAGE_WIDTH));
            size.setH(BigInteger.valueOf(PAGE_HEIGHT));
            CTPageMar margins = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
            BigInteger margin = BigInteger.valueOf(MARGIN);
            margins.setTop(margin);
            margins.setBottom(margin);
            margins.setLeft(margin);
            margins.setRight(margin);
            margins.setHeader(margin);
            margins.setFooter(margin);
            margins.setGutter(BigInteger.ZERO);

            // default font for anything not set per run (also covers East Asian / complex-script text)
            CTFonts fonts = CTFonts.Factory.newInstance();
            fonts.setAscii(template.font());
            fonts.setHAnsi(template.font());
            fonts.setCs(template.font());
            fonts.setEastAsia(template.font());
            document.createStyles().setDefaultFonts(fonts);
        }

        // ---- header ----

        private void writeHeader(BuilderExportRequest request) {
            String contact = String.join("  ·  ", java.util.stream.Stream.of(
                            request.email(), request.phone(), request.location())
                    .filter(ResumeDocxExporter::has).map(String::strip).toList());

            if (template.headerStyle() == ResumeTemplate.HeaderStyle.BAND) {
                XWPFParagraph band = paragraph(0, 0);
                shade(band);
                XWPFRun name = run(band, clean(request.name()), NAME_PT);
                name.setBold(true);
                name.setColor(BAND_TEXT);
                if (!contact.isEmpty()) {
                    name.addBreak();
                    run(band, contact, BODY_PT).setColor(BAND_TEXT);
                }
                return;
            }

            ParagraphAlignment align = template.headerStyle() == ResumeTemplate.HeaderStyle.CENTERED
                    ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT;
            XWPFParagraph namePara = paragraph(0, contact.isEmpty() ? 0 : 2 * 20);
            namePara.setAlignment(align);
            run(namePara, clean(request.name()), NAME_PT).setBold(true);
            if (!contact.isEmpty()) {
                XWPFParagraph contactPara = paragraph(0, 0);
                contactPara.setAlignment(align);
                run(contactPara, contact, BODY_PT);
            }
        }

        // Paragraph background plus same-colour borders so the band has a little padding round the text
        private void shade(XWPFParagraph paragraph) {
            CTShd shd = paragraph.getCTP().getPPr().isSetShd()
                    ? paragraph.getCTP().getPPr().getShd() : paragraph.getCTP().getPPr().addNewShd();
            shd.setVal(STShd.CLEAR);
            shd.setColor("auto");
            shd.setFill(BAND_FILL);
            CTPBdr borders = paragraph.getCTP().getPPr().isSetPBdr()
                    ? paragraph.getCTP().getPPr().getPBdr() : paragraph.getCTP().getPPr().addNewPBdr();
            bandBorder(borders.addNewTop(), 8);
            bandBorder(borders.addNewBottom(), 8);
            bandBorder(borders.addNewLeft(), 8);
            bandBorder(borders.addNewRight(), 8);
        }

        private void bandBorder(CTBorder border, int spacePt) {
            border.setVal(STBorder.SINGLE);
            border.setSz(BigInteger.valueOf(4));
            border.setSpace(BigInteger.valueOf(spacePt));
            border.setColor(BAND_FILL);
        }

        // ---- sections ----

        private void writeSummary(String summary) {
            if (!has(summary)) {
                return;
            }
            heading("Summary");
            List<String> lines = Arrays.stream(summary.split("\\R")).map(String::strip)
                    .filter(line -> !line.isEmpty()).toList();
            for (int i = 0; i < lines.size(); i++) {
                run(paragraph(0, i == lines.size() - 1 ? 0 : BETWEEN_BULLETS), lines.get(i), BODY_PT);
            }
        }

        private void writeExperience(List<ExperienceEntry> experience) {
            List<ExperienceEntry> entries = experience.stream().filter(Objects::nonNull)
                    .filter(e -> has(e.title()) || has(e.company()) || has(e.description())).toList();
            if (entries.isEmpty()) {
                return;
            }
            heading("Experience");
            for (int i = 0; i < entries.size(); i++) {
                ExperienceEntry entry = entries.get(i);
                int before = i == 0 ? 0 : BETWEEN_ENTRIES;
                // title line carries the dates; if there's no title the company takes its place
                String title = has(entry.title()) ? entry.title() : entry.company();
                titleLine(title, entry.dates(), before);
                if (has(entry.title()) && has(entry.company())) {
                    run(paragraph(0, 0), clean(entry.company()), BODY_PT).setItalic(true);
                }
                bulletList(bullets(entry.description()));
            }
        }

        private void writeEducation(List<EducationEntry> education) {
            List<EducationEntry> entries = education.stream().filter(Objects::nonNull)
                    .filter(e -> has(e.institution()) || has(e.degree())).toList();
            if (entries.isEmpty()) {
                return;
            }
            heading("Education");
            for (int i = 0; i < entries.size(); i++) {
                EducationEntry entry = entries.get(i);
                int before = i == 0 ? 0 : BETWEEN_ENTRIES;
                String first = has(entry.institution()) ? entry.institution() : entry.degree();
                titleLine(first, entry.dates(), before);
                boolean degreeLine = has(entry.institution()) && has(entry.degree());
                if (degreeLine || has(entry.location())) {
                    XWPFParagraph line = paragraph(0, 0);
                    rightTab(line);
                    if (degreeLine) {
                        run(line, clean(entry.degree()), BODY_PT).setItalic(true);
                    }
                    if (has(entry.location())) {
                        XWPFRun location = run(line, "", BODY_PT);
                        location.addTab();
                        location.setText(clean(entry.location()));
                        location.setItalic(true);
                    }
                }
                bulletList(bullets(entry.details()));
            }
        }

        private void writeProjects(List<ProjectEntry> projects) {
            List<ProjectEntry> entries = projects.stream().filter(Objects::nonNull)
                    .filter(p -> has(p.name()) || has(p.description())).toList();
            if (entries.isEmpty()) {
                return;
            }
            heading("Projects");
            for (int i = 0; i < entries.size(); i++) {
                ProjectEntry entry = entries.get(i);
                if (has(entry.name())) {
                    run(paragraph(i == 0 ? 0 : BETWEEN_ENTRIES, 0), clean(entry.name()), BODY_PT).setBold(true);
                }
                bulletList(bullets(entry.description()));
            }
        }

        private void writeSkills(List<String> skills) {
            List<String> values = skills.stream().filter(ResumeDocxExporter::has).map(String::strip).toList();
            if (values.isEmpty()) {
                return;
            }
            heading("Skills");
            run(paragraph(0, 0), String.join(", ", values), BODY_PT);
        }

        private void writeCertifications(List<String> certifications) {
            List<String> values = certifications.stream().filter(ResumeDocxExporter::has).map(String::strip).toList();
            if (values.isEmpty()) {
                return;
            }
            heading("Certifications");
            bulletList(values);
        }

        // ---- building blocks shared by every template ----

        // 12pt bold small caps, full-width rule underneath, 6pt after
        private void heading(String text) {
            XWPFParagraph paragraph = paragraph(BEFORE_HEADING, AFTER_HEADING);
            paragraph.setBorderBottom(Borders.SINGLE);
            CTBorder rule = paragraph.getCTP().getPPr().getPBdr().getBottom();
            rule.setSz(BigInteger.valueOf(6));
            rule.setSpace(BigInteger.ONE);
            rule.setColor("auto");
            paragraph.setKeepNext(true);
            XWPFRun run = run(paragraph, text, HEADING_PT);
            run.setBold(true);
            run.setSmallCaps(true);
        }

        // bold title, tab, right-aligned dates on the same line (tab stop, never a table)
        private void titleLine(String title, String dates, int spacingBefore) {
            XWPFParagraph paragraph = paragraph(spacingBefore, 0);
            paragraph.setKeepNext(true);
            rightTab(paragraph);
            run(paragraph, clean(title), BODY_PT).setBold(true);
            if (has(dates)) {
                XWPFRun date = run(paragraph, "", BODY_PT);
                date.addTab();
                date.setText(clean(dates));
            }
        }

        private void bulletList(List<String> items) {
            for (int i = 0; i < items.size(); i++) {
                // 3pt between bullets; the first sits close under its title line
                XWPFParagraph paragraph = paragraph(i == 0 ? 2 * 20 : BETWEEN_BULLETS, 0);
                paragraph.setNumID(bulletNumId());
                paragraph.setNumILvl(BigInteger.ZERO);
                run(paragraph, items.get(i), BODY_PT);
            }
        }

        private void rightTab(XWPFParagraph paragraph) {
            CTTabStop tab = paragraph.getCTP().getPPr().addNewTabs().addNewTab();
            tab.setVal(STTabJc.RIGHT);
            tab.setPos(BigInteger.valueOf(TEXT_WIDTH));
        }

        private XWPFParagraph paragraph(int before, int after) {
            XWPFParagraph paragraph = document.createParagraph();
            if (paragraph.getCTP().getPPr() == null) {
                paragraph.getCTP().addNewPPr();
            }
            paragraph.setSpacingBefore(before);
            paragraph.setSpacingAfter(after);
            paragraph.setSpacingBetween(1.0);
            paragraph.getCTP().getPPr().getSpacing().setLineRule(STLineSpacingRule.AUTO);
            return paragraph;
        }

        private XWPFRun run(XWPFParagraph paragraph, String text, int sizePt) {
            XWPFRun run = paragraph.createRun();
            run.setFontFamily(template.font());
            run.setFontSize(sizePt);
            if (!text.isEmpty()) {
                run.setText(text);
            }
            return run;
        }

        // one real Word bullet list definition shared by every list in the document
        private BigInteger bulletNumId() {
            if (bulletNumId != null) {
                return bulletNumId;
            }
            CTAbstractNum abstractNum = CTAbstractNum.Factory.newInstance();
            abstractNum.setAbstractNumId(BigInteger.ZERO);
            CTLvl level = abstractNum.addNewLvl();
            level.setIlvl(BigInteger.ZERO);
            level.addNewStart().setVal(BigInteger.ONE);
            level.addNewNumFmt().setVal(STNumberFormat.BULLET);
            level.addNewLvlText().setVal("•");
            level.addNewLvlJc().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc.LEFT);
            CTInd indent = level.addNewPPr().addNewInd();
            indent.setLeft(BigInteger.valueOf(360));
            indent.setHanging(BigInteger.valueOf(240));
            CTFonts bulletFont = level.addNewRPr().addNewRFonts();
            bulletFont.setAscii(template.font());
            bulletFont.setHAnsi(template.font());

            XWPFNumbering numbering = document.createNumbering();
            BigInteger abstractId = numbering.addAbstractNum(new XWPFAbstractNum(abstractNum));
            bulletNumId = numbering.addNum(abstractId);
            return bulletNumId;
        }
    }
}
