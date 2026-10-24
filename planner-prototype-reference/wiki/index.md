---
title: "Aurelio: an open-source personal finance app with an AI advisor"
description: "Aurelio tracks your net worth, investments, ETFs, cash and debts on your own computer, and answers questions about them with an AI model you choose. Guides, how each number is worked out, and what it costs."
nav_order: 0
---

# Aurelio: an open-source personal finance app with an AI advisor

**Aurelio is an open-source personal finance app that runs on your own computer: you record
your accounts, investments, home and debts, and it shows your net worth, what your funds
really hold and what is left each month.** A chat answers questions about those records with
an AI model you choose. Your records are kept in a file on your machine; the AI part is
optional, and it goes through [OpenRouter](https://openrouter.ai) with your own key.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/LosaLosSantos/aurelio-finance/main/docs/banner-dark.gif">
  <img alt="Aurelio: everything you own in one place. An animation: the net worth and what makes it up, the countries inside the funds, a question about 1,235 euros left each month answered with a split, and a proposal you confirm or reject." src="https://raw.githubusercontent.com/LosaLosSantos/aurelio-finance/main/docs/banner-light.gif" width="100%">
</picture>

## At a glance

- **What it is:** an open-source personal finance and wealth-management app with an AI
  advisor.
- **Where it runs:** on your own computer (Windows, macOS, Linux), in the browser at
  `localhost:8000`.
- **What it tracks:** bank and broker accounts, ETFs and shares, cash, homes and other real
  assets, debts, income, expenses and goals.
- **The AI:** optional; any model OpenRouter serves, with your own key; Claude Opus 5.5 by
  default.
- **What it costs:** the app is free (MIT license); a question to the AI costs about $0.01 to
  $0.24 on the default model, a full analysis $0.64.
- **Your records:** one SQLite file on your machine.
- **Code:** [github.com/LosaLosSantos/aurelio-finance](https://github.com/LosaLosSantos/aurelio-finance).

This wiki is the long version of the README: how to run it, how each number on the screen is
worked out, personal finance guides built on the same arithmetic, and what the AI does and
refuses to do. The examples use the demo household that ships with the app, so every figure
here can be reproduced on your own computer with one command.

## Start here

- [Self-hosted personal finance app: run Aurelio on your own computer](self-hosted-personal-finance-app.md):
  three tools, one command, and an invented household to try it on.
- [An AI financial advisor with your own OpenRouter key](ai-financial-advisor-with-your-own-openrouter-key.md):
  the key, a cap on what it can spend, and which model answers.
- [How much does an AI financial advisor cost?](how-much-does-an-ai-financial-advisor-cost.md):
  measured on real requests, from about two cents for a follow-up to 51 cents for a full
  analysis.

## Browse by topic

- [Guides](guides.md): running Aurelio, connecting the AI, and what it costs.
- [Net worth and cash](net-worth-and-cash.md): how to calculate net worth, with a mortgage,
  and how to follow your cash without bank sync.
- [ETFs and funds](etfs-and-funds.md): ETF overlap, the portfolio X-ray, and accumulating vs
  distributing.
- [Goals and planning](goals-and-planning.md): the emergency fund, and the rate of return a
  savings goal needs.
- [AI and money](ai-and-money.md): asking AI whether a stock is worth buying, and an AI
  portfolio review built so that it cannot simply agree with you.

## What it is not

Aurelio does not connect to your bank or your broker and never places an order: you record
what you hold, and it works from that. It is built for one person or one household on one
computer, not as a hosted service. What the chat says is educational information, not
regulated financial advice.

---

*Built by [@LosaLosSantos](https://github.com/LosaLosSantos). The rules behind every number
are in [How Aurelio reasons](https://github.com/LosaLosSantos/aurelio-finance/blob/main/docs/how-aurelio-reasons.md).*
