package com.dearus.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearus.common.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/** 스크린샷 API의 멀티파트 받기(파트 이름 · 순서 · 값 변환)와 형식 · 크기 오류 응답을 서비스 없이 확인해요. */
class MessengerControllerTest {

	private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1};

	private final MessengerService service = mock(MessengerService.class);
	private final UUID userId = UUID.randomUUID();
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		// 시험에서는 로그인(JWT 검증) 대신 이 사용자의 토큰을 바로 넣어요
		HandlerMethodArgumentResolver jwt = new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parameter) {
				return parameter.getParameterType().equals(Jwt.class);
			}

			@Override
			public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request,
				WebDataBinderFactory binderFactory) {
				return Jwt.withTokenValue("token").header("alg", "none").subject(userId.toString()).build();
			}
		};
		mvc = MockMvcBuilders.standaloneSetup(new MessengerController(service))
			.setCustomArgumentResolvers(jwt)
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
		when(service.acceptImages(any(), any(), any(), any()))
			.thenReturn(MessengerUploadResponse.from(MessengerUpload.receive(userId, MessengerSource.KAKAOTALK, 2), null));
	}

	@Test
	@SuppressWarnings("unchecked")
	void 멀티파트의_source_consent_images를_올린_순서대로_받아서_202예요() throws Exception {
		mvc.perform(multipart("/uploads/messenger/images")
				.file(new MockMultipartFile("images", "first.png", "image/png", PNG))
				.file(new MockMultipartFile("images", "second.png", "image/png", PNG))
				.param("source", "KAKAOTALK")
				.param("consent", "true"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.format").value("IMAGE"))
			.andExpect(jsonPath("$.photoCount").value(2));

		ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.forClass(List.class);
		verify(service).acceptImages(eq(userId), eq(MessengerSource.KAKAOTALK), eq(true), files.capture());
		assertThat(files.getValue()).extracting(MultipartFile::getOriginalFilename).containsExactly("first.png", "second.png");
	}

	@Test
	void 스크린샷_API에_멀티파트가_아닌_본문을_보내면_415_UNSUPPORTED_IMAGE_TYPE이에요() throws Exception {
		mvc.perform(post("/uploads/messenger/images").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));
		verify(service, never()).acceptImages(any(), any(), any(), any());
	}

	@Test
	void 글자_API에_사진을_보내면_415_IMAGES_NOT_ACCEPTED예요() throws Exception {
		mvc.perform(multipart("/uploads/messenger").file(new MockMultipartFile("images", "a.png", "image/png", PNG)))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("IMAGES_NOT_ACCEPTED"));
	}

	@Test
	void consent나_source가_알_수_없는_값이면_400이에요() throws Exception {
		mvc.perform(multipart("/uploads/messenger/images").file(new MockMultipartFile("images", "a.png", "image/png", PNG))
				.param("source", "KAKAOTALK").param("consent", "네"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mvc.perform(multipart("/uploads/messenger/images").file(new MockMultipartFile("images", "a.png", "image/png", PNG))
				.param("source", "LINE").param("consent", "true"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		verify(service, never()).acceptImages(any(), any(), any(), any());
	}

	@Test
	void 멀티파트_크기_한도를_넘으면_413_PAYLOAD_TOO_LARGE예요() throws Exception {
		when(service.acceptImages(any(), any(), any(), any())).thenThrow(new MaxUploadSizeExceededException(50L * 1024 * 1024));

		mvc.perform(multipart("/uploads/messenger/images").file(new MockMultipartFile("images", "a.png", "image/png", PNG))
				.param("source", "KAKAOTALK").param("consent", "true"))
			.andExpect(status().isContentTooLarge())
			.andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
	}
}
