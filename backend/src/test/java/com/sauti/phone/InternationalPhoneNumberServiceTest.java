package com.sauti.phone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InternationalPhoneNumberServiceTest {
    private final InternationalPhoneNumberService service = new InternationalPhoneNumberService();

    @Test
    void normalizesANationalNumberUsingTheSelectedBusinessCountry() {
        assertThat(service.normalize("0712 345 678", "KE")).isEqualTo("+254712345678");
        assertThat(service.normalize("+254 0712 345 678", "KE")).isEqualTo("+254712345678");
    }

    @Test
    void exposesCountryNamesAndCallingCodesForTheSelector() {
        assertThat(service.countries()).anySatisfy(country -> {
            assertThat(country.region()).isEqualTo("KE");
            assertThat(country.name()).isEqualTo("Kenya");
            assertThat(country.dialingCode()).isEqualTo("+254");
        });
    }

    @Test
    void rejectsInvalidOrIncompleteBusinessNumbers() {
        assertThatThrownBy(() -> service.normalize("123", "KE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid phone number");
    }

    @Test
    void rejectsANumberThatDoesNotMatchTheProviderNumber() {
        assertThatThrownBy(() -> service.normalizeAndRequireMatch(
                "0712 345 678", "KE", "+254 733 000 000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }
}
