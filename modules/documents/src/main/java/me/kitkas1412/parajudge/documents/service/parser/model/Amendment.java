package me.kitkas1412.parajudge.documents.service.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One {@code Khoản} of Điều 219 — the set of changes this code makes to a single
 * other law.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Amendment(
        String khoanNo,
        String targetLaw,
        String instruction,
        List<AmendmentItem> items) {
}
