package me.kitkas1412.parajudge.documents.parser.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts a PDF page by page with PDFBox, keeping the layout signals that plain
 * {@code PDFTextStripper.getText()} throws away.
 *
 * <p>Per page it decides three things the downstream parser depends on:
 * <ul>
 *   <li>the dominant (body) font size, from which footnotes are recognised as the
 *       smaller text in the bottom band of the page;</li>
 *   <li>the left margin, so an indented line can be told apart from the wrapped
 *       continuation of the paragraph above it;</li>
 *   <li>superscript footnote markers, which are stripped out of the text instead
 *       of being glued onto the end of a heading ({@code ĐIỀU KHOẢN THI HÀNH3}).</li>
 * </ul>
 */
public final class PdfPageExtractor {

    /** A glyph this much smaller than its line is a superscript, not content. */
    private static final float SUPERSCRIPT_RATIO = 0.8f;

    /** Footnote text must be at least this much smaller than the body font. */
    private static final float FOOTNOTE_SIZE_DELTA = 1.0f;

    /** Footnotes only ever live in the bottom part of the page. */
    private static final float FOOTNOTE_BAND_TOP = 0.55f;

    public List<PageText> extract(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return extract(document);
        }
    }

    /** For content that never reaches the filesystem, such as an upload. */
    public List<PageText> extract(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return extract(document);
        }
    }

    private List<PageText> extract(PDDocument document) throws IOException {
        LineCollector collector = new LineCollector();
        collector.setSortByPosition(true);
        collector.setStartPage(1);
        collector.setEndPage(document.getNumberOfPages());
        collector.getText(document);

        List<PageText> pages = new ArrayList<>();
        for (int pageNo = 1; pageNo <= document.getNumberOfPages(); pageNo++) {
            PDPage page = document.getPage(pageNo - 1);
            float height = page.getMediaBox().getHeight();
            pages.add(assemble(pageNo, collector.linesOf(pageNo), height));
        }
        return pages;
    }

    private PageText assemble(int pageNo, List<TextLine> lines, float pageHeight) {
        List<TextLine> content = lines.stream().filter(l -> !l.isBlank()).toList();
        if (content.isEmpty()) {
            return new PageText(pageNo, List.of(), List.of(), 0f, 0f);
        }

        float bodySize = dominantFontSize(content);
        float footnoteCeiling = bodySize - FOOTNOTE_SIZE_DELTA;
        float bandTop = pageHeight * FOOTNOTE_BAND_TOP;

        List<TextLine> body = new ArrayList<>();
        List<TextLine> footnotes = new ArrayList<>();
        for (TextLine line : content) {
            if (isPageNumber(line, bodySize)) {
                continue;
            }
            if (line.fontSize() <= footnoteCeiling && line.y() >= bandTop) {
                footnotes.add(line);
            } else {
                body.add(line);
            }
        }

        float leftMargin = body.stream()
                .filter(l -> l.fontSize() >= bodySize - 0.5f)
                .map(TextLine::x)
                .min(Float::compare)
                .orElse(0f);

        return new PageText(pageNo, List.copyOf(body), List.copyOf(footnotes), bodySize, leftMargin);
    }

    /** Running headers and footers on these documents are a bare page number in small type. */
    private boolean isPageNumber(TextLine line, float bodySize) {
        return line.fontSize() < bodySize && line.text().strip().matches("\\d{1,4}");
    }

    /** Mode of the per-line font size, weighted by how much text each line carries. */
    private float dominantFontSize(List<TextLine> lines) {
        Map<Float, Integer> weights = new HashMap<>();
        for (TextLine line : lines) {
            float rounded = Math.round(line.fontSize() * 2) / 2f;
            weights.merge(rounded, line.text().length(), Integer::sum);
        }
        return weights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0f);
    }

    /**
     * Collects one {@link TextLine} per call to
     * {@link PDFTextStripper#writeString(String, List)}, which PDFBox invokes once
     * per visual line when sorting by position.
     */
    private static final class LineCollector extends PDFTextStripper {

        private final Map<Integer, List<TextLine>> byPage = new HashMap<>();

        LineCollector() throws IOException {
            super();
        }

        List<TextLine> linesOf(int pageNo) {
            return byPage.getOrDefault(pageNo, List.of());
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            if (positions.isEmpty()) {
                return;
            }
            float lineSize = dominantGlyphSize(positions);

            StringBuilder content = new StringBuilder();
            StringBuilder marker = new StringBuilder();
            String leadingMarker = null;
            int boldGlyphs = 0;
            int countedGlyphs = 0;

            for (TextPosition position : positions) {
                String glyph = position.getUnicode();
                if (glyph == null || glyph.isEmpty()) {
                    continue;
                }
                if (isSuperscriptDigit(glyph, position, lineSize)) {
                    marker.append(glyph);
                    continue;
                }
                if (!marker.isEmpty()) {
                    if (content.toString().isBlank()) {
                        leadingMarker = marker.toString();
                    }
                    marker.setLength(0);
                }
                content.append(glyph);
                if (!glyph.isBlank()) {
                    countedGlyphs++;
                    if (isBold(position)) {
                        boldGlyphs++;
                    }
                }
            }
            if (!marker.isEmpty() && content.toString().isBlank()) {
                leadingMarker = marker.toString();
            }

            String value = content.toString().strip();
            if (value.isEmpty() && leadingMarker == null) {
                return;
            }
            TextPosition first = positions.get(0);
            byPage.computeIfAbsent(getCurrentPageNo(), k -> new ArrayList<>())
                    .add(new TextLine(getCurrentPageNo(), value, first.getXDirAdj(), first.getYDirAdj(),
                            lineSize, countedGlyphs > 0 && boldGlyphs * 2 > countedGlyphs, leadingMarker));
        }

        private boolean isSuperscriptDigit(String glyph, TextPosition position, float lineSize) {
            return glyph.length() == 1
                    && Character.isDigit(glyph.charAt(0))
                    && position.getFontSizeInPt() < lineSize * SUPERSCRIPT_RATIO;
        }

        private boolean isBold(TextPosition position) {
            if (position.getFont() == null || position.getFont().getName() == null) {
                return false;
            }
            return position.getFont().getName().toLowerCase().contains("bold");
        }

        private float dominantGlyphSize(List<TextPosition> positions) {
            Map<Float, Integer> counts = new HashMap<>();
            for (TextPosition position : positions) {
                String glyph = position.getUnicode();
                if (glyph == null || glyph.isBlank()) {
                    continue;
                }
                counts.merge(Math.round(position.getFontSizeInPt() * 2) / 2f, 1, Integer::sum);
            }
            return counts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(positions.get(0).getFontSizeInPt());
        }
    }
}
