package dev.tracelens;

/** Prevent destructive fixture cleanup from ever targeting an application database. */
abstract class MySqlIntegrationSupport {
    static {
        if (!"1".equals(System.getenv("TRACE_LENS_MYSQL_TEST"))) {
            throw new IllegalStateException("Run MySQL integration tests through backend/scripts/test_mysql.py");
        }
    }
}
