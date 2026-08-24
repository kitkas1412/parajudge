package me.kitkas1412.parajudge.documents.service.ingestion;

/** Thrown when a statute with the same code has already been ingested. */
public class DuplicateDocumentException extends RuntimeException {

    private final String code;

    public DuplicateDocumentException(String code) {
        super("Văn bản số " + code + " đã tồn tại trong cơ sở dữ liệu");
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
