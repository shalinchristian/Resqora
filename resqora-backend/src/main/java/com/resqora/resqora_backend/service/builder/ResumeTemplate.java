package com.resqora.resqora_backend.service.builder;

import java.util.Arrays;
import java.util.List;

// The five builder formats. A template only picks the section order and how the name/contact block looks;
// every template shares the same single-column type scale and spacing.
public enum ResumeTemplate {
    CLASSIC("classic", HeaderStyle.LEFT, "Calibri", List.of(
            Section.SUMMARY, Section.EXPERIENCE, Section.EDUCATION, Section.PROJECTS, Section.SKILLS,
            Section.CERTIFICATIONS)),
    COMBINATION("combination", HeaderStyle.LEFT, "Calibri", List.of(
            Section.SUMMARY, Section.SKILLS, Section.EXPERIENCE, Section.PROJECTS, Section.EDUCATION,
            Section.CERTIFICATIONS)),
    CAMPUS("campus", HeaderStyle.LEFT, "Calibri", List.of(
            Section.SUMMARY, Section.EDUCATION, Section.PROJECTS, Section.EXPERIENCE, Section.SKILLS,
            Section.CERTIFICATIONS)),
    // serif, and only the name/contact lines are centered (centered body lines confuse parsers)
    CENTERED("centered", HeaderStyle.CENTERED, "Times New Roman", CLASSIC.sections),
    // name/contact are real text on a shaded paragraph, not a table or an image
    HEADER_BAND("header-band", HeaderStyle.BAND, "Calibri", CLASSIC.sections);

    public enum HeaderStyle { LEFT, CENTERED, BAND }

    public enum Section { SUMMARY, EXPERIENCE, EDUCATION, PROJECTS, SKILLS, CERTIFICATIONS }

    private final String id;
    private final HeaderStyle headerStyle;
    private final String font;
    private final List<Section> sections;

    ResumeTemplate(String id, HeaderStyle headerStyle, String font, List<Section> sections) {
        this.id = id;
        this.headerStyle = headerStyle;
        this.font = font;
        this.sections = sections;
    }

    public String id() { return id; }
    public HeaderStyle headerStyle() { return headerStyle; }
    public String font() { return font; }
    public List<Section> sections() { return sections; }

    public static ResumeTemplate fromId(String id) {
        return Arrays.stream(values())
                .filter(template -> template.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown templateId '" + id + "'. Allowed values: "
                        + String.join(", ", Arrays.stream(values()).map(ResumeTemplate::id).toList())));
    }
}
