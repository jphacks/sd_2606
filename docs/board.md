# 体験談の掲示板

税金の払い方や手続きでのトラブルなど、手続きの体験談を匿名で投稿・閲覧できる機能。公式の案内だけでは分からない「同じ立場の人が実際どうだったか」を補う。

## 画面

アプリの下のタブで「手続き」と「体験談」を切り替える。タブごとに画面の履歴を持ち、タブを切り替えても元の画面に戻る。

| 場所 | 内容 |
| --- | --- |
| 体験談タブ | 全体の一覧。種類（やり方・体験 / トラブル・失敗）・タグで絞り込み、新着順 / 役に立った順。右下の「体験談を書く」 |
| 手続きの画面の下 | 「みんなの体験談」: 件数・大変さの平均（★）・かかった時間の中央値と、役に立った順の上位3件。「すべて見る」「体験談を書く」 |
| おすすめ一覧のカード | 「みんなの体験談 N件」 |
| 詳細 | 全文、「役に立った」（もう一度押すと取り消し）、通報、自分の投稿なら削除 |
| 投稿 | 種類・タグ（3つまで）・タイトル・本文・大変さ・かかった時間・いつの話か・年代・立場。送る前に「公開される内容」をプレビューで見せる |

手続きの画面から書くと、その手続きに対応するタグが選ばれた状態で開く。

## 個人情報の扱い

- **判定に使う回答は今までどおり送らない**。送るのは、本人が「この内容で公開する」を押した投稿だけ
- 年代・立場は、端末に保存している生年月日・働き方から**初期値を選ぶだけ**。本人が「公開しない」にできる。生年月日そのものは送らず、送るのは「20代前半」のような区分
- アカウントは作らない。投稿の削除用トークンは端末にだけ保存し、サーバーはそのハッシュだけを持つ
- 「役に立った」・通報の重複を防ぐため、インストールごとのランダムな ID（`X-Client-Id`）を送る。回答とは結び付けない
- サーバーは送信元の IP を保存しない（回数制限のためにメモリ上で数えるだけ）
- 投稿の本文は Pi の DB にだけ置き、**リポジトリには入れない**（public なので。削除も確実に反映させるため）

## 荒らし・個人情報の書き込み対策

| 対策 | 内容 |
| --- | --- |
| 個人情報らしい文字列を断る | 電話番号・メールアドレス・12桁の番号（マイナンバーの可能性）・郵便番号、公的機関（`go.jp`・`lg.jp`）以外のリンクがあれば 422 |
| 値の検証 | 種類・タグ・年代・立場・かかった時間は `board.yaml` にある値だけ。未来の月は不可。文字数の上限 |
| 回数制限 | 投稿は送信元ごとに10分で5件（`BOARD_POSTS_PER_10MIN`）、「役に立った」・通報は1分で60回 |
| 通報 | 3件（`BOARD_REPORT_HIDE`）で自動的に非表示 |
| 管理 | Pi で `bash ~/sd_2606/backend/deploy/pi/board-admin.sh list`・`hide <id>`・`show <id>`・`backup <path>` |

## データ

| ファイル | 内容 |
| --- | --- |
| `backend/data/board.yaml` | タグ（と手続きの対応）、種類・年代・立場・かかった時間の選択肢。**アプリは `GET /api/board/meta` で受け取るので、ここを直せばアプリの更新は要らない**。`value` は DB に保存されるので、投稿があるうちは変えない |
| `backend/data/board_samples.yaml` | サンプル投稿。DB に無い `key` だけを起動時に入れる。アプリでは「サンプル」と表示する（実際の投稿に見せかけない） |
| DB（SQLite） | `BOARD_DB_PATH`。Pi では `/var/lib/sd2606/board.db`、ローカルでは `backend/board.db`（コミットしない） |

タグと手続きの対応はタグの側（`procedures`）に書き、手続き YAML には何も書かない。手続きの id が存在することはテストで確認している。

```
posts      (id, kind, title, body, age_band, occupation, happened_ym, difficulty, minutes,
            helpful, reports, hidden, sample_key, delete_token_hash, created_at)
post_tags  (post_id, tag_id)
votes      (post_id, client_id, type)   -- type: helpful / report
```

## API（`/api/board`）

| メソッド | パス | 内容 |
| --- | --- | --- |
| GET | `/meta` | タグと選択肢 |
| GET | `/posts?procedure=&tag=&kind=&sort=new\|helpful&offset=&limit=` | 一覧。`procedure` はその手続きに対応するタグのどれかが付いた投稿 |
| GET | `/posts/{id}` | 詳細（非表示・削除済みは 404） |
| GET | `/summary?procedure=` | 件数・大変さの平均・かかった時間の中央値・種類ごとの件数 |
| GET | `/counts` | 手続きごとの件数 |
| POST | `/posts` | 投稿。返り値の `delete_token` を端末に保存する |
| DELETE | `/posts/{id}` | 削除（ヘッダー `X-Delete-Token`） |
| PUT / DELETE | `/posts/{id}/helpful` | 「役に立った」を付ける / 外す（ヘッダー `X-Client-Id`） |
| POST | `/posts/{id}/report` | 通報（ヘッダー `X-Client-Id`） |

エラーは `{"detail": "日本語のメッセージ"}` で返し、アプリはそのまま表示する。

## コード

| 場所 | 内容 |
| --- | --- |
| `backend/app/routers/board.py` | エンドポイント・入力の検証 |
| `backend/app/board/` | `db.py`（SQLite）・`definitions.py`（YAML の読み込み）・`moderation.py`（個人情報の検出・回数制限）・`schemas.py`・`admin.py`（管理コマンド） |
| `backend/tests/test_board.py` | テスト |
| `android/.../data/BoardModels.kt` | API の型（`app/board/schemas.py` と対応させる） |
| `android/.../data/BoardStore.kt` | 端末に保存するもの（client_id・削除用トークン・押した「役に立った」） |
| `android/.../ui/board/` | ViewModel・一覧・詳細・投稿・手続きの画面の「みんなの体験談」 |
| `android/.../ui/AppNavHost.kt` | 下のタブと画面遷移 |
