package com.resqora.resqora_backend.service.extraction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One clean-up step run on the text of every resume, PDF or DOCX, before it is parsed.
 *
 * Why: the same resume comes out of PDFBox and Apache POI as slightly different characters
 * (a Word bullet becomes the private-use glyph U+F0B7 in a PDF, tabs vs spaces, non-breaking spaces,
 * "ﬁ" ligatures, curly quotes, zero-width characters, a contact line wrapped after a comma).
 * The parser matches text with regular expressions, so those small differences changed what it found
 * and therefore the score. Mapping both formats onto one plain form here means the same content
 * always gives the same parse and the same score.
 *
 * Rules are fixed character mappings plus one word-break rule (no AI), and running it twice changes nothing.
 */
public final class ResumeTextNormalizer {
    // Bullet glyphs the parser understands; a "•Built" with no space gets one so it is seen as a bullet.
    private static final Pattern GLUED_BULLET = Pattern.compile("^([•●▪■◦○□◆◇❖➤►▸▹‣⁃✓✔→»·])(?=\\S)");
    // a lower-case letter then "-" at the very end of a line: a word split across PDF lines
    private static final Pattern HYPHEN_AT_END = Pattern.compile(".*\\p{Ll}-$");
    private static final Pattern SPACES = Pattern.compile(" {2,}");
    // marks a hyphen that ended a PDF line until we decide whether it was a word break (internal only)
    private static final char BREAK_MARK = '\u0000';
    private static final Pattern BROKEN_WORD = Pattern.compile("(\\p{L}+)-" + BREAK_MARK + "(\\p{L}+)");
    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    // endings that are almost never a word of their own: "opment", "ation", "ibility", "ized"
    private static final Pattern SUFFIX_FRAGMENT = Pattern.compile(
            "\\p{Ll}*(?:ment|ments|tion|tions|sion|sions|ity|ities|ness|ance|ances|ence|ences|able|ible|ably"
                    + "|ology|ologies|ical|ically|ally|ized|izes|izing|ised|ising|ure|ures|ous|ious|ism|ist|ists"
                    + "|ial|ency|ancy|ing|ings|ed)");
    // halves of real hyphenated words that resumes use a lot: "full-stack", "self-motivated", "problem-solving"
    private static final Set<String> COMPOUND_HEADS = Set.of(
            "full", "front", "back", "real", "open", "cross", "multi", "self", "well", "high", "low", "long",
            "short", "part", "third", "non", "co", "pre", "re", "e", "user", "data", "cloud", "web", "team",
            "detail", "problem", "decision", "fast", "hands", "cost", "state", "peer", "end", "test", "client",
            "server", "award", "top", "mobile", "event", "object", "customer", "mission", "time", "cutting",
            "deep", "machine", "rule", "on", "in", "off", "sub", "inter", "anti", "auto", "semi", "post", "mid",
            "ex", "single", "double", "first", "results", "goal", "quality", "safety", "security");
    private static final Set<String> COMPOUND_TAILS = Set.of(
            "solving", "making", "driven", "based", "oriented", "friendly", "stack", "end", "time", "source",
            "platform", "side", "level", "scale", "term", "party", "focused", "minded", "motivated", "known",
            "paced", "effective", "ready", "facing", "related", "specific", "wide", "free", "aware",
            "intensive", "centric", "first", "edge", "house", "premise", "premises", "tier", "learning",
            "processing", "building", "testing", "commerce");

    private ResumeTextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(c -> appendNormalized(out, c));

        List<String> lines = new ArrayList<>();
        for (String raw : out.toString().split("\n", -1)) {
            String line = SPACES.matcher(raw).replaceAll(" ").trim();
            if (line.isEmpty()) {
                continue;
            }
            // Private-use glyph at the start of a line = a Word/Wingdings bullet exported to PDF (U+F0B7 etc.)
            if (isPrivateUse(line.codePointAt(0))) {
                line = "• " + line.substring(Character.charCount(line.codePointAt(0))).trim();
            }
            line = line.replaceAll("\\p{Co}", " ").replaceAll(" {2,}", " ").trim(); // icon fonts elsewhere
            line = GLUED_BULLET.matcher(line).replaceFirst("$1 ");
            if (line.isEmpty() || line.equals("•")) {
                continue;
            }
            // A line that ends in "," or "|" was cut by the page width (PDF), e.g. a contact line
            // "... | Ahmedabad," + "Gujarat". Join it so both formats keep it on one line.
            int last = lines.size() - 1;
            if (last >= 0 && endsUnfinished(lines.get(last)) && !line.startsWith("•") && line.indexOf(':') < 0) {
                lines.set(last, lines.get(last) + " " + line);
            } else if (last >= 0 && HYPHEN_AT_END.matcher(lines.get(last)).matches()
                    && Character.isLowerCase(line.codePointAt(0))) {
                // "devel-" + "opment" or "full-" + "stack" split by the PDF line width: join the two lines
                // and decide about the hyphen once the whole text is known (see resolveLineBreakHyphens)
                lines.set(last, lines.get(last) + BREAK_MARK + line);
            } else {
                lines.add(line);
            }
        }
        return resolveLineBreakHyphens(String.join("\n", lines));
    }

    /**
     * A hyphen at the end of a PDF line is either a word broken by the line width ("devel-opment")
     * or a real hyphenated word ("full-stack"). Rules, first match wins, no AI:
     * 1. the hyphenated form appears elsewhere in this resume -> keep the hyphen;
     * 2. the joined form ("development") appears elsewhere in this resume -> drop it;
     * 3. either half is a known part of common hyphenated words ("full", "stack", "self") -> keep it;
     * 4. the second half is only a word ending ("opment", "ation", "ing") -> drop it;
     * 5. otherwise keep it, because a wrongly removed hyphen is worse than a kept one.
     */
    static String resolveLineBreakHyphens(String text) {
        if (text.indexOf(BREAK_MARK) < 0) {
            return text;
        }
        String withoutMarks = text.replace(String.valueOf(BREAK_MARK), "");
        String lower = withoutMarks.toLowerCase(Locale.ROOT);
        Set<String> words = new HashSet<>();
        Matcher word = WORD.matcher(lower);
        while (word.find()) {
            words.add(word.group());
        }
        Matcher broken = BROKEN_WORD.matcher(text);
        StringBuilder out = new StringBuilder();
        while (broken.find()) {
            String head = broken.group(1);
            String tail = broken.group(2);
            String replacement = dropHyphen(head, tail, lower, words) ? head + tail : head + "-" + tail;
            broken.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        broken.appendTail(out);
        return out.toString().replace(String.valueOf(BREAK_MARK), "");
    }

    private static boolean dropHyphen(String head, String tail, String lowerText, Set<String> words) {
        String h = head.toLowerCase(Locale.ROOT);
        String t = tail.toLowerCase(Locale.ROOT);
        if (countOf(lowerText, h + "-" + t) > 1) {
            return false;
        }
        if (words.contains(h + t)) {
            return true;
        }
        if (COMPOUND_HEADS.contains(h) || COMPOUND_TAILS.contains(t)) {
            return false;
        }
        return SUFFIX_FRAGMENT.matcher(t).matches();
    }

    private static int countOf(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            count++;
        }
        return count;
    }

    private static boolean endsUnfinished(String line) {
        return line.endsWith(",") || line.endsWith("|");
    }

    private static boolean isPrivateUse(int c) {
        return Character.getType(c) == Character.PRIVATE_USE;
    }

    private static void appendNormalized(StringBuilder out, int c) {
        switch (c) {
            // line breaks of every kind become "\n"
            case '\r' -> out.append('\n');
            case 0x0B, 0x0C, 0x85, 0x2028, 0x2029 -> out.append('\n');
            // tabs and the various Unicode spaces become a plain space
            case '\t', 0xA0, 0x2007, 0x202F, 0x205F, 0x3000 -> out.append(' ');
            // invisible characters (zero-width space/joiners, BOM, soft hyphen) are dropped
            case 0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF, 0xAD -> { }
            // NUL never belongs in resume text (and is our internal line-break marker)
            case 0x00 -> { }
            // typographic ligatures PDFs keep as one glyph
            case 0xFB00 -> out.append("ff");
            case 0xFB01 -> out.append("fi");
            case 0xFB02 -> out.append("fl");
            case 0xFB03 -> out.append("ffi");
            case 0xFB04 -> out.append("ffl");
            case 0xFB05, 0xFB06 -> out.append("st");
            // curly quotes from Word's autocorrect
            case 0x2018, 0x2019, 0x201A, 0x201B, 0x2032 -> out.append('\'');
            case 0x201C, 0x201D, 0x201E, 0x201F, 0x2033 -> out.append('"');
            default -> {
                if (c >= 0x2000 && c <= 0x200A) {
                    out.append(' '); // en/em/thin spaces
                } else {
                    out.appendCodePoint(c);
                }
            }
        }
    }
}
