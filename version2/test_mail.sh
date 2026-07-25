#!/usr/bin/env bash
# 邮件发信测试：编译整个模块并运行 MailTest（往 radiance_55@qq.com 发一封测试信）
# 用法：在「已切换到手机热点 / 不同网络」后运行  bash test_mail.sh
set -e
cd "$(dirname "$0")"

OUT=out
rm -rf "$OUT" && mkdir -p "$OUT"

OS=$(uname -s)
MP=""
for j in lib/*.jar; do
  case "$j" in
    *-linux.jar) [[ "$OS" == *Linux* ]] && MP="$MP$j:" ;;
    *-win.jar)   [[ "$OS" != *Linux* ]] && MP="$MP$j:" ;;
    *) MP="$MP$j:" ;;
  esac
done
MP=${MP%:}

echo "[1/2] 编译..."
javac --module-path "$MP" -d "$OUT" $(find src -name '*.java')

echo "[2/2] 发信测试 (往 radiance_55@qq.com)..."
java --module-path "$MP:$OUT" -m com.bmi.app/com.bmi.util.MailTest
