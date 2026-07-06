package com.sauti.nlp;

import java.util.List;

public interface LanguageDetector {
    String detect(String transcript, String defaultLanguage, List<String> supportedLanguages);
}
