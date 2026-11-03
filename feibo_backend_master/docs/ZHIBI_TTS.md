# 智笔医言语音对话配置

智笔医言的语音对话默认关闭。用户主动开启后，前端会在模型完整返回正文后去除 Markdown 标记，按句分段请求后端，再依次播放音频。思考过程不会送往语音合成服务。

后端使用阿里云百炼 `qwen-audio-3.0-tts-flash` 语音合成模型，通过 DashScope Java SDK 返回 24 kHz MP3。API Key 只从服务端环境变量读取，不会下发到浏览器。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DASHSCOPE_API_KEY` | 无 | 必填，阿里云百炼 API Key |
| `DASHSCOPE_TTS_MODEL` | `qwen-audio-3.0-tts-flash` | TTS 模型 |
| `DASHSCOPE_TTS_VOICE` | `longanfengyue` | 自然亲切的中文女声 |
| `DASHSCOPE_TTS_MAX_TEXT_LENGTH` | `600` | 单次合成的服务端上限，最大不超过 600 |
| `DASHSCOPE_TTS_TIMEOUT_MILLIS` | `60000` | 单次合成超时，有效范围 5–120 秒 |
| `DASHSCOPE_TTS_MAX_CONCURRENCY` | `8` | 单个服务实例同时执行的合成请求上限 |
| `DASHSCOPE_TTS_USER_MAX_REQUESTS` | `30` | 每名用户在统计窗口内的请求数上限 |
| `DASHSCOPE_TTS_USER_MAX_CHARACTERS` | `12000` | 每名用户在统计窗口内的合成字符数上限 |
| `DASHSCOPE_TTS_USAGE_WINDOW_MILLIS` | `60000` | 用户配额统计窗口，范围 1 秒–1 小时 |

生产环境建议为 TTS 单独创建最小权限的 API Key，并在阿里云控制台中限制调用来源和配额。应用内的用户配额和并发舱壁按单个服务实例生效；多实例部署时还应在 API 网关或 Redis 中配置分布式限流。医疗对话文本会在用户开启语音模式后发往阿里云；本地服务不记录正文、不落盘音频，响应也会带 `Cache-Control: no-store`。

对外接口为受登录保护的 `POST /api/v1/ai/tts`，请求体是 `{ "text": "..." }`，成功时直接返回 `audio/mpeg` 二进制内容。

阿里云官方参考：

- [语音合成模型选型](https://help.aliyun.com/zh/model-studio/tts-model)
- [Qwen-Audio-TTS / CosyVoice Java SDK](https://help.aliyun.com/zh/model-studio/cosyvoice-java-sdk)
- [Qwen-Audio-TTS 音色列表](https://help.aliyun.com/zh/model-studio/qwen-audio-tts-voice-list)
