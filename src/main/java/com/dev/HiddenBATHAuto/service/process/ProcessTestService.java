package com.dev.HiddenBATHAuto.service.process;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.AnswerHistoryResponse;
import com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.CurrentUnitResponse;
import com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.FieldResponse;
import com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.FileResponse;
import com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.OptionResponse;
import com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.SessionResponse;
import com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.StartSessionRequest;
import com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.SubmitAnswerRequest;
import com.dev.HiddenBATHAuto.dto.process.ProcessTestDtos.TestProcessSummaryResponse;
import com.dev.HiddenBATHAuto.enums.process.ProcessAnswerType;
import com.dev.HiddenBATHAuto.enums.process.ProcessBranchTargetMode;
import com.dev.HiddenBATHAuto.enums.process.ProcessBranchType;
import com.dev.HiddenBATHAuto.enums.process.ProcessExecutionStatus;
import com.dev.HiddenBATHAuto.enums.process.ProcessStatus;
import com.dev.HiddenBATHAuto.model.process.ProcessAnswerField;
import com.dev.HiddenBATHAuto.model.process.ProcessAnswerOption;
import com.dev.HiddenBATHAuto.model.process.ProcessDefinition;
import com.dev.HiddenBATHAuto.model.process.ProcessExecutionAnswer;
import com.dev.HiddenBATHAuto.model.process.ProcessExecutionFile;
import com.dev.HiddenBATHAuto.model.process.ProcessExecutionSession;
import com.dev.HiddenBATHAuto.model.process.ProcessQuestion;
import com.dev.HiddenBATHAuto.model.process.ProcessStep;
import com.dev.HiddenBATHAuto.model.process.ProcessUnit;
import com.dev.HiddenBATHAuto.model.process.ProcessUnitBranch;
import com.dev.HiddenBATHAuto.repository.process.ProcessDefinitionRepository;
import com.dev.HiddenBATHAuto.repository.process.ProcessExecutionAnswerRepository;
import com.dev.HiddenBATHAuto.repository.process.ProcessExecutionSessionRepository;
import com.dev.HiddenBATHAuto.service.process.ProcessPriceCalculator.PriceCalculationResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProcessTestService {

	private final ProcessDefinitionRepository processDefinitionRepository;
	private final ProcessExecutionSessionRepository sessionRepository;
	private final ProcessExecutionAnswerRepository answerRepository;
	private final ProcessPriceCalculator processPriceCalculator;
	private final ObjectMapper objectMapper;

	@Value("${spring.upload.path:uploads}")
	private String uploadPath;

	@Transactional(readOnly = true)
	public List<TestProcessSummaryResponse> getProcessListForTest() {
		return processDefinitionRepository.findAllByOrderByCreatedAtDesc().stream().filter(ProcessDefinition::isUseYn)
				.map(this::toProcessSummary).toList();
	}

	@Transactional
	public SessionResponse startSession(StartSessionRequest request) {
		if (request.getProcessId() == null) {
			throw new IllegalArgumentException("프로세스를 선택해주세요.");
		}

		ProcessDefinition process = processDefinitionRepository.findWithAllById(request.getProcessId())
				.orElseThrow(() -> new IllegalArgumentException("프로세스를 찾을 수 없습니다."));

		ProcessUnit firstUnit = findFirstUnit(process);
		if (firstUnit == null) {
			throw new IllegalArgumentException("프로세스에 실행 가능한 UNIT이 없습니다.");
		}

		ProcessExecutionSession session = new ProcessExecutionSession();
		session.setSessionKey(generateSessionKey());
		session.setProcess(process);
		session.setStatus(ProcessExecutionStatus.IN_PROGRESS);
		session.setCurrentUnitKey(firstUnit.getUnitKey());
		session.setActorType(trimToNull(request.getActorType()));
		session.setActorMemberId(request.getActorMemberId());
		session.setActorName(trimToNull(request.getActorName()));
		session.setActorPhone(trimToNull(request.getActorPhone()));
		session.setDeferredUnitKeysJson("[]");

		ProcessExecutionSession saved = sessionRepository.save(session);

		return toSessionResponse(saved);
	}

	@Transactional(readOnly = true)
	public SessionResponse getSession(String sessionKey) {
		ProcessExecutionSession session = getSessionWithAll(sessionKey);
		return toSessionResponse(session);
	}

	@Transactional
	public SessionResponse submitAnswer(String sessionKey, SubmitAnswerRequest request, List<MultipartFile> files) {
		ProcessExecutionSession session = getSessionWithAll(sessionKey);

		if (session.getStatus() != ProcessExecutionStatus.IN_PROGRESS) {
			throw new IllegalArgumentException("이미 종료된 세션입니다.");
		}

		if (session.getCurrentUnitKey() == null || session.getCurrentUnitKey().isBlank()) {
			throw new IllegalArgumentException("현재 진행 중인 UNIT이 없습니다.");
		}

		if (!session.getCurrentUnitKey().equals(request.getUnitKey())) {
			throw new IllegalArgumentException("현재 UNIT과 제출된 UNIT이 다릅니다.");
		}

		ProcessDefinition process = session.getProcess();
		ProcessUnit currentUnit = findUnitByKey(process, session.getCurrentUnitKey());

		if (currentUnit == null) {
			throw new IllegalArgumentException("현재 UNIT 정보를 찾을 수 없습니다.");
		}

		saveAnswer(session, currentUnit, request, files);

		refreshPriceResult(session);

		String nextUnitKey = resolveNextUnitKey(session, currentUnit, request, hasFiles(files));

		if (nextUnitKey == null) {
			session.complete();
		} else {
			session.setCurrentUnitKey(nextUnitKey);
		}

		ProcessExecutionSession saved = sessionRepository.save(session);

		return toSessionResponse(saved);
	}

	private void saveAnswer(ProcessExecutionSession session, ProcessUnit unit, SubmitAnswerRequest request,
			List<MultipartFile> files) {
		ProcessQuestion question = unit.getQuestion();
		if (question == null) {
			throw new IllegalArgumentException("UNIT에 질문이 없습니다.");
		}

		ProcessExecutionAnswer answer = answerRepository.findBySessionAndUnitKey(session, unit.getUnitKey())
				.orElseGet(ProcessExecutionAnswer::new);

		answer.setSession(session);
		answer.setUnitKey(unit.getUnitKey());
		answer.setQuestionTextSnapshot(question.getQuestionText());
		answer.setAnswerType(normalizeAnswerType(question.getAnswerType()));
		answer.setSelectedOptionKey(trimToNull(request.getSelectedOptionKey()));
		answer.setSelectedOptionLabel(trimToNull(request.getSelectedOptionLabel()));

		try {
			answer.setAnswerValueJson(objectMapper.writeValueAsString(request.getAnswerValues()));
		} catch (Exception e) {
			answer.setAnswerValueJson("{}");
		}

		answer.setDisplayAnswerText(buildDisplayAnswerText(question, request));
		answer.clearFiles();

		ProcessExecutionAnswer savedAnswer = answerRepository.save(answer);

		if (hasFiles(files)) {
			for (MultipartFile file : files) {
				if (file == null || file.isEmpty()) {
					continue;
				}

				ProcessExecutionFile savedFile = storeFile(savedAnswer, session.getSessionKey(), file);
				savedAnswer.addFile(savedFile);
			}
		}
	}

	private ProcessExecutionFile storeFile(ProcessExecutionAnswer answer, String sessionKey, MultipartFile file) {
		try {
			String originalFilename = StringUtils
					.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());

			String extension = "";
			int dotIndex = originalFilename.lastIndexOf(".");
			if (dotIndex >= 0) {
				extension = originalFilename.substring(dotIndex);
			}

			String storedFilename = UUID.randomUUID().toString().replace("-", "") + extension;
			String datePath = LocalDate.now().toString();

			Path directory = Path.of(uploadPath, "process-test", sessionKey, datePath);
			Files.createDirectories(directory);

			Path targetPath = directory.resolve(storedFilename);
			file.transferTo(targetPath.toFile());

			String fileUrl = "/administration/upload/process-test/" + sessionKey + "/" + datePath + "/"
					+ storedFilename;

			ProcessExecutionFile executionFile = new ProcessExecutionFile();
			executionFile.setAnswer(answer);
			executionFile.setOriginalFilename(originalFilename);
			executionFile.setStoredFilename(storedFilename);
			executionFile.setContentType(file.getContentType());
			executionFile.setFileSize(file.getSize());
			executionFile.setFilePath(targetPath.toString());
			executionFile.setFileUrl(fileUrl);

			return executionFile;
		} catch (IOException e) {
			throw new IllegalArgumentException("파일 저장 중 오류가 발생했습니다.");
		}
	}

	private String resolveNextUnitKey(ProcessExecutionSession session, ProcessUnit currentUnit,
	        SubmitAnswerRequest request, boolean hasFiles) {

	    ProcessDefinition process = session.getProcess();

	    List<ProcessUnitBranch> sortedBranches = currentUnit.getBranches() == null
	            ? List.of()
	            : currentUnit.getBranches().stream()
	                    .filter(ProcessUnitBranch::isUseYn)
	                    .sorted(Comparator.comparingInt(ProcessUnitBranch::getPriority))
	                    .toList();

	    /*
	     * 중요:
	     * DEFAULT는 항상 true이므로 일반 분기보다 먼저 검사하면 안 됩니다.
	     *
	     * 1. CHOICE / CONDITION / UPLOAD 등 일반 분기 먼저 검사
	     * 2. 없으면 DEFAULT 검사
	     * 3. DEFAULT도 없으면 "분기 없음"으로 보고 AUTO_NEXT 처리
	     *
	     * 여기서 AUTO_NEXT의 의미는
	     * "같은 STEP의 다음 UNIT"이 아니라
	     * "다음 STEP의 첫 번째 UNIT"입니다.
	     */
	    List<ProcessUnitBranch> matchedNormalBranches = sortedBranches.stream()
	            .filter(branch -> branch.getBranchType() != ProcessBranchType.DEFAULT)
	            .filter(branch -> isBranchMatched(branch, request, hasFiles))
	            .toList();

	    if (!matchedNormalBranches.isEmpty()) {
	        return resolveBranchTarget(session, process, currentUnit, matchedNormalBranches.get(0));
	    }

	    ProcessUnitBranch defaultBranch = sortedBranches.stream()
	            .filter(branch -> branch.getBranchType() == ProcessBranchType.DEFAULT)
	            .findFirst()
	            .orElse(null);

	    if (defaultBranch != null) {
	        return resolveBranchTarget(session, process, currentUnit, defaultBranch);
	    }

	    /*
	     * 분기 자체가 없는 경우도 AUTO_NEXT와 동일하게 처리합니다.
	     * 즉, 다음 STEP의 첫 번째 UNIT으로 이동합니다.
	     */
	    return resolveAutoNextUnitKey(session, process, currentUnit);
	}

	private String resolveBranchTarget(ProcessExecutionSession session, ProcessDefinition process,
	        ProcessUnit currentUnit, ProcessUnitBranch selectedBranch) {

	    if (selectedBranch == null) {
	        return resolveAutoNextUnitKey(session, process, currentUnit);
	    }

	    ProcessBranchTargetMode targetMode = selectedBranch.getTargetMode();

	    /*
	     * targetMode가 null이거나 AUTO_NEXT이면
	     * 무조건 다음 STEP의 첫 번째 UNIT으로 이동합니다.
	     */
	    if (targetMode == null || targetMode == ProcessBranchTargetMode.AUTO_NEXT) {
	        return resolveAutoNextUnitKey(session, process, currentUnit);
	    }

	    if (targetMode == ProcessBranchTargetMode.JUMP_UNIT) {
	        String targetUnitKey = trimToNull(selectedBranch.getTargetUnitKey());

	        if (targetUnitKey == null) {
	            throw new IllegalArgumentException("JUMP_UNIT 분기에는 대상 UNIT이 필요합니다.");
	        }

	        ProcessUnit targetUnit = findUnitByKey(process, targetUnitKey);

	        if (targetUnit == null) {
	            throw new IllegalArgumentException("JUMP_UNIT 대상 UNIT을 찾을 수 없습니다: " + targetUnitKey);
	        }

	        return targetUnit.getUnitKey();
	    }

	    if (targetMode == ProcessBranchTargetMode.DEFER_TO_UNIT) {
	        String targetUnitKey = trimToNull(selectedBranch.getTargetUnitKey());

	        if (targetUnitKey == null) {
	            throw new IllegalArgumentException("DEFER_TO_UNIT 분기에는 대상 UNIT이 필요합니다.");
	        }

	        ProcessUnit targetUnit = findUnitByKey(process, targetUnitKey);

	        if (targetUnit == null) {
	            throw new IllegalArgumentException("DEFER_TO_UNIT 대상 UNIT을 찾을 수 없습니다: " + targetUnitKey);
	        }

	        /*
	         * DEFER_TO_UNIT은 즉시 target으로 점프하는 게 아니라,
	         * 해당 UNIT을 예약해두고 다음 STEP 진입 시 활성화합니다.
	         */
	        addDeferredUnitKey(session, targetUnit.getUnitKey());

	        return resolveAutoNextUnitKey(session, process, currentUnit);
	    }

	    return resolveAutoNextUnitKey(session, process, currentUnit);
	}

	private boolean isBranchMatched(ProcessUnitBranch branch, SubmitAnswerRequest request, boolean hasFiles) {
		ProcessBranchType branchType = branch.getBranchType();

		if (branchType == ProcessBranchType.DEFAULT) {
			return true;
		}

		if (branchType == ProcessBranchType.CHOICE) {
			return branch.getAnswerOptionKey() != null
					&& branch.getAnswerOptionKey().equals(request.getSelectedOptionKey());
		}

		if (branchType == ProcessBranchType.UPLOAD) {
			return hasFiles;
		}

		if (branchType == ProcessBranchType.CONDITION) {
			return evaluateCondition(branch.getConditionJson(), request.getAnswerValues());
		}

		return false;
	}

	private boolean evaluateCondition(String conditionJson, Map<String, Object> answerValues) {
	    if (conditionJson == null || conditionJson.isBlank()) {
	        return false;
	    }

	    if (answerValues == null || answerValues.isEmpty()) {
	        return false;
	    }

	    try {
	        Map<String, Object> conditionMap = objectMapper.readValue(
	                conditionJson,
	                new TypeReference<>() {
	                }
	        );

	        String mode = String.valueOf(conditionMap.getOrDefault("mode", "ALL"));

	        List<Map<String, Object>> conditions = objectMapper.convertValue(
	                conditionMap.getOrDefault("conditions", List.of()),
	                new TypeReference<>() {
	                }
	        );

	        if (conditions.isEmpty()) {
	            return false;
	        }

	        List<Boolean> results = new ArrayList<>();

	        for (Map<String, Object> condition : conditions) {
	            String fieldKey = trimToNull(String.valueOf(condition.get("fieldKey")));
	            String operator = trimToNull(String.valueOf(condition.get("operator")));
	            Object standardValueObject = condition.get("value");

	            if (fieldKey == null || operator == null || standardValueObject == null) {
	                results.add(false);
	                continue;
	            }

	            Object inputValueObject = answerValues.get(fieldKey);

	            if (inputValueObject == null) {
	                results.add(false);
	                continue;
	            }

	            BigDecimal inputValue = toBigDecimal(inputValueObject);
	            BigDecimal standardValue = toBigDecimal(standardValueObject);

	            if (inputValue == null || standardValue == null) {
	                results.add(false);
	                continue;
	            }

	            int compare = inputValue.compareTo(standardValue);

	            boolean matched = switch (operator) {
	                case "GT" -> compare > 0;
	                case "GTE" -> compare >= 0;
	                case "LT" -> compare < 0;
	                case "LTE" -> compare <= 0;
	                case "EQ" -> compare == 0;
	                default -> false;
	            };

	            results.add(matched);
	        }

	        if ("ANY".equalsIgnoreCase(mode)) {
	            return results.stream().anyMatch(Boolean::booleanValue);
	        }

	        return results.stream().allMatch(Boolean::booleanValue);

	    } catch (Exception e) {
	        return false;
	    }
	}

	private BigDecimal toBigDecimal(Object value) {
	    if (value == null) {
	        return null;
	    }

	    try {
	        String text = String.valueOf(value).trim();

	        if (text.isBlank()) {
	            return null;
	        }

	        return new BigDecimal(text);
	    } catch (Exception e) {
	        return null;
	    }
	}
	
	private String resolveAutoNextUnitKey(ProcessExecutionSession session, ProcessDefinition process,
	        ProcessUnit currentUnit) {

	    if (process == null || currentUnit == null || currentUnit.getUnitKey() == null) {
	        return null;
	    }

	    ProcessStep currentStep = findStepByUnitKey(process, currentUnit.getUnitKey());

	    if (currentStep == null) {
	        return null;
	    }

	    ProcessStep nextStep = findNextStep(process, currentStep);

	    /*
	     * 다음 STEP이 없으면 프로세스 완료입니다.
	     */
	    if (nextStep == null) {
	        return null;
	    }

	    List<ProcessUnit> nextStepUnits = getOrderedUsableUnits(nextStep);

	    /*
	     * 다음 STEP은 있는데 실행 가능한 UNIT이 없으면 설계 오류로 보는 게 맞습니다.
	     * 조용히 완료 처리하면 실제 프로세스 오류를 놓칠 수 있습니다.
	     */
	    if (nextStepUnits.isEmpty()) {
	        throw new IllegalStateException(
	                "AUTO_NEXT는 다음 STEP의 첫 번째 UNIT으로 이동해야 하지만, 다음 STEP에 실행 가능한 UNIT이 없습니다. STEP: "
	                        + nullToDash(nextStep.getTitle())
	        );
	    }

	    /*
	     * 다음 STEP에 도착했을 때, 예약된 DEFER_TO_UNIT이 해당 STEP 안에 있으면
	     * 기존 설계 의도대로 예약된 UNIT을 먼저 활성화합니다.
	     *
	     * 단, 일반 AUTO_NEXT / 분기 없음 자체의 기본 목적지는 항상 nextStepUnits.get(0)입니다.
	     */
	    ProcessUnit deferredUnitInArrivedStep = findDeferredUnitInSameStep(session, process, nextStep.getStepKey());

	    if (deferredUnitInArrivedStep != null) {
	        removeDeferredUnitKey(session, deferredUnitInArrivedStep.getUnitKey());
	        return deferredUnitInArrivedStep.getUnitKey();
	    }

	    return nextStepUnits.get(0).getUnitKey();
	}

	private ProcessStep findStepByUnitKey(ProcessDefinition process, String unitKey) {
	    if (process == null || process.getSteps() == null || unitKey == null || unitKey.isBlank()) {
	        return null;
	    }

	    return getOrderedSteps(process).stream()
	            .filter(step -> getOrderedUsableUnits(step).stream()
	                    .anyMatch(unit -> unitKey.equals(unit.getUnitKey())))
	            .findFirst()
	            .orElse(null);
	}

	private ProcessStep findNextStep(ProcessDefinition process, ProcessStep currentStep) {
	    if (process == null || currentStep == null || currentStep.getStepKey() == null) {
	        return null;
	    }

	    List<ProcessStep> orderedSteps = getOrderedSteps(process);

	    for (int i = 0; i < orderedSteps.size(); i++) {
	        ProcessStep step = orderedSteps.get(i);

	        if (!currentStep.getStepKey().equals(step.getStepKey())) {
	            continue;
	        }

	        if (i + 1 >= orderedSteps.size()) {
	            return null;
	        }

	        return orderedSteps.get(i + 1);
	    }

	    return null;
	}

	private List<ProcessStep> getOrderedSteps(ProcessDefinition process) {
	    if (process == null || process.getSteps() == null) {
	        return List.of();
	    }

	    return process.getSteps().stream()
	            .sorted(Comparator.comparingInt(ProcessStep::getSortOrder))
	            .toList();
	}

	private List<ProcessUnit> getOrderedUsableUnits(ProcessStep step) {
	    if (step == null || step.getUnits() == null) {
	        return List.of();
	    }

	    return step.getUnits().stream()
	            .filter(ProcessUnit::isUseYn)
	            .sorted(Comparator.comparingInt(ProcessUnit::getSortOrder))
	            .toList();
	}
	
	private ProcessUnit findDeferredUnitInSameStep(ProcessExecutionSession session, ProcessDefinition process,
			String arrivedStepKey) {
		List<String> deferredKeys = getDeferredUnitKeys(session);

		for (String deferredKey : deferredKeys) {
			ProcessUnit unit = findUnitByKey(process, deferredKey);
			if (unit != null && unit.getStep().getStepKey().equals(arrivedStepKey)) {
				return unit;
			}
		}

		return null;
	}

	private void addDeferredUnitKey(ProcessExecutionSession session, String unitKey) {
		if (unitKey == null || unitKey.isBlank()) {
			return;
		}

		List<String> keys = getDeferredUnitKeys(session);
		if (!keys.contains(unitKey)) {
			keys.add(unitKey);
		}

		setDeferredUnitKeys(session, keys);
	}

	private void removeDeferredUnitKey(ProcessExecutionSession session, String unitKey) {
		List<String> keys = getDeferredUnitKeys(session);
		keys.remove(unitKey);
		setDeferredUnitKeys(session, keys);
	}

	private List<String> getDeferredUnitKeys(ProcessExecutionSession session) {
		try {
			if (session.getDeferredUnitKeysJson() == null || session.getDeferredUnitKeysJson().isBlank()) {
				return new ArrayList<>();
			}

			return objectMapper.readValue(session.getDeferredUnitKeysJson(), new TypeReference<>() {
			});
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}

	private void setDeferredUnitKeys(ProcessExecutionSession session, List<String> keys) {
		try {
			session.setDeferredUnitKeysJson(objectMapper.writeValueAsString(keys));
		} catch (Exception e) {
			session.setDeferredUnitKeysJson("[]");
		}
	}

	private void refreshPriceResult(ProcessExecutionSession session) {
		List<ProcessExecutionAnswer> answers = answerRepository.findBySessionOrderByCreatedAtAsc(session);
		PriceCalculationResult result = processPriceCalculator.calculate(answers);

		session.setCalculatedPriceAmount(result.getAmount());
		session.setPriceResultJson(result.getDetailJson());
	}

	private SessionResponse toSessionResponse(ProcessExecutionSession session) {
		SessionResponse response = new SessionResponse();
		response.setSessionKey(session.getSessionKey());
		response.setProcessId(session.getProcess().getId());
		response.setProcessName(session.getProcess().getName());
		response.setStatus(session.getStatus());
		response.setCurrentUnitKey(session.getCurrentUnitKey());
		response.setCalculatedPriceAmount(session.getCalculatedPriceAmount());
		response.setPriceResultJson(session.getPriceResultJson());
		response.setCreatedAt(session.getCreatedAt());
		response.setCompletedAt(session.getCompletedAt());

		if (session.getCurrentUnitKey() != null) {
			ProcessUnit currentUnit = findUnitByKey(session.getProcess(), session.getCurrentUnitKey());
			if (currentUnit != null) {
				response.setCurrentUnit(toCurrentUnitResponse(currentUnit));
			}
		}

		List<ProcessExecutionAnswer> answers = answerRepository.findBySessionOrderByCreatedAtAsc(session);
		response.setAnswers(answers.stream().map(this::toAnswerHistory).toList());

		return response;
	}

	private CurrentUnitResponse toCurrentUnitResponse(ProcessUnit unit) {
		ProcessQuestion question = unit.getQuestion();

		CurrentUnitResponse response = new CurrentUnitResponse();
		response.setUnitKey(unit.getUnitKey());
		response.setUnitTitle(unit.getTitle());
		response.setStepTitle(unit.getStep().getTitle());

		if (question != null) {
			response.setQuestionText(question.getQuestionText());
			response.setAnswerType(normalizeAnswerType(question.getAnswerType()));
			response.setRequiredYn(question.isRequiredYn());
			response.setHelperText(question.getHelperText());

			response.setOptions(
					question.getOptions().stream().sorted(Comparator.comparingInt(ProcessAnswerOption::getSortOrder))
							.map(this::toOptionResponse).toList());

			response.setFields(
					question.getFields().stream().sorted(Comparator.comparingInt(ProcessAnswerField::getSortOrder))
							.map(this::toFieldResponse).toList());
		}

		return response;
	}

	private OptionResponse toOptionResponse(ProcessAnswerOption option) {
		OptionResponse response = new OptionResponse();
		response.setOptionKey(option.getOptionKey());
		response.setLabel(option.getLabel());
		response.setValueText(option.getValueText());
		return response;
	}

	private FieldResponse toFieldResponse(ProcessAnswerField field) {
		FieldResponse response = new FieldResponse();
		response.setFieldKey(field.getFieldKey());
		response.setLabel(field.getLabel());
		response.setInputValueType(field.getInputValueType());
		response.setPlaceholder(field.getPlaceholder());
		response.setUnitText(field.getUnitText());
		response.setRequiredYn(field.isRequiredYn());
		return response;
	}

	private AnswerHistoryResponse toAnswerHistory(ProcessExecutionAnswer answer) {
		AnswerHistoryResponse response = new AnswerHistoryResponse();
		response.setUnitKey(answer.getUnitKey());
		response.setQuestionText(answer.getQuestionTextSnapshot());
		response.setAnswerType(answer.getAnswerType());
		response.setSelectedOptionKey(answer.getSelectedOptionKey());
		response.setSelectedOptionLabel(answer.getSelectedOptionLabel());
		response.setDisplayAnswerText(answer.getDisplayAnswerText());
		response.setAnswerValueJson(answer.getAnswerValueJson());
		response.setCreatedAt(answer.getCreatedAt());

		response.setFiles(answer.getFiles().stream().map(this::toFileResponse).toList());

		return response;
	}

	private FileResponse toFileResponse(ProcessExecutionFile file) {
		FileResponse response = new FileResponse();
		response.setOriginalFilename(file.getOriginalFilename());
		response.setFileUrl(file.getFileUrl());
		response.setFileSize(file.getFileSize());
		return response;
	}

	private String buildDisplayAnswerText(ProcessQuestion question, SubmitAnswerRequest request) {
	    ProcessAnswerType answerType = normalizeAnswerType(question.getAnswerType());

	    if (answerType == null) {
	        return "-";
	    }

	    return switch (answerType) {
	        case SINGLE_SELECT -> request.getSelectedOptionLabel() == null || request.getSelectedOptionLabel().isBlank()
	                ? nullToDash(request.getSelectedOptionKey())
	                : request.getSelectedOptionLabel();

	        case TEXT_INPUT, NUMBER_INPUT, MULTI_INPUT -> {
	            Map<String, String> labelMap = new LinkedHashMap<>();

	            if (question.getFields() != null) {
	                question.getFields().forEach(field -> labelMap.put(field.getFieldKey(), field.getLabel()));
	            }

	            Map<String, Object> answerValues = request.getAnswerValues() == null
	                    ? Map.of()
	                    : request.getAnswerValues();

	            List<String> parts = new ArrayList<>();

	            answerValues.forEach((key, value) -> {
	                String label = labelMap.getOrDefault(key, key);
	                parts.add(label + ": " + nullToDash(value));
	            });

	            yield parts.isEmpty() ? "-" : String.join(" / ", parts);
	        }

	        case FILE_UPLOAD -> "파일 업로드";
	    };
	}

	private ProcessAnswerType normalizeAnswerType(ProcessAnswerType answerType) {
	    if (answerType == null) {
	        return ProcessAnswerType.SINGLE_SELECT;
	    }

	    /*
	     * 기존 저장 데이터 호환:
	     * 과거 MULTI_INPUT은 텍스트/숫자 입력 그룹이었고,
	     * 이번 구조에서는 숫자 분기 처리를 위해 NUMBER_INPUT 쪽으로 보는 것이 안전합니다.
	     */
	    if (answerType == ProcessAnswerType.MULTI_INPUT) {
	        return ProcessAnswerType.NUMBER_INPUT;
	    }

	    return answerType;
	}

	private String nullToDash(Object value) {
	    if (value == null) {
	        return "-";
	    }

	    String text = String.valueOf(value).trim();
	    return text.isBlank() ? "-" : text;
	}
	
	private ProcessExecutionSession getSessionWithAll(String sessionKey) {
		return sessionRepository.findWithAllBySessionKey(sessionKey)
				.orElseThrow(() -> new IllegalArgumentException("실행 세션을 찾을 수 없습니다."));
	}

	private ProcessUnit findFirstUnit(ProcessDefinition process) {
		return flattenUnits(process).stream().findFirst().orElse(null);
	}

	private List<ProcessUnit> flattenUnits(ProcessDefinition process) {
	    return getOrderedSteps(process).stream()
	            .flatMap(step -> getOrderedUsableUnits(step).stream())
	            .toList();
	}

	private ProcessUnit findUnitByKey(ProcessDefinition process, String unitKey) {
		if (unitKey == null) {
			return null;
		}

		return flattenUnits(process).stream().filter(unit -> unit.getUnitKey().equals(unitKey)).findFirst()
				.orElse(null);
	}

	private boolean hasFiles(List<MultipartFile> files) {
		return files != null && files.stream().anyMatch(file -> file != null && !file.isEmpty());
	}

	private String generateSessionKey() {
		String key;
		do {
			key = "session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
		} while (sessionRepository.existsBySessionKey(key));

		return key;
	}

	private TestProcessSummaryResponse toProcessSummary(ProcessDefinition process) {
		TestProcessSummaryResponse response = new TestProcessSummaryResponse();
		response.setId(process.getId());
		response.setProcessKey(process.getProcessKey());
		response.setName(process.getName());
		response.setDescription(process.getDescription());
		response.setStatus(process.getStatus() == null ? ProcessStatus.DRAFT : process.getStatus());
		response.setCreatedAt(process.getCreatedAt());
		return response;
	}

	private String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}