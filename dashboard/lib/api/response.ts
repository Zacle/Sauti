export async function parseSuccessfulJsonResponse<T>(response: Response): Promise<T> {
  if (response.status === 204 || response.status === 205) return undefined as T;
  const body = await response.text();
  if (!body.trim()) return undefined as T;
  return JSON.parse(body) as T;
}
