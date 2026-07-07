-- 2026-07-07 GPT Direct SQL Agent Schema Dictionary
-- 목적:
-- 1) GPT가 information_schema 컬럼명만 보고 추측하지 않도록 RAG 테이블의 업무 의미를 DB에 저장합니다.
-- 2) RagAgentSchemaService가 이 테이블을 읽어 GPT 프롬프트 context.schema.tables에 설명/사용법/주의사항을 함께 제공합니다.
-- 3) 기존 데이터는 삭제하지 않습니다.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS rag_agent_schema_note (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    note_kind varchar(40) NOT NULL DEFAULT 'TABLE',
    table_name varchar(160) NOT NULL,
    object_name varchar(160),
    title varchar(300),
    description text,
    usage_guide text,
    when_to_read text,
    when_to_write text,
    risk_note text,
    example_sql text,
    priority integer NOT NULL DEFAULT 100,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(project_id, version_id, note_kind, table_name, object_name)
);

CREATE INDEX IF NOT EXISTS idx_rag_agent_schema_note_scope
    ON rag_agent_schema_note(project_id, version_id, note_kind, table_name, active);

CREATE INDEX IF NOT EXISTS idx_rag_agent_schema_note_table
    ON rag_agent_schema_note(table_name, note_kind, active, priority);

CREATE OR REPLACE FUNCTION rag_agent_schema_note_touch_updated_at()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_rag_agent_schema_note_touch_updated_at ON rag_agent_schema_note;
CREATE TRIGGER trg_rag_agent_schema_note_touch_updated_at
BEFORE UPDATE ON rag_agent_schema_note
FOR EACH ROW
EXECUTE FUNCTION rag_agent_schema_note_touch_updated_at();


DELETE FROM rag_agent_schema_note
WHERE project_id IS NULL
  AND version_id IS NULL
  AND note_kind = 'TABLE';

INSERT INTO rag_agent_schema_note(note_kind, table_name, object_name, title, description, usage_guide, when_to_read, when_to_write, risk_note, priority)
VALUES
('TABLE','rag_project',NULL,'RAG 프로젝트','RAG 프로젝트의 최상위 단위입니다. 여러 학습 버전을 가질 수 있습니다.','프로젝트 목록, 활성 버전, 기본 모델 설정 확인에 사용합니다.','프로젝트/버전 구조 또는 현재 active_version 확인이 필요할 때 조회합니다.','일반 Agent가 직접 변경하지 않는 것이 원칙입니다.','잘못 변경하면 전체 학습/챗봇 기준 버전이 바뀔 수 있습니다.',10),
('TABLE','rag_project_version',NULL,'RAG 프로젝트 버전','학습 데이터와 챗봇 기준을 분리하는 버전 테이블입니다.','모든 RAG 업무 데이터는 보통 project_id/version_id 범위로 조회합니다.','현재 버전 상태, 학습 방향, 공개 여부 확인이 필요할 때 조회합니다.','버전 publish/상태 변경은 기존 서비스 로직을 우선 사용합니다.','잘못 변경하면 조회 기준과 학습 기준이 섞일 수 있습니다.',10),
('TABLE','rag_document',NULL,'학습 원문 문서','파일 업로드, 대화 학습, Agent staging 원문이 문서 단위로 저장됩니다.','파일 원문, 출처, topic, source_type, raw_text 확인에 사용합니다.','전체 데이터 설명, 파일 학습 결과 확인, 원문 추적이 필요할 때 조회합니다.','새 자연어 지식 저장 시 insert 가능합니다. 보통 INSERT_KNOWLEDGE_NODE가 document/chunk/node를 함께 생성합니다.','raw_text가 길 수 있으므로 필요한 범위만 조회해야 합니다.',20),
('TABLE','rag_chunk',NULL,'문서 청크','rag_document 원문을 검색/조회 단위로 나눈 청크입니다.','긴 파일 원문을 부분 조회하거나 Agent staging 파일 전문을 확인할 때 사용합니다.','업로드 파일 preview가 부족하거나 특정 문구를 찾아야 할 때 조회합니다.','document 생성과 함께 chunk insert가 필요합니다.','content가 길 수 있으므로 LIMIT와 조건을 사용해야 합니다.',20),
('TABLE','rag_knowledge_node',NULL,'지식 노드','GPT가 해석한 핵심 지식입니다. 제품 규칙, 발주 프로세스, 구조화 JSON, 원문 요약이 저장됩니다.','전체 지식 요약, 제품 조건, 발주 절차, 규칙 확인의 1순위 테이블입니다.','전체 데이터 설명, 제품 정보, 주문 프로세스, 기존 학습 확인 시 조회합니다.','자연어 학습 저장은 INSERT_KNOWLEDGE_NODE 또는 명확한 INSERT_SQL로 처리합니다.','중복 node_key/topic 충돌 가능성을 확인해야 합니다.',30),
('TABLE','rag_knowledge_artifact',NULL,'구조화 산출물','엑셀/가격표/제약표 같은 구조화 자료의 상위 메타 정보입니다.','구조화 테이블/가격표가 어떤 파일에서 왔는지 확인합니다.','엑셀 학습 목록, active artifact, 교체 대상 확인 시 조회합니다.','교체 시 기존 active=false/status=SUPERSEDED 처리 후 새 artifact를 추가합니다.','교체 범위가 넓으면 확인 후 적용해야 합니다.',40),
('TABLE','rag_structured_table',NULL,'구조화 테이블','엑셀 등 표 형태 지식의 헤더, 역할, 상태를 저장합니다.','제품표/가격표/색상표 등 표 데이터의 상위 단위입니다.','엑셀 데이터 목록, 컬럼 구조, semantic_role 확인에 사용합니다.','신규 표 학습 또는 교체 시 insert/update 합니다.','row와 table의 active/status 일관성을 맞춰야 합니다.',40),
('TABLE','rag_structured_table_row',NULL,'구조화 테이블 행','구조화 테이블의 실제 row_json입니다. 제품명/색상/사이즈/가격 원본 행이 들어갑니다.','제품 가능 조건, 엑셀 원본값, 전체 제품 목록 조회에 매우 중요합니다.','제품 모든 정보, 색상/사이즈/중분류/단가 확인 시 조회합니다.','대량 교체는 기존 row soft delete 후 새 row insert가 안전합니다.','row_json 키 이름이 파일마다 다를 수 있으므로 샘플 확인이 필요합니다.',41),
('TABLE','rag_structured_pricing_rule',NULL,'구조화 가격 규칙','자연어/엑셀에서 추출한 제품 가격 계산 규칙입니다.','기준금액, 기준치수, 증가단위, 옵션값 기반 견적 계산에 사용합니다.','가격/견적/단가 계산 요청에서 우선 조회합니다.','가격 규칙 학습/수정/교체 시 ChangeSet으로 저장합니다.','가격 변경은 견적 결과에 직접 영향이 있어 중복 규칙을 반드시 확인해야 합니다.',50),
('TABLE','rag_structured_override_rule',NULL,'구조화 예외/수정 규칙','원본 학습 데이터보다 우선 적용되는 허용/금지/수정 규칙입니다.','기존 엑셀을 직접 고치기보다 최신 예외를 반영할 때 사용합니다.','가능 색상 변경, 특정 조건 금지, 기존 규칙 수정 요청에서 조회합니다.','명확한 수정 요청은 새 override rule insert가 안전합니다.','원본과 override 충돌 시 override 우선순위를 설명해야 합니다.',50),
('TABLE','rag_price_matrix',NULL,'가격 매트릭스','행/열 기반 가격표의 상위 테이블입니다.','정확한 가격표 계산에 사용합니다.','가격표 기반 견적, 행/열 조건 확인 시 조회합니다.','가격표 교체 시 기존 matrix 비활성화 후 새 matrix/cell insert가 안전합니다.','가격표 전체 교체는 영향이 크므로 확인이 필요합니다.',55),
('TABLE','rag_price_matrix_cell',NULL,'가격 매트릭스 셀','가격표의 실제 셀 값입니다.','치수/옵션 교차 조건별 가격 확인에 사용합니다.','특정 사이즈/옵션의 정확 가격표 값을 찾을 때 조회합니다.','matrix와 함께 insert/update합니다.','셀 기준 축 해석을 잘못하면 가격이 틀릴 수 있습니다.',56),
('TABLE','rag_dialog_rule',NULL,'발주 대화 규칙','발주 상담 중 질문 순서, 필수값, 조건, 검증 규칙을 저장합니다.','챗봇이 다음에 무엇을 물어볼지 판단하는 핵심 테이블입니다.','발주 프로세스, 필요 요소, 누락 입력, 질문 흐름 요청에서 조회합니다.','대화흐름 학습/수정 시 ChangeSet으로 저장합니다.','잘못 변경하면 소비자 상담 흐름이 꼬일 수 있습니다.',60),
('TABLE','rag_canonical_dataset',NULL,'정본 데이터셋','여러 지식/표/규칙을 병합해 만든 정본 데이터셋입니다.','현재 챗봇이 따라야 할 최신 기준 묶음을 확인합니다.','전체 데이터 설명, 정본 빌드 상태 확인 시 조회합니다.','정본은 보통 별도 빌드 서비스가 생성합니다.','개별 row 직접 수정보다 재빌드를 고려해야 합니다.',70),
('TABLE','rag_canonical_entity',NULL,'정본 엔티티','제품/옵션/시리즈 등 정본화된 엔티티입니다.','제품명, 별칭, 엔티티 목록 조회에 사용합니다.','제품 목록, 특정 제품 존재 여부, alias 확인 시 조회합니다.','정본 엔티티 직접 수정은 신중해야 합니다.','다른 canonical fact/pricing/dialog와 연결되어 있습니다.',71),
('TABLE','rag_canonical_fact',NULL,'정본 속성/조건','정본 엔티티의 속성, 가능 조건, 제약, 설명 fact입니다.','색상 가능 여부, 사이즈 제한, 옵션 조건 등 확인에 사용합니다.','제품 조건/가능 여부/제약 질문에서 조회합니다.','정본 fact 직접 변경은 출처/충돌을 확인해야 합니다.','중복 fact가 있으면 우선순위와 출처를 비교해야 합니다.',72),
('TABLE','rag_canonical_pricing_rule',NULL,'정본 가격 규칙','정본화된 가격 규칙입니다.','견적 계산 시 structured pricing보다 최신 기준일 수 있습니다.','가격/견적 계산 요청에서 우선 조회합니다.','보통 정본 빌드 결과로 생성됩니다.','잘못 수정하면 모든 견적에 영향이 있습니다.',73),
('TABLE','rag_canonical_dialog_flow',NULL,'정본 대화 흐름','정본화된 발주 질문 플로우입니다.','챗봇 대화형 발주 프로세스에 사용합니다.','다음 질문/필수값/주문 흐름 요청에서 조회합니다.','보통 정본 빌드 결과로 생성됩니다.','질문 순서 오류가 상담 품질에 직접 영향이 있습니다.',74),
('TABLE','rag_conversation_working_memory',NULL,'대화 작업 메모리','현재 세션에서 직전 제품, 치수, 색상, 의도, 견적 문맥을 저장합니다.','생략된 질문을 이어받아 해석할 때 사용합니다.','그럼 1500은?, 그 색상도 돼? 같은 후속 질문에서 조회합니다.','세션 문맥 갱신 시 update/insert 가능합니다.','오래된 문맥을 현재 질문에 잘못 적용하지 않도록 expires_at/updated_at을 확인합니다.',80),
('TABLE','rag_learning_session',NULL,'학습 세션','관리자 학습 화면의 세션입니다.','학습 주제, pending resolution, 대화 흐름을 확인합니다.','학습 이력/세션 상태 확인 시 조회합니다.','세션 생성/상태 변경은 기존 서비스 사용을 우선합니다.','세션 상태 변경은 화면 표시와 연결됩니다.',90),
('TABLE','rag_learning_message',NULL,'학습 메시지','관리자 학습 대화 메시지입니다.','최근 학습 대화 문맥 확인에 사용합니다.','학습 중 이전 지시를 확인할 때 조회합니다.','메시지는 서비스가 자동 저장합니다.','중복 저장에 주의해야 합니다.',91),
('TABLE','rag_learning_job',NULL,'비동기 학습 작업','긴 입력/파일 학습 작업 진행 상태입니다.','작업 진행률, 실패 사유, 결과 확인에 사용합니다.','파일 학습 상태/에러 확인 시 조회합니다.','작업 상태 변경은 비동기 서비스가 담당합니다.','임의 변경하면 화면 상태가 틀어질 수 있습니다.',92),
('TABLE','rag_chat_session',NULL,'챗봇 세션','소비자/상담 챗봇 대화 세션입니다.','챗봇 대화 범위와 프로젝트/버전 확인에 사용합니다.','대화 이력 또는 세션 문맥 확인 시 조회합니다.','세션 생성은 기존 서비스 사용을 우선합니다.','세션 삭제는 대화 이력 손실을 유발합니다.',100),
('TABLE','rag_chat_message',NULL,'챗봇 메시지','챗봇 사용자/어시스턴트 메시지입니다.','최근 대화 문맥과 실제 응답 이력 확인에 사용합니다.','후속 질문 문맥 확인, 답변 품질 추적 시 조회합니다.','메시지는 서비스가 자동 저장합니다.','개인정보가 포함될 수 있으므로 답변 노출에 주의합니다.',101),
('TABLE','rag_agent_run',NULL,'Agent 실행 로그','GPT SQL Agent의 각 실행 단위입니다.','어떤 요청이 어떤 context로 처리됐는지 추적합니다.','Agent 오류/처리결과/전체 데이터 설명 실패 원인 확인 시 조회합니다.','서비스가 자동 insert/update합니다.','직접 수정하지 않는 것이 원칙입니다.',110),
('TABLE','rag_agent_sql_query',NULL,'Agent SQL 로그','GPT가 요청한 SQL, 실행 SQL, 결과/에러 로그입니다.','Agent가 실제로 무엇을 조회했는지 추적합니다.','SQL 실패 원인, 조회 근거 확인 시 조회합니다.','서비스가 자동 기록합니다.','result_json이 클 수 있습니다.',111),
('TABLE','rag_agent_change_set',NULL,'Agent 변경 계획','GPT가 만든 저장/수정/삭제 계획의 헤더입니다.','자동 저장/보류/검토 대상 확인에 사용합니다.','변경이 왜 보류되었는지, 적용됐는지 확인할 때 조회합니다.','서비스가 생성하고 apply API가 적용합니다.','검토 없이 강제 적용하면 지식 충돌이 생길 수 있습니다.',112),
('TABLE','rag_agent_change_item',NULL,'Agent 변경 항목','ChangeSet 안의 실제 변경 SQL/지식 insert 항목입니다.','무엇을 insert/update/delete하려 했는지 확인합니다.','변경계획 상세 검토 시 조회합니다.','서비스가 생성하고 적용 상태를 갱신합니다.','write_sql 검증 실패 항목은 수정 후 재생성이 필요합니다.',113),
('TABLE','rag_asset',NULL,'파일/이미지 자산','제품/지식에 연결된 이미지/파일 자산입니다.','이미지/파일 연결, 원본 파일 위치 확인에 사용합니다.','제품 이미지/첨부파일 조회 시 확인합니다.','파일 저장 서비스가 생성합니다.','물리 파일과 DB 메타가 함께 맞아야 합니다.',120),
('TABLE','rag_entity_asset_link',NULL,'엔티티 자산 연결','제품/지식 엔티티와 asset 연결 정보입니다.','제품 조회 시 관련 이미지/파일을 함께 찾습니다.','제품 자료/이미지 연결 확인 시 조회합니다.','자산 연결 요청에서 insert 가능합니다.','잘못 연결하면 다른 제품에 파일이 노출됩니다.',121);



-- 001~018 전체 스키마를 대조해 기존 사전에 빠져 있던 테이블 설명을 보완합니다.
INSERT INTO rag_agent_schema_note(note_kind, table_name, object_name, title, description, usage_guide, when_to_read, when_to_write, risk_note, priority)
VALUES
('TABLE','rag_agent_file_stage',NULL,'Agent 파일 스테이징','DB Tool Agent가 업로드 파일을 해석하기 전에 파일 메타데이터와 제한된 미리보기를 실행 단위로 기록합니다.','run_id와 document/chunk staging 결과를 연결해 파일 처리 과정을 추적합니다.','업로드 파일이 어떤 Agent 실행에서 어떻게 준비되었는지 확인할 때 조회합니다.','RagSqlAgentService가 자동 기록하며 GPT 변경 대상으로 사용하지 않습니다.','preview_text에 원문 일부가 포함될 수 있어 관리자 범위에서만 노출해야 합니다.',109),
('TABLE','rag_agent_schema_note',NULL,'Agent 스키마 업무 사전','테이블과 컬럼의 기술 메타데이터에 업무 의미, 조회 시점, 변경 주의사항을 덧붙이는 설명 사전입니다.','GPT가 테이블명을 고정 매핑하지 않고 업무 용어와 DB 객체를 연결할 때 사용합니다.','catalog 검색과 테이블 의미 판단에 사용합니다.','관리자가 검토한 설명만 추가·수정하며 일반 Agent는 직접 변경하지 않습니다.','잘못된 설명은 GPT의 테이블 선택 전체를 오도할 수 있습니다.',108),
('TABLE','rag_canonical_change_event',NULL,'정본 변경 이벤트','정본 데이터 변경 전후 상태, 변경 지시, 영향 범위를 이벤트로 기록합니다.','정본이 언제 왜 바뀌었는지 before_json/after_json/impact_json으로 추적합니다.','정본 수정·교체 이력과 영향 분석이 필요할 때 조회합니다.','정본 엔진 또는 검증된 변경 서비스가 기록하는 것이 원칙입니다.','감사 이력을 임의 수정하면 변경 추적이 불가능해집니다.',75),
('TABLE','rag_canonical_job',NULL,'정본 비동기 작업','정본 빌드·미리보기·견적 등 비동기 작업의 진행 상태와 결과를 저장합니다.','job_type, 진행률, 상태, 결과/오류를 기준으로 정본 처리 흐름을 확인합니다.','정본 생성 작업 상태 또는 실패 원인을 확인할 때 조회합니다.','작업 서비스가 상태를 갱신하며 GPT가 직접 변경하지 않습니다.','임의 상태 변경은 실제 작업과 화면 표시를 불일치시킵니다.',76),
('TABLE','rag_canonical_job_log',NULL,'정본 작업 단계 로그','rag_canonical_job의 단계별 메시지와 부가정보를 시간순으로 저장합니다.','상위 canonical job과 조인해 상세 진행 과정과 실패 지점을 확인합니다.','정본 작업 장애 분석과 진행 내역 확인 시 조회합니다.','작업 서비스가 자동 기록합니다.','직접 project/version 컬럼이 없어 반드시 상위 job 범위 조인이 필요합니다.',77),
('TABLE','rag_canonical_nl_parse_log',NULL,'자연어 가격 해석 로그','자연어 견적 요청이 어떤 구조화 값과 규칙으로 해석되었는지 기록합니다.','제품명·치수·옵션 등 자연어 파싱 결과와 오류를 검증합니다.','가격 요청 오해석, 누락 변수, 파싱 품질을 분석할 때 조회합니다.','자연어 가격 서비스가 자동 기록합니다.','사용자 입력이나 개인정보가 포함될 수 있어 응답 노출에 주의해야 합니다.',78),
('TABLE','rag_canonical_quality_issue',NULL,'정본 품질 문제','정본 빌드 과정에서 발견한 충돌, 누락, 중복, 불확실성을 심각도와 함께 저장합니다.','정본 데이터의 신뢰도를 판단하고 수정 전 해결해야 할 문제를 확인합니다.','제품정보나 가격 규칙의 충돌 여부를 확인할 때 조회합니다.','품질 검증 서비스가 생성·해결 상태를 관리합니다.','미해결 이슈를 무시하면 잘못된 정본 또는 견적이 사용될 수 있습니다.',79),
('TABLE','rag_canonical_quote_log',NULL,'정본 견적 계산 로그','정본 규칙으로 계산한 견적 입력, 적용 규칙, 결과를 기록합니다.','가격 답변의 재현과 적용 규칙 추적에 사용합니다.','과거 견적 결과나 계산 근거를 확인할 때 조회합니다.','견적 서비스가 자동 기록합니다.','고객 입력과 가격정보가 포함될 수 있어 소비자 간 교차 노출을 막아야 합니다.',81),
('TABLE','rag_entity_alias',NULL,'엔티티 별칭','제품·시리즈·옵션의 표기 차이와 별칭을 정규화된 엔티티 키에 연결합니다.','사용자 표현과 canonical entity/knowledge node를 연결할 때 사용합니다.','제품명이 약칭·오타·다른 표기로 입력되었을 때 조회합니다.','별칭 추가 전 동일 표현의 중복·충돌을 확인한 뒤 ChangeSet으로 저장합니다.','한 별칭이 여러 엔티티에 연결되면 잘못된 제품을 선택할 수 있습니다.',82),
('TABLE','rag_gpt_final_answer_log',NULL,'GPT 최종 답변 로그','DB 조회와 계산 결과를 바탕으로 생성된 최종 사용자 답변과 근거를 기록합니다.','답변 품질, 근거, 실패 또는 보정 과정을 감사할 때 사용합니다.','과거 응답 품질과 근거 추적이 필요할 때 조회합니다.','최종 답변 합성 서비스가 자동 기록합니다.','대화 내용과 조회 결과가 포함될 수 있어 관리자 범위로 제한해야 합니다.',114),
('TABLE','rag_inquiry',NULL,'사용자 문의','챗봇에서 별도 상담 또는 후속 연락이 필요한 문의 정보를 저장합니다.','회사·성명·연락처·메모 등 문의 접수 내용을 관리합니다.','관리자가 접수된 상담 요청을 확인할 때 조회합니다.','문의 접수 서비스만 저장하고 일반 지식 Agent는 변경하지 않습니다.','개인정보가 포함될 수 있으므로 GPT 자유 조회와 소비자 노출을 제한해야 합니다.',102),
('TABLE','rag_interaction_event',NULL,'AI 상호작용 이벤트','학습·조회·변경 등 AI 상호작용의 입력, 결과, 상태와 Agent 연결정보를 기록합니다.','사용자 요청이 어느 처리기와 Agent 실행으로 이어졌는지 추적합니다.','처리 흐름 감사, 오류 분석, 기존 의미판정 결과 확인에 사용합니다.','상호작용 서비스가 자동 기록합니다.','원문과 내부 결과가 포함될 수 있어 직접 수정하거나 소비자에게 노출하지 않습니다.',115),
('TABLE','rag_knowledge_query_cache',NULL,'지식 조회 캐시','반복되는 지식 조회 결과를 키와 만료시각 기준으로 캐시합니다.','동일 질문 또는 동일 조회 계획의 재사용 가능 여부를 확인합니다.','캐시 적중/오염/만료 문제를 진단할 때 조회합니다.','캐시 서비스가 생성·갱신·만료 처리합니다.','오래되거나 다른 범위의 캐시를 사용하면 잘못된 답변이 나올 수 있습니다.',83),
('TABLE','rag_learning_job_file',NULL,'학습 작업 파일','비동기 학습 작업에 포함된 각 업로드 파일의 메타데이터와 처리 상태를 저장합니다.','상위 learning job과 조인해 파일별 처리 결과를 확인합니다.','특정 파일의 파싱·학습 실패를 확인할 때 조회합니다.','학습 작업 서비스가 자동 기록합니다.','직접 project/version 컬럼이 없어 반드시 상위 job 범위 조인이 필요합니다.',93),
('TABLE','rag_learning_job_log',NULL,'학습 작업 로그','비동기 학습 작업의 단계별 진행 메시지와 상세정보를 시간순으로 저장합니다.','상위 learning job과 조인해 장시간 학습의 진행 및 오류를 추적합니다.','학습 작업 정체나 실패 원인을 분석할 때 조회합니다.','학습 작업 서비스가 자동 기록합니다.','직접 project/version 컬럼이 없어 반드시 상위 job 범위 조인이 필요합니다.',94),
('TABLE','rag_reset_event',NULL,'학습 초기화 이벤트','특정 프로젝트·버전·주제에 대한 지식 초기화 요청과 처리 결과를 기록합니다.','엑셀 재업로드나 주제 교체 시 어떤 범위가 초기화되었는지 확인합니다.','데이터가 사라졌거나 교체된 이유를 추적할 때 조회합니다.','초기화 서비스가 실행 결과를 기록하며 일반 Agent는 변경하지 않습니다.','초기화 이력은 데이터 손실 원인 추적에 필요하므로 삭제하면 안 됩니다.',95),
('TABLE','rag_semantic_resolution_event',NULL,'의미판정 보정 이벤트','초기 계획·조회 결과와 GPT 보정 계획을 비교해 최종 사용자 의도를 확정한 기록입니다.','기존 의미판정이 왜 수정되었는지 first/final plan과 retrieval을 비교합니다.','전체 제품 조회 같은 의도가 오해석된 원인을 분석할 때 조회합니다.','의미판정 오케스트레이터가 자동 기록합니다.','내부 계획 JSON과 사용자 입력이 포함되어 관리자 진단용으로만 사용해야 합니다.',116);

COMMENT ON TABLE rag_agent_schema_note IS 'GPT Direct SQL Agent가 RAG 테이블의 업무 의미를 이해하도록 제공하는 스키마 설명 사전';
COMMENT ON COLUMN rag_agent_schema_note.project_id IS 'NULL이면 전역 설명, 값이 있으면 특정 프로젝트 전용 설명';
COMMENT ON COLUMN rag_agent_schema_note.version_id IS 'NULL이면 전역 설명, 값이 있으면 특정 버전 전용 설명';
COMMENT ON COLUMN rag_agent_schema_note.note_kind IS 'TABLE, COLUMN, QUERY_RECIPE 등 설명 종류';
COMMENT ON COLUMN rag_agent_schema_note.table_name IS '설명 대상 테이블명';
COMMENT ON COLUMN rag_agent_schema_note.object_name IS '컬럼명 또는 세부 객체명. 테이블 설명이면 NULL';
