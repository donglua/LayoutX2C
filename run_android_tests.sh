#!/usr/bin/env bash

# LayoutX2C Android 等价性测试运行脚本

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if command -v adb >/dev/null 2>&1; then
  if ! adb devices | awk 'NR > 1 && $2 == "device" { found = 1 } END { exit found ? 0 : 1 }'; then
    echo "未检测到可用 Android 设备或模拟器。"
    echo "请连接设备后重试。"
    exit 1
  fi
fi

echo "=========================================="
echo "  LayoutX2C Android 等价性测试"
echo "=========================================="
echo ""

echo "步骤 1: 清理构建缓存..."
./gradlew :demo:clean

echo ""
echo "步骤 2: 生成 KSP 代码 (包括 DataBinding)..."
./gradlew :demo:kspDebugKotlin

echo ""
echo "步骤 3: 编译 Debug 版本..."
./gradlew :demo:assembleDebug

echo ""
echo "步骤 4: 编译测试 APK..."
./gradlew :demo:assembleDebugAndroidTest

echo ""
echo "步骤 5: 运行 Android 等价性测试..."
echo "注意: 需要连接 Android 设备或启动模拟器"
echo ""

./gradlew :demo:connectedDebugAndroidTest

echo ""
echo "=========================================="
echo "  测试完成!"
echo "=========================================="
echo ""
echo "查看测试报告:"
echo "  open demo/build/reports/androidTests/connected/index.html"
echo ""
