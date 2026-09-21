import unittest

from scripts.source_degradation_probe import (
    TranscriptObservation,
    classify_source_degradation,
)


class SourceDegradationProbeTest(unittest.TestCase):
    def test_transcript_without_otel_is_inspection_only(self):
        result = classify_source_degradation(
            otel_turn_available=False,
            transcript=TranscriptObservation.SAME_ID,
        )

        self.assertFalse(result.formal_trace_available)
        self.assertEqual(result.performance_status, "UNAVAILABLE")
        self.assertEqual(result.content_status, "INSPECTION_ONLY")
        self.assertEqual(result.alignment, "UNMATCHED")

    def test_otel_without_transcript_keeps_formal_performance_trace(self):
        result = classify_source_degradation(
            otel_turn_available=True,
            transcript=TranscriptObservation.MISSING,
        )

        self.assertTrue(result.formal_trace_available)
        self.assertEqual(result.performance_status, "AVAILABLE")
        self.assertEqual(result.content_status, "MISSING")
        self.assertEqual(result.alignment, "UNMATCHED")

    def test_delayed_transcript_is_pending_without_hiding_otel_trace(self):
        result = classify_source_degradation(
            otel_turn_available=True,
            transcript=TranscriptObservation.DELAYED,
        )

        self.assertTrue(result.formal_trace_available)
        self.assertEqual(result.performance_status, "AVAILABLE")
        self.assertEqual(result.content_status, "PENDING")
        self.assertEqual(result.alignment, "UNMATCHED")
        self.assertTrue(result.recompute_when_transcript_changes)

    def test_matching_identity_allows_exact_turn_content_link(self):
        result = classify_source_degradation(
            otel_turn_available=True,
            transcript=TranscriptObservation.SAME_ID,
        )

        self.assertTrue(result.formal_trace_available)
        self.assertEqual(result.content_status, "AVAILABLE")
        self.assertEqual(result.alignment, "EXACT")

    def test_identity_conflict_preserves_trace_without_merging_content(self):
        result = classify_source_degradation(
            otel_turn_available=True,
            transcript=TranscriptObservation.IDENTITY_CONFLICT,
        )

        self.assertTrue(result.formal_trace_available)
        self.assertEqual(result.performance_status, "AVAILABLE")
        self.assertEqual(result.content_status, "CONFLICT")
        self.assertEqual(result.alignment, "UNMATCHED")
        self.assertFalse(result.recompute_when_transcript_changes)


if __name__ == "__main__":
    unittest.main()
