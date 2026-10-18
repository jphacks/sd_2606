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
- アプリが使うのは `GET /api/catalog`（制度データと質問の定義）だけ。判定はアプリの中で行う
- API キーが無くても動く（いまのアプリは Claude を使っていない）
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

チームの共有バックエンドは、メンバーの自宅の Raspberry Pi で動いている。インターネットに公開しているので **Tailscale などのアカウントは不要**。URL を知っていれば誰の PC・スマホからでも使える。

### アクセス方法

- **URL はチームのチャットで共有している**。リポジトリが public なので、ここには書かない
  - 公開経路は Tailscale Funnel（`https://<ホスト名>:8443/` の形）。URL は固定で、サーバーを再起動しても変わらない
  - 2026-10-17 16:05 までは臨時で Cloudflare Quick Tunnel（`https://<ランダム>.trycloudflare.com/` の形）を使っていた。その URL を設定したままなら、チャットで共有している Funnel の URL に切り替える
- 動作確認: ブラウザで `<URL>/health` を開いて `{"status":"ok", ...}` が出れば動いている
- API の試し打ち: `<URL>/docs`（Swagger UI）
- アプリから使う: `android/local.properties` に `api.baseUrl=<共有された URL>` と書き（末尾の `/` を忘れない）、Sync してからビルドし直す（URL はビルド時に埋め込まれる）

| やりたいこと | 繋ぐ先 |
| --- | --- |
| 画面の開発・見た目の確認 | 共有サーバー、または各画面の `@Preview` |
| バックエンドのコードや YAML を変えて試す | **自分の PC のバックエンド**（共有サーバーには main にマージされたものしか載らない） |
| デモ・発表 | 共有サーバー |

### 反映

- 共有サーバーに載るのは GitHub の **main ブランチ**の内容。反映はサーバー管理者が行うので、main にマージしたらチャットで依頼する
- API の形（`backend/app/schemas.py`）を変えるときは、Android 側（`data/Models.kt`）も同じ PR で直す。片方だけ反映されるとアプリが壊れる

### 注意事項

- **URL を公開の場所（README・Issue・PR・SNS など）に書かない**。知っていれば誰でもアクセスでき、認証も無い
- **ユーザーの回答をサーバーに送らない設計**。質問を増やすときも、アプリから送るのではなく `questions.yaml` に足して端末内で判定する（→ [architecture.md](architecture.md)）
- アプリからは使っていないが、`/api/recommendations` は呼ぶたびに Claude API の料金がかかる（同じ入力はサーバーでキャッシュ）。使いすぎ防止のため **サーバー全体で1分あたり20回** まで
- ループや自動テストで大量に叩かない。負荷試験やテストは自分の PC のバックエンドで行う（API キー無しなら Claude は呼ばれない）
- Claude の API キーはサーバーにだけ置く。メンバーに配らない・コミットしない
- 家庭用の回線と Pi 4 なので、停電・回線の不調などで止まることがある。AI の生成待ちで応答に 5〜20 秒かかることもある
- **デモの前には必ず `/health` を確認する**。止まっていたら管理者に連絡し、間に合わなければ自分の PC でバックエンドを起動して `api.baseUrl` を切り替える

### うまく繋がらないとき

| 症状 | 確認すること |
| --- | --- |
| アプリで「通信に失敗しました」 | ブラウザで `<URL>/health` が開けるか。開けなければサーバー側の問題なので管理者に連絡 |
| ブラウザでは開けるがアプリでは失敗 | `local.properties` の URL（`https://`・末尾の `/`）。変更後に Sync とビルドをし直したか |
| 以前は使えた URL が開けない | 停止した Cloudflare Quick Tunnel（`trycloudflare.com`）の URL のままになっていないか。チャットで最新の URL を確認する |
| 追加した質問が出てこない | `GET /api/catalog` の `questions` に出ているか。サーバーの再起動か反映が要る（YAML は起動時に読み込む） |
| 追加した手続き・項目が出てこない | その変更が main にマージされ、サーバーに反映済みか |

### サーバー管理者向け

サーバーの構成・設定・構築手順・運用コマンドは [architecture.md のサーバー構成](architecture.md#サーバー構成共有バックエンド) にまとめてある。よく使うもの:

- main を反映: Pi 上で `bash ~/sd_2606/backend/deploy/pi/deploy.sh`
- 公開の状態の確認: `tailscale funnel status`
- ログ: `journalctl -u sd2606-api -f`

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
