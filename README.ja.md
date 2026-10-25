# OldBiliRetro · 旧版ビリビリ

> **ヴィンテージ Android 端末**向けのサードパーティ製 bilibili クライアント。Android 2.3 から Android 17 まで完全対応し、2013〜2014 年当時の **Holo Dark** デザインを忠実に再現しています。

![minSdk](https://img.shields.io/badge/minSdk-6%20(Android%202.0.1)-green)
![ABI](https://img.shields.io/badge/ABI-armeabi%20%7C%20v7a%20%7C%20arm64%20%7C%20x86%20%7C%20x86__64-blue)
![License](https://img.shields.io/badge/License-GPL--3.0-orange)
![AI](https://img.shields.io/badge/Developed%20by-AI%20100%25-purple)

**言語:** [中文](README.md) · [English](README.en.md) · **日本語**

---

## ⚠️ 重要なお知らせ（最初にお読みください）

1. **本プロジェクトは AI によって完全に開発されています。** ソースコード、アイコン、ドキュメント、ビルドスクリプトはすべて AI（DeepSeek モデル + DeepSeek Harness エージェントフレームワーク）が生成しました。人間は要件定義・実機テスト・最終判断のみを担当しています。
2. **本プロジェクトは bilibili（上海寛娯数碼科技有限公司）と一切関係ありません。** 公式クライアントではなく、公式の承認・提携も受けていません。
3. 動画・弾幕・コメント・アニメなどのコンテンツの**著作権は原作者および bilibili に帰属**します。本アプリは公開 API を利用するサードパーティ製クライアントにすぎません。
4. **商用利用、一括クロール、有料/会員制限の回避、有料コンテンツの再配布**には使用しないでください。
5. サードパーティ製クライアントの利用には、**レート制限・リスクコントロール・最悪の場合アカウント停止**のリスクがあります。自己責任でご利用ください。
6. 正式な法的条項: **[DISCLAIMER.md](DISCLAIMER.md)** ・第三者ライセンス: **[THIRD_PARTY.md](THIRD_PARTY.md)**

---

## 機能

- **ログイン**：QR コード / Cookie / 手動 Cookie インポート / Cookie エクスポート
- **ホーム**：ログイン時は個別おすすめ、未ログイン時は人気。アニメ・映画セクション
- **検索**：動画・アニメ・映画・ライブ・ユーザー。AV/BV 番号で直接ジャンプ。履歴 20 件＋一括削除。ページ送り時にスクロール位置を保持
- **動画詳細**：パート選択、コレクション、関連動画、投稿者ページ、説明文の長押しコピー
- **プレイヤー（自前ビルドの IJKPlayer）**：
  - **横向き固定**
  - 画質リストはサーバーが実際に提供するものだけを表示（360P / 720P / 1080P / 1080P60 / 2K）
  - ストリーム形式：**DASH**（全画質・既定）/ **MP4**（シングルストリーム・安定）
  - ジェスチャー：明るさ / 音量 / シーク / ダブルタップで一時停止。**長押しで 2 倍速**
  - 速度メニュー：0.75x / 1.0x / 1.25x / 1.5x / 2.0x
  - **再生中に弾幕設定を変更可能**（設定画面と同期）
  - 上部：戻る＋タイトル＋いいね / コイン / シェア / フォロー。下部：プログレスバー、再生・弾幕切替・弾幕設定・弾幕送信、右側に速度と画質
- **弾幕**：DanmakuFlameMaster フル版（縁取り・影・衝突判定・ビットマップキャッシュ）
- **コメント**：閲覧 / 返信 / ネスト返信 / いいね / 投稿
- **ユーザーページ**：動画 / ダイナミック / プロフィール。フォロー状態の判定
- **ダウンロード**：パート単位、レジューム対応、画質選択
- **設定**：シークレットモード、**画像なしモード**、**キャッシュ削除**、ストリーム形式、既定画質、**互換性リスト**、利用規約、端末情報、クラッシュログ出力。ログアウト時にメッセージ/ダイナミック/お気に入りのキャッシュも削除

---

## 互換性

### システムと ABI

| ABI | 最低 API | Android | ネイティブプレイヤー | HTTPS |
|---|---|---|---|---|
| armeabi (ARMv5) | **9** | 2.3 ~ 2.3.7 | 自前ビルド | ✅ |
| armeabi-v7a | **9** | 2.3 ~ 4.4 | 自前ビルド | ✅ |
| arm64-v8a | 21 | 5.0 ~ 17 | 自前ビルド | ✅ |
| x86 | **9** | 2.3 ~ 7.x | 自前ビルド | ✅ |
| x86_64 | 21 | 5.0+ | 自前ビルド | ✅ |

- `minSdkVersion 6`（Android 2.0.1）。**完全に動作する最低バージョンは Android 2.3**
- `targetSdkVersion 26`。Android 16 / 17 までインストール・動作可能
- アプリ内に**互換性リスト**を収録（初回起動時の利用規約同意前、および設定画面から表示）

### 画質

| 画質 | 可用性 | 備考 |
|---|---|---|
| 360P | 全端末 | シングルストリーム |
| 480P | ❌ 廃止 | bilibili がプロファイル 32 の提供を終了。選択しても 360P にフォールバック |
| 720P | 全端末 | シングルストリーム |
| 1080P | ほとんどの動画 | シングルストリーム。ログイン/プレミアムが必要な場合あり |
| 1080P 60fps | 一部の動画 | DASH 必須。Android 2.x では非表示 |
| 2K | ごく一部 | DASH + プレミアム必須。Android 2.x では非表示 |

> 画質リストは動的で、サーバーが実際に返した内容に従います。
> 同じ動画で短時間に画質を切り替えると、サーバー側でダウングレードされることがあります（その場合は「サーバーによりダウングレード」と明示表示します）。

---

## ビルド

2013〜2014 年当時のツールチェーンに固定しています：

| コンポーネント | バージョン |
|---|---|
| Android Gradle Plugin | 2.3.2 |
| Gradle | 3.3 |
| JDK | 7（コンパイル）/ 8（Gradle JVM） |
| compileSdk / buildTools | 23 / 25.0.3 |

```bash
gradlew.bat assembleRelease
# 出力: app/build/outputs/apk/app-release.apk
```

署名情報は `local.properties` から読み込みます（**git 管理外。絶対にコミットしないでください**）。

### ネイティブライブラリ（任意）

5 ABI 分の `libijkffmpeg.so` / `libijkplayer.so` / `libijksdl.so` はすべて**本プロジェクトでソースからビルド**しています：

- IJKPlayer（`ff4.0--ijk0.8.8--20210426--001`）
- FFmpeg 4.0（`--disable-gpl`、LGPL-2.1+）
- OpenSSL 1.0.2u（HTTPS 用、静的リンク）
- SoundTouch `ijk-r0.1.2-dev`（再生速度）
- libyuv `ijk-r0.2.1-dev`
- NDK r14b（API 9 系 ABI）と r16b（arm64-v8a / x86_64）

手順と注意点は [docs/BUILD_NATIVE.md](docs/BUILD_NATIVE.md) を参照してください。

---

## 依存関係と参考プロジェクト

### 第三者ライブラリ

| ライブラリ | バージョン | ライセンス | 用途 |
|---|---|---|---|
| [IJKPlayer](https://github.com/bilibili/ijkplayer) | ff4.0--ijk0.8.8 | Apache-2.0 | プレイヤーコア |
| [FFmpeg](https://github.com/Bilibili/FFmpeg) | 4.0（ijk fork） | **LGPL-2.1+** | デコード |
| [OpenSSL](https://www.openssl.org/) | 1.0.2u | OpenSSL/SSLeay | HTTPS |
| [SoundTouch](https://github.com/Bilibili/soundtouch) | ijk-r0.1.2-dev | LGPL-2.1 | 速度・ピッチ |
| [libyuv](https://github.com/Bilibili/libyuv) | ijk-r0.2.1-dev | BSD-3-Clause | 色変換 |
| [DanmakuFlameMaster](https://github.com/bilibili/DanmakuFlameMaster) | 2014 | Apache-2.0 | 弾幕エンジン |
| [ZXing](https://github.com/zxing/zxing) | core 3.3.0 | Apache-2.0 | QR コード |
| Android Support Library | v4 19.1.0 | Apache-2.0 | 互換レイヤー |
| RecyclerView / CardView | 21.0.0 | Apache-2.0 | リスト / カード |
| SwetakeQRCode | 2005 | 作者による無償利用 | ログイン QR |

詳細は **[THIRD_PARTY.md](THIRD_PARTY.md)** を参照してください。

### インスピレーション元・参考プロジェクト

| プロジェクト | 作者 | 関係 |
|---|---|---|
| **BiliTerminal（哔哩终端 修复版）** | **AktuelleKamera** | **主なインスピレーション元** |
| **BiliClassic（哔哩经典）** | **AktuelleKamera** | **UI スタイル・旧端末対応戦略の参考** |
| BiliClient-CE | コミュニティ | 一部 API 動作の参考 |

これらのプロジェクトから UI スタイルや旧端末向け戦略（低メモリ時の弾幕削減、API 9 ツールチェーンの選択など）の着想を得ています。
**ソースコードのコピーは行っていません**。開発環境の `ref/` は比較のための閲覧のみで、**リポジトリには含まれません**。

---

## コントリビューター

| 役割 | コントリビューター |
|---|---|
| 要件・テスト・意思決定 | プロジェクト発起人（人間） |
| **コード生成 / ドキュメント / アイコン / ビルドスクリプト** | **DeepSeek**（AI モデル） |
| **エージェントフレームワーク / オーケストレーション** | **DeepSeek Harness** |
| **インスピレーション元・参考プロジェクト** | **AktuelleKamera**（BiliTerminal、BiliClassic） |

---

## ライセンス

**GNU General Public License v3.0** で公開しています — [LICENSE](LICENSE)。

- 本プロジェクトのコード：GPL-3.0
- FFmpeg：**LGPL-2.1+**（`--disable-gpl`）、`libijkffmpeg.so` に静的リンク。
  LGPL の再リンク要件を満たすため、**完全なソースコードとビルドスクリプトを公開**しており、誰でも再ビルドして差し替え可能です。
- その他のコンポーネントはそれぞれのライセンスに従います（[THIRD_PARTY.md](THIRD_PARTY.md)）。

---

## 免責事項（要約）

本ソフトウェアは**現状のまま（AS IS）**提供され、いかなる保証も行いません。作者およびコントリビューターは、アカウント停止、データ損失、端末の故障、法的紛争を含む**いかなる損害についても責任を負いません**。

本プロジェクトは非公式であり、bilibili と提携関係にありません。「哔哩哔哩」「bilibili」およびテレビ型ロゴは各権利者に帰属します。
権利者が本プロジェクトを侵害と判断した場合は、Issue またはメールでご連絡ください。確認後、速やかに関連内容を削除します。

**完全な条項は [DISCLAIMER.md](DISCLAIMER.md) を参照してください。利用をもって同意したものとみなされます。**
