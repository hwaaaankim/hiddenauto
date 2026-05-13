package com.dev.HiddenBATHAuto.service.process;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.AnswerFieldDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.BranchDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.ProcessDetailRequest;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.QuestionDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.StepDto;
import com.dev.HiddenBATHAuto.dto.process.ProcessMakerDtos.UnitDto;
import com.dev.HiddenBATHAuto.enums.process.ProcessAnswerType;
import com.dev.HiddenBATHAuto.enums.process.ProcessBranchTargetMode;
import com.dev.HiddenBATHAuto.enums.process.ProcessBranchType;
import com.dev.HiddenBATHAuto.enums.process.ProcessInputValueType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProcessDefinitionGraphValidator {

	private final ObjectMapper objectMapper;

	public ProcessDefinitionValidationResult validate(ProcessDetailRequest request) {
		ProcessDefinitionValidationResult result = new ProcessDefinitionValidationResult();

		ProcessGraph graph = buildGraph(request);

		if (graph.units.isEmpty()) {
			return result;
		}

		validateBranchBaseRules(graph, result);
		validateConditionRules(graph, result);
		validateDeferCollision(graph, result);

		Map<String, List<String>> edges = buildExecutionEdges(graph);
		validateCycle(graph, edges, result);
		validateUnreachableUnits(graph, edges, result);

		return result;
	}

	private ProcessGraph buildGraph(ProcessDetailRequest request) {
		ProcessGraph graph = new ProcessGraph();

		if (request.getSteps() == null) {
			return graph;
		}

		for (int stepIndex = 0; stepIndex < request.getSteps().size(); stepIndex++) {
			StepDto step = request.getSteps().get(stepIndex);
			if (step.getUnits() == null) {
				continue;
			}

			for (int unitIndex = 0; unitIndex < step.getUnits().size(); unitIndex++) {
				UnitDto unit = step.getUnits().get(unitIndex);

				UnitNode node = new UnitNode();
				node.step = step;
				node.unit = unit;
				node.stepKey = step.getStepKey();
				node.unitKey = unit.getUnitKey();
				node.stepTitle = step.getTitle();
				node.unitTitle = unit.getTitle();
				node.stepIndex = stepIndex;
				node.unitIndex = unitIndex;
				node.globalIndex = graph.units.size();

				graph.units.add(node);
				graph.unitMap.put(node.unitKey, node);
			}
		}

		return graph;
	}

	private void validateBranchBaseRules(ProcessGraph graph, ProcessDefinitionValidationResult result) {
		for (UnitNode source : graph.units) {
			List<BranchDto> branches = safeBranches(source.unit);
			ProcessAnswerType answerType = getNormalizedAnswerType(source.unit);

			if (branches.isEmpty()) {
				continue;
			}

			if (answerType == ProcessAnswerType.TEXT_INPUT) {
				result.addError(formatUnit(source) + "은 텍스트 입력 UNIT입니다. 텍스트 입력은 분기를 추가할 수 없습니다.");
				continue;
			}

			long defaultCount = branches.stream().filter(branch -> branch.getBranchType() == ProcessBranchType.DEFAULT)
					.count();

			/*
			 * 변경 사항: 기존에는 분기가 있으면 DEFAULT를 필수로 요구했지만, 이제는 DEFAULT가 없어도 조건/선택에 매칭되지 않으면 자동으로
			 * 다음 UNIT으로 진행합니다.
			 */
			if (defaultCount > 1) {
				result.addError(formatUnit(source) + "에 DEFAULT 분기가 2개 이상 있습니다. DEFAULT는 1개만 허용됩니다.");
			}

			Set<String> usedAnswerOptionKeys = new HashSet<>();

			int lastIndex = branches.size() - 1;

			for (int i = 0; i < branches.size(); i++) {
				BranchDto branch = branches.get(i);

				if (branch.getBranchType() == null) {
					result.addError(formatUnit(source) + "에 분기 타입이 비어있는 분기가 있습니다.");
					continue;
				}

				if (branch.getTargetMode() == null) {
					branch.setTargetMode(ProcessBranchTargetMode.AUTO_NEXT);
				}

				if (branch.getBranchType() == ProcessBranchType.DEFAULT) {
					if (i != lastIndex) {
						result.addError(formatUnit(source) + "의 DEFAULT 분기는 항상 마지막 순서여야 합니다.");
					}

					if (branch.getTargetMode() != ProcessBranchTargetMode.AUTO_NEXT
							|| !isBlank(branch.getTargetUnitKey())) {
						result.addError(formatUnit(source)
								+ "의 DEFAULT 분기는 AUTO_NEXT만 허용됩니다. 특정 UNIT으로 보내려면 DEFAULT가 아니라 명시적인 CHOICE/CONDITION 분기를 사용해주세요.");
					}

					continue;
				}

				validateBranchTypeAllowedByAnswerType(source, answerType, branch, result);

				if (branch.getTargetMode() != ProcessBranchTargetMode.AUTO_NEXT) {
					if (isBlank(branch.getTargetUnitKey())) {
						result.addError(
								formatUnit(source) + "의 [" + safeLabel(branch.getLabel()) + "] 분기는 대상 UNIT이 필요합니다.");
						continue;
					}

					UnitNode target = graph.unitMap.get(branch.getTargetUnitKey());
					if (target == null) {
						result.addError(formatUnit(source) + "의 [" + safeLabel(branch.getLabel())
								+ "] 분기 대상 UNIT을 찾을 수 없습니다: " + branch.getTargetUnitKey());
						continue;
					}

					if (source.unitKey.equals(target.unitKey)) {
						result.addError(
								formatUnit(source) + "의 [" + safeLabel(branch.getLabel()) + "] 분기가 자기 자신을 대상으로 합니다.");
					}

					if (branch.getTargetMode() == ProcessBranchTargetMode.DEFER_TO_UNIT
							&& target.stepIndex <= source.stepIndex) {
						result.addError(formatUnit(source) + "의 [" + safeLabel(branch.getLabel())
								+ "] DEFER_TO_UNIT 대상은 현재 STEP보다 뒤쪽 STEP이어야 합니다.");
					}
				}

				if (branch.getBranchType() == ProcessBranchType.CHOICE) {
					validateChoiceBranch(source, branch, result);

					String answerOptionKey = branch.getAnswerOptionKey();
					if (!isBlank(answerOptionKey)) {
						if (!usedAnswerOptionKeys.add(answerOptionKey)) {
							result.addError(formatUnit(source) + "의 선택 답변 [" + answerOptionKey
									+ "]에 분기가 중복 설정되어 있습니다. 동일 답변에는 분기를 1개만 설정할 수 있습니다.");
						}
					}
				}
			}
		}
	}

	private void validateBranchTypeAllowedByAnswerType(UnitNode source, ProcessAnswerType answerType, BranchDto branch,
			ProcessDefinitionValidationResult result) {
		ProcessBranchType branchType = branch.getBranchType();

		if (answerType == ProcessAnswerType.SINGLE_SELECT) {
			if (branchType != ProcessBranchType.CHOICE) {
				result.addError(formatUnit(source) + "은 '여러 개 중 하나 선택' UNIT입니다. 분기 타입은 선택 답변만 사용할 수 있습니다.");
			}
			return;
		}

		if (answerType == ProcessAnswerType.NUMBER_INPUT) {
			if (branchType != ProcessBranchType.CONDITION) {
				result.addError(formatUnit(source) + "은 '숫자 입력' UNIT입니다. 분기 타입은 숫자 조건식만 사용할 수 있습니다.");
			}
			return;
		}

		if (answerType == ProcessAnswerType.FILE_UPLOAD) {
			if (branchType != ProcessBranchType.UPLOAD) {
				result.addError(formatUnit(source) + "은 '파일 등록' UNIT입니다. 분기 타입은 파일 업로드만 사용할 수 있습니다.");
			}
		}
	}

	private ProcessAnswerType getNormalizedAnswerType(UnitDto unit) {
		if (unit == null || unit.getQuestion() == null || unit.getQuestion().getAnswerType() == null) {
			return ProcessAnswerType.SINGLE_SELECT;
		}

		ProcessAnswerType answerType = unit.getQuestion().getAnswerType();

		/*
		 * 기존 데이터 호환용
		 */
		if (answerType == ProcessAnswerType.MULTI_INPUT) {
			return ProcessAnswerType.NUMBER_INPUT;
		}

		return answerType;
	}

	private void validateChoiceBranch(UnitNode source, BranchDto branch, ProcessDefinitionValidationResult result) {
		QuestionDto question = source.unit.getQuestion();

		if (question == null || question.getOptions() == null || question.getOptions().isEmpty()) {
			result.addError(formatUnit(source) + "의 [" + safeLabel(branch.getLabel()) + "] 선택형 분기는 선택 답변 목록이 필요합니다.");
			return;
		}

		if (isBlank(branch.getAnswerOptionKey())) {
			result.addError(
					formatUnit(source) + "의 [" + safeLabel(branch.getLabel()) + "] 선택형 분기는 answerOptionKey가 필요합니다.");
			return;
		}

		boolean exists = question.getOptions().stream()
				.anyMatch(option -> branch.getAnswerOptionKey().equals(option.getOptionKey()));

		if (!exists) {
			result.addError(formatUnit(source) + "의 [" + safeLabel(branch.getLabel()) + "] 분기가 존재하지 않는 답변 옵션을 참조합니다: "
					+ branch.getAnswerOptionKey());
		}
	}

	private void validateConditionRules(ProcessGraph graph, ProcessDefinitionValidationResult result) {
		for (UnitNode source : graph.units) {
			List<BranchDto> conditionBranches = safeBranches(source.unit).stream()
					.filter(branch -> branch.getBranchType() == ProcessBranchType.CONDITION).toList();

			if (conditionBranches.isEmpty()) {
				continue;
			}

			Set<String> numberFieldKeys = getNumberFieldKeys(source.unit);

			List<ConditionBranchInfo> infos = new ArrayList<>();

			for (BranchDto branch : conditionBranches) {
				ParsedCondition parsed = parseCondition(branch.getConditionJson());

				if (!parsed.valid) {
					result.addError(
							formatUnit(source) + "의 [" + safeLabel(branch.getLabel()) + "] 조건 JSON 형식이 올바르지 않습니다.");
					continue;
				}

				if (!"ALL".equalsIgnoreCase(parsed.mode)) {
					result.addError(
							formatUnit(source) + "의 [" + safeLabel(branch.getLabel()) + "] 숫자 조건 분기는 ALL 조건만 허용됩니다.");
					continue;
				}

				if (parsed.conditions.isEmpty()) {
					result.addError(formatUnit(source) + "의 [" + safeLabel(branch.getLabel())
							+ "] 조건 분기에는 조건이 최소 1개 이상 필요합니다.");
					continue;
				}

				if (!parsed.valid) {
					result.addError(
							formatUnit(source) + "의 [" + safeLabel(branch.getLabel()) + "] 조건 JSON 형식이 올바르지 않습니다.");
					continue;
				}

				if (parsed.conditions.isEmpty()) {
					result.addError(formatUnit(source) + "의 [" + safeLabel(branch.getLabel())
							+ "] 조건 분기에는 조건이 최소 1개 이상 필요합니다.");
					continue;
				}

				for (ConditionRow row : parsed.conditions) {
					if (isBlank(row.fieldKey)) {
						result.addError(
								formatUnit(source) + "의 [" + safeLabel(branch.getLabel()) + "] 조건에 fieldKey가 없습니다.");
					} else if (!numberFieldKeys.contains(row.fieldKey)) {
						result.addError(formatUnit(source) + "의 [" + safeLabel(branch.getLabel())
								+ "] 조건이 숫자 필드가 아닌 값을 참조합니다: " + row.fieldKey);
					}

					if (!Set.of("GT", "GTE", "LT", "LTE", "EQ").contains(row.operator)) {
						result.addError(formatUnit(source) + "의 [" + safeLabel(branch.getLabel())
								+ "] 조건 연산자가 올바르지 않습니다: " + row.operator);
					}

					if (row.value == null) {
						result.addError(formatUnit(source) + "의 [" + safeLabel(branch.getLabel()) + "] 조건 기준값이 없습니다.");
					}
				}

				ConditionShape shape = toConditionShape(parsed);
				if (shape.contradiction) {
					result.addError(
							formatUnit(source) + "의 [" + safeLabel(branch.getLabel()) + "] 조건은 절대 만족될 수 없는 조건입니다.");
				}

				infos.add(new ConditionBranchInfo(branch, parsed, shape));
			}

			validateConditionOverlapAndGapErrors(source, infos, result);
		}
	}

	private void validateConditionOverlapAndGapErrors(UnitNode source, List<ConditionBranchInfo> infos,
			ProcessDefinitionValidationResult result) {
		Map<String, List<ConditionIntervalInfo>> intervalsByField = new LinkedHashMap<>();

		for (ConditionBranchInfo info : infos) {
			if (info.shape == null || info.shape.contradiction) {
				continue;
			}

			for (Map.Entry<String, Interval> entry : info.shape.fieldIntervals.entrySet()) {
				String fieldKey = entry.getKey();
				Interval interval = entry.getValue();

				intervalsByField.computeIfAbsent(fieldKey, key -> new ArrayList<>())
						.add(new ConditionIntervalInfo(info.branch, interval));
			}
		}

		for (Map.Entry<String, List<ConditionIntervalInfo>> entry : intervalsByField.entrySet()) {
			String fieldKey = entry.getKey();
			List<ConditionIntervalInfo> intervals = entry.getValue();

			if (intervals.size() <= 1) {
				continue;
			}

			intervals.sort((a, b) -> compareLower(a.interval, b.interval));

			for (int i = 0; i < intervals.size() - 1; i++) {
				ConditionIntervalInfo current = intervals.get(i);
				ConditionIntervalInfo next = intervals.get(i + 1);

				Interval currentInterval = current.interval;
				Interval nextInterval = next.interval;

				if (currentInterval.upper == null) {
					result.addError(formatUnit(source) + "의 숫자 조건 범위가 겹칩니다. 필드 [" + getFieldLabel(source.unit, fieldKey)
							+ "]에서 [" + safeLabel(current.branch.getLabel()) + "] 이후 조건은 도달할 수 없습니다.");
					continue;
				}

				if (nextInterval.lower == null) {
					result.addError(formatUnit(source) + "의 숫자 조건 범위가 겹칩니다. 필드 [" + getFieldLabel(source.unit, fieldKey)
							+ "]에서 [" + safeLabel(next.branch.getLabel()) + "] 조건을 확인해주세요.");
					continue;
				}

				int compare = currentInterval.upper.compareTo(nextInterval.lower);

				if (compare > 0) {
					result.addError(formatUnit(source) + "의 숫자 조건 범위가 겹칩니다. 필드 [" + getFieldLabel(source.unit, fieldKey)
							+ "]에서 [" + safeLabel(current.branch.getLabel()) + "] / ["
							+ safeLabel(next.branch.getLabel()) + "] 조건을 확인해주세요.");
					continue;
				}

				if (compare == 0 && currentInterval.upperInclusive && nextInterval.lowerInclusive) {
					result.addError(formatUnit(source) + "의 숫자 조건 경계값 [" + currentInterval.upper + "]이 중복 포함됩니다. 필드 ["
							+ getFieldLabel(source.unit, fieldKey) + "]에서 [" + safeLabel(current.branch.getLabel())
							+ "] / [" + safeLabel(next.branch.getLabel()) + "] 조건을 확인해주세요.");
					continue;
				}

				if (compare < 0) {
					result.addError(formatUnit(source) + "의 숫자 조건 범위 사이에 빈 구간이 있습니다. 필드 ["
							+ getFieldLabel(source.unit, fieldKey) + "]에서 " + currentInterval.upper + " ~ "
							+ nextInterval.lower + " 사이 값은 갈 수 있는 분기가 없습니다.");
					continue;
				}

				if (compare == 0 && !currentInterval.upperInclusive && !nextInterval.lowerInclusive) {
					result.addError(formatUnit(source) + "의 숫자 조건 경계값 [" + currentInterval.upper
							+ "]이 어느 분기에도 포함되지 않습니다. 필드 [" + getFieldLabel(source.unit, fieldKey) + "]에서 ["
							+ safeLabel(current.branch.getLabel()) + "] / [" + safeLabel(next.branch.getLabel())
							+ "] 조건을 확인해주세요.");
				}
			}
		}
	}

	private int compareLower(Interval a, Interval b) {
		if (a.lower == null && b.lower == null) {
			return 0;
		}

		if (a.lower == null) {
			return -1;
		}

		if (b.lower == null) {
			return 1;
		}

		int compare = a.lower.compareTo(b.lower);

		if (compare != 0) {
			return compare;
		}

		if (a.lowerInclusive == b.lowerInclusive) {
			return 0;
		}

		return a.lowerInclusive ? -1 : 1;
	}

	private String getFieldLabel(UnitDto unit, String fieldKey) {
		if (unit == null || unit.getQuestion() == null || unit.getQuestion().getFields() == null) {
			return fieldKey;
		}

		return unit.getQuestion().getFields().stream()
				.filter(field -> fieldKey != null && fieldKey.equals(field.getFieldKey())).map(AnswerFieldDto::getLabel)
				.findFirst().orElse(fieldKey);
	}

	private void validateDeferCollision(ProcessGraph graph, ProcessDefinitionValidationResult result) {
		Map<String, Map<String, Set<String>>> targetStepSourceTargetMap = new LinkedHashMap<>();

		for (UnitNode source : graph.units) {
			for (BranchDto branch : safeBranches(source.unit)) {
				if (branch.getTargetMode() != ProcessBranchTargetMode.DEFER_TO_UNIT) {
					continue;
				}

				UnitNode target = graph.unitMap.get(branch.getTargetUnitKey());
				if (target == null) {
					continue;
				}

				targetStepSourceTargetMap.computeIfAbsent(target.stepKey, key -> new LinkedHashMap<>())
						.computeIfAbsent(source.unitKey, key -> new HashSet<>()).add(target.unitKey);
			}
		}

		for (Map.Entry<String, Map<String, Set<String>>> stepEntry : targetStepSourceTargetMap.entrySet()) {
			String targetStepKey = stepEntry.getKey();
			Map<String, Set<String>> sourceTargetMap = stepEntry.getValue();

			if (sourceTargetMap.size() <= 1) {
				continue;
			}

			Set<String> allTargetUnits = new HashSet<>();
			sourceTargetMap.values().forEach(allTargetUnits::addAll);

			if (allTargetUnits.size() > 1) {
				result.addError("DEFER_TO_UNIT 충돌 가능성이 있습니다. STEP [" + targetStepKey
						+ "]에 대해 서로 다른 이전 UNIT들이 서로 다른 대상 UNIT을 예약하고 있습니다. "
						+ "같은 STEP에서 예약 대상 UNIT은 하나로 통일하거나, 앞 단계 분기를 JUMP_UNIT으로 명확히 분리해주세요.");
			}
		}
	}

	private Map<String, List<String>> buildExecutionEdges(ProcessGraph graph) {
		Map<String, List<String>> edges = new LinkedHashMap<>();

		for (UnitNode unit : graph.units) {
			edges.putIfAbsent(unit.unitKey, new ArrayList<>());
		}

		if (graph.units.isEmpty()) {
			return edges;
		}

		Set<String> visitedStates = new HashSet<>();
		Deque<ExecutionState> queue = new ArrayDeque<>();

		UnitNode startUnit = graph.units.get(0);
		queue.add(new ExecutionState(startUnit.unitKey, new ArrayList<>()));

		while (!queue.isEmpty()) {
			ExecutionState state = queue.removeFirst();

			String stateKey = makeExecutionStateKey(state.unitKey, state.deferredUnitKeys);
			if (!visitedStates.add(stateKey)) {
				continue;
			}

			UnitNode source = graph.unitMap.get(state.unitKey);
			if (source == null) {
				continue;
			}

			List<ExecutionTransition> transitions = buildExecutionTransitions(graph, source, state.deferredUnitKeys);

			for (ExecutionTransition transition : transitions) {
				if (isBlank(transition.targetUnitKey)) {
					continue;
				}

				addEdge(edges, source.unitKey, transition.targetUnitKey);

				queue.addLast(new ExecutionState(transition.targetUnitKey, transition.nextDeferredUnitKeys));
			}
		}

		return edges;
	}

	private List<ExecutionTransition> buildExecutionTransitions(ProcessGraph graph, UnitNode source,
			List<String> deferredUnitKeys) {
		List<ExecutionTransition> transitions = new ArrayList<>();
		List<BranchDto> branches = safeBranches(source.unit);
		List<String> currentDeferredUnitKeys = normalizeDeferredUnitKeys(graph, deferredUnitKeys);

		/*
		 * 분기가 없으면 다음 STEP의 첫 번째 UNIT으로 이동합니다. 단, 다음 STEP에 예약된 DEFER UNIT이 있으면 1번 UNIT
		 * 대신 예약 UNIT으로 이동합니다.
		 */
		if (branches.isEmpty()) {
			addAutoNextTransition(graph, source, currentDeferredUnitKeys, transitions);
			return transitions;
		}

		boolean hasDefault = false;

		for (BranchDto branch : branches) {
			ProcessBranchTargetMode targetMode = branch.getTargetMode() == null ? ProcessBranchTargetMode.AUTO_NEXT
					: branch.getTargetMode();

			if (branch.getBranchType() == ProcessBranchType.DEFAULT) {
				hasDefault = true;
			}

			if (targetMode == ProcessBranchTargetMode.JUMP_UNIT) {
				transitions.add(new ExecutionTransition(branch.getTargetUnitKey(), currentDeferredUnitKeys));
				continue;
			}

			if (targetMode == ProcessBranchTargetMode.DEFER_TO_UNIT) {
				/*
				 * 핵심: DEFER는 source -> target 직접 이동이 아닙니다. target을 예약해두고, 현재 흐름은 AUTO_NEXT처럼 다음
				 * STEP으로 갑니다. 이후 target이 속한 STEP에 도착하면 해당 STEP의 1번 UNIT 대신 예약 UNIT으로 이동합니다.
				 */
				List<String> nextDeferredUnitKeys = addDeferredUnitKey(graph, currentDeferredUnitKeys,
						branch.getTargetUnitKey());

				addAutoNextTransition(graph, source, nextDeferredUnitKeys, transitions);
				continue;
			}

			addAutoNextTransition(graph, source, currentDeferredUnitKeys, transitions);
		}

		/*
		 * DEFAULT가 없으면 조건/선택에 매칭되지 않는 fallback도 다음 STEP의 첫 번째 UNIT입니다.
		 */
		if (!hasDefault) {
			addAutoNextTransition(graph, source, currentDeferredUnitKeys, transitions);
		}

		return transitions;
	}

	private void addAutoNextTransition(ProcessGraph graph, UnitNode source, List<String> deferredUnitKeys,
			List<ExecutionTransition> transitions) {
		AutoNextResolution resolution = resolveAutoNext(graph, source, deferredUnitKeys);

		if (isBlank(resolution.targetUnitKey)) {
			return;
		}

		transitions.add(new ExecutionTransition(resolution.targetUnitKey, resolution.nextDeferredUnitKeys));
	}

	private AutoNextResolution resolveAutoNext(ProcessGraph graph, UnitNode source, List<String> deferredUnitKeys) {
		List<String> nextDeferredUnitKeys = normalizeDeferredUnitKeys(graph, deferredUnitKeys);
		UnitNode nextStepFirstUnit = getNextStepFirstUnit(graph, source);

		if (nextStepFirstUnit == null) {
			return new AutoNextResolution(null, nextDeferredUnitKeys);
		}

		for (String deferredUnitKey : new ArrayList<>(nextDeferredUnitKeys)) {
			UnitNode deferredUnit = graph.unitMap.get(deferredUnitKey);

			if (deferredUnit == null) {
				continue;
			}

			if (deferredUnit.stepIndex == nextStepFirstUnit.stepIndex) {
				List<String> consumedDeferredUnitKeys = new ArrayList<>(nextDeferredUnitKeys);
				consumedDeferredUnitKeys.remove(deferredUnitKey);

				return new AutoNextResolution(deferredUnit.unitKey, consumedDeferredUnitKeys);
			}
		}

		return new AutoNextResolution(nextStepFirstUnit.unitKey, nextDeferredUnitKeys);
	}

	private UnitNode getNextStepFirstUnit(ProcessGraph graph, UnitNode source) {
		int nextStepIndex = source.stepIndex + 1;

		for (UnitNode candidate : graph.units) {
			if (candidate.stepIndex == nextStepIndex && candidate.unitIndex == 0) {
				return candidate;
			}
		}

		return null;
	}

	private static class ExecutionState {
	    private final String unitKey;
	    private final List<String> deferredUnitKeys;

	    private ExecutionState(String unitKey, List<String> deferredUnitKeys) {
	        this.unitKey = unitKey;
	        this.deferredUnitKeys = deferredUnitKeys == null
	                ? new ArrayList<>()
	                : new ArrayList<>(deferredUnitKeys);
	    }
	}

	private static class ExecutionTransition {
	    private final String targetUnitKey;
	    private final List<String> nextDeferredUnitKeys;

	    private ExecutionTransition(String targetUnitKey, List<String> nextDeferredUnitKeys) {
	        this.targetUnitKey = targetUnitKey;
	        this.nextDeferredUnitKeys = nextDeferredUnitKeys == null
	                ? new ArrayList<>()
	                : new ArrayList<>(nextDeferredUnitKeys);
	    }
	}

	private static class AutoNextResolution {
	    private final String targetUnitKey;
	    private final List<String> nextDeferredUnitKeys;

	    private AutoNextResolution(String targetUnitKey, List<String> nextDeferredUnitKeys) {
	        this.targetUnitKey = targetUnitKey;
	        this.nextDeferredUnitKeys = nextDeferredUnitKeys == null
	                ? new ArrayList<>()
	                : new ArrayList<>(nextDeferredUnitKeys);
	    }
	}
	
	private List<String> normalizeDeferredUnitKeys(ProcessGraph graph, List<String> deferredUnitKeys) {
		List<String> result = new ArrayList<>();

		if (deferredUnitKeys == null || deferredUnitKeys.isEmpty()) {
			return result;
		}

		for (String unitKey : deferredUnitKeys) {
			if (isBlank(unitKey)) {
				continue;
			}

			if (!graph.unitMap.containsKey(unitKey)) {
				continue;
			}

			if (result.contains(unitKey)) {
				continue;
			}

			result.add(unitKey);
		}

		return result;
	}

	private List<String> addDeferredUnitKey(ProcessGraph graph, List<String> deferredUnitKeys, String targetUnitKey) {
		List<String> result = normalizeDeferredUnitKeys(graph, deferredUnitKeys);

		if (isBlank(targetUnitKey)) {
			return result;
		}

		if (!graph.unitMap.containsKey(targetUnitKey)) {
			return result;
		}

		if (!result.contains(targetUnitKey)) {
			result.add(targetUnitKey);
		}

		return result;
	}

	private String makeExecutionStateKey(String unitKey, List<String> deferredUnitKeys) {
		return safeLabel(unitKey) + "__defer__"
				+ String.join("|", deferredUnitKeys == null ? List.of() : deferredUnitKeys);
	}

	private void validateCycle(ProcessGraph graph, Map<String, List<String>> edges,
			ProcessDefinitionValidationResult result) {
		Set<String> visited = new HashSet<>();
		Set<String> visiting = new HashSet<>();
		Deque<String> stack = new ArrayDeque<>();

		for (UnitNode unit : graph.units) {
			if (!visited.contains(unit.unitKey)) {
				if (dfsCycle(unit.unitKey, edges, visited, visiting, stack, result)) {
					return;
				}
			}
		}
	}

	private boolean dfsCycle(String unitKey, Map<String, List<String>> edges, Set<String> visited, Set<String> visiting,
			Deque<String> stack, ProcessDefinitionValidationResult result) {
		visiting.add(unitKey);
		stack.addLast(unitKey);

		for (String next : edges.getOrDefault(unitKey, List.of())) {
			if (isBlank(next)) {
				continue;
			}

			if (visiting.contains(next)) {
				List<String> path = new ArrayList<>(stack);
				path.add(next);

				result.addError("순환 분기가 발견되었습니다: " + String.join(" → ", path));
				return true;
			}

			if (!visited.contains(next)) {
				if (dfsCycle(next, edges, visited, visiting, stack, result)) {
					return true;
				}
			}
		}

		visiting.remove(unitKey);
		visited.add(unitKey);
		stack.removeLast();

		return false;
	}

	private void validateUnreachableUnits(ProcessGraph graph, Map<String, List<String>> edges,
			ProcessDefinitionValidationResult result) {
		if (graph.units.isEmpty()) {
			return;
		}

		String startUnitKey = graph.units.get(0).unitKey;

		Set<String> reachable = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>();

		reachable.add(startUnitKey);
		queue.add(startUnitKey);

		while (!queue.isEmpty()) {
			String current = queue.removeFirst();

			for (String next : edges.getOrDefault(current, List.of())) {
				if (isBlank(next)) {
					continue;
				}

				if (reachable.add(next)) {
					queue.addLast(next);
				}
			}
		}

		for (UnitNode unit : graph.units) {
			if (!reachable.contains(unit.unitKey)) {
				result.addError(formatUnit(unit) + "은 시작 UNIT에서 도달할 수 없습니다. 분기 연결 또는 STEP/UNIT 순서를 확인해주세요.");
			}
		}
	}

	private ParsedCondition parseCondition(String conditionJson) {
		ParsedCondition parsed = new ParsedCondition();

		if (isBlank(conditionJson)) {
			parsed.valid = false;
			return parsed;
		}

		try {
			Map<String, Object> map = objectMapper.readValue(conditionJson, new TypeReference<>() {
			});

			parsed.valid = true;
			parsed.mode = String.valueOf(map.getOrDefault("mode", "ALL"));

			List<Map<String, Object>> rows = objectMapper.convertValue(map.getOrDefault("conditions", List.of()),
					new TypeReference<>() {
					});

			for (Map<String, Object> rowMap : rows) {
				ConditionRow row = new ConditionRow();
				row.fieldKey = rowMap.get("fieldKey") == null ? null : String.valueOf(rowMap.get("fieldKey"));
				row.operator = rowMap.get("operator") == null ? null : String.valueOf(rowMap.get("operator"));
				row.value = rowMap.get("value") == null ? null : new BigDecimal(String.valueOf(rowMap.get("value")));
				parsed.conditions.add(row);
			}

			return parsed;
		} catch (Exception e) {
			parsed.valid = false;
			return parsed;
		}
	}

	private ConditionShape toConditionShape(ParsedCondition condition) {
		ConditionShape shape = new ConditionShape();

		if (!"ALL".equalsIgnoreCase(condition.mode)) {
			return shape;
		}

		for (ConditionRow row : condition.conditions) {
			Interval interval = shape.fieldIntervals.computeIfAbsent(row.fieldKey, key -> new Interval());

			if ("GT".equals(row.operator)) {
				interval.applyLower(row.value, false);
			} else if ("GTE".equals(row.operator)) {
				interval.applyLower(row.value, true);
			} else if ("LT".equals(row.operator)) {
				interval.applyUpper(row.value, false);
			} else if ("LTE".equals(row.operator)) {
				interval.applyUpper(row.value, true);
			} else if ("EQ".equals(row.operator)) {
				interval.applyLower(row.value, true);
				interval.applyUpper(row.value, true);
			}
		}

		for (Interval interval : shape.fieldIntervals.values()) {
			if (interval.isContradiction()) {
				shape.contradiction = true;
				break;
			}
		}

		return shape;
	}

	private Set<String> getNumberFieldKeys(UnitDto unit) {
		QuestionDto question = unit.getQuestion();
		if (question == null || question.getFields() == null) {
			return Set.of();
		}

		Set<String> keys = new HashSet<>();

		for (AnswerFieldDto field : question.getFields()) {
			if (field.getInputValueType() == ProcessInputValueType.NUMBER) {
				keys.add(field.getFieldKey());
			}
		}

		return keys;
	}

	private String getNormalNextUnitKey(ProcessGraph graph, UnitNode source) {
		int nextStepIndex = source.stepIndex + 1;

		for (UnitNode candidate : graph.units) {
			if (candidate.stepIndex == nextStepIndex && candidate.unitIndex == 0) {
				return candidate.unitKey;
			}
		}

		return null;
	}

	private void addEdge(Map<String, List<String>> edges, String from, String to) {
		if (isBlank(from) || isBlank(to)) {
			return;
		}

		edges.computeIfAbsent(from, key -> new ArrayList<>());

		if (!edges.get(from).contains(to)) {
			edges.get(from).add(to);
		}
	}

	private List<BranchDto> safeBranches(UnitDto unit) {
		if (unit == null || unit.getBranches() == null) {
			return List.of();
		}

		return unit.getBranches().stream().sorted(Comparator.comparingInt(BranchDto::getPriority)).toList();
	}

	private String formatUnit(UnitNode node) {
		return "STEP [" + safeLabel(node.stepTitle) + "] / UNIT [" + safeLabel(node.unitTitle) + "]";
	}

	private String safeLabel(String value) {
		return isBlank(value) ? "-" : value.trim();
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static class ProcessGraph {
		private final List<UnitNode> units = new ArrayList<>();
		private final Map<String, UnitNode> unitMap = new LinkedHashMap<>();
	}

	private static class UnitNode {
		private StepDto step;
		private UnitDto unit;
		private String stepKey;
		private String unitKey;
		private String stepTitle;
		private String unitTitle;
		private int stepIndex;
		private int unitIndex;
		private int globalIndex;
	}

	private static class ParsedCondition {
		private boolean valid;
		private String mode = "ALL";
		private List<ConditionRow> conditions = new ArrayList<>();
	}

	private static class ConditionRow {
		private String fieldKey;
		private String operator;
		private BigDecimal value;
	}

	private static class ConditionBranchInfo {
		private final BranchDto branch;
		private final ParsedCondition condition;
		private final ConditionShape shape;

		private ConditionBranchInfo(BranchDto branch, ParsedCondition condition, ConditionShape shape) {
			this.branch = branch;
			this.condition = condition;
			this.shape = shape;
		}
	}

	private static class ConditionIntervalInfo {
		private final BranchDto branch;
		private final Interval interval;

		private ConditionIntervalInfo(BranchDto branch, Interval interval) {
			this.branch = branch;
			this.interval = interval;
		}
	}

	private static class ConditionShape {
		private final Map<String, Interval> fieldIntervals = new HashMap<>();
		private boolean contradiction;
	}

	private static class Interval {
		private BigDecimal lower;
		private boolean lowerInclusive = true;
		private BigDecimal upper;
		private boolean upperInclusive = true;

		private void applyLower(BigDecimal value, boolean inclusive) {
			if (value == null) {
				return;
			}

			if (lower == null) {
				lower = value;
				lowerInclusive = inclusive;
				return;
			}

			int compare = value.compareTo(lower);

			if (compare > 0 || compare == 0 && !inclusive) {
				lower = value;
				lowerInclusive = inclusive;
			}
		}

		private void applyUpper(BigDecimal value, boolean inclusive) {
			if (value == null) {
				return;
			}

			if (upper == null) {
				upper = value;
				upperInclusive = inclusive;
				return;
			}

			int compare = value.compareTo(upper);

			if (compare < 0 || compare == 0 && !inclusive) {
				upper = value;
				upperInclusive = inclusive;
			}
		}

		private boolean isContradiction() {
			if (lower == null || upper == null) {
				return false;
			}

			int compare = lower.compareTo(upper);

			if (compare > 0) {
				return true;
			}

			return compare == 0 && (!lowerInclusive || !upperInclusive);
		}

		private boolean overlaps(Interval other) {
			if (this.upper != null && other.lower != null) {
				int compare = this.upper.compareTo(other.lower);

				if (compare < 0) {
					return false;
				}

				if (compare == 0 && (!this.upperInclusive || !other.lowerInclusive)) {
					return false;
				}
			}

			if (other.upper != null && this.lower != null) {
				int compare = other.upper.compareTo(this.lower);

				if (compare < 0) {
					return false;
				}

				if (compare == 0 && (!other.upperInclusive || !this.lowerInclusive)) {
					return false;
				}
			}

			return true;
		}
	}
}