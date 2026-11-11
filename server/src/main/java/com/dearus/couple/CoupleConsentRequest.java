package com.dearus.couple;

/** API 명세의 ConsentRequest(커플). 동의 화면에서 체크하고 "동의하고 계속하기"를 누르면 agreed가 true예요. */
public record CoupleConsentRequest(Boolean agreed) {
}
