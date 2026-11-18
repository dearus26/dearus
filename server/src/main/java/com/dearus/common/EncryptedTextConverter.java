package com.dearus.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * 엔티티의 글자 필드를 DB에는 암호화한 바이트(*_enc, bytea)로 저장하고, 읽을 때 풀어 주는 변환기예요.
 * 필드에 {@code @Convert(converter = EncryptedTextConverter.class)}를 붙이면 서비스 코드는 평문 그대로 다뤄요.
 * Hibernate가 스프링 빈으로 만들어서 {@link CryptoService}(ENCRYPTION_KEY)를 받아요.
 * 암호문은 매번 달라서 DB에서 내용으로 찾거나 정렬할 수 없어요.
 */
@Component
@Converter
public class EncryptedTextConverter implements AttributeConverter<String, byte[]> {

	private final CryptoService crypto;

	public EncryptedTextConverter(CryptoService crypto) {
		this.crypto = crypto;
	}

	@Override
	public byte[] convertToDatabaseColumn(String plain) {
		return plain == null ? null : crypto.encrypt(plain);
	}

	@Override
	public String convertToEntityAttribute(byte[] encrypted) {
		return encrypted == null ? null : crypto.decrypt(encrypted);
	}
}
