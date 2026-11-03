import React, { useState, useEffect, useRef } from 'react';
import { UserOutlined, LockOutlined, SafetyOutlined } from '@ant-design/icons';
import { useAuth } from '../hooks/useAuth';
import { useNavigate } from 'react-router-dom';
import { getClientFingerprint } from '../services/auth';

const Login: React.FC = () => {
  const [formData, setFormData] = useState({
    phoneNumber: '',
    password: '',
    vercode: ''
  });
  const [errors, setErrors] = useState<{[key: string]: string}>({});
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [captchaText, setCaptchaText] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  const { login, isLoading, error, clearError, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  // 生成验证码
  const generateCaptcha = () => {
    const chars = 'ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678';
    let result = '';
    for (let i = 0; i < 4; i++) {
      result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    setCaptchaText(result);
    return result;
  };

  // 绘制验证码
  const drawCaptcha = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const captcha = generateCaptcha();
    
    // 清空画布
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // 设置背景
    ctx.fillStyle = '#f0f0f0';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    // 绘制干扰线
    for (let i = 0; i < 5; i++) {
      ctx.strokeStyle = `rgb(${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)})`;
      ctx.beginPath();
      ctx.moveTo(Math.random() * canvas.width, Math.random() * canvas.height);
      ctx.lineTo(Math.random() * canvas.width, Math.random() * canvas.height);
      ctx.stroke();
    }
    
    // 绘制验证码文字
    ctx.font = '20px Arial';
    ctx.textBaseline = 'middle';
    
    for (let i = 0; i < captcha.length; i++) {
      ctx.fillStyle = `rgb(${Math.floor(Math.random() * 100)}, ${Math.floor(Math.random() * 100)}, ${Math.floor(Math.random() * 100)})`;
      const x = 20 + i * 20;
      const y = canvas.height / 2;
      const angle = (Math.random() - 0.5) * 0.4;
      
      ctx.save();
      ctx.translate(x, y);
      ctx.rotate(angle);
      ctx.fillText(captcha[i], 0, 0);
      ctx.restore();
    }
    
    // 绘制干扰点
    for (let i = 0; i < 50; i++) {
      ctx.fillStyle = `rgb(${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)}, ${Math.floor(Math.random() * 255)})`;
      ctx.fillRect(Math.random() * canvas.width, Math.random() * canvas.height, 1, 1);
    }
  };

  useEffect(() => {
    drawCaptcha();
  }, []);

  // 如果已经登录，重定向到首页
  useEffect(() => {
    if (isAuthenticated) {
      navigate('/');
    }
  }, [isAuthenticated, navigate]);

  // 清除错误信息
  useEffect(() => {
    if (error) {
      const timer = setTimeout(() => {
        clearError();
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [error, clearError]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
    
    // 清除对应字段的错误
    if (errors[name]) {
      setErrors(prev => ({
        ...prev,
        [name]: ''
      }));
    }
  };

  const validateForm = () => {
    const newErrors: {[key: string]: string} = {};
    
    if (!formData.phoneNumber.trim()) {
      newErrors.phoneNumber = '请输入您的手机号';
    } else if (!/^1[3-9]\d{9}$/.test(formData.phoneNumber.trim())) {
      newErrors.phoneNumber = '请输入正确的手机号格式';
    }
    
    if (!formData.password.trim()) {
      newErrors.password = '请输入您的密码';
    }
    
    if (!formData.vercode.trim()) {
      newErrors.vercode = '请输入验证码';
    } else if (formData.vercode.toLowerCase() !== captchaText.toLowerCase()) {
      newErrors.vercode = '验证码错误';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!validateForm()) {
      return;
    }

    setIsSubmitting(true);
    clearError();

    try {
      const success = await login({
        phoneNumber: formData.phoneNumber,
        password: formData.password,
        clientFingerprint: getClientFingerprint()
      });

      if (success) {
        // 登录成功，导航到首页
        navigate('/');
      } else {
        // 登录失败，重新生成验证码
        drawCaptcha();
        setFormData(prev => ({ ...prev, vercode: '' }));
      }
    } catch (err) {
      console.error('Login error:', err);
      // 重新生成验证码
      drawCaptcha();
      setFormData(prev => ({ ...prev, vercode: '' }));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCanvasClick = () => {
    drawCaptcha();
  };

  return (
    <div className="login-container">
      <div className="login-main">
        <div className="login-header">
          <h2>数字医生管理后台</h2>
        </div>
        <div className="login-body">
          {error && (
            <div className="error-banner" style={{
              background: '#fff2f0',
              border: '1px solid #ffccc7',
              borderRadius: '6px',
              padding: '8px 12px',
              marginBottom: '16px',
              color: '#ff4d4f',
              fontSize: '14px'
            }}>
              {error}
            </div>
          )}
          <form onSubmit={handleSubmit}>
            <div className="form-item">
              <div className="input-wrapper">
                <UserOutlined className="input-icon" />
                <input
                  type="text"
                  name="phoneNumber"
                  value={formData.phoneNumber}
                  onChange={handleInputChange}
                  placeholder="请输入您的手机号"
                  className={`form-input ${errors.phoneNumber ? 'error' : ''}`}
                  autoComplete="off"
                />
              </div>
              {errors.phoneNumber && <div className="error-message">{errors.phoneNumber}</div>}
            </div>
            
            <div className="form-item">
              <div className="input-wrapper">
                <LockOutlined className="input-icon" />
                <input
                  type="password"
                  name="password"
                  value={formData.password}
                  onChange={handleInputChange}
                  placeholder="请输入您的密码"
                  className={`form-input ${errors.password ? 'error' : ''}`}
                  autoComplete="off"
                />
              </div>
              {errors.password && <div className="error-message">{errors.password}</div>}
            </div>
            
            <div className="form-item">
              <div className="captcha-row">
                <div className="captcha-input">
                  <div className="input-wrapper">
                    <SafetyOutlined className="input-icon" />
                    <input
                      type="text"
                      name="vercode"
                      value={formData.vercode}
                      onChange={handleInputChange}
                      placeholder="请输入验证码"
                      className={`form-input ${errors.vercode ? 'error' : ''}`}
                      autoComplete="off"
                    />
                  </div>
                </div>
                <div className="captcha-canvas">
                  <canvas
                    ref={canvasRef}
                    width="120"
                    height="40"
                    onClick={handleCanvasClick}
                    style={{ cursor: 'pointer' }}
                  />
                </div>
              </div>
              {errors.vercode && <div className="error-message">{errors.vercode}</div>}
            </div>
            
            <div className="form-item">
              <button 
                type="submit" 
                className="login-button"
                disabled={isSubmitting || isLoading}
              >
                {isSubmitting || isLoading ? '登录中...' : 'Login'}
              </button>
            </div>
          </form>
        </div>
      </div>
      
      <div className="login-footer">
        <p>飞驳科技（杭州）有限公司</p>
        <p>增值电信业务经营许可证 浙ICP备2024064837号-1</p>
      </div>

      <style>{`
        .login-container {
          min-height: 100vh;
          background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
          display: flex;
          flex-direction: column;
          justify-content: center;
          align-items: center;
          padding: 20px;
          position: relative;
        }

        .login-main {
          background: rgba(0, 0, 0, 0.3);
          border-radius: 8px;
          padding: 40px;
          width: 100%;
          max-width: 400px;
          box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
          backdrop-filter: blur(10px);
          border: 1px solid rgba(255, 255, 255, 0.1);
        }

        .login-header {
          text-align: center;
          margin-bottom: 30px;
        }

        .login-header h2 {
          color: #fff;
          font-size: 24px;
          font-weight: 500;
          margin: 0;
          letter-spacing: 1px;
        }

        .login-body {
          width: 100%;
        }

        .form-item {
          margin-bottom: 20px;
        }

        .input-wrapper {
          position: relative;
          display: flex;
          align-items: center;
        }

        .input-icon {
          position: absolute;
          left: 12px;
          color: #999;
          font-size: 16px;
          z-index: 1;
        }

        .form-input {
          width: 100%;
          height: 44px;
          padding: 0 12px 0 40px;
          border: 1px solid rgba(255, 255, 255, 0.2);
          border-radius: 4px;
          background: rgba(255, 255, 255, 0.1);
          color: #fff;
          font-size: 14px;
          transition: all 0.3s ease;
          outline: none;
        }

        .form-input::placeholder {
          color: rgba(255, 255, 255, 0.6);
        }

        .form-input:focus {
          border-color: #409eff;
          background: rgba(255, 255, 255, 0.15);
          box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
        }

        .form-input.error {
          border-color: #f56c6c;
        }

        .error-message {
          color: #f56c6c;
          font-size: 12px;
          margin-top: 5px;
          margin-left: 5px;
        }

        .captcha-row {
          display: flex;
          gap: 10px;
          align-items: flex-start;
        }

        .captcha-input {
          flex: 1;
        }

        .captcha-canvas {
          flex-shrink: 0;
        }

        .captcha-canvas canvas {
          border: 1px solid rgba(255, 255, 255, 0.2);
          border-radius: 4px;
          background: #f0f0f0;
        }

        .login-button {
          width: 100%;
          height: 44px;
          background: linear-gradient(135deg, #409eff 0%, #36a3f7 100%);
          border: none;
          border-radius: 4px;
          color: #fff;
          font-size: 16px;
          font-weight: 500;
          cursor: pointer;
          transition: all 0.3s ease;
          outline: none;
        }

        .login-button:hover {
          background: linear-gradient(135deg, #36a3f7 0%, #2b8ce6 100%);
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
        }

        .login-button:active {
          transform: translateY(0);
        }

        .login-footer {
          position: absolute;
          bottom: 20px;
          text-align: center;
          color: rgba(255, 255, 255, 0.8);
          font-size: 12px;
          line-height: 20px;
        }

        .login-footer p {
          margin: 0;
        }

        @media (max-width: 480px) {
          .login-main {
            padding: 30px 20px;
            margin: 0 10px;
          }

          .login-header h2 {
            font-size: 20px;
          }

          .captcha-row {
            flex-direction: column;
            gap: 10px;
          }

          .captcha-canvas {
            align-self: center;
          }
        }
      `}</style>
    </div>
  );
};

export default Login;
