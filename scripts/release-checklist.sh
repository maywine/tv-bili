#!/usr/bin/env bash
# Phase 7 release 出包前自检：ABI 单一 / .so 全部 armeabi-v7a / dex ≤ 4 / APK ≤ 20MB
#
# 用法：
#   ./scripts/release-checklist.sh                                          # 默认 app/build/outputs/apk/release/app-release.apk
#   ./scripts/release-checklist.sh path/to/some.apk                         # 自定义路径
#
# 退码：0=全绿；非 0=至少一项不达标，标准错误会列出原因。
set -euo pipefail

APK="${1:-app/build/outputs/apk/release/app-release.apk}"

if [[ ! -f "$APK" ]]; then
    echo "❌ APK not found: $APK" >&2
    exit 1
fi

echo "── checking: $APK ──"
fail=0

# —— APK 体积 ≤ 20 MB ——
size_bytes=$(stat -c%s "$APK")
size_mb=$(( size_bytes / 1024 / 1024 ))
echo "APK size:  ${size_mb} MB (${size_bytes} bytes)"
if (( size_mb > 20 )); then
    echo "  ❌ APK > 20 MB" >&2
    fail=1
fi

# —— ABI 单一：仅 armeabi-v7a ——
abis=$(unzip -l "$APK" | awk '/\.so$/ {
    n = split($NF, a, "/")
    print a[n-1]
}' | sort -u)
if [[ -z "$abis" ]]; then
    echo "ABIs:      <no native libs>"
else
    echo "ABIs:      $(echo "$abis" | tr '\n' ' ')"
    bad=$(echo "$abis" | grep -vx 'armeabi-v7a' || true)
    if [[ -n "$bad" ]]; then
        echo "  ❌ unexpected ABI(s): $(echo "$bad" | tr '\n' ' ')" >&2
        fail=1
    fi
fi

# —— dex 数 ≤ 4 ——
dex_count=$(unzip -l "$APK" | grep -cE 'classes[0-9]*\.dex$' || true)
echo "dex count: $dex_count"
if (( dex_count > 4 )); then
    echo "  ❌ dex > 4" >&2
    fail=1
fi

# —— baseline profile 是否嵌入 ——
if unzip -l "$APK" | grep -q 'assets/dexopt/baseline.prof'; then
    prof_bytes=$(unzip -p "$APK" assets/dexopt/baseline.prof | wc -c)
    echo "baseline:  ${prof_bytes} bytes (assets/dexopt/baseline.prof)"
else
    echo "baseline:  <none> (尚未生成；详见 .claude/PRPs/plans/.../phase-7 § Task 15)"
fi

# —— 共享库清单（仅展示，不参与判定）——
echo "── shared libs ──"
unzip -l "$APK" | awk '/\.so$/ {printf "  %-50s %s bytes\n", $NF, $1}'

if (( fail == 0 )); then
    echo "── ✅ release checklist passed ──"
else
    echo "── ❌ release checklist failed ──" >&2
fi
exit "$fail"
