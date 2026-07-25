export async function finalizeTelnyxEndConversation(
  endConversation: () => Promise<void> | undefined,
  onError: (error: unknown) => void,
  onFinished: () => void,
): Promise<void> {
  try {
    await endConversation();
  } catch (error) {
    onError(error);
  } finally {
    onFinished();
  }
}
