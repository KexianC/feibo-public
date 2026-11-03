import React, { useState } from 'react';
import { loginUser, smsLogin, sendSmsCode, getClientFingerprint, saveUserInfo } from '../services/authApi';
import { getWebSocketService } from '../services/websocketService';
import { API_CONFIG } from '../config/api';
import { User, LoginData } from '../types';

interface LoginProps {
  onLogin: (user: User) => void;
}

const Login: React.FC<LoginProps> = ({ onLogin }) => {
  // 登录表单状态
  const [loginData, setLoginData] = useState<LoginData>({
    phoneNumber: '',
    password: ''
  });
  
  // 验证码登录状态
  const [smsLoginData, setSmsLoginData] = useState({
    phoneNumber: '',
    smsCode: ''
  });
  
  // 验证码相关状态
  const [countdown, setCountdown] = useState(0);
  const [isSendingSms, setIsSendingSms] = useState(false);
  
  const [activeTab, setActiveTab] = useState<'password' | 'sms'>('sms');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  // 统一输入框样式
  const inputStyle = {
    '--n-bezier': 'cubic-bezier(.4, 0, .2, 1)',
    '--n-count-text-color': 'rgb(118, 124, 130)',
    '--n-count-text-color-disabled': 'rgba(194, 194, 194, 1)',
    '--n-color': 'rgba(255, 255, 255, 1)',
    '--n-font-size': '14px',
    '--n-border-radius': '3px',
    '--n-height': '28px',
    '--n-padding-left': '10px',
    '--n-padding-right': '10px',
    '--n-text-color': 'rgb(51, 54, 57)',
    '--n-caret-color': '#8caba8',
    '--n-text-decoration-color': 'rgb(51, 54, 57)',
    '--n-border': '1px solid rgb(224, 224, 230)',
    '--n-border-disabled': '1px solid rgb(224, 224, 230)',
    '--n-border-hover': '1px solid #36ad6a',
    '--n-border-focus': '1px solid #36ad6a',
    '--n-placeholder-color': 'rgba(194, 194, 194, 1)',
    '--n-placeholder-color-disabled': 'rgba(209, 209, 209, 1)',
    '--n-icon-size': '16px',
    '--n-line-height-textarea': '1.6',
    '--n-color-disabled': 'rgb(250, 250, 252)',
    '--n-color-focus': 'rgba(255, 255, 255, 1)',
    '--n-text-color-disabled': 'rgba(194, 194, 194, 1)',
    '--n-box-shadow-focus': '0 0 0 2px rgba(140, 171, 168, 0.2)',
    '--n-loading-color': '#8caba8'
  } as React.CSSProperties;

  // 验证码倒计时效果
  React.useEffect(() => {
    let timer: NodeJS.Timeout;
    if (countdown > 0) {
      timer = setTimeout(() => setCountdown(countdown - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [countdown]);

  // 发送验证码
  const handleSendSms = async () => {
    if (!smsLoginData.phoneNumber.trim()) {
      setError('请输入手机号');
      return;
    }
    
    const phoneRegex = /^1[3-9]\d{9}$/;
    if (!phoneRegex.test(smsLoginData.phoneNumber)) {
      setError('请输入正确的手机号码');
      return;
    }
    
    setIsSendingSms(true);
    setError('');
    
    try {
      const clientFingerprint = getClientFingerprint();
      await sendSmsCode({
        phoneNumber: smsLoginData.phoneNumber,
        clientFingerprint
      });
      
      setCountdown(60); // 60秒倒计时
      setError('');
    } catch (err) {
      setError(err instanceof Error ? err.message : '发送验证码失败，请重试');
    } finally {
      setIsSendingSms(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');
    
    try {
      const clientFingerprint = getClientFingerprint();
      let response;
      
      if (activeTab === 'password') {
        // 密码登录逻辑
        if (!loginData.phoneNumber.trim() || !loginData.password.trim()) {
          setError('请输入手机号和密码');
          return;
        }
        
        response = await loginUser({
          ...loginData,
          clientFingerprint
        });
      } else {
        // 验证码登录逻辑
        if (!smsLoginData.phoneNumber.trim() || !smsLoginData.smsCode.trim()) {
          setError('请输入手机号和验证码');
          return;
        }
        
        const phoneRegex = /^1[3-9]\d{9}$/;
        if (!phoneRegex.test(smsLoginData.phoneNumber)) {
          setError('请输入正确的手机号码');
          return;
        }
        

        
        response = await smsLogin({
          phoneNumber: smsLoginData.phoneNumber,
          verificationCode: smsLoginData.smsCode, // 将smsCode映射到verificationCode字段
          clientFingerprint
        });
      }
      
      // 构造用户对象
      const user: User = {
        userId: String(response.userId),
        nickName: response.nickName,
        avatar: response.avatar,
        phoneNumber: response.phoneNumber,
        gender: response.gender,
        roles: response.roles,
        loginTime: new Date(),
        username: response.nickName // 兼容旧字段
      };
      
      // 保存用户信息
      saveUserInfo(response);
      
      // 初始化并连接WebSocket
      try {
        const wsService = getWebSocketService({
          url: API_CONFIG.WS_URL,
          heartbeatInterval: 30000,
          reconnectInterval: 5000,
          maxReconnectAttempts: 5,
          debug: false
        });
        
        // 连接WebSocket
        await wsService.connect();
        
        // 订阅连接状态变化
        wsService.onStateChange((state) => {
          void state;
        });
        
      } catch (wsError) {
        console.warn('WebSocket连接失败，但不影响登录');
      }
      
      onLogin(user);
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败，请重试');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div 
      className="w-screen h-screen bg-no-repeat bg-center bg-cover flex"
      style={{
        backgroundImage: 'url("/img/login_bg.png")'
      }}
    >
      <div 
        className="m-auto w-4/5 h-4/5 rounded-lg bg-no-repeat bg-center bg-contain flex relative"
        style={{
          backgroundImage: 'url("/img/loginbox_1.png")'
        }}
      >

        {/* 登录卡片 */}
        <div className="m-auto rounded-2xl bg-white w-[300px] min-h-[200px] shadow-2xl pt-4 px-8 pb-8">
          <div className="text-lg text-center mb-4">用户登录</div>
          
          {/* n-tabs 样式系统 */}
          <div 
            className="n-tabs n-tabs--bar-type n-tabs--small-size n-tabs--top card-tab"
            style={{
              '--n-bezier': 'cubic-bezier(.4, 0, .2, 1)',
              '--n-color-segment': 'rgb(247, 247, 250)',
              '--n-bar-color': '#8caba8',
              '--n-tab-font-size': '14px',
              '--n-tab-text-color': 'rgb(31, 34, 37)',
              '--n-tab-text-color-active': '#8caba8',
              '--n-tab-text-color-disabled': 'rgba(194, 194, 194, 1)',
              '--n-tab-text-color-hover': '#8caba8',
              '--n-pane-text-color': 'rgb(51, 54, 57)',
              '--n-tab-border-color': 'rgb(239, 239, 245)',
              '--n-tab-border-radius': '3px',
              '--n-close-size': '18px',
              '--n-close-icon-size': '14px',
              '--n-close-color-hover': 'rgba(0, 0, 0, .09)',
              '--n-close-color-pressed': 'rgba(0, 0, 0, .13)',
              '--n-close-border-radius': '3px',
              '--n-close-icon-color': 'rgba(102, 102, 102, 1)',
              '--n-close-icon-color-hover': 'rgba(102, 102, 102, 1)',
              '--n-close-icon-color-pressed': 'rgba(102, 102, 102, 1)',
              '--n-tab-color': 'rgb(247, 247, 250)',
              '--n-tab-font-weight': '400',
              '--n-tab-font-weight-active': '400',
              '--n-tab-padding': '4px 0',
              '--n-tab-padding-vertical': '0 4px',
              '--n-tab-gap': '36px',
              '--n-pane-padding': '8px 0 0 0',
              '--n-font-weight-strong': '500',
              '--n-tab-color-segment': '#FFF'
            } as React.CSSProperties}
          >
            <div className="n-tabs-nav--bar-type n-tabs-nav--top n-tabs-nav">
              <div className="n-tabs-nav-scroll-wrapper">
                <div className="v-x-scroll">
                  <div className="n-tabs-nav-scroll-content">
                    <div className="n-tabs-wrapper">
                      <div className="n-tabs-scroll-padding" style={{ width: '0px' }}></div>
                      <div className="n-tabs-tab-wrapper">
                        <div 
                          data-name="sms" 
                          className={`n-tabs-tab ${activeTab === 'sms' ? 'n-tabs-tab--active' : ''}`}
                          onClick={() => setActiveTab('sms')}
                          style={{ cursor: 'pointer' }}
                        >
                          <span className="n-tabs-tab__label">验证码登录</span>
                        </div>
                      </div>
                      <div className="n-tabs-tab-wrapper">
                        <div className="n-tabs-tab-pad"></div>
                        <div 
                          data-name="password" 
                          className={`n-tabs-tab ${activeTab === 'password' ? 'n-tabs-tab--active' : ''}`}
                          onClick={() => setActiveTab('password')}
                          style={{ cursor: 'pointer' }}
                        >
                          <span className="n-tabs-tab__label">密码登录</span>
                        </div>
                      </div>
                      <div className="n-tabs-scroll-padding" style={{ width: '0px' }}></div>
                    </div>
                    <div 
                      className="n-tabs-bar" 
                      style={{
                        left: activeTab === 'sms' ? '0px' : '50%',
                        maxWidth: '80px',
                        width: '8192px',
                        transition: 'left 0.3s ease'
                      }}
                    ></div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* n-tabs-pane-wrapper */}
          <div className="n-tabs-pane-wrapper">
            <div className="n-tab-pane text-[#418a82]">
              <form onSubmit={handleSubmit}>
                <div className="space-y-4 py-6">
                  {activeTab === 'sms' ? (
                    <>
                      {/* 手机号输入 */}
                      <div className="n-input n-input--resizable n-input--round n-input--stateful" style={inputStyle}>
                        <div className="n-input-wrapper">
                          <div className="n-input__input">
                            <input 
                              type="tel" 
                              className="n-input__input-el" 
                              placeholder="请输入手机号" 
                              size={20}
                              value={smsLoginData.phoneNumber}
                              onChange={(e) => setSmsLoginData({...smsLoginData, phoneNumber: e.target.value})}
                            />
                            <div className="n-input__placeholder">
                              <span>请输入手机号</span>
                            </div>
                          </div>
                        </div>
                        <div className="n-input__border"></div>
                        <div className="n-input__state-border"></div>
                      </div>

                      {/* 验证码输入和发送按钮 */}
                      <div className="flex space-x-2">
                        <div className="flex-1 n-input n-input--resizable n-input--round n-input--stateful" style={inputStyle}>
                          <div className="n-input-wrapper">
                            <div className="n-input__input">
                              <input 
                                type="text" 
                                className="n-input__input-el" 
                                placeholder="请输入验证码" 
                                size={20}

                                value={smsLoginData.smsCode}
                                onChange={(e) => setSmsLoginData({...smsLoginData, smsCode: e.target.value.replace(/\D/g, '')})}
                              />
                              <div className="n-input__placeholder">
                                <span>请输入验证码</span>
                              </div>
                            </div>
                          </div>
                          <div className="n-input__border"></div>
                          <div className="n-input__state-border"></div>
                        </div>
                        
                        {/* 发送验证码按钮 */}
                        <button
                          type="button"
                          onClick={handleSendSms}
                          disabled={countdown > 0 || isSendingSms}
                          className="px-3 py-1 text-sm bg-teal-600 hover:bg-teal-700 disabled:bg-gray-400 text-white rounded transition-colors whitespace-nowrap"
                        >
                          {countdown > 0 ? `${countdown}s` : isSendingSms ? '发送中...' : '获取验证码'}
                        </button>
                      </div>
                    </>
                  ) : (
                    <>
                      {/* 手机号输入 */}
                      <div className="n-input n-input--resizable n-input--round n-input--stateful" style={inputStyle}>
                        <div className="n-input-wrapper">
                          <div className="n-input__input">
                            <input 
                              type="tel" 
                              className="n-input__input-el" 
                              placeholder="请输入手机号" 
                              size={20}
                              value={loginData.phoneNumber}
                              onChange={(e) => setLoginData({...loginData, phoneNumber: e.target.value})}
                            />
                            <div className="n-input__placeholder">
                              <span>请输入手机号</span>
                            </div>
                          </div>
                        </div>
                        <div className="n-input__border"></div>
                        <div className="n-input__state-border"></div>
                      </div>

                      {/* 密码输入 */}
                      <div className="n-input n-input--resizable n-input--round n-input--stateful" style={inputStyle}>
                        <div className="n-input-wrapper">
                          <div className="n-input__input">
                            <input 
                              type="password" 
                              className="n-input__input-el" 
                              placeholder="请输入密码" 
                              size={20}
                              value={loginData.password}
                              onChange={(e) => setLoginData({...loginData, password: e.target.value})}
                            />
                            <div className="n-input__placeholder">
                              <span>请输入密码</span>
                            </div>
                          </div>
                        </div>
                        <div className="n-input__border"></div>
                        <div className="n-input__state-border"></div>
                      </div>
                    </>
                  )}
                </div>

                {/* 错误信息 */}
                {error && (
                  <div className="mb-4 text-sm text-red-600 text-center">
                    {error}
                  </div>
                )}

                {/* n-button 样式系统 */}
                <button
                  type="submit"
                  disabled={isLoading}
                  className="n-button n-button--primary-type n-button--small-type n-button--block"
                  tabIndex={0}
                  style={{
                    '--n-bezier': 'cubic-bezier(.4, 0, .2, 1)',
                    '--n-bezier-ease-out': 'cubic-bezier(0, 0, .2, 1)',
                    '--n-ripple-duration': '.6s',
                    '--n-opacity-disabled': '0.5',
                    '--n-wave-opacity': '0.6',
                    fontWeight: '400',
                    '--n-color': '#8caba8',
                    '--n-color-hover': '#36ad6a',
                    '--n-color-pressed': '#0c7a43',
                    '--n-color-focus': '#36ad6a',
                    '--n-color-disabled': '#8caba8',
                    '--n-ripple-color': '#8caba8',
                    '--n-text-color': '#FFF',
                    '--n-text-color-hover': '#FFF',
                    '--n-text-color-pressed': '#FFF',
                    '--n-text-color-focus': '#FFF',
                    '--n-text-color-disabled': '#FFF',
                    '--n-border': '1px solid #8caba8',
                    '--n-border-hover': '1px solid #36ad6a',
                    '--n-border-pressed': '1px solid #0c7a43',
                    '--n-border-focus': '1px solid #36ad6a',
                    '--n-border-disabled': '1px solid #8caba8',
                    '--n-width': 'initial',
                    '--n-height': '28px',
                    '--n-font-size': '14px',
                    '--n-padding': '0 14px',
                    '--n-icon-size': '18px',
                    '--n-icon-margin': '6px',
                    '--n-border-radius': '28px'
                  } as React.CSSProperties}
                >
                  <span className="n-button__content">
                    {isLoading ? (activeTab === 'sms' ? '验证码登录中...' : '密码登录中...') : '登录'}
                  </span>
                  <div aria-hidden="true" className="n-base-wave"></div>
                  <div aria-hidden="true" className="n-button__border"></div>
                  <div aria-hidden="true" className="n-button__state-border"></div>
                </button>
              </form>
            </div>
          </div>
        </div>
      </div>

      {/* 底部版权信息 */}
      <div className="fixed bottom-[15px] inset-x-0 m-[auto]">
        <p className="text-center" style={{ color: 'rgb(0, 0, 0)' }}>飞驳科技（杭州）有限公司</p>
        <p className="text-center" style={{ color: 'rgb(0, 0, 0)' }}>增值电信业务经营许可证 浙ICP备2024064837号-1</p>
      </div>
    </div>
  );
};

export default Login;
