package com.dev.HiddenBATHAuto.config.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.as-notification")
public class AsNotificationProperties {
    private boolean enabled = true;
    private boolean notifyActor = false;
    private final Kakao kakao = new Kakao();

    @Getter
    @Setter
    public static class Kakao {
        private boolean enabled = true;
        private String templateCode = "D8jAppuvn1";
    }
}
