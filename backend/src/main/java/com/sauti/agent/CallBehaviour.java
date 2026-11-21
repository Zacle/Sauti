package com.sauti.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * How the agent takes turns and handles silence on a call: when a caller may interrupt, when it
 * reminds and hangs up, how it treats voicemail and call screening, and how long a call may last.
 */
@Embeddable
public class CallBehaviour {
    static final int DEFAULT_MAX_CALL_SECONDS = 300;

    @Column(name = "barge_in_sensitivity", nullable = false)
    private double bargeInSensitivity = 0.70;

    @Column(name = "barge_in_grace_ms", nullable = false)
    private int bargeInGraceMs = 300;

    @Column(name = "end_call_on_silence_seconds", nullable = false)
    private int endCallOnSilenceSeconds = 60;

    @Column(name = "reminder_after_silence_seconds", nullable = false)
    private int reminderAfterSilenceSeconds = 30;

    @Column(name = "max_reminders", nullable = false)
    private int maxReminders = 1;

    @Column(name = "detect_voicemail", nullable = false)
    private boolean detectVoicemail = true;

    @Column(name = "handle_call_screening", nullable = false)
    private boolean handleCallScreening = true;

    @Column(name = "max_call_duration_seconds", nullable = false)
    private int maxCallDurationSeconds = DEFAULT_MAX_CALL_SECONDS;

    protected CallBehaviour() {
    }

    static CallBehaviour defaults() {
        return new CallBehaviour();
    }

    /** Missing values fall back to the defaults; out-of-range values are pulled into range. */
    void configure(Double bargeInSensitivity, Integer bargeInGraceMs, Integer endCallOnSilenceSeconds,
                   Integer reminderAfterSilenceSeconds, Integer maxReminders, Boolean detectVoicemail,
                   Boolean handleCallScreening) {
        this.bargeInSensitivity = Math.max(0, Math.min(1, bargeInSensitivity == null ? 0.70 : bargeInSensitivity));
        this.bargeInGraceMs = Ranges.clamp(bargeInGraceMs, 0, 3000, 300);
        this.endCallOnSilenceSeconds = Ranges.clamp(endCallOnSilenceSeconds, 60, 3600, 60);
        this.reminderAfterSilenceSeconds = Ranges.clamp(reminderAfterSilenceSeconds, 30, 600, 30);
        this.maxReminders = Ranges.clamp(maxReminders, 0, 10, 1);
        this.detectVoicemail = detectVoicemail == null || detectVoicemail;
        this.handleCallScreening = handleCallScreening == null || handleCallScreening;
    }

    /** Calls shorter than a minute are not allowed, so anything below that means "use the default". */
    void limitDuration(Integer seconds) {
        this.maxCallDurationSeconds = seconds == null || seconds < 60 ? DEFAULT_MAX_CALL_SECONDS : seconds;
    }

    public double getBargeInSensitivity() { return bargeInSensitivity; }
    public int getBargeInGraceMs() { return bargeInGraceMs; }
    public int getEndCallOnSilenceSeconds() { return endCallOnSilenceSeconds; }
    public int getReminderAfterSilenceSeconds() { return reminderAfterSilenceSeconds; }
    public int getMaxReminders() { return maxReminders; }
    public boolean isDetectVoicemail() { return detectVoicemail; }
    public boolean isHandleCallScreening() { return handleCallScreening; }
    public int getMaxCallDurationSeconds() { return maxCallDurationSeconds; }
}
