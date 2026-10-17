# 制度データ（手続き YAML）の書き方

`backend/data/procedures/` に **1手続き1ファイル** で置く。ファイル名は `id` と同じにする（例: `kokuho_enroll.yaml`）。
ファイルが分かれているので、複数人で同時に追加してもコンフリクトしにくい。どの制度を扱うか・作る順番は [procedure-list.md](procedure-list.md)。

```yaml
id: kokumin_nenkin                # ファイル名と同じ。英小文字と _
title: 国民年金（第1号被保険者）の手続き
category: pension                 # tax | insurance | pension | benefit | registration
summary: 1〜2文の説明
deadline: 会社を辞めたときは14日以内  # 任意。画面に出す期限の説明
due: {days: 14}                   # 任意。期限順の並べ替えに使う（下の「期限」）
office: 市区町村の役所              # 任意
relations:                        # きっかけの回答ごとの関係（下の「関係の書き方」）
  - field: age_at_year_end
    value: 20
    flag: conditional
    score: 1.0
    reason: 今年20歳になり、国民年金に加入するため
    when:                         # conditional（条件次第）の条件。すべて満たせば必須
      - {field: occupation, op: ne, value: employee}
  - {field: occupation, value: part_time, flag: conditional, score: 0.4, reason: パート・アルバイトで厚生年金に入っていない20〜59歳は加入するため}
  - {field: occupation, value: employee, flag: unrelated, score: -1.0, reason: 会社員・公務員は厚生年金に入るため}
steps:                            # 手続きのフロー（アプリでタイムライン表示）
  - title: 書類をそろえる
    detail: 説明
    where: 市区町村の役所          # 任意
    documents: [離職票]            # 任意。アプリは全ステップ分を「必要なもの」にまとめる
    details:                      # 任意。詳しい手順の箇条書き（アプリでは「詳しい手順」を押すと開く）
      - 窓口では「国民年金被保険者関係届書（申出書）」に書きます
    when:                         # 任意。この説明が当てはまる人（下の「説明を人に合わせる」）
      - {field: occupation, value: unemployed}
reminders:                        # 任意。期限の前の通知（アプリの「通知と履歴」で本人がオンにしたものだけ）
  - {label: 確定申告の期限, month: 3, day: 15}          # 毎年 3/15。before_days（何日前に通知するか）を省くと7日前
  - {label: 国民年金保険料の納付期限（毎月末）, day: 31}  # month を省くと毎月。その月に無い日は月末
links:
  - {label: 日本年金機構, url: "https://..."}
```

## 関係の書き方（`relations`）

1行ごとに「きっかけ（`field` / `op` / `value`。例: 働き方が学生、12月31日時点で20歳）」と、そのときの **状態・点数・理由** を書く。`op` を省くと `eq`。きっかけには、最初に聞く **年齢（生年月日から計算）と働き方** を主に使う。

| `flag` | 意味 | `score` | `reason` | `when` |
| --- | --- | --- | --- | --- |
| `required`（必須） | このきっかけなら必ず必要 | 正（0〜1） | 必要 | 書かない |
| `conditional`（条件次第） | `when` の条件をすべて満たせば必須。満たさない条件があれば、この行は数えない。`when` が無ければ点数だけ | `when` があれば正。無ければ -1〜1（負は関係を薄める） | 正の点数なら必要 | 任意 |
| `unrelated`（対象外） | このきっかけなら手続きは対象外（必須の行があれば必須が優先）。`when` があれば、すべて満たしたときだけ | 0 以下（-1〜0） | 任意（対象外の理由として出す） | 任意 |

アプリは手続きごとに次のように分ける（`backend/app/rules/engine.py` と `android/.../data/RuleEngine.kt` が同じ意味で判定する）。

1. きっかけに当てはまる行だけを数える
2. `required` の行か、条件をすべて満たした `conditional` の行があれば **「必要な手続き」**
3. そうでなく、`unrelated` の行が当てはまれば（`when` があればすべて満たせば） **「対象外の手続き」**（閉じたグループ。押すと開く）。例: 19〜22歳で年収136万円以下なら「親の扶養から外れる」は対象外
4. それ以外は **「関係がありそうな手続き」**。しきい値（`engine.py` の `THRESHOLD`）はいま -inf で、対象外でなければすべて出す。数値にすると、点数の合計がそれ未満のものは対象外のグループに回る
5. 並びは、関連度順（点数の合計が高い順）か期限順（`due` の近い順）をユーザーが選ぶ。理由は数えた行の `reason` を点数の高い順に出す

点数の目安:

- **1.0**: そのきっかけの代表的な手続き（今年20歳 → 国民年金、会社員 → 年末調整）
- **0.5〜0.8**: 関係が強い（無職 → 国保・任意継続）
- **0.2〜0.3**: 年齢などから見た傾向（20代前半 → 親の扶養、20代 → 引越し）。ほかの行と足し合わせて並び順を決める
- **負（-0.3〜-0.8）**: 対象外ではないが関係が薄い（30歳以上 → 就職1〜2年目の住民税）。`when` の無い `conditional` に書く
- **対象外**: 法律上その人には関係しない（会社員なら国民年金・国保は不要）は `unrelated` に、理由を添えて書く

### 未回答の項目と「判定には〇〇が必要です」

最初の画面で必ず答えてもらうのは、基本情報の **働き方・生年月日・扶養の状況・年収**（`questions.yaml` の `required: true`）。ほかの項目（健康保険・就職前の健康保険・扶養している家族の人数）は任意で、最初の画面の「もっと詳しく（任意）」か、必要になった手続きのカードで聞く。

- 答えると判定（必要・対象外）が変わりうる項目があると、カードに **「判定には（項目名）が必要です」** と案内し、その場で入力できる。入れた値は端末に保存し、ほかの手続きの判定にも使う
  - `conditional` の `when` に未回答の項目がある（その行は点数だけ数える）
  - `required` の行や、`when` のある `conditional` の行のきっかけが未回答（例: 扶養している家族の人数 → 扶養控除）
  - `unrelated` の行のきっかけが未回答（例: 加入している健康保険 → 国保への加入）
- きっかけが未回答の行は数えない
- `age` / `age_at_year_end` を使うと、アプリは生年月日を聞く（保存するのは生年月日だけで、年齢は判定のたびに計算する）

## 期限（`due`）

期限順に並べるための値。`deadline` は画面に出す説明で、`due` は並べ替えだけに使う。期限の決まっていない手続きは書かない（最後に並ぶ）。

| 書き方 | 意味 | 例 |
| --- | --- | --- |
| `due: {days: 14}` | きっかけから何日以内（並べ替えでは今日をきっかけの日とみなす） | 国保への加入（14日以内） |
| `due: {month: 3, day: 15}` | 毎年の期限（今日以降で最初に来るその日） | 確定申告 |
| `due: {month: 6}` | 毎年その月の末日 | 住民税の納付 |

## 文章の書き方（読む量を減らす）

- `title` は **やること**（「〜を出す」「〜を届け出る」「〜を親に伝える」）。状況の説明（「〜の見込み」「〜になります」）はステップにしない
- `detail` は1文（いつ・どこで・どうやって）。なぜ必要か・払わないとどうなるかは書かない
- 書類の書き方・オンラインでの出し方など、詳しい手順が要るものは `details` に箇条書きで書く（最初は閉じて表示する）。1項目は1文で、`項目: 説明` のようにコロンと空白を続けて書かない（YAML の対応表として読まれる）
- `summary` は1文で「何の手続きか」。`reason` は「〜ため」の短い1文
- 年齢・働き方・年収で分かれる内容は、条件の違うステップに分けて書き、文章に「（19〜22歳）」「20〜59歳は」のような但し書きを入れない（アプリは当てはまるステップだけを出すので、本人には不要）
- 「住民税がかかる見込み」のように、将来の日付に起きることは、いまは書かない（期限の日付に通知する仕組みを作るときに扱う）

## 説明を人に合わせる（ステップの `when`）

ステップに `when` を書くと、その条件を満たさない人には表示しない（満たさない条件が1つでもあれば非表示）。未回答で分からない条件だけなら「当てはまる場合のみ」と添えて表示する。年齢や働き方で説明が変わるときは、条件の違うステップを並べて書く。

```yaml
steps:
  - title: 収入が150万円未満か確かめる
    detail: これから1年間の収入で判断します。
    when:
      - {field: age_at_year_end, op: in, value: [19, 20, 21, 22]}
  - title: 収入が130万円未満か確かめる
    detail: これから1年間の収入で判断します。
    when:
      - {field: age_at_year_end, op: not_in, value: [19, 20, 21, 22]}
```

「必要なもの」の一覧は、表示するステップの `documents` をまとめて作る（同じものは1つ）。当てはまるか分からないステップだけで使うものには「（当てはまる場合）」と添える。

`title` / `detail` に `{annual_income}` のように項目名を書くと、アプリがその人の回答に置き換える（「年収（{annual_income}）を親に伝える」→「年収（140万円）を親に伝える」）。使えるのは `questions.yaml` の項目と、生年月日から計算する `age`・`age_at_year_end`（「20歳」）。未回答なら「（年収は未入力）」になる。置き換えは端末の中で行い、回答は送らない。

## 条件に使えるもの

`field` は `UserProfile` の項目（[backend/app/schemas.py](../backend/app/schemas.py)）:

| field | 型 | 値 |
| --- | --- | --- |
| `occupation` | 文字列のリスト（複数可） | `employee` `part_time` `self_employed` `freelance` `student` `unemployed` `retired` |
| `age` | 整数 | 今日時点の年齢（アプリは生年月日から計算する） |
| `age_at_year_end` | 整数 | その年の12月31日時点の年齢（生年月日から計算。所得税の控除や、19〜22歳の扶養の基準など、年末の年齢で決まる判定に使う） |
| `supported_by` | 文字列 | 誰の扶養に入っているか: `parent` `spouse` `none` `unknown` |
| `health_insurance` | 文字列 | いま入っている健康保険: `employer` `national` `dependent` `unknown` |
| `prev_health_insurance` | 文字列 | 就職する前に入っていた健康保険（値は `health_insurance` と同じ） |
| `annual_income` | 整数 | 年収（円。アプリでは万円で入力） |
| `dependents` | 整数 | 扶養している家族の人数 |

`op`: `eq` `ne` `in` `not_in` `contains`（リストに含む） `gte` `gt` `lte` `lt`

`occupation` は複数選択なので、`eq` / `in` は「選んだどれかが当てはまる」、`ne` / `not_in` は「選んだどれも当てはまらない」の意味になる。
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
  required: true             # 最初の画面で必ず答えてもらう基本情報か。多くの手続きの判定が変わる項目だけ true にする。
                             # single で必須にするなら「わからない」（unknown）の選択肢を入れる（テストで確認）
  text: いまの働き方は？（複数選択可）
  label: 働き方               # 短い名前。「判定には働き方が必要です」の案内に使う
  choices:                   # single / multi は選択肢を書く（value は schemas.py の Literal の値）
    - {value: employee, label: 会社員・公務員}
  # none_if_empty: true      # multi で何も選ばなかったとき「どれも当てはまらない」とする。
                             # 書かなければ未回答として、判定に必要なときにもう一度聞く

- field: birth_date
  kind: date
  required: true
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
