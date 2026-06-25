# Access Detector

## 概要

`AccessDetector` は、軽量な HTTP サーバーとして機能し、受信した HTTP リクエストからの接続情報をリアルタイムでキャプチャし表示する Java CLI アプリケーションです。透過 GIF 画像を使ったトラッキングピクセルや、ngrok を利用した公開テストなどに役立ちます。

## 必要環境

- Java 17 以上
- 対応OS: Windows 10/11 (cmd.exe / Windows Terminal / PowerShell)、Linux (Ubuntu 20.04+)

## インストール

GitHub リリースページからコンパイル済みの `access-detector.jar` をダウンロードして使用します。

## 使い方

以下のコマンドを使用して実行します：

```bash
java -jar access-detector.jar [オプション]
```

## オプション一覧

| フラグ | 型 | デフォルト | 説明 |
|------|------|---------|-------------|
| `-p <port>` | int | `8080` | リッスンするポート番号を指定します。 |
| `-r <url>` | String | — | すべてのリクエストをこの URL にリダイレクト (HTTP 302) します。 |
| `-file <filename>` | String | — | すべてのリクエストに対してこのローカルファイルを提供します。 |
| `-l <filename.txt>` | String | — | リクエストのログをこのファイルに記録します。 |
| `-ipv4` | boolean | — | IPv4 バインド (`0.0.0.0`) を強制します。 |
| `-ipv6` | boolean | — | IPv6 バインド (`::`) を強制します。 |
| `--cdn` | boolean | — | `X-Forwarded-For` ヘッダーからクライアント IP を読み取ります。 |
| `-cf` | boolean | — | `CF-Connecting-IP` ヘッダー (Cloudflare) からクライアント IP を読み取ります。 |
| `-ngrok` | boolean | — | Java SDK を介して ngrok トンネルを起動し、`X-Forwarded-For` から IP を読み取ります。 |

## 使用例

1. **基本起動 (デフォルトポート 8080)**
   ```bash
   java -jar access-detector.jar
   ```
   デフォルト設定で起動し、HTTP リクエストを待ち受けます。

2. **ポート番号の指定**
   ```bash
   java -jar access-detector.jar -p 3000
   ```
   ポート 3000 で待ち受けます。

3. **Cloudflare CDN 経由のアクセスの検出**
   ```bash
   java -jar access-detector.jar -p 80 -cf -l log.txt
   ```
   80 番ポートで待ち受け、Cloudflare の `CF-Connecting-IP` を使って元のクライアント IP を取得し、`log.txt` に記録します。

4. **ngrok を介した公開**
   ```bash
   java -jar access-detector.jar -p 8080 -ngrok
   ```
   起動と同時に ngrok を使ったトンネルを作成し、外部からアクセス可能な URL を表示します。

5. **別の URL へのリダイレクト**
   ```bash
   java -jar access-detector.jar -p 8080 -r https://example.com
   ```
   すべてのリクエストを `https://example.com` にリダイレクトさせます。

6. **ファイルを提供しながらアクセスをログに記録**
   ```bash
   java -jar access-detector.jar -p 8080 -file index.html -l access.txt
   ```
   `index.html` の内容をレスポンスとして返しつつ、アクセスを `access.txt` に記録します。

7. **IPv6 バインドで起動**
   ```bash
   java -jar access-detector.jar -p 8080 -ipv6
   ```
   IPv6 でのみ待ち受けます (`::` バインド)。

## ngrokの使い方

`-ngrok` オプションを使用すると、アプリケーションに組み込まれた `java-ngrok` SDK が自動で OS ごとのバイナリをダウンロード（`~/.ngrok2/` に保存）し、トンネルを作成します。

ngrok の無料プランには帯域幅や接続数の制限があります。制限を緩和するためには、以下の手順で ngrok の認証トークンを設定してください。

```bash
ngrok config add-authtoken <あなたのトークン>
```

トークンが設定されていなくてもトンネルは機能しますが、警告メッセージが表示され無料枠の制限が適用されます。

## ログフォーマット

`-l` オプションで記録されるログファイルの形式は以下の通りです。UTF-8 形式で追記されます。

```text
[2025-01-15 14:22:01] IP=203.0.113.1 PATH=/login METHOD=GET LOCATION=Japan,Tokyo ISP=AS2527 NTT UA=Mozilla/5.0 (Windows NT 10.0)
```

## 表示項目の説明

- **No:** アクセスの通し番号
- **Time:** アクセスされた時刻
- **IP Address:** クライアントの IP アドレス（CDN/ngrok 経由の場合は元の IP を表示）
- **Location:** IP から推測される国や地域（Private IP などの場合も表示されます）
- **ISP:** インターネットサービスプロバイダーの名前
- **Path:** リクエストされたパス（`/admin` や `/login` など怪しいパスは赤色で表示）
- **User-Agent:** クライアントのブラウザやツールの情報

## 注意事項

- 本ツールで使用している IP ロケーション検索 API (`ip-api.com`) は、**非商用利用のみ無料**です。商用目的で利用する場合は、別途有料プランの契約が必要になる場合があります。
- ngrok の無料プランではセッションの接続や帯域幅に制限があります。

## ビルド方法

開発者向けに、ソースコードから自分でビルドする方法は以下の通りです。Maven と Java 17 以上が必要です。

```bash
mvn clean package
```
これにより `target/` ディレクトリ配下に、すべての依存関係を含んだ `access-detector.jar` が生成されます。
