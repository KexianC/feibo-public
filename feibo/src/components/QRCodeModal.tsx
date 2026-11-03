import React, { useEffect, useRef, useState } from 'react';
import { X, Copy, Check, RefreshCw, Settings } from 'lucide-react';
import QRCode from 'qrcode';

interface QRCodeModalProps {
  isOpen: boolean;
  onClose: () => void;
  url: string;
  title?: string;
  description?: string;
}

// 二维码生成方式枚举
type QRGeneratorType = 'local' | 'caoliao' | 'liantu' | 'qrserver';

// 在线二维码服务配置
const QR_SERVICES = {
  caoliao: {
    name: '草料二维码',
    url: (text: string) => `https://cli.im/api/qrcode/code?text=${encodeURIComponent(text)}&mhid=sAaOokmOmm2dm2aaOOm`,
    description: '草料二维码在线生成'
  },
  liantu: {
    name: '联图二维码',
    url: (text: string) => `https://api.qrserver.com/v1/create-qr-code/?size=256x256&data=${encodeURIComponent(text)}`,
    description: '联图二维码在线生成'
  },
  qrserver: {
    name: 'QR Server',
    url: (text: string) => `https://api.qrserver.com/v1/create-qr-code/?size=256x256&format=png&data=${encodeURIComponent(text)}`,
    description: 'QR Server在线生成'
  }
};

const QRCodeModal: React.FC<QRCodeModalProps> = ({
  isOpen,
  onClose,
  url,
  title = '扫码支付',
  description = '请使用微信扫描二维码完成支付'
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imgRef = useRef<HTMLImageElement>(null);
  const [copied, setCopied] = useState(false);
  const [qrError, setQrError] = useState<string>('');
  const [currentGenerator, setCurrentGenerator] = useState<QRGeneratorType>('local');
  const [isLoading, setIsLoading] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [retryCount, setRetryCount] = useState(0);
  const [maxRetries] = useState(3);

  useEffect(() => {
    if (isOpen && url) {
      setRetryCount(0);
      generateQRCode();
    }
  }, [isOpen, url]);

  useEffect(() => {
    if (isOpen && url) {
      generateQRCode();
    }
  }, [currentGenerator]);

  const generateQRCode = async () => {
    if (!url || url.trim() === '') {
      console.error('URL为空，无法生成二维码');
      setQrError('支付链接为空，无法生成二维码。请检查支付流程是否正常。');
      return;
    }
    
    setIsLoading(true);
    setQrError('');
    
    const cleanUrl = url.trim().replace(/`/g, '');
    
    // 验证URL格式
    if (!cleanUrl || cleanUrl.length < 10) {
      console.error('URL格式无效');
      setQrError('支付链接格式无效');
      setIsLoading(false);
      return;
    }
    
    try {
      await generateLocalQRCode(cleanUrl);
    } catch (error) {
      console.error('本地生成二维码失败');
      setQrError('生成失败，请稍后重试');
    } finally {
      setIsLoading(false);
    }
  };

  const generateLocalQRCode = async (cleanUrl: string) => {
    if (!canvasRef.current) {
      console.error('Canvas元素未找到');
      throw new Error('Canvas元素未找到');
    }
    
    if (typeof QRCode === 'undefined') {
      console.error('QRCode库未正确加载');
      throw new Error('QRCode库未正确加载');
    }
    
    try {
      await QRCode.toCanvas(canvasRef.current, cleanUrl, {
        width: 256,
        margin: 2,
        color: {
          dark: '#000000',
          light: '#FFFFFF'
        },
        errorCorrectionLevel: 'M'
      });
      
      // 隐藏在线图片
      if (imgRef.current) {
        imgRef.current.style.display = 'none';
      }
      canvasRef.current.style.display = 'block';
    } catch (qrError) {
      console.error('QRCode.toCanvas执行失败');
      throw new Error('二维码生成失败');
    }
  };

  const generateOnlineQRCode = async (cleanUrl: string, generator: QRGeneratorType) => {
    if (generator === 'local') return;
    
    const service = QR_SERVICES[generator];
    if (!service) throw new Error('未知的生成服务');
    
    const qrUrl = service.url(cleanUrl);
    
    return new Promise((resolve, reject) => {
      if (!imgRef.current) {
        reject(new Error('图片元素未找到'));
        return;
      }
      
      const img = imgRef.current;
      
      const handleLoad = () => {
        // 隐藏canvas，显示在线图片
        if (canvasRef.current) {
          canvasRef.current.style.display = 'none';
        }
        img.style.display = 'block';
        img.removeEventListener('load', handleLoad);
        img.removeEventListener('error', handleError);
        resolve(void 0);
      };
      
      const handleError = () => {
        img.removeEventListener('load', handleLoad);
        img.removeEventListener('error', handleError);
        reject(new Error(`${service.name}加载失败`));
      };
      
      img.addEventListener('load', handleLoad);
      img.addEventListener('error', handleError);
      
      // 设置跨域属性
      img.crossOrigin = 'anonymous';
      img.src = qrUrl;
    });
  };

  // 移除自动重试机制，避免访问外部服务
  const handleGenerationError = async (error: any) => {
    const errorMessage = error?.message || '生成失败';
    setQrError(`二维码生成失败: ${errorMessage}`);
  };

  const handleManualSwitch = (generator: QRGeneratorType) => {
    setCurrentGenerator(generator);
    setRetryCount(0);
    setShowSettings(false);
  };

  const getCurrentGeneratorName = () => {
    if (currentGenerator === 'local') return '本地生成';
    return QR_SERVICES[currentGenerator]?.name || '未知服务';
  };

  const copyToClipboard = async () => {
    try {
      const cleanUrl = url.trim().replace(/`/g, '');
      await navigator.clipboard.writeText(cleanUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (error) {
      console.error('复制失败:', error);
    }
  };

  const handleClose = () => {
    setCopied(false);
    setQrError('');
    setShowSettings(false);
    setRetryCount(0);
    onClose();
  };

  if (!isOpen) {
    return null;
  }
  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-[400px] max-w-md mx-4">
        {/* 标题栏 */}
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-xl font-semibold text-gray-800">{title}</h2>
          <button
            onClick={handleClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          >
            <X size={24} />
          </button>
        </div>



        {/* 二维码区域 */}
        <div className="flex flex-col items-center">
          {qrError ? (
            <div className="w-64 h-64 flex items-center justify-center border-2 border-dashed border-gray-300 rounded-lg">
              <div className="text-center">
                <p className="text-red-500 mb-3 text-sm">{qrError}</p>
                <div className="space-y-2">
                  <button
                    onClick={generateQRCode}
                    className="px-4 py-2 bg-teal-600 hover:bg-teal-700 text-white rounded transition-colors text-sm"
                    disabled={isLoading}
                  >
                    {isLoading ? '生成中...' : '重新生成'}
                  </button>
                  {/* 备用方案：显示支付链接 */}
                  <div className="mt-4 p-3 bg-gray-50 rounded border">
                    <p className="text-xs text-gray-600 mb-2">备用方案：复制支付链接</p>
                    <div className="flex items-center gap-2">
                      <input
                        type="text"
                        value={url || ''}
                        readOnly
                        className="flex-1 px-2 py-1 text-xs border rounded bg-white"
                      />
                      <button
                        onClick={copyToClipboard}
                        className="px-2 py-1 text-xs bg-blue-500 hover:bg-blue-600 text-white rounded transition-colors"
                      >
                        {copied ? '已复制' : '复制'}
                      </button>
                    </div>
                  </div>
                  <button
                    onClick={() => setShowSettings(true)}
                    className="block mx-auto px-3 py-1 text-sm text-gray-600 hover:text-gray-800 border border-gray-300 rounded hover:bg-gray-50 transition-colors"
                  >
                    尝试其他方式
                  </button>
                </div>
              </div>
            </div>
          ) : (
            <div className="relative">
              {isLoading && (
                <div className="absolute inset-0 bg-white bg-opacity-80 flex items-center justify-center z-10 rounded-lg">
                  <div className="flex items-center gap-2 text-teal-600">
                    <RefreshCw className="w-5 h-5 animate-spin" />
                    <span className="text-sm">生成中...</span>
                  </div>
                </div>
              )}
              <div className="p-4 bg-white border-2 border-gray-200 rounded-lg shadow-sm">
                {/* 本地生成的canvas */}
                <canvas
                  ref={canvasRef}
                  className="block"
                  style={{ maxWidth: '100%', height: 'auto', display: currentGenerator === 'local' ? 'block' : 'none' }}
                />
                {/* 在线生成的图片 */}
                <img
                  ref={imgRef}
                  alt="二维码"
                  className="block max-w-full h-auto"
                  style={{ display: 'none' }}
                />
              </div>
            </div>
          )}

          {/* 描述文字 */}
          <p className="text-gray-600 text-center mt-4 mb-4">{description}</p>



          {/* 提示信息 */}
          <div className="mt-4 p-3 bg-blue-50 border border-blue-200 rounded-lg w-full">
            <p className="text-blue-700 text-sm text-center">
              💡 支付完成后，页面将自动跳转或刷新
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default QRCodeModal;
