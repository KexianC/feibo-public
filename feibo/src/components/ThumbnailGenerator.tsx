import React, { useEffect, useRef, useCallback } from 'react';
import { SceneElement } from '../types';

interface ThumbnailGeneratorProps {
  elements: SceneElement[];
  aspectRatio: '16:9' | '9:16';
  width?: number;
  height?: number;
  /**
   * 缩略图最大高度（仅在未显式传入height时生效），用于竖屏防止过高
   */
  maxHeight?: number;
  /**
   * 是否将画布适配固定容器(16:9)，内部内容等比居中缩放
   */
  fitContainer?: boolean;
  onGenerated?: (dataUrl: string) => void;
}

// 图片缓存
const imageCache = new Map<string, HTMLImageElement>();

/**
 * 缩略图生成器组件
 * 根据项目元素数据生成缩略图预览，支持背景、图片和插图的优化渲染
 */
/**
 * ThumbnailGenerator 组件
 * 根据元素列表和纵横比生成缩略图；在竖屏下应用最大高度限制，避免canvas过高
 */
/**
 * 使用Canvas生成页面缩略图
 * - 支持竖屏高度限制和固定容器适配（等比居中缩放）
 */
const ThumbnailGenerator: React.FC<ThumbnailGeneratorProps> = ({
  elements,
  aspectRatio,
  width = 200,
  height,
  maxHeight = 300,
  fitContainer = true,
  onGenerated
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  
  /**
   * 计算画布尺寸（支持竖屏最大高度限制）
   * - 横屏: 按 `9/16` 比例
   * - 竖屏: 按 `16/9` 比例，但不超过 `maxHeight`
   */
  /**
   * 计算画布尺寸：当fitContainer为true时，始终按照容器的16:9来设置Canvas尺寸，
   * 竖屏内容通过内部缩放居中显示，不改变容器大小
   */
  const canvasHeight = (() => {
    if (height) return height;
    if (fitContainer) {
      return Math.round(width * 9 / 16);
    }
    if (aspectRatio === '16:9') {
      return Math.round(width * 9 / 16);
    }
    const expected = Math.round(width * 16 / 9);
    return Math.min(expected, maxHeight);
  })();
  
  /**
   * 加载图片的辅助函数，支持缓存、超时处理和CORS错误回退
   */
  const loadImage = useCallback(async (url: string, timeout: number = 5000): Promise<HTMLImageElement> => {
    // 检查缓存
    if (imageCache.has(url)) {
      const cachedImage = imageCache.get(url)!;
      if (cachedImage.complete && cachedImage.naturalWidth > 0) {
        return cachedImage;
      }
    }

    // 首先尝试使用crossOrigin='anonymous'加载
    try {
      return await loadImageWithCORS(url, timeout, 'anonymous');
    } catch (corsError) {
      // 检测是否为CORS相关错误
      const isCorsError = corsError.message.includes('CORS') || corsError.message.includes('cross-origin');
      
      if (isCorsError) {
        console.debug('检测到CORS错误，尝试无CORS模式');
      }
      
      // 如果CORS失败，尝试不设置crossOrigin
      try {
        return await loadImageWithCORS(url, timeout, null);
      } catch (noCorsError) {
        // 最后尝试use-credentials模式（仅在明确需要时）
        if (url.includes('credentials') || url.includes('auth')) {
          try {
            return await loadImageWithCORS(url, timeout, 'use-credentials');
          } catch (finalError) {
            // 所有方式都失败，但只记录简化的错误信息
            console.debug('图片加载完全失败');
            throw new Error('图片加载失败');
          }
        } else {
          // 跳过credentials模式，直接失败
          console.debug('图片加载失败');
          throw new Error('图片加载失败');
        }
      }
    }
  }, []);

  /**
   * 使用指定CORS模式加载图片的内部函数
   */
  const loadImageWithCORS = useCallback(async (
    url: string, 
    timeout: number, 
    crossOrigin: 'anonymous' | 'use-credentials' | null
  ): Promise<HTMLImageElement> => {
    return new Promise((resolve, reject) => {
      const img = new Image();
      
      // 根据参数设置crossOrigin属性
      if (crossOrigin) {
        img.crossOrigin = crossOrigin;
      }
      
      // 设置超时
      const timeoutId = setTimeout(() => {
        img.onload = null;
        img.onerror = null;
        reject(new Error(`图片加载超时(${crossOrigin || 'no-cors'}): ${url}`));
      }, timeout);
      
      img.onload = () => {
        clearTimeout(timeoutId);
        img.onload = null;
        img.onerror = null;
        
        // 验证图片是否真正加载成功
        if (img.naturalWidth > 0 && img.naturalHeight > 0) {
          // 缓存成功加载的图片
          imageCache.set(url, img);
          resolve(img);
        } else {
          reject(new Error(`图片数据无效(${crossOrigin || 'no-cors'}): ${url}`));
        }
      };
      
      img.onerror = (event) => {
        clearTimeout(timeoutId);
        img.onload = null;
        img.onerror = null;
        
        // 提供更详细的错误信息
        const errorDetail = event instanceof ErrorEvent ? event.message : '未知错误';
        reject(new Error(`图片加载失败(${crossOrigin || 'no-cors'}): ${url} - ${errorDetail}`));
      };
      
      // 设置src触发加载
      img.src = url;
    });
  }, []);

  /**
   * 生成缩略图
   */
  /**
   * 检测Canvas是否被跨域内容污染
   * @param canvas - 要检测的Canvas元素
   * @returns 如果Canvas被污染返回true，否则返回false
   */
  const isCanvasTainted = (canvas: HTMLCanvasElement): boolean => {
    try {
      // 尝试读取一个像素来检测是否被污染
      const ctx = canvas.getContext('2d');
      if (!ctx) return false;
      
      ctx.getImageData(0, 0, 1, 1);
      return false; // 如果没有抛出异常，说明Canvas没有被污染
    } catch (error) {
      // 如果抛出SecurityError，说明Canvas被污染了
      return error instanceof DOMException && error.name === 'SecurityError';
    }
  };

  /**
   * 安全地生成Canvas的DataURL，处理跨域污染问题
   * @param canvas - 要导出的Canvas元素
   * @returns DataURL字符串，如果失败返回null
   */
  const safeToDataURL = (canvas: HTMLCanvasElement): string | null => {
    try {
      // 检测Canvas是否被污染
      if (isCanvasTainted(canvas)) {
        console.warn('Canvas被跨域内容污染，无法导出DataURL，但Canvas内容仍可正常显示');
        return null;
      }
      
      // 尝试生成DataURL
      return canvas.toDataURL('image/png');
    } catch (error) {
      if (error instanceof DOMException && error.name === 'SecurityError') {
        console.warn('Canvas SecurityError: 跨域内容污染导致无法导出DataURL，Canvas显示正常');
      } else {
        console.error('Canvas导出失败:', error);
      }
      return null;
    }
  };

  const generateThumbnail = async () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    
    try {
      // 清空画布
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      
      // 设置默认背景色
      ctx.fillStyle = '#f8fafc';
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      // 统一缩放与居中偏移（基于页面纵横比，而非Canvas纵横比）
      const isPortraitContent = aspectRatio === '9:16';
      const baseWidth = isPortraitContent ? 1080 : 1920;
      const baseHeight = isPortraitContent ? 1920 : 1080;
      let scaleX = canvas.width / baseWidth;
      let scaleY = canvas.height / baseHeight;
      let offsetX = 0;
      let offsetY = 0;

      if (fitContainer && isPortraitContent) {
        const uniform = Math.min(canvas.width / baseWidth, canvas.height / baseHeight);
        scaleX = uniform;
        scaleY = uniform;
        offsetX = (canvas.width - baseWidth * uniform) / 2;
        offsetY = (canvas.height - baseHeight * uniform) / 2;
      }
      
      // 按zIndex排序元素
      const sortedElements = [...elements].sort((a, b) => (a.zIndex || 0) - (b.zIndex || 0));
      
      // 在竖屏等比缩放模式下，给内容区域添加裁剪，防止背景/插图越界
      if (fitContainer && isPortraitContent) {
        ctx.save();
        ctx.beginPath();
        ctx.rect(
          offsetX,
          offsetY,
          baseWidth * scaleX,
          baseHeight * scaleY
        );
        ctx.clip();
      }

      // 渲染每个元素（考虑统一缩放与偏移）
      for (const element of sortedElements) {
        await renderElement(ctx, element, {
          canvasWidth: canvas.width,
          canvasHeight: canvas.height,
          baseWidth,
          baseHeight,
          scaleX,
          scaleY,
          offsetX,
          offsetY
        });
      }

      if (fitContainer && isPortraitContent) {
        ctx.restore();
      }
      
      // 安全地生成数据URL
      const dataUrl = safeToDataURL(canvas);
      
      // 只有在成功生成DataURL时才调用回调
      // 即使DataURL生成失败，Canvas内容仍然可以正常显示
      if (dataUrl && onGenerated) {
        onGenerated(dataUrl);
      } else if (!dataUrl) {
        console.debug('跳过DataURL回调调用，但Canvas内容正常显示');
      }
    } catch (error) {
      console.error('缩略图生成失败:', error);
      // 渲染错误状态
      ctx.fillStyle = '#fee2e2';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = '#dc2626';
      ctx.font = '12px Arial';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText('渲染失败', canvas.width / 2, canvas.height / 2);
    }
  };
  
  /**
   * 渲染单个元素到画布
   */
  /**
   * 将场景坐标转换为缩略图坐标（考虑横/竖屏基准）
   * - 横屏基准：1920x1080
   * - 竖屏基准：1080x1920（交换）
   */
  const renderElement = async (
    ctx: CanvasRenderingContext2D,
    element: SceneElement,
    transform: {
      canvasWidth: number;
      canvasHeight: number;
      baseWidth: number;
      baseHeight: number;
      scaleX: number;
      scaleY: number;
      offsetX: number;
      offsetY: number;
    }
  ) => {
    const { position, size, type } = element;
    
    if (!position || !size) return;
    
    // 计算元素在缩略图中的位置和尺寸（统一缩放+居中偏移）
    const { scaleX, scaleY, offsetX, offsetY } = transform;
    const x = position.x * scaleX + offsetX;
    const y = position.y * scaleY + offsetY;
    const w = size.width * scaleX;
    const h = size.height * scaleY;
    
    ctx.save();
    
    try {
      switch (type) {
        case 'background':
          await renderBackground(ctx, element, x, y, w, h);
          break;
        case 'digital-human':
          await renderDigitalHuman(ctx, element, x, y, w, h);
          break;
        case 'text':
        case 'title':
        case 'subtitle':
        case 'flash-text':
          renderText(ctx, element, x, y, w, h);
          break;
        case 'image':
        case 'illustration':
          await renderImage(ctx, element, x, y, w, h);
          break;
        default:
          // 跳过无法识别的元素类型，不渲染任何内容
          console.debug('跳过未知元素类型:', type);
      }
    } catch (error) {
        console.warn('渲染元素失败，跳过该元素:', type, error);
      }
    
    ctx.restore();
  };
  
  /**
   * 渲染背景元素 - 增强版本，支持更好的渐变和图片处理
   */
  const renderBackground = async (ctx: CanvasRenderingContext2D, element: SceneElement, x: number, y: number, w: number, h: number) => {
    const props = element.properties || {};
    
    try {
      if (props.backgroundType === 'color' && props.color) {
        // 纯色背景（限定在内容区域）
        ctx.fillStyle = props.color;
        ctx.fillRect(x, y, w, h);
      } else if (props.backgroundType === 'gradient' && props.gradientColors) {
        // 渐变背景（限定在内容区域）
        const gradient = ctx.createLinearGradient(x, y, x + w, y + h);
        props.gradientColors.forEach((color: string, index: number) => {
          gradient.addColorStop(index / (props.gradientColors.length - 1), color);
        });
        ctx.fillStyle = gradient;
        ctx.fillRect(x, y, w, h);
      } else if (props.backgroundType === 'image' && props.imageUrl) {
        // 背景图片
        try {
          const img = await loadImage(props.imageUrl);
          
          // 计算图片缩放比例，保持宽高比
          const imgAspect = img.naturalWidth / img.naturalHeight;
          const areaAspect = w / h;
          
          let drawWidth, drawHeight, drawX, drawY;
          
          if (imgAspect > areaAspect) {
            // 图片更宽，以背景区域高度为准
            drawHeight = h;
            drawWidth = drawHeight * imgAspect;
            drawX = x + (w - drawWidth) / 2;
            drawY = y;
          } else {
            // 图片更高，以背景区域宽度为准
            drawWidth = w;
            drawHeight = drawWidth / imgAspect;
            drawX = x;
            drawY = y + (h - drawHeight) / 2;
          }
          
          ctx.drawImage(img, drawX, drawY, drawWidth, drawHeight);
        } catch (error) {
          // 静默处理图片加载失败，使用优雅的渐变背景作为回退
          console.debug('背景图片加载失败，使用默认渐变背景');
          const gradient = ctx.createLinearGradient(x, y, x + w, y + h);
          gradient.addColorStop(0, '#e0e7ff');
          gradient.addColorStop(0.5, '#c7d2fe');
          gradient.addColorStop(1, '#a5b4fc');
          ctx.fillStyle = gradient;
          ctx.fillRect(x, y, w, h);
        }
      } else {
        // 默认优雅渐变背景
        const gradient = ctx.createLinearGradient(x, y, x + w, y + h);
        gradient.addColorStop(0, '#f8fafc');
        gradient.addColorStop(0.5, '#f1f5f9');
        gradient.addColorStop(1, '#e2e8f0');
        ctx.fillStyle = gradient;
        ctx.fillRect(x, y, w, h);
      }
    } catch (error) {
      console.error('背景渲染失败:', error);
      // 最终回退方案
      ctx.fillStyle = '#f8fafc';
      ctx.fillRect(x, y, w, h);
    }
  };
  
  /**
   * 渲染数字人元素 - 加载并显示真实的数字人图像
   */
  const renderDigitalHuman = async (ctx: CanvasRenderingContext2D, element: SceneElement, x: number, y: number, w: number, h: number) => {
    const props = element.properties || {};
    const imageUrl = props.avatar || props.speakerUrl || props.imageUrl;
    
    // 只有当数字人图片URL存在时才尝试渲染
    if (!imageUrl) {
      console.debug('数字人图片URL不存在，跳过渲染:', element.type);
      return;
    }
    
    try {
      const img = await loadImage(imageUrl);
      
      // 计算图片缩放比例，保持宽高比
      const imgAspect = img.naturalWidth / img.naturalHeight;
      const elementAspect = w / h;
      
      let drawWidth, drawHeight, drawX, drawY;
      
      if (imgAspect > elementAspect) {
        // 图片更宽，以高度为准
        drawHeight = h;
        drawWidth = h * imgAspect;
        drawX = x + (w - drawWidth) / 2;
        drawY = y;
      } else {
        // 图片更高，以宽度为准
        drawWidth = w;
        drawHeight = w / imgAspect;
        drawX = x;
        drawY = y + (h - drawHeight) / 2;
      }
      
      // 绘制数字人图片
      ctx.drawImage(img, drawX, drawY, drawWidth, drawHeight);
      
      // 取消边框效果
      
    } catch (error) {
      // 静默处理数字人图片加载失败，不渲染任何内容
      console.debug('数字人图片加载失败，跳过渲染');
    }
  };
  
  /**
   * 渲染文本元素 - 增强版本，支持更好的文本样式和背景
   */
  const renderText = (ctx: CanvasRenderingContext2D, element: SceneElement, x: number, y: number, w: number, h: number) => {
    const props = element.properties || {};
    const text = props.text || props.content || '文本';
    const isTitle = element.type === 'title';
    const isSubtitle = element.type === 'subtitle';
    const isFlashText = element.type === 'flash-text';
    
    // 根据文本类型调整字体大小
    let baseFontSize;
    if (isTitle) {
      baseFontSize = Math.max(12, Math.min(w / 6, h / 2));
    } else if (isSubtitle) {
      baseFontSize = Math.max(8, Math.min(w / 8, h / 3));
    } else {
      baseFontSize = Math.max(6, Math.min(w / 10, h / 4));
    }
    
    // 文本颜色
    const textColor = props.color || (isTitle ? '#1f2937' : isSubtitle ? '#374151' : '#4b5563');
    
    // 添加文本背景（如果是闪文或有背景色）
    if (isFlashText || props.backgroundColor) {
      const bgColor = props.backgroundColor || (isFlashText ? '#fef3c7' : 'transparent');
      if (bgColor !== 'transparent') {
        ctx.fillStyle = bgColor;
        const padding = baseFontSize * 0.2;
        ctx.fillRect(x - padding, y - padding, w + padding * 2, h + padding * 2);
        
        // 取消文本背景边框
      }
    }
    
    // 设置文本样式
    ctx.fillStyle = textColor;
    const fontWeight = isTitle ? 'bold' : 'normal';
    ctx.font = `${fontWeight} ${baseFontSize}px Arial, sans-serif`;
    ctx.textAlign = props.textAlign || 'center';
    ctx.textBaseline = 'middle';
    
    // 添加文本阴影效果（如果是标题）
    if (isTitle && props.textShadow) {
      ctx.shadowColor = 'rgba(0, 0, 0, 0.3)';
      ctx.shadowBlur = 2;
      ctx.shadowOffsetX = 1;
      ctx.shadowOffsetY = 1;
    }
    
    // 文本换行处理
    const lines = text.split('\n');
    const lineHeight = baseFontSize * 1.3;
    const totalTextHeight = lines.length * lineHeight;
    const startY = y + h / 2 - totalTextHeight / 2 + lineHeight / 2;
    
    // 文本长度限制和省略号处理
    const maxCharsPerLine = Math.floor(w / (baseFontSize * 0.6));
    
    lines.forEach((line, index) => {
      let displayLine = line;
      if (line.length > maxCharsPerLine) {
        displayLine = line.substring(0, maxCharsPerLine - 3) + '...';
      }
      
      const lineY = startY + index * lineHeight;
      const lineX = props.textAlign === 'left' ? x + baseFontSize * 0.5 : 
                   props.textAlign === 'right' ? x + w - baseFontSize * 0.5 : 
                   x + w / 2;
      
      ctx.fillText(displayLine, lineX, lineY);
    });
    
    // 重置阴影
    if (isTitle && props.textShadow) {
      ctx.shadowColor = 'transparent';
      ctx.shadowBlur = 0;
      ctx.shadowOffsetX = 0;
      ctx.shadowOffsetY = 0;
    }
  };
  
  /**
   * 渲染图片和插图元素 - 只渲染实际可用的图片内容
   */
  const renderImage = async (ctx: CanvasRenderingContext2D, element: SceneElement, x: number, y: number, w: number, h: number) => {
    const props = element.properties || {};
    const imageUrl = props.imageUrl || props.src || props.url;
    const isIllustration = element.type === 'illustration';
    
    // 只有当图片URL存在时才尝试渲染
    if (!imageUrl) {
      console.debug('图片URL不存在，跳过渲染:', element.type);
      return;
    }
    
    try {
      const img = await loadImage(imageUrl);
      
      // 计算图片缩放比例，保持宽高比
      const imgAspect = img.naturalWidth / img.naturalHeight;
      const elementAspect = w / h;
      
      let drawWidth, drawHeight, drawX, drawY;
      
      if (imgAspect > elementAspect) {
        // 图片更宽，以宽度为准
        drawWidth = w;
        drawHeight = w / imgAspect;
        drawX = x;
        drawY = y + (h - drawHeight) / 2;
      } else {
        // 图片更高，以高度为准
        drawHeight = h;
        drawWidth = h * imgAspect;
        drawX = x + (w - drawWidth) / 2;
        drawY = y;
      }
      
      // 添加圆角效果（如果是插图）
      if (isIllustration && props.borderRadius) {
        ctx.save();
        const radius = Math.min(props.borderRadius, Math.min(w, h) / 2);
        ctx.beginPath();
        ctx.roundRect(x, y, w, h, radius);
        ctx.clip();
      }
      
      // 绘制图片
      ctx.drawImage(img, drawX, drawY, drawWidth, drawHeight);
      
      // 取消插图边框效果
      
      if (isIllustration && props.borderRadius) {
        ctx.restore();
      }
      
    } catch (error) {
      // 静默处理图片加载失败，不渲染任何内容
      console.debug(`${isIllustration ? '插图' : '图片'}加载失败，跳过渲染`);
      // 图片加载失败时不渲染任何内容，静默跳过
    }
  };
  
  // renderDefault函数已被移除，不再渲染占位符或替代图案
  
  // 当元素或尺寸变化时重新生成缩略图
  useEffect(() => {
    generateThumbnail();
  }, [elements, aspectRatio, width, canvasHeight]);
  
  return (
    <canvas
      ref={canvasRef}
      width={width}
      height={canvasHeight}
      style={{ width, height: canvasHeight }}
      className="rounded border"
    />
  );
};

export default ThumbnailGenerator;
