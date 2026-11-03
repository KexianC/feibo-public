export const sanitizeAvatarUrl = (avatar: unknown): string => {
  if (!avatar || typeof avatar !== 'string') return '';

  return avatar
    .trim()
    .replace(/^`+|`+$/g, '')
    .replace(/^"+|"+$/g, '')
    .replace(/^'+|'+$/g, '');
};
