package me.kitkas1412.parajudge.documents.parser;

import me.kitkas1412.parajudge.documents.parser.model.Amendment;
import me.kitkas1412.parajudge.documents.parser.model.AmendmentItem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Parses the nested-law article — Điều 219, {@code Sửa đổi, bổ sung một số điều của
 * các luật có liên quan đến lao động}.
 *
 * <p>This article is the one place in the code where the numbering is not its own.
 * Its {@code Khoản} name a target law, its {@code Điểm} name a change to make, and
 * each change is followed by a “…” block reproducing the new text of that other
 * statute — a block carrying its own {@code Điều}, {@code Khoản} and {@code Điểm}
 * numbering that restarts from 1. Feeding those to the ordinary parser would create
 * a second Điều 32 and Điều 54 inside the Labour Code and scramble the clause
 * sequence, so they are captured verbatim under the amendment that introduced them.
 *
 * <p>Quote depth, not indentation, marks the boundary: everything between an opening
 * “ and its matching ” is quoted law.
 */
public final class Article219Parser {

    private final List<Amendment> amendments = new ArrayList<>();

    private AmendmentBuilder currentAmendment;
    private ItemBuilder currentItem;

    /**
     * @param insideQuote whether the caller's quote tracking says this block is
     *                    still inside a “…” block opened by an earlier one
     */
    public void feed(TextBlock block, boolean insideQuote) {
        String text = block.text();
        if (insideQuote || text.startsWith("“")) {
            captureQuoted(text);
            return;
        }

        Matcher khoan = LegalTextPatterns.KHOAN.matcher(text);
        if (khoan.matches() && LegalTextPatterns.isNextKhoan(currentKhoanNo(), khoan.group(1))) {
            closeAmendment();
            currentAmendment = new AmendmentBuilder(khoan.group(1), khoan.group(2));
            return;
        }

        Matcher diem = LegalTextPatterns.DIEM.matcher(text);
        if (currentAmendment != null && diem.matches()
                && LegalTextPatterns.isNextDiem(currentAmendment.lastDiemNo, diem.group(1))) {
            closeItem();
            currentItem = new ItemBuilder(diem.group(1), diem.group(2));
            currentAmendment.lastDiemNo = diem.group(1);
            return;
        }

        // An unnumbered continuation of whatever is currently open.
        if (currentItem != null) {
            currentItem.instruction += "\n" + text;
        } else if (currentAmendment != null) {
            currentAmendment.instruction += "\n" + text;
        }
    }

    public List<Amendment> build() {
        closeAmendment();
        return List.copyOf(amendments);
    }

    private void captureQuoted(String text) {
        if (currentItem == null) {
            if (currentAmendment != null) {
                currentAmendment.instruction += "\n" + text;
            }
            return;
        }
        Matcher heading = LegalTextPatterns.DIEU.matcher(text.replaceAll("^[“\"]+", "").strip());
        if (currentItem.targetArticleNo == null && heading.lookingAt()) {
            currentItem.targetArticleNo = Integer.parseInt(heading.group(1));
            currentItem.targetArticleTitle = heading.group(2).strip();
        }
        currentItem.quoted.add(text);
    }

    private String currentKhoanNo() {
        return currentAmendment == null ? null : currentAmendment.khoanNo;
    }

    private void closeItem() {
        if (currentItem != null && currentAmendment != null) {
            currentAmendment.items.add(currentItem.build());
        }
        currentItem = null;
    }

    private void closeAmendment() {
        closeItem();
        if (currentAmendment != null) {
            amendments.add(currentAmendment.build());
            currentAmendment = null;
        }
    }

    private static final class AmendmentBuilder {
        private final String khoanNo;
        private final List<AmendmentItem> items = new ArrayList<>();
        private String instruction;
        private String lastDiemNo;

        private AmendmentBuilder(String khoanNo, String instruction) {
            this.khoanNo = khoanNo;
            this.instruction = instruction;
        }

        private Amendment build() {
            Matcher law = LegalTextPatterns.TARGET_LAW.matcher(instruction);
            return new Amendment(khoanNo, law.find() ? law.group(1).strip() : null,
                    instruction, List.copyOf(items));
        }
    }

    private static final class ItemBuilder {
        private final String diemNo;
        private final List<String> quoted = new ArrayList<>();
        private String instruction;
        private Integer targetArticleNo;
        private String targetArticleTitle;

        private ItemBuilder(String diemNo, String instruction) {
            this.diemNo = diemNo;
            this.instruction = instruction;
        }

        private AmendmentItem build() {
            return new AmendmentItem(diemNo, instruction, targetArticleNo, targetArticleTitle,
                    List.copyOf(quoted));
        }
    }
}
