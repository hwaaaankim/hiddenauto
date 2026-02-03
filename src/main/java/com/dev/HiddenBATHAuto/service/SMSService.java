package com.dev.HiddenBATHAuto.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


@Service
@Slf4j
public class SMSService {

    private static final String API_URL = "https://sslsms.cafe24.com/sms_sender.php";
    private static final String USER_AGENT = "Mozilla/5.0";

    // ✅ 카페24 쪽에서 EUC-KR 계열을 많이 사용 (UTF-8보다 바이트가 줄어 단문 한도에 유리)
    private static final Charset SMS_CHARSET = Charset.forName("EUC-KR");

    // ✅ 단문(SMS) 바이트 기준 (환경에 따라 80/90 차이가 있을 수 있어 상수로 관리)
    private static final int SMS_MAX_BYTES = 90;

    // ✅ 테스트 발송 플래그 (운영이면 false)
    private static final boolean IS_TEST = false;

    // TODO: 실제 값으로 관리(환경변수/설정파일 권장)
    private static final String SMS_USER_ID = "hidden2024";
    private static final String SMS_SECURE_KEY = "26b0c0384f76870b5ebd97b54f392dbb";

    // 발신번호 (등록/인증된 번호여야 함)
    private static final String SPHONE1 = "031";
    private static final String SPHONE2 = "8011";
    private static final String SPHONE3 = "3660";

    public void sendMessage(String phone, String message) {
        HttpURLConnection con = null;

        try {
            String normalizedPhone = normalizePhone(phone);

            // 1) 메시지 EUC-KR 바이트
            byte[] msgBytes = message.getBytes(SMS_CHARSET);
            int msgLen = msgBytes.length;

            // 2) 길이에 따라 smsType 선택
            boolean isLong = msgLen > SMS_MAX_BYTES;
            String smsType = isLong ? "L" : "S";

            // 3) Base64는 "바이트" 기준으로 인코딩해야 안전합니다.
            String b64UserId = b64(SMS_USER_ID.getBytes(SMS_CHARSET));
            String b64Secure = b64(SMS_SECURE_KEY.getBytes(SMS_CHARSET));
            String b64Msg = b64(msgBytes);
            String b64Rphone = b64(normalizedPhone.getBytes(SMS_CHARSET));
            String b64Sphone1 = b64(SPHONE1.getBytes(SMS_CHARSET));
            String b64Sphone2 = b64(SPHONE2.getBytes(SMS_CHARSET));
            String b64Sphone3 = b64(SPHONE3.getBytes(SMS_CHARSET));
            String b64Mode = b64("1".getBytes(SMS_CHARSET));
            String b64SmsType = b64(smsType.getBytes(SMS_CHARSET));

            // 4) form-urlencoded에서는 base64에 포함된 '+' '/' '=' 등이 깨질 수 있어 URL 인코딩 필수
            StringBuilder sb = new StringBuilder();
            sb.append("user_id=").append(url(b64UserId))
              .append("&secure=").append(url(b64Secure))
              .append("&msg=").append(url(b64Msg))
              .append("&rphone=").append(url(b64Rphone))
              .append("&sphone1=").append(url(b64Sphone1))
              .append("&sphone2=").append(url(b64Sphone2))
              .append("&sphone3=").append(url(b64Sphone3))
              .append("&mode=").append(url(b64Mode))
              .append("&smsType=").append(url(b64SmsType));

            // ✅ LMS면 subject(제목)도 같이 넣는 것이 안전합니다(일부 환경에서 필요)
            if (isLong) {
                String subject = "비밀번호 초기화 안내";
                String b64Subject = b64(subject.getBytes(SMS_CHARSET));
                sb.append("&subject=").append(url(b64Subject));
            }

            if (IS_TEST) {
                String b64Testflag = b64("Y".getBytes(SMS_CHARSET));
                sb.append("&testflag=").append(url(b64Testflag));
            }

            byte[] out = sb.toString().getBytes(StandardCharsets.UTF_8);

            URL obj = new URL(API_URL);
            con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", USER_AGENT);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            con.setDoOutput(true);

            try (OutputStream os = con.getOutputStream()) {
                os.write(out);
                os.flush();
            }

            int responseCode = con.getResponseCode();
            log.info("POST Response Code :: {}", responseCode);

            BufferedReader in;
            if (responseCode >= 200 && responseCode < 300) {
                in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            } else {
                in = new BufferedReader(new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8));
            }

            String line;
            StringBuilder buf = new StringBuilder();
            while ((line = in.readLine()) != null) {
                buf.append(line);
            }
            in.close();

            log.info("SMS Response: {}", buf);

        } catch (IOException e) {
            log.error("SMS IOException : {}", e.getMessage(), e);
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String normalizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9]", "");
    }

    private static String b64(byte[] bytes) {
        return new String(Base64.encodeBase64(bytes), StandardCharsets.US_ASCII);
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}