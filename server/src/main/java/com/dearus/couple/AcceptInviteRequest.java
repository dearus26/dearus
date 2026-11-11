package com.dearus.couple;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API 명세의 AcceptInviteRequest. 형식(DU-XXXX-XXXX)은 대문자로 바꾼 뒤 서비스에서 확인해요(소문자로 넣어도 돼요). */
public record AcceptInviteRequest(@NotBlank(message = "꼭 필요해요") @Size(max = 20, message = "20자까지예요") String inviteCode) {
}
