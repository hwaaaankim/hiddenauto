package com.dev.HiddenBATHAuto.provider.notification;

import java.util.Iterator;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.dev.HiddenBATHAuto.dto.notification.SolapiSendApiResult;
import com.dev.HiddenBATHAuto.dto.notification.SolapiSendRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SolapiMessageClient {

    private final RestClient solapiRestClient;
    private final SolapiAuthHeaderProvider authHeaderProvider;
    private final ObjectMapper objectMapper;

    public SolapiSendApiResult sendManyDetail(SolapiSendRequest request) {
        try {
            String responseBody = solapiRestClient.post()
                    .uri("/messages/v4/send-many/detail")
                    .header(HttpHeaders.AUTHORIZATION, authHeaderProvider.createAuthorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            return parseSuccessResponse(responseBody);
        } catch (RestClientResponseException e) {
            return SolapiSendApiResult.builder()
                    .httpStatus(e.getStatusCode().value())
                    .responseBody(e.getResponseBodyAsString())
                    .statusCode(extractErrorCode(e.getResponseBodyAsString()))
                    .statusMessage(extractErrorMessage(e.getResponseBodyAsString()))
                    .build();
        } catch (Exception e) {
            return SolapiSendApiResult.builder()
                    .httpStatus(0)
                    .responseBody(e.getMessage())
                    .statusCode("LOCAL_ERROR")
                    .statusMessage(e.getMessage())
                    .build();
        }
    }

    private SolapiSendApiResult parseSuccessResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        String groupId = textAt(root, "/groupInfo/groupId");
        if (groupId == null) {
            groupId = textAt(root, "/groupInfo/_id");
        }

        String messageId = findFirstTextByField(root, "messageId");
        String statusCode = findFirstTextByField(root, "statusCode");
        String statusMessage = findFirstTextByField(root, "statusMessage");

        return SolapiSendApiResult.builder()
                .httpStatus(200)
                .responseBody(responseBody)
                .groupId(groupId)
                .messageId(messageId)
                .statusCode(statusCode)
                .statusMessage(statusMessage)
                .build();
    }

    private String extractErrorCode(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String errorCode = textAt(root, "/errorCode");
            return errorCode != null ? errorCode : textAt(root, "/statusCode");
        } catch (Exception e) {
            return "HTTP_ERROR";
        }
    }

    private String extractErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String errorMessage = textAt(root, "/errorMessage");
            return errorMessage != null ? errorMessage : textAt(root, "/statusMessage");
        } catch (Exception e) {
            return body;
        }
    }

    private String textAt(JsonNode root, String pointer) {
        JsonNode node = root.at(pointer);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String findFirstTextByField(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            JsonNode direct = node.get(fieldName);
            if (direct != null && direct.isValueNode()) {
                String value = direct.asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String found = findFirstTextByField(entry.getValue(), fieldName);
                if (found != null) {
                    return found;
                }
            }
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findFirstTextByField(child, fieldName);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }
}