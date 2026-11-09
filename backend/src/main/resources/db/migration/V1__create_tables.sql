CREATE TABLE coding_attempt (
    id BIGSERIAL PRIMARY KEY,
    question_id VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    source_code TEXT NOT NULL,
    tests_passed INTEGER NOT NULL,
    tests_total INTEGER NOT NULL,
    test_score INTEGER NOT NULL,
    ai_score INTEGER NOT NULL,
    overall_score INTEGER NOT NULL,
    feedback TEXT NOT NULL,
    duration_seconds INTEGER NOT NULL,
    explanation TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE resume_interview (
    id BIGSERIAL PRIMARY KEY,
    resume_text TEXT NOT NULL,
    questions_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE resume_answer (
    id BIGSERIAL PRIMARY KEY,
    interview_id BIGINT NOT NULL REFERENCES resume_interview(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    score INTEGER NOT NULL,
    feedback TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX coding_attempt_topic_idx ON coding_attempt(topic);
CREATE INDEX resume_answer_interview_idx ON resume_answer(interview_id);
