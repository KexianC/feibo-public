export function parseApiJson<T>(text: string): T {
  const preprocessedText = text.replace(/"(\w*[Ii]d\w*)"\s*:\s*(\d{16,})/g, '"$1":"$2"');

  return JSON.parse(preprocessedText, (key, value) => {
    if (
      typeof value === 'string' &&
      (key.includes('Id') || key === 'userId' || key.includes('id') || key.includes('ID')) &&
      /^\d+$/.test(value)
    ) {
      return value;
    }

    return value;
  }) as T;
}
