import React, { useEffect, useState } from 'react';
import { X, FileText, RefreshCw } from 'lucide-react';
import {
  getCopywritingListByStatus,
  APPROVAL_STATUS,
  APPROVAL_STATUS_TEXT,
  type CopywritingItem,
  type PaginationData
} from '../services/copywritingApi';

const formatCreateTime = (s: string) => {
  if (!s) return '';
  const m = s.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})/);
  if (m) return `${m[1]} ${m[2]}`;
  const d = new Date(s);
  if (isNaN(d.getTime())) return s;
  const pad = (n: number) => n.toString().padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

interface CopywritingSelectionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (content: string, title?: string) => void;
}

/**
 * 文案选择弹窗（用于从智核医审中选择已提交审核的文案）
 * 功能：
 * - 默认展示审核完成的文案列表
 * - 支持分页加载（上一页/下一页）
 * - 点击“选择”后将文案内容回填到外层脚本输入框
 */
const CopywritingSelectionModal: React.FC<CopywritingSelectionModalProps> = ({
  isOpen,
  onClose,
  onSelect
}) => {
  const [status, setStatus] = useState<number>(APPROVAL_STATUS.APPROVED);
  const [page, setPage] = useState<number>(1);
  const [size, setSize] = useState<number>(10);
  const [items, setItems] = useState<CopywritingItem[]>([]);
  const [hasNext, setHasNext] = useState<boolean>(false);
  const [total, setTotal] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * 加载文案列表（按审批状态）
   * @param s 审批状态（0-5）
   * @param p 页码
   * @param sz 每页大小
   */
  const loadCopywritings = async (s: number, p: number, sz: number) => {
    setIsLoading(true);
    setError(null);
    try {
      const data: PaginationData = await getCopywritingListByStatus({ status: s, page: p, size: sz });
      setItems(data.data || []);
      setHasNext(!!data.hasNext);
      setTotal(data.total || 0);
    } catch (e: any) {
      console.error('加载文案列表失败:', e);
      setError(e?.message || '加载失败，请稍后重试');
      setItems([]);
      setHasNext(false);
      setTotal(0);
    } finally {
      setIsLoading(false);
    }
  };

  /**
   * 弹窗打开或筛选条件变化时重新加载数据
   */
  useEffect(() => {
    if (!isOpen) return;
    loadCopywritings(status, page, size);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, status, page, size]);

  /**
   * 关闭弹窗并重置分页
   */
  const handleClose = () => {
    setPage(1);
    onClose();
  };

  /**
   * 选择某条文案，并回填到外层脚本输入框
   * @param item 文案项
   */
  const handleSelect = (item: CopywritingItem) => {
    const content = item?.copywriting?.content || '';
    const title = item?.copywriting?.title || '';
    if (!content.trim()) {
      alert('该文案内容为空，无法选择');
      return;
    }
    onSelect(content, title);
    handleClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-[96vw] max-w-3xl">
        {/* 头部 */}
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-800">选择文案</h2>
          <div className="flex items-center gap-2">
            <span className="text-sm text-gray-600">共 {total} 条</span>
            <button
              onClick={() => loadCopywritings(status, page, size)}
              className="flex items-center gap-1 px-2 py-1 border border-gray-300 rounded hover:bg-gray-100"
              disabled={isLoading}
              title="刷新列表"
            >
              <RefreshCw size={14} />
              刷新
            </button>
            <button onClick={handleClose} className="text-gray-400 hover:text-gray-600 transition-colors">
              <X size={20} />
            </button>
          </div>
        </div>

        

        {/* 列表区域 */}
        <div className="p-4 max-h-[60vh] overflow-y-auto">
          {isLoading ? (
            <div className="flex justify-center items-center py-10 text-gray-500">加载中...</div>
          ) : error ? (
            <div className="py-10">
              <div className="p-4 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>
              <div className="text-xs text-gray-500 mt-2">请确认已登录并具备访问智核医审的权限。</div>
            </div>
          ) : items.length === 0 ? (
            <div className="flex justify-center items-center py-10 text-gray-500">暂无文案</div>
          ) : (
            <div className="space-y-3">
              {items.map((item) => (
                <div key={item.copywriting.copywritingId} className="flex items-center justify-between p-3 border border-gray-200 rounded">
                  <div className="flex items-start gap-3">
                    <FileText className="text-teal-600 mt-0.5" size={20} />
                    <div>
                      <div className="font-medium text-gray-800 text-sm">
                        {item.copywriting.title || '未命名文案'}
                      </div>
                      <div className="text-xs text-gray-600 mt-1 line-clamp-2">
                        {item.copywriting.summary || item.copywriting.content?.slice(0, 120) || '（无摘要）'}
                      </div>
                      <div className="text-xs text-gray-500 mt-1">
                        创建时间：{formatCreateTime(item.copywriting.createTime)}
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleSelect(item)}
                      className="px-3 py-1 bg-teal-600 text-white rounded text-xs hover:bg-teal-700 transition-colors"
                    >选择</button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 底部分页 */}
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200 bg-gray-50">
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              className="px-3 py-1 border border-gray-300 rounded text-sm hover:bg-gray-100"
              disabled={isLoading || page <= 1}
            >上一页</button>
            <span className="text-sm text-gray-600">第 {page} 页</span>
            <button
              onClick={() => setPage((p) => p + 1)}
              className="px-3 py-1 border border-gray-300 rounded text-sm hover:bg-gray-100"
              disabled={isLoading || !hasNext}
            >下一页</button>
          </div>
          <div>
            <button
              onClick={handleClose}
              className="px-4 py-1 text-gray-600 bg-white border border-gray-300 rounded hover:bg-gray-50 text-sm"
            >关闭</button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CopywritingSelectionModal;
