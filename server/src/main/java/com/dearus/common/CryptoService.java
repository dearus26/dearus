package com.dearus.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 메신저 요약 · 페르소나 분석처럼 DB에 암호화해서 두는 값(ERD의 *_enc 컬럼)을 다뤄요.
 * AES-256-GCM이고, 저장하는 바이트는 [12바이트 IV][암호문 + 인증 태그]예요. 같은 글자도 매번 다른 바이트가 돼요.
 */
@Component
public class CryptoService {

	private static final int KEY_BYTES = 32;
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec key;
	private final SecureRandom random = new SecureRandom();

	public CryptoService(CryptoProperties properties) {
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(properties.key() == null ? "" : properties.key().trim());
		} catch (IllegalArgumentException e) {
			raw = new byte[0];
		}
		if (raw.length != KEY_BYTES) {
			throw new IllegalStateException("ENCRYPTION_KEY는 base64로 인코딩한 32바이트여야 해요. "
				+ "만들기: node -e \"console.log(require('crypto').randomBytes(32).toString('base64'))\"");
		}
		this.key = new SecretKeySpec(raw, "AES");
	}

	public byte[] encrypt(String plain) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] out = new byte[IV_BYTES + encrypted.length];
			System.arraycopy(iv, 0, out, 0, IV_BYTES);
			System.arraycopy(encrypted, 0, out, IV_BYTES, encrypted.length);
			return out;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("암호화에 실패했어요", e);
		}
	}

	/** 키가 다르거나 바이트가 바뀌었으면 예외가 나요 (인증 태그 검사). */
	public String decrypt(byte[] data) {
		if (data == null || data.length <= IV_BYTES) {
			throw new IllegalArgumentException("암호화된 값이 아니에요");
		}
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(data, 0, IV_BYTES)));
			return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("복호화에 실패했어요 (키가 다르거나 값이 바뀌었어요)", e);
		}
	}
}
