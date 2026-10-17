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
| `age` | 整数 | 年齢 |
| `occupation` | 文字列 | `employee` `part_time` `self_employed` `freelance` `student` `unemployed` `retired` |
| `annual_income` | 整数 | 年収（円） |
| `health_insurance` | 文字列 | `employer` `national` `dependent` `unknown` |
| `has_spouse` | 真偽 | `true` / `false` |
| `dependents` | 整数 | 扶養家族の人数 |
| `life_events` | 文字列のリスト | `new_job` `resign` `marriage` `childbirth` `move` `start_business` `side_job` `turn_20` `high_medical_expense` |

`op`: `eq` `ne` `in` `not_in` `contains`（リストに含む） `gte` `gt` `lte` `lt`

## 追加したら

```sh
cd backend
.venv/Scripts/python -m pytest -q    # Windows（Mac/Linux は .venv/bin/python）
```

`test_all_procedure_files_load` が YAML の書式・`field`・`op` の誤りを検出する。

## 内容の正確さ

制度の内容は公的機関のページ（国税庁・日本年金機構・厚生労働省・自治体）で確認し、`links` に出典を入れる。断定できない条件は `detail` に「詳しくは窓口で確認」と書く。
