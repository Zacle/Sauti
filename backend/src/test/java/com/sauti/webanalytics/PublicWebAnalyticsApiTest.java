package com.sauti.webanalytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.sauti.shared.RedisRateLimiter;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PublicWebAnalyticsApiTest {
    @Autowired MockMvc mvc;
    @Autowired PublicWebAnalyticsRepository events;
    @MockitoBean RedisRateLimiter rateLimiter;

    @Test
    void acceptsAnonymousMarketingEventsWithoutPersistingRawNetworkIdentity() throws Exception {
        when(rateLimiter.tryAcquire(any(), any(), anyInt(), any())).thenReturn(true);
        mvc.perform(post("/api/v1/public/analytics/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Mozilla/5.0 Sauti test")
                        .header("X-Forwarded-For", "203.0.113.71")
                        .content("""
                                {"eventType":"page_view","path":"/pricing?secret=value",
                                 "referrer":"https://example.com/private/path","utmSource":"launch"}
                                """))
                .andExpect(status().isNoContent());

        var stored = events.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getPath()).isEqualTo("/pricing");
        assertThat(stored.get(0).getVisitorHash()).doesNotContain("203.0.113.71");
    }
}
