"""Model the source-degradation invariants used by the S2.2 feasibility gate."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class TranscriptObservation(Enum):
    MISSING = "MISSING"
    DELAYED = "DELAYED"
    SAME_ID = "SAME_ID"
    IDENTITY_CONFLICT = "IDENTITY_CONFLICT"


@dataclass(frozen=True)
class SourceDegradationResult:
    formal_trace_available: bool
    performance_status: str
    content_status: str
    alignment: str
    recompute_when_transcript_changes: bool


def classify_source_degradation(
    *,
    otel_turn_available: bool,
    transcript: TranscriptObservation,
) -> SourceDegradationResult:
    """Apply the OTel-first trace and transcript-content ownership rules."""
    if not otel_turn_available:
        content_status = (
            "INSPECTION_ONLY"
            if transcript
            in {TranscriptObservation.SAME_ID, TranscriptObservation.IDENTITY_CONFLICT}
            else transcript.value
        )
        return SourceDegradationResult(
            formal_trace_available=False,
            performance_status="UNAVAILABLE",
            content_status=content_status,
            alignment="UNMATCHED",
            recompute_when_transcript_changes=(
                transcript is TranscriptObservation.DELAYED
            ),
        )

    if transcript is TranscriptObservation.SAME_ID:
        return SourceDegradationResult(
            formal_trace_available=True,
            performance_status="AVAILABLE",
            content_status="AVAILABLE",
            alignment="EXACT",
            recompute_when_transcript_changes=False,
        )
    if transcript is TranscriptObservation.IDENTITY_CONFLICT:
        return SourceDegradationResult(
            formal_trace_available=True,
            performance_status="AVAILABLE",
            content_status="CONFLICT",
            alignment="UNMATCHED",
            recompute_when_transcript_changes=False,
        )

    content_status = (
        "PENDING"
        if transcript is TranscriptObservation.DELAYED
        else transcript.value
    )
    return SourceDegradationResult(
        formal_trace_available=True,
        performance_status="AVAILABLE",
        content_status=content_status,
        alignment="UNMATCHED",
        recompute_when_transcript_changes=(
            transcript is TranscriptObservation.DELAYED
        ),
    )
