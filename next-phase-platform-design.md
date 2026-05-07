# PACS 平台下一阶段设计定稿

## 1. 背景

当前项目已经完成：

1. 标准 DICOM DIMSE 互通（C-ECHO / C-STORE / C-FIND / C-MOVE / 主动推送）
2. 定时同步平台、执行记录、任务配置、治理能力、前端管理页
3. 多实例调度保护、基础限流、回流防护规则

下一阶段不再优先推进 AI 调度中心，先补平台底座与阅片能力。

## 2. 本次敲定范围

本次敲定并进入开发计划的内容：

1. Python 造数服务
2. 公开测试影像样本补充与模板库建设
3. Storage SPI
4. Distributed Lock SPI
5. JPA + JdbcTemplate + 方言能力抽象
6. Cornerstone3 阅片方案（目标体验向 OHIF 靠拢）

本次明确暂缓：

1. AI 调度中心
2. 更完整的统一重试中心

## 3. Python 造数服务方案

## 3.1 方案结论

造数服务采用 Python 独立实现，通过 HTTP 或任务方式与 Java PACS 主服务解耦。

原因：

1. Python 医学影像处理生态更成熟
2. 更适合做 DICOM 标签改写、像素变换、批量生成
3. 后续若接入 AI，可直接复用 Python 影像处理链路

推荐技术栈：

- FastAPI
- pydicom
- highdicom
- numpy
- Pillow
- SimpleITK
- OpenCV

## 3.2 数据来源策略

当前模板影像不足，因此不采用“仅依赖现有模板”的方案，而是三种来源并行：

1. 现有少量模板影像
2. 公开测试影像样本库
3. 纯 DICOM 对象生成（适合部分类型）

公开测试样本库的用途：

1. 作为 PACS 联调数据
2. 作为 Python 造数模板库
3. 作为前端阅片兼容性测试数据

样本类型覆盖目标不是“先覆盖几类主流影像”，而是**尽可能覆盖当前项目后续可能接入、传输、存储、查询、阅片、造数的全部 DICOM 对象类型**。

样本库建设按以下分类维度尽量铺满：

1. 诊断影像：
   - CT
   - MR
   - PET
   - NM
   - XA / XRF
   - US（单帧 / 多帧）
   - MG
   - CR / DX
   - IO / OCT / Ophthalmic
   - Endoscopy / Microscopy / Visible Light
2. 放疗相关对象：
   - RT Image
   - RT Structure Set
   - RT Plan
   - RT Dose
   - 新版 RT 对象（条件允许时）
3. 报告与结构化对象：
   - SR
   - Encapsulated PDF
   - CDA（如可获得）
4. 分割与标注对象：
   - SEG
   - Parametric Map
   - PR / GSPS
5. 视频与多媒体对象：
   - Video Endoscopic
   - Video Microscopic
   - Video Photographic
   - 其他封装视频对象
6. 波形与其他特殊对象：
   - ECG / Waveform
   - Secondary Capture
   - OT
   - Raw Data（若可获得）

除对象类型外，还要尽量覆盖以下兼容维度：

1. 单帧 / 多帧
2. 非压缩 / JPEG / JPEG-LS / JPEG2000 / RLE
3. 显式 VR / 隐式 VR
4. 不同 Photometric Interpretation
5. 不同位深、像素表示、分辨率、体位和方向信息

## 3.3 造数模式

### 模式 A：模板改写型

基于现有或公开样本批量改写：

- PatientID
- AccessionNumber
- StudyInstanceUID
- SeriesInstanceUID
- SOPInstanceUID
- StudyDate / StudyTime
- SeriesNumber / InstanceNumber
- 机构、设备、检查描述等元数据

该模式为第一优先级。

### 模式 B：像素变换增强型

在模板改写基础上补充：

- 旋转
- 翻转
- 缩放
- 裁剪
- 轻噪声
- 轻模糊
- 水印 / 叠字
- 多帧顺序变化

该模式为第二优先级。

### 模式 C：纯对象生成型

用于补齐部分模板缺失类型，不作为初始主路径。

优先适合：

1. Secondary Capture
2. OT
3. Encapsulated PDF
4. Video
5. 简单 US 多帧

不将高质量 CT / MR / PET 纯合成作为初期目标。

## 3.4 服务形态

推荐接口：

1. `POST /generate/jobs`
2. `GET /generate/jobs/{id}`
3. `POST /generate/push-to-pacs`
4. `POST /generate/export`

输出模式：

1. 写入模板输出目录
2. 直接 C-STORE 推送到 PACS
3. 写入“模拟上游 PACS”供当前平台定时拉取

## 3.5 造数服务接入 PACS 的两阶段验收链路

造数服务接入 PACS 不只验“能生成文件”，而是按两阶段闭环验收：

### 阶段一：直接推送入 PACS

链路：

`Python 造数服务 -> C-STORE 推送 -> Java PACS 接收入库 -> QIDO/WADO 查询读取 -> 前端阅片展示`

本阶段目标：

1. 验证 Python 造数结果是标准可接收 DICOM 对象
2. 验证当前 PACS 的接收、入库、索引、查询、读取链路完整可用
3. 验证造数后的 Study / Series / Instance UID、PatientID、AccessionNumber 改写结果能在 PACS 中正确落库并被检索
4. 验证生成样本能直接进入后续阅片联调数据集

本阶段必验项：

1. PACS 可成功接收并保存生成样本
2. QIDO-RS 可按患者、检查、序列、实例检索到生成结果
3. WADO-RS 可读取实例内容
4. 前端阅片链路可打开至少 CT / MR / DX / US 多帧等关键样本
5. 重复推送时满足现有幂等策略，不破坏既有数据

### 阶段二：模拟上游 PACS 后由当前平台定时拉取

链路：

`Python 造数服务 -> 写入/推送到模拟上游 PACS -> Java PACS 定时同步任务拉取 -> Java PACS 本地入库 -> QIDO/WADO 查询读取 -> 前端阅片展示`

本阶段目标：

1. 验证造数服务可作为“外部上游数据源”接入，而不只是一段离线脚本
2. 验证当前已完成的定时同步平台可稳定拉取生成样本
3. 验证同步任务治理能力，包括限流、执行记录、失败处理、自动暂停、回流防护
4. 验证生成样本在真实多系统流转场景中仍可被 PACS 正确接收与展示

本阶段必验项：

1. 模拟上游 PACS 中的新样本可被当前同步任务发现并拉取
2. 拉取后的样本可被当前 PACS 查询、读取、展示
3. 执行记录中可看到成功、失败、跳过等状态
4. 防回环规则生效，避免当前 PACS 再次回推自身来源数据
5. 同一批样本重复同步时满足去重/幂等要求

### 两阶段的定位

1. 阶段一优先验证“造数服务本身 + PACS 接收链路”
2. 阶段二优先验证“造数服务作为外部数据源 + PACS 定时同步链路”
3. 两阶段都通过，才视为“造数服务正式接入 PACS”验收完成

## 4. 存储方案定稿

## 4.1 总体结论

存储采用 SPI 方式扩展，不让业务层直接感知底层实现。

## 4.2 存储实现定稿

首批支持：

1. `LocalStorageProvider`
2. `FastDfsStorageProvider`
3. `S3CompatibleStorageProvider`

说明：

- 本地存储继续保留，支持普通目录、挂载磁盘、NAS 挂载目录
- FastDFS 作为老项目兼容实现保留
- 新对象存储默认以 `S3CompatibleStorageProvider` 为统一抽象

## 4.3 SeaweedFS 替代 MinIO

MinIO 不再作为新部署默认选项。

新对象存储默认推荐：

- SeaweedFS（首选）

后续可在 `S3CompatibleStorageProvider` 下继续兼容：

- Ceph RGW
- 云厂商 S3 / OSS / COS / OBS 类服务

这样可以避免在代码层绑定某一个具体对象存储产品。

## 4.4 存储抽象建议

建议统一抽象为：

```java
public interface ObjectStorageProvider {
    StorageWriteResult save(String path, InputStream stream, Map<String, String> metadata);
    InputStream read(String path);
    boolean exists(String path);
    void delete(String path);
    String resolveAccessPath(String path);
}
```

## 4.5 元数据建议

实例文件元数据从“仅保存 filePath”升级为更通用结构：

- storageType
- storageBucket
- storageKey
- fileSize
- checksum
- storageClass

## 5. 分布式锁方案定稿

## 5.1 总体结论

分布式锁统一走 SPI，当前默认实现继续使用数据库锁。

## 5.2 首批实现

1. `DatabaseLockProvider`（默认）
2. `RedissonLockProvider`（后续支持）
3. `ZookeeperLockProvider`（后续支持）

## 5.3 抽象建议

```java
public interface DistributedLockProvider {
    boolean tryLock(String key, Duration leaseTime);
    void unlock(String key);
    boolean renew(String key, Duration leaseTime);
}
```

说明：

- 当前系统已有数据库锁能力，先抽象为默认实现
- 后续多实例数量和并发量提升时，再引入 Redis / ZK 实现

## 6. 数据访问层方案定稿

## 6.1 总体结论

数据库访问层继续采用：

1. JPA 作为主路线
2. JdbcTemplate 作为补位
3. 方言按能力抽象

不在当前阶段切换到 MyBatis-Plus。

## 6.2 为什么保留 JPA

当前系统实体关系明确，CRUD 与中等复杂度查询为主，JPA 足够支撑：

- patient
- study
- series
- instance
- sync_job_config
- sync_execution

JPA 继续负责：

1. 实体映射
2. 常规 CRUD
3. 一般条件查询

## 6.3 为什么还需要 JdbcTemplate

不是因为 JPA 不支持原生 SQL，而是因为以下场景使用 JdbcTemplate 更直接：

1. 分布式锁抢锁 / 释放
2. 批量更新
3. 统计聚合
4. 不需要实体映射的轻量查询
5. 对 SQL 结构有明确控制要求的基础设施语句

JPA 仍支持原生 SQL：

- `@Query(nativeQuery = true)`
- `EntityManager.createNativeQuery(...)`

因此最终选择是“组合使用”，而不是替换。

## 6.4 方言策略

方言问题不由 ORM 自动消除，因此采用“按能力抽象”而非“按数据库散落多套 SQL”的方式。

建议抽象能力：

1. 分页
2. UPSERT / MERGE
3. 锁语句
4. JSON / CLOB 兼容
5. 日期时间函数

建议结构：

```text
infrastructure/
  persistence/
    jpa/
    jdbc/
    dialect/
      DatabaseDialectAdapter
      PostgresDialectAdapter
      MySqlDialectAdapter
      OracleLikeDialectAdapter
```

## 7. 阅片方案定稿

## 7.1 总体结论

前端阅片选择方案 A：

- 使用 Cornerstone3 自建阅片能力
- 产品体验目标对齐 OHIF
- 后续如有更强临床需求，再评估引入 OHIF

## 7.2 五条强约束

从设计初期开始就必须坚持：

1. 不是只把 Cornerstone3 跑起来，而是按产品级阅片页设计
2. 先设计布局系统，再做工具细节
3. 缩略图和预加载从第一阶段就考虑
4. 工具状态和视口状态集中管理
5. 压缩传输语法兼容尽早验证

## 7.3 页面目标能力

### 第一阶段

1. Study / Series / Instance 树
2. 缩略图列表
3. 单视口、双视口、四宫格
4. 窗宽窗位、缩放、平移、反相
5. 堆栈滚动
6. 多帧播放
7. 基础测量工具
8. 美观、统一、偏专业化 UI

### 第二阶段

1. MPR
2. 多序列联动
3. Cine 播放控制
4. 预加载和缓存优化
5. 工具面板 / 测量面板 / 信息面板

### 第三阶段

1. SEG 叠加
2. SR 展示
3. PR 支持
4. PDF / 视频对象展示
5. 标注持久化

## 7.4 前端结构建议

```text
ViewerPage
  ├─ ViewerHeader
  ├─ StudySidebar
  │   ├─ PatientInfoCard
  │   ├─ SeriesTree
  │   └─ ThumbnailList
  ├─ ViewerToolbar
  ├─ ViewportGrid
  │   ├─ ViewportPane
  │   ├─ CineControl
  │   └─ OverlayInfo
  └─ RightPanel
      ├─ MeasurementsPanel
      ├─ InstanceInfoPanel
      └─ DisplaySettingsPanel
```

## 7.5 后端接口策略

后端继续复用现有：

1. QIDO-RS
2. WADO-RS

后续补充：

1. 缩略图接口
2. Study / Series / Instance 聚合接口
3. 多帧访问优化接口
4. SR / SEG / PR 查询接口

## 8. 总体开发顺序

按当前优先级，下一阶段开发顺序固定为：

1. 公开测试影像样本补充与模板库目录建设
2. Storage SPI
3. Lock SPI
4. JPA + JdbcTemplate + Dialect 能力抽象
5. Python 造数服务
6. Cornerstone3 阅片 V1

## 9. 结论

本次方案敲定后，项目下一阶段的核心原则为：

1. 先做底座，再做能力扩展
2. 对象存储走统一抽象，默认面向 S3 兼容能力
3. 数据访问继续以 JPA 为主，少量 SQL 集中治理
4. 阅片从第一天就按“接近 OHIF”的产品目标设计
5. Python 造数服务与 Java PACS 主服务解耦演进
