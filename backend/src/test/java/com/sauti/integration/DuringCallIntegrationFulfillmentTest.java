package com.sauti.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DuringCallIntegrationFulfillmentTest {

    @Test
    void returnsOnlyTheConfiguredSheetColumnsToTheAgent() throws Exception {
        var row = new ObjectMapper().readTree("[\"customer-1\",\"private note\",\"active\",\"gold\"]");

        var projected = DuringCallIntegrationFulfillment.projectedValues(
                row,
                DuringCallIntegrationFulfillment.columnIndexes("0, 2")
        );

        assertThat(projected).containsExactly("customer-1", "active");
        assertThat(projected).doesNotContain("private note", "gold");
    }
}
