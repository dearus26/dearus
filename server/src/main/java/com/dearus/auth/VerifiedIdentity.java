package com.dearus.auth;

/**
 * 제공자가 확인해 준 사람이에요. providerUserId는 그 제공자 안에서 바뀌지 않는 사용자 ID(구글 sub, 카카오 id)이고,
 * displayName은 제공자 프로필의 이름(없으면 null)이에요. 이메일은 받지도 저장하지도 않아요.
 */
public record VerifiedIdentity(String providerUserId, String displayName) {
}
