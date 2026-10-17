# sd_2606

JPHACKS 2026 仙台会場のチームリポジトリ（public・MIT）。個人の状況から関係する税金・保険・年金の手続きを推薦し、手続きのフローを可視化する Android アプリ。

- 構成・API・ディレクトリ: [docs/architecture.md](docs/architecture.md)
- 環境構築・ブランチ運用: [docs/development.md](docs/development.md)
- 制度データの書き方: [docs/procedures.md](docs/procedures.md)
- 体験談の掲示板: [docs/board.md](docs/board.md)

## ルール

- 評価は Hack Day 中の実装のみ。動くデモを最優先する
- public リポジトリなので API キー・`.env`・`local.properties`・個人のサーバー情報をコミットしない
- **ユーザーの回答をサーバーに送らない**。アプリは `GET /api/catalog`（制度データと質問の定義）を受け取り、判定は端末内（`data/RuleEngine.kt`）で行う。回答は端末の中にだけ保存する
- 例外は掲示板（体験談）の投稿だけ。送るのは本人が「公開する」を押した内容で、年代は区分（生年月日は送らない）。投稿の本文はリポジトリに入れない
- 最初に聞くのはおすすめが大きく変わる項目だけ（`questions.yaml` の `initial: true`）。ほかの項目は判定に必要になったときだけ聞く
- 掲示板の API の形を変えるときは `backend/app/board/schemas.py` と `android/.../data/BoardModels.kt` を同時に直す
- `Catalog` の形を変えるときは `backend/app/schemas.py` と `android/app/src/main/java/jp/jphacks/sd2606/data/Models.kt` を同時に直す。判定の意味を変えるときは `backend/app/rules/engine.py` と `RuleEngine.kt` の両方を直す
- 「どの手続きが該当するか」はルール（YAML）で決め、LLM には判定させない
- バックエンドを変えたら `cd backend && .venv/Scripts/python -m pytest -q`、判定ロジックを変えたら `cd android && ./gradlew testDebugUnitTest`
