# 阅片 V2 进度与执行顺序

## 1. 当前状态

截至当前会话，Viewer V2 还未正式进入编码实现阶段，当前处于：

- 已完成需求方向确认
- 已完成总体方案梳理
- 已完成会话内待办拆分
- 尚未开始 UI 重构代码落地

## 2. 与现有主线关系

Viewer V2 不是独立于当前 viewer 的新系统，而是当前 `pacs-viewer` 的后续版本。

因此当前执行顺序为：

1. 优先继续完成 `viewer-black-screen-followup`
2. 再进入 Viewer V2 的工作区与 UI 重构

## 3. 待办拆分

### 3.1 `viewer-v2-shell`

目标：

- 替换当前偏表格化、调试型的整体页面骨架
- 落地顶部工具区、左侧导航区、中间 viewport 工作区、右侧上下文区

当前状态：`pending`

### 3.2 `viewer-v2-toolbar-chrome`

目标：

- 重做 toolbar、活动视口态、viewport 状态条和控制区
- 提升阅片工作站感

当前状态：`pending`

### 3.3 `viewer-v2-navigation-panels`

目标：

- 重构 Study / Series / Thumbnail 浏览体验
- 从表格和诊断列表导向，转为工作流导向

当前状态：`pending`

### 3.4 `viewer-v2-polish`

目标：

- 收口暗色主题、信息层级、交互细节、快捷操作
- 完成 UI 统一性和可读性打磨

当前状态：`pending`

## 4. 依赖关系

- `viewer-v2-shell` 依赖 `viewer-black-screen-followup`
- `viewer-v2-toolbar-chrome` 依赖 `viewer-v2-shell`
- `viewer-v2-navigation-panels` 依赖 `viewer-v2-shell`
- `viewer-v2-polish` 依赖 `viewer-v2-toolbar-chrome` 与 `viewer-v2-navigation-panels`

## 5. 推荐执行顺序

### 阶段 A：稳定性收口

- 继续验证并收口黑屏问题
- 确保 Viewer V2 构建在稳定渲染链路上

### 阶段 B：骨架升级

- 实现 Viewer V2 页面骨架
- 完成主要区块重新布局

### 阶段 C：交互升级

- 重做 toolbar 与 viewport chrome
- 强化活动视口、状态展示、控制入口

### 阶段 D：导航升级

- 重做 series 浏览与 thumbnail 体验
- 弱化调试表格感，强化阅片流

### 阶段 E：视觉收口

- 统一样式、间距、层级、主题
- 补齐细节状态与交互一致性

## 6. 当前建议

如果下一步继续开发，建议直接按以下顺序推进：

1. 先确认黑屏收尾是否可正式关闭
2. 若可关闭，立即开始 `viewer-v2-shell`
3. 若暂不能关闭，则继续以样本验证为主，不建议过早大改外壳
