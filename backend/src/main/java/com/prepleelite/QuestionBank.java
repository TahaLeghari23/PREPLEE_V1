package com.prepleelite;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QuestionBank {
    private final List<CodingQuestion> questions = List.of(
        new CodingQuestion(
            "two-sum", "Arrays", "Two Sum",
            "Return the indices of two numbers whose sum equals the target.",
            "function twoSum(numbers, target) {\n  // Write your solution\n}\n",
            "twoSum",
            List.of(
                new HiddenTest(List.of(List.of(2, 7, 11, 15), 9), List.of(0, 1)),
                new HiddenTest(List.of(List.of(3, 2, 4), 6), List.of(1, 2)),
                new HiddenTest(List.of(List.of(3, 3), 6), List.of(0, 1))
            )
        ),
        new CodingQuestion(
            "valid-palindrome", "Strings", "Valid Palindrome",
            "Return true when a string reads the same forward and backward after removing non-alphanumeric characters.",
            "function isPalindrome(text) {\n  // Write your solution\n}\n",
            "isPalindrome",
            List.of(
                new HiddenTest(List.of("A man, a plan, a canal: Panama"), true),
                new HiddenTest(List.of("race a car"), false),
                new HiddenTest(List.of(" "), true)
            )
        ),
        new CodingQuestion(
            "max-profit", "Greedy", "Best Time to Buy and Sell Stock",
            "Return the largest profit possible from one purchase followed by one sale.",
            "function maxProfit(prices) {\n  // Write your solution\n}\n",
            "maxProfit",
            List.of(
                new HiddenTest(List.of(List.of(7, 1, 5, 3, 6, 4)), 5),
                new HiddenTest(List.of(List.of(7, 6, 4, 3, 1)), 0),
                new HiddenTest(List.of(List.of(2, 4, 1)), 2)
            )
        )
    );

    public List<CodingQuestion> all() {
        return questions;
    }

    public CodingQuestion find(String id) {
        return questions.stream().filter(question -> question.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown coding question."));
    }

    public record CodingQuestion(
        String id, String topic, String title, String prompt,
        String starterCode, String functionName, List<HiddenTest> tests
    ) {}

    public record HiddenTest(List<Object> args, Object expected) {}
}
