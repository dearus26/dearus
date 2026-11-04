package com.dearus.report;

/** GET report가 202로 돌려주는 "만드는 중" 응답이에요. */
public record ReportPending(ReportStatus status) {
}
