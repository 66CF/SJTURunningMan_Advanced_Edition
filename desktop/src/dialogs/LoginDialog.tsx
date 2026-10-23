import { useEffect, useState } from 'react';
import type { LoginPreparation } from '../lib/backend';
import { Dialog } from '../components/Dialog';
import { Notice } from '../components/Notice';
import { TextField } from '../components/TextField';

interface LoginDialogProps {
  open: boolean;
  stage: 1 | 2;
  preparation: LoginPreparation | null;
  loading: boolean;
  notice: string;
  noticeKind: 'info' | 'error' | 'success';
  onRefresh: () => void;
  onSubmit: (username: string, password: string, captcha: string) => void;
  onCancel: () => void;
}

export function LoginDialog({
  open,
  stage,
  preparation,
  loading,
  notice,
  noticeKind,
  onRefresh,
  onSubmit,
  onCancel,
}: LoginDialogProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [captcha, setCaptcha] = useState('');
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (!open || stage === 2) {
      setPassword('');
      setCaptcha('');
    }
    if (!open) setVisible(false);
  }, [open, stage]);

  useEffect(() => {
    if (open && stage === 1) setCaptcha(preparation?.captchaCode ?? '');
  }, [open, stage, preparation]);

  const submit = () => {
    if (loading) return;
    onSubmit(username.trim(), password, captcha);
    setPassword('');
  };

  return (
    <Dialog
      open={open && stage === 1}
      onClose={onCancel}
      headline="连接 JAccount"
      icon="person"
      className="login-dialog"
      actions={
        <>
          <md-text-button onClick={onCancel} disabled={loading}>
            取消
          </md-text-button>
          <md-filled-button onClick={submit} disabled={loading}>
            {loading ? '连接中…' : '登录'}
          </md-filled-button>
        </>
      }
    >
      <form
        className="dialog-form"
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            submit();
          }
        }}
      >
        <p className="dialog-lead">凭据仅发送给学校认证服务，不保存在本地。</p>
        <TextField
          label="JAccount 账号"
          value={username}
          onValueChange={setUsername}
          disabled={loading}
          name="username"
        />
        <TextField
          label="密码"
          value={password}
          onValueChange={setPassword}
          password={!visible}
          disabled={loading}
          name="password"
          trailingIcon={
            <md-icon-button
              aria-label={visible ? '隐藏密码' : '显示密码'}
              title={visible ? '隐藏密码' : '显示密码'}
              onClick={(event) => {
                event.preventDefault();
                setVisible((value) => !value);
              }}
            >
              <md-icon aria-hidden="true">{visible ? 'visibility_off' : 'visibility'}</md-icon>
            </md-icon-button>
          }
        />
        <div className="captcha-row">
          <div className="captcha-copy">
            <TextField
              label="验证码"
              value={captcha}
              onValueChange={setCaptcha}
              disabled={loading}
              name="captcha"
            />
          </div>
          <div className="captcha-image-box">
            {preparation?.captcha ? (
              <img src={preparation.captcha} alt="图形验证码" />
            ) : (
              <md-circular-progress indeterminate aria-label="正在加载验证码" />
            )}
          </div>
          <md-outlined-icon-button
            aria-label="刷新验证码"
            title="刷新验证码"
            onClick={(event) => {
              event.preventDefault();
              onRefresh();
            }}
            disabled={loading}
          >
            <md-icon aria-hidden="true">refresh</md-icon>
          </md-outlined-icon-button>
        </div>
        {notice ? <Notice message={notice} kind={noticeKind} /> : null}
        <button type="submit" className="visually-hidden" tabIndex={-1} aria-hidden="true" />
      </form>
    </Dialog>
  );
}
