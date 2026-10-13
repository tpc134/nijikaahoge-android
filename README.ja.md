# Nijika's Ahoge — Android 版

[简体中文](README.md) | [English](README.en.md) | **日本語**

![iPhone・iPad・Android 実機での Nijika's Ahoge 実行画面](readme_pic/1.jpg)

**『Nijika's Ahoge』（虹夏ちゃんの無限ドリトス）の非公式 Android 移植版です。**

| 項目 | 内容 |
| --- | --- |
| 原作 | **Nijika's Ahoge**（虹夏ちゃんの無限ドリトス） |
| 原作リンク | <https://tamanidamani.itch.io/nijikas-ahoge> |
| 原作者 | **ACh（ナコ）**、**TamaniDamani（塔打）** |
| 許諾 | **原作者お二方の同意を得ています**。条件は**氏名表示**と**非営利**。本移植は**無料・広告なし**です。 |

原作は Construct 3 で作られたブラウザゲームです。本移植ではゲームロジック・レンダラ・オーディオを
すべて **Kotlin** で再実装し、物理は **jBox2D 2.2.1.1**、描画は **OpenGL ES 2.0** に置き換えています。
ゲームエンジンは使用していません。

| 項目 | 値 |
| --- | --- |
| バージョン | 1.2（versionCode 3） |
| パッケージ名 | `com.ruich97.nijikaahoge` |
| 最低 OS | Android 5.0 / API 21 |
| compile / target SDK | 35 / 35 |
| グラフィックス | OpenGL ES 2.0 |
| ネイティブライブラリ | なし —— armeabi-v7a と arm64-v8a 共通の単一 APK |
| サイズ | release APK 約 2.36 MB |
| ツールチェーン | Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21 / JDK 17+ |

## 遊び方

虹夏の頭のアホ毛を抜き、袋に落として回収します。袋を涼や喜多に与えるとそれぞれ違う反応が返り、
金の袋が満杯になると大ぼっちの祝福が発生します。スコアが **10 / 50 / 100 / 500 / 1000 / 5000** に
達すると節目の演出が入り、星歌が 4 方向から登場します。起動時の向きは端末の既定に従い、
1.5 秒後はセンサーで自由に回転します。縦横どちらでも遊べます。

## ダウンロードとインストール

ビルド済み APK は **Releases** ページで公開しており、そのままインストールできます
（ソースツリー自体に APK は含まれていません）。

| 成果物 | サイズ | 説明 |
| --- | ---: | --- |
| `app-release_v1.2.apk` | 2.36 MB | release、署名済み。通常はこちらを使用 |
| `app-debug.apk` | 2.62 MB | debug、ADB デバッグコマンド対応。日常利用には非推奨 |

SHA-256:

```
app-release_v1.2.apk  d56b8c45f76dc833ea5033660166ff50be6463a3699eef4331863a9f17d54bc6
app-debug.apk         e77a0794578e3bf473352cc5f6a706d87e68e6fa1bb5a1369daa8f41cfb42f40
```

再ビルドすると APK の内容が変わるため、チェックサムは再計算が必要です。

- **Android 5.0 / API 21 以上**、**OpenGL ES 2.0** 対応端末が必要です。
  ABI 専用のネイティブライブラリはなく、armeabi-v7a と arm64-v8a を同一 APK でカバーします。
- パッケージ `com.ruich97.nijikaahoge`、バージョン 1.2（versionCode 3）、targetSdk 35。
  旧版 `com.nijika.ahoge` とはパッケージ名が異なるため、**旧版への上書きインストールはできません**。
  2 つのアプリとして併存します。
- 本 APK は Android の**デバッグキーストア**（`~/.android/debug.keystore`、証明書 DN
  `C=US, O=Android, CN=Android Debug`、証明書 SHA-256
  `935262ff0e7873606d8e311646907cc83ae43249b08f762b966114b82ef3830c`）で署名されています。
  **開発用の署名であり Play ストアの本番署名ではない**ため、ストアへの申請には使えません。
  別の鍵で署名した APK から本パッケージへ上書きインストールすることもできません。
  本リポジトリにキーストアは含まれていません。
- ご自身でビルドした release APK は**署名されていません**。インストール前に自分の鍵で署名してください。

## ビルド

必要なもの：JDK 17 以上、Android SDK platform 35 と build-tools 35.0.0、
環境変数 `ANDROID_HOME` を設定するか `android/local.properties` に `sdk.dir` を記述
（このファイルは端末固有のパスを持つため `.gitignore` で除外済み）。

```sh
cd android
# Windows
.\gradlew.bat assembleRelease
# Linux / macOS
sh ./gradlew assembleRelease
```

Gradle Wrapper の初回実行時は Gradle と依存関係のダウンロードにネットワーク接続が必要です。
キャッシュ済みなら `--offline` を付けられます。

## テスト

計測テストは **17 件**。4 方向のミラー、小ぼっちの復帰、ドラッグ解除時の速度、フィクスチャと
キャッシュの整合性、袋が画面外に出た際の状態保持、節目、EGL 再生成後の進行保持、
60/120/240 本のアホ毛の負荷、指のエッジ許容、直接ヒット優先などをカバーしています。

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.ruich97.nijikaahoge.test/android.test.InstrumentationTestRunner
```

`tools/build_render_atlas.py` で `assets/frames/` から `small_objects_atlas.png` と
`render_atlas.json` を再生成できます（Python + Pillow が必要）。アトラスは同梱済みのため、
通常のビルドに Python は不要です。

## 実装のポイント

### モジュール構成

| パス | 役割 |
| --- | --- |
| `MainActivity.kt` | エントリポイント、没入型全画面、ライフサイクル、センサー回転 |
| `GameView.kt` | SurfaceView + GL レンダースレッド、タッチ振り分け |
| `GameRenderer.kt` | フレームループ、レターボックス表示、レイヤ順の描画 |
| `game/Data.kt` | `game_data.json` を型 / アニメーション / インスタンスへ解析 |
| `game/Obj.kt` | オブジェクトモデル、アニメ進行、ジオメトリ（多角形 / 境界 / 三角形キャッシュ） |
| `game/Phys.kt` | jBox2D ワールド、フィクスチャ生成、スリープと起床 |
| `game/Sat.kt` | 分離軸（SAT）多角形とエディタ多角形 |
| `game/World.kt` | インスタンスの生成 / 破棄、レイヤ順、親子同期 |
| `game/Logic.kt` | ゲームロジック全般：引き抜き、接触、給餌、節目、パーティクル、星歌、小ぼっち |
| `game/Game.kt` | 外部向けファサード：`update` / `touch*` |
| `game/Sys.kt` | Tween とシーケンス（Scheduler） |
| `game/Parts.kt` | パーティクルエミッタ |
| `game/Snd.kt` | SoundPool オーディオ：先読み + オンデマンドデコード |
| `game/Vib.kt` | 振動フィードバック |
| `gfx/SpriteBatch.kt` | 頂点バッチ処理、VBO orphaning |
| `gfx/Textures.kt` | テクスチャとアトラスの読み込み |
| `assets/` | `game_data.json`、`render_atlas.json`、`small_objects_atlas.png`、`frames/`、`media/` |
| `readme_pic/` | README 冒頭のバナー画像 |

### パフォーマンス

- **アトラス統合**：アホ毛とパーティクルの小さなスプライト 26 枚を 1 枚の 512×512 アトラスに
  元の画素のまま統合。画面外オブジェクトのカリング、GPU 頂点バッファの再利用、
  毎フレームのオブジェクトリスト走査を型インデックスへ置換、Tween の再利用で一時オブジェクトを削減。
- **VBO orphaning（今回最大の改善）**：`SpriteBatch.flush()` は毎フレーム約 10 回、同じ VBO に
  `glBufferSubData` を書いた直後に `glDrawElements` を発行します。Adreno は buffer rename を
  行うため以前から影響を受けませんでしたが、Mali は「まだ参照されているバッファへの書き込み」で
  暗黙に直列化します —— これが「GPU が強い方が遅い」理由でした。修正は `subData` の前に
  `glBufferData(..., DYNAMIC_DRAW)` を呼んでバッファを orphaning するものです。

  | 端末 | 改善前 | 改善後 |
  | --- | ---: | ---: |
  | Kirin 960 | 約 35 fps | **安定 60 fps** |
  | Snapdragon 210 | ほぼ上限 | 安定 60 fps、発熱も明確に低下 |
  | Snapdragon 410 | — | 安定 60 fps |
  | Snapdragon W5+ Gen 1 | ややコマ落ち | 約 55 fps |

- **低スペック向けのフレーム制限を撤廃**：Snapdragon 210 / Wear 2100 で実測したところ、
  制限は発熱を改善せずドラッグの操作感だけを損ないました。全端末で連続描画とし、
  長いフレームは dt クランプで吸収します（上限 0.05 秒、およそ 20 fps の下限）。
  `lowEndProfile` はコンパクトな UI レイアウトの選択のみになりました。
- **オーディオのオンデマンドデコード**：SoundPool は単一スレッドで直列デコードします。
  全 12 音声で 28.5 秒あり、低スペック端末では 1 コアを約 20 秒占有して、
  レンダースレッドを 20 秒以上にわたり 30〜47 fps まで落とします。そこで高頻度の短い音声のみを
  先読みします —— 最小の 4 つ（引き抜き 0.6 秒、回収 0.26〜0.49 秒）に、給餌で必ず選ばれる
  `1` と `2` を加えた計 6 音声・4.52 秒。Snapdragon 210 で 55 fps に到達するまでの時間が
  20 秒以上から約 8〜10 秒に短縮しました。デコード中の再生要求は、一定時間の再試行後に
  黙って捨てるのではなく `onLoadComplete` コールバックを待って再生します。

### 互換性と再現度

- 効果音を Opus/WebM から **AAC/M4A** へ変更：一部の Android 6 ROM では WebM/Opus のデコードが
  同梱の ffmpeg プラグインにフォールバックし、失敗時に `CHECK_EQ` → `abort()` を踏みます。
  これは Java 側の `try/catch` では捕捉できないネイティブ abort で、起動直後のクラッシュを招きます。
  プラットフォームのデコーダに切り替えた結果、当該端末は安定 60 fps になりました。
- 最低 OS を API 24 から **API 21** へ引き下げ。minSdk 21 での lint `NewApi` は 0 件です。
- 星歌の 4 方向ミラーを**オブジェクト原点まわりの反転**に修正（頂点・境界・衝突多角形が一致）。
  旧版の UV のみの入れ替えでは、見た目の位置と衝突位置が食い違っていました。
- 小ぼっちは転送前にスリープ中の剛体を明示的に起床。指を離した時は線速度と角速度を 0 にし、
  最後のドラッグ位置を同期。パーティクルは原作のレイヤに復帰（涼 / 喜多は第 3 層、
  大ぼっち / 星歌は第 5 層）。
- 指入力には、引き抜き対象のアホ毛多角形の縁に約 **12dp の許容**を追加（上限 96 論理ピクセル）。
  直接ヒットした袋や残骸が優先される点は変わらず、マウスとスタイラスは厳密判定のままです。
- 振動：掴む / 袋に入れると **9ms** の短振動、スコアの節目で **55ms** の長振動。
  API 31+ は `VibratorManager` を使用し、振動子のない端末では黙ってスキップします。

## 既知の制限

- 本ソースからビルドした release APK は未署名です。全機種を横断した統一ベンチマークは未実施です。
- 密に積み重なった衝突では依然としてフレーム落ちの可能性があります。円形画面のクリッピング、
  長時間の連続プレイでの発熱と消費電力、旧版におけるベンダー独自のオーディオ実装は実機検証が必要です。
- ゲーム状態が保持されるのは回転と短時間のバックグラウンド移行のみです。プロセスが回収されたり
  終了した場合は最初からになります —— **永続セーブはありません**。
- アホ毛の本数上限はなく、回収アイテムの削除も、Box2D ソルバの反復回数の削減もしていません。

## ライセンス・許諾・クレジット

### 原作

| 項目 | 内容 |
| --- | --- |
| 原作名 | **Nijika's Ahoge**（虹夏ちゃんの無限ドリトス） |
| 原作リンク | <https://tamanidamani.itch.io/nijikas-ahoge> |
| 原作者 | **ACh（ナコ）**、**TamaniDamani（塔打）** |

**許諾状況：原作者お二方の同意を得ています。条件は氏名表示と非営利であり、
本移植は無料・広告なしで、有料コンテンツも広告 SDK も含みません。**

### 素材の著作権（重要）

原作に由来するゲームの画像、音声、キャラクターその他の素材の著作権は
**原作者（イラストレーター）に帰属し**、**本リポジトリの MIT ライセンスの対象外です**。
本リポジトリはこれらの素材について著作権を主張せず、MIT ライセンスによって
利用権を許諾することもありません。原作画面の原作者署名は表示されたままにしています。
原作者の肖像や公式のプロモーション画像をカバーに使わず、ご自身で撮影したプレイ画面を使用してください。

### コードのライセンス

本移植が独自に記述したソースコードは **MIT ライセンス**で公開します（[`LICENSE`](LICENSE)）。
その許諾範囲は**本プロジェクトが独自に記述したコードに限られ**、上記の原作素材は含みません。

### サードパーティ

- 物理エンジンは [jBox2D](https://github.com/jbox2d/jbox2d) 2.2.1.1 を使用し、原ライセンスで配布します。
- 本リポジトリは**非公式の二次創作移植**であり、原作者とは無関係で、原作者の見解を示すものでもありません。
