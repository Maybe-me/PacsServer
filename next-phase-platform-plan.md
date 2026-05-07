# PACS 平台下一阶段开发计划

## 1. 目标

本计划用于承接以下已经敲定的需求：

1. 公开测试影像样本补充与模板库
2. Python 造数服务
3. Storage SPI
4. Distributed Lock SPI
5. JPA + JdbcTemplate + 方言能力抽象
6. Cornerstone3 阅片 V1（目标体验对齐 OHIF）

不在本计划范围内：

1. AI 调度中心
2. 完整统一重试中心

## 2. 分阶段实施

## 阶段 1：公开测试样本与模板库建设

### 目标

补充一套可用于：

1. PACS 联调
2. Python 造数模板
3. 阅片兼容性测试

的公开样本库。

### 工作项

1. 选定公开样本来源与可使用范围
2. 按尽可能完整的 DICOM 对象类型整理模板目录
3. 建立样本元数据清单
4. 选出可直接用于自动化测试的轻量样本
5. 建立“对象类型 + 传输语法 + 帧类型 + 压缩方式”的覆盖矩阵

### 产出

1. 模板目录规范
2. 模板清单文档
3. 尽可能完整的样本分类标记体系，至少覆盖：
   - CT / MR / PET / NM / XA / XRF / US / MG / CR / DX
   - Visible Light / Ophthalmic / Endoscopy / Microscopy
   - RT Image / RT Struct / RT Plan / RT Dose
   - SR / SEG / PR / Parametric Map
   - Encapsulated PDF / Video / Waveform / SC / OT
4. 覆盖矩阵文档（对象类型、压缩方式、单帧/多帧、传输语法）

### 测试要求

1. 样本可被当前 PACS 接收
2. 样本可被当前查询接口识别
3. 样本可被当前 WADO-RS 正常读取
4. 样本覆盖矩阵中的关键类型至少各有可验证样本

## 阶段 2：Storage SPI

### 目标

将当前文件存储能力抽象为统一接口，支持多种底层实现。

### 工作项

1. 定义 `ObjectStorageProvider`
2. 改造当前本地存储实现为 `LocalStorageProvider`
3. 引入 `S3CompatibleStorageProvider`
4. 引入 `FastDfsStorageProvider`
5. 调整实例文件元数据结构
6. 补充配置模型与启动配置

### 首批实现

1. Local
2. FastDFS
3. S3 Compatible（默认面向 SeaweedFS）

### 测试要求

#### 单元测试

1. Provider 接口行为一致性测试
2. 路径转换与 key 生成测试
3. metadata 读写测试

#### 集成测试

1. Local 存储读写回归测试
2. S3 Compatible 冒烟测试
3. FastDFS 适配层冒烟测试（可使用 mock/stub）
4. WADO-RS 读取新存储抽象后的回归测试

## 阶段 3：Distributed Lock SPI

### 目标

将现有数据库锁抽象为统一锁接口，为 Redisson / Zookeeper 扩展留口。

### 工作项

1. 定义 `DistributedLockProvider`
2. 迁移当前数据库锁为 `DatabaseLockProvider`
3. 设计 Redisson 扩展接口
4. 设计 Zookeeper 扩展接口
5. 调整同步调度模块使用锁 SPI

### 测试要求

#### 单元测试

1. tryLock / renew / unlock 语义测试
2. 锁续约边界测试
3. 同一 key 并发竞争测试

#### 集成测试

1. 调度任务在数据库锁下的单实例执行测试
2. 多 owner 抢锁测试
3. 释放锁后重新获取测试

## 阶段 4：JPA + JdbcTemplate + Dialect 能力抽象

### 目标

继续保留 JPA 为主链路，同时建立 SQL 能力抽象，避免后续多数据库兼容失控。

### 工作项

1. 梳理现有 JPA 主链路
2. 识别必须 SQL 化的基础设施场景
3. 引入 `DatabaseDialectAdapter`
4. 抽象分页、锁语句、日期函数、特殊更新能力
5. 将分布式锁、统计类查询、批量更新迁移到 JdbcTemplate 或集中 SQL 层

### 测试要求

#### 单元测试

1. Dialect Adapter 能力选择测试
2. SQL 片段生成测试
3. 分页 / 锁 / upsert 能力映射测试

#### 集成测试

1. 现有 JPA CRUD 回归测试
2. JdbcTemplate 锁与统计查询测试
3. H2 / PostgreSQL 兼容性测试（若环境允许）

## 阶段 5：Python 造数服务

### 目标

提供独立的 DICOM 造数与变换能力，支持 PACS 联调与后续自动化测试。

### 工作项

1. 建立 Python 服务骨架
2. 接入模板改写能力
3. 接入像素轻变换能力
4. 支持输出目录、直接推送、模拟上游 PACS 三种模式
5. 增加任务状态查询
6. 与 Java 侧形成调用约定

### 第一阶段支持范围

1. 模板改写型造数
2. 元数据变换
3. 轻量像素变换
4. 基础对象生成（SC / OT / PDF / Video）

### 测试要求

#### Python 单元测试

1. UID 生成测试
2. DICOM 标签改写测试
3. 像素变换测试
4. 输出文件有效性测试

#### 集成测试

1. 生成结果可被当前 PACS 接收
2. 生成结果可被当前查询接口识别
3. 生成结果可被当前阅片链路读取

#### 两阶段验收链路

1. 阶段一：`Python 造数服务 -> C-STORE -> Java PACS -> QIDO/WADO -> 阅片页`
2. 阶段二：`Python 造数服务 -> 模拟上游 PACS -> Java PACS 定时拉取 -> QIDO/WADO -> 阅片页`

#### 两阶段验收要求

1. 阶段一必须验证生成样本的直接入库、查询、读取、展示闭环
2. 阶段二必须验证模拟上游接入、定时同步、执行记录、防回环、重复同步幂等闭环
3. 两阶段都要至少覆盖 CT / MR / DX / US 多帧等关键样本类型

## 阶段 6：Cornerstone3 阅片 V1

### 目标

建立一版接近 OHIF 使用体验的业务内嵌阅片页。

### 工作项

1. 设计 Viewer 页面骨架
2. 引入 Cornerstone3 核心能力
3. 实现 Study / Series / Instance 浏览
4. 实现缩略图与预加载
5. 实现单视口、双视口、四宫格
6. 实现基础工具栏与测量能力
7. 实现多帧浏览与基础播放
8. 调整 UI 风格，建立专业化深色阅片布局
9. 补充后端缩略图 / 聚合接口

### 强约束

1. 先设计布局系统
2. 视口状态集中管理
3. 工具状态集中管理
4. 预加载从第一版开始考虑
5. 压缩语法兼容尽早验证

### 测试要求

#### 前端单元测试

1. viewer store 状态管理测试
2. study / series 切换测试
3. 工具栏状态切换测试
4. 多视口布局切换测试

#### 前端集成测试

1. 基础 Study 打开流程测试
2. 缩略图加载测试
3. 多帧浏览测试
4. 工具操作链路测试

#### 联调测试

1. WADO-RS 加载测试
2. QIDO-RS 查询到展示闭环测试
3. CT / MR / DX / US 多帧样本展示测试

## 3. 模块拆分建议

## Java 侧

建议补充或演进模块：

1. `pacs-infrastructure.storage`
2. `pacs-infrastructure.lock`
3. `pacs-infrastructure.persistence.dialect`
4. `pacs-infrastructure.rest.viewer`

## Python 侧

建议新增独立目录：

```text
dicom-generator/
  app/
  tests/
  templates/
```

## 前端侧

建议新增：

```text
src/viewer/
  components/
  stores/
  services/
  utils/
```

## 4. 推荐实施顺序

为了降低返工风险，固定顺序如下：

1. 公开测试样本与模板库建设
2. Storage SPI
3. Lock SPI
4. JPA + JdbcTemplate + Dialect 能力抽象
5. Python 造数服务
6. Cornerstone3 阅片 V1

## 5. 验收标准

达到以下条件视为本轮计划完成：

1. 存储层可切换 Local / FastDFS / S3 Compatible
2. 锁层可切换 Database，并预留 Redis / ZK 扩展口
3. JPA 主链路保持稳定，SQL 能力集中治理
4. Python 造数服务可稳定生成并输出标准 DICOM 对象
5. 当前 PACS 能完成“直推入库”和“模拟上游定时拉取”两阶段接入验收
6. 前端能以 Cornerstone3 实现一版接近 OHIF 的基础阅片体验
7. 每个阶段都具备对应单元测试与必要集成测试
