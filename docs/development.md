# 開発環境と進め方

## 必要なもの

| 用途 | ツール |
| --- | --- |
| Android | Android Studio（最新の安定版。JDK・Android SDK も同梱） |
| バックエンド | Python 3.12 以上 |
| 共通 | Git |

## バックエンド

```sh
cd backend
python -m venv .venv
.venv/Scripts/pip install -r requirements-dev.txt     # Mac/Linux: .venv/bin/pip
cp .env.example .env                                   # Claude を使うなら ANTHROPIC_API_KEY を入れる
.venv/Scripts/uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- http://localhost:8000/docs で API を試せる
- API キーが無くても動く（AI の補足文が付かないだけ）
- テスト: `.venv/Scripts/python -m pytest -q`

## Android

1. Android Studio で `android/` フォルダを開く（リポジトリのルートではない）
2. 接続先を `android/local.properties` に書く（このファイルはコミットされない）

   ```properties
   # エミュレータから自分の PC のバックエンドに繋ぐ（未指定時の既定値）
   api.baseUrl=http://10.0.2.2:8000/
   # 実機や共有サーバーに繋ぐ場合は、チームで共有している URL を入れる（末尾の / を忘れずに）
   # api.baseUrl=https://<共有サーバー>/
   ```

3. Run ▶ で起動

各画面には `@Preview` があるので、バックエンド無しでも Android Studio の Preview で見た目を確認できる。

## 共有サーバー（Raspberry Pi）

チームの共有バックエンドは Raspberry Pi 上で動かしている。URL はチームのチャットを参照。

- 初回: Pi 上で clone して `bash backend/deploy/pi/install.sh`（venv 作成・systemd ユニット配置・起動）
- 更新: main に push した後、Pi 上で `bash ~/sd_2606/backend/deploy/pi/deploy.sh`
- 構成: systemd ユニット `sd2606-api` が 127.0.0.1:8606 で起動し、Tailscale Funnel で HTTPS 公開

## ブランチと PR

- `main` は常に動く状態に保つ。作業は `feat/<内容>` などのブランチで行い、PR でマージする
- PR を出すと CI（バックエンドのテストと APK ビルド）が走る。緑になってからマージ
- 担当が分かれやすい単位:
  - 画面ごと（`ui/profile/`・`ui/recommend/`・`ui/flow/`）
  - 制度データ（`backend/data/procedures/` に1ファイルずつ追加）
  - バックエンドのロジック（`rules/`・`llm/`）
- API の形を変えるときは `backend/app/schemas.py` と `android/.../data/Models.kt` を同じ PR で変える

## 秘密情報

リポジトリは public。API キー・`.env`・`local.properties`・署名鍵はコミットしない（`.gitignore` 済み）。
