package com.dev.HiddenBATHAuto.config.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.order-notification")
public class OrderNotificationProperties {

    /** 웹 종 알림 생성 여부 */
    private boolean enabled = true;

    /** 같은 이벤트를 만든 본인에게도 다시 알릴지 여부 */
    private boolean notifyActor = false;

    private final Kakao kakao = new Kakao();

    @Getter
    @Setter
    public static class Kakao {
        /** SOLAPI 연계 자체 활성화 */
        private boolean enabled = false;
        /** 긴급 관리자요청 발송 */
        private boolean emergencyEnabled = true;
        /** 일반 변경/완료/담당자 변경 발송 */
        private boolean normalEnabled = false;
    }
}
