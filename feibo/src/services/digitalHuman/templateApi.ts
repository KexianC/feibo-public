import type { Template } from '@/types';
import { backendDataRequest } from '@/services/http/backendClient';
import type { TemplateResponse } from '@/services/digitalHuman/types';

export async function createTemplate(title: string, body: string): Promise<void> {
  await backendDataRequest<void>(
    '/digital-human/template/create',
    {
      method: 'POST',
      body: JSON.stringify({ title, body }),
    },
    {
      auth: true,
    },
  );
}

export async function getUserTemplates(lastCursorId?: string, size = 20): Promise<{
  templates: Template[];
  hasNext: boolean;
  nextCursorId: string | null;
}> {
  const queryParams = new URLSearchParams();
  if (lastCursorId) {
    queryParams.append('lastCursorId', lastCursorId);
  }
  queryParams.append('size', size.toString());

  const response = await backendDataRequest<TemplateResponse>(
    `/digital-human/template/user?${queryParams.toString()}`,
    {
      method: 'GET',
      dedupeKey: `digital-human:templates:${lastCursorId || 'first'}:${size}`,
      reuseInflight: true,
      cancelKey: 'digital-human:templates:list',
    },
    {
      auth: true,
      idempotent: false,
    },
  );

  return {
    templates: response.data,
    hasNext: response.hasNext,
    nextCursorId: response.nextCursorId,
  };
}

export async function updateTemplateTitle(digitalTemplateId: string, title: string): Promise<void> {
  await backendDataRequest<void>(
    '/digital-human/template/update-title',
    {
      method: 'PUT',
      body: JSON.stringify({
        digitalTemplateId: parseInt(digitalTemplateId, 10),
        title,
      }),
    },
    {
      auth: true,
    },
  );
}

export async function deleteTemplate(templateId: string): Promise<boolean> {
  await backendDataRequest<void>(
    `/digital-human/template/${templateId}/delete`,
    {
      method: 'POST',
    },
    {
      auth: true,
    },
  );

  return true;
}
