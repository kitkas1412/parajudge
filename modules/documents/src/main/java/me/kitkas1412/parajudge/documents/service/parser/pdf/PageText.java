package me.kitkas1412.parajudge.documents.service.parser.pdf;

import java.util.List;

/**
 * Text of a single PDF page, already split into body lines and page-bottom
 * footnote lines.
 *
 * @param bodyFontSize the dominant font size on the page, used as the reference
 *                     for footnote and superscript detection
 * @param leftMargin   the smallest x of any body line; wrapped continuation lines
 *                     sit on it while new paragraphs are indented past it
 */
public record PageText(
        int pageNo,
        List<TextLine> body,
        List<TextLine> footnotes,
        float bodyFontSize,
        float leftMargin) {

    public String bodyAsText() {
        StringBuilder sb = new StringBuilder();
        for (TextLine line : body) {
            sb.append(line.text()).append('\n');
        }
        return sb.toString();
    }
}
