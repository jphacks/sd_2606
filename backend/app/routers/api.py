from fastapi import APIRouter, HTTPException

from app.llm.chat import ChatReply, ChatRequest, ChatUnavailable, chat
from app.llm.explain import explain
from app.rules.engine import load_procedures, recommend
from app.rules.questions import build_catalog
from app.schemas import Catalog, Procedure, RecommendationResponse, UserProfile

router = APIRouter(prefix="/api")


@router.get("/catalog", response_model=Catalog)
def get_catalog() -> Catalog:
    """全手続き（判定条件つき）と質問の定義。アプリはこれを受け取って端末内で判定し、ユーザーの情報は送らない。"""
    return build_catalog()


@router.post("/recommendations", response_model=RecommendationResponse)
async def post_recommendations(profile: UserProfile) -> RecommendationResponse:
    """プロフィールから関係しそうな手続きを優先度順に返す。

    アプリはプロフィールを送らない方針にしたため使っていない（判定は /api/catalog を使って端末内で行う）。
    """
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
    return [Procedure.model_validate(p.model_dump(exclude={"relations"})) for p in load_procedures().values()]


@router.get("/procedures/{procedure_id}", response_model=Procedure)
def get_procedure(procedure_id: str) -> Procedure:
    """手続きの詳細とフロー（steps）を返す。"""
    proc = load_procedures().get(procedure_id)
    if proc is None:
        raise HTTPException(status_code=404, detail="procedure not found")
    return Procedure.model_validate(proc.model_dump(exclude={"relations"}))


@router.post("/chat", response_model=ChatReply)
async def post_chat(req: ChatRequest) -> ChatReply:
    """アプリの「AIに質問」。回答を表示用に加工したもの（年齢など）と会話を受け取り、Claude の答えか聞き返しを返す。

    受け取った内容はサーバーに保存しない。
    """
    if req.messages[-1].role != "user":
        raise HTTPException(status_code=422, detail="最後のメッセージはユーザーの質問にしてください")
    try:
        return await chat(req)
    except ChatUnavailable as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
