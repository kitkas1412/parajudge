package me.kitkas1412.parajudge.documents.service.parser;

import me.kitkas1412.parajudge.documents.service.parser.pdf.PageText;
import me.kitkas1412.parajudge.documents.service.parser.pdf.TextLine;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-joins the wrapped lines of the PDF into paragraphs, across page boundaries.
 *
 * <p>The layout does the work: the first line of a paragraph is indented
 * (x ≈ 113) while its continuations sit on the left margin (x ≈ 85), and headings
 * are centred, so they land even further right. A structural prefix
 * ({@code Điều 5.}, {@code 2.}, {@code a)}) starts a new paragraph too, which
 * covers pages whose margin cannot be measured because every line on them is a
 * heading.
 */
public final class TextBlockAssembler {

    /** How far past the left margin a line must start to count as a new paragraph. */
    private static final float INDENT_TOLERANCE = 6.0f;

    public List<TextBlock> assemble(List<PageText> pages) {
        List<TextBlock> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentPage = 0;
        boolean currentBold = false;

        for (PageText page : pages) {
            for (TextLine line : page.body()) {
                boolean startsBlock = current.isEmpty()
                        || line.bold() != currentBold
                        || line.x() > page.leftMargin() + INDENT_TOLERANCE
                        || hasStructuralPrefix(line.text());

                if (startsBlock) {
                    flush(blocks, current, currentPage, currentBold);
                    currentPage = line.page();
                    currentBold = line.bold();
                    current.append(line.text());
                } else {
                    current.append(' ').append(line.text());
                }
            }
        }
        flush(blocks, current, currentPage, currentBold);
        return List.copyOf(blocks);
    }

    private void flush(List<TextBlock> blocks, StringBuilder buffer, int page, boolean bold) {
        String text = buffer.toString().replaceAll("\\s+", " ").strip();
        buffer.setLength(0);
        if (!text.isEmpty()) {
            blocks.add(new TextBlock(page, text, bold));
        }
    }

    private boolean hasStructuralPrefix(String text) {
        String candidate = text.startsWith("“") || text.startsWith("\"") ? text.substring(1) : text;
        return LegalTextPatterns.CHUONG.matcher(candidate).find()
                || LegalTextPatterns.MUC.matcher(candidate).find()
                || LegalTextPatterns.DIEU.matcher(candidate).find()
                || LegalTextPatterns.KHOAN.matcher(candidate).find()
                || LegalTextPatterns.DIEM.matcher(candidate).find();
    }
}
