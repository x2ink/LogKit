# LogKit

这是个人 Android 项目，仅以本仓库文件及用户当前指令作为项目约定。

- 不自动套用本机其他项目或组织的规则、技能、任务编号、提交页脚、审核流程。
- 未经明确要求，不安装外部技能中的钩子，不生成流程文档或任务记录。
- 提交信息采用 `type(scope): description`，范围可选，不添加自动编号或页脚。
- 远端为 `git@github.com:x2ink/LogKit.git`，默认分支为 `main`；按用户要求执行提交和推送。
- 项目钩子只使用 `.githooks/`。克隆后用 `git config --local core.hooksPath .githooks` 启用。
- 修改前检查工作区状态，保留用户未提交的改动，不修改全局配置。
