package com.dearus.couple;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 초대 코드 "DU-XXXX-XXXX"를 만들고 읽어요. 헷갈리는 글자(0 · O · 1 · I)는 쓰지 않아요.
 * 32글자 중 8자리라 약 1조 가지예요. 24시간만 유효하고 기다리는 초대끼리만 겹치지 않으면 돼요.
 */
final class InviteCodes {

	static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	/** API 명세의 AcceptInviteRequest.inviteCode 형식 */
	static final Pattern FORMAT = Pattern.compile("^DU-[A-Z0-9]{4}-[A-Z0-9]{4}$");
	private static final SecureRandom RANDOM = new SecureRandom();

	private InviteCodes() {
	}

	static String generate() {
		StringBuilder code = new StringBuilder("DU-");
		for (int i = 0; i < 8; i++) {
			if (i == 4) {
				code.append('-');
			}
			code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

	/** 앞뒤 공백을 지우고 대문자로 바꿔요. 형식이 맞지 않으면 null이에요. */
	static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		String code = raw.strip().toUpperCase(Locale.ROOT);
		return FORMAT.matcher(code).matches() ? code : null;
	}
}
