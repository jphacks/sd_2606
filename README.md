# 税人

[![IMAGE ALT TEXT HERE](https://jphacks.com/wp-content/uploads/2026/07/IMG_5316.png)](https://www.youtube.com/watch?v=piaNsc6ilBI)

> 退職・開業・出産・引越し……人生の節目に必要な税金・保険・年金の手続きを、あなたの状況から洗い出し、やる順番と流れを見える化する Android アプリ。

## 製品概要
### 背景(製品開発のきっかけ、課題等）
国民健康保険・国民年金・所得税・住民税など、個人に関わる手続きは制度ごとに窓口も期限もばらばらで、「自分に何が関係あるのか」を調べるところから難しい。退職後14日以内の保険・年金の切り替えや、学生納付特例の申請漏れなど、知らないまま期限を過ぎて損をするケースも多い。

### 製品説明（具体的な製品の説明）
年齢・職業・年収・家族構成・最近のライフイベントを入力すると、関係しそうな手続きを優先度順に表示する。各手続きは「何を・どこで・いつ・何を持って」行うかをステップごとのフローで表示する。

### 特長
#### 1. 状況を選ぶだけで、関係する手続きを洗い出す
#### 2. 手続きの流れをタイムラインで可視化
#### 3. 判定はルール、説明は AI ── 根拠を示しつつ、その人向けのアドバイスを付ける

### 解決出来ること
### 今後の展望
### 注力したこと（こだわり等）
*
*

## 開発技術
### 活用した技術
#### API・データ
* Claude API（手続きごとの個別アドバイス生成）
* 国税庁・日本年金機構・厚生労働省・こども家庭庁の公開情報をもとにした制度データ

#### フレームワーク・ライブラリ・モジュール
* Android: Kotlin / Jetpack Compose / Material 3 / Retrofit / kotlinx.serialization
* バックエンド: Python / FastAPI / Pydantic / Anthropic Python SDK

#### デバイス
* Android スマートフォン
* Raspberry Pi 4（バックエンドのホスティング）

### 独自技術
#### ハッカソンで開発した独自機能・技術
* 独自で開発したものの内容をこちらに記載してください
* 特に力を入れた部分をファイルリンク、またはcommit_idを記載してください。

---

## 開発者向け

- [docs/development.md](docs/development.md) — 環境構築・起動方法・ブランチ運用
- [docs/architecture.md](docs/architecture.md) — 構成・ディレクトリ・API
- [docs/procedures.md](docs/procedures.md) — 制度データ（YAML）の追加方法

```
android/   Android アプリ（Kotlin + Jetpack Compose）
backend/   API サーバー（FastAPI）と制度データ
docs/      開発ドキュメント
```
