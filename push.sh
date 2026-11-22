#!/usr/bin/env bash
# 用法: GITHUB_TOKEN=ghp_xxx ./push.sh <用户名> <仓库名>
set -euo pipefail
USER_NAME="${1:?用法: ./push.sh <用户名> <仓库名>}"
REPO="${2:?用法: ./push.sh <用户名> <仓库名>}"
TOKEN="${GITHUB_TOKEN:?请设置环境变量 GITHUB_TOKEN}"

echo "→ 创建仓库 $USER_NAME/$REPO"
curl -fsS -X POST https://api.github.com/user/repos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -d "{\"name\":\"$REPO\",\"description\":\"数字果蝇 · 原生 Android 果蝇全脑模拟（围栏 / 大世界 / 养果蝇）\",\"homepage\":\"\",\"private\":false}" \
  > /dev/null || echo "（仓库可能已存在，继续）"

echo "→ 推送代码与标签"
git remote remove origin 2>/dev/null || true
git remote add origin "https://$USER_NAME:$TOKEN@github.com/$USER_NAME/$REPO.git"
git branch -M main
git push -u origin main
git push origin v1.43.0

echo "→ 创建 Release"
curl -fsS -X POST "https://api.github.com/repos/$USER_NAME/$REPO/releases" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -d "{\"tag_name\":\"v1.43.0\",\"name\":\"数字果蝇 v1.43.0\",\"body\":$(python3 -c "import json,sys;print(json.dumps(open('.github/RELEASE_NOTES_v1.43.0.md',encoding='utf-8').read()))")}" \
  > /dev/null

echo "→ 上传 APK"
RELEASE_ID=$(curl -fsS "https://api.github.com/repos/$USER_NAME/$REPO/releases/tags/v1.43.0" \
  -H "Authorization: Bearer $TOKEN" | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")
curl -fsS -X POST "https://uploads.github.com/repos/$USER_NAME/$REPO/releases/$RELEASE_ID/assets?name=drosophila-1.43.0.apk" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary "@数字果蝇-1.43.0.apk" > /dev/null

echo "✓ 完成： https://github.com/$USER_NAME/$REPO/releases/tag/v1.43.0"
