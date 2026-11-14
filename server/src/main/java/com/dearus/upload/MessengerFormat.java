package com.dearus.upload;

/** 메신저 대화를 어떤 형식으로 올렸는지 (API 명세의 MessengerUpload.format). 지금은 TXT만 받아요. IMAGE는 2026.11.11 전 업로드에만 있어요. */
public enum MessengerFormat {
	TXT, IMAGE
}
