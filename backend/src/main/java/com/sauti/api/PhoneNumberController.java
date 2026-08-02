package com.sauti.api;

import com.sauti.phone.InternationalPhoneNumberService;
import com.sauti.phone.InternationalPhoneNumberService.CountryCallingCode;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/phone-numbers")
public class PhoneNumberController {
    private final InternationalPhoneNumberService phoneNumbers;

    public PhoneNumberController(InternationalPhoneNumberService phoneNumbers) {
        this.phoneNumbers = phoneNumbers;
    }

    @GetMapping("/countries")
    List<CountryCallingCode> countries() {
        return phoneNumbers.countries();
    }
}
