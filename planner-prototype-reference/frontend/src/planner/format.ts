export function fmt(
  value: string | number | null | undefined,
  currency = "CNY",
) {
  if (value == null || value === "" || !Number.isFinite(Number(value)))
    return "待补充";
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
    .format(Number(value))
    .replaceAll("-", "−");
}
