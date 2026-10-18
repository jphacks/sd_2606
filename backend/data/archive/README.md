# 配信していない手続き

アプリの対象を学生に絞ったとき（2026-10-18）に、学生にはほとんど関係しない手続きをここに移した。
`backend/data/procedures/` にある YAML だけが読み込まれ、アプリに配信される。戻すときは `procedures/` に移し、
学生向けの質問（`part_time` など）に合わせて条件を書き直すこと。

- `gakusei_tokurei.yaml` は、国民年金（`kokumin_nenkin.yaml`）のフローの選択肢「学生納付特例を使う」に統合した
