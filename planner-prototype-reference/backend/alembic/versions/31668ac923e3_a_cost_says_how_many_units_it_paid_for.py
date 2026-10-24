"""A situation's cost can say how many of the row's units it paid for.

Brief AV (2026-10-14). A row whose photograph recorded no cost, with buys on
top, is partly paid: measured on the demo with an invented fund photographed
at 10 x 100 and three plan buys of 100, 300.00 of a 1,300.00 book. "Start from
the last one" wrote the new situation's row with no cost at all, so what was
paid was folded into the situation's value and nothing could read it back.
The new row now keeps it: its cost is what was paid, and `cost_quantity` says
for how many of its units (3 of the 13); the others are the situation's value.

One column added, no row rewritten: `holdings.cost_quantity`. Nullable, and
null is the honest value for every row already on record, whose cost, where
it has one, was typed for all of its units. No other table is touched. The app
copies the database before it runs this (brief W part 1); run by hand through
Alembic's command line, nothing does.

The downgrade drops the column, and with it what each carried row said its
cost paid for: those costs would then read as covering every unit.

Revision ID: 31668ac923e3
Revises: 491fe57d31db
"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "31668ac923e3"
down_revision = "491fe57d31db"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("holdings") as batch:
        batch.add_column(sa.Column("cost_quantity", sa.Float(), nullable=True))


def downgrade() -> None:
    with op.batch_alter_table("holdings") as batch:
        batch.drop_column("cost_quantity")
