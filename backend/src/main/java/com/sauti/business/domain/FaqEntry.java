package com.sauti.business.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/** An approved question and the answer agents may give. */
@Embeddable
public class FaqEntry {
    @Column(name = "question", nullable = false, length = 300)
    private String question;

    @Column(name = "answer", nullable = false, length = 1000)
    private String answer;

    protected FaqEntry() {
    }

    public FaqEntry(String question, String answer) {
        var cleanQuestion = Text.limit(Text.clean(question), 300, "Questions");
        var cleanAnswer = Text.limit(Text.clean(answer), 1000, "Answers");
        if (cleanQuestion == null || cleanAnswer == null) {
            throw new BusinessProfileException("Each FAQ needs both a question and an answer");
        }
        this.question = cleanQuestion;
        this.answer = cleanAnswer;
    }

    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }

    @Override
    public boolean equals(Object other) {
        return other instanceof FaqEntry that && question.equals(that.question) && answer.equals(that.answer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(question, answer);
    }
}
