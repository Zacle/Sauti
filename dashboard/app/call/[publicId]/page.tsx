import { WebVoiceCall } from "@/features/web-voice/WebVoiceCall";

export default async function PublicWebVoicePage({ params }: { params: Promise<{ publicId: string }> }) {
  const { publicId } = await params;
  return <WebVoiceCall publicId={publicId} />;
}
