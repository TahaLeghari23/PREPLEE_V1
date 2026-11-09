package com.prepleelite;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class InterviewController {
    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    @GetMapping("/code/next")
    public Map<String, Object> nextQuestion() {
        return service.nextQuestion();
    }

    @PostMapping("/code/submit")
    public Map<String, Object> submitCode(@RequestBody InterviewService.CodeSubmission request) {
        return service.submitCode(request);
    }

    @PostMapping("/resume/generate")
    public Map<String, Object> generateResumeInterview(@RequestBody InterviewService.ResumeRequest request) {
        return service.generateResumeInterview(request);
    }

    @PostMapping("/resume/answer")
    public Map<String, Object> submitResumeAnswer(@RequestBody InterviewService.ResumeAnswer request) {
        return service.submitResumeAnswer(request);
    }

    @GetMapping("/progress")
    public Map<String, Object> progress() {
        return service.progress();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> serverError(IllegalStateException error) {
        return ResponseEntity.internalServerError().body(Map.of("error", error.getMessage()));
    }
}
