import type { VideoComposeDetail, VideoComposeRequest } from '@/services/digitalHuman/types';

const containsChinese = (text: string): boolean => /[\u4e00-\u9fff]/.test(text);

const nonChineseFonts = new Set([
  'Arial',
  'Times New Roman',
  'Helvetica',
  'Georgia',
  'Verdana',
  'Calibri',
  'Tahoma',
  'Trebuchet MS',
  'Courier New',
]);

const getFontKey = (fontFamily?: string, textContent?: string): string => {
  if (!fontFamily) {
    return 'STSong';
  }

  const fontMapping: Record<string, string> = {
    宋体: 'STSong',
    黑体: 'STHeiti',
    楷体: 'STKaiti',
    仿宋: 'STFangsong',
    微软雅黑: 'Microsoft YaHei',
    华文黑体: 'STHeiti',
    华文楷体: 'STKaiti',
    华文宋体: 'STSong',
    华文仿宋: 'STFangsong',
    Arial: 'Arial',
    'Times New Roman': 'Times New Roman',
    Helvetica: 'Helvetica',
    Georgia: 'Georgia',
    Verdana: 'Verdana',
  };

  const mappedFont = fontMapping[fontFamily] || fontFamily || 'STSong';

  if (textContent && containsChinese(textContent) && nonChineseFonts.has(mappedFont)) {
    return 'STSong';
  }

  return mappedFont;
};

interface BuildVideoComposeRequestParams {
  name: string;
  width?: number;
  height?: number;
  elements?: any[];
  digitalHuman?: any;
  text?: string;
  magicSpeakerId?: string;
  voiceSpeed?: number;
  voiceVolume?: number;
  callbackUrl?: string;
  isPreview?: boolean;
  subtitleConfig?: any;
  driveMode?: 'text' | 'audio';
  audioFile?: any;
}

export const buildVideoComposeRequest = ({
  name,
  width = 1920,
  height = 1080,
  elements = [],
  digitalHuman,
  text,
  magicSpeakerId = 'moqianxue_meet_24k',
  voiceSpeed = 1.0,
  voiceVolume = 1.0,
  callbackUrl = 'https://api.hzmwmt.com/api/v1/digital-human/work/video-callback',
  isPreview = false,
  subtitleConfig,
  driveMode = 'text',
  audioFile,
}: BuildVideoComposeRequestParams): VideoComposeRequest => {
  const detailList: VideoComposeDetail[] = [];
  let hasTextContent = false;

  if (elements && elements.length > 0) {
    const sortedElements = [...elements].sort((a, b) => a.zIndex - b.zIndex);

    for (const element of sortedElements) {
      switch (element.type) {
        case 'background':
          if (element.properties?.backgroundType === 'image' && element.properties?.imageUrl) {
            detailList.push({
              contentType: 'video',
              content: element.properties.imageUrl,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              audioType: 'wav',
            });
          } else if (
            element.properties?.backgroundType === 'video' &&
            element.properties?.videoUrl
          ) {
            detailList.push({
              contentType: 'video',
              content: element.properties.videoUrl,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              audioType: 'wav',
            });
          }
          break;

        case 'illustration':
          if (element.properties?.imageUrl) {
            detailList.push({
              contentType: 'video',
              content: element.properties.imageUrl,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              audioType: 'wav',
            });
          }
          break;

        case 'video':
          if (element.properties?.videoUrl) {
            detailList.push({
              contentType: 'video',
              content: element.properties.videoUrl,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              audioType: 'wav',
            });
          }
          break;

        case 'title':
          if (element.properties?.text) {
            detailList.push({
              contentType: 'tag',
              content: element.properties.text,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              startTime: element.properties?.startTime || 0,
              stopTime: element.properties?.stopTime || 999999,
              fontsize: element.properties?.fontSize,
              fontname: getFontKey(element.properties?.fontFamily, element.properties.text),
              primaryColour: element.properties?.color || '#000000',
              bold: element.properties?.fontWeight === 'bold' ? 1 : 0,
              italic: element.properties?.fontStyle === 'italic' ? 1 : 0,
            });
          }
          break;

        case 'flash-text':
          if (element.properties?.text) {
            detailList.push({
              contentType: 'tag',
              content: element.properties.text,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              startTime: element.properties?.startTime || 0,
              stopTime: element.properties?.endTime || 999999,
              fontsize: element.properties?.fontSize,
              fontname: getFontKey(element.properties?.fontFamily, element.properties.text),
              primaryColour:
                element.properties?.textColor || element.properties?.color || '#000000',
            });
          }
          break;

        case 'subtitle': {
          const subtitleText = element.properties?.text || '请输入字幕内容';
          detailList.push({
            contentType: 'subtitles',
            content: subtitleText,
            magicSpeakerId,
            volume: 1.0,
            audioType: 'wav',
            sortNum: element.zIndex,
            x: element.position.x,
            y: element.position.y,
            width: element.size.width,
            height: element.size.height,
            primaryColour: element.properties?.primaryColour || element.properties?.textColor || '#800000',
            fontname: element.properties?.fontname || element.properties?.fontFamily || 'ZQKNLT',
            bold: String(
              element.properties?.bold !== undefined
                ? element.properties.bold
                : element.properties?.isBold
                  ? 1
                  : 0,
            ),
            subtitleUsepx: element.properties?.subtitleUsepx || 'Y',
            fontsize: String(element.properties?.fontsize || element.properties?.fontSize || 35),
            underline:
              element.properties?.underline !== undefined
                ? element.properties.underline
                : element.properties?.isUnderline
                  ? 1
                  : 0,
            italic:
              element.properties?.italic !== undefined
                ? element.properties.italic
                : element.properties?.isItalic
                  ? 1
                  : 0,
            bottomSpace: element.properties?.bottomSpace ?? element.properties?.bottomDistance ?? 10,
            wordSpace: element.properties?.wordSpace ?? 20,
          });
          break;
        }

        case 'digital-human':
          if (element.properties?.digitalHumanId || element.properties?.digitalHuman) {
            const digitalHumanData = element.properties.digitalHumanId
              ? {
                  id: element.properties.digitalHumanId,
                  headx: element.properties.headx,
                  heady: element.properties.heady,
                  headr: element.properties.headr,
                }
              : element.properties.digitalHuman;

            detailList.push({
              contentType: 'avatar',
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              useAvatarHead: Boolean(digitalHumanData.headx),
              headx: digitalHumanData.headx,
              heady: digitalHumanData.heady,
              headr: digitalHumanData.headr,
              wetaSpeakerId: digitalHumanData.id,
              audioType: 'wav',
            });

            let digitalHumanText = element.properties?.text || text;
            if (digitalHumanText && !hasTextContent && driveMode === 'text') {
              if (isPreview && digitalHumanText.length > 80) {
                digitalHumanText = digitalHumanText.substring(0, 80);
              }

              detailList.push({
                contentType: 'text',
                content: digitalHumanText,
                magicSpeakerId,
                srtLen: 18,
                audioType: 'wav',
                rate: 16000,
                speed: voiceSpeed,
                volume: voiceVolume,
                sortNum: element.zIndex + 100,
              });

              hasTextContent = true;
            }
          }
          break;

        case 'image':
          if (element.properties?.imageUrl) {
            detailList.push({
              contentType: 'video',
              content: element.properties.imageUrl,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              audioType: 'wav',
            });
          }
          break;

        case 'text':
          if (element.properties?.text && !hasTextContent && driveMode === 'text') {
            detailList.push({
              contentType: 'text',
              content: element.properties.text,
              magicSpeakerId,
              srtLen: 18,
              audioType: 'wav',
              rate: 16000,
              speed: voiceSpeed,
              volume: voiceVolume,
              sortNum: element.zIndex,
              x: element.position.x,
              y: element.position.y,
              width: element.size.width,
              height: element.size.height,
              fontname: getFontKey(element.properties?.fontFamily, element.properties.text),
              fontsize: element.properties?.fontSize || 16,
              primaryColour: element.properties?.color || '#000000',
              bold: element.properties?.fontWeight === 'bold' ? 1 : 0,
              italic: element.properties?.fontStyle === 'italic' ? 1 : 0,
              underline: element.properties?.textDecoration === 'underline' ? 1 : 0,
            });
            hasTextContent = true;
          }
          break;

        case 'voice':
          if (element.properties?.audioUrl) {
            detailList.push({
              contentType: 'audio',
              content: element.properties.audioUrl,
              sortNum: element.zIndex,
              audioType: 'wav',
              volume: element.properties?.volume || 1.0,
            });
          }
          break;

        case 'music':
          if (element.properties?.audioUrl) {
            detailList.push({
              contentType: 'back_audio',
              content: element.properties.audioUrl,
              sortNum: element.zIndex,
              audioType: 'wav',
              volume: element.properties?.volume || 1.0,
            });
          }
          break;

        default:
          break;
      }
    }
  }

  if (digitalHuman && !elements?.some((element) => element.type === 'digital-human')) {
    detailList.push({
      contentType: 'avatar',
      sortNum: 2,
      x: 0,
      y: 0,
      width: digitalHuman.speakerWidth,
      height: digitalHuman.speakerHeight,
      useAvatarHead: Boolean(digitalHuman.headx),
      headx: digitalHuman.headx,
      heady: digitalHuman.heady,
      headr: digitalHuman.headr,
      wetaSpeakerId: digitalHuman.id,
      audioType: 'wav',
    });
  }

  if (
    text &&
    !hasTextContent &&
    driveMode === 'text' &&
    !elements?.some((element) =>
      ['title', 'flash-text', 'subtitle', 'text', 'digital-human'].includes(element.type),
    )
  ) {
    let processedText = text;
    if (isPreview && text.length > 80) {
      processedText = text.substring(0, 80);
    }

    detailList.push({
      contentType: 'text',
      content: processedText,
      magicSpeakerId,
      srtLen: 18,
      audioType: 'wav',
      rate: 16000,
      speed: voiceSpeed,
      volume: voiceVolume,
    });
    hasTextContent = true;
  }

  if (subtitleConfig && subtitleConfig.isEnabled && text && driveMode === 'text') {
    let subtitleText = text;
    if (isPreview && text.length > 80) {
      subtitleText = text.substring(0, 80);
    }

    detailList.push({
      contentType: 'subtitles',
      content: subtitleText,
      magicSpeakerId,
      volume: 1.0,
      audioType: 'wav',
      sortNum: 999,
      x: 0,
      y: height - (subtitleConfig.bottomSpace ?? 10) - parseInt(subtitleConfig.fontsize ?? '35', 10),
      width,
      height: parseInt(subtitleConfig.fontsize || '35', 10),
      primaryColour: subtitleConfig.primaryColour || '#000000',
      fontname: subtitleConfig.fontname || 'STSONG',
      bold: subtitleConfig.bold || '0',
      subtitleUsepx: subtitleConfig.subtitleUsepx || 'Y',
      fontsize: subtitleConfig.fontsize || '35',
      underline: subtitleConfig.underline !== undefined ? subtitleConfig.underline : 0,
      italic: subtitleConfig.italic !== undefined ? subtitleConfig.italic : 0,
      bottomSpace: subtitleConfig.bottomSpace ?? 10,
      wordSpace: subtitleConfig.wordSpace ?? 20,
    });
  }

  if (driveMode === 'audio' && audioFile?.url) {
    detailList.push({
      contentType: 'audio',
      content: audioFile.url,
      sortNum: 1,
      audioType: 'wav',
      volume: voiceVolume || 1.0,
    });
  }

  return {
    name,
    width,
    height,
    callbackUrl,
    items: [
      {
        detailList,
      },
    ],
  };
};
