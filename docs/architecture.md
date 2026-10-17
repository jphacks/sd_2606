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
│   └── deploy/pi/                   Raspberry Pi へのデプロイ設定
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

## フィールドを増やすとき

1. `backend/app/schemas.py` の `UserProfile`（選択肢なら `Literal`）に追加
2. `android/.../data/Models.kt` の `UserProfile` に追加（JSON 名は `@SerialName` で snake_case に）
3. 選択肢なら `android/.../data/Labels.kt` に表示名を追加
4. 入力欄を `ui/profile/ProfileScreen.kt` に追加
5. YAML の条件で使う
