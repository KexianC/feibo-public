import { readVoiceDataFromExcel, getDefaultVoiceOptions, VoiceOption } from '../utils/excelReader';

/**
 * 语音数据服务类
 */
export class VoiceDataService {
  private static instance: VoiceDataService;
  private voiceOptions: VoiceOption[] = [];
  private isLoaded = false;

  private constructor() {}

  public static getInstance(): VoiceDataService {
    if (!VoiceDataService.instance) {
      VoiceDataService.instance = new VoiceDataService();
    }
    return VoiceDataService.instance;
  }

  /**
   * 加载语音数据
   * @param forceReload 是否强制重新加载
   */
  public async loadVoiceData(forceReload = false): Promise<VoiceOption[]> {
    if (this.isLoaded && !forceReload) {
      return this.voiceOptions;
    }

    try {
      // 尝试读取Excel文件
      const excelPath = '/发音人调用名称.xlsx'; // 相对于public目录
      console.log('尝试读取Excel文件:', excelPath);
      
      this.voiceOptions = await readVoiceDataFromExcel(excelPath);
      console.log(`成功从Excel加载了 ${this.voiceOptions.length} 个发音人数据`);
      
      this.isLoaded = true;
      return this.voiceOptions;
    } catch (error) {
      console.warn('从Excel加载语音数据失败，使用默认数据:', error);
      
      // 如果Excel读取失败，使用默认数据
      this.voiceOptions = getDefaultVoiceOptions();
      this.isLoaded = true;
      return this.voiceOptions;
    }
  }

  /**
   * 获取当前语音数据
   */
  public getVoiceOptions(): VoiceOption[] {
    return this.voiceOptions;
  }

  /**
   * 根据筛选条件获取语音数据
   */
  public getFilteredVoiceOptions(filters: {
    searchTerm?: string;
    genderFilter?: string;
    ageFilter?: string;
    fieldFilter?: string;
    styleFilter?: string;
    languageFilter?: string;
  }): VoiceOption[] {
    let filtered = this.voiceOptions;

    // 搜索过滤
    if (filters.searchTerm) {
      const searchLower = filters.searchTerm.toLowerCase();
      filtered = filtered.filter(voice => 
        voice.name.toLowerCase().includes(searchLower)
      );
    }

    // 性别过滤
    if (filters.genderFilter && filters.genderFilter !== 'all') {
      filtered = filtered.filter(voice => voice.gender === filters.genderFilter);
    }

    // 年龄过滤
    if (filters.ageFilter && filters.ageFilter !== 'all') {
      filtered = filtered.filter(voice => voice.age === filters.ageFilter);
    }

    // 领域过滤
    if (filters.fieldFilter && filters.fieldFilter !== 'all') {
      filtered = filtered.filter(voice => voice.field === filters.fieldFilter);
    }

    // 语言过滤
    if (filters.languageFilter && filters.languageFilter !== 'all') {
      filtered = filtered.filter(voice => voice.language === filters.languageFilter);
    }

    // 风格过滤
    if (filters.styleFilter && filters.styleFilter !== 'all') {
      filtered = filtered.filter(voice => voice.style === filters.styleFilter);
    }

    return filtered;
  }

  /**
   * 获取所有可用的筛选选项
   */
  public getFilterOptions() {
    const voices = this.voiceOptions;
    
    return {
      genders: [...new Set(voices.map(v => v.gender))],
      ages: [...new Set(voices.map(v => v.age).filter(Boolean))],
      fields: [...new Set(voices.map(v => v.field).filter(Boolean))],
      styles: [...new Set(voices.map(v => v.style).filter(Boolean))],
      languages: [...new Set(voices.map(v => v.language).filter(Boolean))]
    };
  }

  /**
   * 重置数据
   */
  public reset(): void {
    this.voiceOptions = [];
    this.isLoaded = false;
  }
}

// 导出单例实例
export const voiceDataService = VoiceDataService.getInstance();