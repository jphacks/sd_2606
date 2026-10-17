# 制度データ（手続き YAML）の書き方

`backend/data/procedures/` に **1手続き1ファイル** で置く。ファイル名は `id` と同じにする（例: `kokuho_enroll.yaml`）。
ファイルが分かれているので、複数人で同時に追加してもコンフリクトしにくい。どの制度を扱うか・作る順番は [procedure-list.md](procedure-list.md)。

```yaml
id: kokumin_nenkin                # ファイル名と同じ。英小文字と _
title: 国民年金（第1号被保険者）の手続き
category: pension                 # tax | insurance | pension | benefit | registration
summary: 1〜2文の説明
deadline: 退職したときは14日以内    # 任意
office: 市区町村の役所              # 任意
relations:                        # きっかけの回答ごとの関係（下の「関係の書き方」）
  - {field: life_events, value: turn_20, flag: required, score: 1.0, reason: 20歳になると国民年金に加入するため}
  - field: life_events
    value: resign
    flag: conditional
    score: 0.8
    reason: 退職して厚生年金から外れる20〜59歳は、国民年金に切り替えるため
    when:                         # conditional（条件次第）の条件
      - {field: age, op: gte, value: 20}
      - {field: age, op: lt, value: 60}
      - {field: occupation, op: ne, value: employee}
  - {field: occupation, value: employee, flag: unrelated, score: -1.0}   # 会社員なら厚生年金
steps:                            # 手続きのフロー（アプリでタイムライン表示）
  - title: 書類をそろえる
    detail: 説明
    where: 市区町村の役所          # 任意
    documents: [離職票]            # 任意
links:
  - {label: 日本年金機構, url: "https://..."}
```

## 関係の書き方（`relations`）

1行ごとに「きっかけ（`field` / `op` / `value`。例: ライフイベントに退職がある）」と、そのときの **状態・点数・理由** を書く。`op` を省くと `eq`。

| `flag` | 意味 | `score` | `reason` | `when` |
| --- | --- | --- | --- | --- |
| `required`（必須） | このきっかけなら必ず必要 | 正（0〜1） | 必要 | 書かない |
| `conditional`（条件次第） | `when` の条件をすべて満たせば必須。満たさない条件があれば、この行は数えない | 正（0〜1） | 必要 | 任意（書かなければ必須にはならない） |
| `unrelated`（無関係） | 必須にはならない。点数を下げるのに使う | 0 以下（-1〜0） | 書かない | 書かない |

アプリは手続きごとに次のように分ける（`backend/app/rules/engine.py` と `android/.../data/RuleEngine.kt` が同じ意味で判定する）。

1. きっかけに当てはまる行だけを数える
2. `required` の行か、条件をすべて満たした `conditional` の行があれば **「必要な手続き」**
3. そうでなければ、数えた行の点数の合計が **しきい値（0.5。`engine.py` の `THRESHOLD`）以上なら「関係がありそうな手続き」**
4. どちらの一覧も点数の合計が高い順に並ぶ。理由は数えた行の `reason` を点数の高い順に出す

点数の目安:

- **1.0**: そのきっかけの代表的な手続き（20歳 → 国民年金、退職 → 国保）
- **0.5〜0.8**: 関係が強い。単独で「関係がありそう」に出る
- **0.3 前後**: 単独では出さないが、ほかのきっかけと合わさると出る（学生 ＋ アルバイトを増やした → 住民税）
- **-1.0**: 当てはまると関係が無くなる（会社員なら国保は不要）。法律上の対象外は `conditional` の `when` で表すと、点数にも数えなくなる

### 未回答の項目と「判定には〇〇が必要です」

最初に聞くのはライフイベントと働き方だけ。ほかの項目は、必要になったときにアプリが聞く。

- `conditional` の `when` に未回答の項目があると、その行は点数だけ数えて「関係がありそう」に出し、カードに **「判定には（項目名）が必要です」** と案内する。答えると必須になるか、行が数えられなくなる
- きっかけ（`field`）が未回答の行は数えない。きっかけには、最初に聞く項目か、ほかの手続きの案内で聞かれる項目を使う
- `age` / `age_at_year_end` を使うと、アプリは生年月日を聞く（保存するのは生年月日だけで、年齢は判定のたびに計算する）

## 条件に使えるもの

`field` は `UserProfile` の項目（[backend/app/schemas.py](../backend/app/schemas.py)）:

| field | 型 | 値 |
| --- | --- | --- |
| `life_events` | 文字列のリスト | `new_job` `turn_20` `resign` `part_time_more`（アルバイトを始めた・増やした） `move` `marriage` `childbirth` `side_job` `start_business` `high_medical_expense` |
| `occupation` | 文字列のリスト（複数可） | `employee` `part_time` `self_employed` `freelance` `student` `unemployed` `retired` |
| `age` | 整数 | 今日時点の年齢（アプリは生年月日から計算する） |
| `age_at_year_end` | 整数 | その年の12月31日時点の年齢（生年月日から計算。所得税の控除や、19〜22歳の扶養の基準など、年末の年齢で決まる判定に使う） |
| `supported_by` | 文字列 | 誰の扶養に入っているか: `parent` `spouse` `none` `unknown` |
| `health_insurance` | 文字列 | いま入っている健康保険: `employer` `national` `dependent` `unknown` |
| `prev_health_insurance` | 文字列 | 就職する前に入っていた健康保険（値は `health_insurance` と同じ） |
| `annual_income` | 整数 | 年収（円。アプリでは万円で入力） |
| `dependents` | 整数 | 扶養している家族の人数 |

`op`: `eq` `ne` `in` `not_in` `contains`（リストに含む） `gte` `gt` `lte` `lt`

`occupation` と `life_events` は複数選択なので、`eq` / `in` は「選んだどれかが当てはまる」、`ne` / `not_in` は「選んだどれも当てはまらない」の意味になる。
たとえば「フリーランスで、会社員ではない」は、`when` に次のように書く（会社員とフリーランスを兼ねる人を除く）。

```yaml
when:
  - {field: occupation, op: in, value: [freelance]}
  - {field: occupation, op: ne, value: employee}
```

## 質問の定義（`backend/data/questions.yaml`）

条件に使う項目は、すべてここに聞き方を書く（無いとテストが失敗する）。

```yaml
- field: occupation          # UserProfile の項目名
  kind: multi                # single / multi / number（数値を入力）/ date（日付）/ range（数値の範囲を選ぶ）
  initial: true              # 最初の画面で聞くか。おすすめが大きく変わる項目だけ true にする
  text: いまの働き方は？（複数選択可）
  label: 働き方               # 短い名前。「判定には働き方が必要です」の案内に使う
  choices:                   # single / multi は選択肢を書く（value は schemas.py の Literal の値）
    - {value: employee, label: 会社員・公務員}
  # none_if_empty: true      # multi で何も選ばなかったとき「どれも当てはまらない」とする（life_events で使用）。
                             # 書かなければ未回答として、判定に必要なときにもう一度聞く

- field: birth_date
  kind: date
  text: 生年月日は？
  label: 生年月日
  provides: [age, age_at_year_end]   # この日付からアプリが計算する項目（計算方法は RuleEngine.kt）

- field: annual_income
  kind: number
  text: 年収（税込み）は？
  label: 年収
  unit: 万円                 # 入力欄の単位
  scale: 10000               # 入力の単位（万円で入れて、円で保存・判定する）
```

`kind: range` にすると、選択肢を書かずに、手続き YAML の条件の境目から自動で作る（判定に必要な細かさでしか聞かない）。いまは使っていない。

## 追加したら

```sh
cd backend
.venv/Scripts/python -m pytest -q    # Windows（Mac/Linux は .venv/bin/python）
```

`test_all_procedure_files_load` が YAML の書式・`field`・`op`・`flag` と点数の組み合わせの誤りを、`test_catalog.py` が質問の定義の漏れや選択肢の誤りを検出する。

## 内容の正確さ

制度の内容は公的機関のページ（国税庁・日本年金機構・厚生労働省・協会けんぽ・自治体）で確認し、`links` に出典を入れる。年収の基準は2025〜2026年に何度も変わっているので、令和8年分（2026年の収入）の値を使う（国税庁「源泉所得税の改正のあらまし（令和8年4月）」）。断定できない条件は `detail` に「詳しくは窓口で確認」と書く。
