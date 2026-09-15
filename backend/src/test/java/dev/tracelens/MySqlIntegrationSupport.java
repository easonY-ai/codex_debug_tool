package dev.tracelens;

/** Prevent destructive fixture cleanup from ever targeting an application database. */
abstract class MySqlIntegrationSupport {
    @org.springframework.test.context.DynamicPropertySource
    static void testDatabase(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("analyzer.database.url", () -> "jdbc:mysql://127.0.0.1:3306/codex_analyze_test");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private javax.sql.DataSource testDataSource;

    @org.junit.jupiter.api.BeforeEach
    void verifyTestSchemaBeforeCleanup() throws java.sql.SQLException {
        try (var connection = testDataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next() || !"codex_analyze_test".equals(result.getString(1))) {
                throw new IllegalStateException("Refusing fixture cleanup outside codex_analyze_test");
            }
        }
    }

    static {
        if (!"1".equals(System.getenv("TRACE_LENS_MYSQL_TEST"))) {
            throw new IllegalStateException("Set TRACE_LENS_MYSQL_TEST=1 to use the dedicated codex_analyze_test schema");
        }
    }
}
