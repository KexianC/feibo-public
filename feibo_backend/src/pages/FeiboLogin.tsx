import React, { useState, useEffect, useRef } from 'react';
import { UserOutlined, LockOutlined, SafetyOutlined } from '@ant-design/icons';
import { useAuth } from '@/hooks/useAuth';
import { useNavigate } from 'react-router-dom';
import { AuthService, getClientFingerprint } from '@/services/auth';

/**
 * 飞驳风格登录页面（完全仿制 https://manage.feibointel.com/web/login/）
 * - 使用相同的布局类名与背景素材，确保视觉一致
 * - 密码及短信登录复用本系统的认证逻辑（useAuth）
 */
const FeiboLogin: React.FC = () => {
  const [formData, setFormData] = useState({ phoneNumber: '', password: '', vercode: '', verificationCode: '' });
  const [loginMethod, setLoginMethod] = useState<'password' | 'sms'>('password');
  const [errors, setErrors] = useState<{[key: string]: string}>({});
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [captchaText, setCaptchaText] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const sendingSmsRef = useRef(false);
  const smsCooldownUntilRef = useRef(0);
  const [isSendingSms, setIsSendingSms] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [smsError, setSmsError] = useState('');
  const [smsSent, setSmsSent] = useState(false);

  const { login, isLoading, error, clearError, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  /** 生成验证码 */
  const generateCaptcha = () => {
    const chars = 'ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678';
    let result = '';
    for (let i = 0; i < 4; i++) result += chars.charAt(Math.floor(Math.random() * chars.length));
    setCaptchaText(result);
    return result;
  };

  /** 绘制验证码 */
  const drawCaptcha = () => {
    const canvas = canvasRef.current; if (!canvas) return;
    const ctx = canvas.getContext('2d'); if (!ctx) return;
    const captcha = generateCaptcha();
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#f0f0f0'; ctx.fillRect(0, 0, canvas.width, canvas.height);
    for (let i = 0; i < 5; i++) { ctx.strokeStyle = `rgb(${Math.floor(Math.random()*255)}, ${Math.floor(Math.random()*255)}, ${Math.floor(Math.random()*255)})`; ctx.beginPath(); ctx.moveTo(Math.random()*canvas.width, Math.random()*canvas.height); ctx.lineTo(Math.random()*canvas.width, Math.random()*canvas.height); ctx.stroke(); }
    ctx.font = '20px Arial'; ctx.textBaseline = 'middle';
    for (let i = 0; i < captcha.length; i++) { ctx.fillStyle = `rgb(${Math.floor(Math.random()*100)}, ${Math.floor(Math.random()*100)}, ${Math.floor(Math.random()*100)})`; const x = 20 + i * 20; const y = canvas.height/2; const angle = (Math.random()-0.5)*0.4; ctx.save(); ctx.translate(x,y); ctx.rotate(angle); ctx.fillText(captcha[i],0,0); ctx.restore(); }
    for (let i = 0; i < 50; i++) { ctx.fillStyle = `rgb(${Math.floor(Math.random()*255)}, ${Math.floor(Math.random()*255)}, ${Math.floor(Math.random()*255)})`; ctx.fillRect(Math.random()*canvas.width, Math.random()*canvas.height, 1, 1); }
  };

  useEffect(() => { if (loginMethod === 'password') drawCaptcha(); }, [loginMethod]);
  useEffect(() => { if (isAuthenticated) navigate('/'); }, [isAuthenticated, navigate]);
  useEffect(() => { if (error) { const t = setTimeout(() => clearError(), 5000); return () => clearTimeout(t); } }, [error, clearError]);
  useEffect(() => { document.title = '飞驳 · 数字医生管理后台'; }, []);
  useEffect(() => {
    if (countdown <= 0) return;
    const timer = window.setTimeout(() => {
      setCountdown(Math.max(0, Math.ceil((smsCooldownUntilRef.current - Date.now()) / 1000)));
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [countdown]);

  const switchLoginMethod = (method: 'password' | 'sms') => {
    if (submittingRef.current || sendingSmsRef.current) return;
    setLoginMethod(method);
    setErrors({});
    setSmsError('');
    clearError();
  };

  /** 输入变更 */
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: name === 'verificationCode' ? value.replace(/\D/g, '').slice(0, 6) : value,
      ...(name === 'phoneNumber' ? { verificationCode: '' } : {}),
    }));
    if (name === 'phoneNumber') {
      setSmsSent(false);
      setSmsError('');
    }
    if (errors[name]) setErrors(prev => ({ ...prev, [name]: '' }));
  };

  /** 表单校验 */
  const validateForm = () => {
    const newErrors: {[key: string]: string} = {};
    if (!formData.phoneNumber.trim()) newErrors.phoneNumber = '请输入您的手机号';
    else if (!/^1[3-9]\d{9}$/.test(formData.phoneNumber.trim())) newErrors.phoneNumber = '请输入正确的手机号格式';
    if (loginMethod === 'sms') {
      if (!/^\d{6}$/.test(formData.verificationCode.trim())) newErrors.verificationCode = '请输入6位短信验证码';
    } else {
      if (!formData.password.trim()) newErrors.password = '请输入您的密码';
      if (!formData.vercode.trim()) newErrors.vercode = '请输入图形验证码';
      else if (formData.vercode.toLowerCase() !== captchaText.toLowerCase()) newErrors.vercode = '图形验证码错误';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSendSms = async () => {
    if (sendingSmsRef.current || submittingRef.current || isLoading || Date.now() < smsCooldownUntilRef.current) return;
    const phoneNumber = formData.phoneNumber.trim();
    if (!/^1[3-9]\d{9}$/.test(phoneNumber)) {
      setErrors(prev => ({ ...prev, phoneNumber: '请输入正确的手机号格式' }));
      return;
    }
    sendingSmsRef.current = true;
    setIsSendingSms(true);
    setSmsError('');
    setSmsSent(false);
    clearError();
    try {
      await AuthService.sendLoginCode(phoneNumber);
      smsCooldownUntilRef.current = Date.now() + 60_000;
      setCountdown(60);
      setSmsSent(true);
    } catch (error) {
      setSmsError(error instanceof Error ? error.message : '发送验证码失败，请稍后重试');
    } finally {
      sendingSmsRef.current = false;
      setIsSendingSms(false);
    }
  };

  /** 提交登录 */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submittingRef.current || sendingSmsRef.current || isLoading) return;
    if (!validateForm()) return;
    submittingRef.current = true;
    setIsSubmitting(true); clearError(); setSmsError('');
    try {
      const identity = { phoneNumber: formData.phoneNumber.trim(), clientFingerprint: getClientFingerprint() };
      const success = await login(loginMethod === 'sms'
        ? { ...identity, verificationCode: formData.verificationCode.trim() }
        : { ...identity, password: formData.password });
      if (success) navigate('/');
      else if (loginMethod === 'password') { drawCaptcha(); setFormData(prev => ({ ...prev, vercode: '' })); }
    } catch {
      if (loginMethod === 'password') { drawCaptcha(); setFormData(prev => ({ ...prev, vercode: '' })); }
    } finally { submittingRef.current = false; setIsSubmitting(false); }
  };

  /** 刷新验证码 */
  const handleCanvasClick = () => { drawCaptcha(); };

  return (
    <div className="layadmin-user-login" style={{ minHeight: '100vh' }}>
      <div className="layadmin-user-login-main">
        <div className="layadmin-user-login-header">
          <h2>飞驳 · 数字医生管理后台</h2>
          <p>Feibo Digital Doctor Management</p>
        </div>
        <div className="layadmin-user-login-body layadmin-user-login-box">
          <div className="login-method-tabs" role="group" aria-label="登录方式">
            <button type="button" aria-pressed={loginMethod === 'password'} disabled={isSubmitting || isSendingSms || isLoading} onClick={() => switchLoginMethod('password')}>密码登录</button>
            <button type="button" aria-pressed={loginMethod === 'sms'} disabled={isSubmitting || isSendingSms || isLoading} onClick={() => switchLoginMethod('sms')}>短信登录</button>
          </div>
          {(error || smsError) && (
            <div role="alert" className="error-banner" style={{ background: '#fff2f0', border: '1px solid #ffccc7', borderRadius: '6px', padding: '8px 12px', marginBottom: '16px', color: '#ff4d4f', fontSize: '14px' }}>{error || smsError}</div>
          )}
          <form onSubmit={handleSubmit}>
            <div className="layui-form-item">
              <div className="input-wrapper">
                <UserOutlined className="layadmin-user-login-icon" />
                <input type="tel" inputMode="numeric" name="phoneNumber" value={formData.phoneNumber} onChange={handleInputChange} placeholder="请输入您的手机号" className={`layui-input ${errors.phoneNumber ? 'error' : ''}`} autoComplete="tel" disabled={isSubmitting || isSendingSms} />
              </div>
              {errors.phoneNumber && <div className="error-message">{errors.phoneNumber}</div>}
            </div>
            {loginMethod === 'password' ? <>
            <div className="layui-form-item">
              <div className="input-wrapper">
                <LockOutlined className="layadmin-user-login-icon" />
                <input type="password" name="password" value={formData.password} onChange={handleInputChange} placeholder="请输入您的密码" className={`layui-input ${errors.password ? 'error' : ''}`} autoComplete="off" />
              </div>
              {errors.password && <div className="error-message">{errors.password}</div>}
            </div>
            <div className="layui-form-item">
              <div className="captcha-row">
                <div className="captcha-input">
                  <div className="input-wrapper">
                    <SafetyOutlined className="layadmin-user-login-icon" />
                    <input type="text" name="vercode" value={formData.vercode} onChange={handleInputChange} placeholder="请输入图形验证码" className={`layui-input ${errors.vercode ? 'error' : ''}`} autoComplete="off" />
                  </div>
                </div>
                <div className="captcha-canvas">
                  <canvas ref={canvasRef} width="120" height="40" onClick={handleCanvasClick} style={{ cursor: 'pointer' }} />
                </div>
              </div>
              {errors.vercode && <div className="error-message">{errors.vercode}</div>}
            </div>
            </> : <div className="layui-form-item">
              <div className="sms-code-row">
                <div className="input-wrapper">
                  <SafetyOutlined className="layadmin-user-login-icon" />
                  <input type="text" inputMode="numeric" name="verificationCode" value={formData.verificationCode} onChange={handleInputChange} placeholder="请输入6位短信验证码" className={`layui-input ${errors.verificationCode ? 'error' : ''}`} autoComplete="one-time-code" maxLength={6} disabled={isSubmitting} />
                </div>
                <button type="button" className="sms-send-button" onClick={handleSendSms} disabled={isSendingSms || isSubmitting || isLoading || countdown > 0}>{isSendingSms ? '发送中...' : countdown > 0 ? `${countdown}秒后重发` : '发送验证码'}</button>
              </div>
              {errors.verificationCode && <div className="error-message">{errors.verificationCode}</div>}
              {smsSent && <div className="sms-sent-message" role="status">验证码已发送，请查看短信</div>}
            </div>}
            <div className="layui-form-item">
              <button type="submit" className="layui-btn layui-btn-fluid" disabled={isSubmitting || isLoading || isSendingSms}>{isSubmitting || isLoading ? '登录中...' : '登录'}</button>
            </div>
          </form>
        </div>
      </div>
      <div className="layadmin-user-login-footer"><p>飞驳科技（杭州）有限公司<span>|</span>增值电信业务经营许可证 浙ICP备2024064837号-1</p></div>
      <style>{`
        body, html { height: 100%; }
        .layadmin-user-login { display: flex; align-items: center; justify-content: center; overflow: auto; background: url('/images/login/bg-d9b478f4.png') center center / cover no-repeat; min-height: 100vh; position: relative; }
        .layadmin-user-login-main { width: 400px; margin: 0 auto; background: #fff; box-shadow: 0 0 5px rgba(0,0,0,0.05); border-radius: 8px; z-index: 10; position: relative; }
        .layadmin-user-login-main::before { content: ""; position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); width: 150%; height: 150%; background: radial-gradient(ellipse, rgba(28,161,150,0.10), transparent 68%); z-index: -1; pointer-events: none; }
        .layadmin-user-login-main::after { content: ""; position: absolute; right: 20px; top: -60px; width: 80px; height: 80px; background: url('/images/login/logo-033e4277.jpg') center center / contain no-repeat; z-index: -1; pointer-events: none; }
        .layadmin-user-login-header { text-align: center; padding-top: 30px; }
        .layadmin-user-login-header h2 { margin-bottom: 10px; font-weight: 300; font-size: 30px; color: #000; }
        .layadmin-user-login-header p { font-weight: 300; color: #999; }
        .layadmin-user-login-box { padding: 20px; }
        .login-method-tabs { display: flex; gap: 12px; margin-bottom: 20px; border-bottom: 1px solid #e6e6e6; }
        .login-method-tabs button { flex: 1; padding: 10px 0; background: transparent; border: none; border-bottom: 2px solid transparent; color: #666; cursor: pointer; font-size: 15px; }
        .login-method-tabs button[aria-pressed="true"] { color: #1ca196; border-bottom-color: #1ca196; }
        .layadmin-user-login button:disabled { cursor: not-allowed; opacity: 0.6; }
        .sms-code-row { display: flex; gap: 8px; }
        .sms-code-row .input-wrapper { flex: 1; min-width: 0; }
        .sms-send-button { flex-shrink: 0; height: 38px; padding: 0 10px; color: #1ca196; background: #fff; border: 1px solid #1ca196; border-radius: 4px; font-size: 12px; }
        .sms-sent-message { margin-top: 8px; color: #16867d; font-size: 12px; }
        .layui-form-item { position: relative; margin-bottom: 16px; }
        .input-wrapper { position: relative; display: flex; align-items: center; }
        .layadmin-user-login-icon { position: absolute; left: 1px; top: 1px; width: 38px; line-height: 36px; text-align: center; color: #d2d2d2; z-index: 1; }
        .layui-input { width: 100%; height: 38px; padding-left: 38px; border: 1px solid #e6e6e6; border-radius: 4px; font-size: 14px; }
        .layui-input.error { border-color: #f56c6c; }
        .captcha-row { display: flex; gap: 10px; align-items: flex-start; }
        .captcha-input { flex: 1; }
        .captcha-canvas canvas { max-height: 38px; width: 120px; border: 1px solid #e6e6e6; border-radius: 4px; background: #f0f0f0; }
        .error-message { color: #f56c6c; font-size: 12px; margin-top: 5px; margin-left: 5px; }
        .layui-btn.layui-btn-fluid { width: 100%; height: 40px; background-color: #1ca196; border: none; border-radius: 4px; color: #fff; font-size: 16px; }
        .layadmin-user-login-footer { position: absolute; left: 0; bottom: 0; width: 100%; line-height: 30px; padding: 20px; text-align: center; box-sizing: border-box; color: rgba(0,0,0,0.5); }
        .layadmin-user-login-footer span { padding: 0 5px; }
        @media screen and (max-width: 768px) { 
          .layadmin-user-login { padding-top: 60px; }
          .layadmin-user-login-main { width: 300px; }
          .layadmin-user-login-box { padding: 10px; }
        }
      `}</style>
    </div>
  );
};

export default FeiboLogin;
