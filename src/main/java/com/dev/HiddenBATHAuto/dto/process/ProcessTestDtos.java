package com.dev.HiddenBATHAuto.dto.process;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dev.HiddenBATHAuto.enums.process.ProcessAnswerType;
import com.dev.HiddenBATHAuto.enums.process.ProcessExecutionStatus;
import com.dev.HiddenBATHAuto.enums.process.ProcessInputValueType;
import com.dev.HiddenBATHAuto.enums.process.ProcessStatus;

import lombok.Getter;
import lombok.Setter;

public class ProcessTestDtos {

    @Getter
    @Setter
    public static class TestProcessSummaryResponse {
        private Long id;
        private String processKey;
        private String name;
        private String description;
        private ProcessStatus status;
        private LocalDateTime createdAt;
    }

    @Getter
    @Setter
    public static class StartSessionRequest {
        private Long processId;
        private String actorType;
        private Long actorMemberId;
        private String actorName;
        private String actorPhone;
    }

    @Getter
    @Setter
    public static class SubmitAnswerRequest {
        private String unitKey;
        private String selectedOptionKey;
        private String selectedOptionLabel;
        private Map<String, Object> answerValues = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class SessionResponse {
        private String sessionKey;
        private Long processId;
        private String processName;
        private ProcessExecutionStatus status;
        private String currentUnitKey;
        private CurrentUnitResponse currentUnit;
        private BigDecimal calculatedPriceAmount;
        private String priceResultJson;
        private List<AnswerHistoryResponse> answers = new ArrayList<>();
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
    }

    @Getter
    @Setter
    public static class CurrentUnitResponse {
        private String unitKey;
        private String unitTitle;
        private String stepTitle;
        private String questionText;
        private ProcessAnswerType answerType;
        private boolean requiredYn;
        private String helperText;
        private List<OptionResponse> options = new ArrayList<>();
        private List<FieldResponse> fields = new ArrayList<>();
        private List<InfoImageResponse> infoImages = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class OptionResponse {
        private String optionKey;
        private String label;
        private String valueText;
        private List<InfoImageResponse> infoImages = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class FieldResponse {
        private String fieldKey;
        private String label;
        private ProcessInputValueType inputValueType;
        private String placeholder;
        private String unitText;
        private boolean requiredYn;
    }

    @Getter
    @Setter
    public static class AnswerHistoryResponse {
        private String unitKey;
        private String questionText;
        private ProcessAnswerType answerType;
        private String selectedOptionKey;
        private String selectedOptionLabel;
        private String displayAnswerText;
        private String answerValueJson;
        private List<FileResponse> files = new ArrayList<>();
        private LocalDateTime createdAt;
    }

    @Getter
    @Setter
    public static class FileResponse {
        private String originalFilename;
        private String fileUrl;
        private long fileSize;
    }
    
    @Getter
    @Setter
    public static class InfoImageResponse {
        private String imageKey;
        private String originalFilename;
        private String fileUrl;
        private int sortOrder;
    }
}