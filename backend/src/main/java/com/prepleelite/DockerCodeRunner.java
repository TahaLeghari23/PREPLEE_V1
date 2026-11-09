package com.prepleelite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

@Service
public class DockerCodeRunner {
    private static final String RESULT_MARKER = "__PREPLEE_RESULT__";
    private final ObjectMapper objectMapper;

    public DockerCodeRunner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TestResult run(QuestionBank.CodingQuestion question, String sourceCode) {
        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("preplee-lite-").toAbsolutePath().normalize();
            Path runner = tempDirectory.resolve("runner.js");
            Files.writeString(runner, buildRunner(question, sourceCode), StandardCharsets.UTF_8);

            Process process = new ProcessBuilder(
                "docker", "run", "--rm",
                "--network=none", "--memory=128m", "--cpus=0.5", "--pids-limit=64",
                "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=16m",
                "-v", tempDirectory + ":/work:ro", "-w", "/work",
                "node:22-alpine", "node", "runner.js"
            ).redirectErrorStream(true).start();

            boolean finished = process.waitFor(Duration.ofSeconds(8).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new TestResult(0, question.tests().size(), "Execution timed out.");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int marker = output.lastIndexOf(RESULT_MARKER);
            if (marker < 0) {
                return new TestResult(0, question.tests().size(), "Compilation or execution failed: " + shorten(output));
            }
            JsonNode result = objectMapper.readTree(output.substring(marker + RESULT_MARKER.length()));
            return new TestResult(result.path("passed").asInt(), result.path("total").asInt(), result.path("message").asText());
        } catch (Exception error) {
            return new TestResult(0, question.tests().size(), "Docker execution failed: " + shorten(error.getMessage()));
        } finally {
            deleteTemporaryDirectory(tempDirectory);
        }
    }

    private String buildRunner(QuestionBank.CodingQuestion question, String sourceCode) throws IOException {
        String tests = objectMapper.writeValueAsString(question.tests());
        return """
            console.log = () => {};
            %s
            const tests = %s;
            let passed = 0;
            for (const test of tests) {
              try {
                const actual = %s(...test.args);
                if (JSON.stringify(actual) === JSON.stringify(test.expected)) passed++;
              } catch (_) {}
            }
            const result = {
              passed,
              total: tests.length,
              message: passed === tests.length ? "All hidden tests passed." : "Some hidden tests failed."
            };
            process.stdout.write("%s" + JSON.stringify(result));
            """.formatted(sourceCode, tests, question.functionName(), RESULT_MARKER);
    }

    private void deleteTemporaryDirectory(Path directory) {
        if (directory == null || !directory.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private String shorten(String value) {
        if (value == null || value.isBlank()) return "No output was returned.";
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.substring(0, Math.min(cleaned.length(), 180));
    }

    public record TestResult(int passed, int total, String message) {}
}
