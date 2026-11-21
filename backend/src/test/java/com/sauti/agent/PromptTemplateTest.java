package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplateTest {
    private static final Map<String, String> VALUES = Map.of(
            "business_name", "Bright Smile Dental",
            "website", "",
            "phone", "+254700000000"
    );

    @Test
    void aSentenceWhosePlaceholdersAreAllEmptyIsLeftOut() {
        var rendered = PromptTemplate.render(
                "You answer for {{business_name}}. Our website is {{website}}. Call {{phone}}.", VALUES::get);

        assertThat(rendered).isEqualTo("You answer for Bright Smile Dental. Call +254700000000.");
    }

    @Test
    void aLineWithOnlyEmptyPlaceholdersDisappearsButOtherLinesStay() {
        var rendered = PromptTemplate.render("""
                FACTS
                - Business: {{business_name}}
                - Website: {{website}}
                - Unknown: {{never_defined}}
                Be brief.""", VALUES::get);

        assertThat(rendered).isEqualTo("FACTS\n- Business: Bright Smile Dental\nBe brief.");
    }

    @Test
    void aSentenceWithSomeFilledValuesKeepsThemAndTidiesTheGap() {
        var rendered = PromptTemplate.render("Reach us at {{phone}} or {{website}} .", VALUES::get);

        assertThat(rendered).isEqualTo("Reach us at +254700000000 or.");
    }

    @Test
    void indentationOfFilledLinesIsKept() {
        assertThat(PromptTemplate.render("    * Name: {{business_name}}", VALUES::get))
                .isEqualTo("    * Name: Bright Smile Dental");
    }

    @Test
    void malformedPlaceholdersAreReportedAndNeverReachACaller() {
        assertThat(PromptTemplate.malformed("Hi {{Business Name}} and {{ ok }} and {{business_name}}"))
                .containsExactly("{{Business Name}}", "{{ ok }}");
        assertThat(PromptTemplate.render("Welcome to {{Business Name}}. Bye.", VALUES::get)).isEqualTo("Bye.");
        assertThat(PromptTemplate.render("Welcome to {{ business_name }}.", VALUES::get))
                .isEqualTo("Welcome to Bright Smile Dental.");
    }

    @Test
    void placeholdersAreListedOnceInOrder() {
        assertThat(PromptTemplate.placeholders("{{b}} {{a}}", "{{b}} {{c}} {{Bad}}")).containsExactly("b", "a", "c");
    }
}
