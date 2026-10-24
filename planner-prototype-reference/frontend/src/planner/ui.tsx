import { fmt } from "./format";
import { useEffect, useId, useRef, type ReactNode } from "react";
import type { Calculation } from "./api";

const paths: Record<string, string> = {
  home: "M3.5 11 12 4l8.5 7M6 9.5V20h12V9.5",
  book: "M5 4.5h11a3 3 0 0 1 3 3V20H8a3 3 0 0 1-3-3V4.5zM5 17a3 3 0 0 1 3-3h11",
  sliders: "M4 7h9M17 7h3M4 17h3M11 17h9M13 4.5v5M7 14.5v5",
  check: "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0zM8 12.4l2.7 2.7 5.3-5.6",
  spark:
    "M12 3.5l1.9 5.4 5.4 1.9-5.4 1.9L12 18.1l-1.9-5.4-5.4-1.9 5.4-1.9L12 3.5z",
  x: "M6 6l12 12M18 6 6 18",
  plus: "M12 5v14M5 12h14",
  refresh:
    "M20 11a8 8 0 0 0-14.3-3.6M4.5 4v4h4M4 13a8 8 0 0 0 14.3 3.6M19.5 20v-4h-4",
  lock: "M6.5 11h11V20h-11zM8.7 11V8.2a3.3 3.3 0 0 1 6.6 0V11",
  chev: "M9.5 6l6 6-6 6",
};
export function Icon({ name, size = 20 }: { name: string; size?: number }) {
  return (
    <svg
      className="ic"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name] ?? paths.check} />
    </svg>
  );
}

export function BigMoney({
  value,
  currency = "CNY",
}: {
  value: string | null;
  currency?: string;
}) {
  if (value == null) return <>待补充</>;
  if (currency !== "CNY") return <>{fmt(value, currency)}</>;
  const parts = fmt(value).replace(/[¥￥]/g, "").split(".");
  return (
    <>
      <span className="cur">¥</span>
      {parts[0]}
      <span className="dec">.{parts[1]}</span>
    </>
  );
}
export function Chip({
  children,
  tone = "plain",
}: {
  children: ReactNode;
  tone?: string;
}) {
  return <span className={`chip ${tone}`}>{children}</span>;
}
export function Button({
  children,
  primary = false,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { primary?: boolean }) {
  return (
    <button
      type="button"
      {...props}
      className={`btn ${primary ? "primary" : ""} ${props.className ?? ""}`}
    >
      {children}
    </button>
  );
}
export function PageHead({
  eyebrow,
  title,
  lede,
  meta,
}: {
  eyebrow: string;
  title: string;
  lede?: string;
  meta?: ReactNode;
}) {
  return (
    <header className="page-head">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1 className="title">{title}</h1>
        {lede && <p className="lede">{lede}</p>}
      </div>
      {meta && <div className="meta">{meta}</div>}
    </header>
  );
}
export function SectionHead({
  no,
  title,
  aside,
}: {
  no: string;
  title: string;
  aside?: ReactNode;
}) {
  return (
    <div className="sec-head">
      <h2>
        <span className="no">{no}</span>
        {title}
      </h2>
      {aside && <div className="section-aside">{aside}</div>}
    </div>
  );
}
export function LedgerLine({
  title,
  value,
  sw,
  sign,
  total,
  note,
}: {
  title: ReactNode;
  value: ReactNode;
  sw?: string;
  sign?: string;
  total?: boolean;
  note?: ReactNode;
}) {
  return (
    <div className="ldw">
      <div className={`ld ${total ? "total" : ""}`}>
        {sign && <span className="sign">{sign}</span>}
        {sw && <span className={`sw ${sw}`} />}
        <span>{title}</span>
        <i />
        <b>{value}</b>
      </div>
      {note && <div className="ld-note ind">{note}</div>}
    </div>
  );
}
export function Field({
  title,
  children,
  hint,
  error,
}: {
  title: string;
  children: ReactNode;
  hint?: ReactNode;
  error?: string;
}) {
  return (
    <label className="field">
      <span className="lab">{title}</span>
      {children}
      {hint && <span className="hint">{hint}</span>}
      {error && (
        <span className="errmsg" role="alert">
          {error}
        </span>
      )}
    </label>
  );
}
export function Issues({ result }: { result: Calculation }) {
  const messages = [...result.missing, ...result.errors, ...result.warnings];
  return messages.length ? (
    <div className={result.errors.length ? "summary-err" : "review-banner"}>
      {messages.map((x) => (
        <p key={x}>{x}</p>
      ))}
    </div>
  ) : null;
}
export function Modal({
  title,
  children,
  close,
  sheet = false,
  footer,
  preview,
  busy = false,
}: {
  title: string;
  children: ReactNode;
  close: () => void;
  sheet?: boolean;
  footer?: ReactNode;
  preview?: ReactNode;
  busy?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const busyRef = useRef(busy);
  const id = useId();
  useEffect(() => {
    busyRef.current = busy;
  }, [busy]);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    (
      ref.current?.querySelector<HTMLElement>("[data-autofocus]") ?? ref.current
    )?.focus();
    const key = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busyRef.current) close();
      if (event.key !== "Tab") return;
      const nodes = [
        ...(ref.current?.querySelectorAll<HTMLElement>(
          "button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href]",
        ) ?? []),
      ].filter((x) => x.getClientRects().length > 0);
      const first = nodes[0],
        last = nodes.at(-1);
      if (!first) {
        event.preventDefault();
        return;
      }
      if (
        event.shiftKey &&
        (document.activeElement === first ||
          document.activeElement === ref.current)
      ) {
        event.preventDefault();
        last?.focus();
      } else if (
        !event.shiftKey &&
        (document.activeElement === last ||
          document.activeElement === ref.current)
      ) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", key);
    return () => {
      document.body.style.overflow = overflow;
      document.removeEventListener("keydown", key);
      previous?.focus();
    };
  }, [close]);
  return (
    <>
      <div
        className="scrim"
        onClick={() => !busy && close()}
        aria-hidden="true"
      />
      <div
        ref={ref}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby={id}
        className={sheet ? "sheet" : "dialog"}
        data-fresh="true"
      >
        {sheet ? (
          <>
            <div className="sheet-head">
              <h2 id={id}>{title}</h2>
              <span className="sp" />
              <Button
                className="ghost sm"
                onClick={close}
                disabled={busy}
                aria-label="关闭抽屉"
              >
                <Icon name="x" />
              </Button>
            </div>
            <div className="sheet-body">{children}</div>
            {preview && <div className="sheet-pv">{preview}</div>}
            {footer && <div className="sheet-foot">{footer}</div>}
          </>
        ) : (
          <>
            <h2 id={id}>{title}</h2>
            {children}
            <div className="foot">{footer}</div>
          </>
        )}
      </div>
    </>
  );
}
