import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

// 计算文本尺寸的辅助函数
export function calculateTextSize(text: string, fontSize: number, fontFamily: string = 'Arial') {
  // 创建一个临时的canvas元素来测量文本尺寸
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');
  
  if (!context) {
    // 如果无法获取context，使用估算方法
    const charWidth = fontSize; // 估算字符宽度
    const lineHeight = fontSize * 1.2; // 估算行高
    const lines = text.split('\n');
    const maxLineLength = Math.max(...lines.map(line => line.length));
    
    return {
      width: Math.max(200, maxLineLength * charWidth + 60), // 最小宽度200，加40像素padding
      height: Math.max(60, lines.length * lineHeight + 40) // 最小高度60，加40像素padding
    };
  }
  
  // 设置字体样式
  context.font = `${fontSize}px ${fontFamily}`;
  
  // 分割文本为多行
  const lines = text.split('\n');
  
  // 计算每行的宽度，取最大值
  let maxWidth = 0;
  lines.forEach(line => {
    const metrics = context.measureText(line);
    maxWidth = Math.max(maxWidth, metrics.width);
  });
  
  // 计算总高度
  const lineHeight = fontSize * 1.2; // 行高通常是字体大小的1.2倍
  const totalHeight = lines.length * lineHeight;
  
  return {
    width: Math.max(200, Math.ceil(maxWidth) + 40), // 最小宽度200，加40像素padding
    height: Math.max(60, Math.ceil(totalHeight) + 40) // 最小高度60，加40像素padding
  };
}
