import os
import tempfile
from pathlib import Path

# テストでは Claude API を呼ばない
os.environ["LLM_ENABLED"] = "0"
# 掲示板の DB はテストごとの一時フォルダに作る
os.environ["BOARD_DB_PATH"] = str(Path(tempfile.mkdtemp(prefix="sd2606-board-")) / "board.db")
