import { readFile } from 'node:fs/promises';

const readSource = async relativePath => readFile(new URL(`../${relativePath}`, import.meta.url), 'utf8');

const [service, agentLibrary, zhibiYiyan] = await Promise.all([
  readSource('src/services/aiShardingApi.ts'),
  readSource('src/components/AgentLibrary.tsx'),
  readSource('src/components/ZhibiYiyan.tsx'),
]);

const requireMatch = (source, pattern, message) => {
  if (!pattern.test(source)) throw new Error(message);
};

const forbidMatch = (source, pattern, message) => {
  if (pattern.test(source)) throw new Error(message);
};

requireMatch(service, /aiModelConfigId:\s*string;/, 'AIModelConfig ID must remain a string');
requireMatch(service, /authenticatedRequest<unknown>/, 'model responses must enter through unknown');
requireMatch(service, /Number\.isSafeInteger\(value\)/, 'legacy numeric IDs must be gated by Number.isSafeInteger');
requireMatch(service, /export const normalizeAIModelTree/, 'recursive model-tree normalization is required');
forbidMatch(service, /aiModelConfigId:\s*number;/, 'numeric AIModelConfig IDs are forbidden');
forbidMatch(service, /aiModelConfigId\.toString\s*\(/, 'converting parsed model IDs with toString is too late');

forbidMatch(agentLibrary, /GENERAL_MODEL_ORDER|getGeneralModelRank/, 'client-side provider order overrides are forbidden');
forbidMatch(agentLibrary, /\.sort\s*\(/, 'provider/version arrays must retain backend order');
forbidMatch(agentLibrary, /(?:\|\||\?\?)\s*["']1["']/, 'AgentLibrary must not route to magic model ID 1');
forbidMatch(zhibiYiyan, /(?:\|\||\?\?)\s*["']1["']/, 'ZhibiYiyan must not route to magic model ID 1');

const validationIndex = zhibiYiyan.indexOf('const aiModelConfigId = resolveModelConfigId(');
const sendingIndex = zhibiYiyan.indexOf('setIsSending(true)', validationIndex);
if (validationIndex === -1 || sendingIndex === -1 || validationIndex > sendingIndex) {
  throw new Error('model ID validation must happen before a send is marked active');
}

console.log('AI model ID/order contract verified');
