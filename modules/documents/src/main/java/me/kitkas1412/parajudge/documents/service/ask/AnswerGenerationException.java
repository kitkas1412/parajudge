package me.kitkas1412.parajudge.documents.service.ask;

/**
 * The model could not be reached or refused the request.
 *
 * <p>Kept apart from an unanswerable question on purpose. "The corpus does not say" and
 * "the model server is down" look the same to a caller that only sees
 * {@code answered: false}, and the first would send someone off believing the law is
 * silent when it is not.
 */
public class AnswerGenerationException extends RuntimeException {

    public AnswerGenerationException(Throwable cause) {
        super("Khong goi duoc model sinh cau tra loi: " + cause.getMessage(), cause);
    }
}
