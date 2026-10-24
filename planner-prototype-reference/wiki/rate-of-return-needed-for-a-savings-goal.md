---
title: "What rate of return does your savings goal need?"
description: "The annual rate of return a savings goal requires, from what you have, what you add each month and the date. The formula, worked examples (€4,000 plus €250 a month reaches €18,000 in three years only at 17.5% a year), and how to read the answer."
parent: "Goals and planning"
nav_order: 2
---

# What rate of return does your savings goal need?

**The rate of return a savings goal needs is the yearly growth that turns what you have now,
plus what you add each month, into the target by its date: €4,000 plus €250 a month becomes
€18,000 in three years only at 17.5% a year.** If saving alone gets you there, the answer is
0%. If only an implausible return would, the useful move is to change the plan (more each
month, a later date, a smaller target), not to hope for the return. Aurelio, an open-source
personal finance app, works this out for every goal you record.

## The formula

With `C` what you have now, `M` what you add each month, `i` the monthly return and `n` the
number of months, the value at the end is:

```
FV = C × (1 + i)^n + M × ((1 + i)^n − 1) / i
```

With nothing added each month this solves directly: `i = (target / C)^(1/n) − 1`. With a
monthly contribution there is no closed form, so Aurelio searches for the rate numerically (by
bisection) and turns the monthly rate into a yearly one: `(1 + i)^12 − 1`.

## Worked out on the demo: a car in three years

The [demo household](self-hosted-personal-finance-app.md#try-it-on-the-demo-first) wants a
€18,000 car in three years. It has €4,000 set aside and adds €250 a month. Without any growth
that makes €4,000 + 36 × €250 = €13,000, which is €5,000 short. The return that closes the gap
is **17.5% a year**, and Aurelio's reading of it is "Unrealistic: consider extending the
horizon, raising contributions, or lowering the target."

Here is what each change does to it:

| The plan | Return required | Aurelio's reading |
|---|---|---|
| As recorded: €250 a month, 3 years | 17.5% | Unrealistic |
| €300 a month | 10.7% | Aggressive |
| €350 a month | 4.5% | Moderate |
| €389 a month | 0% | Already on track without investment growth |
| 4 years instead of 3 | 4.8% | Moderate |
| 5 years instead of 3 | 0% | Already on track without investment growth |
| A €16,000 car | 11.0% | Aggressive |

For a goal three years away, the monthly amount and the date move the answer far more than
any return you could reasonably expect. Over three years a portfolio that should earn a
moderate return on average can just as well be down when you need the money.

## Longer goals

The same arithmetic over longer horizons, where growth does more of the work:

| Goal | Return required |
|---|---|
| €300,000 in 25 years, from nothing, €500 a month | 5.2% |
| The same, starting from €10,000 | 4.4% |
| €50,000 in 5 years, from €10,000, €600 a month | 2.8% |
| €60,000 in 10 years, from €50,000, nothing added | 1.8% |
| €200,000 in 5 years, from €20,000, nothing added | 58.5% |

## How do you read the answer?

Aurelio puts each answer in one of these bands. They are its rules of thumb, not forecasts of
what any market will return:

| Return required | Reading |
|---|---|
| 0% or less | Already on track without investment growth |
| up to 4% | Conservative: feasible with a low-risk allocation |
| up to 8% | Moderate: in line with a diversified long-term portfolio |
| up to 12% | Aggressive: high risk, not guaranteed |
| above 12% | Unrealistic: consider extending the horizon, raising contributions, or lowering the target |
| no rate gets there | Target effectively unreachable with these contributions |

Two things the figure does not include:

- **Inflation.** The calculation is in plain money. A target written in today's prices,
  reached in ten years, buys less than it does today, so either write the target in future
  money or read the required return as a real one and add inflation on top.
- **Taxes and costs.** A fund's fees and the tax on its gains come out of the return, so the
  return you need before them is a little higher.

## Where does Aurelio show it?

Under Profile, Goals, choose "Target amount by a date" and fill in the target, what you have,
the monthly contribution and the date: the page answers at once, for instance "Over ~3 years
this requires a return of 17.5%/yr.", green up to 8%, amber up to 12%, red above that.

The chat sees each goal with its required return, what it would need each month with no
growth at all (€388.80 for the demo's car, on 12 October 2026) and what it lacks of that
(€138.80). The €250 recorded for the car counts as money already committed when the chat
works out what is left to invest each month.

## Questions

**What rate of return is realistic?** No one knows what markets will return. Aurelio's bands
read up to 4% a year as conservative, up to 8% as in line with a diversified long-term
portfolio and above 12% as unrealistic; they are rules of thumb for reading a plan, not
promises.

**How do I lower the return my goal needs?** Add more each month, move the date later, or
lower the target. For the demo's car, €100 more a month takes it from 17.5% to 4.5%, and one
more year takes it to 4.8%.

**Is this the same as a savings goal calculator?** It is the inverse: a savings calculator
asks what you will have at a given return; this asks what return your plan needs.

## Sources

- The formula and the bands: [planning.py](https://github.com/LosaLosSantos/aurelio-finance/blob/main/backend/app/planning.py).
- What each goal needs a month: [sizing.py](https://github.com/LosaLosSantos/aurelio-finance/blob/main/backend/app/sizing.py).
- [How many months of emergency fund do you need?](how-many-months-of-emergency-fund-do-you-need.md)

*Educational information, not regulated financial advice.*
