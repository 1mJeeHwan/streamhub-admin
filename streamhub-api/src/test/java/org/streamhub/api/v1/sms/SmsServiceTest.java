package org.streamhub.api.v1.sms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.streamhub.api.v1.sms.entity.SmsChannel;

/**
 * Unit tests for the SMS channel (SMS/LMS) decision and recipient masking. The channel rule and
 * masking are pure functions, so they are exercised directly without Spring wiring.
 */
class SmsServiceTest {

    private final SmsService service = new SmsService(null, null, null, null, null);

    @DisplayName("EUC-KR 90바이트 초과 본문은 LMS, 이하이면 SMS")
    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource({
            "'짧은 알림입니다', SMS",
            "'안녕하세요 결제가 완료되었습니다 감사합니다', SMS"
    })
    void resolveChannel_short(String content, SmsChannel expected) {
        assertThat(service.resolveChannel(content)).isEqualTo(expected);
    }

    @DisplayName("긴 본문(>90 EUC-KR byte)은 LMS")
    @ParameterizedTest
    @ValueSource(strings = {
            "한글은 EUC-KR에서 글자당 2바이트이므로 마흔여섯 글자를 넘어가는 충분히 긴 안내 문구는 엘엠에스로 분류되어야 정상입니다 확인용"
    })
    void resolveChannel_long(String content) {
        assertThat(service.resolveChannel(content)).isEqualTo(SmsChannel.LMS);
    }

    @DisplayName("null 본문은 기본 SMS")
    @org.junit.jupiter.api.Test
    void resolveChannel_null() {
        assertThat(service.resolveChannel(null)).isEqualTo(SmsChannel.SMS);
    }

    @DisplayName("수신번호는 끝 4자리를 마스킹한다")
    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource({
            "'010-1234-5678', '010-1234-****'",
            "'01012345678', '010-1234-****'",
            "'010 9876 5432', '010-9876-****'"
    })
    void maskNumber_masksLast4(String raw, String expected) {
        assertThat(service.maskNumber(raw)).isEqualTo(expected);
    }

    @DisplayName("null/빈 번호는 플레이스홀더로 마스킹")
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void maskNumber_blank(String raw) {
        assertThat(service.maskNumber(raw)).isEqualTo("010-0000-****");
    }
}
