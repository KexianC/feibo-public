export interface MaterialPage<T> {
    records: T[];
    total: number;
    pages: number;
    size: number;
    current: number;
}
export function requireSaved(value: unknown, action = '保存'): void {
    if (value !== true)
        throw new Error(`${action}未完成，请刷新后核实记录`);
}
export function requireRecord(value: any): any {
    if (!value || typeof value !== 'object' || value.id == null) {
        throw new Error('服务器未返回有效记录');
    }
    return value;
}
export function mapMaterialPage<T>(data: any, map: (record: any) => T): MaterialPage<T> {
    if (!data || !Array.isArray(data.records))
        throw new Error('分页响应格式异常');
    return { ...data, records: data.records.map(map), total: Number(data.total), current: Number(data.current), size: Number(data.size), pages: Number(data.pages) };
}
export function materialQuery(page: number, size: number, title?: string, nameKey = 'title'): string {
    const query = new URLSearchParams({ pageNum: String(page), pageSize: String(size) });
    if (title?.trim())
        query.set(nameKey, title.trim());
    return query.toString();
}
export function materialFile(data: FormData, key: string, file: File | undefined, label: string): void {
    if (!file || file.size === 0)
        throw new Error(`请选择有效的${label}`);
    data.append(key, file);
}
export function materialName(value: string): string {
    if (!value?.trim())
        throw new Error('请输入名称');
    return value.trim();
}
export function modelImportData(text: string): string {
    let parsed: any;
    try {
        parsed = JSON.parse(text);
    }
    catch {
        throw new Error('文件不是有效的 JSON');
    }
    const records = parsed?.data?.records;
    if (!Array.isArray(records) || records.length === 0 || records.length > 500) {
        throw new Error('导入文件需包含 data.records，且每次为 1 至 500 条模型记录');
    }
    if (records.some((record: any) => !record?.id || !record?.speakerName || !record?.speakerId)) {
        throw new Error('每条模型记录必须包含 id、speakerName、speakerId');
    }
    return text;
}
