let xlsxModulePromise: Promise<typeof import('xlsx')> | null = null;

async function loadXlsx() {
  if (!xlsxModulePromise) {
    xlsxModulePromise = import('xlsx');
  }

  return xlsxModulePromise;
}

export interface VoiceData {
  name: string;
  gender: string;
  age: string;
  styleCallName: string;
  field: string;
  styleName: string;
  language: string;
}

export interface VoiceOption {
  id: string;
  name: string;
  gender: 'male' | 'female';
  language: string;
  style: string;
  age?: string;
  field?: string;
  demoUrl?: string;
  magicSpeakerId?: string; // 风格调用名参数
}

/**
 * 读取Excel文件并解析发音人数据
 * @param filePath Excel文件路径
 * @returns Promise<VoiceOption[]>
 */
export async function readVoiceDataFromExcel(filePath: string): Promise<VoiceOption[]> {
  try {
    const XLSX = await loadXlsx();
    // 在浏览器环境中使用fetch读取文件
    const response = await fetch(filePath);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    const arrayBuffer = await response.arrayBuffer();
    const workbook = XLSX.read(arrayBuffer, { type: 'array' });
    const sheetName = workbook.SheetNames[0];
    const worksheet = workbook.Sheets[sheetName];
    
    // 将工作表转换为JSON数据
    const jsonData = XLSX.utils.sheet_to_json(worksheet, { header: 1 }) as any[][];
    
    // 跳过标题行，从第二行开始处理数据
    const dataRows = jsonData.slice(1);
    
    const voiceOptions: VoiceOption[] = [];
    
    dataRows.forEach((row, index) => {
      if (row && row.length >= 7) {
        const [name, gender, age, styleCallName, field, styleName, language] = row;
        
        // 跳过空行
        if (!name || !gender || !language) return;
        
        // 转换性别格式
        let genderValue: 'male' | 'female';
        if (gender === '男' || gender === 'male' || gender === 'M') {
          genderValue = 'male';
        } else if (gender === '女' || gender === 'female' || gender === 'F') {
          genderValue = 'female';
        } else {
          console.warn(`未知性别格式: ${gender}，默认设为female`);
          genderValue = 'female';
        }
        
        voiceOptions.push({
          id: `voice_${index + 1}`,
          name: String(name).trim(),
          gender: genderValue,
          language: String(language).trim(),
          style: String(styleName || styleCallName || '标准').trim(),
          age: String(age || '').trim(),
          field: String(field || '').trim(),
          demoUrl: `https://example.com/demo_${index + 1}.mp3`,
          magicSpeakerId: String(styleCallName || '').trim() // 将风格调用名映射为magicSpeakerId
        });
      }
    });
    
    return voiceOptions;
  } catch (error) {
    console.error('读取Excel文件失败:', error);
    throw error;
  }
}

/**
 * 获取默认的发音人数据（作为备用）
 */
export function getDefaultVoiceOptions(): VoiceOption[] {
  return [
    {
      id: 'voice1',
      name: '小雅',
      gender: 'female',
      language: '中文',
      style: '甜美',
      age: 'youth',
      field: 'live',
      demoUrl: 'https://example.com/demo1.mp3',
      magicSpeakerId: 'xiaoya_sweet_24k'
    },
    {
      id: 'voice2',
      name: '小明',
      gender: 'male',
      language: '中文',
      style: '标准',
      age: 'middle',
      field: 'news',
      demoUrl: 'https://example.com/demo2.mp3',
      magicSpeakerId: 'xiaoming_standard_24k'
    },
    {
      id: 'voice3',
      name: '小慧',
      gender: 'female',
      language: '中文',
      style: '温柔',
      age: 'youth',
      field: 'film',
      demoUrl: 'https://example.com/demo3.mp3',
      magicSpeakerId: 'xiaohui_gentle_24k'
    },
    {
      id: 'voice4',
      name: '小强',
      gender: 'male',
      language: '中文',
      style: '磁性',
      age: 'middle',
      field: 'game',
      demoUrl: 'https://example.com/demo4.mp3',
      magicSpeakerId: 'xiaoqiang_magnetic_24k'
    },
    {
      id: 'voice5',
      name: 'Emma',
      gender: 'female',
      language: '英文',
      style: 'Professional',
      age: 'youth',
      field: 'news',
      demoUrl: 'https://example.com/demo5.mp3',
      magicSpeakerId: 'emma_professional_24k'
    },
    {
      id: 'voice6',
      name: 'John',
      gender: 'male',
      language: '英文',
      style: 'Professional',
      age: 'middle',
      field: 'film',
      demoUrl: 'https://example.com/demo6.mp3',
      magicSpeakerId: 'john_professional_24k'
    },
    {
      id: 'voice7',
      name: '小童',
      gender: 'female',
      language: '中文',
      style: '活泼',
      age: 'child',
      field: 'game',
      demoUrl: 'https://example.com/demo7.mp3',
      magicSpeakerId: 'xiaotong_lively_24k'
    },
    {
      id: 'voice8',
      name: '老王',
      gender: 'male',
      language: '中文',
      style: '磁性',
      age: 'elderly',
      field: 'food',
      demoUrl: 'https://example.com/demo8.mp3',
      magicSpeakerId: 'laowang_magnetic_24k'
    }
  ];
}
