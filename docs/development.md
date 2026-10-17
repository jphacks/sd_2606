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

チームの共有バックエンドは、メンバーの自宅の Raspberry Pi で動いている。インターネットに公開しているので **Tailscale などのアカウントは不要**。URL を知っていれば誰の PC・スマホからでも使える。

### アクセス方法

- **URL はチームのチャットで共有している**。リポジトリが public なので、ここには書かない
  - 現在は臨時で Cloudflare Quick Tunnel（`https://<ランダム>.trycloudflare.com/` の形）を使っている。**サーバーの再起動などで URL が変わる**ことがあるので、繋がらなくなったらチャットで最新の URL を確認する
  - 本来の公開経路（Tailscale Funnel、`https://<ホスト名>:8443/` の形）は準備中
- 動作確認: ブラウザで `<URL>/health` を開いて `{"status":"ok", ...}` が出れば動いている（`llm_enabled` が `true` なら AI の補足が付く）
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
- **本物の個人情報（氏名・住所・マイナンバーなど）を送らない**。入力欄を増やすときも、年齢や職業のような選択項目にとどめる
- `/api/recommendations` は呼ぶたびに Claude API の料金がかかる（同じ入力はサーバーでキャッシュ）。使いすぎ防止のため **サーバー全体で1分あたり20回** まで。超えると AI の補足が付かず、ルール判定の結果だけが返る（エラーにはならない）
- ループや自動テストで大量に叩かない。負荷試験やテストは自分の PC のバックエンドで行う（API キー無しなら Claude は呼ばれない）
- Claude の API キーはサーバーにだけ置く。メンバーに配らない・コミットしない
- 家庭用の回線と Pi 4 なので、停電・回線の不調などで止まることがある。AI の生成待ちで応答に 5〜20 秒かかることもある
- **デモの前には必ず `/health` を確認する**。止まっていたら管理者に連絡し、間に合わなければ自分の PC でバックエンドを起動して `api.baseUrl` を切り替える

### うまく繋がらないとき

| 症状 | 確認すること |
| --- | --- |
| アプリで「通信に失敗しました」 | ブラウザで `<URL>/health` が開けるか。開けなければサーバー側の問題なので管理者に連絡 |
| ブラウザでは開けるがアプリでは失敗 | `local.properties` の URL（`https://`・末尾の `/`）。変更後に Sync とビルドをし直したか |
| 以前は使えた URL が開けない | Quick Tunnel の URL が変わった可能性。チャットで最新の URL を確認する |
| 結果は出るが AI の補足が付かない | `/health` の `llm_enabled`。`true` なら回数制限に達しただけなので少し待つ |
| 追加した手続き・項目が出てこない | その変更が main にマージされ、サーバーに反映済みか |

### サーバー管理者向け

サーバーの構成・設定・構築手順・運用コマンドは [architecture.md のサーバー構成](architecture.md#サーバー構成共有バックエンド) にまとめてある。よく使うもの:

- main を反映: Pi 上で `bash ~/sd_2606/backend/deploy/pi/deploy.sh`
- 公開 URL の確認（変わったらチャットで共有し直す）: `bash ~/sd_2606/backend/deploy/pi/tunnel-url.sh`
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
