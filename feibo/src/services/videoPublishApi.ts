import { authenticatedRequest } from './authApi';
import { videoBlobRequest, videoRawRequest, videoRequest } from './http/videoClient';

const videoApiRequest = async <T>(
    url: string,
    options: RequestInit = {},
    uuid?: string
): Promise<ApiResponse<T>> => {
    return videoRequest<T>(url, options, {
        idempotent: Boolean(uuid),
        idempotencyKey: uuid,
    });
};

// 通用响应结构
interface ApiResponse<T = any> {
    code: number;
    msg: string;
    data: T;
}

export interface AccountsResponse {
    id: number;
    type: number;
    filePath: string;
    userName: string;
    status: number;
    user_id: string;
}

//快速获取账号
export const getAccounts = async (): Promise<ApiResponse> => {
    const response = await videoApiRequest('/video-publish/getAccounts');
    return response;
};

export interface UploadSaveResponse {
    filename: string,
    filepath: string
}

//上传素材并保存
export const uploadSave = async (file: File): Promise<ApiResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await videoApiRequest(
        `/video-publish/uploadSave`,
        {
            method: 'POST',
            body: formData
        }
    );

    return response;
}
//获取素材文件
export const getFile = async (filename: string): Promise<Blob> => {
    return videoBlobRequest(`/video-publish/getFile?filename=${encodeURIComponent(filename)}`, {
        method: 'GET',
    });
};

export interface GetFilesResponse {
    id: string,
    filename: string,
    filesize: string,
    upload_time: Date,
    file_path: string,
    user_id: string
}

export const getFiles = async (): Promise<GetFilesResponse[]> => {
    const response = await authenticatedRequest<GetFilesResponse[]>(
        `/video-pulish/getFiles`,
        {
            method: 'GET'
        }
    );
    return response.data;
}
//验证账号是否有效
export const getValidAccounts = async (): Promise<ApiResponse> => {
    const response = await videoApiRequest<ApiResponse>(
        '/video-publish/getValidAccounts',
        {
            method: 'GET',
        }
    );

    return response;
}
//删除素材文件
export const deleteFile = async (id: number): Promise<ApiResponse> => {
    const response = await videoApiRequest(
        `/video-publish/deleteFile?id=${id}`,
        {
            method: 'GET'
        }
    )
    return response
}
//删除账号
export const deleteAccount = async (id: number): Promise<ApiResponse> => {
    const response = await videoApiRequest(
        `/video-publish/deleteAccount?id=${id}`,
        {
            method: 'GET'
        }
    );
    return response
}

export const loginVideoAccount = async (type: number, username: string): Promise<Response> => {
    return videoRawRequest(`/video-publish/login?username=${username}&type=${type}`, {
        method: 'GET',
    });
}

export interface UpdateUserInfoRequest {
    id: number,
    type: number,
    userName: string
}

export const updateUserinfo = async (data: UpdateUserInfoRequest): Promise<ApiResponse> => {
    const response = await videoApiRequest(
        `/video-publish/updateUserinfo`,
        {
            method: 'POST',
            body: JSON.stringify(data)
        }
    );
    return response;
}

export interface PostVideoRequest {
    type: number,
    title: string,
    tags: string[],
    fileList: string[],
    accountList: string[],
    enableTimer: number,
    videosPerDay: number,
    dailyTimes: string[],
    startDays: number,
    category: number,
    productLink: string,
    productTitle: string,
    isDraft: boolean,
}

export const postVideo = async (data: PostVideoRequest): Promise<ApiResponse> => {
    return await videoApiRequest(
        `/video-publish/postVideo`,
        {
            method: 'POST',
            body: JSON.stringify(data)
        }
    )
}

export const postVideoBatch = async (data: PostVideoRequest[]): Promise<void> => {
    await authenticatedRequest(
        `/video-publish/postVideoBatch`,
        {
            method: 'POST',
            body: JSON.stringify(data)
        }
    )
}

export const uploadCookie = async (file: File, id: string, type: string): Promise<ApiResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    const json = JSON.stringify({'id':parseInt(id),'platform':type});
    const bolo = new Blob([json],{
        type:'application/json',
    });
    formData.append('dto',bolo)
    const response = await videoApiRequest(
        `/video-publish/uploadCookie`,
        {
            method: 'POST',
            body: formData,
        }
    );
    return response
}

export const downloadCookie = async (filePath: string): Promise<Blob> => {
    return videoBlobRequest(`/video-publish/downloadCookie?filePath=${encodeURIComponent(filePath)}`, {
        method: 'GET',
    });
};

//快速验证单个账号
export const verifyAccount = async(id:number) :Promise<ApiResponse> =>{
     const response = await videoApiRequest<ApiResponse>(
        `/video-publish/verifyAccount?id=${id}`,
        {
            method: 'GET',
        }
    );

    return response;
}

//提交验证码
export const submitVerification = async (code:string):Promise<ApiResponse> =>{
    const response = await videoApiRequest<ApiResponse>(
        '/video-publish/submit_verification',
        {
            method: 'POST',
            body: JSON.stringify({ code }),
        }
    );
    return response;
}
