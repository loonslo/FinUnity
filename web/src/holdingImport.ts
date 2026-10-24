import type { Locale, ParsedHolding } from './types';

export const importTemplate = '名称,数量,成本价,现价,币种,代码,类型\n示例股票,100,10.50,11.20,CNY,600000,stock';
export const englishImportTemplate = 'Name,Quantity,Average cost,Current price,Currency,Symbol,Type\nExample stock,100,10.50,11.20,USD,AAPL,stock';
const importError = (locale: Locale, chinese: string, english: string) =>
  locale === 'en-US' ? english : chinese;

/** CSV quoting also supports pasted Excel tabs and multiline quoted cells. */
export function parseDelimited(text: string, locale: Locale = 'zh-CN'): string[][] {
  text = text.replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n');
  const separator = text.split('\n')[0].includes('\t') ? '\t' : ',';
  const rows: string[][] = [];
  let row: string[] = [];
  let value = '';
  let state: 'plain' | 'quoted' | 'closed' = 'plain';
  const pushRow = () => {
    rows.push(row); row = [];
    if (rows.length > 501) throw new Error(importError(locale, '每次最多 500 行', 'A maximum of 500 rows is allowed'));
  };
  if (new TextEncoder().encode(text).length > 10 * 1024 * 1024) throw new Error(importError(locale, '文本不能超过 10 MB', 'Text exceeds 10 MB'));
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (state === 'quoted') {
      if (c === '"' && text[i + 1] === '"') { value += '"'; i++; }
      else if (c === '"') state = 'closed';
      else value += c;
    } else if (c === separator || c === '\n') {
      row.push(value); value = ''; state = 'plain';
      if (c === '\n') pushRow();
    } else if (c === '"' && !value && state === 'plain') state = 'quoted';
    else {
      if (state === 'closed' || c === '"') throw new Error(importError(locale, '引号格式错误', 'Invalid CSV quotes'));
      value += c;
    }
  }
  if (state === 'quoted') throw new Error(importError(locale, '引号未闭合', 'Unclosed CSV quote'));
  row.push(value);
  if (row.some(v => v.trim())) pushRow();
  return rows;
}

export function parseHoldingRows(rows: unknown[][], defaultCurrency?: string, locale: Locale = 'zh-CN'): ParsedHolding[] {
  if (rows.length < 2) throw new Error(importError(locale, '请提供表头和数据', 'A header row and at least one data row are required'));
  if (rows.length > 501) throw new Error(importError(locale, '每次最多 500 行', 'A maximum of 500 rows is allowed'));
  const aliases: Record<string, string> = {
    name: 'name', assetname: 'name', securityname: 'name',
    quantity: 'quantity', shares: 'quantity', units: 'quantity',
    cost: 'average_cost', averagecost: 'average_cost', avgcost: 'average_cost', unitcost: 'average_cost', costprice: 'average_cost',
    totalcost: 'total_cost', costamount: 'total_cost',
    currentprice: 'current_price', latestprice: 'current_price', marketprice: 'current_price',
    marketvalue: 'market_value',
    currency: 'currency', code: 'instrument_id', symbol: 'instrument_id', instrumentid: 'instrument_id',
    type: 'asset_type', assettype: 'asset_type', securitytype: 'asset_type',
    名称: 'name', 名稱: 'name', 持仓名称: 'name', 持倉名稱: 'name', 证券名称: 'name', 证券简称: 'name',
    股票名称: 'name', 基金名称: 'name', 产品名称: 'name', 资产名称: 'name',
    数量: 'quantity', 數量: 'quantity', 持仓数量: 'quantity', 持倉數量: 'quantity', 持有数量: 'quantity',
    持有份额: 'quantity', 持仓份额: 'quantity', 持倉份額: 'quantity',
    成本: 'average_cost', 成本价: 'average_cost', 成本價: 'average_cost', 单位成本: 'average_cost',
    持仓成本: 'average_cost', 持倉成本: 'average_cost', 买入均价: 'average_cost', 買入均價: 'average_cost',
    平均成本: 'average_cost', 平均成本价: 'average_cost',
    总成本: 'total_cost', 總成本: 'total_cost', 成本金额: 'total_cost', 成本金額: 'total_cost',
    累计成本: 'total_cost', 累計成本: 'total_cost',
    现价: 'current_price', 現價: 'current_price', 最新价: 'current_price', 最新價: 'current_price',
    当前价: 'current_price', 當前價: 'current_price', 市场价: 'current_price', 市場價: 'current_price',
    市值: 'market_value', 持仓市值: 'market_value', 持倉市值: 'market_value', 市场价值: 'market_value',
    币种: 'currency', 幣種: 'currency', 币别: 'currency', 幣別: 'currency', 货币: 'currency', 貨幣: 'currency',
    代码: 'instrument_id', 代碼: 'instrument_id', 证券代码: 'instrument_id', 證券代碼: 'instrument_id',
    股票代码: 'instrument_id', 基金代码: 'instrument_id', 产品代码: 'instrument_id', 產品代碼: 'instrument_id',
    类型: 'asset_type', 類型: 'asset_type', 资产类型: 'asset_type', 資產類型: 'asset_type',
    证券类型: 'asset_type', 證券類型: 'asset_type', 产品类型: 'asset_type', 產品類型: 'asset_type',
  };
  const headers = rows[0].map(v => { const key = String(v ?? '').trim().toLowerCase().replace(/\s+/g, ''); return aliases[key] || key; });
  if (!headers.includes('name') || !headers.includes('quantity')) throw new Error(importError(locale, '缺少名称或数量列', 'Name and quantity columns are required'));
  const allowed = new Set(['name', 'quantity', 'average_cost', 'total_cost', 'current_price', 'market_value', 'currency', 'instrument_id', 'asset_type']);
  if (headers.some(h => !allowed.has(h))) throw new Error(importError(locale, '存在空白或未知表头，请使用模板', 'Blank or unknown header; use the template'));
  if (new Set(headers).size !== headers.length) throw new Error(importError(locale, '表头重复', 'Duplicate headers'));
  const result = rows.slice(1).map((row, index) => {
    if (!row.some(v => v != null && String(v).trim())) return null;
    const field = (key: string) => String(row[headers.indexOf(key)] ?? '').trim();
    const fail = () => { throw new Error(importError(locale, `第 ${index + 2} 行：检查名称、数值、类型和账户币种`, `Row ${index + 2}: check the name, numbers, type, and account currency`)); };
    if (row.length > headers.length) fail();
    if (typeof row[headers.indexOf("instrument_id")] === "number") throw new Error(importError(locale, `第 ${index + 2} 行：代码列请设为文本以保留前导零`, `Row ${index + 2}: format the symbol column as text to preserve leading zeros`));
    const numeric = (key: string, required = false): number | undefined => {
      const raw = field(key); if (!raw) return required ? fail() : undefined;
      const normalized = raw.replace(/[\s,，]/g, '').replace(/^[¥￥$€£]/, '');
      if (!/^\d+(\.\d+)?$/.test(normalized)) return fail();
      const number = Number(normalized); if (!Number.isFinite(number) || number > Number.MAX_SAFE_INTEGER) return fail();
      return number;
    };
    const quantity = numeric('quantity', true)!;
    const totalCost = numeric('total_cost');
    const marketValue = numeric('market_value');
    const averageCost = numeric('average_cost');
    const currentPrice = numeric('current_price');
    const rawType = field('asset_type').toLowerCase();
    const assetType = ({ 股票: 'stock', stock: 'stock', etf: 'etf', 基金: 'fund', fund: 'fund', 债券: 'bond', 債券: 'bond', bond: 'bond', 现金: 'cash', 現金: 'cash', crypto: 'crypto', 加密货币: 'crypto', 加密貨幣: 'crypto', other: 'other', 其他: 'other' } as Record<string, string>)[rawType] || (rawType ? rawType : 'other');
    const parsedCurrency = field('currency').toUpperCase() || defaultCurrency;
    const item: ParsedHolding = { name: field('name'), quantity, average_cost: averageCost ?? (totalCost != null && quantity ? totalCost / quantity : undefined), current_price: currentPrice ?? (marketValue != null && quantity ? marketValue / quantity : undefined), currency: parsedCurrency, instrument_id: field('instrument_id') || undefined, asset_type: assetType };
    if (!item.name || item.name.length > 200 || (item.instrument_id?.length ?? 0) > 100 || item.quantity <= 0 || (item.currency && !/^[A-Z]{3,5}$/.test(item.currency)) || !['stock', 'etf', 'fund', 'cash', 'bond', 'crypto', 'other'].includes(item.asset_type!)) fail();
    return item;
  }).filter((item): item is ParsedHolding => item !== null);
  if (!result.length) throw new Error(importError(locale, '没有可导入的数据', 'No data rows to import'));
  return result;
}

export async function readHoldingFile(file: File, defaultCurrency?: string, locale: Locale = 'zh-CN'): Promise<ParsedHolding[]> {
  if (file.size > 10 * 1024 * 1024) throw new Error(importError(locale, '文件不能超过 10 MB', 'File exceeds 10 MB'));
  if (/\.xlsx$/i.test(file.name)) {
    const { readSheet } = await import('read-excel-file/browser');
    return parseHoldingRows(await readSheet(file, 1), defaultCurrency, locale);
  }
  if (!/\.(csv|tsv|txt)$/i.test(file.name)) throw new Error(importError(locale, '支持 XLSX、CSV、TSV、TXT', 'Supported formats: XLSX, CSV, TSV, and TXT'));
  return parseHoldingRows(parseDelimited(await file.text(), locale), defaultCurrency, locale);
}
