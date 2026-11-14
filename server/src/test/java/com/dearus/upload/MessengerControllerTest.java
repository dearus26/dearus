package com.dearus.upload;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearus.common.GlobalExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 메신저 업로드가 txt만 받는지(사진 · multipart는 막고, 스크린샷 경로는 없는지) 서비스 없이 확인해요(2026.11.11 결정). */
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
	}

	@Test
	void 글자_API에_사진을_보내면_415_IMAGES_NOT_ACCEPTED예요() throws Exception {
		mvc.perform(multipart("/uploads/messenger").file(new MockMultipartFile("images", "a.png", "image/png", PNG)))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("IMAGES_NOT_ACCEPTED"));
		verifyNoInteractions(service);
	}

	@Test
	void 스크린샷_올리기_경로는_없어서_405예요() throws Exception {
		mvc.perform(multipart("/uploads/messenger/images").file(new MockMultipartFile("images", "a.png", "image/png", PNG))
				.param("source", "KAKAOTALK").param("consent", "true"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		verifyNoInteractions(service);
	}
}
