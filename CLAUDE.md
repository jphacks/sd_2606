# sd_2606

JPHACKS 2026 仙台会場のチームリポジトリ（public・MIT）。個人の状況から関係する税金・保険・年金の手続きを推薦し、手続きのフローを可視化する Android アプリ。

- 構成・API・ディレクトリ: [docs/architecture.md](docs/architecture.md)
- 環境構築・ブランチ運用: [docs/development.md](docs/development.md)
- 制度データの書き方: [docs/procedures.md](docs/procedures.md)

## ルール

- 評価は Hack Day 中の実装のみ。動くデモを最優先する
- public リポジトリなので API キー・`.env`・`local.properties`・個人のサーバー情報をコミットしない
- API の形を変えるときは `backend/app/schemas.py` と `android/app/src/main/java/jp/jphacks/sd2606/data/Models.kt` を同時に直す
- 「どの手続きが該当するか」はルール（YAML）で決め、LLM には判定させない。LLM は補足文の生成だけ
- バックエンドを変えたら `cd backend && .venv/Scripts/python -m pytest -q`
