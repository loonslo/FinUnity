---
title: "How much does an AI financial advisor cost?"
description: "Measured on Aurelio in October 2026 on the default model: about two cents for a follow-up question, eleven to twelve cents for a first question, 64 cents for a full portfolio analysis, paid to OpenRouter with your own key."
parent: "Guides"
nav_order: 3
---

# How much does an AI financial advisor cost?

**An AI financial advisor costs a few cents per question when you pay for the model yourself:
on Aurelio's default model, a follow-up question costs about $0.02, a first question $0.11 to
$0.12, and a full portfolio analysis $0.64.** These are what OpenRouter charged for
real requests made through the app in October 2026, on a test database, with Claude Opus 5.5
at $4 per million input tokens and $20 per million output tokens. You pay OpenRouter with your
own key; there is no subscription on top, and the app itself is free and open source.

## What did each kind of question cost?

| What you do | What it cost |
|---|---|
| A first question that drew a card, answered in one round | $0.11 to $0.12 |
| A follow-up, answered in one round | $0.021 |
| A follow-up that drew a card, answered in one round | $0.012 to $0.022 |
| Confirming a card, with the reply after it | $0.014 to $0.049 |
| Whether something is worth buying, and how much, in two or three rounds, its searches included | $0.067 to $0.24 |
| Each web search, on top of the round that asked for it | $0.0073 to $0.0075 |
| An analysis, its lookups included | $0.64 |

A card is a change the chat proposes to your records, which you confirm or reject; a round is
one call to the model, and a question that needs a tool (a search, a calculation on your
records) takes more than one. The chat's rows were measured on 11 and 12 October 2026, the
analysis on 12 October, the web search on 9 October.

## Why does the first question cost more than the next?

Every question carries your whole financial picture, rebuilt from the records at that
moment: the accounts, the positions, the flows, the goals, the profile, and a note on the page
you asked from. Aurelio sends all of it rather than letting the model fetch one section at a
time, because the sections answer each other: "can I afford a house" is also a question about
your expenses. On the test database that prompt ran to about 25,000 tokens.

The provider keeps the start of a prompt in a cache for a few minutes, and reading it from
there costs a fraction of sending it fresh. A follow-up asked soon after shares that start
with the question before it, so it pays mostly for what is new. Measured on the questions
about whether something is worth buying: $0.24 for the first question of the run, which
wrote the whole prompt, and $0.107 to $0.139 for the ones after it, which read it from the
cache.

## What else moves the price?

- **The model.** The same question on a model at $2 / $6 per million tokens costs a fraction
  of what it costs on one at $4 / $20. The choices are on
  [An AI financial advisor with your own OpenRouter key](ai-financial-advisor-with-your-own-openrouter-key.md).
- **The length of the answer.** Output costs five times input on the default model: 3,000
  tokens of answer are $0.06.
- **Searches.** Each one adds about $0.0075. The chat searches when a question needs
  something your records do not hold, such as a company's latest results.
- **Rounds.** A question that asks the app to size a purchase or read your funds' look-through
  takes two or three rounds instead of one.

## Why does an analysis cost more?

The [AI portfolio review](ai-portfolio-review.md) is several steps, each writing a full
document: an analyst, a confidant who challenges it, the analyst's answer when the challenge
is contested, and a synthesis. That is three steps when there is nothing to argue about and up
to six when there is. The analyst's steps may look things up first, in up to four requests and
three web searches a step, so what a run costs moves with what they look up: in the run
measured on 12 October 2026 the analyst looked up ten things in three rounds, and the run cost
$0.64. Before it starts, the card that proposes it tells you what the last run cost, so you
confirm a figure rather than a guess.

## What does a month cost?

An illustration from the measured figures, not a measurement: five first questions at $0.12,
twenty follow-ups at $0.021 and two analyses at $0.64 come to $0.60 + $0.42 + $1.28 = $2.30 for
the month, on the default model.

## How do you keep it bounded?

- Give the key a credit limit on OpenRouter: it can never spend past it. Leave it some room,
  as [explained here](ai-financial-advisor-with-your-own-openrouter-key.md#cap-what-the-key-can-spend).
- Pick a cheaper model in the chat's menu for everyday questions, and keep the large one for
  decisions.
- Check what you spend on OpenRouter's [Activity](https://openrouter.ai/activity) page, which
  holds your account's usage history.

## What these numbers are not

They were measured on one model, on a test database, in October 2026. A larger record of
your own makes the prompt larger, prices on OpenRouter change, and another model changes
everything. The [README](https://github.com/LosaLosSantos/aurelio-finance#the-ai-part) carries
the table and is updated when it is measured again.

## Questions

**Is there a subscription?** No. Aurelio is free and open source; the only cost is what
OpenRouter charges for each request, on your own key.

**Can a question cost more than the table says?** Yes, with a longer record, a longer answer,
more searches or a pricier model. A credit limit on the key caps the total whatever happens.
