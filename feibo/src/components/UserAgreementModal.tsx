import React from 'react';

interface UserAgreementModalProps {
  showUserAgreement: boolean;
  showPrivacyPolicy: boolean;
  onCloseUserAgreement: () => void;
  onClosePrivacyPolicy: () => void;
}

const UserAgreementModal: React.FC<UserAgreementModalProps> = ({
  showUserAgreement,
  showPrivacyPolicy,
  onCloseUserAgreement,
  onClosePrivacyPolicy
}) => {
  return (
    <>
      {/* 用户协议模态框 */}
      {showUserAgreement && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg max-w-2xl max-h-[80vh] w-full mx-4 flex flex-col">
            <div className="flex items-center justify-between p-6 border-b">
              <h2 className="text-xl font-semibold text-gray-900">用户协议</h2>
              <button
                onClick={onCloseUserAgreement}
                className="text-gray-400 hover:text-gray-600 text-2xl"
              >
                ×
              </button>
            </div>
            <div className="p-6 overflow-y-auto flex-1">
              <div className="prose max-w-none text-sm text-gray-700 leading-relaxed">
                <h3 className="text-lg font-medium mb-4">飞驳数字人视频合成平台用户协议</h3>
                
                <h4 className="font-medium mt-6 mb-2">第一条 协议的接受</h4>
                <p className="mb-4">欢迎使用飞驳数字医生工作台（以下简称"本平台"）。本协议是您与飞驳科技（杭州）有限公司（以下简称"我们"或"公司"）之间关于使用本平台服务的法律协议。</p>
                
                <h4 className="font-medium mt-6 mb-2">第二条 服务内容</h4>
                <p className="mb-4">本平台为用户提供数字人视频合成、AI智能对话、文案审核等相关服务。我们保留随时修改、暂停或终止部分或全部服务的权利。</p>
                
                <h4 className="font-medium mt-6 mb-2">第三条 用户义务</h4>
                <p className="mb-2">用户在使用本平台服务时，应当：</p>
                <ul className="list-disc pl-6 mb-4">
                  <li>遵守相关法律法规和本协议的规定</li>
                  <li>不得利用本平台从事违法违规活动</li>
                  <li>不得上传包含违法、有害、威胁、辱骂、骚扰、侵权等内容</li>
                  <li>保护自己的账户信息安全</li>
                </ul>
                
                <h4 className="font-medium mt-6 mb-2">第四条 知识产权</h4>
                <p className="mb-4">本平台的所有内容，包括但不限于文字、图片、音频、视频、软件等，均受知识产权法保护。未经授权，用户不得复制、传播、修改或用于商业用途。</p>
                
                <h4 className="font-medium mt-6 mb-2">第五条 免责声明</h4>
                <p className="mb-4">在法律允许的最大范围内，我们对因使用本平台服务而产生的任何直接、间接、偶然、特殊或后果性损害不承担责任。</p>
                
                <h4 className="font-medium mt-6 mb-2">第六条 协议修改</h4>
                <p className="mb-4">我们保留随时修改本协议的权利。修改后的协议将在本平台公布，继续使用本平台服务即表示您接受修改后的协议。</p>
                
                <p className="mt-6 text-gray-500">本协议最后更新时间：2025年9月</p>
              </div>
            </div>
            <div className="p-6 border-t">
              <button
                onClick={onCloseUserAgreement}
                className="w-full bg-teal-600 hover:bg-teal-700 text-white py-2 px-4 rounded transition-colors"
              >
                我已阅读并理解
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 隐私政策模态框 */}
      {showPrivacyPolicy && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg max-w-2xl max-h-[80vh] w-full mx-4 flex flex-col">
            <div className="flex items-center justify-between p-6 border-b">
              <h2 className="text-xl font-semibold text-gray-900">隐私政策</h2>
              <button
                onClick={onClosePrivacyPolicy}
                className="text-gray-400 hover:text-gray-600 text-2xl"
              >
                ×
              </button>
            </div>
            <div className="p-6 overflow-y-auto flex-1">
              <div className="prose max-w-none text-sm text-gray-700 leading-relaxed">
                <h3 className="text-lg font-medium mb-4">飞驳数字医生工作台隐私政策</h3>
                
                <h4 className="font-medium mt-6 mb-2">第一条 信息收集</h4>
                <p className="mb-4">我们可能收集以下类型的信息：</p>
                <ul className="list-disc pl-6 mb-4">
                  <li>账户信息：手机号、用户名、头像等</li>
                  <li>使用信息：访问日志、操作记录、设备信息等</li>
                  <li>内容信息：您上传或生成的文案、视频等内容</li>
                </ul>
                
                <h4 className="font-medium mt-6 mb-2">第二条 信息使用</h4>
                <p className="mb-4">我们使用收集的信息用于：</p>
                <ul className="list-disc pl-6 mb-4">
                  <li>提供和改进我们的服务</li>
                  <li>处理您的请求和交易</li>
                  <li>发送服务相关通知</li>
                  <li>保护平台和用户安全</li>
                </ul>
                
                <h4 className="font-medium mt-6 mb-2">第三条 信息共享</h4>
                <p className="mb-4">除以下情况外，我们不会与第三方共享您的个人信息：</p>
                <ul className="list-disc pl-6 mb-4">
                  <li>获得您的明确同意</li>
                  <li>法律法规要求</li>
                  <li>保护我们的权利和财产</li>
                  <li>与服务提供商合作（在严格保密协议下）</li>
                </ul>
                
                <h4 className="font-medium mt-6 mb-2">第四条 信息安全</h4>
                <p className="mb-4">我们采用行业标准的安全措施保护您的个人信息，包括数据加密、访问控制、安全审计等。</p>
                
                <h4 className="font-medium mt-6 mb-2">第五条 您的权利</h4>
                <p className="mb-4">您有权：</p>
                <ul className="list-disc pl-6 mb-4">
                  <li>访问和更新您的个人信息</li>
                  <li>删除您的账户和相关数据</li>
                  <li>撤回对信息处理的同意</li>
                  <li>投诉和举报隐私问题</li>
                </ul>
                
                <h4 className="font-medium mt-6 mb-2">第六条 联系我们</h4>
                <p className="mb-4">如果您对本隐私政策有任何疑问，请通过平台内的客服功能联系我们。</p>
                
                <p className="mt-6 text-gray-500">本隐私政策最后更新时间：2025年9月</p>
              </div>
            </div>
            <div className="p-6 border-t">
              <button
                onClick={onClosePrivacyPolicy}
                className="w-full bg-teal-600 hover:bg-teal-700 text-white py-2 px-4 rounded transition-colors"
              >
                我已阅读并理解
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default UserAgreementModal;