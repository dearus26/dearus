package com.dearus.common.ai;

import java.util.Objects;
import java.util.Optional;

/**
 * AI로 보내는 이미지 한 장이에요(메신저 스크린샷). 글자처럼 이미지도 로그 · 디스크 · DB에 남기지 않고 메모리에서만 다뤄요.
 * toString은 바이트를 찍지 않고 형식과 크기만 보여줘요.
 */
public record AiImage(MediaType mediaType, byte[] data) {

	/** AI의 이미지 입력이 바로 읽는 형식이에요(서버가 변환하지 않아요). */
	public enum MediaType {
		JPEG, PNG, WEBP;

		/**
		 * 파일 앞부분(매직 넘버)으로 형식을 알아내요. 앱이 보낸 Content-Type은 틀릴 수 있어서 믿지 않아요.
		 * JPEG FF D8 FF · PNG 89 50 4E 47 0D 0A 1A 0A · WebP 0번째 "RIFF" + 8번째 "WEBP"
		 */
		public static Optional<MediaType> detect(byte[] data) {
			if (data == null) {
				return Optional.empty();
			}
			if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) {
				return Optional.of(JPEG);
			}
			if (startsWith(data, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
				return Optional.of(PNG);
			}
			if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) {
				return Optional.of(WEBP);
			}
			return Optional.empty();
		}

		private static boolean startsWith(byte[] data, int offset, int... expected) {
			if (data.length < offset + expected.length) {
				return false;
			}
			for (int i = 0; i < expected.length; i++) {
				if ((data[offset + i] & 0xFF) != expected[i]) {
					return false;
				}
			}
			return true;
		}
	}

	public AiImage {
		Objects.requireNonNull(mediaType, "mediaType");
		if (data == null || data.length == 0) {
			throw new IllegalArgumentException("이미지가 비어 있어요");
		}
	}

	@Override
	public String toString() {
		return "AiImage[" + mediaType + ", " + data.length + " bytes]";
	}
}
