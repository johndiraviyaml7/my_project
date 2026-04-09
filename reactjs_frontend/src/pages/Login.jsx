import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import styles from './Login.module.css';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('admin@quantixmed.com');
  const [password, setPassword] = useState('Admin@123');
  const [showPass, setShowPass] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [keepLogged, setKeepLogged] = useState(true);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    await new Promise(r => setTimeout(r, 600));
    const ok = login(email, password);
    if (ok) {
      navigate('/offerings');
    } else {
      setError('Invalid email or password. Use admin@quantixmed.com / Admin@123');
    }
    setLoading(false);
  };

  return (
    <div className={styles.page}>
      {/* Left panel */}
      <div className={styles.left}>
        <div className={styles.leftContent}>
          <div className={styles.illustration}>
            <div className={styles.illustrationBg}>
              <div className={styles.robot}>
                <div className={styles.robotHead}>
                  <div className={styles.robotEye} />
                  <div className={styles.robotEye} />
                </div>
                <div className={styles.robotBody}>
                  <div className={styles.crossIcon}>+</div>
                </div>
                <div className={styles.robotArm} />
              </div>
              <div className={styles.pillBottle} />
              <div className={styles.pills}>
                <span className={styles.pill} style={{background:'#ef4444'}}/>
                <span className={styles.pill} style={{background:'#22c55e'}}/>
                <span className={styles.pill} style={{background:'#f97316'}}/>
                <span className={styles.pill} style={{background:'#ef4444'}}/>
              </div>
              <div className={styles.chatBubble}>
                <span/><span/><span/>
              </div>
              <div className={styles.screenCard}/>
            </div>
          </div>
        </div>
        <div className={styles.leftOverlay} />
      </div>

      {/* Right panel */}
      <div className={styles.right}>
        <div className={styles.formWrap}>
          <div className={styles.brandMobile}>
            <span>💙</span> <strong>Quantix</strong>Med
          </div>

          <h1 className={styles.title}>Sign In</h1>
          <p className={styles.subtitle}>Enter your email and password to sign in!</p>

          <form onSubmit={handleSubmit} className={styles.form}>
            <div className={styles.field}>
              <label className={styles.label}>Email<span className={styles.req}>*</span></label>
              <input
                className={styles.input}
                type="email"
                placeholder="mail@simmmple.com"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label}>Password<span className={styles.req}>*</span></label>
              <div className={styles.passWrap}>
                <input
                  className={styles.input}
                  type={showPass ? 'text' : 'password'}
                  placeholder="••••••••••"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  required
                />
                <button type="button" className={styles.eyeBtn} onClick={() => setShowPass(!showPass)}>
                  {showPass ? '🙈' : '👁️'}
                </button>
              </div>
            </div>

            <div className={styles.row}>
              <label className={styles.checkLabel}>
                <input
                  type="checkbox"
                  checked={keepLogged}
                  onChange={e => setKeepLogged(e.target.checked)}
                  className={styles.checkbox}
                />
                Keep me logged in
              </label>
              <button type="button" className={styles.forgotLink}>Forget password?</button>
            </div>

            {error && <div className={styles.error}>{error}</div>}

            <button type="submit" className={styles.submitBtn} disabled={loading}>
              {loading ? <span className={styles.spinner} /> : 'Sign In'}
            </button>

            <div className={styles.orDivider}><span>or</span></div>

            <button type="button" className={styles.altBtn}>
              Sign in with Number
            </button>

            <p className={styles.register}>
              Not registered yet?{' '}
              <button type="button" className={styles.createLink}>Create an Account</button>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}
