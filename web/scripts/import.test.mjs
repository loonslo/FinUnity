import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseDelimited, parseHoldingRows } from '../src/holdingImport.ts';
import { decimalString } from '../src/decimal.ts';

test('Chinese CSV preserves quoted names, codes and zero price', () => {
  const [row] = parseHoldingRows(parseDelimited('\uFEFF名称,数量,成本价,现价,币种,代码\r\n"公司, A",100,12.50,0,CNY,000001'), 'CNY');
  assert.equal(row.name, '公司, A'); assert.equal(row.instrument_id, '000001');
  assert.equal(row.current_price, 0); assert.equal(row.average_cost, 12.5);
});
test('pasted Excel tabs support optional missing costs', () => {
  const [row] = parseHoldingRows(parseDelimited('name\tquantity\nBond\t2'), 'USD');
  assert.equal(row.currency, 'USD'); assert.equal(row.average_cost, undefined);
});
test('invalid values and mismatched currencies never become preview data', () => {
  for (const value of ['', '-1', 'NaN', 'Infinity', '1e300', '=1+1', '0']) {
    assert.throws(() => parseHoldingRows([['name', 'quantity'], ['A', value]], 'CNY'));
  }
  assert.equal(parseHoldingRows([['name', 'quantity', 'currency'], ['A', 1, 'USD']], 'CNY')[0].currency, 'USD');
  assert.throws(() => parseDelimited('name,quantity\n"A,1'));
  assert.throws(() => parseHoldingRows([['name', 'name', 'quantity'], ['A', 'B', 1]], 'CNY'));
});
test('row limit rejects oversized imports', () => {
  assert.throws(() => parseHoldingRows([['name', 'quantity'], ...Array.from({length: 501}, () => ['A', 1])], 'CNY'));
});

test('malformed quote suffix, extra columns and unknown headers are rejected', () => {
  assert.throws(() => parseDelimited('name,quantity\n"A"oops,1'));
  assert.throws(() => parseHoldingRows(parseDelimited('name,quantity\nA,1,USD'), 'CNY'));
  assert.throws(() => parseHoldingRows([['name', 'quantity', 'curreny'], ['A', 1, 'USD']], 'CNY'));
  assert.throws(() => parseHoldingRows([['name', 'quantity'], [null, null]], 'CNY'));
});

test('traditional headers and original row numbers survive blank rows', () => {
  assert.equal(parseHoldingRows([['名稱', '數量', '幣種'], ['測試', 1, 'CNY']], 'CNY')[0].name, '測試');
  assert.throws(() => parseHoldingRows([['name', 'quantity'], [], ['A', -1]], 'CNY'), /第 3 行/);
  assert.deepEqual(parseDelimited('name,quantity\n"A\nB",2\n'), [['name', 'quantity'], ['A\nB', '2']]);
});

test('small quantities serialize using the server decimal contract', () => {
  assert.equal(decimalString(0.0000001), '0.0000001');
  assert.equal(decimalString(1.25e-8), '0.0000000125');
  assert.equal(decimalString(1e21), '1000000000000000000000');
  assert.equal(decimalString(0), '0');
  assert.throws(() => decimalString(Infinity));
});

test('numeric Excel instrument codes require explicit text formatting', () => {
  assert.throws(() => parseHoldingRows([['name', 'quantity', 'instrument_id'], ['A', 1, 1]], 'CNY'), /代码列请设为文本/);
  assert.equal(parseHoldingRows([['name', 'quantity', 'instrument_id'], ['A', 1, '000001']], 'CNY')[0].instrument_id, '000001');
});

test('common broker headers and formatted amounts derive missing unit values', () => {
  const [row] = parseHoldingRows([
    ['证券名称', '证券代码', '持仓数量', '总成本', '市值', '币别', '证券类型'],
    ['沪深300ETF', '510300', '1,000', '3,500.00', '3,600.00', 'CNY', 'ETF'],
  ], 'CNY');
  assert.equal(row.name, '沪深300ETF');
  assert.equal(row.quantity, 1000);
  assert.equal(row.average_cost, 3.5);
  assert.equal(row.current_price, 3.6);
  assert.equal(row.asset_type, 'etf');
});
