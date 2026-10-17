from fastapi import APIRouter, HTTPException

from app.llm.explain import explain
from app.rules.engine import load_procedures, recommend
from app.schemas import Procedure, RecommendationResponse, UserProfile

router = APIRouter(prefix="/api")


@router.post("/recommendations", response_model=RecommendationResponse)
async def post_recommendations(profile: UserProfile) -> RecommendationResponse:
    """プロフィールから関係しそうな手続きを優先度順に返す。"""
    items = recommend(profile)
    explanation = await explain(profile, items)
    if explanation is None:
        return RecommendationResponse(items=items)

    notes = {n.procedure_id: n.note for n in explanation.notes}
    for item in items:
        item.personal_note = notes.get(item.procedure_id)
    return RecommendationResponse(items=items, overall_advice=explanation.overall_advice, ai_generated=True)


@router.get("/procedures", response_model=list[Procedure])
def list_procedures() -> list[Procedure]:
    return [Procedure.model_validate(p.model_dump(exclude={"match"})) for p in load_procedures().values()]


@router.get("/procedures/{procedure_id}", response_model=Procedure)
def get_procedure(procedure_id: str) -> Procedure:
    """手続きの詳細とフロー（steps）を返す。"""
    proc = load_procedures().get(procedure_id)
    if proc is None:
        raise HTTPException(status_code=404, detail="procedure not found")
    return Procedure.model_validate(proc.model_dump(exclude={"match"}))
