import { readFile } from 'node:fs/promises';

const readSource = async relativePath => readFile(new URL(`../${relativePath}`, import.meta.url), 'utf8');

const [page, service] = await Promise.all([
  readSource('src/pages/system/AiModelManagement.tsx'),
  readSource('src/services/aiModelAdminService.ts'),
]);

const requireMatch = (source, pattern, message) => {
  if (!pattern.test(source)) throw new Error(message);
};

const forbidMatch = (source, pattern, message) => {
  if (pattern.test(source)) throw new Error(message);
};

requireMatch(service, /providerId\?:\s*string;/, 'providerId must remain a string');
requireMatch(service, /Number\.isSafeInteger\(value\)/, 'numeric business IDs must be accepted only when safe');
requireMatch(service, /validatedProviderId\s*=\s*toBusinessId\(providerId\)/, 'provider order writes must validate the string providerId');
requireMatch(service, /providers\/\$\{encodeURIComponent\(validatedProviderId\)\}\/order/, 'provider order endpoint must encode the validated providerId');
requireMatch(service, /static async deleteModel\(id:\s*string\)[\s\S]{0,300}?validatedModelId\s*=\s*toBusinessId\(id\)/, 'model deletion must validate the leaf business ID');
requireMatch(service, /AI_MODEL_ADMIN_BASE_URL\}\/\$\{encodeURIComponent\(validatedModelId\)\}[\s\S]{0,100}?method:\s*'DELETE'/, 'model deletion must call the encoded leaf endpoint with DELETE');
requireMatch(page, /model\.providerId\s*\?\s*`provider:\$\{model\.providerId\}`\s*:\s*`model:\$\{model\.id\}`/, 'groups must use providerId with a non-merging legacy fallback');
requireMatch(page, /if\s*\(!providerId\)\s*\{[\s\S]*?已拒绝更新排序/, 'provider order updates must fail closed without providerId');
requireMatch(page, /await AiModelAdminService\.updateProviderOrder\(providerId,\s*values\.order\);[\s\S]{0,300}?await loadModels\(\);/, 'successful provider ordering must reload the server-normalized order');
requireMatch(page, /const allProviderGroups\s*=\s*useMemo\([\s\S]{0,200}?buildProviderGroups\(models,\s*supportedModelCodes\)/, 'position choices must use the unfiltered model list');
requireMatch(page, /selectedCreateProviderGroups[\s\S]{0,300}?allProviderGroups\.filter\(group\s*=>\s*group\.modelCode\s*===\s*normalizedCode\)/, 'create position choices must resolve one physical provider group');
requireMatch(page, /selectedCreateProviderGroups\.length\s*>\s*1[\s\S]{0,200}?无法确定新增版本归属/, 'ambiguous create provider scopes must fail closed');
requireMatch(page, /siblingCount\s*=\s*editingModel\.providerId[\s\S]{0,100}?models\.filter\(model\s*=>\s*model\.providerId\s*===\s*editingModel\.providerId\)\.length/, 'edit position choices must include every version in the unfiltered provider');
requireMatch(page, /await AiModelAdminService\.update\(editingModel\.id,[\s\S]{0,500}?await loadModels\(\);/, 'successful version ordering must reload the server-normalized order');
requireMatch(page, /if\s*\(!model\.providerId\)\s*\{[\s\S]{0,120}?供应商分组不可删除/, 'provider/category deletion must fail closed');
requireMatch(page, /if\s*\(model\.visible\)\s*\{[\s\S]{0,120}?请先隐藏该模型/, 'visible models must not be deletable');
requireMatch(page, /从管理列表移除[\s\S]{0,160}?model\.name[\s\S]{0,160}?模型 ID：\{model\.id\}/, 'delete confirmation must identify the model name and business ID');
requireMatch(page, /API Key 覆盖将被清除/, 'delete confirmation must disclose API Key override removal');
requireMatch(page, /await AiModelAdminService\.deleteModel\(model\.id\);[\s\S]{0,220}?closeEditModal\(\);[\s\S]{0,120}?await loadModels\(\);/, 'successful model deletion must close edit and reload the authoritative list');
requireMatch(page, /await AiModelAdminService\.deleteModel\(model\.id\);[\s\S]{0,160}?setModels\(current => current\.filter\(item => item\.id !== model\.id\)\)/, 'successful model deletion must remove the tombstone from local state before a fallible refresh');
requireMatch(page, /editingModel\?\.providerId\s*&&[\s\S]{0,900}?disabled=\{editingModel\.visible\s*\|\|/, 'delete control must be leaf-only and disabled while the model is visible');
requireMatch(page, /<Select[\s\S]{0,500}?options=\{createVersionPositionOptions\}/, 'create version position must use a Select');
requireMatch(page, /<Select[\s\S]{0,250}?options=\{editVersionPositionOptions\}/, 'edit version position must use a Select');
requireMatch(page, /<Select[\s\S]{0,250}?options=\{providerPositionOptions\}/, 'provider position must use a Select');
forbidMatch(page, /\bInputNumber\b/, 'free-form numeric ordering controls are forbidden');
forbidMatch(page, /providerOrder:\s*values\.order/, 'provider order must not be patched locally before server normalization');
requireMatch(page, /if\s*\(left\s*===\s*undefined\)\s*return\s*1;[\s\S]*?if\s*\(right\s*===\s*undefined\)\s*return\s*-1;/, 'undefined orders must sort after configured orders');
requireMatch(page, /compareNullableOrder\(left\.order,\s*right\.order\)[\s\S]*?compareBusinessId\(left\.id,\s*right\.id\)/, 'versions must sort by nullable-last order then business ID');
requireMatch(page, /compareNullableOrder\(left\.providerOrder,\s*right\.providerOrder\)/, 'providers must use the same nullable-last ordering');
forbidMatch(page, /groupedModels\.(?:get|set)\(modelCode/, 'modelCode must never be the provider grouping boundary');
forbidMatch(page, /`provider:\$\{modelCode\}`/, 'modelCode-derived provider keys are forbidden');
forbidMatch(service, /updateProvider(?:Visibility|Name|ApiKey)/, 'provider-level visibility, name, and Key mutations are forbidden');

forbidMatch(service, /\b(?:Number|parseInt|parseFloat)\s*\(\s*(?:id|raw\.(?:id|aiModelConfigId|modelId|providerId))/, 'business IDs must never be numerically coerced');
forbidMatch(service, /raw\.apiKey(?!Configured)/, 'raw API Key material must never enter normal model state');
requireMatch(page, /createForm\.setFieldsValue\(\{\s*apiKey:\s*'',\s*confirmApiKey:\s*''\s*\}\)/, 'create secrets must be cleared after submission');
requireMatch(page, /apiKeyForm\.resetFields\(\)/, 'updated secrets must be cleared from form state');

console.log('AI model admin grouping/key contract verified');
