#!/usr/bin/env python3
"""把新的网页源码同步进 APK，并检查原生层依赖的 DOM 契约。

用法:
    python3 tools/adapt_web.py /path/to/flybrain

它会:
  1. 复制 play/ → app/src/main/assets/www
  2. 把 brain/*.gz 解压成 *.bin（AAPT 会破坏 .gz，必须转）
  3. 注入 native-shell 样式（隐藏网页自带的 UI）
  4. 检查原生层依赖的元素 id / class 是否还在，缺了会列出来
"""
import os
import re
import gzip
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WWW = os.path.join(ROOT, "app/src/main/assets/www")

# 每个页面的隐藏规则（原生外壳接管这些元素）
HIDE = {
    "index.html": """
  .topbar, #drives, #tools, #zoom, #inboxBtn, #menuBtn, #menu {
    display: none !important;
  }
  /* INBOX 改为原生面板；小地图上移，避开原生工具条 */
  #inbox { display: none !important; }
  #mini {
    bottom: calc(14px + 146px + 30px) !important;
    border: 0 !important;
    border-radius: 16px !important;
    background: rgba(15,19,26,.82) !important;
    box-shadow: 0 8px 22px rgba(0,0,0,.5) !important;
    outline: 0 !important;
  }
  #mini canvas { border: 0 !important; outline: 0 !important; display: block; }
  #brainPanel, #brainPanel * { display: none !important; }""",
    "world/index.html": """
  .top, .tools, .stats, #legend { display: none !important; }
  #dbgBox { display: none !important; }""",
    "colony/index.html": """
  #bar, #stats, #hint { display: none !important; }
  /* 二级面板限高，给原生按钮留出空间 */
  #panel { max-height: 62vh !important; overflow: auto !important; }
  #brainwrap { max-height: 46vh !important; margin-bottom: 116px !important; }""",
}

# 原生层依赖的 DOM 契约：(文件, 说明, 选择器)
CONTRACT = [
    ("index.html", "状态文本", 'id="state"'),
    ("index.html", "驱动力值", 'id="v-hunger"'),
    ("index.html", "驱动力值", 'id="v-fear"'),
    ("index.html", "驱动力值", 'id="v-fatigue"'),
    ("index.html", "驱动力值", 'id="v-curiosity"'),
    ("index.html", "观察记录计数", 'id="inboxBtn"'),
    ("index.html", "观察记录内容", 'id="msgs"'),
    ("index.html", "观察面板字段", 'id="o-state"'),
    ("index.html", "观察面板字段", 'id="o-energy"'),
    ("index.html", "观察面板字段", 'id="o-avers"'),
    ("index.html", "观察面板字段", 'id="o-sticky"'),
    ("index.html", "观察面板字段", 'id="o-stuck"'),
    ("index.html", "观察面板字段", 'id="o-mn"'),
    ("index.html", "观察面板副标题", 'id="bInfo"'),
    ("index.html", "动作按钮", 'id="feedBtn"'),
    ("index.html", "动作按钮", 'id="foodClear"'),
    ("index.html", "动作按钮", 'id="stickyBtn"'),
    ("index.html", "动作按钮", 'id="stickyClear"'),
    ("index.html", "动作按钮", 'id="obsBtn"'),
    ("index.html", "缩放按钮", 'id="zin"'),
    ("index.html", "缩放按钮", 'id="zout"'),
    ("index.html", "缩放按钮", 'id="zrst"'),
    ("index.html", "参数面板行", 'class="pp-row"'),
    ("index.html", "参数分组", 'class="pp-group"'),
    ("index.html", "放电条容器", 'id="strip"'),
    ("index.html", "小地图", 'id="mini"'),

    ("world/index.html", "统计字段", 'id="n-fly"'),
    ("world/index.html", "统计字段", 'id="n-egg"'),
    ("world/index.html", "统计字段", 'id="n-pred2"'),
    ("world/index.html", "统计字段", 'id="n-eggp"'),
    ("world/index.html", "统计字段", 'id="n-food"'),
    ("world/index.html", "统计字段", 'id="n-frog"'),
    ("world/index.html", "统计字段", 'id="n-trap"'),
    ("world/index.html", "统计字段", 'id="avg-e"'),
    ("world/index.html", "统计字段", 'id="avg-p"'),
    ("world/index.html", "统计字段", 'id="gen"'),
    ("world/index.html", "统计字段", 'id="gen-p"'),
    ("world/index.html", "统计字段", 'id="mate-f"'),
    ("world/index.html", "统计字段", 'id="mate-p"'),
    ("world/index.html", "统计字段", 'id="eat-p"'),
    ("world/index.html", "统计字段", 'id="eat-f"'),
    ("world/index.html", "统计字段", 'id="eat-t"'),
    ("world/index.html", "动作按钮", 'id="tFood"'),
    ("world/index.html", "动作按钮", 'id="tFly"'),
    ("world/index.html", "动作按钮", 'id="tPred"'),
    ("world/index.html", "动作按钮", 'id="tClear"'),
    ("world/index.html", "动作按钮", 'id="tLegend"'),
    ("world/index.html", "动作按钮", 'id="tChart"'),
    ("world/index.html", "动作按钮", 'id="tSpeed"'),
    ("world/index.html", "动作按钮", 'id="btnPause"'),
    ("world/index.html", "动作按钮", 'id="btnReset"'),
    ("world/index.html", "参数行", 'class="prow"'),
    ("world/index.html", "参数分组", 'class="pg"'),
    ("world/index.html", "曲线面板", 'id="chart"'),

    ("colony/index.html", "数据面板", 'id="stats"'),
    ("colony/index.html", "模式按钮", 'data-mode="food"'),
    ("colony/index.html", "模式按钮", 'data-mode="trap"'),
    ("colony/index.html", "模式按钮", 'data-mode="fly"'),
    ("colony/index.html", "模式按钮", 'data-mode="erase"'),
    ("colony/index.html", "动作按钮", 'id="btnView"'),
    ("colony/index.html", "动作按钮", 'id="btnBrain"'),
    ("colony/index.html", "动作按钮", 'id="btnField"'),
    ("colony/index.html", "动作按钮", 'id="btnPause"'),
    ("colony/index.html", "动作按钮", 'id="btnParam"'),
    ("colony/index.html", "动作按钮", 'id="btnReset"'),
    ("colony/index.html", "参数面板", 'id="sliders"'),
    ("colony/index.html", "脑活动面板", 'id="brainwrap"'),
]


def inject(path, name):
    css = HIDE.get(name)
    if not css:
        return
    s = open(path, encoding="utf-8").read()
    s = re.sub(r'<style id="native-shell">.*?</style>\s*', "", s, flags=re.S)
    block = '<style id="native-shell">\n' + css + "\n</style>\n\n"
    if "<body>" in s:
        s = s.replace("<body>", block + "<body>", 1)
    else:
        s = block + s
    open(path, "w", encoding="utf-8").write(s)


def main():
    if len(sys.argv) < 2:
        print("用法: python3 tools/adapt_web.py <flybrain 目录>")
        sys.exit(1)
    src = os.path.join(sys.argv[1], "play")
    if not os.path.isdir(src):
        print(f"找不到 {src}")
        sys.exit(1)

    if os.path.exists(WWW):
        shutil.rmtree(WWW)
    shutil.copytree(src, WWW)
    print(f"已复制资源 → {WWW}")

    brain = os.path.join(WWW, "brain")
    ref = "/home/kalpa/flybrain-src/flybrain/play/brain"
    need = not os.path.isdir(brain) or not os.path.exists(os.path.join(brain, "meta.json"))
    if need and os.path.isdir(ref):
        os.makedirs(brain, exist_ok=True)
        for n in ("indptr", "dst", "w"):
            gz = os.path.join(ref, n + ".gz")
            if os.path.exists(gz):
                shutil.copy(gz, os.path.join(brain, n + ".gz.bin"))
                print(f"  补齐 {n}.gz.bin（来自参考目录）")
        meta = os.path.join(ref, "meta.json")
        if os.path.exists(meta):
            shutil.copy(meta, os.path.join(brain, "meta.json"))
    elif need:
        print("  ⚠ 找不到连接组数据，本地运算会失效")

    # AAPT 会解压 .gz 并去后缀 → 统一改名为 .gz.bin
    if os.path.isdir(brain):
        for n in ("indptr", "dst", "w"):
            gz = os.path.join(brain, n + ".gz")
            tgt = os.path.join(brain, n + ".gz.bin")
            if os.path.exists(gz):
                if os.path.exists(tgt):
                    os.remove(tgt)
                os.rename(gz, tgt)
                print(f"  {n}.gz → {n}.gz.bin")

    # 并同步 worker 里的路径
    worker = os.path.join(WWW, "brain_worker.js")
    if os.path.exists(worker):
        w = open(worker, encoding="utf-8").read()
        w2 = w
        for n in ("indptr", "dst", "w"):
            w2 = w2.replace(f"'brain/{n}.gz'", f"'brain/{n}.gz.bin'")
        if w2 != w:
            open(worker, "w", encoding="utf-8").write(w2)
            print("  已改写 worker 数据路径 → .gz.bin")
    wk = os.path.join(WWW, "brain_worker.js")
    if not os.path.exists(wk):
        ref = "/home/kalpa/flybrain-src/flybrain/play/brain_worker.js"
        if os.path.exists(ref):
            shutil.copy(ref, wk)
            print("  补齐 brain_worker.js（来自参考目录）")
        else:
            print("  ⚠ 缺少 brain_worker.js，本地运算会失败")


    for name in HIDE:
        p = os.path.join(WWW, name)
        if os.path.exists(p):
            inject(p, name)
    print("已注入 native-shell 样式")

    print("\n--- DOM 契约检查 ---")
    missing = 0
    cache = {}
    for f, desc, sel in CONTRACT:
        p = os.path.join(WWW, f)
        if p not in cache:
            cache[p] = open(p, encoding="utf-8").read() if os.path.exists(p) else ""
        if sel not in cache[p]:
            print(f"  ✗ {f}: {desc} ({sel})")
            missing += 1
    if missing == 0:
        print("  ✓ 全部命中，原生层无需改动")
    else:
        print(f"  共 {missing} 项缺失，需要更新 MainActivity 里的选择器")


if __name__ == "__main__":
    main()
