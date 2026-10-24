import { api } from "../api/client";
import type { components } from "../api/schema";
export type Household = components["schemas"]["Household-Output"];
export type Plan = components["schemas"]["PlanRead"];
export type Calculation = components["schemas"]["Calculation"];
export type PlanRequest = components["schemas"]["PlanRequest-Input"];
export type Occurrence = components["schemas"]["Occurrence-Output"];
export type Snapshot = components["schemas"]["SnapshotResponse"];
export type Action = components["schemas"]["ActionRead"];
export type Event = components["schemas"]["OccurrenceRead"];
export type Review = components["schemas"]["ReviewRead"];
export type Task = components["schemas"]["TaskRead"];
const root = "/api/v1/planner";
export async function get<T>(path: string): Promise<T> {
  return (await api.get<T>(root + path)).data;
}
export async function post<T>(path: string, data: unknown): Promise<T> {
  return (await api.post<T>(root + path, data)).data;
}
export function errorMessage(error: unknown): string {
  const e = error as {
    response?: {
      data?: {
        detail?: unknown;
      };
    };
    message?: string;
  };
  const detail = e.response?.data?.detail;
  if (Array.isArray(detail))
    return detail
      .map((x: { msg: string; loc: (string | number)[] }) => {
        const fields: Record<string, string> = {
          household: "建档",
          scenario: "试算条件",
          income: "收入",
          expenses: "支出",
          amount: "金额",
          annual_return: "年收益假设",
          annual_inflation: "年通胀假设",
          as_of: "余额截至日期",
          observed_on: "日终日期",
          items: "记录",
          name: "名称",
          monthly: "每月投入",
          assets: "账户",
          goals: "目标",
          allocations: "资金分配",
          balance: "余额",
        };
        const location = x.loc
          .slice(1)
          .map((k) =>
            typeof k === "number" ? `第 ${k + 1} 条` : (fields[k] ?? k),
          )
          .join(" / ");
        const message = x.msg.startsWith("Value error, ")
          ? x.msg.slice(13)
          : /[\u4e00-\u9fff]/.test(x.msg)
            ? x.msg
            : x.msg.includes("required")
              ? "请填写此项"
              : x.msg.includes("greater") || x.msg.includes("less")
                ? "数值超出允许范围，请核对"
                : x.msg.includes("decimal")
                  ? "请填写有效的十进制数值"
                  : "格式无效，请核对数值、日期和必填项";
        return `${location}：${message}`;
      })
      .join("；");
  if (detail && typeof detail === "object" && "message" in detail)
    return String(detail.message);
  return typeof detail === "string"
    ? detail
    : "操作未完成，请检查后端是否已启动。";
}
