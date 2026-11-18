package com.sauti.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class WorkspaceInvitationEmailTemplateTest {
    private final TemplateEngine engine = engine();

    @Test
    void namesTheWorkspaceInviterAndRoleOnce() throws Exception {
        var html = render("amina@northclinic.example");

        assertThat(html).contains("Join North Clinic");
        assertThat(html).contains("https://sauti.uk/invitations/accept#token=abc");
        assertThat(html).contains("new.person@example.com");
        assertThat(html).contains("25 November 2026");
        assertThat(html.split("amina@northclinic.example", -1)).hasSize(3); // preheader + body
        assertThat(html).doesNotContain("A colleague");
        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build", "workspace-invitation-preview.html"), html);
    }

    @Test
    void fallsBackToAColleagueWhenTheInviterIsUnknown() {
        var html = render("");

        assertThat(html).contains("A colleague");
        assertThat(html).doesNotContain("A colleagueA colleague");
    }

    private String render(String inviterEmail) {
        var context = new Context();
        context.setVariable("workspaceName", "North Clinic");
        context.setVariable("inviterEmail", inviterEmail);
        context.setVariable("role", "Member");
        context.setVariable("acceptUrl", "https://sauti.uk/invitations/accept#token=abc");
        context.setVariable("toEmail", "new.person@example.com");
        context.setVariable("expiresAt", OffsetDateTime.parse("2026-11-25T12:00:00Z"));
        return engine.process("email/workspace-invitation", context);
    }

    private static TemplateEngine engine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
