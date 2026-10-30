# 提交类型选择

启用本项目的钩子：

```sh
git config --local core.hooksPath .githooks
```

运行 `git commit` 时依次选择 `feat`、`fix` 等类型、影响范围，并填写描述。
提交信息示例：`feat(logkit): 支持格式化日志`。

直接使用 `git commit -m "消息"` 时保留手写内容，不添加编号或页脚。
