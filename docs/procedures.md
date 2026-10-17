# 制度データ（手続き YAML）の書き方

`backend/data/procedures/` に **1手続き1ファイル** で置く。ファイル名は `id` と同じにする（例: `kokuho_enroll.yaml`）。
ファイルが分かれているので、複数人で同時に追加してもコンフリクトしにくい。

```yaml
id: kokuho_enroll                 # ファイル名と同じ。英小文字と _
title: 国民健康保険への加入
category: insurance               # tax | insurance | pension | benefit | registration
summary: 1〜2文の説明
priority: 95                      # 0〜100。大きいほど一覧の上に出る（期限が短く、放置すると困るものを高く）
deadline: 退職日の翌日から14日以内   # 任意
office: 市区町村の役所              # 任意
match:                            # 該当条件。どれか1つの when をすべて満たせば該当
  - reason: 退職して勤務先の健康保険を抜けるため   # 画面に「理由」として出る
    when:
      - {field: life_events, op: contains, value: resign}
  - reason: 自営業・フリーランスで、扶養にも入っていないため
    when:
      - {field: occupation, op: in, value: [self_employed, freelance]}
      - {field: health_insurance, op: not_in, value: [national, dependent]}
steps:                            # 手続きのフロー（アプリでタイムライン表示）
  - title: 資格喪失証明書をもらう
    detail: 説明
    where: 前の勤務先              # 任意
    documents: [健康保険資格喪失証明書]  # 任意
links:
  - {label: 厚生労働省, url: "https://..."}
```

## 条件に使えるもの

`field` は `UserProfile` の項目（[backend/app/schemas.py](../backend/app/schemas.py)）:

| field | 型 | 値 |
| --- | --- | --- |
| `age` | 整数 | 今日時点の年齢（アプリは生年月日から計算する） |
| `age_at_year_end` | 整数 | その年の12月31日時点の年齢（生年月日から計算。所得税の控除など、年末の年齢で決まる税の判定に使う） |
| `occupation` | 文字列のリスト（複数可） | `employee` `part_time` `self_employed` `freelance` `student` `unemployed` `retired` |
| `annual_income` | 整数 | 年収（円） |
| `health_insurance` | 文字列 | `employer` `national` `dependent` `unknown` |
| `has_spouse` | 真偽 | `true` / `false` |
| `dependents` | 整数 | 扶養家族の人数 |
| `life_events` | 文字列のリスト | `new_job` `resign` `marriage` `childbirth` `move` `start_business` `side_job` `turn_20` `high_medical_expense` |

`op`: `eq` `ne` `in` `not_in` `contains`（リストに含む） `gte` `gt` `lte` `lt`

`occupation` と `life_events` は複数選択なので、`eq` / `in` は「選んだどれかが当てはまる」、`ne` / `not_in` は「選んだどれも当てはまらない」の意味になる。
たとえば「フリーランスで、会社員ではない」は次のように書く（会社員とフリーランスを兼ねる人を除く）。

```yaml
when:
  - {field: occupation, op: in, value: [freelance]}
  - {field: occupation, op: ne, value: employee}
```

## アプリでの判定と質問

判定はアプリの中で行う（ユーザーの回答はサーバーに送らない）。最初に聞くのはライフイベントと働き方だけで、
条件に使われているほかの項目は、**その項目しだいで手続きが該当するかどうか決まるとき**にだけアプリが聞く。

- 未回答の項目を含む `when` は「まだ分からない」になり、その手続きは一覧の「答えると分かる手続き」に出る
- 同じ `when` に偽の条件が1つでもあれば、ほかが未回答でも対象外になる（例: 退職していなければ生年月日は聞かない）
- `age` / `age_at_year_end` を条件に使うと、アプリは生年月日を聞く（保存するのは生年月日だけで、年齢は判定のたびに計算する）
- `annual_income` などの数値の項目は、**条件の境目から選択肢が自動で作られる**。`{field: annual_income, op: gt, value: 20000000}` だけなら「2,000万円以下 / 2,000万円超」。新しい境目を使うと選択肢が増えるので、必要以上に細かい境目を作らない

## 質問の定義（`backend/data/questions.yaml`）

条件に使う項目は、すべてここに聞き方を書く（無いとテストが失敗する）。

```yaml
- field: occupation          # UserProfile の項目名
  kind: multi                # single（1つ選ぶ）/ multi（複数選ぶ）/ range（数値の範囲を選ぶ）/ date（日付）
  initial: true              # 最初の画面で聞くか。おすすめが大きく変わる項目だけ true にする
  text: いまの働き方は？（複数選択可）
  choices:                   # single / multi は選択肢を書く（value は schemas.py の Literal の値）
    - {value: employee, label: 会社員・公務員}
  # none_if_empty: true      # multi で何も選ばなかったとき「どれも当てはまらない」とする（life_events で使用）。
                             # 書かなければ未回答として、判定に必要なときにもう一度聞く

- field: birth_date
  kind: date
  text: 生年月日は？
  provides: [age, age_at_year_end]   # この日付からアプリが計算する項目（計算方法は RuleEngine.kt）

- field: annual_income
  kind: range                # range は choices を書かない（条件の境目から自動で作る）
  text: 年収（税込み）は？
  unit: 万円                 # 選択肢のラベルに付ける単位
  scale: 10000               # 表示の単位（円 → 万円）
```

## 追加したら

```sh
cd backend
.venv/Scripts/python -m pytest -q    # Windows（Mac/Linux は .venv/bin/python）
```

`test_all_procedure_files_load` が YAML の書式・`field`・`op` の誤りを、`test_catalog.py` が質問の定義の漏れや選択肢の誤りを検出する。

## 内容の正確さ

制度の内容は公的機関のページ（国税庁・日本年金機構・厚生労働省・自治体）で確認し、`links` に出典を入れる。断定できない条件は `detail` に「詳しくは窓口で確認」と書く。
