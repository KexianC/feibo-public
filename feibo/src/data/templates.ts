import { ApplicationTemplate, SceneElement } from '../types';

// 生成唯一ID的辅助函数
const generateId = () => Math.random().toString(36).substr(2, 9);

// 预设模板数据
export const predefinedTemplates: ApplicationTemplate[] = [
  

];

// 根据分类获取模板
export const getTemplatesByCategory = (category: string): ApplicationTemplate[] => {
  if (category === 'all') {
    return predefinedTemplates;
  }
  return predefinedTemplates.filter(template => template.category === category);
};

// 获取热门模板
export const getPopularTemplates = (): ApplicationTemplate[] => {
  return predefinedTemplates.filter(template => template.isPopular);
};

// 根据ID获取模板
export const getTemplateById = (id: string): ApplicationTemplate | undefined => {
  return predefinedTemplates.find(template => template.id === id);
};