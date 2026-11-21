"use client";


import { Select } from "@/components/ui/select";
import {
Field,
FieldGrid,
SliderField,
StudioGroup,
StudioInput,
StudioSection,
studioSelectClass
} from "./studio-kit";
import { VoicePicker } from "./VoicePicker";


export function SpeechSettings(props: {
  voice: string; language: string; supportedLanguages: string[]; bargeInSensitivity: number; bargeInGraceMs: number; sttEndpointingMs: number;
  vocabularyDomain: string; boostedKeywords: string;
  onVoice: (value: string) => void; onBargeInSensitivity: (value: number) => void;
  onBargeInGraceMs: (value: number) => void; onSttEndpointingMs: (value: number) => void;
  onVocabularyDomain: (value: string) => void; onBoostedKeywords: (value: string) => void;
}) {
  return (
    <StudioSection eyebrow="Speech & transcription" title="Voice and turn-taking" description="Tune how the agent sounds, listens, and decides that a caller has finished speaking.">
      <StudioGroup title="Voice" description="What callers hear. Listen to each voice before choosing.">
        <VoicePicker value={props.voice} primaryLanguage={props.language} supportedLanguages={props.supportedLanguages} onChange={props.onVoice} />
      </StudioGroup>
      <StudioGroup title="Turn-taking" description="How quickly the agent replies, and how easily callers can interrupt it.">
        <SliderField
          label="Interruption sensitivity"
          description="How readily the agent stops talking when the caller starts speaking."
          value={props.bargeInSensitivity}
          min={0}
          max={1}
          step={0.05}
          format={(value) => `${Math.round(value * 100)}%`}
          lowLabel="Finishes its sentence"
          highLabel="Stops straight away"
          onChange={props.onBargeInSensitivity}
        />
        <SliderField
          label="Interruption grace period"
          description="How long the caller must speak before it counts as an interruption."
          value={props.bargeInGraceMs}
          min={0}
          max={1500}
          step={50}
          format={(value) => `${value} ms`}
          lowLabel="Any sound"
          highLabel="A full phrase"
          onChange={props.onBargeInGraceMs}
        />
        <SliderField
          label="Reply delay"
          description="How long the caller must be silent before the agent answers."
          value={props.sttEndpointingMs}
          min={100}
          max={1500}
          step={50}
          format={(value) => `${value} ms`}
          lowLabel="Quick replies"
          highLabel="Waits for long pauses"
          onChange={props.onSttEndpointingMs}
        />
      </StudioGroup>
      <StudioGroup title="Transcription" description="Help speech recognition with your field and the names callers say.">
        <FieldGrid>
          <Field label="Vocabulary">
            <Select size="md" className={studioSelectClass} ariaLabel="Vocabulary specialization" value={props.vocabularyDomain || "general"} onValueChange={props.onVocabularyDomain}
              options={[{ value: "general", label: "General" }, { value: "medical", label: "Medical" }]} />
          </Field>
          <Field label="Words to listen for" htmlFor="boosted-keyterms" hint="Separate with commas: names, places, products.">
            <StudioInput id="boosted-keyterms" value={props.boostedKeywords} onChange={(event) => props.onBoostedKeywords(event.target.value)} placeholder="Kibera, Thiong'o, Invisalign" />
          </Field>
        </FieldGrid>
      </StudioGroup>
    </StudioSection>
  );
}
