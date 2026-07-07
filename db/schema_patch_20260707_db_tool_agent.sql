-- 2026-07-07 OpenAI Function Tool 기반 RAG DB Agent 확장
-- 전제: 001~019 스키마가 적용되어 있어야 합니다.
-- 목적:
-- 1) OpenAI Responses API의 실제 function tool 호출 이력을 보존합니다.
-- 2) Agent 실행별 모델/response/tool turn/usage를 추적합니다.
-- 3) DB 자격증명은 모델에 전달하지 않고 Spring Boot 내부 Tool Gateway만 사용합니다.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE rag_agent_run
    ADD COLUMN IF NOT EXISTS agent_mode varchar(60) NOT NULL DEFAULT 'OPENAI_FUNCTION_TOOLS',
    ADD COLUMN IF NOT EXISTS model_name varchar(120),
    ADD COLUMN IF NOT EXISTS last_response_id varchar(160),
    ADD COLUMN IF NOT EXISTS tool_turn_count integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS usage_json jsonb NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_rag_agent_run_response_id
    ON rag_agent_run(last_response_id)
    WHERE last_response_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS rag_agent_tool_call (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id uuid NULL REFERENCES rag_agent_run(id) ON DELETE SET NULL,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid NULL,
    response_id varchar(160),
    call_id varchar(200) NOT NULL,
    turn_no integer NOT NULL,
    tool_name varchar(160) NOT NULL,
    arguments_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    result_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(30) NOT NULL DEFAULT 'SUCCESS',
    duration_ms bigint NOT NULL DEFAULT 0,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rag_agent_tool_call_run_call
    ON rag_agent_tool_call(run_id, call_id)
    WHERE run_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rag_agent_tool_call_run
    ON rag_agent_tool_call(run_id, turn_no ASC, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_rag_agent_tool_call_scope
    ON rag_agent_tool_call(project_id, version_id, tool_name, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_agent_tool_call_response
    ON rag_agent_tool_call(response_id, created_at ASC)
    WHERE response_id IS NOT NULL;

COMMENT ON TABLE rag_agent_tool_call IS
'OpenAI Responses API function tool 호출의 입력, 결과, 상태, 소요시간을 기록하는 감사 로그';
COMMENT ON COLUMN rag_agent_tool_call.arguments_json IS
'모델이 function call로 제출한 인자. DB 비밀번호와 DataSource 정보는 포함하지 않습니다.';
COMMENT ON COLUMN rag_agent_tool_call.result_json IS
'Spring Boot 내부 DB Tool Gateway가 실행한 결과 또는 오류';
COMMENT ON COLUMN rag_agent_tool_call.turn_no IS
'한 Agent 실행 안에서 OpenAI tool loop의 반복 차수';

-- 전역 스키마 사전에서 신규 감사 테이블 설명을 최신화합니다.
DELETE FROM rag_agent_schema_note
WHERE project_id IS NULL
  AND version_id IS NULL
  AND note_kind = 'TABLE'
  AND table_name = 'rag_agent_tool_call';

INSERT INTO rag_agent_schema_note(
    note_kind, table_name, object_name, title, description,
    usage_guide, when_to_read, when_to_write, risk_note, priority, active
) VALUES (
    'TABLE',
    'rag_agent_tool_call',
    NULL,
    'Agent Function Tool 호출 로그',
    'OpenAI 모델이 선택한 DB 메타데이터/조회/변경계획/최종답변 도구의 입력과 실행 결과를 보존합니다.',
    'Agent가 어떤 테이블을 왜 탐색했고 어떤 오류 후 재시도했는지 실행 차수 순서대로 확인합니다.',
    'Agent 답변 근거, 도구 선택, SQL 이전 탐색 과정, 장애 원인을 감사할 때 조회합니다.',
    'Spring Boot의 RagAgentToolAuditService가 자동 기록하며 직접 수정하지 않는 것이 원칙입니다.',
    'arguments_json/result_json에 학습 원문 또는 조회 결과가 포함될 수 있으므로 관리자 권한으로만 노출해야 합니다.',
    110,
    true
);

-- 기존 객체와 신규 객체에 대한 설명 주석입니다.
COMMENT ON COLUMN rag_agent_run.agent_mode IS 'Agent 실행 모드. 기본값은 OPENAI_FUNCTION_TOOLS';
COMMENT ON COLUMN rag_agent_run.model_name IS '실제 Agent 실행에 사용한 OpenAI 모델명';
COMMENT ON COLUMN rag_agent_run.last_response_id IS '가장 최근 OpenAI Responses API response id';
COMMENT ON COLUMN rag_agent_run.tool_turn_count IS '실제 function tool loop 반복 횟수';
COMMENT ON COLUMN rag_agent_run.usage_json IS '마지막 Responses API usage 정보';
