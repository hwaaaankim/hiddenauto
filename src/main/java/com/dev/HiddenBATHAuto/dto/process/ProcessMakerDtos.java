package com.dev.HiddenBATHAuto.dto.process;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.dev.HiddenBATHAuto.enums.calculator.ProcessPriceRuleType;
import com.dev.HiddenBATHAuto.enums.process.ProcessAnswerType;
import com.dev.HiddenBATHAuto.enums.process.ProcessBranchTargetMode;
import com.dev.HiddenBATHAuto.enums.process.ProcessBranchType;
import com.dev.HiddenBATHAuto.enums.process.ProcessInputValueType;
import com.dev.HiddenBATHAuto.enums.process.ProcessStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class ProcessMakerDtos {
	
	@Getter
	@Setter
	@NoArgsConstructor
	public static class PriceRuleDto {

	    private String ruleKey;

	    private String ruleName;

	    private ProcessPriceRuleType ruleType;

	    private boolean enabledYn = true;

	    private int sortOrder;

	    /**
	     * 타입별 가격 규칙 JSON.
	     */
	    private String ruleJson;
	}
	
    @Getter
    @Setter
    public static class CreateProcessRequest {
        private String name;
        private String description;
    }

    @Getter
    @Setter
    public static class ProcessSummaryResponse {
        private Long id;
        private String processKey;
        private String name;
        private String description;
        private ProcessStatus status;
        private boolean useYn;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    public static class ProcessDetailRequest {
        private Long id;
        private String processKey;
        private String name;
        private String description;
        private ProcessStatus status = ProcessStatus.DRAFT;
        private boolean useYn = true;
        private List<StepDto> steps = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class ProcessDetailResponse extends ProcessDetailRequest {
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private List<String> validationWarnings = new ArrayList<>();
    }
    
    @Getter
    @Setter
    public static class StepDto {
        private String stepKey;
        private String title;
        private String description;
        private int sortOrder;
        private List<UnitDto> units = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class UnitDto {
        private String unitKey;
        private String title;
        private String description;
        private int sortOrder;
        private boolean useYn = true;
        private QuestionDto question = new QuestionDto();
        private List<BranchDto> branches = new ArrayList<>();

        /**
         * 이 UNIT에 연결된 가격 계산 규칙 목록.
         * 답변 형태가 SINGLE_SELECT 또는 NUMBER_INPUT일 때만 사용합니다.
         */
        private List<PriceRuleDto> priceRules = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class QuestionDto {
        private String questionText;
        private ProcessAnswerType answerType = ProcessAnswerType.SINGLE_SELECT;
        private boolean requiredYn = true;
        private String helperText;
        private List<AnswerOptionDto> options = new ArrayList<>();
        private List<AnswerFieldDto> fields = new ArrayList<>();
        private List<InfoImageDto> infoImages = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class AnswerOptionDto {
        private String optionKey;
        private String label;
        private String valueText;
        private int sortOrder;
        private List<InfoImageDto> infoImages = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class AnswerFieldDto {
        private String fieldKey;
        private String label;
        private ProcessInputValueType inputValueType = ProcessInputValueType.TEXT;
        private String placeholder;
        private String unitText;
        private boolean requiredYn;
        private int sortOrder;
    }

    @Getter
    @Setter
    public static class BranchDto {
        private String branchKey;
        private String label;
        private ProcessBranchType branchType = ProcessBranchType.DEFAULT;
        private String answerOptionKey;
        private String conditionJson;
        private ProcessBranchTargetMode targetMode = ProcessBranchTargetMode.AUTO_NEXT;
        private String targetUnitKey;
        private int priority;
        private boolean useYn = true;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    public static class InfoImageDto {

        private String imageKey;

        private String originalFilename;

        private String storedFilename;

        private String contentType;

        private long fileSize;

        /**
         * 관리자 저장 payload에서만 사용합니다.
         * 테스트 화면 응답에는 노출하지 않는 것이 좋습니다.
         */
        private String filePath;

        private String fileUrl;

        private int sortOrder;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DeleteInfoImageRequest {
        private String imageKey;
        private String filePath;
    }
}