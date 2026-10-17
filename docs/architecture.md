# 構成

```
┌─ Android アプリ（Kotlin + Compose）──────┐                  ┌─ FastAPI (backend/) ──────────────┐
│ ① 最初の質問（2問だけ）                    │  GET /api/catalog │ data/procedures/*.yaml  制度データ │
│ ② 必要な手続き ／ 関係がありそうな手続き    │ ◀──────────────── │ data/questions.yaml     質問の定義 │
│ ③ 手続きフロー                             │  制度データと     │ rules/questions.py      質問を     │
│                                            │  判定ルールだけ   │                         配る形に   │
│ RuleEngine  端末内で判定（3状態＋点数）     │                  │                                    │
│ LocalStore  回答は端末の中だけに保存        │ ──────╳─────────▶ └────────────────────────────────────┘
└────────────────────────────────────────────┘  回答は送らない
```

- **ユーザーの情報は送らない**: アプリがサーバーから受け取るのは、制度データ（手続きの説明・フロー・判定ルール）と質問の定義だけ。判定はアプリの中（`data/RuleEngine.kt`）で行い、回答は端末の中（`data/LocalStore.kt`）にだけ保存する。サーバーのログにも、誰が何に当てはまるかは残らない
- **基本情報は必須、残りは任意**: 最初の画面では、多くの手続きの判定が変わる基本情報（`questions.yaml` の `required: true`。働き方・生年月日・扶養の状況・年収）に必ず答えてもらう（答えるまで「調べる」を押せない。扶養は「わからない」も選べる）。それ以外の項目は任意で、最初の画面の「もっと詳しく（任意）」か、ある手続きが「その項目しだいで必要かどうか決まる」ときに、そのカードに「判定には扶養している家族の人数が必要です」と案内して、その場で聞く
- **読む量を減らす**: ステップは「〜を出す」「〜を伝える」のようなやることだけにし、説明は1文にする。年収の壁では、本人の年収で家族がする届出（親の控除の切り替え・健康保険の扶養から外れる）のステップだけを出し、基準より低ければ「〜万円を超えそうなら家族に伝える」だけにする。ステップの説明の `{annual_income}` などは、端末の中で回答（「140万円」「20歳」）に置き換えられる
- **年齢と働き方で関係の強さを決め、3状態＋点数で分ける**: 手続きごとに、きっかけの回答（学生・20歳以上など）1つずつについて、状態（必須・条件次第・対象外）と関係の点数（-1〜1）を YAML に書く。必須になったものは「必要な手続き」、対象外になったものは閉じたグループ「対象外の手続き」（押すと開く）、それ以外はすべて「関係がありそうな手続き」に出す（しきい値は -inf。機能は残してあり、`engine.py` の `THRESHOLD` を数値にすると、点数がそれ未満のものも対象外のグループに回る）。並びは関連度順（点数の高い順）か期限順（YAML の `due`）を選べる（書き方は [procedures.md](procedures.md)）
- **説明はその人に当てはまるものだけ**: 手続きのステップに条件（`when`。例: 19〜22歳、会社員）を付けられる。当てはまらないステップは表示せず、未回答で分からないものは「当てはまる場合のみ」と添えて表示する。必要なもの（持ち物・書類）は、表示するステップの `documents` をまとめて一覧にする
- **終わった手続きは完了として記録する**: 手続きの画面や一覧のカードで「完了にする」と、完了した日を端末に保存し、一覧では「完了した手続き」のグループに移す（元に戻せる）
- **未回答は「まだ分からない」として判定する**: 条件ごとに 真・偽・不明 の3値で判定する。条件次第の条件が不明なら、点数だけ数えて「関係がありそう」に出し、不明の原因になった項目だけを質問として出す
- **年収と扶養家族の人数は数値で聞く**（年収は万円で入力し、円で保存）。入力の途中で判定が動かないよう、「決定」を押したときに反映する
- **年齢は生年月日から計算する**: 生年月日を聞いて端末に保存し、判定のたびに `age`（今日時点の年齢）と `age_at_year_end`（その年の12月31日時点の年齢。所得税の控除などはこの年齢で決まる）を計算する。手続き YAML ではこの2つを条件に使える
- **働き方は複数選べる**（例: 会社員とフリーランスの副業、学生とアルバイト）。複数選択の項目では、`eq` / `in` は「選んだどれかが当てはまる」、`ne` / `not_in` は「選んだどれも当てはまらない」と判定する
- **判定はルール**: 「どの手続きが該当するか」は `backend/data/procedures/*.yaml` の条件で決める（毎回同じ結果になり、根拠を説明できる）。制度が変わったらサーバーのデータを直すだけで、アプリを更新せずに反映できる
- **通信できなくても使える**: アプリは前回受け取った制度データを端末に残し、取得に失敗したときはそれで判定する（画面に注記を出す）
- **LLM（Claude）の補足**: バックエンドには、プロフィールを受け取って Claude で補足文を付ける `POST /api/recommendations` が残っているが、プロフィールを送ることになるため、いまのアプリからは使っていない。API キーはサーバー側だけに置く

## ディレクトリ

```
sd_2606/
├── android/                         Android アプリ（Android Studio でこのフォルダを開く）
│   └── app/src/
│       ├── main/java/jp/jphacks/sd2606/
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   ├── Models.kt        GET /api/catalog の型（backend/app/schemas.py の Catalog と対応）
│       │   │   ├── Answer.kt        回答の型（単一選択・複数選択・範囲・数値・日付）
│       │   │   ├── RuleEngine.kt    端末内の判定（3状態＋点数、未回答は3値）、当てはまるステップの選別、並べ替え、生年月日からの年齢計算
│       │   │   ├── LocalStore.kt    回答・完了の記録・制度データのキャッシュを端末に保存
│       │   │   ├── Labels.kt        分類（税金・保険など）の表示名
│       │   │   ├── ApiClient.kt     Retrofit（制度データの取得だけ）
│       │   │   └── SampleData.kt    Preview 用ダミー
│       │   └── ui/
│       │       ├── AppNavHost.kt    画面遷移（横スライド）
│       │       ├── AppViewModel.kt  制度データ・回答・判定結果
│       │       ├── Questions.kt     質問の入力欄（選択肢・数値・日付）・質問カード・保存している情報のダイアログ（編集・削除）
│       │       ├── Common.kt        共通部品
│       │       ├── profile/         ① 最初の質問
│       │       ├── recommend/       ② おすすめ一覧
│       │       ├── flow/            ③ 手続きフロー（タイムライン）
│       │       └── theme/
│       └── test/                    RuleEngine の単体テスト
├── backend/                         FastAPI
│   ├── app/
│   │   ├── main.py
│   │   ├── schemas.py               API の入出力
│   │   ├── routers/api.py           エンドポイント
│   │   ├── rules/engine.py          制度データの読み込みとルール判定（サーバー側）
│   │   ├── rules/questions.py       質問の定義の読み込みと、range の選択肢の自動生成
│   │   └── llm/explain.py           Claude による補足生成（いまのアプリでは未使用）
│   ├── data/procedures/*.yaml       制度データ（1手続き1ファイル）→ docs/procedures.md
│   ├── data/questions.yaml          質問の定義 → docs/procedures.md
│   ├── tests/
│   └── deploy/pi/                   Raspberry Pi へのデプロイ設定（→ サーバー構成）
├── docs/
└── .github/workflows/ci.yml         backend のテスト、APK のビルドと単体テスト
```

## API

| メソッド | パス | 内容 |
| --- | --- | --- |
| GET | `/health` | 稼働確認 |
| GET | `/api/catalog` | **アプリが使うのはこれだけ**。全手続き（判定条件 `relations` つき）、質問の定義（`questions`）、しきい値（`threshold`）を返す |
| GET | `/api/procedures` | 全手続きの一覧（判定条件なし） |
| GET | `/api/procedures/{id}` | 手続きの詳細とフロー（`steps`） |
| POST | `/api/recommendations` | プロフィール（`UserProfile`）→ 該当する手続き。LLM が使えれば補足文が付く。**アプリからは使っていない**（プロフィールを送ることになるため） |

スキーマの詳細はバックエンド起動中に `http://localhost:8000/docs`（Swagger UI）で見られる。

`GET /api/catalog` の `questions` の例:

```json
[
  {"field": "occupation", "kind": "multi", "required": true, "text": "いまの働き方は？（複数選択可）",
   "choices": [{"label": "会社員・公務員", "value": "employee"}, "..."]},
  {"field": "birth_date", "kind": "date", "required": true, "text": "生年月日は？",
   "provides": ["age", "age_at_year_end"]},
  {"field": "annual_income", "kind": "number", "required": true, "text": "今年（1〜12月）の収入の見込みは？（税込み・だいたいで可。収入がなければ 0）",
   "label": "年収", "unit": "万円", "scale": 10000}
]
```

## サーバー構成（共有バックエンド）

チームの共有バックエンドは、メンバーの自宅の Raspberry Pi で動かしている。使い方と注意事項は [development.md](development.md#共有サーバーraspberry-pi)、ここでは設定を記録する。URL・ホスト名・アカウント名は public リポジトリに書かない。

```
 Android アプリ / ブラウザ
        │ HTTPS
        ▼
 Cloudflare（https://<ランダム>.trycloudflare.com）      ← 現在の公開経路（臨時）
        │ cloudflared が Pi から外向きに張ったトンネル
        ▼
┌─ Raspberry Pi ─────────────────────────────────────────┐
│ sd2606-tunnel  cloudflared tunnel --url 127.0.0.1:8606  │
│ sd2606-api     uvicorn app.main:app  127.0.0.1:8606      │──▶ Claude API
│ （tailscaled   Funnel :8443 → 127.0.0.1:8606  ※準備中） │
└────────────────────────────────────────────────────────┘
```

### 環境

| 項目 | 内容 |
| --- | --- |
| ハードウェア | Raspberry Pi 4 Model B（RAM 4GB） |
| OS | Debian GNU/Linux 13 (trixie)、aarch64 |
| Python | 3.13（システムの python3 で venv を作る） |
| 配置 | 実行ユーザーのホームに clone（`~/sd_2606`）。venv は `backend/.venv` |
| 公開 | Cloudflare Quick Tunnel（`cloudflared` 2026.10.1、GitHub の公式 .deb）。無料・アカウント不要 |

### systemd サービス

設定ファイルは [backend/deploy/pi/](../backend/deploy/pi/) にあり、インストールスクリプトが実行ユーザーとパスを埋めて `/etc/systemd/system/` に置く。

| ユニット | 内容 | 主な設定 |
| --- | --- | --- |
| `sd2606-api` | FastAPI（uvicorn）。**127.0.0.1:8606 だけで待ち受ける**（LAN や外部から直接は繋がらない） | `EnvironmentFile=backend/.env`、`Restart=on-failure`、`MemoryMax=512M`、`ProtectSystem=strict`・`ProtectHome=read-only`・`NoNewPrivileges`（コードやシステムを書き換えられない） |
| `sd2606-tunnel` | `cloudflared tunnel --no-autoupdate --url http://127.0.0.1:8606`。Cloudflare への外向き接続だけを使うので、ルーターやファイアウォールのポート開放は不要 | `After=sd2606-api`、`Restart=on-failure`、同様のサンドボックス設定 |

Pi 4 では API の起動に約5秒かかる。スクリプトは `/health` が応答するまで最大30秒待つ。

### 環境変数（`backend/.env`）

`.env.example` をコピーして作る（`install.sh` が権限 600 で自動作成）。**コミットしない。**

| 変数 | 既定値 | 内容 |
| --- | --- | --- |
| `ANTHROPIC_API_KEY` | 空 | Claude の API キー。空なら LLM を使わずルール判定だけで動く |
| `CLAUDE_MODEL` | `claude-opus-5-5` | 使うモデル |
| `LLM_CALLS_PER_MINUTE` | `20` | サーバー全体で Claude を呼ぶ回数の上限（公開 API の使いすぎ防止）。超えた分はルール判定の結果だけを返す |
| `LLM_TIMEOUT_SEC` | `30` | Claude 呼び出しのタイムアウト |
| `LLM_ENABLED` | `1` | `0` にすると、キーがあっても LLM を使わない |

変更したら `sudo systemctl restart sd2606-api`。

### スクリプト

| ファイル | 用途 |
| --- | --- |
| `install.sh` | 初回: venv 作成・依存のインストール・`.env` 作成・`sd2606-api` の配置と起動 |
| `deploy.sh` | 更新: `origin/main` に合わせて（ローカルの変更は破棄）依存を入れ直し、API を再起動 |
| `install-tunnel.sh` | `sd2606-tunnel` の配置と起動。最後に URL を表示する |
| `tunnel-url.sh` | 現在の Quick Tunnel の URL を表示（journal から読む） |

### 一から構築する手順

```sh
sudo apt install -y git python3-venv
git clone https://github.com/jphacks/sd_2606 ~/sd_2606
bash ~/sd_2606/backend/deploy/pi/install.sh          # API を起動
nano ~/sd_2606/backend/.env                           # ANTHROPIC_API_KEY を設定
sudo systemctl restart sd2606-api

# cloudflared（arm64 の例。最新版は https://github.com/cloudflare/cloudflared/releases ）
curl -fsSLo /tmp/cloudflared.deb https://github.com/cloudflare/cloudflared/releases/download/2026.10.1/cloudflared-linux-arm64.deb
sudo dpkg -i /tmp/cloudflared.deb
bash ~/sd_2606/backend/deploy/pi/install-tunnel.sh   # 公開 URL が表示される
```

### 運用

| やること | コマンド（Pi 上） |
| --- | --- |
| main を反映 | `bash ~/sd_2606/backend/deploy/pi/deploy.sh` |
| 公開 URL の確認 | `bash ~/sd_2606/backend/deploy/pi/tunnel-url.sh` |
| 状態 | `systemctl status sd2606-api sd2606-tunnel` |
| ログ | `journalctl -u sd2606-api -f`（API）、`journalctl -u sd2606-tunnel -f`（トンネル） |
| 公開を止める | `sudo systemctl disable --now sd2606-tunnel` |
| すべて止める | `sudo systemctl disable --now sd2606-tunnel sd2606-api` |

### セキュリティ上の考え方

- 外部に出しているのは API（`sd2606-api`）だけ。API は 127.0.0.1 で待ち受け、トンネルを通してしか届かない
- API に認証は無い。アプリは制度データを受け取るだけで、ユーザーの回答を送らない設計にしている（判定は端末内）
- Claude の API キーは Pi の `.env` にだけ置き、アプリにもリポジトリにも入れない。呼び出し回数はサーバー全体で制限する
- サービスは一般ユーザー権限で動かし、systemd のサンドボックスでシステムやホームディレクトリへの書き込みを禁止している

### 制約

- **Quick Tunnel の URL は `sd2606-tunnel` が起動するたびに変わる**（Pi の再起動・サービスの再起動・回線の切断後の再接続など）。変わったらチームに共有し直す
- Quick Tunnel は Cloudflare のテスト用機能で、稼働の保証は無い（同時リクエスト200件まで）
- 家庭用の回線と電源で動いているので、停電・回線の不調で止まることがある。デモの前には `/health` を確認する
- 固定 URL の経路として Tailscale Funnel（ポート 8443）も設定済みだが、Tailscale 側で公開 DNS が登録されず、2026-10-17 時点では外部から届かない

## 質問する項目を増やすとき

1. `backend/app/schemas.py` の `UserProfile`（選択肢なら `Literal`）に追加
2. `backend/data/questions.yaml` に聞き方を追加（`required: true`（最初の画面で必須）にするのは、多くの手続きの判定が変わる基本情報だけ）
3. 手続き YAML の条件で使う

アプリ側の変更は要らない（質問の文言・選択肢はサーバーから受け取る）。詳しくは [procedures.md](procedures.md)。
