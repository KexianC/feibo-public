import React, {useState, useEffect, useRef, useMemo} from 'react';
import {
    Search, Plus, RefreshCw, Download, Upload,
    User, Edit, Trash2, AlertCircle, CheckCircle,
    X, Loader2, QrCode
} from 'lucide-react';
import {
    getAccounts,
    getValidAccounts,
    deleteAccount,
    updateUserinfo,
    downloadCookie,
    uploadCookie,
    loginVideoAccount,
    UpdateUserInfoRequest,
    verifyAccount,
    submitVerification, // 新增导入：提交验证码API
} from "@/services/videoPublishApi.ts";
import API_CONFIG from "@/config/api.ts";
import {
    openAuthenticatedEventSource,
    type AuthenticatedEventSourceConnection,
} from '@/services/http/authenticatedEventSource';
import {message, notification, Modal, Input} from 'antd'; // 引入 Input

// 类型定义
interface Account {
    id: number;
    type: number;
    filePath: string;
    userName: string;
    status: number;
    user_id: string;
}

const platformMap = {
    4: "快手",
    3: "抖音",
    2: "视频号",
    1: "小红书",
}

const statusMap = {
    1: "正常",
    2: "验证中",
    0: "异常",
}

interface AccountForm {
    id?: number;
    type: number;
    username: string;
}

interface PlatformCount {
    name: string;
    count: number;
}

const AccountManagement: React.FC = () => {
    // 状态管理
    const [activeTab, setActiveTab] = useState<'all' | 'kuaishou' | 'douyin' | 'channels' | 'xiaohongshu'>('all');
    const [searchKeyword, setSearchKeyword] = useState('');
    const [accounts, setAccounts] = useState<Account[]>([]);

    const [showDialog, setShowDialog] = useState(false);
    const [dialogType, setDialogType] = useState<'add' | 'edit'>('add');
    const [accountForm, setAccountForm] = useState<AccountForm>();

    const [sseConnecting, setSseConnecting] = useState(false);
    const [qrCodeData, setQrCodeData] = useState<string>('');
    const [loginStatus, setLoginStatus] = useState<string>('');
    const [isRefreshing, setIsRefreshing] = useState(false);

    // 验证码弹窗状态
    const [showCaptchaModal, setShowCaptchaModal] = useState(false);
    const [captchaInput, setCaptchaInput] = useState('');
    const [isVerifyingCaptcha, setIsVerifyingCaptcha] = useState(false);
    const [captchaError, setCaptchaError] = useState('');

    const fileInputRef = useRef<HTMLInputElement>(null);
    //防止重复挂载
    const hasFetched = useRef(false);

    // 平台统计
    const platformCounts = useMemo<PlatformCount[]>(() => [
        {name: '全部', count: accounts.length},
        {name: '快手', count: accounts.filter(a => platformMap[a.type] === '快手').length},
        {name: '抖音', count: accounts.filter(a => platformMap[a.type] === '抖音').length},
        {name: '视频号', count: accounts.filter(a => platformMap[a.type] === '视频号').length},
        {name: '小红书', count: accounts.filter(a => platformMap[a.type] === '小红书').length}
    ], [accounts]);

    // 过滤账户
    const filteredAccounts = useMemo(() => {
        let filtered = accounts.filter(account =>
            account.userName.toLowerCase().includes(searchKeyword.toLowerCase()) ||
            platformMap[account.type].toLowerCase().includes(searchKeyword.toLowerCase())
        );

        if (activeTab !== 'all') {
            const playformMapToChinese = {
                'kuaishou': "快手",
                'douyin': "抖音",
                'channels': "视频号",
                'xiaohongshu': "小红书"
            }
            filtered = filtered.filter(account =>
                platformMap[account.type] === playformMapToChinese[activeTab]
            );
        }

        return filtered;
    }, [accounts, searchKeyword, activeTab]);

    // 获取平台标签颜色
    const getPlatformTagColor = (type: number): string => {
        const colors: Record<string, string> = {
            1: 'bg-green-100 text-green-800',
            2: 'bg-red-100 text-red-800',
            3: 'bg-yellow-100 text-yellow-800',
            4: 'bg-blue-100 text-blue-800'
        };
        return colors[type] || 'bg-gray-100 text-gray-800';
    };

    // 获取状态标签颜色
    const getStatusTagColor = (status: number): string => {
        if (status === 1) return 'bg-green-100 text-green-800';
        if (status === 2) return 'bg-blue-100 text-blue-800';
        if (status === 0) return 'bg-red-100 text-red-800';
        return 'bg-gray-100 text-gray-800';
    };

    // 状态是否可点击
    const isStatusClickable = (status: number): boolean => {
        return status === 0;
    };

    // 获取默认头像
    const getDefaultAvatar = (name: string): string => {
        return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=random`;
    };

    // 加载账户数据
    const fetchAccounts = async () => {
        setIsRefreshing(true);
        const loadingMessage = message.loading({
            content: '正在验证账号信息...',
            duration: 0,
            key: 'validating-accounts',
        });
        try {
            accounts.map(account => {
                account.status = 2;
            })
            const validAccounts = await getValidAccounts();
            if (validAccounts.code == 200) {
                message.success({
                    content: '账号验证完成',
                    key: 'validating-accounts',
                });
                setAccounts(validAccounts.data);
                console.log('账户数据已刷新');
            } else {
                message.error({
                    content: validAccounts.msg || '验证失败',
                    key: 'validating-accounts',
                });
            }
        } catch (error) {
            message.error({
                content: `加载账户数据失败: ${error.message || error}`,
                key: 'validating-accounts',
            });
        } finally {
            setIsRefreshing(false);
        }
    };

    // 快速获取账户数据
    const fetchAccountsQuick = async () => {
        try {
            const fastAccounts = await getAccounts();
            message.success("获取账号成功");
            setAccounts(fastAccounts.data);
        } catch (error) {
            message.error('快速获取账户失败:', error);
        }
    };

    // 添加账户
    const handleAddAccount = () => {
        setDialogType('add');
        setAccountForm({
            type: 0,
            username: '',
        });
        setSseConnecting(false);
        setQrCodeData('');
        setLoginStatus('');
        setShowDialog(true);
        // 重置验证码相关状态
        setShowCaptchaModal(false);
        setCaptchaInput('');
        setIsVerifyingCaptcha(false);
        setCaptchaError('');
    };

    // 编辑账户
    const handleEdit = (account: Account) => {
        setDialogType('edit');
        setAccountForm({
            id: account.id,
            type: account.type,
            username: account.userName,
        });
        setSseConnecting(false);
        setQrCodeData('');
        setLoginStatus('');
        setShowDialog(true);
        setShowCaptchaModal(false);
        setCaptchaInput('');
        setIsVerifyingCaptcha(false);
        setCaptchaError('');
    };

    // 删除账户
    const handleDelete = (account: Account) => {
        Modal.confirm({
            title: '确认删除',
            content: `确定要删除账号 ${account.userName} 吗？`,
            okText: '确认',
            cancelText: '取消',
            onOk: async () => {
                try {
                    await deleteAccount(account.id)
                    message.success("账号删除成功");
                    setAccounts(prev => prev.filter(a => a.id !== account.id));
                } catch (error) {
                    message.error('删除账户失败:', error);
                }
            }
        })
    };

    // 下载Cookie
    const handleDownloadCookie = async (account: Account) => {
        if (!account.filePath) {
            message.error('该账户没有Cookie文件');
            return;
        }
        try {
            const blob = await downloadCookie(account.filePath)
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = account.filePath;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
            message.success('下载cookie文件成功')
        } catch (error) {
            message.error('下载cookie文件失败:', error);
        }
    };

    // 上传Cookie
    const handleUploadCookie = async (account: Account) => {
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
            fileInputRef.current.click();
            const input = fileInputRef.current;
            input.dataset.accountId = String(account.id);
        }
    };

    const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) return;

        if (!file.name.endsWith('.json')) {
            message.error('请选择JSON格式的Cookie文件');
            return;
        }

        const accountId = event.target.dataset.accountId;
        if (!accountId) return;

        try {
            const response = await uploadCookie(file, accountId, String(accounts.find((a) => a.id.toString() === accountId).type));
            if (response.code === 200) {
                message.success('Cookie文件上传成功');
                setTimeout(async () => {
                    message.info(`开始验证账号${accounts.find((a) => a.id.toString() === accountId).userName}`)
                    const response = await verifyAccount(parseInt(accountId));
                    if (response.code == 200) {
                        message.success('验证通过');
                    } else {
                        message.error('验证失败');
                    }
                }, 2000);
            } else {
                message.error(response.msg || 'Cookie文件上传失败');
            }
        } catch (error) {
            console.error('上传Cookie文件失败:', error);
            message.error('Cookie文件上传失败');
        }
    };

    // 重新登录账号
    const handleReLogin = (row) => {
        setDialogType("edit")
        Object.assign(accountForm, {
            id: row.id,
            username: row.userName,
            type: row.type,
        })

        setSseConnecting(false)
        setQrCodeData('')
        setLoginStatus('')
        setShowDialog(true)
        setShowCaptchaModal(false)
        setCaptchaInput('')
        setIsVerifyingCaptcha(false)
        setCaptchaError('')

        setTimeout(() => {
            connectSSE(row.type, row.userName)
        }, 300)
    }

    // 关闭SSE连接
    const closeSSEConnection = () => {
        console.log('正在关闭SSE连接...');
        if (eventSource.current) {
            try {
                eventSource.current.close();
                console.log('SSE连接已关闭');
            } catch (error) {
                console.error('关闭SSE连接时出错:', error);
            }
            eventSource.current = null;
        }

        setSseConnecting(false);
    };

    const eventSource = useRef<AuthenticatedEventSourceConnection | null>(null);
    // 建立SSE连接
    const connectSSE = (type: number, username: string) => {
        closeSSEConnection();

        setSseConnecting(true);
        setQrCodeData('');
        setLoginStatus('');

        const baseUrl = API_CONFIG.API_BASE_URL || 'http://localhost:5409';
        const url = `${baseUrl}/video-publish/login?type=${type}&username=${encodeURIComponent(username)}`;

        eventSource.current = openAuthenticatedEventSource(url, {
            heartbeatTimeout: 300000,
            withCredentials: true,
        }, {
          onMessage: (event) => {
            const data = event.data

            if (data.length > 100) {
                try {
                    if (data.startsWith('data:image')) {
                        setQrCodeData(data)
                    } else {
                        setQrCodeData(`data:image/png;base64,${data}`)
                    }
                } catch (error) {
                    console.error('处理二维码数据出错:', error)
                }
            } else if (data === '200' || data === '500' || data === '401' || data === '402') {
                setLoginStatus(data)

                if (data === '200') {
                    // 登录成功，关闭验证码弹窗（如果有）
                    setShowCaptchaModal(false);
                    setCaptchaInput('');
                    setIsVerifyingCaptcha(false);
                    setCaptchaError('');

                    setTimeout(() => {
                        closeSSEConnection()

                        setTimeout(async () => {
                            setShowDialog(false)
                            setSseConnecting(false)

                            if (dialogType === 'edit') {
                                await deleteAccount(accountForm.id);
                            }
                            const fastAccounts = await getAccounts();
                            setAccounts(fastAccounts.data);
                            message.success(dialogType === 'edit' ? '重新登录成功' : '账号添加成功')
                        }, 1000)
                    }, 1000)
                } else if (data === '401') {
                    // 需要验证码，弹出验证码输入框
                    setShowCaptchaModal(true);
                    // 不清空输入框，保留用户可能已输入的内容,但第一次收到401时输入框应为空
                    setCaptchaInput('');
                    setIsVerifyingCaptcha(false);
                    setCaptchaError('');
                } else if (data === '402') {
                    // 验证码错误
                    setCaptchaError('验证码错误，请重新输入');
                    setIsVerifyingCaptcha(false);
                    // 不关闭弹窗，让用户重新输入
                } else if (data === '500') {
                    // 登录失败，关闭连接和弹窗
                    closeSSEConnection();
                    setShowCaptchaModal(false);
                    setCaptchaInput('');
                    setIsVerifyingCaptcha(false);
                    setCaptchaError('');
                    message.error("登录超时，请重试");

                    setTimeout(() => {
                        setSseConnecting(false)
                        setQrCodeData('')
                        setLoginStatus('')
                    }, 2000)
                }
            }
          },
          onError: (error) => {
            console.error('SSE连接错误:', error)
            message.error('连接服务器失败，请稍后再试')
            closeSSEConnection()
            setSseConnecting(false)
            setShowCaptchaModal(false)
            setCaptchaInput('')
            setIsVerifyingCaptcha(false)
            setCaptchaError('')
          },
        });
    };

    // 提交验证码
    const handleCaptchaSubmit = async () => {
        if (!captchaInput.trim()) {
            setCaptchaError('请输入验证码');
            return;
        }

        setIsVerifyingCaptcha(true);
        setCaptchaError('');

        try {
            // 调用提交验证码API，需要传递账号信息（type, username）和验证码
            // 假设API定义：submitVerification(code: string, type: number, username: string)
            await submitVerification(captchaInput);
            // 提交成功后，等待SSE返回结果（200或402），不关闭弹窗
            // 如果API调用失败（网络错误），则提示错误并恢复按钮
        } catch (error) {
            console.error('提交验证码失败:', error);
            message.error('提交验证码失败，请稍后重试');
            setIsVerifyingCaptcha(false);
        }
    };

    // 关闭验证码弹窗
    const handleCloseCaptchaModal = () => {
        setShowCaptchaModal(false);
        setCaptchaInput('');
        setIsVerifyingCaptcha(false);
        setCaptchaError('');
        // 注意：不关闭SSE连接，用户可能想继续扫码？但通常验证码弹窗关闭后，登录流程应终止。
        // 这里根据需求决定：关闭弹窗时是否终止登录流程？我们选择关闭弹窗的同时关闭SSE连接，因为用户可能放弃登录。
        closeSSEConnection();
        // 同时关闭主对话框？可以保持主对话框开启，让用户重新扫码或取消。为了简单，我们保持主对话框开启，但SSE已关闭。
        // 更好的做法是：如果用户关闭验证码弹窗，视为放弃登录，关闭SSE并重置主对话框中的登录状态。
        setSseConnecting(false);
        setQrCodeData('');
        setLoginStatus('');
    };

    // 提交账户表单
    const submitAccountForm = async () => {
        if (!accountForm.type || !accountForm.username) {
            message.error('请填写完整的平台和名称信息');
            return;
        }

        if (sseConnecting) {
            Modal.confirm({
                title: '确认继续',
                content: '已有进程正在运行，是否继续？',
                okText: '确认',
                cancelText: '取消',
                onOk: () => {
                    closeSSEConnection();
                },
                onCancel: () => {
                    return;
                },
            })
        }

        if (dialogType === 'add') {
            connectSSE(accountForm.type, accountForm.username);
        } else {
            if (accountForm.id) {
                const editAccount: UpdateUserInfoRequest = {
                    id: accountForm.id,
                    type: accountForm.type,
                    userName: accountForm.username
                };
                const response = await updateUserinfo(editAccount)
                setShowDialog(false);
                if (response.code == 200) {
                    message.success('更新成功');
                    const fastAccounts = await getAccounts();
                    setAccounts(fastAccounts.data);
                } else {
                    message.error("更新失败")
                }
            }
        }
    };

    // 关闭主对话框
    const handleCloseDialog = () => {
        if (sseConnecting) {
            closeSSEConnection();
        }
        setShowCaptchaModal(false); // 同时关闭验证码弹窗
        setCaptchaInput('');
        setIsVerifyingCaptcha(false);
        setCaptchaError('');
        setTimeout(() => {
            setShowDialog(false);
            setQrCodeData('');
            setLoginStatus('');
        }, 100);
    };

    // 初始化数据
    useEffect(() => {
        if (!hasFetched.current) {
            hasFetched.current = true;
            fetchAccountsQuick();
        }
    }, []);

    // 组件卸载时关闭SSE连接
    useEffect(() => {
        const handleBeforeUnload = () => {
            console.log('页面即将刷新/关闭，清理SSE连接');
            closeSSEConnection();
        };

        window.addEventListener('beforeunload', handleBeforeUnload);

        return () => {
            closeSSEConnection();
            window.removeEventListener('beforeunload', handleBeforeUnload);
        };
    }, []);

    return (
        <div className="flex-1 bg-gray-50 flex relative h-full overflow-hidden">
            {/* 主要内容区域 */}
            <main className="transition-all duration-300 w-full">
                <div
                    className="bg-white overflow-y-auto pb-2 h-[98%] rounded-[10px] shadow-lg border border-gray-200 m-3"
                    style={{boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)'}}>

                    {/* 页面标题 */}
                    <div className="mx-[5%] pt-[2.5%] mb-6">
                        <h1 className="text-2xl font-bold text-gray-800">账号管理</h1>
                    </div>

                    {/* 平台分类标签 */}
                    <div className="mx-[5%] mt-4">
                        <div className="mt-3 flex justify-between gap-2">
                            {platformCounts.map((platform, index) => {
                                const tabKey = index === 0 ? 'all' :
                                    platform.name === '快手' ? 'kuaishou' :
                                        platform.name === '抖音' ? 'douyin' :
                                            platform.name === '视频号' ? 'channels' : 'xiaohongshu';

                                return (
                                    <div
                                        key={platform.name}
                                        onClick={() => setActiveTab(tabKey as any)}
                                        className={`cursor-pointer font-bold rounded-[10px] flex justify-between items-center px-4 py-2.5 transition-colors flex-1 ${
                                            activeTab === tabKey
                                                ? 'bg-teal-600 text-white'
                                                : 'text-gray-600 bg-gray-200 hover:bg-gray-300'
                                        }`}
                                    >
                                        <div className="text-sm">{platform.name}</div>
                                        <div
                                            className={`text-sm ${activeTab === tabKey ? 'text-white' : 'text-gray-600'}`}>
                                            {platform.count}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>

                    {/* 搜索和操作按钮 */}
                    <div className="mx-[5%] mt-6">
                        <div className="flex items-center justify-between gap-4 mb-6">
                            <div className="flex-1">
                                <div className="relative">
                                    <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400"
                                            size={20}/>
                                    <input
                                        type="text"
                                        placeholder="输入名称或账号搜索"
                                        value={searchKeyword}
                                        onChange={(e) => setSearchKeyword(e.target.value)}
                                        className="w-full h-[40px] pl-10 pr-4 bg-gray-100 border-none rounded-[40px] focus:outline-none focus:ring-2 focus:ring-teal-500"
                                    />
                                </div>
                            </div>
                            <div className="flex items-center gap-3">
                                <button
                                    onClick={handleAddAccount}
                                    className="px-4 py-2 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors flex items-center gap-2"
                                >
                                    <Plus size={18}/>
                                    添加账号
                                </button>
                                <button
                                    onClick={fetchAccounts}
                                    disabled={isRefreshing}
                                    className="px-4 py-2 text-gray-700 bg-gray-200 rounded-lg hover:bg-gray-300 transition-colors flex items-center gap-2 disabled:opacity-50"
                                >
                                    <RefreshCw size={18} className={isRefreshing ? 'animate-spin' : ''}/>
                                    {isRefreshing ? '刷新中' : '刷新'}
                                </button>
                            </div>
                        </div>

                        {/* 账户列表表格 */}
                        <div className="bg-white rounded-lg overflow-hidden border border-gray-200">
                            {/* 表头 */}
                            <div
                                className="grid gap-4 p-4 border-b border-gray-200 bg-gray-50 text-sm font-medium text-gray-700"
                                style={{gridTemplateColumns: '150px 380px 320px 320px 1fr'}}>
                                <div className="flex items-center justify-center">头像</div>
                                <div className="flex items-center justify-center">名称</div>
                                <div className="flex items-center justify-center">平台</div>
                                <div className="flex items-center justify-center">状态</div>
                                <div className="flex items-center justify-center">操作</div>
                            </div>

                            {/* 表格内容 */}
                            <div className="divide-y divide-gray-200">
                                {filteredAccounts.length === 0 ? (
                                    <div className="flex justify-center items-center py-12">
                                        <div className="text-gray-500">
                                            {activeTab === 'all' ? '暂无账号数据' :
                                                `暂无${activeTab === 'kuaishou' ? '快手' :
                                                    activeTab === 'douyin' ? '抖音' :
                                                        activeTab === 'channels' ? '视频号' : '小红书'}账号数据`}
                                        </div>
                                    </div>
                                ) : (
                                    filteredAccounts.map((account, index) => (
                                        <div key={account.id}
                                             className={`grid gap-4 p-4 hover:bg-gray-50 transition-colors ${
                                                 index % 2 === 1 ? 'bg-gray-50' : 'bg-white'
                                             }`}
                                             style={{gridTemplateColumns: '150px 380px 320px 320px 1fr'}}>

                                            {/* 头像 */}
                                            <div className="flex items-center justify-center">
                                                <img
                                                    src={getDefaultAvatar(account.userName)}
                                                    alt={account.userName}
                                                    className="w-10 h-10 rounded-full"
                                                />
                                            </div>

                                            {/* 名称 */}
                                            <div
                                                className="text-sm text-gray-800 truncate flex items-center justify-center">
                                                {account.userName}
                                            </div>

                                            {/* 平台 */}
                                            <div className="flex items-center justify-center">
                                                <span
                                                    className={`text-xs px-2 py-1 rounded-full ${getPlatformTagColor(account.type)}`}>
                                                    {platformMap[account.type]}
                                                </span>
                                            </div>

                                            {/* 状态 */}
                                            <div className="flex items-center justify-center">
                                                <span
                                                    className={`text-xs px-2 py-1 rounded-full ${getStatusTagColor(account.status)} ${
                                                        isStatusClickable(account.status) ? 'cursor-pointer hover:opacity-80' : ''
                                                    }`}
                                                    onClick={() => isStatusClickable(account.status) && handleReLogin(account)}
                                                >
                                                    {account.status === 2 && (
                                                        <Loader2 size={12} className="inline mr-1 animate-spin"/>
                                                    )}
                                                    {statusMap[account.status]}
                                                </span>
                                            </div>

                                            {/* 操作 */}
                                            <div className="flex items-center gap-2 items-center justify-center">
                                                <button
                                                    onClick={() => handleEdit(account)}
                                                    className="px-3 py-1 text-gray-700 bg-gray-100 rounded text-xs hover:bg-gray-200 transition-colors flex items-center gap-1"
                                                >
                                                    <Edit size={12}/>
                                                    编辑
                                                </button>
                                                <button
                                                    onClick={() => handleDownloadCookie(account)}
                                                    className="px-3 py-1 text-blue-600 bg-blue-50 rounded text-xs hover:bg-blue-100 transition-colors flex items-center gap-1"
                                                >
                                                    <Download size={12}/>
                                                    下载Cookie
                                                </button>
                                                <button
                                                    onClick={() => handleUploadCookie(account)}
                                                    className="px-3 py-1 text-gray-600 bg-gray-50 rounded text-xs hover:bg-gray-100 transition-colors flex items-center gap-1"
                                                >
                                                    <Upload size={12}/>
                                                    上传Cookie
                                                </button>
                                                <button
                                                    onClick={() => handleDelete(account)}
                                                    className="px-3 py-1 text-red-600 bg-red-50 rounded text-xs hover:bg-red-100 transition-colors flex items-center gap-1"
                                                >
                                                    <Trash2 size={12}/>
                                                    删除
                                                </button>
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    </div>
                </div>
            </main>

            {/* 隐藏的文件输入框 */}
            <input
                type="file"
                ref={fileInputRef}
                onChange={handleFileUpload}
                accept=".json"
                className="hidden"
            />
            {/*账号验证阶段禁止任何操作*/}
            {isRefreshing && (
                <div
                    style={{
                        position: 'fixed',
                        top: 0,
                        left: 0,
                        right: 0,
                        bottom: 0,
                        backgroundColor: 'rgba(255, 255, 255, 0.5)',
                        zIndex: 9999,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexDirection: 'column',
                    }}
                    onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                    }}
                    onKeyDown={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                    }}
                    tabIndex={-1}
                >
                </div>
            )}

            {/* 添加/编辑账号对话框 */}
            {showDialog && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg shadow-xl w-[90vw] max-w-md">
                        {/* 弹窗头部 */}
                        <div className="flex items-center justify-between p-6 border-b border-gray-200">
                            <h2 className="text-xl font-semibold text-gray-800">
                                {dialogType === 'add' ? '添加账号' : '编辑账号'}
                            </h2>
                            <button
                                onClick={handleCloseDialog}
                                disabled={sseConnecting}
                                className="text-gray-400 hover:text-gray-600 transition-colors disabled:opacity-50"
                            >
                                <X size={24}/>
                            </button>
                        </div>

                        {/* 弹窗内容 */}
                        <div className="p-6 space-y-4">
                            {!sseConnecting ? (
                                <>
                                    {/* 平台选择 */}
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">
                                            平台 <span className="text-red-500">*</span>
                                        </label>
                                        <select
                                            value={accountForm.type.toString()}
                                            onChange={(e) => setAccountForm({
                                                ...accountForm,
                                                type: parseInt(e.target.value)
                                            })}
                                            disabled={dialogType === 'edit'}
                                            className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent transition-colors bg-white disabled:opacity-50"
                                        >
                                            <option value="">请选择平台</option>
                                            <option value="4">快手</option>
                                            <option value="3">抖音</option>
                                            <option value="2">视频号</option>
                                            <option value="1">小红书</option>
                                        </select>
                                    </div>

                                    {/* 名称输入 */}
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">
                                            名称 <span className="text-red-500">*</span>
                                        </label>
                                        <input
                                            type="text"
                                            value={accountForm.username}
                                            onChange={(e) => {
                                                setAccountForm({...accountForm, username: e.target.value});
                                            }}
                                            placeholder="请输入账号名称"
                                            className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent transition-colors"
                                        />
                                    </div>
                                </>
                            ) : (
                                /* 二维码显示区域 */
                                <div className="flex flex-col items-center justify-center min-h-[250px]">
                                    {qrCodeData && !loginStatus ? (
                                        <div className="text-center">
                                            <p className="text-gray-600 mb-4">{'请使用' + platformMap[accountForm.type] + '平台APP扫描二维码登录'}</p>
                                            <div
                                                className="w-48 h-48 bg-white border border-gray-300 flex items-center justify-center rounded">
                                                {qrCodeData ? (
                                                    <img
                                                        src={qrCodeData}
                                                        alt="登录二维码"
                                                        style={{backgroundColor: '#ffffff'}}
                                                        className="w-full h-full object-contain p-2"
                                                        onError={(e) => {
                                                            console.error('二维码图片加载失败:', e);
                                                            e.currentTarget.style.display = 'none';
                                                        }}
                                                    />
                                                ) : (
                                                    <QrCode size={100} className="text-gray-400"/>
                                                )}
                                            </div>
                                        </div>
                                    ) : !qrCodeData && !loginStatus ? (
                                        <div className="text-center">
                                            <Loader2 size={48} className="animate-spin text-gray-400 mx-auto mb-4"/>
                                            <span className="text-gray-600">请求中...</span>
                                        </div>
                                    ) : loginStatus === '200' ? (
                                        <div className="text-center">
                                            <CheckCircle size={48} className="text-green-500 mx-auto mb-4"/>
                                            <span className="text-green-600">添加成功</span>
                                        </div>
                                    ) : loginStatus === '500' ? (
                                        <div className="text-center">
                                            <AlertCircle size={48} className="text-red-500 mx-auto mb-4"/>
                                            <span className="text-red-600">添加失败，请稍后再试</span>
                                        </div>
                                    ) : null}
                                </div>
                            )}
                        </div>

                        {/* 弹窗底部按钮 */}
                        <div className="flex items-center justify-end gap-3 p-6 border-t border-gray-200 bg-gray-50">
                            <button
                                onClick={handleCloseDialog}
                                className="px-6 py-2 text-gray-600 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors disabled:opacity-50"
                            >
                                取消
                            </button>
                            <button
                                onClick={submitAccountForm}
                                disabled={sseConnecting || !accountForm.type || !accountForm.username}
                                className="px-6 py-2 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                {sseConnecting ? '请求中...' : '确认'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* 验证码输入弹窗 */}
            <Modal
                title="请输入验证码"
                open={showCaptchaModal}
                onCancel={handleCloseCaptchaModal}
                footer={[
                    <button
                        key="cancel"
                        onClick={handleCloseCaptchaModal}
                        className="px-4 py-2 text-gray-600 bg-gray-200 rounded hover:bg-gray-300 transition-colors"
                    >
                        取消
                    </button>,
                    <button
                        key="submit"
                        onClick={handleCaptchaSubmit}
                        disabled={isVerifyingCaptcha}
                        className={`px-4 py-2 text-white bg-teal-600 rounded hover:bg-teal-700 transition-colors ml-2 ${
                            isVerifyingCaptcha ? 'opacity-50 cursor-not-allowed' : ''
                        }`}
                    >
                        {isVerifyingCaptcha ? '验证中...' : '确定'}
                    </button>,
                ]}
            >
                <div className="space-y-3">
                    <Input
                        placeholder="请输入验证码"
                        value={captchaInput}
                        onChange={(e) => setCaptchaInput(e.target.value)}
                        disabled={isVerifyingCaptcha}
                        autoFocus
                    />
                    {captchaError && (
                        <div className="text-red-500 text-sm">{captchaError}</div>
                    )}
                </div>
            </Modal>
        </div>
    );
};

export default AccountManagement;
