package com.dev.HiddenBATHAuto.service.process;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.AnswerFieldDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.AnswerOptionDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.BranchDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.CreateProcessRequest;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.PriceRuleDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.ProcessDetailRequest;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.ProcessDetailResponse;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.ProcessSummaryResponse;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.QuestionDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.StepDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.UnitDto;
import com.dev.HiddenBATHAuto.enums.process.ProcessAnswerType;
import com.dev.HiddenBATHAuto.enums.process.ProcessBranchTargetMode;
import com.dev.HiddenBATHAuto.enums.process.ProcessInputValueType;
import com.dev.HiddenBATHAuto.enums.process.ProcessStatus;
import com.dev.HiddenBATHAuto.model.calculator.ProcessUnitPriceRule;
import com.dev.HiddenBATHAuto.model.process.ProcessAnswerField;
import com.dev.HiddenBATHAuto.model.process.ProcessAnswerOption;
import com.dev.HiddenBATHAuto.model.process.ProcessDefinition;
import com.dev.HiddenBATHAuto.model.process.ProcessQuestion;
import com.dev.HiddenBATHAuto.model.process.ProcessStep;
import com.dev.HiddenBATHAuto.model.process.ProcessUnit;
import com.dev.HiddenBATHAuto.model.process.ProcessUnitBranch;
import com.dev.HiddenBATHAuto.repository.process.ProcessDefinitionRepository;
import com.dev.HiddenBATHAuto.service.calculator.ProcessPriceRuleValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProcessMakerService {

    private final ProcessDefinitionRepository processDefinitionRepository;
    private final ProcessDefinitionGraphValidator processDefinitionGraphValidator;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final ProcessPriceRuleValidator processPriceRuleValidator;

    @Transactional(readOnly = true)
    public List<ProcessSummaryResponse> getProcessList() {
        return processDefinitionRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public ProcessDetailResponse createProcess(CreateProcessRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("프로세스 이름을 입력해주세요.");
        }

        ProcessDefinition process = new ProcessDefinition();
        process.setProcessKey(generateProcessKey());
        process.setName(request.getName().trim());
        process.setDescription(trimToNull(request.getDescription()));
        process.setStatus(ProcessStatus.DRAFT);
        process.setUseYn(true);

        processDefinitionRepository.save(process);

        return toDetail(process);
    }

    @Transactional(readOnly = true)
    public ProcessDetailResponse getProcess(Long processId) {
        ProcessDefinition process = processDefinitionRepository.findWithAllById(processId)
                .orElseThrow(() -> new IllegalArgumentException("프로세스를 찾을 수 없습니다."));

        return toDetail(process);
    }

    @Transactional
    public ProcessDetailResponse updateProcess(Long processId, ProcessDetailRequest request) {
        ProcessDefinition process = processDefinitionRepository.findWithAllById(processId)
                .orElseThrow(() -> new IllegalArgumentException("프로세스를 찾을 수 없습니다."));

        validateProcessRequest(request);
        processPriceRuleValidator.validate(request);

        ProcessDefinitionValidationResult validationResult = processDefinitionGraphValidator.validate(request);
        if (validationResult.hasErrors()) {
            throw new IllegalArgumentException(String.join("\n", validationResult.getErrors()));
        }

        process.setName(request.getName().trim());
        process.setDescription(trimToNull(request.getDescription()));
        process.setStatus(request.getStatus() == null ? ProcessStatus.DRAFT : request.getStatus());
        process.setUseYn(request.isUseYn());

        /*
         * 중요:
         * 기존 STEP/UNIT/QUESTION/BRANCH를 지운 뒤 같은 stepKey/unitKey로 다시 insert하는 구조입니다.
         * Hibernate가 delete보다 insert를 먼저 보내면 uk_process_step_key 중복이 발생할 수 있습니다.
         * 따라서 clear 후 flush로 기존 row delete를 먼저 DB에 반영합니다.
         */
        process.clearSteps();
        entityManager.flush();

        List<StepDto> requestedSteps = request.getSteps() == null ? List.of() : request.getSteps();

        for (int stepIndex = 0; stepIndex < requestedSteps.size(); stepIndex++) {
            StepDto stepDto = requestedSteps.get(stepIndex);

            ProcessStep step = new ProcessStep();
            step.setStepKey(required(stepDto.getStepKey(), "스탭 key가 없습니다."));
            step.setTitle(defaultText(stepDto.getTitle(), "STEP " + (stepIndex + 1)));
            step.setDescription(trimToNull(stepDto.getDescription()));
            step.setSortOrder(stepIndex);

            if (stepDto.getUnits() != null) {
                for (int unitIndex = 0; unitIndex < stepDto.getUnits().size(); unitIndex++) {
                    UnitDto unitDto = stepDto.getUnits().get(unitIndex);

                    ProcessUnit unit = new ProcessUnit();
                    unit.setUnitKey(required(unitDto.getUnitKey(), "유닛 key가 없습니다."));
                    unit.setTitle(defaultText(unitDto.getTitle(), "UNIT " + (unitIndex + 1)));
                    unit.setDescription(trimToNull(unitDto.getDescription()));
                    unit.setSortOrder(unitIndex);
                    unit.setUseYn(unitDto.isUseYn());

                    unit.setQuestion(toQuestionEntity(unitDto.getQuestion()));

                    if (unitDto.getBranches() != null) {
                        for (int branchIndex = 0; branchIndex < unitDto.getBranches().size(); branchIndex++) {
                            BranchDto branchDto = unitDto.getBranches().get(branchIndex);
                            ProcessUnitBranch branch = toBranchEntity(branchDto, branchIndex);
                            unit.addBranch(branch);
                        }
                    }

                    if (unitDto.getPriceRules() != null) {
                        for (int priceRuleIndex = 0; priceRuleIndex < unitDto.getPriceRules().size(); priceRuleIndex++) {
                            PriceRuleDto priceRuleDto = unitDto.getPriceRules().get(priceRuleIndex);
                            ProcessUnitPriceRule priceRule = toPriceRuleEntity(priceRuleDto, priceRuleIndex);
                            unit.addPriceRule(priceRule);
                        }
                    }

                    step.addUnit(unit);
                }
            }

            process.addStep(step);
        }

        ProcessDefinition saved = processDefinitionRepository.save(process);

        ProcessDetailResponse response = toDetail(saved);
        response.setValidationWarnings(validationResult.getWarnings());

        return response;
    }

    private ProcessQuestion toQuestionEntity(QuestionDto dto) {
        if (dto == null) {
            dto = new QuestionDto();
        }

        ProcessQuestion question = new ProcessQuestion();
        question.setQuestionText(defaultText(dto.getQuestionText(), "질문을 입력해주세요."));
        ProcessAnswerType answerType = dto.getAnswerType() == null
                ? ProcessAnswerType.SINGLE_SELECT
                : dto.getAnswerType();

        if (answerType == ProcessAnswerType.MULTI_INPUT) {
            answerType = ProcessAnswerType.NUMBER_INPUT;
        }

        question.setAnswerType(answerType);
        question.setRequiredYn(dto.isRequiredYn());
        question.setHelperText(trimToNull(dto.getHelperText()));

        if (dto.getOptions() != null) {
            for (int i = 0; i < dto.getOptions().size(); i++) {
                AnswerOptionDto optionDto = dto.getOptions().get(i);

                ProcessAnswerOption option = new ProcessAnswerOption();
                option.setOptionKey(required(optionDto.getOptionKey(), "답변 optionKey가 없습니다."));
                option.setLabel(defaultText(optionDto.getLabel(), "옵션 " + (i + 1)));
                option.setValueText(trimToNull(optionDto.getValueText()));
                option.setSortOrder(i);

                question.addOption(option);
            }
        }

        if (dto.getFields() != null) {
            for (int i = 0; i < dto.getFields().size(); i++) {
                AnswerFieldDto fieldDto = dto.getFields().get(i);

                ProcessAnswerField field = new ProcessAnswerField();
                field.setFieldKey(required(fieldDto.getFieldKey(), "답변 fieldKey가 없습니다."));
                field.setLabel(defaultText(fieldDto.getLabel(), "입력값 " + (i + 1)));
                field.setInputValueType(fieldDto.getInputValueType());
                field.setPlaceholder(trimToNull(fieldDto.getPlaceholder()));
                field.setUnitText(trimToNull(fieldDto.getUnitText()));
                field.setRequiredYn(fieldDto.isRequiredYn());
                field.setSortOrder(i);

                question.addField(field);
            }
        }

        return question;
    }

    private ProcessUnitBranch toBranchEntity(BranchDto dto, int index) {
        ProcessUnitBranch branch = new ProcessUnitBranch();
        branch.setBranchKey(required(dto.getBranchKey(), "분기 branchKey가 없습니다."));
        branch.setLabel(defaultText(dto.getLabel(), "분기 " + (index + 1)));
        branch.setBranchType(dto.getBranchType());
        branch.setAnswerOptionKey(trimToNull(dto.getAnswerOptionKey()));
        branch.setConditionJson(trimToNull(dto.getConditionJson()));
        branch.setTargetMode(dto.getTargetMode() == null ? ProcessBranchTargetMode.AUTO_NEXT : dto.getTargetMode());
        branch.setTargetUnitKey(trimToNull(dto.getTargetUnitKey()));
        branch.setPriority(index);
        branch.setUseYn(dto.isUseYn());
        return branch;
    }
    
    private ProcessUnitPriceRule toPriceRuleEntity(PriceRuleDto dto, int index) {
        ProcessUnitPriceRule priceRule = new ProcessUnitPriceRule();

        priceRule.setRuleKey(required(dto.getRuleKey(), "가격 규칙 ruleKey가 없습니다."));
        priceRule.setRuleName(defaultText(dto.getRuleName(), "가격 규칙 " + (index + 1)));

        if (dto.getRuleType() == null) {
            throw new IllegalArgumentException("가격 규칙 타입이 없습니다: " + priceRule.getRuleName());
        }

        priceRule.setRuleType(dto.getRuleType());
        priceRule.setEnabledYn(dto.isEnabledYn());
        priceRule.setSortOrder(index);
        priceRule.setRuleJson(required(dto.getRuleJson(), "가격 규칙 JSON이 없습니다: " + priceRule.getRuleName()));

        return priceRule;
    }
    
    private void validateProcessRequest(ProcessDetailRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("프로세스 이름을 입력해주세요.");
        }

        Set<String> stepKeys = new HashSet<>();
        Set<String> unitKeys = new HashSet<>();

        if (request.getSteps() == null) {
            return;
        }

        for (StepDto step : request.getSteps()) {
            if (step.getStepKey() == null || step.getStepKey().isBlank()) {
                throw new IllegalArgumentException("스탭 key가 비어있습니다.");
            }
            if (!stepKeys.add(step.getStepKey())) {
                throw new IllegalArgumentException("중복된 스탭 key가 있습니다: " + step.getStepKey());
            }

            if (step.getUnits() == null) {
                continue;
            }

            for (UnitDto unit : step.getUnits()) {
                if (unit.getUnitKey() == null || unit.getUnitKey().isBlank()) {
                    throw new IllegalArgumentException("유닛 key가 비어있습니다.");
                }
                if (!unitKeys.add(unit.getUnitKey())) {
                    throw new IllegalArgumentException("중복된 유닛 key가 있습니다: " + unit.getUnitKey());
                }
            }
        }

        for (StepDto step : request.getSteps()) {
            if (step.getUnits() == null) {
                continue;
            }

            for (UnitDto unit : step.getUnits()) {
            	
            	 validateUnitAnswerAndBranchRule(unit, unitKeys);
            	
                if (unit.getBranches() == null) {
                    continue;
                }

                Set<String> optionKeys = new HashSet<>();
                if (unit.getQuestion() != null && unit.getQuestion().getOptions() != null) {
                    for (AnswerOptionDto option : unit.getQuestion().getOptions()) {
                        optionKeys.add(option.getOptionKey());
                    }
                }

                for (BranchDto branch : unit.getBranches()) {
                    if (branch.getTargetMode() != null
                            && branch.getTargetMode() != ProcessBranchTargetMode.AUTO_NEXT
                            && (branch.getTargetUnitKey() == null || branch.getTargetUnitKey().isBlank())) {
                        throw new IllegalArgumentException("JUMP/DEFER 분기는 대상 UNIT이 필요합니다.");
                    }

                    if (branch.getTargetUnitKey() != null
                            && !branch.getTargetUnitKey().isBlank()
                            && !unitKeys.contains(branch.getTargetUnitKey())) {
                        throw new IllegalArgumentException("분기 대상 UNIT을 찾을 수 없습니다: " + branch.getTargetUnitKey());
                    }

                    if (branch.getAnswerOptionKey() != null
                            && !branch.getAnswerOptionKey().isBlank()
                            && !optionKeys.contains(branch.getAnswerOptionKey())) {
                        throw new IllegalArgumentException("선택형 분기의 답변 옵션을 찾을 수 없습니다: " + branch.getAnswerOptionKey());
                    }
                }
            }
        }
    }

    private ProcessSummaryResponse toSummary(ProcessDefinition process) {
        ProcessSummaryResponse dto = new ProcessSummaryResponse();
        dto.setId(process.getId());
        dto.setProcessKey(process.getProcessKey());
        dto.setName(process.getName());
        dto.setDescription(process.getDescription());
        dto.setStatus(process.getStatus());
        dto.setUseYn(process.isUseYn());
        dto.setCreatedAt(process.getCreatedAt());
        dto.setUpdatedAt(process.getUpdatedAt());
        return dto;
    }

    private ProcessDetailResponse toDetail(ProcessDefinition process) {
        ProcessDetailResponse dto = new ProcessDetailResponse();
        dto.setId(process.getId());
        dto.setProcessKey(process.getProcessKey());
        dto.setName(process.getName());
        dto.setDescription(process.getDescription());
        dto.setStatus(process.getStatus());
        dto.setUseYn(process.isUseYn());
        dto.setCreatedAt(process.getCreatedAt());
        dto.setUpdatedAt(process.getUpdatedAt());

        if (process.getSteps() != null) {
            dto.setSteps(process.getSteps().stream().map(this::toStepDto).toList());
        }

        return dto;
    }

    private StepDto toStepDto(ProcessStep step) {
        StepDto dto = new StepDto();
        dto.setStepKey(step.getStepKey());
        dto.setTitle(step.getTitle());
        dto.setDescription(step.getDescription());
        dto.setSortOrder(step.getSortOrder());

        if (step.getUnits() != null) {
            dto.setUnits(step.getUnits().stream().map(this::toUnitDto).toList());
        }

        return dto;
    }

    private UnitDto toUnitDto(ProcessUnit unit) {
        UnitDto dto = new UnitDto();
        dto.setUnitKey(unit.getUnitKey());
        dto.setTitle(unit.getTitle());
        dto.setDescription(unit.getDescription());
        dto.setSortOrder(unit.getSortOrder());
        dto.setUseYn(unit.isUseYn());

        if (unit.getQuestion() != null) {
            dto.setQuestion(toQuestionDto(unit.getQuestion()));
        }

        if (unit.getBranches() != null) {
            dto.setBranches(unit.getBranches().stream().map(this::toBranchDto).toList());
        }

        if (unit.getPriceRules() != null) {
            dto.setPriceRules(unit.getPriceRules().stream().map(this::toPriceRuleDto).toList());
        }

        return dto;
    }

    private PriceRuleDto toPriceRuleDto(ProcessUnitPriceRule priceRule) {
        PriceRuleDto dto = new PriceRuleDto();

        dto.setRuleKey(priceRule.getRuleKey());
        dto.setRuleName(priceRule.getRuleName());
        dto.setRuleType(priceRule.getRuleType());
        dto.setEnabledYn(priceRule.isEnabledYn());
        dto.setSortOrder(priceRule.getSortOrder());
        dto.setRuleJson(priceRule.getRuleJson());

        return dto;
    }
    
    private QuestionDto toQuestionDto(ProcessQuestion question) {
        QuestionDto dto = new QuestionDto();
        dto.setQuestionText(question.getQuestionText());
        dto.setAnswerType(question.getAnswerType());
        dto.setRequiredYn(question.isRequiredYn());
        dto.setHelperText(question.getHelperText());

        if (question.getOptions() != null) {
            dto.setOptions(question.getOptions().stream().map(this::toOptionDto).toList());
        }

        if (question.getFields() != null) {
            dto.setFields(question.getFields().stream().map(this::toFieldDto).toList());
        }

        return dto;
    }

    private AnswerOptionDto toOptionDto(ProcessAnswerOption option) {
        AnswerOptionDto dto = new AnswerOptionDto();
        dto.setOptionKey(option.getOptionKey());
        dto.setLabel(option.getLabel());
        dto.setValueText(option.getValueText());
        dto.setSortOrder(option.getSortOrder());
        return dto;
    }

    private AnswerFieldDto toFieldDto(ProcessAnswerField field) {
        AnswerFieldDto dto = new AnswerFieldDto();
        dto.setFieldKey(field.getFieldKey());
        dto.setLabel(field.getLabel());
        dto.setInputValueType(field.getInputValueType());
        dto.setPlaceholder(field.getPlaceholder());
        dto.setUnitText(field.getUnitText());
        dto.setRequiredYn(field.isRequiredYn());
        dto.setSortOrder(field.getSortOrder());
        return dto;
    }

    private BranchDto toBranchDto(ProcessUnitBranch branch) {
        BranchDto dto = new BranchDto();
        dto.setBranchKey(branch.getBranchKey());
        dto.setLabel(branch.getLabel());
        dto.setBranchType(branch.getBranchType());
        dto.setAnswerOptionKey(branch.getAnswerOptionKey());
        dto.setConditionJson(branch.getConditionJson());
        dto.setTargetMode(branch.getTargetMode());
        dto.setTargetUnitKey(branch.getTargetUnitKey());
        dto.setPriority(branch.getPriority());
        dto.setUseYn(branch.isUseYn());
        return dto;
    }

    private String generateProcessKey() {
        String key;
        do {
            key = "process_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        } while (processDefinitionRepository.existsByProcessKey(key));

        return key;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    
    private void validateUnitAnswerAndBranchRule(UnitDto unit, Set<String> unitKeys) {
        if (unit == null) {
            return;
        }

        String unitTitle = defaultText(unit.getTitle(), unit.getUnitKey());

        QuestionDto question = unit.getQuestion();
        if (question == null) {
            throw new IllegalArgumentException(unitTitle + ": 질문 정보가 없습니다.");
        }

        ProcessAnswerType answerType = question.getAnswerType() == null
                ? ProcessAnswerType.SINGLE_SELECT
                : question.getAnswerType();

        if (answerType == ProcessAnswerType.MULTI_INPUT) {
            answerType = ProcessAnswerType.NUMBER_INPUT;
        }

        List<BranchDto> branches = unit.getBranches() == null ? List.of() : unit.getBranches();

        if (answerType == ProcessAnswerType.SINGLE_SELECT) {
            validateSingleSelectBranches(unitTitle, question, branches, unitKeys);
            return;
        }

        if (answerType == ProcessAnswerType.TEXT_INPUT) {
            if (!branches.isEmpty()) {
                throw new IllegalArgumentException(unitTitle + ": 텍스트 입력은 분기를 추가할 수 없습니다.");
            }
            return;
        }

        if (answerType == ProcessAnswerType.NUMBER_INPUT) {
            validateNumberBranches(unitTitle, question, branches, unitKeys);
            return;
        }

        if (answerType == ProcessAnswerType.FILE_UPLOAD) {
            validateCommonBranchTargets(unitTitle, branches, unitKeys);
        }
    }

    private void validateSingleSelectBranches(
            String unitTitle,
            QuestionDto question,
            List<BranchDto> branches,
            Set<String> unitKeys
    ) {
        Set<String> optionKeys = new HashSet<>();

        if (question.getOptions() != null) {
            for (AnswerOptionDto option : question.getOptions()) {
                if (option.getOptionKey() != null && !option.getOptionKey().isBlank()) {
                    optionKeys.add(option.getOptionKey());
                }
            }
        }

        Set<String> usedOptionKeys = new HashSet<>();

        for (BranchDto branch : branches) {
            String branchType = enumName(branch.getBranchType());

            if ("DEFAULT".equals(branchType)) {
                continue;
            }

            if (!"CHOICE".equals(branchType)) {
                throw new IllegalArgumentException(unitTitle + ": 여러 개 중 하나 선택 답변은 선택 답변 분기만 사용할 수 있습니다.");
            }

            String answerOptionKey = trimToNull(branch.getAnswerOptionKey());
            if (answerOptionKey == null) {
                throw new IllegalArgumentException(unitTitle + ": 선택 답변 분기에 연결할 답변이 없습니다.");
            }

            if (!optionKeys.contains(answerOptionKey)) {
                throw new IllegalArgumentException(unitTitle + ": 존재하지 않는 답변에 분기가 연결되어 있습니다: " + answerOptionKey);
            }

            if (!usedOptionKeys.add(answerOptionKey)) {
                throw new IllegalArgumentException(unitTitle + ": 동일 답변에 분기가 중복 설정되어 있습니다.");
            }
        }

        validateCommonBranchTargets(unitTitle, branches, unitKeys);
    }

    private void validateNumberBranches(
            String unitTitle,
            QuestionDto question,
            List<BranchDto> branches,
            Set<String> unitKeys
    ) {
        Set<String> numberFieldKeys = new HashSet<>();

        if (question.getFields() != null) {
            for (AnswerFieldDto field : question.getFields()) {
                if (field.getInputValueType() == ProcessInputValueType.NUMBER
                        && field.getFieldKey() != null
                        && !field.getFieldKey().isBlank()) {
                    numberFieldKeys.add(field.getFieldKey());
                }
            }
        }

        if (numberFieldKeys.isEmpty()) {
            throw new IllegalArgumentException(unitTitle + ": 숫자 입력은 숫자 필드가 최소 1개 필요합니다.");
        }

        Map<String, List<NumericInterval>> intervalMap = new LinkedHashMap<>();

        for (BranchDto branch : branches) {
            String branchType = enumName(branch.getBranchType());

            if ("DEFAULT".equals(branchType)) {
                continue;
            }

            if (!"CONDITION".equals(branchType)) {
                throw new IllegalArgumentException(unitTitle + ": 숫자 입력은 숫자 조건식 분기만 사용할 수 있습니다.");
            }

            Map<String, NumericInterval> branchIntervals = parseNumericIntervals(
                    unitTitle,
                    defaultText(branch.getLabel(), branch.getBranchKey()),
                    branch.getConditionJson()
            );

            if (branchIntervals.isEmpty()) {
                throw new IllegalArgumentException(unitTitle + ": 숫자 분기는 조건을 최소 1개 이상 가져야 합니다.");
            }

            for (NumericInterval interval : branchIntervals.values()) {
                if (!numberFieldKeys.contains(interval.fieldKey)) {
                    throw new IllegalArgumentException(unitTitle + ": 숫자 조건에 존재하지 않는 필드가 사용되었습니다: " + interval.fieldKey);
                }

                if (interval.lower == null && interval.upper == null) {
                    throw new IllegalArgumentException(unitTitle + ": 숫자 조건은 시작값 또는 종료값 중 하나 이상 필요합니다.");
                }

                if (interval.lower != null && interval.upper != null && interval.lower.compareTo(interval.upper) > 0) {
                    throw new IllegalArgumentException(unitTitle + ": 숫자 조건의 시작값이 종료값보다 클 수 없습니다.");
                }

                intervalMap.computeIfAbsent(interval.fieldKey, key -> new ArrayList<>()).add(interval);
            }
        }

        validateIntervalOverlapAndGap(unitTitle, intervalMap);
        validateCommonBranchTargets(unitTitle, branches, unitKeys);
    }

    private Map<String, NumericInterval> parseNumericIntervals(
            String unitTitle,
            String branchLabel,
            String conditionJson
    ) {
        Map<String, NumericInterval> result = new LinkedHashMap<>();

        if (conditionJson == null || conditionJson.isBlank()) {
            return result;
        }

        try {
            Map<String, Object> root = objectMapper.readValue(
                    conditionJson,
                    new TypeReference<Map<String, Object>>() {
                    }
            );

            Object mode = root.get("mode");
            if (mode != null && !"ALL".equals(String.valueOf(mode))) {
                throw new IllegalArgumentException(unitTitle + ": 숫자 조건은 AND 조건만 사용할 수 있습니다.");
            }

            Object conditionsObj = root.get("conditions");
            if (!(conditionsObj instanceof List<?> conditions)) {
                return result;
            }

            for (Object item : conditions) {
                if (!(item instanceof Map<?, ?> condition)) {
                    continue;
                }

                String fieldKey = stringValue(condition.get("fieldKey"));
                String operator = stringValue(condition.get("operator"));
                BigDecimal value = decimalValue(condition.get("value"));

                if (fieldKey == null || operator == null || value == null) {
                    continue;
                }

                NumericInterval interval = result.computeIfAbsent(fieldKey, key -> {
                    NumericInterval created = new NumericInterval();
                    created.fieldKey = key;
                    created.branchLabel = branchLabel;
                    return created;
                });

                applyOperator(interval, operator, value);
            }

            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(unitTitle + ": 숫자 조건 JSON 형식이 올바르지 않습니다.");
        }
    }

    private void applyOperator(NumericInterval interval, String operator, BigDecimal value) {
        if ("EQ".equals(operator)) {
            interval.lower = value;
            interval.lowerInclusive = true;
            interval.upper = value;
            interval.upperInclusive = true;
            return;
        }

        if ("GTE".equals(operator) || "GT".equals(operator)) {
            boolean inclusive = "GTE".equals(operator);

            if (interval.lower == null
                    || value.compareTo(interval.lower) > 0
                    || value.compareTo(interval.lower) == 0 && !inclusive) {
                interval.lower = value;
                interval.lowerInclusive = inclusive;
            }

            return;
        }

        if ("LTE".equals(operator) || "LT".equals(operator)) {
            boolean inclusive = "LTE".equals(operator);

            if (interval.upper == null
                    || value.compareTo(interval.upper) < 0
                    || value.compareTo(interval.upper) == 0 && !inclusive) {
                interval.upper = value;
                interval.upperInclusive = inclusive;
            }
        }
    }

    private void validateIntervalOverlapAndGap(
            String unitTitle,
            Map<String, List<NumericInterval>> intervalMap
    ) {
        for (Map.Entry<String, List<NumericInterval>> entry : intervalMap.entrySet()) {
            List<NumericInterval> intervals = entry.getValue();

            if (intervals.size() <= 1) {
                continue;
            }

            intervals.sort(Comparator.comparing(
                    interval -> interval.lower,
                    Comparator.nullsFirst(BigDecimal::compareTo)
            ));

            for (int i = 0; i < intervals.size() - 1; i++) {
                NumericInterval current = intervals.get(i);
                NumericInterval next = intervals.get(i + 1);

                if (current.upper == null) {
                    throw new IllegalArgumentException(
                            unitTitle + ": 숫자 조건 범위가 겹칩니다. [" + current.branchLabel + "] 이후 조건은 도달할 수 없습니다."
                    );
                }

                if (next.lower == null) {
                    throw new IllegalArgumentException(
                            unitTitle + ": 숫자 조건 범위가 겹칩니다. [" + next.branchLabel + "] 조건을 확인해주세요."
                    );
                }

                int compare = current.upper.compareTo(next.lower);

                if (compare > 0) {
                    throw new IllegalArgumentException(
                            unitTitle + ": 숫자 조건 범위가 겹칩니다. [" + current.branchLabel + "] / [" + next.branchLabel + "]"
                    );
                }

                if (compare == 0 && current.upperInclusive && next.lowerInclusive) {
                    throw new IllegalArgumentException(
                            unitTitle + ": 숫자 조건 경계값 " + current.upper + "이 중복 포함됩니다. ["
                                    + current.branchLabel + "] / [" + next.branchLabel + "]"
                    );
                }

                if (compare < 0) {
                    throw new IllegalArgumentException(
                            unitTitle + ": 숫자 조건 범위 사이에 빈 구간이 있습니다. "
                                    + current.upper + " ~ " + next.lower + " 사이 입력값은 갈 수 있는 분기가 없습니다."
                    );
                }
            }
        }
    }

    private void validateCommonBranchTargets(
            String unitTitle,
            List<BranchDto> branches,
            Set<String> unitKeys
    ) {
        for (BranchDto branch : branches) {
            ProcessBranchTargetMode targetMode = branch.getTargetMode() == null
                    ? ProcessBranchTargetMode.AUTO_NEXT
                    : branch.getTargetMode();

            if (targetMode != ProcessBranchTargetMode.AUTO_NEXT) {
                String targetUnitKey = trimToNull(branch.getTargetUnitKey());

                if (targetUnitKey == null) {
                    throw new IllegalArgumentException(unitTitle + ": JUMP/DEFER 분기는 대상 UNIT이 필요합니다.");
                }

                if (!unitKeys.contains(targetUnitKey)) {
                    throw new IllegalArgumentException(unitTitle + ": 분기 대상 UNIT을 찾을 수 없습니다: " + targetUnitKey);
                }
            }
        }
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static class NumericInterval {
        private String fieldKey;
        private String branchLabel;
        private BigDecimal lower;
        private boolean lowerInclusive = true;
        private BigDecimal upper;
        private boolean upperInclusive = true;
    }
}