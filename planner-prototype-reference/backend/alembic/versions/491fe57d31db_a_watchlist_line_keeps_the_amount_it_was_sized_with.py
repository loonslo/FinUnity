"""A watchlist line keeps the amount its card was sized with.

Brief AQ (2026-10-12). A suggestion card may now carry an amount: a sum put in
now, a sum put in each month, or both, chosen by the chat within what the
records allow, which the app computes (`sizing`), with the assumptions it rests
on. Accepting the card parks the idea as before, and the line now keeps what
the card said: the amounts, the currency they are in, the assumptions in the
chat's words, and the figures they stood on in the app's words, with their day.
A month later the question about an amount is what it was sized against.

Five columns added, no row rewritten: `amount_now`, `amount_monthly`,
`amount_currency`, `amount_assumptions`, `amount_figures`. Nullable, and null is
the honest value for every line already on record, which no amount was ever
given for, as it is for a card that carries none. No other table is touched.
The app copies the database before it runs this (brief W part 1); run by hand
through Alembic's command line, nothing does.

The downgrade drops the five columns, and with them the amounts the lines kept.

Revision ID: 491fe57d31db
Revises: e62c6f6c8060
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "491fe57d31db"
down_revision = "e62c6f6c8060"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("watchlist_items") as batch:
        batch.add_column(sa.Column("amount_now", sa.Float(), nullable=True))
        batch.add_column(sa.Column("amount_monthly", sa.Float(), nullable=True))
        batch.add_column(sa.Column("amount_currency", sa.String(), nullable=True))
        batch.add_column(sa.Column("amount_assumptions", sa.Text(), nullable=True))
        batch.add_column(sa.Column("amount_figures", sa.Text(), nullable=True))


def downgrade() -> None:
    with op.batch_alter_table("watchlist_items") as batch:
        batch.drop_column("amount_figures")
        batch.drop_column("amount_assumptions")
        batch.drop_column("amount_currency")
        batch.drop_column("amount_monthly")
        batch.drop_column("amount_now")
