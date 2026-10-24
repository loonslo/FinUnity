export const zh = {
  brand: "FinUnity",
  subtitle: "把目标变成每月可以做到的事",
  overview: "我的规划",
  household: "财务建档",
  scenarios: "方案测算",
  execution: "执行与复盘",
  assistant: "规划助手",
  unknown: "待补充",
  pending: "待确认",
  in_progress: "进行中",
  needs_data: "待核对资料",
  active: "使用中",
  superseded: "历史版本",
  completed: "已完成",
  forecast: "预计 · 不入账",
  confirmed: "已发生",
  settled: "已核销",
  voided: "已作废",
  queued: "排队中",
  running: "生成中",
  failed: "未完成",
  interrupted: "已中断",
  stale: "已过期",
  discarded: "已放弃",
};
export function label(key: string): string {
  return zh[key as keyof typeof zh] ?? key;
}
