import os

from dotenv import load_dotenv

load_dotenv()


class Settings:
    claude_model: str = os.getenv("CLAUDE_MODEL", "claude-opus-5-5")
    # キーが無ければ LLM を使わずルール判定だけで動く
    llm_enabled: bool = bool(os.getenv("ANTHROPIC_API_KEY")) and os.getenv("LLM_ENABLED", "1") != "0"
    llm_timeout_sec: float = float(os.getenv("LLM_TIMEOUT_SEC", "30"))
    # 公開 API なので、Claude の呼び出し回数をサーバー全体で制限する
    llm_calls_per_minute: int = int(os.getenv("LLM_CALLS_PER_MINUTE", "20"))


settings = Settings()
