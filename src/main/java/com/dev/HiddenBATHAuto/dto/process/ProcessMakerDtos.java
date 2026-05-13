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
    }

    @Getter
    @Setter
    public static class AnswerOptionDto {
        private String optionKey;
        private String label;
        private String valueText;
        private int sortOrder;
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
}