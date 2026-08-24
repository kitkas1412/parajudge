package me.kitkas1412.parajudge.documents;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Slice tests search upwards for a {@code @SpringBootConfiguration}, and this
 * module ships web components without an application class of its own — the real
 * one lives in {@code start}. This stands in for it so the module's tests do not
 * depend on the module that boots it.
 */
@SpringBootApplication
public class DocumentsTestApplication {
}
