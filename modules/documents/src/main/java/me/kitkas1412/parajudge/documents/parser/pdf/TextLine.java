package me.kitkas1412.parajudge.documents.parser.pdf;

/**
 * One visual line of a PDF page together with the layout facts the parser needs:
 * where it starts horizontally (first-line indent vs. wrapped continuation),
 * how big it is (body vs. footnote) and whether it is bold (headings are).
 *
 * @param leadingMarker the superscript digits the line started with, already
 *                      removed from {@link #text()}; {@code null} when there were
 *                      none. On a footnote line this is the footnote's number.
 */
public record TextLine(
        int page,
        String text,
        float x,
        float y,
        float fontSize,
        boolean bold,
        String leadingMarker) {

    public boolean isBlank() {
        return text.isBlank();
    }
}
