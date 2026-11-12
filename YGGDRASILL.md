# ユグドラシルの直し（ruten-studio/BetterHud）

[toxicity188/BetterHud](https://github.com/toxicity188/BetterHud) の master `8f4b11dc`（CI の 449 回目、`BetterHud-bukkit-2.2.0-SNAPSHOT-449`）に、Minecraft サーバー「ユグドラシル」で使う直しを足したもの。直しはブランチ `ygg/shared-glyphs` にある。どちらの設定も入れなければ、元の BetterHud と同じに動く（作られる `build.zip` が元の jar と一致することを確かめた）。

## 足した設定（`plugins/BetterHud/config.yml`）

| 設定 | 既定 | 内容 |
|---|---|---|
| `text-antialiasing` | `false` | TTF の字を、ふちのなめらかな画像で作る。切り詰める幅は白黒の字と同じなので、字の間隔は変わらない |
| `share-text-glyphs` | `false` | 字の画像を文字の要素どうしで共有する（下の説明）。`text.vsh` のテンプレートに `#CreateTextElement` と共有の字の読み取りが要る |

## 字の共有（`share-text-glyphs`）

元の BetterHud は、文字の要素の番号（レイアウトの位置・倍率・層などの組）を、フォントの定義の `ascent` に入れる。そのため、文字の要素の 1 行ごとに別のフォントの定義が要り、Minecraft は同じ字の画像を定義の数だけ読み込む。ユグドラシルでは HUD とメニューで約 2.2GB になっていた。

共有するときは次のようにする（`dist/.../text/SharedTextGlyphs.kt`）。

- 字の定義（ファイル・大きさ・相対の ascent・空白の幅）が同じ文字の要素は、1 つのフォントを使う。その ascent には予約の番号 `SHARED_ID`（1023）を入れる
- 要素は、行の前後に入れる空白で、字をはるか右へずらした量（要素の番号 × 4096）で見分ける。前後の空白で打ち消すので、行の幅は変わらない
- `#CreateTextElement` が、要素の数と、要素ごとの元のシェーダーの番号（`TEXT_ELEMENT_SHADER`）と縦の位置（`TEXT_ELEMENT_Y`）の表を書く
- `text.vsh` は、番号が `SHARED_ID` の頂点で、`pos.x` から要素を読み、ずらしを戻して、表の番号と縦の位置を当てる。そのあとは元の処理と同じ

字の並べ方は Minecraft のままなので、大きさ・字の間隔・影は変わらない。背景（`background`）か絵文字（`image`）を使う文字の要素は、共有しない（元のまま）。ポップアップとコンパスの文字も元のまま。

`text.vsh` で足す部分（`#CreateLayout` の switch の前、`pos.x -= 0.5 * ui.x;` と `pos.y -= ...;` のあと）:

```glsl
if (id == SHARED_TEXT_ID) {
    int element = clamp(int(floor(pos.x / TEXT_ELEMENT_SHIFT + 0.5)), 0, TEXT_ELEMENT_COUNT);
    pos.x -= float(element) * TEXT_ELEMENT_SHIFT;
    id = TEXT_ELEMENT_SHADER[element];
    pos.y += TEXT_ELEMENT_Y[element];
}
```

ユグドラシルのテンプレートは plugin-yggmenu の `src/main/resources/pack/text.vsh` にあり、YggMenu が起動時に `plugins/BetterHud/shaders/betterhud_26_3/` へ書き出す。

e2e での結果: 文字のフォントの定義が 368 個から 34 個、字の画像の読み込みが約 2.2GB から約 251MB、クライアントのメモリが約 6.4GB から約 3.6GB になった。

## 組み立て

1.21 系向けの NMS（`nms/v1_21_R*`）の準備に使う remapper は、手元の Java 25 で動かない（`Unsupported class file major version 69`）。手元では、同じコミットの公式の jar から 1.21 系のクラスを取り出した jar を渡して組み立てる。

```bash
# 公式の jar から kr/toxicity/hud/nms/v1_21_R* を取り出した jar を作っておく
GRADLE_USER_HOME=/d/YggDrasill/.gradle-home ./gradlew :bootstrap:bukkit:shadowJar -PlegacyNmsJar=D:/YggDrasill/.gradle-home/legacy-nms-449.jar
```

`-PlegacyNmsJar` を渡さなければ、元のとおりすべてをソースから組み立てる。出力は `build/libs/BetterHud-bukkit-2.2.0.jar`。node-a には `BetterHud-bukkit-2.2.0-ygg1.jar` の名前で入れた（2026-11-12）。

## BetterHud を更新するとき

元の BetterHud の新しい版にこのブランチを載せ直し、`text.vsh` のテンプレートの差分（BetterHud 側の `betterhud_26_3` の変更）も YggMenu に取り込む。
