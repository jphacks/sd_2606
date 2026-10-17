# 構成

```
┌──────────────────────┐   HTTPS (JSON)   ┌───────────────────────────────┐
│ Android アプリ        │ ───────────────▶ │ FastAPI (backend/)            │
│ Kotlin + Compose      │                  │  ├ rules/  YAML の制度データ   │
│  プロフィール入力       │ ◀─────────────── │  │         でルール判定        │
│  → おすすめ一覧        │                  │  └ llm/    Claude で個別の補足 │
│  → 手続きフロー         │                  └───────────────┬───────────────┘
└──────────────────────┘                                  │
                                                          ▼
                                                 Claude API（任意）
```

- **判定はルール、説明は LLM**: 「どの手続きが該当するか」は `backend/data/procedures/*.yaml` の条件で決める（毎回同じ結果になり、根拠を説明できる）。Claude は該当した手続きについて、その人向けの補足文と全体のアドバイスを書くだけ。API キーが無い・失敗したときはルール判定の結果だけを返す
- **API キーはサーバー側だけ**: APK に入れると抜き取られるため、Claude の呼び出しはバックエンドで行う

## ディレクトリ

```
sd_2606/
├── android/                         Android アプリ（Android Studio でこのフォルダを開く）
│   └── app/src/main/java/jp/jphacks/sd2606/
│       ├── MainActivity.kt
│       ├── data/                    API 通信・データ型
│       │   ├── Models.kt            API の入出力（backend/app/schemas.py と対応）
│       │   ├── Labels.kt            選択肢の値 → 日本語の表示名
│       │   ├── ApiClient.kt         Retrofit
│       │   └── SampleData.kt        Preview 用ダミー
│       └── ui/
│           ├── AppNavHost.kt        画面遷移
│           ├── AppViewModel.kt      画面をまたぐ状態
│           ├── Common.kt            共通部品
│           ├── profile/             ① プロフィール入力
│           ├── recommend/           ② おすすめ手続き一覧
│           ├── flow/                ③ 手続きフロー（タイムライン）
│           └── theme/
├── backend/                         FastAPI
│   ├── app/
│   │   ├── main.py
│   │   ├── schemas.py               API の入出力
│   │   ├── routers/api.py           エンドポイント
│   │   ├── rules/engine.py          ルールエンジン
│   │   └── llm/explain.py           Claude による補足生成
│   ├── data/procedures/*.yaml       制度データ（1手続き1ファイル）→ docs/procedures.md
│   ├── tests/
│   └── deploy/pi/                   Raspberry Pi へのデプロイ設定（→ サーバー構成）
├── docs/
└── .github/workflows/ci.yml         backend のテストと APK ビルド
```

## API

| メソッド | パス | 内容 |
| --- | --- | --- |
| GET | `/health` | 稼働確認 |
| POST | `/api/recommendations` | プロフィール（`UserProfile`）→ 該当する手続きを優先度順に返す。LLM が使えれば `personal_note` と `overall_advice` が付く |
| GET | `/api/procedures` | 全手続きの一覧 |
| GET | `/api/procedures/{id}` | 手続きの詳細とフロー（`steps`） |

スキーマの詳細はバックエンド起動中に `http://localhost:8000/docs`（Swagger UI）で見られる。

リクエスト例:

```json
POST /api/recommendations
{
  "age": 28,
  "occupation": "unemployed",
  "annual_income": 0,
  "health_insurance": "unknown",
  "has_spouse": false,
  "dependents": 0,
  "life_events": ["resign"]
}
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
- API に認証は無い。そのため、本物の個人情報を受け取らない設計にしている（年齢・職業などの選択項目だけ）
- Claude の API キーは Pi の `.env` にだけ置き、アプリにもリポジトリにも入れない。呼び出し回数はサーバー全体で制限する
- サービスは一般ユーザー権限で動かし、systemd のサンドボックスでシステムやホームディレクトリへの書き込みを禁止している

### 制約

- **Quick Tunnel の URL は `sd2606-tunnel` が起動するたびに変わる**（Pi の再起動・サービスの再起動・回線の切断後の再接続など）。変わったらチームに共有し直す
- Quick Tunnel は Cloudflare のテスト用機能で、稼働の保証は無い（同時リクエスト200件まで）
- 家庭用の回線と電源で動いているので、停電・回線の不調で止まることがある。デモの前には `/health` を確認する
- 固定 URL の経路として Tailscale Funnel（ポート 8443）も設定済みだが、Tailscale 側で公開 DNS が登録されず、2026-10-17 時点では外部から届かない

## フィールドを増やすとき

1. `backend/app/schemas.py` の `UserProfile`（選択肢なら `Literal`）に追加
2. `android/.../data/Models.kt` の `UserProfile` に追加（JSON 名は `@SerialName` で snake_case に）
3. 選択肢なら `android/.../data/Labels.kt` に表示名を追加
4. 入力欄を `ui/profile/ProfileScreen.kt` に追加
5. YAML の条件で使う
