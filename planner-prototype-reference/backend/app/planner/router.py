from __future__ import annotations

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.database import get_db
from app.planner import assistant, service
from app.planner.schemas import (
    ActionRead,
    ActionDecision,
    BatchRecordRequest,
    BatchRecordRead,
    Ask,
    Calculation,
    Confirm,
    Renew,
    Reconcile,
    Household,
    Occurrence,
    OccurrenceRead,
    PlanRead,
    PlanRequest,
    ProposeRequest,
    ReviewRequest,
    ReviewRead,
    SaveHousehold,
    TaskRead,
)

router = APIRouter(prefix="/api/v1/planner", tags=["local-planning"])


class SnapshotResponse(BaseModel):
    revision: int
    household_revision: int
    household: Household
    result: Calculation
    active_result: Calculation | None
    active_plan_id: str | None


class RecordRequest(BaseModel):
    expected_revision: int
    event: Occurrence


class RevisionRequest(BaseModel):
    expected_revision: int


@router.get("/state", response_model=SnapshotResponse)
def read_state(db: Session = Depends(get_db)):
    return service.snapshot(db)


@router.post("/household", response_model=SnapshotResponse)
def save(payload: SaveHousehold, db: Session = Depends(get_db)):
    return service.save_household(db, payload.household, payload.expected_revision)


@router.post("/household/validate", response_model=Household)
def validate_household(payload: Household):
    """Validate an import preview without persisting or changing the revision."""
    return payload


@router.post("/calculate", response_model=SnapshotResponse)
def calculate(payload: PlanRequest, db: Session = Depends(get_db)):
    return service.snapshot(db, payload)


@router.post("/plans", status_code=201, response_model=PlanRead)
def propose(payload: ProposeRequest, db: Session = Depends(get_db)) -> dict:
    return service.propose(
        db,
        PlanRequest.model_validate(payload.model_dump(exclude={"expected_revision"})),
        payload.expected_revision,
    )


@router.get("/plans", response_model=list[PlanRead])
def plans(db: Session = Depends(get_db)) -> list[dict]:
    return [service.plan_view(db, row) for row in service.rows(db, "plan")]


@router.post("/plans/{identifier}/discard", response_model=PlanRead)
def discard(identifier: str, db: Session = Depends(get_db)) -> dict:
    return service.discard_plan(db, identifier)


@router.post("/plans/{identifier}/renew", response_model=list[ActionRead])
def renew(identifier: str, payload: Renew, db: Session = Depends(get_db)) -> list[dict]:
    return service.renew(
        db, identifier, payload.expected_revision, payload.accept_shortfalls
    )


@router.post("/reconciliations", status_code=201, response_model=OccurrenceRead)
def reconcile(payload: Reconcile, db: Session = Depends(get_db)) -> dict:
    return service.reconcile(db, payload)


@router.post("/plans/{identifier}/confirm", response_model=PlanRead)
def confirm(identifier: str, payload: Confirm, db: Session = Depends(get_db)) -> dict:
    return service.confirm(db, identifier, payload.accept_shortfalls)


@router.post("/plans/{identifier}/reviews", status_code=201, response_model=ReviewRead)
def review(
    identifier: str, payload: ReviewRequest, db: Session = Depends(get_db)
) -> dict:
    return service.review(db, identifier, payload.month)


@router.get("/occurrences", response_model=list[OccurrenceRead])
def occurrences(db: Session = Depends(get_db)) -> list[dict]:
    return [service.document(row) for row in service.rows(db, "occurrence")]


@router.post("/occurrences", status_code=201, response_model=OccurrenceRead)
def record(payload: RecordRequest, db: Session = Depends(get_db)) -> dict:
    return service.record(db, payload.event, payload.expected_revision)


@router.post("/occurrences/batch", status_code=201, response_model=BatchRecordRead)
def record_batch(payload: BatchRecordRequest, db: Session = Depends(get_db)) -> dict:
    return service.record_batch(db, payload)


@router.post("/occurrences/{identifier}/void", response_model=OccurrenceRead)
def void(
    identifier: str, payload: RevisionRequest, db: Session = Depends(get_db)
) -> dict:
    return service.void_occurrence(db, identifier, payload.expected_revision)


@router.get("/actions", response_model=list[ActionRead])
def actions(db: Session = Depends(get_db)) -> list[dict]:
    return [service.action_view(db, row) for row in service.rows(db, "action")]


@router.post("/actions/{identifier}/complete", response_model=ActionRead)
def complete(
    identifier: str, payload: ActionDecision, db: Session = Depends(get_db)
) -> dict:
    return service.complete_action(db, identifier, payload.event_id, payload.note)


@router.get("/reviews", response_model=list[ReviewRead])
def reviews(db: Session = Depends(get_db)) -> list[dict]:
    return [service.document(row) for row in service.review_documents(db)]


@router.get("/assistant")
def assistant_config() -> dict:
    return {
        "configured": assistant.configured(),
        "daily_tasks": assistant.DAILY_TASKS,
        "max_calls": assistant.MAX_CALLS,
        "max_output_tokens_per_call": assistant.MAX_OUTPUT_TOKENS,
    }


@router.post("/assistant/tasks", status_code=202, response_model=TaskRead)
def ask(payload: Ask, db: Session = Depends(get_db)) -> dict:
    return assistant.enqueue(db, payload)


@router.get("/assistant/tasks", response_model=list[TaskRead])
def tasks(db: Session = Depends(get_db)) -> list[dict]:
    return [service.document(row) for row in service.rows(db, "task")]


@router.get("/assistant/tasks/{identifier}", response_model=TaskRead)
def task(identifier: str, db: Session = Depends(get_db)) -> dict:
    return service.document(service.get_document(db, identifier, "task"))
