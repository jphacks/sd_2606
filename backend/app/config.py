import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()


class Settings:
    claude_model: str = os.getenv("CLAUDE_MODEL", "claude-opus-5-5")
    # キーが無ければ LLM を使わずルール判定だけで動く
    llm_enabled: bool = bool(os.getenv("ANTHROPIC_API_KEY")) and os.getenv("LLM_ENABLED", "1") != "0"
    llm_timeout_sec: float = float(os.getenv("LLM_TIMEOUT_SEC", "30"))
    # 公開 API なので、Claude の呼び出し回数をサーバー全体で制限する
    llm_calls_per_minute: int = int(os.getenv("LLM_CALLS_PER_MINUTE", "20"))
    # 体験談の掲示板の SQLite。Pi では systemd の StateDirectory（/var/lib/sd2606）に置く（リポジトリの外なので deploy で消えない）
    board_db_path: str = os.getenv("BOARD_DB_PATH", str(Path(__file__).resolve().parents[1] / "board.db"))
    # 掲示板の投稿の回数制限（送信元ごと・10分あたり）と、通報で自動的に非表示にする件数
    board_posts_per_10min: int = int(os.getenv("BOARD_POSTS_PER_10MIN", "5"))
    board_report_hide: int = int(os.getenv("BOARD_REPORT_HIDE", "3"))


settings = Settings()
