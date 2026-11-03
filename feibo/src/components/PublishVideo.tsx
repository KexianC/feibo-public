import React, {useState, useEffect, useRef, useMemo} from 'react';
import {
    Upload, Plus, X, Folder, Check, AlertCircle, User,
    Video, Shield
} from 'lucide-react';
import {message} from "antd";
import { useNavigate } from 'react-router-dom';
import {getAccounts, postVideo, verifyAccount, uploadSave, deleteFile} from "@/services/videoPublishApi";
import {getUserWorks, getVideoInfo, type UserWork} from "@/services/digitalHumanApi";
import { ROUTE_PATHS } from '@/router/routePaths';

// 类型定义
interface FileItem {
    name: string;
    url: string;
    path: string;
    size: number;
    type: string;
    file?: File;
}

interface Account {
    id: number;
    type: number;
    filePath: string;
    userName: string;
    status: number;
    user_id: string;
}

interface TabData {
    name: string;
    label: string;
    fileList: FileItem[];
    displayFileList: Array<{ name: string; url: string }>;
    selectedAccount: string | null;
    selectedPlatform: number;
    title: string;
    productLink: string;
    productTitle: string;
    selectedTopics: string[];
    scheduleEnabled: boolean;
    videosPerDay: number;
    dailyTimes: string[];
    startDays: number;
    publishStatus: { message: string; type: string } | null;
    publishing: boolean;
    isDraft: boolean;
    verifyingAccount: boolean;
    accountVerified: boolean;
}

interface Platform {
    key: number;
    name: string;
}

interface PublishResult {
    label: string;
    status: 'success' | 'error' | 'cancelled';
    message: string;
}

const PublishCenter: React.FC = () => {
    // 状态管理
    const navigate = useNavigate();
    const [activeTab, setActiveTab] = useState('tab1');
    const [tabs, setTabs] = useState<TabData[]>([
        {
            name: 'tab1',
            label: '发布1',
            fileList: [],
            displayFileList: [],
            selectedAccount: null,
            selectedPlatform: 1,
            title: '',
            productLink: '',
            productTitle: '',
            selectedTopics: [],
            scheduleEnabled: false,
            videosPerDay: 1,
            dailyTimes: ['10:00'],
            startDays: 0,
            publishStatus: null,
            publishing: false,
            isDraft: false,
            verifyingAccount: false,
            accountVerified: false
        }
    ]);

    const [tabCounter, setTabCounter] = useState(1);

    // 上传相关状态
    const [uploadOptionsVisible, setUploadOptionsVisible] = useState(false);
    const [localUploadVisible, setLocalUploadVisible] = useState(false);
    const [userWorkLibraryVisible, setUserWorkLibraryVisible] = useState(false);
    const [currentUploadTab, setCurrentUploadTab] = useState<TabData | null>(null);
    const [selectedUserWork, setSelectedUserWork] = useState<UserWork>();

    // 账号相关状态
    const [accountDialogVisible, setAccountDialogVisible] = useState(false);
    const [tempSelectedAccount, setTempSelectedAccount] = useState<string | null>(null);
    const [currentTab, setCurrentTab] = useState<TabData | null>(null);

    // 话题相关状态
    const [topicDialogVisible, setTopicDialogVisible] = useState(false);
    const [customTopic, setCustomTopic] = useState('');

    // 批量发布相关状态
    const [batchPublishing, setBatchPublishing] = useState(false);
    const [batchPublishDialogVisible, setBatchPublishDialogVisible] = useState(false);
    const [currentPublishingTab, setCurrentPublishingTab] = useState<TabData | null>(null);
    const [publishProgress, setPublishProgress] = useState(0);
    const [publishResults, setPublishResults] = useState<PublishResult[]>([]);
    const [isCancelled, setIsCancelled] = useState(false);
    //单个任务取消发布状态
    const [cancelledStatus, setCancelledStatus] = useState(0);
    //防止重复挂载
    const hasFetched = useRef(false);

    // 文件上传引用
    const fileInputRef = useRef<HTMLInputElement>(null);
    // 批量发布取消引用
    const cancelledRef = useRef(false);

    // 账号数据
    const [accounts, setAccounts] = useState<Account[]>([]);
    // 我的作品数据
    const [userWorks, setUserWorks] = useState<UserWork[]>([]);

    // 平台列表
    const platforms: Platform[] = [
        {key: 1, name: '小红书'},
        {key: 2, name: '视频号'},
        {key: 3, name: '抖音'},
        {key: 4, name: '快手'}
    ];

    // 推荐话题
    const recommendedTopics = [
        '游戏', '电影', '音乐', '美食', '旅行', '文化',
        '科技', '生活', '娱乐', '体育', '教育', '艺术',
        '健康', '时尚', '美妆', '摄影', '宠物', '汽车'
    ];

    // 获取当前激活的tab
    const getCurrentTab = () => tabs.find(tab => tab.name === activeTab);

    // 快速获取账户数据
    const fetchAccountsQuick = async () => {
        try {
            const fastAccounts = await getAccounts();
            // message.success("获取账号成功");
            setAccounts(fastAccounts.data);
        } catch (error) {
            console.error('快速获取账户失败:', error);
            message.error('快速获取账户失败');
        }
    };

    // 验证账号有效性
    const verifyAccountValidity = async (accountId: number): Promise<boolean> => {
        try {
            const result = await verifyAccount(accountId);
            return result.code === 200;
        } catch (error) {
            console.error('验证账号失败:', error);
            return false;
        }
    };

    // 加载我的作品
    const loadUserWorks = async () => {
        try {
            const result = await getUserWorks();
            console.log('获取我的作品成功，记录数:', result.records.length);

            const worksWithVideoInfo = await Promise.all(
                result.records.map(async (work) => {
                    try {
                        const videoInfo = await getVideoInfo(work.externalId);
                        return {
                            ...work,
                            videoUrl: videoInfo.videoUrl || work.videoUrl,
                            coverUrl: videoInfo.coverUrl || work.coverUrl,
                            thumbnailUrl: videoInfo.thumbnailUrl || work.thumbnailUrl,
                            duration: videoInfo.duration || work.duration,
                            width: videoInfo.width || work.width,
                            height: videoInfo.height || work.height,
                            status: videoInfo.status || work.status,
                            progress: videoInfo.progress || work.progress,
                            errorMessage: videoInfo.errorMessage || work.errorMessage,
                            createTime: videoInfo.createTime || work.createTime,
                            updateTime: videoInfo.updateTime || work.updateTime
                        };
                    } catch (error) {
                        console.warn(`获取作品${work.digitalWorkId}的视频信息失败:`, error);
                        return work;
                    }
                })
            );
            setUserWorks(worksWithVideoInfo);
        } catch (error) {
            console.error('加载我的作品失败:', error);
            message.error('加载我的作品失败');
        }
    };

    // 根据平台获取媒体账号
    const getAvailableAccounts = useMemo(() => {
        const platformMap: Record<number, string> = {
            3: '抖音',
            2: '视频号',
            1: '小红书',
            4: '快手'
        };

        const currentTabData = getCurrentTab();
        const currentPlatform = currentTabData ? platformMap[currentTabData.selectedPlatform] : null;

        return currentPlatform ? accounts.filter(acc => platformMap[acc.type] === currentPlatform) : [];
    }, [accounts, activeTab, tabs]);

    // 添加新tab
    const addTab = () => {
        const newCounter = tabCounter + 1;
        setTabCounter(newCounter);

        const newTab: TabData = {
            name: `tab${newCounter}`,
            label: `发布${newCounter}`,
            fileList: [],
            displayFileList: [],
            selectedAccount: null,
            selectedPlatform: 1,
            title: '',
            productLink: '',
            productTitle: '',
            selectedTopics: [],
            scheduleEnabled: false,
            videosPerDay: 1,
            dailyTimes: ['10:00'],
            startDays: 0,
            publishStatus: null,
            publishing: false,
            isDraft: false,
            verifyingAccount: false,
            accountVerified: false
        };

        setTabs(prev => [...prev, newTab]);
        setActiveTab(newTab.name);
    };

    // 删除tab
    const removeTab = (tabName: string) => {
        const index = tabs.findIndex(tab => tab.name === tabName);
        if (index > -1) {
            const newTabs = [...tabs];
            newTabs.splice(index, 1);
            setTabs(newTabs);

            if (activeTab === tabName && newTabs.length > 0) {
                setActiveTab(newTabs[0].name);
            }
        }
    };

    // 显示上传选项
    const showUploadOptions = (tab: TabData) => {
        setCurrentUploadTab(tab);
        setUploadOptionsVisible(true);
    };

    // 选择本地上传
    const selectLocalUpload = () => {
        setUploadOptionsVisible(false);
        setLocalUploadVisible(true);
    };

    // 选择我的作品库
    const selectUserWorkLibrary = async () => {
        setUploadOptionsVisible(false);
        setSelectedUserWork(undefined);
        await loadUserWorks();
        setUserWorkLibraryVisible(true);
    };

    // 处理文件选择（限制为单个文件）
    const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = e.target.files;
        if (!files || !currentUploadTab) return;

        const file = files[0];
        if (!file) return;

        const newFile: FileItem = {
            name: file.name,
            url: URL.createObjectURL(file),
            path: `/uploads/${file.name}`,
            size: file.size,
            type: file.type,
            file: file
        };

        const tabIndex = tabs.findIndex(tab => tab.name === currentUploadTab.name);
        if (tabIndex > -1) {
            const updatedTabs = [...tabs];
            updatedTabs[tabIndex] = {
                ...updatedTabs[tabIndex],
                fileList: [newFile],
                displayFileList: [{name: newFile.name, url: newFile.url}]
            };
            setTabs(updatedTabs);
        }

        setLocalUploadVisible(false);
        message.success('文件上传成功');
    };

    // 获取视频大小
    const getVideoSize = async (videoUrl: string): Promise<number> => {
        try {
            let response = await fetch(videoUrl, {
                method: 'HEAD',
                mode: 'cors',
                cache: 'no-cache',
                headers: {
                    'Accept': 'video/mp4,video/*;q=0.9',
                }
            });

            if (!response.ok && response.status === 405) {
                response = await fetch(videoUrl, {
                    method: 'GET',
                    mode: 'cors',
                    cache: 'no-cache',
                    headers: {
                        'Range': 'bytes=0-0'
                    }
                });
            }

            if (response.ok) {
                const contentLength = response.headers.get('Content-Length');
                if (contentLength) {
                    return parseInt(contentLength, 10);
                } else {
                    const contentRange = response.headers.get('Content-Range');
                    if (contentRange) {
                        const match = contentRange.match(/bytes \d+-\d+\/(\d+)/);
                        if (match) {
                            return parseInt(match[1], 10);
                        }
                    }
                }
            }
        } catch (error) {
            console.error('获取视频大小失败:', error);
        }
        return 0;
    };

    // 确认作品选择
    const confirmUserWorkSelection = async () => {
        if (!selectedUserWork) {
            message.error('请选择您的作品！');
            return;
        }

        if (!selectedUserWork.videoUrl) {
            message.error('该作品暂无视频文件');
            return;
        }

        if (currentUploadTab) {
            const videosize: number = await getVideoSize(selectedUserWork.videoUrl);
            const fileItem: FileItem = {
                name: selectedUserWork.name || selectedUserWork.title || `作品${selectedUserWork.digitalWorkId}`,
                url: selectedUserWork.videoUrl,
                path: selectedUserWork.videoUrl,
                size: videosize,
                type: 'video/mp4'
            };

            const tabIndex = tabs.findIndex(tab => tab.name === currentUploadTab.name);
            if (tabIndex > -1) {
                const updatedTabs = [...tabs];
                updatedTabs[tabIndex] = {
                    ...updatedTabs[tabIndex],
                    fileList: [fileItem],
                    displayFileList: [{name: fileItem.name, url: fileItem.url}]
                };
                setTabs(updatedTabs);
            }
        }

        setUserWorkLibraryVisible(false);
        setSelectedUserWork(undefined);
        setCurrentUploadTab(null);
        message.success(`已添加作品：${selectedUserWork.name || selectedUserWork.title}`);
    };

    // 删除文件
    const removeFile = (tab: TabData) => {
        const tabIndex = tabs.findIndex(t => t.name === tab.name);
        if (tabIndex > -1) {
            const updatedTabs = [...tabs];
            updatedTabs[tabIndex].fileList = [];
            updatedTabs[tabIndex].displayFileList = [];
            setTabs(updatedTabs);
        }
    };

    // 打开账号选择弹窗
    const openAccountDialog = (tab: TabData) => {
        setCurrentTab(tab);
        setTempSelectedAccount(tab.selectedAccount);
        setAccountDialogVisible(true);
    };

    // 确认账号选择
    const confirmAccountSelection = () => {
        if (currentTab && tempSelectedAccount !== null) {
            const tabIndex = tabs.findIndex(tab => tab.name === currentTab.name);
            if (tabIndex > -1) {
                const updatedTabs = [...tabs];
                updatedTabs[tabIndex] = {
                    ...updatedTabs[tabIndex],
                    selectedAccount: tempSelectedAccount,
                    accountVerified: false
                };
                setTabs(updatedTabs);
            }
        }
        setAccountDialogVisible(false);
        setCurrentTab(null);
        setTempSelectedAccount(null);
    };

    // 删除账号
    const removeAccount = (tab: TabData) => {
        const tabIndex = tabs.findIndex(t => t.name === tab.name);
        if (tabIndex > -1) {
            const updatedTabs = [...tabs];
            updatedTabs[tabIndex].selectedAccount = null;
            updatedTabs[tabIndex].accountVerified = false;
            setTabs(updatedTabs);
        }
    };

    // 验证账号
    const verifyAccountInTab = async (tab: TabData) => {
        if (!tab.selectedAccount) {
            message.error('请先选择账号');
            return false;
        }

        const tabIndex = tabs.findIndex(t => t.name === tab.name);
        if (tabIndex > -1) {
            const updatedTabs = [...tabs];
            updatedTabs[tabIndex].verifyingAccount = true;
            setTabs(updatedTabs);

            try {
                const isValid = await verifyAccountValidity(parseInt(tab.selectedAccount || '0'));

                const updatedTabsAfterVerify = [...tabs];
                updatedTabsAfterVerify[tabIndex] = {
                    ...updatedTabsAfterVerify[tabIndex],
                    verifyingAccount: false,
                    accountVerified: isValid
                };
                setTabs(updatedTabsAfterVerify);

                if (isValid) {
                    message.success('账号验证成功');
                } else {
                    message.error('账号验证失败，请检查账号状态');
                }

                return isValid;
            } catch (error) {
                console.error('账号验证失败:', error);
                const updatedTabsAfterVerify = [...tabs];
                updatedTabsAfterVerify[tabIndex] = {
                    ...updatedTabsAfterVerify[tabIndex],
                    verifyingAccount: false,
                    accountVerified: false
                };
                setTabs(updatedTabsAfterVerify);
                message.error('账号验证失败');
                return false;
            }
        }
        return false;
    };

    // 获取账号显示名称
    const getAccountDisplayName = (accountId: string | null) => {
        if (!accountId) return '未选择账号';
        const account = accounts.find(acc => acc.id.toString() === accountId);
        return account ? account.userName : accountId;
    };

    // 获取账号信息
    const getAccountInfo = (accountId: string | null) => {
        if (!accountId) return null;
        return accounts.find(acc => acc.id.toString() === accountId);
    };

    // 打开话题弹窗
    const openTopicDialog = (tab: TabData) => {
        setCurrentTab(tab);
        setTopicDialogVisible(true);
    };

    // 添加自定义话题
    const addCustomTopic = () => {
        if (!customTopic.trim()) {
            message.error('请输入话题内容');
            return;
        }

        if (currentTab && !currentTab.selectedTopics.includes(customTopic.trim())) {
            const tabIndex = tabs.findIndex(tab => tab.name === currentTab.name);
            if (tabIndex > -1) {
                const updatedTabs = [...tabs];
                updatedTabs[tabIndex].selectedTopics.push(customTopic.trim());
                setTabs(updatedTabs);
                setCustomTopic('');
            }
        } else {
            message.error('话题已存在');
        }
    };

    // 切换推荐话题
    const toggleRecommendedTopic = (topic: string) => {
        if (!currentTab) return;

        const tabIndex = tabs.findIndex(tab => tab.name === currentTab.name);
        if (tabIndex > -1) {
            const updatedTabs = [...tabs];
            const topics = updatedTabs[tabIndex].selectedTopics;
            const index = topics.indexOf(topic);

            if (index > -1) {
                topics.splice(index, 1);
            } else {
                topics.push(topic);
            }

            setTabs(updatedTabs);
        }
    };

    // 删除话题
    const removeTopic = (tab: TabData, index: number) => {
        const tabIndex = tabs.findIndex(t => t.name === tab.name);
        if (tabIndex > -1) {
            const updatedTabs = [...tabs];
            updatedTabs[tabIndex].selectedTopics.splice(index, 1);
            setTabs(updatedTabs);
        }
    };

    // 确认话题选择
    const confirmTopicSelection = () => {
        setTopicDialogVisible(false);
        setCustomTopic('');
        setCurrentTab(null);
    };

    // 添加时间
    const addTime = (tab: TabData) => {
        const tabIndex = tabs.findIndex(t => t.name === tab.name);
        if (tabIndex > -1) {
            const updatedTabs = [...tabs];
            updatedTabs[tabIndex].dailyTimes.push('10:00');
            setTabs(updatedTabs);
        }
    };

    // 更新tab数据
    const updateTab = (field: keyof TabData, value: any) => {
        const tabIndex = tabs.findIndex(tab => tab.name === activeTab);
        if (tabIndex > -1) {
            const updatedTabs = [...tabs];
            updatedTabs[tabIndex] = {
                ...updatedTabs[tabIndex],
                [field]: value
            };
            setTabs(updatedTabs);
        }
    };

    // 取消发布
    const cancelPublish = () => {
        if (cancelledStatus !== -1) {
            setCancelledStatus(1);
            message.info('正在取消发布...');
        } else {
            message.error('已到发布视频阶段，不可取消发布！');
        }
    };

    // 发布单个tab的核心逻辑
    const publishTabInternal = async (tab: TabData): Promise<{ success: boolean; message: string }> => {
        // 数据验证
        if (tab.fileList.length === 0) {
            return {success: false, message: '请先上传视频文件'};
        }
        if (!tab.title.trim()) {
            return {success: false, message: '请输入标题'};
        }
        if (!tab.selectedAccount) {
            return {success: false, message: '请选择发布账号'};
        }
        if (tab.isDraft && tab.scheduleEnabled) {
            return {success: false, message: '草稿发布与定时发布不可同时选择'}
        }

        // 账号验证（如果未验证）
        if (!tab.accountVerified) {
            const isValid = await verifyAccountInTab(tab);
            if (!isValid) {
                return {success: false, message: '账号验证失败，请检查账号状态'};
            }
        }

        let file_id: number = -1;

        try {
            // 查找账号信息
            const accountInfo = accounts.find(acc => acc.id.toString() === tab.selectedAccount);
            if (!accountInfo) {
                return {success: false, message: '账号信息不存在'};
            }

            // 如果是本地上传的文件，需要先上传到服务器在进行发布
            if (tab.fileList[0].file) {
                const response = await uploadSave(tab.fileList[0].file);
                if (response.code === 200) {
                    tab.fileList[0].path = response.data.filepath;
                    file_id = response.data.id;
                    message.success("上传本地文件到服务器成功！");
                } else {
                    return {success: false, message: "上传本地文件到服务器失败，请重试"};
                }
            }

            // 构造发布数据
            const publishData = {
                type: tab.selectedPlatform,
                title: tab.title,
                tags: tab.selectedTopics,
                fileList: tab.fileList.map(file => file.path),
                accountList: [accountInfo.filePath],
                enableTimer: tab.scheduleEnabled ? 1 : 0,
                videosPerDay: tab.scheduleEnabled ? tab.videosPerDay || 1 : 1,
                dailyTimes: tab.scheduleEnabled ? tab.dailyTimes || ['10:00'] : ['10:00'],
                startDays: tab.scheduleEnabled ? tab.startDays || 0 : 0,
                category: 0,
                productLink: tab.productLink.trim() || '',
                productTitle: tab.productTitle.trim() || '',
                isDraft: tab.isDraft
            };

            const response = await postVideo(publishData);

            if (response.code === 200) {
                return {success: true, message: '发布成功'};
            } else {
                return {success: false, message: response.msg || '发布失败'};
            }
        } catch (error) {
            console.error('发布错误:', error);
            return {success: false, message: '发布失败，请检查网络连接'};
        } finally {
            // 不管成功与否，都需要删除服务器存储的本地文件
            if (file_id !== -1) {
                try {
                    const result = await deleteFile(file_id);
                    if (result.code === 200) {
                        console.log("删除本地文件成功");
                    } else {
                        console.log('删除本地文件未成功，状态码:', result.code);
                    }
                } catch (error) {
                    console.error('删除文件失败:', error);
                }
            }
        }
    };

    // 确认发布（带账号验证）
    const confirmPublish = async (tab: TabData) => {
        if (tab.publishing) {
            message.error('正在发布中，请稍候...');
            return;
        }

        // 设置发布中状态
        const tabIndex = tabs.findIndex(t => t.name === tab.name);
        if (tabIndex > -1) {
            const updatedTabs = [...tabs];
            updatedTabs[tabIndex].publishing = true;
            updatedTabs[tabIndex].verifyingAccount = false;
            setTabs(updatedTabs);
        }

        const result = await publishTabInternal(tab);

        const updatedTabs = [...tabs];
        const currentTabIndex = tabs.findIndex(t => t.name === tab.name);

        if (currentTabIndex > -1) {
            updatedTabs[currentTabIndex] = {
                ...updatedTabs[currentTabIndex],
                publishing: false,
                verifyingAccount: false,
                publishStatus: {
                    message: result.message,
                    type: result.success ? 'success' : 'error'
                }
            };

            // 如果发布成功，清空数据
            if (result.success) {
                updatedTabs[currentTabIndex] = {
                    ...updatedTabs[currentTabIndex],
                    fileList: [],
                    displayFileList: [],
                    title: '',
                    selectedTopics: [],
                    selectedAccount: null,
                    scheduleEnabled: false,
                    verifyingAccount: false,
                    accountVerified: false,
                    productLink: '',
                    productTitle: '',
                    isDraft: false,
                };
                message.success('发布成功');
            } else {
                message.error(result.message);
            }

            setTabs(updatedTabs);
        }
    };

    // 取消批量发布
    const cancelBatchPublish = () => {
        cancelledRef.current = true;
        setIsCancelled(true);
        message.success('取消发布成功！本任务完成后后续任务不再进行！');
    };

    // 批量发布（带账号验证）
    const batchPublish = async () => {
        if (batchPublishing) return;

        setBatchPublishing(true);
        setCurrentPublishingTab(null);
        setPublishProgress(0);
        setPublishResults([]);
        setIsCancelled(false);
        cancelledRef.current = false;
        setBatchPublishDialogVisible(true);

        const results: PublishResult[] = [];

        try {
            for (let i = 0; i < tabs.length; i++) {
                if (cancelledRef.current) {
                    results.push({
                        label: tabs[i].label,
                        status: 'cancelled',
                        message: '已取消'
                    });
                    setPublishResults([...results]);
                    continue;
                }

                const tab = tabs[i];
                setCurrentPublishingTab(tab);

                // 发布当前tab
                const result = await publishTabInternal(tab);

                // 累积结果
                const publishResult: PublishResult = {
                    label: tab.label,
                    status: result.success ? 'success' : 'error',
                    message: result.message
                };
                results.push(publishResult);

                // 更新状态
                setPublishResults([...results]);

                // 更新进度
                const progress = Math.floor(((i + 1) / tabs.length) * 100);
                setPublishProgress(progress);

                // 如果发布成功，更新tab数据（清空）
                if (result.success) {
                    const tabIndex = tabs.findIndex(t => t.name === tab.name);
                    if (tabIndex > -1) {
                        const updatedTabs = [...tabs];
                        updatedTabs[tabIndex] = {
                            ...updatedTabs[tabIndex],
                            fileList: [],
                            displayFileList: [],
                            title: '',
                            selectedTopics: [],
                            selectedAccount: null,
                            scheduleEnabled: false,
                            accountVerified: false,
                            productLink: '',
                            productTitle: '',
                            isDraft: false
                        };
                        setTabs(updatedTabs);
                    }
                }
            }

            setPublishProgress(100);

            // 统计结果
            const successCount = results.filter(r => r.status === 'success').length;
            const failCount = results.filter(r => r.status === 'error').length;
            const cancelCount = results.filter(r => r.status === 'cancelled').length;

            if (cancelledRef.current) {
                message.success(`发布已取消：${successCount}个成功，${failCount}个失败，${cancelCount}个未执行`);
            } else if (failCount > 0) {
                message.success(`发布完成：${successCount}个成功，${failCount}个失败`);
            } else {
                message.success('所有Tab发布成功');
                setTimeout(() => {
                    setBatchPublishDialogVisible(false);
                }, 1000);
            }
        } catch (error) {
            console.error('批量发布出错:', error);
            message.error('批量发布出错，请重试');
        } finally {
            setBatchPublishing(false);
            setIsCancelled(false);
        }
    };

    // 处理平台切换
    const handlePlatformChange = (platformKey: number) => {
        const currentTabData = getCurrentTab();
        if (!currentTabData) return;

        // 如果切换到了不同的平台
        if (currentTabData.selectedPlatform !== platformKey) {
            const tabIndex = tabs.findIndex(tab => tab.name === currentTabData.name);
            if (tabIndex > -1) {
                const updatedTabs = [...tabs];

                // 如果有已选择的账号，清空它
                if (currentTabData.selectedAccount) {
                    updatedTabs[tabIndex] = {
                        ...updatedTabs[tabIndex],
                        selectedAccount: null,
                        accountVerified: false,
                        selectedPlatform: platformKey
                    };
                    message.warning('已切换平台，请重新选择账号');
                } else {
                    // 如果没有选择账号，直接更新平台
                    updatedTabs[tabIndex] = {
                        ...updatedTabs[tabIndex],
                        selectedPlatform: platformKey
                    };
                }

                setTabs(updatedTabs);
            }
        }
    };

    const currentTabData = getCurrentTab();

    // 初始化数据
    useEffect(() => {
        if (!hasFetched.current) {
            hasFetched.current = true;
            fetchAccountsQuick();
            loadUserWorks();
        }
    }, []);

    return (
        <div className="flex-1 bg-gray-50 flex relative h-full overflow-hidden">
            {/* 主要内容区域 */}
            <main className="transition-all duration-300 w-full">
                <div
                    className="bg-white overflow-y-auto pb-2 h-[98%] rounded-[10px] shadow-lg border border-gray-200 m-3"
                    style={{boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)'}}>

                    {/* Tab管理区域 */}
                    <div className="mx-[5%] pt-[2.5%] mb-6">
                        <div className="bg-white rounded-lg border border-gray-200 shadow-sm mb-6">
                            <div className="p-4 flex items-center justify-between">
                                {/* Tab列表 */}
                                <div className="flex flex-wrap gap-2 flex-1">
                                    {tabs.map(tab => (
                                        <div
                                            key={tab.name}
                                            className={`flex items-center gap-1 px-3 py-1 rounded-lg cursor-pointer transition-colors ${
                                                activeTab === tab.name
                                                    ? 'bg-teal-600 text-white'
                                                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                            }`}
                                            onClick={() => setActiveTab(tab.name)}
                                        >
                                            <span className="text-sm">{tab.label}</span>
                                            {tabs.length > 1 && (
                                                <button
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        removeTab(tab.name);
                                                    }}
                                                    className="p-0.5 hover:bg-white/20 rounded"
                                                >
                                                    <X size={14}/>
                                                </button>
                                            )}
                                        </div>
                                    ))}
                                </div>

                                {/* Tab操作按钮 */}
                                <div className="flex items-center gap-2">
                                    <button
                                        onClick={() => navigate(ROUTE_PATHS.account)}
                                        className="px-3 py-1 text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-1 text-sm"
                                    >
                                        <User size={16}/>
                                        账号管理
                                    </button>
                                    <button
                                        onClick={addTab}
                                        className="px-3 py-1 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors flex items-center gap-1 text-sm"
                                    >
                                        <Plus size={16}/>
                                        添加Tab
                                    </button>
                                    <button
                                        onClick={batchPublish}
                                        disabled={batchPublishing}
                                        className="px-3 py-1 text-white bg-green-600 rounded-lg hover:bg-green-700 transition-colors text-sm disabled:opacity-50"
                                    >
                                        {batchPublishing ? '发布中...' : '批量发布'}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Tab内容区域 */}
                    {currentTabData && (
                        <div className="mx-[5%]">
                            <div className="max-w-4xl mx-auto space-y-6">
                                {/* 发布状态提示 */}
                                {currentTabData.publishStatus && (
                                    <div className={`p-4 rounded-lg ${
                                        currentTabData.publishStatus.type === 'success'
                                            ? 'bg-green-50 border border-green-200'
                                            : 'bg-red-50 border border-red-200'
                                    }`}>
                                        <div className="flex items-center gap-2">
                                            {currentTabData.publishStatus.type === 'success' ? (
                                                <Check className="text-green-500" size={20}/>
                                            ) : (
                                                <AlertCircle className="text-red-500" size={20}/>
                                            )}
                                            <span
                                                className="font-medium">{currentTabData.publishStatus.message}</span>
                                        </div>
                                    </div>
                                )}

                                {/* 视频上传区域 */}
                                <div className="bg-white p-6 rounded-lg border border-gray-200">
                                    <h3 className="text-lg font-semibold text-gray-800 mb-4">视频</h3>
                                    <div className="space-y-4">
                                        <button
                                            onClick={() => showUploadOptions(currentTabData)}
                                            className="px-4 py-2 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors flex items-center gap-2"
                                        >
                                            <Upload size={18}/>
                                            上传视频
                                        </button>

                                        {/* 已上传文件列表 */}
                                        {currentTabData.fileList.length > 0 && (
                                            <div className="mt-4">
                                                <h4 className="text-sm font-medium text-gray-700 mb-2">已选择视频：</h4>
                                                <div className="space-y-2">
                                                    {currentTabData.fileList.map((file, index) => (
                                                        <div key={index}
                                                             className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                                                            <div className="flex items-center gap-3 flex-1">
                                                                <Video className="text-gray-400" size={20}/>
                                                                <div className="flex-1 min-w-0">
                                                                    <a
                                                                        href={file.url}
                                                                        target="_blank"
                                                                        rel="noopener noreferrer"
                                                                        className="text-blue-600 hover:text-blue-800 truncate block"
                                                                    >
                                                                        {file.name}
                                                                    </a>
                                                                    <span className="text-xs text-gray-500">
                                                                        {(file.size / 1024 / 1024).toFixed(2)}MB
                                                                    </span>
                                                                </div>
                                                            </div>
                                                            <button
                                                                onClick={() => removeFile(currentTabData)}
                                                                className="px-3 py-1 text-red-600 bg-red-50 rounded hover:bg-red-100 transition-colors text-sm"
                                                            >
                                                                删除
                                                            </button>
                                                        </div>
                                                    ))}
                                                </div>
                                                <p className="text-sm text-gray-500 mt-2">
                                                    每个发布页只能选择一个视频文件
                                                </p>
                                            </div>
                                        )}
                                        {currentTabData.fileList.length === 0 && (
                                            <p className="text-sm text-gray-500 mt-2">
                                                每个发布页只能选择一个视频文件
                                            </p>
                                        )}
                                    </div>
                                </div>

                                {/* 账号选择 */}
                                <div className="bg-white p-6 rounded-lg border border-gray-200">
                                    <h3 className="text-lg font-semibold text-gray-800 mb-4">账号</h3>
                                    <div className="space-y-4">
                                        {currentTabData.selectedAccount ? (
                                            <div className="space-y-3">
                                                <div
                                                    className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
                                                    <div className="flex items-center gap-3">
                                                        <User size={20} className="text-blue-500"/>
                                                        <div>
                                                            <div className="font-medium text-gray-800">
                                                                {getAccountDisplayName(currentTabData.selectedAccount)}
                                                            </div>
                                                            <div className="text-sm text-gray-500">
                                                                平台: {platforms.find(p => p.key === currentTabData.selectedPlatform)?.name || '未知'}
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div className="flex items-center gap-2">
                                                        {currentTabData.accountVerified && (
                                                            <span
                                                                className="px-2 py-1 bg-green-100 text-green-800 text-xs rounded-full flex items-center gap-1">
                                                                <Check size={12}/> 已验证
                                                            </span>
                                                        )}
                                                        <button
                                                            onClick={() => removeAccount(currentTabData)}
                                                            className="text-red-600 hover:text-red-800 p-1"
                                                            title="移除账号"
                                                        >
                                                            <X size={18}/>
                                                        </button>
                                                    </div>
                                                </div>

                                                <div className="flex items-center gap-3">
                                                    <button
                                                        onClick={() => verifyAccountInTab(currentTabData)}
                                                        disabled={currentTabData.verifyingAccount}
                                                        className="px-4 py-2 text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-2 disabled:opacity-50"
                                                    >
                                                        <Shield size={16}/>
                                                        {currentTabData.verifyingAccount ? '验证中...' : '验证账号'}
                                                    </button>

                                                    <button
                                                        onClick={() => openAccountDialog(currentTabData)}
                                                        className="px-4 py-2 text-blue-600 border border-blue-600 rounded-lg hover:bg-blue-50 transition-colors"
                                                    >
                                                        更换账号
                                                    </button>
                                                </div>
                                            </div>
                                        ) : (
                                            <div>
                                                <button
                                                    onClick={() => openAccountDialog(currentTabData)}
                                                    className="px-4 py-3 w-full text-blue-600 border-2 border-dashed border-blue-300 rounded-lg hover:bg-blue-50 transition-colors flex items-center justify-center gap-2"
                                                >
                                                    <User size={20}/>
                                                    选择发布账号
                                                </button>
                                                <p className="text-sm text-gray-500 mt-2 text-center">
                                                    每个发布页只能选择一个账号
                                                </p>
                                            </div>
                                        )}
                                    </div>
                                </div>

                                {/* 平台选择 */}
                                <div className="bg-white p-6 rounded-lg border border-gray-200">
                                    <h3 className="text-lg font-semibold text-gray-800 mb-4">平台</h3>
                                    <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
                                        {platforms.map(platform => (
                                            <button
                                                key={platform.key}
                                                onClick={() => handlePlatformChange(platform.key)}
                                                className={`px-4 py-3 rounded-lg border transition-colors ${
                                                    currentTabData.selectedPlatform === platform.key
                                                        ? 'bg-teal-600 text-white border-teal-600'
                                                        : 'bg-gray-50 text-gray-700 border-gray-300 hover:bg-gray-100'
                                                }`}
                                            >
                                                {platform.name}
                                            </button>
                                        ))}
                                    </div>
                                </div>

                                {/* 草稿选项（仅在视频号可见） */}
                                {currentTabData.selectedPlatform === 2 && (
                                    <div className="bg-white p-6 rounded-lg border border-gray-200">
                                        <div className="flex items-center">
                                            <input
                                                type="checkbox"
                                                id="draft"
                                                checked={currentTabData.isDraft}
                                                onChange={(e) => updateTab('isDraft', e.target.checked)}
                                                className="w-4 h-4 text-teal-600 rounded focus:ring-teal-500"
                                            />
                                            <label htmlFor="draft" className="ml-2 text-gray-700">
                                                视频号仅保存草稿（用手机发布）
                                            </label>
                                        </div>
                                    </div>
                                )}

                                {/* 商品链接（仅在抖音可见） */}
                                {currentTabData.selectedPlatform === 3 && (
                                    <div className="bg-white p-6 rounded-lg border border-gray-200 space-y-4">
                                        <h3 className="text-lg font-semibold text-gray-800">商品链接</h3>
                                        <div className="space-y-3">
                                            <div>
                                                <label
                                                    className="block text-sm font-medium text-gray-700 mb-1">商品名称</label>
                                                <input
                                                    type="text"
                                                    value={currentTabData.productTitle}
                                                    onChange={(e) => updateTab('productTitle', e.target.value)}
                                                    placeholder="请输入商品名称"
                                                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500"
                                                    maxLength={200}
                                                />
                                            </div>
                                            <div>
                                                <label
                                                    className="block text-sm font-medium text-gray-700 mb-1">商品链接</label>
                                                <input
                                                    type="text"
                                                    value={currentTabData.productLink}
                                                    onChange={(e) => updateTab('productLink', e.target.value)}
                                                    placeholder="请输入商品链接"
                                                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500"
                                                    maxLength={200}
                                                />
                                            </div>
                                        </div>
                                    </div>
                                )}

                                {/* 标题输入 */}
                                <div className="bg-white p-6 rounded-lg border border-gray-200">
                                    <h3 className="text-lg font-semibold text-gray-800 mb-4">标题</h3>
                                    <textarea
                                        value={currentTabData.title}
                                        onChange={(e) => updateTab('title', e.target.value)}
                                        placeholder="请输入标题"
                                        rows={3}
                                        maxLength={100}
                                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500 resize-none"
                                    />
                                    <div className="text-right text-sm text-gray-500 mt-1">
                                        {currentTabData.title.length}/100
                                    </div>
                                </div>

                                {/* 话题输入 */}
                                <div className="bg-white p-6 rounded-lg border border-gray-200">
                                    <h3 className="text-lg font-semibold text-gray-800 mb-4">话题</h3>
                                    <div className="space-y-3">
                                        <div className="flex flex-wrap gap-2">
                                            {currentTabData.selectedTopics.map((topic, index) => (
                                                <div
                                                    key={index}
                                                    className="flex items-center gap-2 px-3 py-1 bg-purple-100 text-purple-700 rounded-full text-sm"
                                                >
                                                    <span>#{topic}</span>
                                                    <button
                                                        onClick={() => removeTopic(currentTabData, index)}
                                                        className="text-purple-700 hover:text-purple-900"
                                                    >
                                                        <X size={14}/>
                                                    </button>
                                                </div>
                                            ))}
                                        </div>
                                        <button
                                            onClick={() => openTopicDialog(currentTabData)}
                                            className="px-4 py-2 text-purple-600 border border-purple-600 rounded-lg hover:bg-purple-50 transition-colors"
                                        >
                                            添加话题
                                        </button>
                                    </div>
                                </div>

                                {/* 定时发布 */}
                                <div className="bg-white p-6 rounded-lg border border-gray-200">
                                    <h3 className="text-lg font-semibold text-gray-800 mb-4">定时发布</h3>
                                    <div className="space-y-4">
                                        <div className="flex items-center gap-3">
                                            <div className="relative">
                                                <input
                                                    type="checkbox"
                                                    id="schedule"
                                                    checked={currentTabData.scheduleEnabled}
                                                    onChange={(e) => updateTab('scheduleEnabled', e.target.checked)}
                                                    className="sr-only"
                                                />
                                                <div
                                                    onClick={() => updateTab('scheduleEnabled', !currentTabData.scheduleEnabled)}
                                                    className={`w-12 h-6 flex items-center rounded-full p-1 cursor-pointer transition-colors ${
                                                        currentTabData.scheduleEnabled ? 'bg-teal-600' : 'bg-gray-300'
                                                    }`}
                                                >
                                                    <div
                                                        className={`bg-white w-4 h-4 rounded-full shadow-md transform transition-transform ${
                                                            currentTabData.scheduleEnabled ? 'translate-x-6' : 'translate-x-0'
                                                        }`}
                                                    />
                                                </div>
                                            </div>
                                            <label htmlFor="schedule" className="text-gray-700">
                                                {currentTabData.scheduleEnabled ? '定时发布' : '立即发布'}
                                            </label>
                                        </div>

                                        {currentTabData.scheduleEnabled && (
                                            <div className="bg-gray-50 p-4 rounded-lg space-y-4">
                                                <div className="flex items-center gap-4">
                                                    <span
                                                        className="text-sm text-gray-700 min-w-[120px]">每天发布视频数：</span>
                                                    <select
                                                        value={currentTabData.videosPerDay}
                                                        onChange={(e) => updateTab('videosPerDay', parseInt(e.target.value))}
                                                        className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500"
                                                    >
                                                        {Array.from({length: 1}, (_, i) => i + 1).map(num => (
                                                            <option key={num} value={num}>{num}</option>
                                                        ))}
                                                    </select>
                                                </div>

                                                <div className="space-y-2">
                                                    <div className="flex items-center gap-4">
                                                        <span
                                                            className="text-sm text-gray-700 min-w-[120px]">每天发布时间：</span>
                                                        <div className="flex-1 space-y-2">
                                                            {currentTabData.dailyTimes.map((time, index) => (
                                                                <div key={index}
                                                                     className="flex items-center gap-2">
                                                                    <input
                                                                        type="time"
                                                                        value={time}
                                                                        onChange={(e) => {
                                                                            const newTimes = [...currentTabData.dailyTimes];
                                                                            newTimes[index] = e.target.value;
                                                                            updateTab('dailyTimes', newTimes);
                                                                        }}
                                                                        className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500"
                                                                    />
                                                                </div>
                                                            ))}
                                                        </div>
                                                    </div>

                                                    {currentTabData.dailyTimes.length < currentTabData.videosPerDay && (
                                                        <button
                                                            onClick={() => addTime(currentTabData)}
                                                            className="px-3 py-1 text-teal-600 border border-teal-600 rounded hover:bg-teal-50 transition-colors text-sm"
                                                        >
                                                            + 添加时间
                                                        </button>
                                                    )}
                                                </div>

                                                <div className="flex items-center gap-4">
                                                    <span
                                                        className="text-sm text-gray-700 min-w-[120px]">开始天数：</span>
                                                    <select
                                                        value={currentTabData.startDays}
                                                        onChange={(e) => updateTab('startDays', parseInt(e.target.value))}
                                                        className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-teal-500"
                                                    >
                                                        <option value={0}>明天</option>
                                                        <option value={1}>后天</option>
                                                    </select>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                </div>

                                {/* 操作按钮 */}
                                <div
                                    className="bg-white p-6 rounded-lg border border-gray-200 flex justify-end gap-3">
                                    <button
                                        onClick={() => confirmPublish(currentTabData)}
                                        disabled={currentTabData.publishing}
                                        className="px-6 py-2 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors disabled:opacity-50"
                                    >
                                        {currentTabData.publishing ? '发布中...' : '发布'}
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* 隐藏的文件输入框 */}
                    <input
                        type="file"
                        ref={fileInputRef}
                        onChange={handleFileSelect}
                        accept="video/*"
                        className="hidden"
                    />
                </div>
            </main>

            {/* 上传选项弹窗 */}
            {uploadOptionsVisible && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg shadow-xl w-[90vw] max-w-md">
                        <div className="flex items-center justify-between p-6 border-b border-gray-200">
                            <h2 className="text-xl font-semibold text-gray-800">选择上传方式</h2>
                            <button
                                onClick={() => setUploadOptionsVisible(false)}
                                className="text-gray-400 hover:text-gray-600 transition-colors"
                            >
                                <X size={24}/>
                            </button>
                        </div>

                        <div className="p-6 space-y-4">
                            <button
                                onClick={selectLocalUpload}
                                className="w-full px-4 py-3 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors flex items-center justify-center gap-2"
                            >
                                <Upload size={20}/>
                                本地上传
                            </button>
                            <button
                                onClick={selectUserWorkLibrary}
                                className="w-full px-4 py-3 text-white bg-green-600 rounded-lg hover:bg-green-700 transition-colors flex items-center justify-center gap-2"
                            >
                                <Folder size={20}/>
                                我的作品库
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* 本地上传弹窗 */}
            {localUploadVisible && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg shadow-xl w-[90vw] max-w-2xl">
                        <div className="flex items-center justify-between p-6 border-b border-gray-200">
                            <h2 className="text-xl font-semibold text-gray-800">本地上传</h2>
                            <button
                                onClick={() => setLocalUploadVisible(false)}
                                className="text-gray-400 hover:text-gray-600 transition-colors"
                            >
                                <X size={24}/>
                            </button>
                        </div>

                        <div className="p-6">
                            <div
                                className="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center hover:border-teal-500 transition-colors">
                                <Upload className="mx-auto text-gray-400 mb-4" size={48}/>
                                <p className="text-gray-600 mb-2">将视频文件拖到此处，或</p>
                                <button
                                    onClick={() => fileInputRef.current?.click()}
                                    className="px-6 py-2 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors"
                                >
                                    点击上传
                                </button>
                                <p className="text-sm text-gray-500 mt-4">
                                    支持MP4、AVI等视频格式，只能上传一个文件
                                </p>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* 我的作品库选择弹窗 */}
            {userWorkLibraryVisible && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div
                        className="bg-white rounded-lg shadow-xl w-[90vw] max-w-3xl max-h-[80vh] flex flex-col">
                        <div className="flex items-center justify-between p-6 border-b border-gray-200">
                            <h2 className="text-xl font-semibold text-gray-800">选择我的作品</h2>
                            <button
                                onClick={() => setUserWorkLibraryVisible(false)}
                                className="text-gray-400 hover:text-gray-600 transition-colors"
                            >
                                <X size={24}/>
                            </button>
                        </div>

                        <div className="p-6 flex-1 overflow-y-auto">
                            {userWorks.length === 0 ? (
                                <div className="text-center py-8">
                                    <Folder className="mx-auto text-gray-400 mb-4" size={48}/>
                                    <p className="text-gray-600">暂无作品</p>
                                </div>
                            ) : (
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    {userWorks.map(work => (
                                        <div
                                            key={work.id}
                                            className={`border rounded-lg p-4 cursor-pointer transition-all ${
                                                selectedUserWork?.id === work.id
                                                    ? 'border-teal-500 bg-teal-50'
                                                    : 'border-gray-200 hover:border-gray-300'
                                            }`}
                                            onClick={() => setSelectedUserWork(work)}
                                        >
                                            <div className="flex items-start gap-3">
                                                <div className="flex-shrink-0">
                                                    {work.coverUrl ? (
                                                        <img
                                                            src={work.coverUrl}
                                                            alt={work.name}
                                                            className="w-16 h-16 object-cover rounded"
                                                        />
                                                    ) : (
                                                        <div
                                                            className="w-16 h-16 bg-gray-100 rounded flex items-center justify-center">
                                                            <Video className="text-gray-400" size={24}/>
                                                        </div>
                                                    )}
                                                </div>
                                                <div className="flex-1 min-w-0">
                                                    <h4 className="font-medium text-gray-800 truncate">
                                                        {work.name || work.title || `作品${work.digitalWorkId}`}
                                                    </h4>
                                                    <div className="text-sm text-gray-600 mt-1 space-y-1">
                                                        {work.duration && (
                                                            <div>时长: {work.duration}</div>
                                                        )}
                                                        <div>状态: {work.status}</div>
                                                        <div>创建时间: {new Date(work.createTime).toLocaleDateString()}</div>
                                                    </div>
                                                    {!work.videoUrl && (
                                                        <div className="mt-2 text-xs text-red-500">
                                                            提示：该作品暂无视频文件
                                                        </div>
                                                    )}
                                                </div>
                                                {selectedUserWork?.id === work.id && (
                                                    <div className="flex-shrink-0">
                                                        <Check className="text-teal-500" size={20}/>
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>

                        <div className="flex items-center justify-end gap-3 p-6 border-t border-gray-200">
                            <button
                                onClick={() => setUserWorkLibraryVisible(false)}
                                className="px-6 py-2 text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                            >
                                取消
                            </button>
                            <button
                                onClick={confirmUserWorkSelection}
                                disabled={!selectedUserWork}
                                className="px-6 py-2 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors disabled:opacity-50"
                            >
                                确定
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* 账号选择弹窗（单选） */}
            {accountDialogVisible && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div
                        className="bg-white rounded-lg shadow-xl w-[90vw] max-w-2xl max-h-[80vh] flex flex-col">
                        <div className="flex items-center justify-between p-6 border-b border-gray-200">
                            <h2 className="text-xl font-semibold text-gray-800">选择账号（单选）</h2>
                            <button
                                onClick={() => setAccountDialogVisible(false)}
                                className="text-gray-400 hover:text-gray-600 transition-colors"
                            >
                                <X size={24}/>
                            </button>
                        </div>

                        <div className="p-6 flex-1 overflow-y-auto">
                            <div className="space-y-2">
                                {accounts
                                    .filter(account => account.type === currentTab?.selectedPlatform)
                                    .map(account => (
                                        <div
                                            key={account.id}
                                            className={`flex items-center p-3 border rounded-lg cursor-pointer transition-all ${
                                                tempSelectedAccount === account.id.toString()
                                                    ? 'border-teal-500 bg-teal-50'
                                                    : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
                                            }`}
                                            onClick={() => setTempSelectedAccount(account.id.toString())}
                                        >
                                            <div className="flex items-center">
                                                <div
                                                    className={`w-5 h-5 rounded-full border flex items-center justify-center mr-3 ${
                                                        tempSelectedAccount === account.id.toString()
                                                            ? 'border-teal-500 bg-teal-500'
                                                            : 'border-gray-300'
                                                    }`}>
                                                    {tempSelectedAccount === account.id.toString() && (
                                                        <Check size={12} className="text-white"/>
                                                    )}
                                                </div>
                                                <div className="flex-1">
                                                    <div className="flex items-center justify-between">
                                                        <div className="font-medium">{account.userName}</div>
                                                        <span className={`text-xs px-2 py-1 rounded ${
                                                            account.status === 1 ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                                                        }`}>
                                                            {account.status === 1 ? '正常' : '异常'}
                                                        </span>
                                                    </div>
                                                    <div className="text-sm text-gray-500 mt-1">
                                                        平台: {platforms.find(p => p.key === account.type)?.name || '未知'}
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                            </div>
                        </div>

                        <div className="flex items-center justify-end gap-3 p-6 border-t border-gray-200">
                            <button
                                onClick={() => {
                                    setAccountDialogVisible(false);
                                    setTempSelectedAccount(null);
                                }}
                                className="px-6 py-2 text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                            >
                                取消
                            </button>
                            <button
                                onClick={confirmAccountSelection}
                                className="px-6 py-2 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors"
                            >
                                确定
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* 话题弹窗 */}
            {topicDialogVisible && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div
                        className="bg-white rounded-lg shadow-xl w-[90vw] max-w-2xl max-h-[80vh] flex flex-col">
                        <div className="flex items-center justify-between p-6 border-b border-gray-200">
                            <h2 className="text-xl font-semibold text-gray-800">添加话题</h2>
                            <button
                                onClick={() => setTopicDialogVisible(false)}
                                className="text-gray-400 hover:text-gray-600 transition-colors"
                            >
                                <X size={24}/>
                            </button>
                        </div>

                        <div className="p-6 flex-1 overflow-y-auto space-y-6">
                            {/* 自定义话题输入 */}
                            <div className="flex gap-2">
                                <div className="flex-1">
                                    <div className="flex">
                    <span
                        className="px-3 py-2 bg-gray-100 border border-r-0 border-gray-300 rounded-l-lg text-gray-700">
                      #
                    </span>
                                        <input
                                            type="text"
                                            value={customTopic}
                                            onChange={(e) => setCustomTopic(e.target.value)}
                                            placeholder="输入自定义话题"
                                            className="flex-1 px-3 py-2 border border-gray-300 rounded-r-lg focus:outline-none focus:ring-2 focus:ring-teal-500"
                                        />
                                    </div>
                                </div>
                                <button
                                    onClick={addCustomTopic}
                                    className="px-4 py-2 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors"
                                >
                                    添加
                                </button>
                            </div>

                            {/* 推荐话题 */}
                            <div>
                                <h4 className="text-lg font-medium text-gray-800 mb-3">推荐话题</h4>
                                <div className="grid grid-cols-3 md:grid-cols-6 gap-2">
                                    {recommendedTopics.map(topic => (
                                        <button
                                            key={topic}
                                            onClick={() => toggleRecommendedTopic(topic)}
                                            className={`px-3 py-2 rounded-lg border transition-colors ${
                                                currentTab?.selectedTopics?.includes(topic)
                                                    ? 'bg-teal-600 text-white border-teal-600'
                                                    : 'bg-gray-50 text-gray-700 border-gray-300 hover:bg-gray-100'
                                            }`}
                                        >
                                            {topic}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        </div>

                        <div className="flex items-center justify-end gap-3 p-6 border-t border-gray-200">
                            <button
                                onClick={() => setTopicDialogVisible(false)}
                                className="px-6 py-2 text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                            >
                                取消
                            </button>
                            <button
                                onClick={confirmTopicSelection}
                                className="px-6 py-2 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors"
                            >
                                确定
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* 批量发布进度对话框 */}
            {batchPublishDialogVisible && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg shadow-xl w-[90vw] max-w-2xl">
                        <div className="flex items-center justify-between p-6 border-b border-gray-200">
                            <h2 className="text-xl font-semibold text-gray-800">批量发布进度</h2>
                            <button
                                onClick={() => setBatchPublishDialogVisible(false)}
                                disabled={publishProgress !== 100}
                                className="text-gray-400 hover:text-gray-600 transition-colors disabled:opacity-50"
                            >
                                <X size={24}/>
                            </button>
                        </div>

                        <div className="p-6 space-y-6">
                            {/* 进度条 */}
                            <div>
                                <div className="w-full bg-gray-200 rounded-full h-2.5">
                                    <div
                                        className="bg-teal-600 h-2.5 rounded-full transition-all duration-300"
                                        style={{width: `${publishProgress}%`}}
                                    />
                                </div>
                                <div className="text-center text-sm text-gray-600 mt-2">
                                    {publishProgress}%
                                </div>
                            </div>

                            {/* 当前发布项 */}
                            {currentPublishingTab && (
                                <div className="text-center text-gray-700">
                                    正在发布：{currentPublishingTab.label}
                                </div>
                            )}

                            {/* 发布结果列表 */}
                            {publishResults.length > 0 && (
                                <div className="border-t pt-4">
                                    <h4 className="font-medium text-gray-800 mb-3">发布结果</h4>
                                    <div className="space-y-2 max-h-60 overflow-y-auto">
                                        {publishResults.map((result, index) => (
                                            <div
                                                key={index}
                                                className={`flex items-center gap-2 p-3 rounded-lg ${
                                                    result.status === 'success'
                                                        ? 'bg-green-50 text-green-700'
                                                        : result.status === 'error'
                                                            ? 'bg-red-50 text-red-700'
                                                            : 'bg-gray-50 text-gray-700'
                                                }`}
                                            >
                                                {result.status === 'success' ? (
                                                    <Check size={18}/>
                                                ) : result.status === 'error' ? (
                                                    <AlertCircle size={18}/>
                                                ) : (
                                                    <AlertCircle size={18}/>
                                                )}
                                                <span className="font-medium">{result.label}</span>
                                                <span className="ml-auto">{result.message}</span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>

                        <div className="flex items-center justify-end gap-3 p-6 border-t border-gray-200">
                            <button
                                onClick={cancelBatchPublish}
                                disabled={publishProgress === 100}
                                className="px-6 py-2 text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors disabled:opacity-50"
                            >
                                取消发布
                            </button>
                            {publishProgress === 100 && (
                                <button
                                    onClick={() => setBatchPublishDialogVisible(false)}
                                    className="px-6 py-2 text-white bg-teal-600 rounded-lg hover:bg-teal-700 transition-colors"
                                >
                                    关闭
                                </button>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default PublishCenter;
