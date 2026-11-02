package com.resqora.resqora_backend.service.extraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResumeTextNormalizerTest {
    @Test
    void mapsFormatQuirksToPlainText() {
        String messy = "Spring\u00A0Boot\t\tdeveloper\r\n"           // non-breaking space, tabs, CRLF
                + "\uF0B7 Built an e\uFB03cient cache\u200B\r\n"      // Word bullet in a PDF, ligature, zero-width
                + "\u2022Wrote \u201Cclean\u201D code\u2019s tests\n"   // glued bullet, curly quotes
                + "Ahmedabad,\nGujarat\n\n\n"                           // line cut after a comma
                + "Languages: Java";
        assertEquals("Spring Boot developer\n"
                + "• Built an efficient cache\n"
                + "• Wrote \"clean\" code's tests\n"
                + "Ahmedabad, Gujarat\n"
                + "Languages: Java", ResumeTextNormalizer.normalize(messy));
    }

    @Test
    void runningTwiceChangesNothing() {
        String once = ResumeTextNormalizer.normalize("A,\nB |\n\uF0A7 item\n  x  y  ");
        assertEquals(once, ResumeTextNormalizer.normalize(once));
        assertNull(ResumeTextNormalizer.normalize(null));
    }

    // A word split across PDF lines is rejoined; a real hyphenated word keeps its hyphen
    @Test
    void rejoinsWordsHyphenatedAcrossLines() {
        assertEquals("• Built a full-stack app", ResumeTextNormalizer.normalize("• Built a full-\nstack app"));
        assertEquals("Java-\nScript", ResumeTextNormalizer.normalize("Java-\nScript")); // upper case: not a split word
        assertEquals("2019-\n2020", ResumeTextNormalizer.normalize("2019-\n2020"));
        assertEquals("well-\n• Next bullet", ResumeTextNormalizer.normalize("well-\n• Next bullet"));
    }

    // "devel-\nopment" was cut by the line width: the joined word gets no hyphen (fixed rules, no AI)
    @Test
    void dropsTheHyphenOfAWordBrokenByTheLineWidth() {
        assertEquals("Web development using Spring", ResumeTextNormalizer.normalize("Web devel-\nopment using Spring"));
        assertEquals("Led the implementation of caching",
                ResumeTextNormalizer.normalize("Led the implemen-\ntation of caching"));
        // the joined word appears elsewhere in the resume, so the break was not a real hyphen
        assertEquals("Springboot\nused Springboot", ResumeTextNormalizer.normalize("Springboot\nused Spring-\nboot"));
        String once = ResumeTextNormalizer.normalize("Web devel-\nopment");
        assertEquals(once, ResumeTextNormalizer.normalize(once));
    }

    @Test
    void keepsTheHyphenOfRealHyphenatedWords() {
        assertEquals("strong problem-solving skills", ResumeTextNormalizer.normalize("strong problem-\nsolving skills"));
        assertEquals("a data-driven team", ResumeTextNormalizer.normalize("a data-\ndriven team"));
        assertEquals("self-motivated", ResumeTextNormalizer.normalize("self-\nmotivated"));
        // the hyphenated form is used elsewhere in the resume
        assertEquals("e-commerce site\nbuilt e-commerce", ResumeTextNormalizer.normalize("e-commerce site\nbuilt e-\ncommerce"));
        // unknown halves: keeping the hyphen is the safe choice
        assertEquals("a zap-bot", ResumeTextNormalizer.normalize("a zap-\nbot"));
    }
}
