package com.dearus.diary;

import java.util.List;

/** API 명세의 DiaryList. */
public record DiaryListResponse(List<DiaryResponse> items) {
}
