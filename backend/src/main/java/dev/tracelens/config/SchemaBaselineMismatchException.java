package dev.tracelens.config;

/** Stable startup configuration failure that intentionally contains no connection details. */
public class SchemaBaselineMismatchException extends IllegalStateException {
    public SchemaBaselineMismatchException() {
        super("SCHEMA_BASELINE_MISMATCH: expected exactly baseline 2");
    }
}
