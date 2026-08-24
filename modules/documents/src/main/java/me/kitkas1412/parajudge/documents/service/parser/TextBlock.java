package me.kitkas1412.parajudge.documents.parser;

/** A paragraph, re-joined from the wrapped lines PDFBox reports. */
public record TextBlock(int page, String text, boolean bold) {
}
