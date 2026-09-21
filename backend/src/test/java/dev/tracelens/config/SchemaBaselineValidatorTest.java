package dev.tracelens.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SchemaBaselineValidatorTest {
    @Test
    void acceptsTheSingleExpectedBaselineWithoutWritingToTheDatabase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(SchemaBaselineValidator.VERSION_QUERY, Integer.class))
                .thenReturn(List.of(2));

        assertThatCode(() -> new SchemaBaselineValidator(jdbcTemplate).run(null))
                .doesNotThrowAnyException();

        verify(jdbcTemplate).queryForList(SchemaBaselineValidator.VERSION_QUERY, Integer.class);
        verify(jdbcTemplate, never()).update(org.mockito.ArgumentMatchers.anyString());
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void rejectsMissingMultipleAndUnexpectedBaselinesWithAStableCategory() {
        assertInvalidBaseline(List.of());
        assertInvalidBaseline(List.of(2, 2));
        assertInvalidBaseline(List.of(1));
        assertInvalidBaseline(List.of(3));
    }

    private static void assertInvalidBaseline(List<Integer> versions) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(SchemaBaselineValidator.VERSION_QUERY, Integer.class))
                .thenReturn(versions);

        assertThatThrownBy(() -> new SchemaBaselineValidator(jdbcTemplate).run(null))
                .isInstanceOf(SchemaBaselineMismatchException.class)
                .hasMessage("SCHEMA_BASELINE_MISMATCH: expected exactly baseline 2");

        verify(jdbcTemplate).queryForList(SchemaBaselineValidator.VERSION_QUERY, Integer.class);
        verifyNoMoreInteractions(jdbcTemplate);
    }
}
