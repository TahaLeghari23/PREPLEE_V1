 package com.prepleelite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class InterviewService {
    private final JdbcTemplate jdbc;
    private final QuestionBank questionBank;
    private final DockerCodeRunner codeRunner;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final SimpleJdbcInsert codingInsert;
    private final SimpleJdbcInsert resumeInterviewInsert;
    private final SimpleJdbcInsert resumeAnswerInsert;

    public InterviewService(
        JdbcTemplate jdbc, QuestionBank questionBank, DockerCodeRunner codeRunner,
        AiService aiService, ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.questionBank = questionBank;
        this.codeRunner = codeRunner;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.codingInsert = new SimpleJdbcInsert(jdbc).withTableName("coding_attempt").usingGeneratedKeyColumns("id");
        this.resumeInterviewInsert = new SimpleJdbcInsert(jdbc).withTableName("resume_interview").usingGeneratedKeyColumns("id");
        this.resumeAnswerInsert = new SimpleJdbcInsert(jdbc).withTableName("resume_answer").usingGeneratedKeyColumns("id");
    }

    public Map<String, Object> nextQuestion() {
        QuestionBank.CodingQuestion question = selectAdaptiveQuestion();
        return Map.of(
            "id", question.id(), "topic", question.topic(), "title", question.title(),
            "prompt", question.prompt(), "starterCode", question.starterCode(),
            "timeLimitSeconds", 900
        );
    }

    public Map<String, Object> submitCode(CodeSubmission request) {
        QuestionBank.CodingQuestion question = questionBank.find(request.questionId());
        DockerCodeRunner.TestResult tests = codeRunner.run(question, request.sourceCode());
        int testScore = Math.round((tests.passed() * 100f) / tests.total());
        AiService.Evaluation evaluation = aiService.evaluateCode(
            question.prompt(), request.sourceCode(), tests.passed() + "/" + tests.total(), request.explanation()
        );
        int overallScore = Math.round(testScore * 0.7f + evaluation.score() * 0.3f);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("question_id", question.id());
        values.put("topic", question.topic());
        values.put("source_code", request.sourceCode());
        values.put("tests_passed", tests.passed());
        values.put("tests_total", tests.total());
        values.put("test_score", testScore);
        values.put("ai_score", evaluation.score());
        values.put("overall_score", overallScore);
        values.put("feedback", evaluation.feedback());
        values.put("duration_seconds", Math.max(0, Math.min(3600, request.durationSeconds())));
        values.put("explanation", request.explanation());
        codingInsert.execute(values);

        return Map.of(
            "testsPassed", tests.passed(), "testsTotal", tests.total(),
            "testScore", testScore, "aiScore", evaluation.score(),
            "overallScore", overallScore, "feedback", evaluation.feedback(),
            "executionMessage", tests.message()
        );
    }

    public Map<String, Object> generateResumeInterview(ResumeRequest request) {
        String resumeText = request.resumeText().trim();
        if (resumeText.length() < 80) throw new IllegalArgumentException("Add at least 80 characters of resume text.");
        if (resumeText.length() > 20_000) resumeText = resumeText.substring(0, 20_000);
        List<String> questions = aiService.generateResumeQuestions(resumeText);
        try {
            Number id = resumeInterviewInsert.executeAndReturnKey(Map.of(
                "resume_text", resumeText,
                "questions_json", objectMapper.writeValueAsString(questions)
            ));
            return Map.of("interviewId", id.longValue(), "questions", questions);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Could not save generated questions.", error);
        }
    }

    public Map<String, Object> submitResumeAnswer(ResumeAnswer request) {
        if (request.answer().trim().length() < 20) throw new IllegalArgumentException("Answer at least 20 characters before submitting.");
        AiService.Evaluation evaluation = aiService.evaluateResumeAnswer(request.question(), request.answer());
        resumeAnswerInsert.execute(Map.of(
            "interview_id", request.interviewId(), "question", request.question(),
            "answer", request.answer(), "score", evaluation.score(), "feedback", evaluation.feedback()
        ));
        return Map.of("score", evaluation.score(), "feedback", evaluation.feedback());
    }

    public Map<String, Object> progress() {
        List<Map<String, Object>> coding = jdbc.queryForList("""
            SELECT id, topic, overall_score, tests_passed, tests_total, duration_seconds, created_at
            FROM coding_attempt ORDER BY id DESC LIMIT 20
            """);
        List<Map<String, Object>> resume = jdbc.queryForList("""
            SELECT id, question, score, created_at FROM resume_answer ORDER BY id DESC LIMIT 20
            """);
        List<Map<String, Object>> topics = jdbc.queryForList("""
            SELECT topic, ROUND(AVG(overall_score))::INTEGER AS average, COUNT(*) AS attempts
            FROM coding_attempt GROUP BY topic ORDER BY average ASC
            """);
        int total = coding.stream().mapToInt(row -> ((Number) row.get("overall_score")).intValue()).sum()
            + resume.stream().mapToInt(row -> ((Number) row.get("score")).intValue()).sum();
        int count = coding.size() + resume.size();
        return Map.of(
            "overallAverage", count == 0 ? 0 : Math.round((float) total / count),
            "codingAttempts", coding, "resumeAnswers", resume, "topics", topics
        );
    }

    private QuestionBank.CodingQuestion selectAdaptiveQuestion() {
        List<String> weakest = jdbc.queryForList("""
            SELECT topic FROM coding_attempt GROUP BY topic ORDER BY AVG(overall_score) ASC LIMIT 1
            """, String.class);
        if (!weakest.isEmpty()) {
            return questionBank.all().stream().filter(question -> question.topic().equals(weakest.getFirst()))
                .findFirst().orElse(questionBank.all().getFirst());
        }
        return questionBank.all().get(ThreadLocalRandom.current().nextInt(questionBank.all().size()));
    }

    public record CodeSubmission(String questionId, String sourceCode, String explanation, int durationSeconds) {
        public CodeSubmission {
            if (questionId == null || sourceCode == null || sourceCode.length() < 20) throw new IllegalArgumentException("Question or code is missing.");
            explanation = explanation == null ? "" : explanation.trim();
        }
    }
    public record ResumeRequest(String resumeText) {
        public ResumeRequest { if (resumeText == null) resumeText = ""; }
    }
    public record ResumeAnswer(long interviewId, String question, String answer) {
        public ResumeAnswer {
            if (interviewId <= 0 || question == null || question.isBlank() || answer == null)
                throw new IllegalArgumentException("Interview question or answer is missing.");
        }
    }
}
