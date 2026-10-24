"""Add isolated household planning documents without changing legacy facts."""
from alembic import op
import sqlalchemy as sa

revision = "9a38d012ab61"
down_revision = "1f24a6148f6b"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table("planning_state", sa.Column("id", sa.Integer(), primary_key=True), sa.Column("revision", sa.Integer(), nullable=False), sa.Column("payload", sa.Text(), nullable=False))
    op.create_table("planning_documents", sa.Column("id", sa.String(64), primary_key=True), sa.Column("kind", sa.String(24), nullable=False), sa.Column("status", sa.String(24), nullable=False), sa.Column("revision", sa.Integer(), nullable=False), sa.Column("payload", sa.Text(), nullable=False), sa.Column("created_at", sa.String(40), nullable=False))
    op.create_index("ix_planning_documents_kind", "planning_documents", ["kind"])
    op.create_index("ix_planning_documents_status", "planning_documents", ["status"])


def downgrade():
    op.drop_table("planning_documents")
    op.drop_table("planning_state")
