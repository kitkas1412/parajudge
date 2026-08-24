package me.kitkas1412.parajudge.documents.service.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** A {@code Khoản}. {@code no} is textual because of inserted clauses such as {@code 1a}. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParsedClause(
        String no,
        int page,
        String text,
        List<ParsedPoint> points) {
}
