import { backendDataRequest, backendRequest } from '@/services/http/backendClient';
import { getVideoComposeResult } from '@/services/digitalHuman/openPlatform';
import type {
  UserWork,
  UserWorksResponse,
  VideoComposeRequest,
  VideoComposeResponse,
} from '@/services/digitalHuman/types';

const calculateVideoTime = (request: VideoComposeRequest): number => {
  let totalTime = 0;

  for (const item of request.items) {
    for (const detail of item.detailList) {
      if (detail.contentType === 'text' && detail.content) {
        totalTime = Math.max(totalTime, detail.content.length / 160);
      } else if (detail.contentType === 'audio' && detail.content) {
        totalTime = Math.max(totalTime, 1);
      }
    }
  }

  return Math.max(Math.ceil(totalTime), 1);
};

export async function composeVideo(request: VideoComposeRequest): Promise<VideoComposeResponse> {
  const response = await backendRequest<string>(
    '/digital-human/work/composition',
    {
      method: 'POST',
      body: JSON.stringify({
        body: JSON.stringify(request),
        videoTime: calculateVideoTime(request),
      }),
    },
  );

  return {
    code: response.code,
    info: response.info,
    data: response.data,
    ok: true,
  };
}

export async function getUserWorks(lastCursorId?: string, size = 20) {
  const queryParams = new URLSearchParams();
  if (lastCursorId) {
    queryParams.append('lastCursorId', lastCursorId);
  }
  queryParams.append('size', size.toString());

  const response = await backendDataRequest<UserWorksResponse['data']>(
    `/digital-human/work/index?${queryParams.toString()}`,
    {
      method: 'GET',
      dedupeKey: `digital-human:works:${lastCursorId || 'first'}:${size}`,
      reuseInflight: true,
      cancelKey: 'digital-human:works:list',
    },
    {
      auth: true,
      idempotent: false,
    },
  );

  const works = (response.data || []).map((work: any) => {
    let workName = work.title;
    if (!workName) {
      try {
        const bodyData = JSON.parse(work.body);
        workName = bodyData.name || `作品_${work.digitalWorkId}`;
      } catch {
        workName = `作品_${work.digitalWorkId}`;
      }
    }

    return {
      ...work,
      id: work.digitalWorkId,
      name: workName,
      externalId: work.externalId,
      digitalWorkId: work.digitalWorkId.toString(),
      title: work.title,
      body: work.body,
      createTime: work.createTime,
    } as UserWork;
  });

  return {
    records: works,
    hasNext: response.hasNext,
    nextCursorId: response.nextCursorId,
  };
}

export async function updateWorkTitle(digitalWorkId: string, title: string): Promise<void> {
  await backendDataRequest<void>(
    '/digital-human/work/update-title',
    {
      method: 'PUT',
      body: JSON.stringify({
        digitalWorkId: parseInt(digitalWorkId, 10),
        title,
      }),
    },
    {
      auth: true,
    },
  );
}

export async function deleteUserWork(workId: string): Promise<boolean> {
  await backendDataRequest<void>(
    `/digital-human/work/${workId}/delete`,
    {
      method: 'POST',
    },
    {
      auth: true,
    },
  );

  return true;
}

export { getVideoComposeResult };
