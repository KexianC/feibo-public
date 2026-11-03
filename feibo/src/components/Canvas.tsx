import React, { useRef, useEffect, useState, useCallback } from 'react';
import { Stage, Layer, Rect, Image, Text, Transformer, Line } from 'react-konva';
import { useCurrentProject, useSelectedElement, useActions } from '../store';
import { SceneElement, BackgroundProperties, TitleProperties, IllustrationProperties } from '../types';
import Konva from 'konva';
import { useStore } from '../store';
import { useAuthGuard } from '../hooks/useAuthGuard';

interface CanvasElementProps {
  element: SceneElement;
  isSelected: boolean;
  onSelect: () => void;
  onTransform: (updates: Partial<SceneElement>) => void;
  scale: number;
}

const CanvasElement: React.FC<CanvasElementProps> = ({ 
  element, 
  isSelected, 
  onSelect, 
  onTransform,
  scale 
}) => {
  // 如果元素不可见，则不渲染
  if (element.properties.visible === false) {
    return null;
  }
  const shapeRef = useRef<any>();
  const trRef = useRef<Konva.Transformer>();
  const [image, setImage] = useState<HTMLImageElement | null>(null);
  const [imageLoading, setImageLoading] = useState<boolean>(false);
  const [imageError, setImageError] = useState<string | null>(null);
  const bindingTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const lastBindingTimeRef = useRef<number>(0);
  const monitoringIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const isMonitoringRef = useRef<boolean>(false);

  // 节点状态监控函数 - 必须在useEffect之前定义
  const stopNodeMonitoring = useCallback(() => {
    isMonitoringRef.current = false;
    if (monitoringIntervalRef.current) {
      clearInterval(monitoringIntervalRef.current);
      monitoringIntervalRef.current = null;
    }
  }, []);

  const startNodeMonitoring = useCallback(() => {
    if (isMonitoringRef.current) {
      return; // 已在监控中
    }
    
    isMonitoringRef.current = true;
    
    const monitorNodeState = () => {
      try {
        const node = shapeRef.current;
        const transformer = trRef.current;
        
        if (!node || !transformer || !isSelected) {
          stopNodeMonitoring();
          return;
        }
        
        // 检查节点是否仍然有效
        const isNodeValid = (
          node.getStage &&
          typeof node.getStage === 'function' &&
          node.getStage() &&
          node.getParent &&
          typeof node.getParent === 'function' &&
          node.getParent()
        );
        
        if (!isNodeValid) {
          console.warn('监控检测到节点失效，清理Transformer状态');
          try {
            transformer.nodes([]);
            const layer = node.getLayer && node.getLayer();
            if (layer && typeof layer.batchDraw === 'function') {
              layer.batchDraw();
            }
          } catch (cleanupError) {
            console.warn('监控清理失败:', cleanupError);
          }
          stopNodeMonitoring();
          return;
        }
        
        // 检查Transformer绑定状态
        const boundNodes = transformer.nodes();
        if (!boundNodes || boundNodes.length === 0) {
          console.warn('监控检测到Transformer未绑定节点');
          stopNodeMonitoring();
          return;
        }
        
        if (boundNodes[0] !== node) {
          console.warn('监控检测到Transformer绑定了错误的节点');
          try {
            transformer.nodes([node]);
          } catch (rebindError) {
            console.warn('监控重新绑定失败:', rebindError);
            transformer.nodes([]);
            stopNodeMonitoring();
          }
        }
        
      } catch (error) {
        console.warn('节点状态监控出错:', error);
        // 发生错误时停止监控并清理状态
        try {
          const transformer = trRef.current;
          if (transformer) {
            transformer.nodes([]);
          }
        } catch (cleanupError) {
          console.warn('监控错误清理失败:', cleanupError);
        }
        stopNodeMonitoring();
      }
    };
    
    // 每200ms检查一次节点状态
    monitoringIntervalRef.current = setInterval(monitorNodeState, 200);
  }, [isSelected, stopNodeMonitoring]);

  // 防抖的节点绑定函数
  const bindTransformerNode = useCallback(() => {
    const now = Date.now();
    const timeSinceLastBinding = now - lastBindingTimeRef.current;
    
    // 防抖：如果距离上次绑定时间太短，则延迟执行
    if (timeSinceLastBinding < 100) {
      if (bindingTimeoutRef.current) {
        clearTimeout(bindingTimeoutRef.current);
      }
      bindingTimeoutRef.current = setTimeout(() => {
        bindTransformerNode();
      }, 100 - timeSinceLastBinding);
      return;
    }
    
    lastBindingTimeRef.current = now;
    
    const node = shapeRef.current;
    const transformer = trRef.current;
    
    if (!node || !transformer) {
      return;
    }
    
    try {
      // 多层节点验证
      const isNodeValid = (
        node &&
        typeof node === 'object' &&
        node.getStage &&
        typeof node.getStage === 'function' &&
        node.getParent &&
        typeof node.getParent === 'function' &&
        node.getLayer &&
        typeof node.getLayer === 'function'
      );
      
      if (!isNodeValid) {
        console.warn('节点基础验证失败');
        transformer.nodes([]);
        return;
      }
      
      const stage = node.getStage();
      const parent = node.getParent();
      const layer = node.getLayer();
      
      if (!stage || !parent || !layer) {
        console.warn('节点层级验证失败');
        transformer.nodes([]);
        return;
      }
      
      // 检查节点是否真的存在于Layer中
      if (!layer.children || layer.children.indexOf(node) === -1) {
        console.warn('节点不在Layer中');
        transformer.nodes([]);
        return;
      }
      
      // 尝试绑定节点
      transformer.nodes([node]);
      
      // 验证绑定是否成功
      const boundNodes = transformer.nodes();
      if (!boundNodes || boundNodes.length === 0 || boundNodes[0] !== node) {
        console.warn('节点绑定验证失败');
        transformer.nodes([]);
        return;
      }
      
      // 安全地重绘
       if (layer && typeof layer.batchDraw === 'function') {
         layer.batchDraw();
       }
       
       // 绑定成功后启动节点状态监控
       startNodeMonitoring();
       
     } catch (error) {
      console.warn('Transformer节点绑定过程出错:', error);
      try {
        transformer.nodes([]);
      } catch (clearError) {
        console.warn('清除Transformer状态失败:', clearError);
      }
    }
  }, []);
  
  useEffect(() => {
    if (isSelected && trRef.current && shapeRef.current) {
      // 使用防抖的绑定函数
      const timeoutId = setTimeout(() => {
        bindTransformerNode();
      }, 50);
      
      return () => {
        clearTimeout(timeoutId);
        if (bindingTimeoutRef.current) {
          clearTimeout(bindingTimeoutRef.current);
          bindingTimeoutRef.current = null;
        }
      };
    } else {
       // 清除选择时重置transformer和停止监控
       stopNodeMonitoring();
       if (trRef.current) {
         try {
           trRef.current.nodes([]);
           if (bindingTimeoutRef.current) {
             clearTimeout(bindingTimeoutRef.current);
             bindingTimeoutRef.current = null;
           }
         } catch (error) {
           console.warn('Transformer重置失败:', error);
         }
       }
     }
  }, [isSelected, bindTransformerNode, startNodeMonitoring, stopNodeMonitoring]);
  
  // 清理effect，防止内存泄漏
  useEffect(() => {
    return () => {
      stopNodeMonitoring();
      if (trRef.current) {
        try {
          // 安全清理Transformer
          trRef.current.nodes([]);
          // 强制重绘以确保清理生效
          const layer = trRef.current.getLayer();
          if (layer) {
            layer.batchDraw();
          }
        } catch (error) {
          console.warn('Transformer清理失败:', error);
        }
      }
      if (bindingTimeoutRef.current) {
        clearTimeout(bindingTimeoutRef.current);
      }
    };
  }, [stopNodeMonitoring]);

  // 加载图片资源
  useEffect(() => {
    let imageUrl = '';
    
    if (element.type === 'digital-human' && element.properties.avatar) {
      imageUrl = element.properties.avatar;
    } else if (element.type === 'illustration' && element.properties.imageUrl) {
      imageUrl = element.properties.imageUrl;
    } else if (element.type === 'background' && element.properties.backgroundType === 'image' && element.properties.imageUrl) {
      imageUrl = element.properties.imageUrl;
    }
    
    if (imageUrl) {
      let retryCount = 0;
      const maxRetries = 3;
      
      // 重置状态
      setImageLoading(true);
      setImageError(null);
      setImage(null);
      
      const loadImage = () => {
        const img = new window.Image();
        
        // 移除crossOrigin设置以避免OSS跨域问题
        // img.crossOrigin = 'anonymous';
        
        img.onload = () => {
          setImage(img);
          setImageLoading(false);
          setImageError(null);
          console.log('图片加载成功');
        };
        
        img.onerror = (error) => {
          const errorMsg = `图片加载失败 (尝试 ${retryCount + 1}/${maxRetries + 1})`;
          console.warn(errorMsg);
          
          if (retryCount < maxRetries) {
            retryCount++;
            setImageError(`${errorMsg}，正在重试...`);
            // 延迟重试，避免频繁请求
            setTimeout(() => {
              console.log(`重试加载图片 (第${retryCount}次)`);
              loadImage();
            }, 1000 * retryCount); // 递增延迟：1s, 2s, 3s
          } else {
            const finalError = '图片加载最终失败，已达到最大重试次数';
            console.error(finalError);
            setImage(null);
            setImageLoading(false);
            setImageError(finalError);
          }
        };
        
        img.src = imageUrl;
      };
      
      loadImage();
    } else {
      setImage(null);
      setImageLoading(false);
      setImageError(null);
    }
  }, [element.type, element.properties.avatar, element.properties.imageUrl, element.properties.backgroundType]);

  const handleTransformEnd = () => {
    try {
      const node = shapeRef.current;
      if (!node) {
        console.warn('handleTransformEnd: 节点不存在');
        return;
      }

      // 验证节点仍然有效
      if (!node.getStage || typeof node.getStage !== 'function' || !node.getStage()) {
        console.warn('handleTransformEnd: 节点已失效');
        return;
      }

      let scaleX, scaleY, x, y, width, height, rotation;
      
      try {
        scaleX = node.scaleX();
        scaleY = node.scaleY();
        x = node.x();
        y = node.y();
        width = node.width();
        height = node.height();
        rotation = node.rotation();
      } catch (error) {
        console.warn('handleTransformEnd: 获取节点属性失败:', error);
        return;
      }

      try {
        // 重置缩放并更新尺寸
        node.scaleX(1);
        node.scaleY(1);
      } catch (error) {
        console.warn('handleTransformEnd: 重置缩放失败:', error);
        return;
      }

      // 确保onTransform回调存在且有效
      if (typeof onTransform === 'function') {
        try {
          // 对于需要保持比例的元素，以较大的缩放比例为准进行等比缩放
          const isKeepRatio = element.type === 'digital-human' || element.type === 'image';
          let finalWidth = width * scaleX;
          let finalHeight = height * scaleY;
          
          if (isKeepRatio) {
            const scale = Math.max(scaleX, scaleY);
            finalWidth = width * scale;
            finalHeight = height * scale;
          }

          onTransform({
            position: {
              x: x,
              y: y
            },
            size: {
              width: Math.max(5, finalWidth),
              height: Math.max(5, finalHeight)
            },
            rotation: rotation
          });
        } catch (error) {
          console.warn('handleTransformEnd: onTransform回调失败:', error);
        }
      }
    } catch (error) {
      console.warn('handleTransformEnd: 整体处理失败:', error);
    }
  };

  const renderElement = () => {
    const commonProps = {
      ref: shapeRef,
      x: element.position.x,
      y: element.position.y,
      width: element.size.width,
      height: element.size.height,
      rotation: element.rotation,
      draggable: element.type !== 'background', // 背景元素不可拖拽
      onClick: onSelect,
      onTap: onSelect,
      onDragEnd: (e: any) => {
        try {
          const target = e.target;
          if (!target) {
            console.warn('onDragEnd: 拖拽目标不存在');
            return;
          }

          // 验证目标节点仍然有效
          if (!target.getStage || typeof target.getStage !== 'function' || !target.getStage()) {
            console.warn('onDragEnd: 拖拽目标节点已失效');
            return;
          }

          if (typeof onTransform === 'function') {
            try {
              const x = target.x();
              const y = target.y();
              onTransform({
                position: {
                  x: x,
                  y: y
                }
              });
            } catch (error) {
              console.warn('onDragEnd: 获取位置或回调失败:', error);
            }
          }
        } catch (error) {
          console.warn('onDragEnd: 整体处理失败:', error);
        }
      }
    };

    switch (element.type) {
      case 'digital-human':
        if (image) {
          return (
            <Image
              {...commonProps}
              image={image}
              cornerRadius={8}
            />
          );
        } else {
          // 根据加载状态显示不同的占位符
          return (
            <>
              <Rect
                {...commonProps}
                fill={imageLoading ? "#F7FAFC" : imageError ? "#FED7D7" : "#4A5568"}
                stroke={imageLoading ? "#4299E1" : imageError ? "#E53E3E" : "#718096"}
                strokeWidth={2}
                cornerRadius={8}
                dash={imageLoading ? [5, 5] : undefined}
              />
              <Text
                x={element.position.x + element.size.width / 2}
                y={element.position.y + element.size.height / 2}
                text={imageLoading ? "加载中..." : imageError ? "加载失败" : "数字人"}
                fontSize={Math.min(element.size.width, element.size.height) * 0.1}
                fill={imageLoading ? "#4299E1" : imageError ? "#E53E3E" : "#A0AEC0"}
                align="center"
                verticalAlign="middle"
                offsetX={element.size.width / 2}
                offsetY={Math.min(element.size.width, element.size.height) * 0.05}
                fontFamily="Arial"
              />
            </>
          );
        }
      
      case 'background':
        const bgProps = element.properties as BackgroundProperties;
        if (bgProps.backgroundType === 'color') {
          return (
            <Rect
              {...commonProps}
              fill={bgProps.color || '#000000'}
              opacity={bgProps.opacity || 1}
            />
          );
        } else if (bgProps.backgroundType === 'image' && image) {
          return (
            <Image
              {...commonProps}
              image={image}
              opacity={bgProps.opacity || 1}
            />
          );
        } else {
          // 根据加载状态显示不同的背景占位符
          return (
            <>
              <Rect
                {...commonProps}
                fill={imageLoading ? "#F7FAFC" : imageError ? "#FED7D7" : "#2D3748"}
                stroke={imageLoading ? "#4299E1" : imageError ? "#E53E3E" : "#4A5568"}
                strokeWidth={1}
                dash={imageLoading ? [3, 3] : [5, 5]}
              />
              {(imageLoading || imageError) && (
                <Text
                  x={element.position.x + element.size.width / 2}
                  y={element.position.y + element.size.height / 2}
                  text={imageLoading ? "背景加载中..." : "背景加载失败"}
                  fontSize={Math.min(element.size.width, element.size.height) * 0.08}
                  fill={imageLoading ? "#4299E1" : "#E53E3E"}
                  align="center"
                  verticalAlign="middle"
                  offsetX={element.size.width / 2}
                  offsetY={Math.min(element.size.width, element.size.height) * 0.04}
                  fontFamily="Arial"
                />
              )}
            </>
          );
        }
      
      case 'title':
        const titleProps = element.properties as TitleProperties;
        const titleStyle = (() => {
          const bold = (titleProps.fontWeight === 'bold');
          const italic = (titleProps.fontStyle === 'italic');
          if (bold && italic) return 'bold italic';
          if (bold) return 'bold';
          if (italic) return 'italic';
          return 'normal';
        })();
        return (
          <>
            {/* 背景矩形 */}
            {titleProps.backgroundColor && (
              <Rect
                x={element.position.x}
                y={element.position.y}
                width={element.size.width}
                height={element.size.height}
                fill={titleProps.backgroundColor}
                cornerRadius={titleProps.borderRadius || 0}
                opacity={0.9}
              />
            )}
            {/* 文字 */}
            <Text
              {...commonProps}
              text={titleProps.text || '标题文字'}
              fontSize={((titleProps.fontSize || 20) * 2.5) / scale}
              fill={titleProps.color || '#FFFFFF'}
              fontFamily={titleProps.fontFamily || 'Arial'}
              fontStyle={titleStyle}
              skewX={titleStyle.includes('italic') ? 0.15 : 0}
              align={titleProps.textAlign || 'left'}
              verticalAlign="top"
              padding={titleProps.padding ?? 0}
              opacity={titleProps.opacity || 1}
              shadowColor={titleProps.shadow?.color}
              shadowBlur={titleProps.shadow?.blur}
              shadowOffsetX={titleProps.shadow?.offsetX}
              shadowOffsetY={titleProps.shadow?.offsetY}
            />
          </>
        );
      
      case 'illustration':
        const illProps = element.properties as IllustrationProperties;
        if (image) {
          return (
            <Image
              {...commonProps}
              image={image}
              opacity={illProps.opacity || 1}
              cornerRadius={illProps.borderRadius || 0}
              // 暂时移除滤镜功能，Konva不支持CSS滤镜
              // filters={illProps.filter ? [Konva.Filters.Blur] : undefined}
            />
          );
        } else {
          // 根据加载状态显示不同的占位符
          return (
            <>
              <Rect
                {...commonProps}
                fill={imageLoading ? "#F7FAFC" : imageError ? "#FED7D7" : "#E2E8F0"}
                stroke={imageLoading ? "#4299E1" : imageError ? "#E53E3E" : "#CBD5E0"}
                strokeWidth={2}
                cornerRadius={illProps.borderRadius || 0}
                dash={imageLoading ? [5, 5] : undefined}
              />
              <Text
                x={element.position.x + element.size.width / 2}
                y={element.position.y + element.size.height / 2}
                text={imageLoading ? "加载中..." : imageError ? "加载失败" : "插图"}
                fontSize={Math.min(element.size.width, element.size.height) * 0.1}
                fill={imageLoading ? "#4299E1" : imageError ? "#E53E3E" : "#718096"}
                align="center"
                verticalAlign="middle"
                offsetX={element.size.width / 2}
                offsetY={Math.min(element.size.width, element.size.height) * 0.05}
                fontFamily="Arial"
              />
            </>
          );
        }
      
      case 'text':
        return (
          <Text
            {...commonProps}
            text={element.properties.text || '文本内容'}
            fontSize={element.properties.fontSize || 16}
            fill={element.properties.color || '#FFFFFF'}
            fontFamily={element.properties.fontFamily || 'Arial'}
          />
        );
      
      case 'shape':
        return (
          <Rect
            {...commonProps}
            fill={element.properties.fill || '#3182CE'}
            stroke={element.properties.stroke || '#2C5282'}
            strokeWidth={element.properties.strokeWidth || 2}
            cornerRadius={element.properties.cornerRadius || 0}
          />
        );
      
      case 'image':
        // 通用图片元素
        if (image) {
          return (
            <Image
              {...commonProps}
              image={image}
            />
          );
        } else {
          return (
            <Rect
              {...commonProps}
              fill="#E2E8F0"
              stroke="#CBD5E0"
              strokeWidth={1}
            />
          );
        }
      
      case 'voice':
        // 配音元素显示为音频图标
        return (
          <>
            <Rect
              {...commonProps}
              fill="#4C51BF"
              stroke="#6366F1"
              strokeWidth={2}
              cornerRadius={8}
            />
            <Text
              x={element.position.x + element.size.width / 2}
              y={element.position.y + element.size.height / 2}
              text="🎤"
              fontSize={Math.min(element.size.width, element.size.height) * 0.4}
              align="center"
              verticalAlign="middle"
              offsetX={Math.min(element.size.width, element.size.height) * 0.2}
              offsetY={Math.min(element.size.width, element.size.height) * 0.2}
            />
          </>
        );
      
      case 'video':
        // 视频元素
        if (image) {
          return (
            <Image
              {...commonProps}
              image={image}
              opacity={element.properties.opacity || 1}
              // 暂时移除滤镜功能，Konva不支持CSS滤镜
               // filters={element.properties.filter && element.properties.filter !== 'none' ? [Konva.Filters.Blur] : undefined}
            />
          );
        } else {
          return (
            <>
              <Rect
                {...commonProps}
                fill="#1A202C"
                stroke="#4A5568"
                strokeWidth={2}
                cornerRadius={4}
              />
              <Text
                x={element.position.x + element.size.width / 2}
                y={element.position.y + element.size.height / 2}
                text="🎬"
                fontSize={Math.min(element.size.width, element.size.height) * 0.3}
                align="center"
                verticalAlign="middle"
                offsetX={Math.min(element.size.width, element.size.height) * 0.15}
                offsetY={Math.min(element.size.width, element.size.height) * 0.15}
              />
            </>
          );
        }
      
      case 'music':
        // 音乐元素显示为音乐图标和波形
        return (
          <>
            <Rect
              {...commonProps}
              fill="#065F46"
              stroke="#10B981"
              strokeWidth={2}
              cornerRadius={8}
            />
            <Text
              x={element.position.x + 10}
              y={element.position.y + 10}
              text="🎵"
              fontSize={20}
              fill="#10B981"
            />
            <Text
              x={element.position.x + 40}
              y={element.position.y + 15}
              text={element.properties.name || '背景音乐'}
              fontSize={14}
              fill="#FFFFFF"
              fontFamily="Arial"
            />
            <Text
              x={element.position.x + 40}
              y={element.position.y + 35}
              text={element.properties.artist || '未知艺术家'}
              fontSize={10}
              fill="#9CA3AF"
              fontFamily="Arial"
            />
          </>
        );
      
      case 'flash-text':
        const flashProps = element.properties;
        const flashStyle = (() => {
          const bold = (flashProps.fontWeight === 'bold' || flashProps.bold === '1');
          const italic = (flashProps.fontStyle === 'italic' || flashProps.italic === 1 || flashProps.italic === '1');
          if (bold && italic) return 'bold italic';
          if (bold) return 'bold';
          if (italic) return 'italic';
          return 'normal';
        })();
        return (
          <>
            {/* 背景 */}
            {flashProps.backgroundColor && flashProps.backgroundColor !== 'transparent' && (
              <Rect
                x={element.position.x}
                y={element.position.y}
                width={element.size.width}
                height={element.size.height}
                fill={flashProps.backgroundColor}
                cornerRadius={4}
                opacity={0.9}
              />
            )}
            {/* 闪文文字 */}
            <Text
              {...commonProps}
              text={flashProps.text || '闪文效果'}
              fontSize={(flashProps.fontSize || 48) / scale}
              fill={flashProps.textColor || '#FFFFFF'}
              fontFamily={flashProps.fontFamily || 'Arial'}
              fontStyle={flashStyle}
              skewX={flashStyle.includes('italic') ? 0.15 : 0}
              align={flashProps.textAlign || 'center'}
              verticalAlign="middle"
              shadowColor={flashProps.textShadow ? 'rgba(0,0,0,0.5)' : undefined}
              shadowBlur={flashProps.textShadow ? 4 : 0}
              shadowOffsetX={flashProps.textShadow ? 2 : 0}
              shadowOffsetY={flashProps.textShadow ? 2 : 0}
              // 暂时移除发光效果，Konva不支持CSS滤镜
               // filters={flashProps.glowEffect ? [Konva.Filters.Blur] : undefined}
            />
          </>
        );
      
      case 'subtitle':
        // 字幕元素
        const subtitleProps = element.properties;
        const subItalic = (subtitleProps.italic === 1 || subtitleProps.italic === '1');
        const subBold = (subtitleProps.bold === '1');
        const subStyle = (() => {
          if (subBold && subItalic) return 'bold italic';
          if (subBold) return 'bold';
          if (subItalic) return 'italic';
          return 'normal';
        })();
        return (
          <>
            <Rect
              {...commonProps}
              fill={'rgba(0,0,0,0.6)'}
              cornerRadius={4}
            />
            <Text
              x={element.position.x + 16}
              y={element.position.y + 12}
              width={element.size.width - 32}
              height={element.size.height - 24}
              text={subtitleProps.text || '字幕示例文本'}
              fontSize={parseInt(subtitleProps.fontsize || '35', 10)}
              fill={subtitleProps.primaryColour || '#FFFFFF'}
              fontFamily={subtitleProps.fontname || 'Arial'}
              fontStyle={subStyle}
              skewX={subItalic ? 0.15 : 0}
              letterSpacing={parseInt(String(subtitleProps.wordSpace ?? 0), 10)}
              fontVariant={'normal'}
              align={'center'}
              verticalAlign={'middle'}
            />
            {subBold && (
              <Text
                x={element.position.x + 16}
                y={element.position.y + 12}
                width={element.size.width - 32}
                height={element.size.height - 24}
                text={subtitleProps.text || '字幕示例文本'}
                fontSize={parseInt(subtitleProps.fontsize || '35', 10)}
                fill={subtitleProps.primaryColour || '#FFFFFF'}
                fontFamily={subtitleProps.fontname || 'Arial'}
                fontStyle={subStyle}
                skewX={subItalic ? 0.15 : 0}
                align={'center'}
                verticalAlign={'middle'}
                stroke={subtitleProps.primaryColour || '#FFFFFF'}
                strokeWidth={1}
              />
            )}
            {(subtitleProps.underline === 1 || subtitleProps.underline === '1') && (
              <Line
                points={[
                  element.position.x + 24,
                  element.position.y + element.size.height - 18,
                  element.position.x + element.size.width - 24,
                  element.position.y + element.size.height - 18
                ]}
                stroke={subtitleProps.primaryColour || '#FFFFFF'}
                strokeWidth={2}
              />
            )}
          </>
        );
      
      default:
        return null;
    }
  };

  return (
    <>
      {renderElement()}
      {isSelected && element.type !== 'background' && (
        <Transformer
          ref={trRef}
          keepRatio={element.type === 'digital-human' || element.type === 'image'}
          boundBoxFunc={(oldBox, newBox) => {
            try {
              // 验证当前绑定的节点是否仍然有效
              const transformer = trRef.current;
              if (transformer) {
                const nodes = transformer.nodes();
                if (nodes && nodes.length > 0) {
                  const node = nodes[0];
                  if (!node || !node.getStage || !node.getStage()) {
                    console.warn('boundBoxFunc: 节点已失效，返回原始尺寸');
                    return oldBox;
                  }
                }
              }
              
              // 限制最小尺寸
              if (!newBox || newBox.width < 5 || newBox.height < 5) {
                return oldBox;
              }
              
              return newBox;
            } catch (error) {
              console.warn('boundBoxFunc错误:', error);
              return oldBox;
            }
          }}
          onTransformStart={() => {
            try {
              // 变换开始时验证节点状态
              const node = shapeRef.current;
              const transformer = trRef.current;
              
              if (!node || !transformer) {
                console.warn('onTransformStart: 节点或Transformer不存在');
                return;
              }
              
              if (!node.getStage || !node.getStage()) {
                console.warn('onTransformStart: 节点已失效');
                transformer.nodes([]);
                return;
              }
            } catch (error) {
              console.warn('onTransformStart错误:', error);
            }
          }}
          onTransform={() => {
            try {
              // 变换过程中持续验证节点状态
              const node = shapeRef.current;
              const transformer = trRef.current;
              
              if (!node || !transformer) {
                return;
              }
              
              // 检查节点是否仍然有效
              if (!node.getStage || typeof node.getStage !== 'function') {
                console.warn('onTransform: 节点方法不可用');
                transformer.nodes([]);
                return;
              }
              
              const stage = node.getStage();
              if (!stage) {
                console.warn('onTransform: 节点已从舞台移除');
                transformer.nodes([]);
                return;
              }
              
              // 验证Transformer绑定的节点
              const boundNodes = transformer.nodes();
              if (!boundNodes || boundNodes.length === 0 || boundNodes[0] !== node) {
                console.warn('onTransform: Transformer节点绑定异常');
                // 尝试重新绑定
                try {
                  transformer.nodes([node]);
                } catch (rebindError) {
                  console.warn('onTransform: 重新绑定失败:', rebindError);
                  transformer.nodes([]);
                }
              }
            } catch (error) {
              console.warn('onTransform错误:', error);
              // 发生错误时清除Transformer状态
              try {
                const transformer = trRef.current;
                if (transformer) {
                  transformer.nodes([]);
                }
              } catch (clearError) {
                console.warn('onTransform清理失败:', clearError);
              }
            }
          }}
          onTransformEnd={handleTransformEnd}
        />
      )}
    </>
  );
};

const Canvas: React.FC = () => {
  // 登录状态检测
  const { isAuthenticated, isChecking } = useAuthGuard({
    autoRedirect: true,
    checkInterval: 30000, // 30秒检查一次
    onUnauthorized: () => {
      console.log('Canvas: 检测到用户未登录');
    }
  });

  const currentProject = useCurrentProject();
  const selectedElementId = useSelectedElement();
  const { updateElement, selectElement, deleteElement } = useActions();
  const [displaySize, setDisplaySize] = useState({ width: 800, height: 600 }); // 设置默认尺寸
  const [scale, setScale] = useState(0.5); // 更合理的初始缩放值
  const [isCanvasReady, setIsCanvasReady] = useState(true); // 默认为true，确保画布能显示
  const containerRef = useRef<HTMLDivElement>(null);

  // 获取当前页面
  const currentPage = currentProject?.pages.find(p => p.id === currentProject.currentPageId);
  const elements = currentPage?.elements || [];

  // 获取当前页面的字幕配置
  const defaultSubtitleConfig = {
    isEnabled: false,
    contentType: 'subtitles',
    primaryColour: '#800000',
    fontname: 'STSONG',
    bold: '1',
    subtitleUsepx: 'Y',
    fontsize: '35',
    underline: 1,
    italic: 1,
    bottomSpace: 10,
    wordSpace: 0
  };
  
  const subtitlePreview = currentPage?.subtitleConfig || defaultSubtitleConfig;

  // 获取文案的第一句话
  const getFirstSentence = (text: string): string => {
    if (!text) return '';
    const sentences = text.split(/[。！？.!?]/);
    return sentences[0] + (sentences.length > 1 ? '。' : '');
  };

  const subtitleText = currentPage?.script ? getFirstSentence(currentPage.script) : '';

  // 根据页面比例确定固定的画布尺寸
  const getCanvasSize = () => {
    const aspectRatio = currentPage?.aspectRatio || '16:9';
    if (aspectRatio === '9:16') {
      return { width: 1080, height: 1920 }; // 竖屏
    } else {
      return { width: 1920, height: 1080 }; // 横屏
    }
  };

  const canvasSize = getCanvasSize();

  // 响应式调整显示尺寸和缩放比例
  useEffect(() => {
    const updateSize = () => {
      console.log('Canvas: 开始更新尺寸');
      if (containerRef.current) {
        const container = containerRef.current;
        const containerWidth = container.offsetWidth;
        const containerHeight = container.offsetHeight;
        
        console.log('Canvas: 容器尺寸', { containerWidth, containerHeight });
        
        // 放宽条件，即使容器尺寸为0也尝试设置默认值
        if (containerWidth > 0 && containerHeight > 0) {
          // 计算缩放比例，确保画布完整显示在容器中
          const scaleX = containerWidth / canvasSize.width;
          const scaleY = containerHeight / canvasSize.height;
          const newScale = Math.min(scaleX, scaleY, 1); // 最大不超过1倍
          
          console.log('Canvas: 计算缩放', { scaleX, scaleY, newScale });
          
          setScale(newScale);
          setDisplaySize({
            width: canvasSize.width * newScale,
            height: canvasSize.height * newScale
          });
        } else {
          // 容器尺寸无效时使用默认值
          console.log('Canvas: 使用默认尺寸');
          const defaultScale = Math.min(800 / canvasSize.width, 600 / canvasSize.height, 1);
          setScale(defaultScale);
          setDisplaySize({
            width: canvasSize.width * defaultScale,
            height: canvasSize.height * defaultScale
          });
        }
        setIsCanvasReady(true); // 确保画布标记为准备好
      } else {
        console.log('Canvas: 容器引用不存在，使用默认设置');
        // 即使没有容器引用也设置默认值
        const defaultScale = Math.min(800 / canvasSize.width, 600 / canvasSize.height, 1);
        setScale(defaultScale);
        setDisplaySize({
          width: canvasSize.width * defaultScale,
          height: canvasSize.height * defaultScale
        });
        setIsCanvasReady(true);
      }
    };

    // 立即执行一次
    updateSize();
    
    // 延迟执行以确保DOM完全渲染
    const timer = setTimeout(updateSize, 100);
    
    // 添加超时机制，确保画布最终能显示
    const fallbackTimer = setTimeout(() => {
      console.log('Canvas: 超时机制触发，强制显示画布');
      setIsCanvasReady(true);
    }, 1000);
    
    window.addEventListener('resize', updateSize);
    return () => {
      clearTimeout(timer);
      clearTimeout(fallbackTimer);
      window.removeEventListener('resize', updateSize);
    };
  }, [canvasSize.width, canvasSize.height]);

  const handleStageClick = (e: any) => {
    console.log('Stage clicked，targetClassName:', e.target.className);
    
    // 更准确的空白区域判断
    const isStage = e.target === e.target.getStage();
    const isBackground = e.target.name && e.target.name() === 'canvas-background';
    const clickedOnEmpty = isStage || isBackground;
    
    console.log('Click analysis:', {
      isStage,
      isBackground,
      clickedOnEmpty,
      currentSelection: selectedElementId
    });
    
    if (clickedOnEmpty) {
      console.log('Clearing selection...');
      selectElement(null);
    }
  };

  const handleElementSelect = (elementId: string) => {
    selectElement(elementId);
  };

  const handleElementTransform = (elementId: string, updates: Partial<SceneElement>) => {
    updateElement(elementId, updates);
  };

  /**
   * 绑定 Backspace 删除快捷键
   * - 当存在选中元素且按下 Backspace 时，调用 store.deleteElement 删除该元素
   * - 避免在输入框等可编辑元素中误触，检测事件目标是否为可编辑节点
   * - 组件卸载时移除事件监听，防止内存泄漏
   */
  useEffect(() => {
    const isEditableTarget = (target: EventTarget | null): boolean => {
      if (!target || !(target as any).closest) return false;
      try {
        const el = (target as Element);
        const editableSelectors = 'input, textarea, [contenteditable="true"], select';
        return !!el.closest(editableSelectors);
      } catch {
        return false;
      }
    };

    const handleKeyDown = (e: KeyboardEvent) => {
      // 仅处理 Backspace
      if (e.key !== 'Backspace') return;

      // 输入框内保留默认行为
      if (isEditableTarget(e.target)) return;

      // 需要选中元素
      if (!selectedElementId) return;

      // 阻止页面后退等默认行为
      e.preventDefault();
      deleteElement(selectedElementId);
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [selectedElementId, deleteElement]);

  // 如果正在检查登录状态，显示加载界面
  if (isChecking) {
    return (
      <div className="flex flex-1 items-center justify-center bg-slate-100">
        <div className="text-center">
          <div className="mx-auto mb-4 h-12 w-12 animate-spin rounded-full border-2 border-slate-200 border-t-teal-600"></div>
          <p className="text-slate-600">正在验证登录状态...</p>
        </div>
      </div>
    );
  }

  // 如果未登录，显示提示信息（实际上会被autoRedirect处理）
  if (!isAuthenticated) {
    return (
      <div className="flex flex-1 items-center justify-center bg-slate-100">
        <div className="text-center">
          <p className="mb-4 text-slate-600">请先登录后使用画布编辑功能</p>
          <button 
            onClick={() => window.location.reload()}
            className="app-button-primary rounded-lg px-4 py-2 transition-colors"
          >
            返回登录
          </button>
        </div>
      </div>
    );
  }

  return (
    <div 
      ref={containerRef}
      className="app-canvas-grid relative m-0 flex h-full w-full flex-1 items-center justify-center overflow-hidden p-0"
    >
      {/* 画布容器 - 始终显示 */}
      <div 
        className="relative rounded-lg shadow-2xl shadow-slate-900/20 ring-1 ring-slate-300/60"
        style={{
          width: displaySize.width,
          height: displaySize.height
        }}
      >
        {/* 调试信息 */}
        {process.env.NODE_ENV === 'development' && (
          <div className="absolute left-2 top-2 z-50 rounded bg-slate-900/70 p-2 text-xs text-white">
            <div>Ready: {isCanvasReady ? 'Yes' : 'No'}</div>
            <div>Size: {displaySize.width}x{displaySize.height}</div>
            <div>Scale: {scale.toFixed(2)}</div>
            <div>Canvas: {canvasSize.width}x{canvasSize.height}</div>
          </div>
        )}
        
        {/* Konva Stage */}
        <Stage
          width={canvasSize.width}
          height={canvasSize.height}
          onClick={handleStageClick}
          onTap={handleStageClick}
          scaleX={scale}
          scaleY={scale}
        >
        <Layer>
          {/* 画布背景 */}
          <Rect
            name="canvas-background"
            x={0}
            y={0}
            width={canvasSize.width}
            height={canvasSize.height}
            fill="#ffffff"
          />
          
          {/* 渲染所有元素 - 按zIndex从低到高排序 */}
          {[...elements].sort((a, b) => a.zIndex - b.zIndex).map((element) => (
            <CanvasElement
              key={element.id}
              element={element}
              isSelected={element.id === selectedElementId}
              onSelect={() => handleElementSelect(element.id)}
              onTransform={(updates) => handleElementTransform(element.id, updates)}
              scale={scale}
            />
          ))}
          
          {/* 字幕预览 */}
          {subtitleText && subtitlePreview.isEnabled && (() => {
            const availableWidth = canvasSize.width - 100;
            const fontSizePx = parseInt(subtitlePreview.fontsize);
            const spacing = parseInt(String(subtitlePreview.wordSpace ?? 0), 10);
            const perChar = Math.max(1, fontSizePx + spacing);
            const maxChars = Math.max(0, Math.floor(availableWidth / perChar));
            const renderText = subtitleText.slice(0, maxChars);
            return (
              <Text
                x={50}
                y={canvasSize.height - Number(subtitlePreview.bottomSpace) - parseInt(subtitlePreview.fontsize)}
                width={canvasSize.width - 100}
                text={renderText}
                fontSize={fontSizePx}
                fontFamily={subtitlePreview.fontname}
                fill={subtitlePreview.primaryColour}
                fontStyle={`${subtitlePreview.bold === '1' ? 'bold' : 'normal'} ${subtitlePreview.italic === 1 ? 'italic' : 'normal'}`}
                textDecoration={subtitlePreview.underline === 1 ? 'underline' : ''}
                letterSpacing={spacing}
                align="center"
                verticalAlign="top"
                listening={false}
                opacity={0.8}
              />
            );
          })()}
        </Layer>
        </Stage>
      </div>
      

      
      {/* 空状态提示 */}
      {elements.length === 0 && (
        <div className="absolute inset-0 flex items-center justify-center">
          <div className="rounded-lg border border-slate-200 bg-white/90 px-8 py-7 text-center text-slate-600 shadow-lg">
            <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-teal-50 text-2xl">🎬</div>
            <h3 className="mb-2 text-xl font-semibold text-slate-900">开始创作数字人视频</h3>
            <p className="text-sm text-slate-500">
              从右侧数字人库选择角色，或使用顶部工具栏添加其他元素
            </p>
          </div>
        </div>
      )}
      

    </div>
  );
};

export default Canvas;
