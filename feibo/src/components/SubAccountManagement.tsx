import React, { useState, useEffect } from 'react';
import { X, Plus, User, Phone, UserCheck, AlertCircle, ChevronLeft, ChevronRight } from 'lucide-react';
import ModalPortal from './ModalPortal';
import { 
  getExpertAssistants, 
  getAssistantExperts, 
  registerAssistant, 
  SubAccountUser, 
  PaginatedResponse, 
  RegisterAssistantRequest 
} from '../services/userApi';

interface SubAccountManagementProps {
  isOpen: boolean;
  onClose: () => void;
  userRole?: 'expert' | 'assistant'; // 用户角色，决定显示哪些功能
}

const SubAccountManagement: React.FC<SubAccountManagementProps> = ({ 
  isOpen, 
  onClose, 
  userRole = 'expert' 
}) => {
  const [activeTab, setActiveTab] = useState<'list' | 'register'>('list');
  const [users, setUsers] = useState<SubAccountUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pagination, setPagination] = useState({
    page: 1,
    size: 10,
    total: 0,
    hasNext: false
  });
  
  // 注册表单状态
  const [registerForm, setRegisterForm] = useState<RegisterAssistantRequest>({
    nickName: '',
    phoneNumber: '',
    password: '',
    genderCode: 0
  });
  const [registerLoading, setRegisterLoading] = useState(false);
  const [registerSuccess, setRegisterSuccess] = useState(false);

  // 获取用户列表
  const fetchUsers = async (page: number = 1) => {
    setLoading(true);
    setError(null);
    
    try {
      let response: PaginatedResponse<SubAccountUser>;
      // 确保size参数至少为1，避免API调用失败
      const safeSize = Math.max(pagination.size || 10, 1);
      
      if (userRole === 'expert') {
        response = await getExpertAssistants(page, safeSize);
      } else {
        response = await getAssistantExperts(page, safeSize);
      }
      
      setUsers(response.data);
      setPagination({
        page: response.page,
        size: Math.max(response.size || 10, 1), // 确保返回的size也不为0
        total: response.total,
        hasNext: response.hasNext
      });
    } catch (err: any) {
      setError(err.message || '获取用户列表失败');
    } finally {
      setLoading(false);
    }
  };

  // 注册新助理
  const handleRegisterAssistant = async () => {
    if (!registerForm.nickName || !registerForm.phoneNumber || !registerForm.password) {
      setError('请填写完整信息');
      return;
    }

    if (!/^1[3-9]\d{9}$/.test(registerForm.phoneNumber)) {
      setError('请输入正确的手机号码');
      return;
    }

    setRegisterLoading(true);
    setError(null);
    
    try {
      await registerAssistant(registerForm);
      setRegisterSuccess(true);
      setRegisterForm({
        nickName: '',
        phoneNumber: '',
        password: '',
        genderCode: 0
      });
      
      // 3秒后自动切换到列表页面并刷新数据
      setTimeout(() => {
        setRegisterSuccess(false);
        setActiveTab('list');
        fetchUsers(1);
      }, 3000);
    } catch (err: any) {
      setError(err.message || '注册助理失败');
    } finally {
      setRegisterLoading(false);
    }
  };

  // 页面切换
  const handlePageChange = (newPage: number) => {
    if (newPage >= 1 && (newPage <= Math.ceil(pagination.total / pagination.size))) {
      fetchUsers(newPage);
    }
  };

  // 性别显示
  const getGenderDisplay = (gender: string) => {
    switch (gender) {
      case '男':
      case '1':
        return '男';
      case '女':
      case '2':
        return '女';
      default:
        return '未知';
    }
  };

  // 组件挂载时获取数据
  useEffect(() => {
    if (isOpen && activeTab === 'list') {
      fetchUsers(1);
    }
  }, [isOpen, activeTab, userRole]);

  if (!isOpen) return null;

  return (
    <ModalPortal>
      <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-6">
      <div className="my-auto flex w-full max-w-4xl max-h-[calc(100vh-2rem)] flex-col overflow-hidden rounded-lg bg-white shadow-xl">
        {/* 标题栏 */}
        <div className="flex flex-shrink-0 items-center justify-between bg-gradient-to-r from-blue-600 to-blue-700 px-6 py-4 text-white">
          <div className="flex items-center gap-3">
            <div className="bg-white bg-opacity-20 p-2 rounded-lg">
              <User size={20} />
            </div>
            <div>
              <h2 className="text-lg font-semibold">
                {userRole === 'expert' ? '助理管理' : '专家管理'}
              </h2>
              <p className="text-blue-100 text-sm">
                {userRole === 'expert' ? '管理您的助理账号' : '查看关联的专家'}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-white hover:text-blue-200 transition-colors p-2 rounded-lg hover:bg-white hover:bg-opacity-10"
          >
            <X size={20} />
          </button>
        </div>

        {/* 标签页 */}
        <div className="flex-shrink-0 border-b border-gray-200">
          <div className="flex">
            <button
              onClick={() => setActiveTab('list')}
              className={`px-6 py-3 text-sm font-medium border-b-2 transition-colors ${
                activeTab === 'list'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {userRole === 'expert' ? '助理列表' : '专家列表'}
            </button>
            {userRole === 'expert' && (
              <button
                onClick={() => setActiveTab('register')}
                className={`px-6 py-3 text-sm font-medium border-b-2 transition-colors ${
                  activeTab === 'register'
                    ? 'border-blue-500 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700'
                }`}
              >
                注册助理
              </button>
            )}
          </div>
        </div>

        {/* 内容区域 */}
        <div className="min-h-0 flex-1 overflow-y-auto p-6">
          {/* 错误提示 */}
          {error && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2">
              <AlertCircle size={16} className="text-red-500" />
              <span className="text-red-700 text-sm">{error}</span>
              <button
                onClick={() => setError(null)}
                className="ml-auto text-red-500 hover:text-red-700"
              >
                <X size={14} />
              </button>
            </div>
          )}

          {/* 用户列表 */}
          {activeTab === 'list' && (
            <div>
              {/* 统计信息 */}
              <div className="mb-6">
                <div className="bg-blue-50 px-3 py-1 rounded-full inline-block">
                  <span className="text-blue-700 text-sm font-medium">
                    共 {pagination.total} 个{userRole === 'expert' ? '助理' : '专家'}
                  </span>
                </div>
              </div>

              {/* 用户表格 */}
              <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead className="bg-gray-50 border-b border-gray-200">
                      <tr>
                        <th className="text-left py-3 px-4 font-medium text-gray-700 text-sm">序号</th>
                        <th className="text-left py-3 px-4 font-medium text-gray-700 text-sm">昵称</th>
                        <th className="text-left py-3 px-4 font-medium text-gray-700 text-sm">手机号</th>
                        <th className="text-left py-3 px-4 font-medium text-gray-700 text-sm">性别</th>
                        <th className="text-left py-3 px-4 font-medium text-gray-700 text-sm">头像</th>
                      </tr>
                    </thead>
                    <tbody>
                      {loading ? (
                        <tr>
                          <td colSpan={5} className="py-12 text-center">
                            <div className="flex items-center justify-center">
                              <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600 mr-2"></div>
                              <span className="text-gray-500">加载中...</span>
                            </div>
                          </td>
                        </tr>
                      ) : !users || users.length === 0 ? (
                        <tr>
                          <td colSpan={5} className="py-12 text-center">
                            <div className="flex flex-col items-center justify-center text-gray-400">
                              <div className="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mb-4">
                                <User size={24} />
                              </div>
                              <p className="text-gray-500 text-sm">
                                暂无{userRole === 'expert' ? '助理' : '专家'}数据
                              </p>
                            </div>
                          </td>
                        </tr>
                      ) : (
                        users.map((user, index) => (
                          <tr key={user.userId} className="hover:bg-gray-50 border-b border-gray-100 last:border-b-0">
                            <td className="py-3 px-4 text-sm text-gray-600">
                              {(pagination.page - 1) * pagination.size + index + 1}
                            </td>
                            <td className="py-3 px-4">
                              <div className="flex items-center gap-3">
                                <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center">
                                  <User size={14} className="text-blue-600" />
                                </div>
                                <span className="text-sm font-medium text-gray-900">{user.nickName}</span>
                              </div>
                            </td>
                            <td className="py-3 px-4 text-sm text-gray-600">
                              <div className="flex items-center gap-1">
                                <Phone size={12} />
                                <span>{user.phoneNumber}</span>
                              </div>
                            </td>
                            <td className="py-3 px-4 text-sm text-gray-600">
                              {getGenderDisplay(user.gender)}
                            </td>
                            <td className="py-3 px-4">
                              {user.avatar && !user.avatar.includes('digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com') ? (
                                <img 
                                  src={user.avatar} 
                                  alt="头像" 
                                  className="w-8 h-8 rounded-full object-cover"
                                  onError={(e) => {
                                    const target = e.target as HTMLImageElement;
                                    target.style.display = 'none';
                                    target.nextElementSibling?.classList.remove('hidden');
                                  }}
                                />
                              ) : (
                                <div className="w-8 h-8 bg-gray-100 rounded-full flex items-center justify-center">
                                  <User size={14} className="text-gray-400" />
                                </div>
                              )}
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
                
                {/* 分页 */}
                {pagination.total > pagination.size && (
                  <div className="px-4 py-3 border-t border-gray-200 bg-gray-50">
                    <div className="flex items-center justify-between">
                      <div className="text-sm text-gray-500">
                        显示 {(pagination.page - 1) * pagination.size + 1} - {Math.min(pagination.page * pagination.size, pagination.total)} 条，共 {pagination.total} 条
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => handlePageChange(pagination.page - 1)}
                          disabled={pagination.page <= 1}
                          className={`px-3 py-1 text-sm rounded border ${
                            pagination.page <= 1
                              ? 'text-gray-400 border-gray-200 cursor-not-allowed'
                              : 'text-gray-600 border-gray-300 hover:bg-gray-50'
                          }`}
                        >
                          <ChevronLeft size={14} />
                        </button>
                        <span className="px-3 py-1 text-sm bg-blue-600 text-white rounded">
                          {pagination.page}
                        </span>
                        <button
                          onClick={() => handlePageChange(pagination.page + 1)}
                          disabled={!pagination.hasNext}
                          className={`px-3 py-1 text-sm rounded border ${
                            !pagination.hasNext
                              ? 'text-gray-400 border-gray-200 cursor-not-allowed'
                              : 'text-gray-600 border-gray-300 hover:bg-gray-50'
                          }`}
                        >
                          <ChevronRight size={14} />
                        </button>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* 注册助理 */}
          {activeTab === 'register' && userRole === 'expert' && (
            <div className="max-w-md mx-auto">
              {registerSuccess ? (
                <div className="text-center py-8">
                  <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
                    <UserCheck size={32} className="text-green-600" />
                  </div>
                  <h3 className="text-lg font-semibold text-gray-900 mb-2">注册成功！</h3>
                  <p className="text-gray-600 text-sm mb-4">
                    助理账号已成功创建并绑定到您的账户
                  </p>
                  <div className="text-xs text-gray-500">
                    3秒后自动跳转到助理列表...
                  </div>
                </div>
              ) : (
                <div>
                  <div className="mb-6 text-center">
                    <h3 className="text-lg font-semibold text-gray-900 mb-2">注册新助理</h3>
                    <p className="text-gray-600 text-sm">为您的团队添加新的助理账号</p>
                  </div>

                  <div className="space-y-4">
                    {/* 昵称 */}
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        昵称 <span className="text-red-500">*</span>
                      </label>
                      <input
                        type="text"
                        value={registerForm.nickName}
                        onChange={(e) => setRegisterForm({ ...registerForm, nickName: e.target.value })}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm"
                        placeholder="请输入助理昵称"
                      />
                    </div>

                    {/* 手机号 */}
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        手机号 <span className="text-red-500">*</span>
                      </label>
                      <input
                        type="tel"
                        value={registerForm.phoneNumber}
                        onChange={(e) => setRegisterForm({ ...registerForm, phoneNumber: e.target.value })}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm"
                        placeholder="请输入手机号（作为登录账号）"
                      />
                    </div>

                    {/* 密码 */}
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">
                        初始密码 <span className="text-red-500">*</span>
                      </label>
                      <input
                        type="password"
                        value={registerForm.password}
                        onChange={(e) => setRegisterForm({ ...registerForm, password: e.target.value })}
                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm"
                        placeholder="请输入初始密码"
                      />
                    </div>

                    {/* 性别 */}
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">
                        性别
                      </label>
                      <div className="flex gap-4">
                        <label className="flex items-center gap-2 cursor-pointer">
                          <input
                            type="radio"
                            name="gender"
                            value={0}
                            checked={registerForm.genderCode === 0}
                            onChange={(e) => setRegisterForm({ ...registerForm, genderCode: parseInt(e.target.value) })}
                            className="w-4 h-4 text-blue-600"
                          />
                          <span className="text-sm text-gray-600">未知</span>
                        </label>
                        <label className="flex items-center gap-2 cursor-pointer">
                          <input
                            type="radio"
                            name="gender"
                            value={1}
                            checked={registerForm.genderCode === 1}
                            onChange={(e) => setRegisterForm({ ...registerForm, genderCode: parseInt(e.target.value) })}
                            className="w-4 h-4 text-blue-600"
                          />
                          <span className="text-sm text-gray-600">男</span>
                        </label>
                        <label className="flex items-center gap-2 cursor-pointer">
                          <input
                            type="radio"
                            name="gender"
                            value={2}
                            checked={registerForm.genderCode === 2}
                            onChange={(e) => setRegisterForm({ ...registerForm, genderCode: parseInt(e.target.value) })}
                            className="w-4 h-4 text-blue-600"
                          />
                          <span className="text-sm text-gray-600">女</span>
                        </label>
                      </div>
                    </div>

                    {/* 提交按钮 */}
                    <div className="pt-4">
                      <button
                        onClick={handleRegisterAssistant}
                        disabled={registerLoading}
                        className={`w-full px-4 py-3 rounded-lg font-medium transition-colors ${
                          registerLoading
                            ? 'bg-gray-400 text-white cursor-not-allowed'
                            : 'bg-blue-600 text-white hover:bg-blue-700'
                        }`}
                      >
                        {registerLoading ? (
                          <div className="flex items-center justify-center gap-2">
                            <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                            <span>注册中...</span>
                          </div>
                        ) : (
                          <div className="flex items-center justify-center gap-2">
                            <Plus size={16} />
                            <span>注册助理</span>
                          </div>
                        )}
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
      </div>
    </ModalPortal>
  );
};

export default SubAccountManagement;
