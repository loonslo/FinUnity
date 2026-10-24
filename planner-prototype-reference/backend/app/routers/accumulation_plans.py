"""REST endpoints for accumulation plans (PAC): recurring savings plans that
move cash from a source institution to buy one or more target investments.

For now a plan is declarative (the advisor sees it; it does not move money on
its own — auto-execution is the future Buy transaction). The institutions a
plan names must exist (404 otherwise), and both ends of it must be named:
`AccumulationPlanWrite` requires the source and each target's institution,
which `AccumulationPlanCreate` does not. See the schema for why the two differ.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app import crud, models, schemas
from app.database import get_db
from app.routers.deps import get_or_404

router = APIRouter(prefix="/api", tags=["accumulation_plans"])


def _missing(exc: crud.InstitutionMissing) -> HTTPException:
    """404 for an institution the plan names that does not exist. The check is
    `crud`'s since brief AO, so the PAC form's requests and the chat's card
    make it alike; its sentence is the one `get_or_404` says."""
    return HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc))


@router.post(
    "/accumulation-plans",
    response_model=schemas.AccumulationPlanRead,
    status_code=status.HTTP_201_CREATED,
)
def create_accumulation_plan(
    payload: schemas.AccumulationPlanWrite,
    db: Session = Depends(get_db),
) -> schemas.AccumulationPlanRead:
    """Create a new accumulation plan (404 if a linked institution doesn't exist).

    `AccumulationPlanWrite` rather than `AccumulationPlanCreate`: a plan posted
    here must say where the money comes from and where each position will be
    held. Money moves from an account into a holding, and a plan that names
    neither end is describing a purchase nobody made — see the schema for the
    total it moved when it did."""
    try:
        return crud.create_accumulation_plan(db, payload)
    except crud.InstitutionMissing as exc:
        raise _missing(exc)


@router.get("/accumulation-plans", response_model=list[schemas.AccumulationPlanRead])
def list_accumulation_plans(
    db: Session = Depends(get_db),
) -> list[schemas.AccumulationPlanRead]:
    """List all accumulation plans."""
    return crud.get_accumulation_plans(db)


@router.get(
    "/accumulation-plans/{plan_id}", response_model=schemas.AccumulationPlanRead
)
def get_accumulation_plan(
    plan_id: int,
    db: Session = Depends(get_db),
) -> schemas.AccumulationPlanRead:
    """Return a single accumulation plan, or 404 if it does not exist."""
    return get_or_404(db, models.AccumulationPlan, plan_id)


@router.put(
    "/accumulation-plans/{plan_id}", response_model=schemas.AccumulationPlanRead
)
def update_accumulation_plan(
    plan_id: int,
    payload: schemas.AccumulationPlanWrite,
    db: Session = Depends(get_db),
) -> schemas.AccumulationPlanRead:
    """Update an accumulation plan (404 if it or a linked institution doesn't exist).

    The same requirement as the POST, and this is the door a plan saved without
    either end is repaired through: naming them is a two-field edit. The GET
    keeps returning such a plan unchanged, so it can be read before it is
    fixed. The institutions are checked first, then the plan, as before."""
    try:
        plan = crud.update_accumulation_plan(db, plan_id, payload)
    except crud.InstitutionMissing as exc:
        raise _missing(exc)
    return plan if plan is not None else get_or_404(db, models.AccumulationPlan, plan_id)


@router.delete(
    "/accumulation-plans/{plan_id}", status_code=status.HTTP_204_NO_CONTENT
)
def delete_accumulation_plan(plan_id: int, db: Session = Depends(get_db)) -> None:
    """Delete an accumulation plan."""
    get_or_404(db, models.AccumulationPlan, plan_id)
    crud.delete_accumulation_plan(db, plan_id)
