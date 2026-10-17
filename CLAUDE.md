# sd_2606

JPHACKS 2026 仙台会場のチームリポジトリ（public・MIT）。個人の状況から関係する税金・保険・年金の手続きを推薦し、手続きのフローを可視化する Android アプリ。

- 構成・API・ディレクトリ: [docs/architecture.md](docs/architecture.md)
- 環境構築・ブランチ運用: [docs/development.md](docs/development.md)
- 制度データの書き方: [docs/procedures.md](docs/procedures.md)

## ルール

- 評価は Hack Day 中の実装のみ。動くデモを最優先する
- public リポジトリなので API キー・`.env`・`local.properties`・個人のサーバー情報をコミットしない
- **ユーザーの回答をサーバーに送らない**。アプリは `GET /api/catalog`（制度データと質問の定義）を受け取り、判定は端末内（`data/RuleEngine.kt`）で行う。回答は端末の中にだけ保存する
- 最初の画面で必須にするのは基本情報（働き方・生年月日・扶養の状況・年収。`questions.yaml` の `required: true`）だけ。ほかの項目は任意で、判定に必要になった手続きのカードでも聞く
- `Catalog` の形を変えるときは `backend/app/schemas.py` と `android/app/src/main/java/jp/jphacks/sd2606/data/Models.kt` を同時に直す。判定の意味を変えるときは `backend/app/rules/engine.py` と `RuleEngine.kt` の両方を直す
- 「どの手続きが該当するか」はルール（YAML）で決め、LLM には判定させない
- バックエンドを変えたら `cd backend && .venv/Scripts/python -m pytest -q`、判定ロジックを変えたら `cd android && ./gradlew testDebugUnitTest`
