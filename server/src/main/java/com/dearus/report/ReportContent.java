package com.dearus.report;

/** AI가 만든 리포트의 세 항목이에요. 개인 모드의 "당시 고민 · 현재 변화 · 한 줄 요약"이고, oneLineSummary는 100자 이하예요. */
public record ReportContent(String pastConcern, String presentChange, String oneLineSummary) {
}
