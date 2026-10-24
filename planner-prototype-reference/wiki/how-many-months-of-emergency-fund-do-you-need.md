---
title: "How many months of emergency fund do you need?"
description: "The rule of thumb is three to six months of essential expenses, not of salary. What changes your number, which expenses count, and how Aurelio sizes the reserve and keeps it out of what it calls investable."
parent: "Goals and planning"
nav_order: 1
---

# How many months of emergency fund do you need?

**The rule of thumb most often quoted is three to six months of essential expenses: what you
would still have to pay if your income stopped tomorrow.** Where you land in that range, or
beyond it, depends less on the rule than on how likely it is that your income stops, and for
how long. Aurelio, an open-source personal finance app, keeps six months unless you tell it
otherwise, and never counts the reserve as money available to invest.

## Months of salary or months of expenses?

Months of **essential expenses**, not of salary and not of everything you spend. The fund
replaces what you would still have to pay, not what you earn: the demo household earns €3,400
a month and has €1,905 of essential expenses, so six months is €11,430, not €20,400.

In an emergency you would stop eating out long before you stopped paying the mortgage, so the
discretionary part is what you cut, not what the reserve has to cover. Essential usually
means housing (rent or the mortgage payment), food, utilities, transport to work, insurance,
debt repayments and health. Discretionary means what you could stop next month without a
penalty.

## What moves the number?

These push it towards more months:

- one income in the household, or an income that varies (self-employment, commissions);
- people who depend on you;
- a job that would take long to replace in your field or your area;
- large costs that arrive without warning (an old car, an old house).

These towards fewer:

- two stable incomes, either of which covers the essentials;
- insurance that pays out if you cannot work;
- expenses you could cut quickly.

Rules of thumb differ, and so do people: the right number is the one you would still agree
with in the middle of the emergency.

## How Aurelio sizes the reserve

The reserve is the months times your essential expenses a month, and each part comes from
your records:

- **The months** are your answer to the Profile question "How many months of essential
  expenses do you want to keep aside as a reserve, whatever you invest?". Unanswered, the
  app uses six, and when the chat uses it, it says that six is the app's default and not your
  choice.
- **The essential expenses** are those you marked essential under Records, Cash flow, at their
  monthly rate: a yearly bill counts a twelfth of itself each month. An expense with no nature
  set counts as essential, because a reserve that is too large errs on the safe side.
- **No expenses recorded, no reserve.** Without them nothing says what a month costs, so the
  app says the reserve cannot be sized rather than calling it zero.

## Worked out on the demo

The [demo household](self-hosted-personal-finance-app.md#try-it-on-the-demo-first) has six
expenses:

| Expense | Nature | A month |
|---|---|---|
| Mortgage payment | essential | €1,050 |
| Groceries | essential | €480 |
| Electricity and heating | essential | €190 |
| Public transport | essential | €140 |
| Home insurance, €540 a year | essential | €45 |
| Eating out and leisure | discretionary | €260 |

Essential expenses come to €1,905 a month. The demo's profile does not answer the reserve
question, so the app's six months apply: a reserve of €11,430.

On 12 October 2026 the demo's two accounts held €14,374 in cash, so €2,944 sat above the
reserve. That €2,944 is the largest sum the chat will put into a purchase today: the reserve
itself is never offered for investing, and a suggestion with a larger amount is refused, with
the figures that refused it.

## Keep the goal and the reserve apart

The demo also has an "Emergency fund" goal, with a note ("Six months of expenses, kept at the
bank."). A goal describes what you are aiming for; the reserve is what the app holds back
when it works out what you could invest. The months the reserve uses are the Profile answer,
not the goal's note, so if you keep a different number of months, say it in Profile.

## Questions

**Is three months of expenses enough?** It can be, with two stable incomes, few fixed costs
and insurance against losing your income. With one income that varies, people who depend on
it, or a job that is slow to replace, six months or more is the safer end.

**Should an emergency fund be invested?** Most guidance keeps it in cash you can reach within
days, because the moment you need it can be the moment markets are down. Aurelio counts only
the cash above the reserve as investable.

**Does a yearly expense count?** Yes, at a twelfth of itself each month: the demo's €540 home
insurance adds €45 to every month of the reserve.

## Sources

- How the reserve is sized: [sizing.py](https://github.com/LosaLosSantos/aurelio-finance/blob/main/backend/app/sizing.py).
- [Personal finance without bank sync](personal-finance-without-bank-sync.md): where the cash
  on the accounts comes from.
- [Ask AI whether a stock is worth buying](ask-ai-whether-a-stock-is-worth-buying.md): the
  reserve is the first figure an amount stands on.

*Educational information, not regulated financial advice.*
