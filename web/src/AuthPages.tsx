import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useLocation } from "react-router-dom";
import { api, createClientRequestId, type AuthResult } from "./api";
import type { Locale, User } from "./types";
import { policyHref } from "./i18n";
import { X } from "lucide-react";

const english: Record<string, string> = {
  "请保存恢复码": "Save your recovery code",
  "恢复码只在此处显示一次，用于忘记密码时找回账号。请保存在可信位置，勿分享给他人。": "This code is shown once. Store it securely to recover your account; do not share it.",
  "我已保存恢复码": "I have saved the recovery code",
  "进入应用": "Continue",
  "请求失败": "Request failed",
  "验证码已发送": "Verification code sent",
  "为旧账号设置用户名": "Set a username for your existing account",
  "创建账号": "Create account",
  "使用恢复码找回密码": "Recover password with your code",
  "登录 FinUnity": "Sign in to FinUnity",
  "账户、持仓与流水使用同一份账本。行情为参考数据，请核对识别结果后再导入。": "Accounts, holdings and transactions share one ledger. Prices are reference data; review recognized rows before importing.",
  "原账号的数据将保留。设置用户名并保存恢复码后，可继续使用。": "Your data is preserved. Set a username and save your recovery code to continue.",
  "旧账号邮箱": "Existing account email",
  "用户名（旧账号可输入邮箱登录）": "Username (existing accounts may use email)",
  "用户名": "Username",
  "3～32 位字母、数字、下划线或短横线，不区分大小写。": "3–32 ASCII letters, digits, underscores or hyphens; case insensitive.",
  "显示名称（可选）": "Display name (optional)",
  "恢复码": "Recovery code",
  "邮箱验证码": "Email code",
  "发送验证码": "Send code",
  "新密码": "New password",
  "密码": "Password",
  "再次输入密码": "Confirm password",
  "我已阅读并同意": "I have read and agree to the",
  "隐私政策": "Privacy policy",
  "和": "and",
  "用户协议": "Terms of use",
  "处理中…": "Working…",
  "返回登录": "Back to sign in",
  "忘记密码？使用恢复码": "Forgot password? Use recovery code",
  "切换密码 / 邮箱验证码登录": "Switch password / email-code sign in",
  "已有账号，登录": "Already registered? Sign in",
  "注册账号": "Register",
  "已更新，其他会话已失效。": "Updated; older sessions are invalid.",
  "账号安全与数据": "Account security and data",
  "当前密码": "Current password",
  "再次输入新密码": "Confirm new password",
  "修改密码": "Change password",
  "重新生成恢复码": "Regenerate recovery code",
  "导出我的账户、持仓、流水 JSON": "Export my accounts, holdings and transactions as JSON",
  "确认永久注销账号并删除所有云端业务数据（含归档）": "Confirm permanent account deletion, including archived cloud data",
  "注销账号": "Delete account"
};
const localize = (locale: Locale, value: string) => locale === "zh-CN" ? value : english[value] || value;

type Props = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  onSignedIn: (user: User) => void;
  onRegistered?: (user: User) => Promise<User>;
  onClose?: () => void;
  initialResult?: AuthResult;
  onPending?: (result: AuthResult) => void;
};

export function RecoveryNotice({ result, onSaved, locale = "zh-CN", busy = false }: {
  result: AuthResult; onSaved: () => void; locale?: Locale; busy?: boolean;
}) {
  const copy = (value: string) => localize(locale, value);
  const zh = locale === "zh-CN";
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState("");
  return <section className="recovery-step" aria-live="polite">
    <small>{zh ? "第 2 步，共 2 步" : "Step 2 of 2"}</small>
    <h2 id="auth-title">{copy("请保存恢复码")}</h2>
    <p>{zh ? "账号已创建。没有邮箱和手机号，恢复码是唯一的找回方式。这串恢复码只显示这一次。" : "Your account is ready. With no email or phone, this code is the only way to recover it. It is shown once."}</p>
    <pre style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{result.recovery_code}</pre>
    <div className="recovery-actions">
      <button type="button" className="secondary-button" onClick={async () => {
        try { await navigator.clipboard.writeText(result.recovery_code || ""); setSaved(true); setError(""); }
        catch { setError(zh ? "复制失败，请下载或手动保存。" : "Copy failed. Download or save manually."); }
      }}>{zh ? "复制" : "Copy"}</button>
      <button type="button" className="secondary-button" onClick={() => {
        const url = URL.createObjectURL(new Blob([result.recovery_code || ""], { type: "text/plain;charset=utf-8" }));
        const a = document.createElement("a"); a.href = url; a.download = "finunity-recovery-code.txt"; a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000); setSaved(true);
      }}>{zh ? "下载 .txt" : "Download .txt"}</button>
    </div>
    {error && <p role="alert">{error}</p>}
    <label className="consent-row"><input type="checkbox" checked={saved} onChange={e => setSaved(e.target.checked)} /> {copy("我已保存恢复码")}</label>
    <button className="primary-button" disabled={!saved || busy} onClick={onSaved}>{busy ? copy("处理中…") : zh ? "进入我的账本" : "Open my ledger"}</button>
    <p>{zh ? "以后也可以在设置里用密码重新生成。" : "You can regenerate a code in Settings with your password."}</p>
  </section>;
}

function CredentialsPage({ locale, setLocale, onSignedIn, onRegistered, onClose, initialResult, onPending, register = false, migrate = false }: Props & {
  register?: boolean; migrate?: boolean;
}) {
  const copy = (value: string) => localize(locale, value);
  const zh = locale === "zh-CN";
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [recoveryCode, setRecoveryCode] = useState("");
  const [reset, setReset] = useState(false);
  const [emailCodeMode, setEmailCodeMode] = useState(false);
  const [emailCode, setEmailCode] = useState("");
  const [consent, setConsent] = useState(false);
  const [confirmConsent, setConfirmConsent] = useState(false);
  const [pending, setPending] = useState<AuthResult | null>(initialResult ?? null);
  const [message, setMessage] = useState("");
  const panel = useRef<HTMLElement>(null);
  const closeRef = useRef(onClose);
  useEffect(() => { closeRef.current = onClose; }, [onClose]);
  // Config is only fetched after a visitor submits a form: opening the panel stays offline.
  const config = useQuery({ queryKey: ["auth-config"], queryFn: api.authConfig, enabled: false, retry: false });
  const complete = useMutation({
    mutationFn: async () => {
      if (!pending) return;
      let user = api.completeAuth(pending);
      if (register && onRegistered) user = await onRegistered(user);
      onSignedIn(user);
    },
    onError: error => setMessage(error.message),
  });
  const blocked = Boolean(pending) || complete.isPending;
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    panel.current?.querySelector<HTMLElement>("input,button,a")?.focus();
    const key = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !blocked) closeRef.current?.();
      if (event.key !== "Tab") return;
      const nodes = Array.from(panel.current?.querySelectorAll<HTMLElement>('input:not(:disabled),button:not(:disabled),a[href]') || []);
      const first = nodes[0], last = nodes.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    const unload = (event: BeforeUnloadEvent) => { if (blocked) event.preventDefault(); };
    addEventListener("beforeunload", unload);
    document.addEventListener("keydown", key);
    const overflow = document.body.style.overflow; document.body.style.overflow = "hidden";
    return () => { removeEventListener("beforeunload", unload); document.removeEventListener("keydown", key); document.body.style.overflow = overflow; previous?.focus(); };
  }, [blocked]);
  const action = useMutation({
    mutationFn: async (accepted: boolean) => {
      if (migrate) return api.setUsername(username, password);
      if (register) return api.registerUsername(username, password, password, "", accepted);
      if (reset) return api.recoverPassword(username, recoveryCode, password, password);
      const user = emailCodeMode
        ? await api.login(username, emailCode, createClientRequestId())
        : await api.loginWithPassword(username, password, createClientRequestId());
      onSignedIn(user);
      return null;
    },
    onSuccess: result => { if (result) { setPending(result); onPending?.(result); setPassword(""); } },
    onError: error => { setMessage(error.message); if (!register && !reset && !migrate) void config.refetch(); },
  });
  const sendCode = useMutation({
    mutationFn: () => api.sendCode(username, "login", locale),
    onSuccess: data => setMessage(data.dev_code ? `Local code: ${data.dev_code}` : copy("验证码已发送")),
    onError: error => setMessage(error.message),
  });
  const title = migrate ? copy("为旧账号设置用户名") : reset ? copy("使用恢复码找回密码") : register
    ? (location.state?.reason === "screenshot" ? zh ? "识别你自己的截图需要账号" : "Create an account to recognize your screenshots" : zh ? "保存你的账本" : "Save your ledger")
    : zh ? "登录后回到你的账本" : "Sign in to your ledger";
  return <div className="auth-backdrop">
    <section ref={panel} className="auth-panel" role="dialog" aria-modal="true" aria-labelledby="auth-title">
      <header><span>{zh ? "衡仓" : "FinUnity"}</span><div>
        <button type="button" className="text-button" onClick={() => setLocale(zh ? "en-US" : "zh-CN")}>{zh ? "English" : "中文"}</button>
        {onClose && <button type="button" className="icon-button" disabled={blocked || action.isPending} onClick={onClose} aria-label={zh ? "关闭" : "Close"}><X /></button>}
      </div></header>
      {message && <p role="alert">{message}</p>}
      {pending ? <RecoveryNotice locale={locale} result={pending} busy={complete.isPending} onSaved={() => complete.mutate()} /> : <>
        {register && <small>{zh ? "第 1 步，共 2 步" : "Step 1 of 2"}</small>}
        <h2 id="auth-title">{title}</h2>
        {register && <p>{zh ? "不需要邮箱或手机号。示例里调过的目标比例、主币种和语言会一起带过去。" : "No email or phone needed. Your adjusted targets, base currency and language come with you."}</p>}
        {!migrate && <nav className="auth-tabs" aria-label={zh ? "账号操作" : "Account"}>
          <Link className={register ? "active" : ""} replace to="/register" state={location.state}>{copy("注册账号")}</Link>
          <Link className={!register ? "active" : ""} replace to="/login" state={location.state}>{zh ? "登录" : "Sign in"}</Link>
        </nav>}
        <form onSubmit={e => { e.preventDefault(); setMessage(""); if (register && !consent) setConfirmConsent(true); else action.mutate(consent); }}>
          <fieldset disabled={action.isPending}>
          <label className="field">{copy(register || reset || migrate ? "用户名" : "用户名（旧账号可输入邮箱登录）")}
            <input value={username} onChange={e => setUsername(e.target.value)} required autoComplete="username" minLength={3}
              maxLength={register || reset || migrate ? 32 : 254} pattern={register || reset || migrate ? "[A-Za-z0-9_\\-]{3,32}" : undefined} />
          </label>
          {(register || migrate) && <small>{copy("3～32 位字母、数字、下划线或短横线，不区分大小写。")}</small>}
          {reset && <label className="field">{copy("恢复码")}<input value={recoveryCode} onChange={e => setRecoveryCode(e.target.value)} required autoComplete="off" /></label>}
          {emailCodeMode ? <><label className="field">{copy("邮箱验证码")}<input value={emailCode} onChange={e => setEmailCode(e.target.value)} pattern="[0-9]{6}" required /></label>
            <button type="button" className="secondary-button" disabled={sendCode.isPending} onClick={() => sendCode.mutate()}>{copy("发送验证码")}</button></> :
          <label className="field">{reset ? copy("新密码") : copy("密码")}<span className="password-input"><input type={showPassword ? "text" : "password"} value={password} onChange={e => setPassword(e.target.value)}
            autoComplete={register || reset ? "new-password" : "current-password"} minLength={8} maxLength={128} required />
            <button type="button" className="text-button" onClick={() => setShowPassword(!showPassword)}>{zh ? showPassword ? "隐藏" : "显示" : showPassword ? "Hide" : "Show"}</button></span></label>}
          {register && <label className="consent-row"><input type="checkbox" checked={consent} onChange={e => { setConsent(e.target.checked); setConfirmConsent(false); }} />{copy("我已阅读并同意")} <a href={policyHref(locale, "privacy")} target="_blank" rel="noreferrer">{copy("隐私政策")}</a> {copy("和")} <a href={policyHref(locale, "terms")} target="_blank" rel="noreferrer">{copy("用户协议")}</a></label>}
          {confirmConsent && <div className="consent-confirm" role="alert"><p>{zh ? "创建账号需要同意上述隐私政策和用户协议。" : "Agree to the privacy policy and terms to create your account."}</p>
            <button type="button" className="primary-button" onClick={() => { setConsent(true); setConfirmConsent(false); action.mutate(true); }}>{zh ? "同意并创建" : "Agree and create"}</button></div>}
          <button className="primary-button" disabled={action.isPending}>{action.isPending ? copy("处理中…") : register ? copy("创建账号") : reset ? copy("使用恢复码找回密码") : migrate ? copy("为旧账号设置用户名") : zh ? "登录" : "Sign in"}</button>
          </fieldset>
        </form>
        {!register && !migrate && <button type="button" className="text-link" onClick={() => { setReset(!reset); setEmailCodeMode(false); setMessage(""); }}>{reset ? copy("返回登录") : copy("忘记密码？使用恢复码")}</button>}
        {!register && !reset && !migrate && config.data?.email_auth_enabled && <button type="button" className="text-link" onClick={() => setEmailCodeMode(!emailCodeMode)}>{copy("切换密码 / 邮箱验证码登录")}</button>}
      </>}
    </section>
  </div>;
}

export const PublicHome = (props: Props) => <CredentialsPage {...props} />;
export const RegisterPage = (props: Props) => <CredentialsPage {...props} register />;
export const UsernameMigration = (props: Props) => <CredentialsPage {...props} migrate />;

export function AccountSecurity({ onLogout, locale }: { onLogout: () => void; locale: Locale }) {
  const copy = (value: string) => localize(locale, value);
  const [password, setPassword] = useState("");
  const [next, setNext] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [deleteConfirmed, setDeleteConfirmed] = useState(false);
  const [recovery, setRecovery] = useState<AuthResult | null>(null);
  const [message, setMessage] = useState("");
  const action = useMutation({
    mutationFn: async (kind: "change" | "regenerate" | "delete" | "export") => {
      if (kind === "export") {
        const data = await api.exportData();
        const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }));
        const a = document.createElement("a"); a.href = url; a.download = "finunity-export.json"; a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000); return;
      }
      if (kind === "delete") { await api.deleteAccount(password); onLogout(); return; }
      if (kind === "regenerate") setRecovery(await api.regenerateRecovery(password));
      else await api.changePassword(password, next, confirmation);
      setPassword(""); setNext(""); setConfirmation("");
      setMessage(copy("已更新，其他会话已失效。"));
    },
    onError: error => setMessage(error.message),
  });
  if (recovery) return <RecoveryNotice locale={locale} result={recovery} onSaved={() => setRecovery(null)} />;
  return <section className="account-security"><h3>{copy("账号安全与数据")}</h3>
    {message && <p role="status">{message}</p>}
    <label className="field">{copy("当前密码")}<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" /></label>
    <label className="field">{copy("新密码")}<input type="password" value={next} onChange={e => setNext(e.target.value)} autoComplete="new-password" /></label>
    <label className="field">{copy("再次输入新密码")}<input type="password" value={confirmation} onChange={e => setConfirmation(e.target.value)} autoComplete="new-password" /></label>
    <button className="secondary-button" disabled={action.isPending || !password || !next || !confirmation} onClick={() => action.mutate("change")}>{copy("修改密码")}</button>
    <button className="secondary-button" disabled={action.isPending || !password} onClick={() => action.mutate("regenerate")}>{copy("重新生成恢复码")}</button>
    <button className="secondary-button" disabled={action.isPending} onClick={() => action.mutate("export")}>{copy("导出我的账户、持仓、流水 JSON")}</button>
    <label className="consent-row"><input type="checkbox" checked={deleteConfirmed} onChange={e => setDeleteConfirmed(e.target.checked)} />{copy("确认永久注销账号并删除所有云端业务数据（含归档）")}</label>
    <button className="danger-button" disabled={action.isPending || !password || !deleteConfirmed} onClick={() => action.mutate("delete")}>{copy("注销账号")}</button>
  </section>;
}
