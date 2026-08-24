package me.kitkas1412.parajudge.documents.parser;

import me.kitkas1412.parajudge.documents.parser.model.DocumentMetadata;
import me.kitkas1412.parajudge.documents.parser.model.Footnote;
import me.kitkas1412.parajudge.documents.parser.model.ParsedArticle;
import me.kitkas1412.parajudge.documents.parser.model.ParsedChapter;
import me.kitkas1412.parajudge.documents.parser.model.ParsedClause;
import me.kitkas1412.parajudge.documents.parser.model.ParsedDocument;
import me.kitkas1412.parajudge.documents.parser.model.ParsedPoint;
import me.kitkas1412.parajudge.documents.parser.model.ParsedSection;
import me.kitkas1412.parajudge.documents.parser.pdf.PageText;
import me.kitkas1412.parajudge.documents.parser.pdf.TextLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Turns the flat block stream into the Chương &gt; Mục &gt; Điều &gt; Khoản &gt; Điểm tree.
 *
 * <p>A single left-to-right pass with a stack of open builders. Two rules keep
 * inline references from being read as structure: a heading must be the whole
 * block, and its number must continue the sequence — a {@code Điều 54} appearing
 * after {@code Điều 219} is quoted law, not a new article. Anything inside a “…”
 * block is text, never structure.
 */
public final class LegalDocumentParser {

    /** The article that amends other statutes; its quoted blocks get their own model. */
    private static final int NESTED_LAW_ARTICLE = 219;

    private final List<String> preamble = new ArrayList<>();
    private final List<ParsedChapter> chapters = new ArrayList<>();

    private ChapterBuilder chapter;
    private SectionBuilder section;
    private ArticleBuilder article;
    private ClauseBuilder clause;
    private PointBuilder point;
    private Article219Parser nestedLawParser;

    private Pending pending = Pending.NONE;
    private int lastArticleNo;
    private int quoteDepth;

    private enum Pending { NONE, CHAPTER_TITLE, SECTION_TITLE }

    public ParsedDocument parse(DocumentMetadata partialMetadata, List<PageText> pages) {
        List<TextBlock> blocks = new TextBlockAssembler().assemble(pages);
        for (TextBlock block : blocks) {
            handle(block);
        }
        closeChapter();

        List<Footnote> footnotes = collectFootnotes(pages);
        String joinedPreamble = String.join("\n", preamble);
        Matcher code = LegalTextPatterns.DOCUMENT_CODE.matcher(joinedPreamble);

        DocumentMetadata metadata = new DocumentMetadata(
                partialMetadata.sourceFile(),
                code.find() ? code.group(1) : null,
                documentTitle(),
                partialMetadata.totalPages(),
                pages.size(),
                partialMetadata.droppedScanPages(),
                chapters.size(),
                countArticles());

        return new ParsedDocument(metadata, List.copyOf(preamble), List.copyOf(chapters), footnotes);
    }

    private void handle(TextBlock block) {
        String text = block.text();
        boolean insideQuote = quoteDepth > 0;
        quoteDepth = Math.max(0, quoteDepth + count(text, '“') - count(text, '”'));

        if (pending != Pending.NONE) {
            if (block.bold() && LegalTextPatterns.isAllCaps(text)) {
                appendPendingTitle(text);
                return;
            }
            pending = Pending.NONE;
        }

        if (!insideQuote && !text.startsWith("“")) {
            if (matchChapter(block) || matchSection(block) || matchArticle(block)) {
                return;
            }
        }

        if (article == null) {
            preamble.add(text);
            return;
        }
        if (nestedLawParser != null) {
            nestedLawParser.feed(block, insideQuote);
            article.body.add(text);
            return;
        }
        if (!insideQuote) {
            if (matchClause(block) || matchPoint(block)) {
                return;
            }
        }
        appendToOpenBody(text);
    }

    private boolean matchChapter(TextBlock block) {
        Matcher matcher = LegalTextPatterns.CHUONG.matcher(block.text());
        if (!matcher.matches()) {
            return false;
        }
        closeChapter();
        chapter = new ChapterBuilder(matcher.group(1), block.page());
        pending = Pending.CHAPTER_TITLE;
        return true;
    }

    private boolean matchSection(TextBlock block) {
        Matcher matcher = LegalTextPatterns.MUC.matcher(block.text());
        if (!matcher.matches() || chapter == null) {
            return false;
        }
        closeSection();
        section = new SectionBuilder(matcher.group(1), block.page());
        pending = Pending.SECTION_TITLE;
        return true;
    }

    private boolean matchArticle(TextBlock block) {
        Matcher matcher = LegalTextPatterns.DIEU.matcher(block.text());
        if (!matcher.matches()) {
            return false;
        }
        int no = Integer.parseInt(matcher.group(1));
        if (no <= lastArticleNo || chapter == null) {
            return false;
        }
        closeArticle();
        lastArticleNo = no;
        article = new ArticleBuilder(no, matcher.group(2).strip(), block.page(),
                chapter.no, section == null ? null : section.no);
        nestedLawParser = no == NESTED_LAW_ARTICLE ? new Article219Parser() : null;
        return true;
    }

    private boolean matchClause(TextBlock block) {
        Matcher matcher = LegalTextPatterns.KHOAN.matcher(block.text());
        if (!matcher.matches()) {
            return false;
        }
        String no = matcher.group(1);
        if (!LegalTextPatterns.isNextKhoan(article.lastClauseNo, no)) {
            return false;
        }
        closeClause();
        clause = new ClauseBuilder(no, block.page(), matcher.group(2).strip());
        article.lastClauseNo = no;
        article.body.add(block.text());
        return true;
    }

    private boolean matchPoint(TextBlock block) {
        Matcher matcher = LegalTextPatterns.DIEM.matcher(block.text());
        if (clause == null || !matcher.matches()) {
            return false;
        }
        String no = matcher.group(1);
        if (!LegalTextPatterns.isNextDiem(clause.lastPointNo, no)) {
            return false;
        }
        closePoint();
        point = new PointBuilder(no, block.page(), matcher.group(2).strip());
        clause.lastPointNo = no;
        article.body.add(block.text());
        return true;
    }

    /** A paragraph with no number of its own belongs to the innermost open element. */
    private void appendToOpenBody(String text) {
        article.body.add(text);
        if (point != null) {
            point.text += "\n" + text;
        } else if (clause != null) {
            clause.text += "\n" + text;
        } else {
            article.leadText.add(text);
        }
    }

    private void appendPendingTitle(String text) {
        if (pending == Pending.CHAPTER_TITLE && chapter != null) {
            chapter.title = chapter.title.isEmpty() ? text : chapter.title + " " + text;
        } else if (pending == Pending.SECTION_TITLE && section != null) {
            section.title = section.title.isEmpty() ? text : section.title + " " + text;
        }
    }

    private void closePoint() {
        if (point != null && clause != null) {
            clause.points.add(new ParsedPoint(point.no, point.page, point.text));
        }
        point = null;
    }

    private void closeClause() {
        closePoint();
        if (clause != null && article != null) {
            article.clauses.add(new ParsedClause(clause.no, clause.page, clause.text,
                    List.copyOf(clause.points)));
        }
        clause = null;
    }

    private void closeArticle() {
        closeClause();
        if (article == null) {
            return;
        }
        List<me.kitkas1412.parajudge.documents.parser.model.Amendment> amendments =
                nestedLawParser == null ? List.of() : nestedLawParser.build();
        ParsedArticle built = new ParsedArticle(article.no, article.title, article.page,
                article.chapterNo, article.sectionNo, List.copyOf(article.leadText),
                List.copyOf(article.clauses), amendments, article.fullText());
        if (section != null) {
            section.articles.add(built);
        } else if (chapter != null) {
            chapter.articles.add(built);
        }
        article = null;
        nestedLawParser = null;
    }

    private void closeSection() {
        closeArticle();
        if (section != null && chapter != null) {
            chapter.sections.add(new ParsedSection(section.no, section.title, section.page,
                    List.copyOf(section.articles)));
        }
        section = null;
    }

    private void closeChapter() {
        closeSection();
        if (chapter != null) {
            chapters.add(new ParsedChapter(chapter.no, LegalTextPatterns.romanToInt(chapter.no),
                    chapter.title, chapter.page, List.copyOf(chapter.sections),
                    List.copyOf(chapter.articles)));
        }
        chapter = null;
    }

    /** The document title is the capitalised block(s) under the national heading. */
    private String documentTitle() {
        List<String> caps = new ArrayList<>();
        for (String block : preamble.subList(0, Math.min(6, preamble.size()))) {
            if (LegalTextPatterns.isAllCaps(block) && !block.startsWith("CỘNG HÒA")) {
                caps.add(block);
            }
        }
        return caps.isEmpty() ? null : String.join(" ", caps);
    }

    private int countArticles() {
        int total = 0;
        for (ParsedChapter c : chapters) {
            total += c.articles().size();
            for (ParsedSection s : c.sections()) {
                total += s.articles().size();
            }
        }
        return total;
    }

    /**
     * Footnote lines were separated during extraction; a new footnote starts wherever
     * a line carried the superscript marker.
     */
    private List<Footnote> collectFootnotes(List<PageText> pages) {
        List<Footnote> footnotes = new ArrayList<>();
        for (PageText page : pages) {
            StringBuilder current = new StringBuilder();
            String marker = null;
            for (TextLine line : page.footnotes()) {
                if (line.leadingMarker() != null) {
                    flushFootnote(footnotes, marker, page.pageNo(), current);
                    marker = line.leadingMarker();
                }
                if (!current.isEmpty()) {
                    current.append(' ');
                }
                current.append(line.text());
            }
            flushFootnote(footnotes, marker, page.pageNo(), current);
        }
        return List.copyOf(footnotes);
    }

    private void flushFootnote(List<Footnote> sink, String marker, int page, StringBuilder buffer) {
        String text = buffer.toString().strip();
        buffer.setLength(0);
        if (marker != null && !text.isEmpty()) {
            sink.add(new Footnote(marker, page, text));
        }
    }

    private static int count(String text, char c) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                total++;
            }
        }
        return total;
    }

    private static final class ChapterBuilder {
        private final String no;
        private final int page;
        private final List<ParsedSection> sections = new ArrayList<>();
        private final List<ParsedArticle> articles = new ArrayList<>();
        private String title = "";

        private ChapterBuilder(String no, int page) {
            this.no = no;
            this.page = page;
        }
    }

    private static final class SectionBuilder {
        private final String no;
        private final int page;
        private final List<ParsedArticle> articles = new ArrayList<>();
        private String title = "";

        private SectionBuilder(String no, int page) {
            this.no = no;
            this.page = page;
        }
    }

    private static final class ArticleBuilder {
        private final int no;
        private final String title;
        private final int page;
        private final String chapterNo;
        private final String sectionNo;
        private final List<String> leadText = new ArrayList<>();
        private final List<ParsedClause> clauses = new ArrayList<>();
        private final List<String> body = new ArrayList<>();
        private String lastClauseNo;

        private ArticleBuilder(int no, String title, int page, String chapterNo, String sectionNo) {
            this.no = no;
            this.title = title;
            this.page = page;
            this.chapterNo = chapterNo;
            this.sectionNo = sectionNo;
        }

        private String fullText() {
            return "Điều " + no + ". " + title + "\n" + String.join("\n", body);
        }
    }

    private static final class ClauseBuilder {
        private final String no;
        private final int page;
        private final List<ParsedPoint> points = new ArrayList<>();
        private String text;
        private String lastPointNo;

        private ClauseBuilder(String no, int page, String text) {
            this.no = no;
            this.page = page;
            this.text = text;
        }
    }

    private static final class PointBuilder {
        private final String no;
        private final int page;
        private String text;

        private PointBuilder(String no, int page, String text) {
            this.no = no;
            this.page = page;
            this.text = text;
        }
    }
}
