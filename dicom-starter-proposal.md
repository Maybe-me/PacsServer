# DICOM PACS Starter 设计方案

## 1. 目标

构建一个**完整的 DICOM PACS**，具备影像存储、查询、检索、阅片能力：

| 能力 | DICOM服务 | 说明 |
|------|-----------|------|
| **拉取他人影像** | C-FIND SCU + C-MOVE SCU | 主动查询并从其他PACS拉取影像到本地存储 |
| **被他人拉取** | C-FIND SCP + C-MOVE SCP | 外部PACS查询本地影像并发起C-MOVE，本服务通过C-STORE SCU发送 |
| **接收他人推送** | C-STORE SCP | 其他PACS主动推送影像到本服务，存入本地 |
| **推送到他人** | C-STORE SCU | 主动将本地影像推送到其他PACS |
| **连接验证** | C-ECHO SCU/SCP | 验证DICOM节点连通性 |
| **Web阅片** | WADO-RS / DICOMweb | 浏览器端查看影像，支持2D/3D渲染 |

## 2. 技术栈

| 层 | 技术选型 | 说明 |
|----|---------|------|
| **语言** | Java 21 | Virtual Threads + Record + Pattern Matching |
| **框架** | Spring Boot 3.3+ | 自动配置、Web、Data JPA |
| **网络层** | Netty 4.1+ | DICOM协议TCP传输，高并发异步IO |
| **DICOM** | dcm4che 5.21+ | DICOM编解码、Attributes操作、SOP Class |
| **ORM** | Spring Data JPA | 领域模型持久化 |
| **前端** | Vue 3 + Ant Design Vue | 管理界面 + 阅片UI |
| **3D渲染** | Cornerstone3D | WebGL医学影像2D/MPR/VR渲染 |
| **构建** | Maven | 多模块DDD结构 |

### 2.1 为什么用Netty替代dcm4che内置网络层

dcm4che的`Device`/`NetworkApplicationEntity`基于Java NIO（老式Selector），存在：
- 单线程事件循环，高并发下瓶颈明显
- 连接管理不灵活，无法精细控制背压和超时
- 与Spring Boot生命周期集成困难

Netty优势：
- **EventLoopGroup多线程**：boss+worker线程模型，轻松处理数千并发连接
- **Pipeline模式**：DICOM协议解码/编码独立为ChannelHandler，可插拔
- **背压控制**：大文件C-STORE传输时防止OOM
- **与Spring Boot集成**：通过`@PostConstruct`/`@PreDestroy`管理生命周期

### 2.2 数据存储模型

- **DICOM文件**：文件系统，路径 `{storage_dir}/{PatientID}/{StudyUID}/{SeriesUID}/{SOPInstanceUID}.dcm`
- **Tag索引**：关系数据库，Patient→Study→Series→Instance四级，高频Tag存字段，低频Tag存extra_tags JSON
- **关联**：`pacs_instance.file_path` 指向文件系统中的.dcm文件

## 3. DDD架构

### 3.1 分层

```
┌──────────────────────────────────────────────────────────────┐
│                    Interface Layer (接口层)                    │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────────┐ │
│  │ DICOM SCP  │  │ DICOM SCU │  │ REST API (DICOMweb)    │ │
│  │ (Netty)    │  │ (Netty)   │  │ (Spring MVC)           │ │
│  └─────┬──────┘  └─────┬──────┘  └───────────┬────────────┘ │
├────────┼───────────────┼─────────────────────┼──────────────┤
│        │         Application Layer (应用层)     │             │
│  ┌─────▼──────────────▼─────────────────────▼───────────┐   │
│  │  DicomApplicationService  AetApplicationService      │   │
│  │  ViewerApplicationService                             │   │
│  └───────────────────────┬──────────────────────────────┘   │
├──────────────────────────┼─────────────────────────────────┤
│                    Domain Layer (领域层)                     │
│  ┌──────────────────────▼───────────────────────────────┐    │
│  │  model/              service/           repository/   │    │
│  │  PacsPatient         DicomStoreSvc      PacsPatientR. │    │
│  │  PacsStudy           DicomQuerySvc      PacsStudyRepo │    │
│  │  PacsSeries          DicomRetrieveSvc   PacsSeriesR.  │    │
│  │  PacsInstance        AetManager         PacsInstanceR.│    │
│  │  AetNode                                AetNodeRepo   │    │
│  └───────────────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────────────┤
│                Infrastructure Layer (基础设施层)               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ Netty    │ │ File     │ │ JPA      │ │ dcm4che  │        │
│  │ Transport│ │ Storage  │ │ MySQL/PG │ │ Codec    │        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 Maven多模块

```
pacs-server/                              # 父POM
├── pacs-common/                          # 共享工具+常量
│   └── .../common/
│       ├── constant/  DicomTagConst, SopClassConst
│       ├── util/      DicomTagUtil, AttributesConverter
│       └── exception/ PacsException
│
├── pacs-domain/                          # 领域层(纯Java，无框架依赖)
│   └── .../domain/
│       ├── model/                        # 领域模型(Java Record)
│       │   ├── PacsPatient.java
│       │   ├── PacsStudy.java
│       │   ├── PacsSeries.java
│       │   ├── PacsInstance.java
│       │   └── AetNode.java
│       ├── repository/                   # 仓储接口(Interface)
│       │   ├── PacsPatientRepository.java
│       │   ├── PacsStudyRepository.java
│       │   ├── PacsSeriesRepository.java
│       │   ├── PacsInstanceRepository.java
│       │   └── AetNodeRepository.java
│       └── service/                     # 领域服务
│           ├── DicomStoreDomainService.java
│           ├── DicomQueryDomainService.java
│           ├── DicomRetrieveDomainService.java
│           └── AetManager.java
│
├── pacs-application/                     # 应用层(用例编排)
│   └── .../application/
│       ├── DicomApplicationService.java  # 核心用例入口
│       ├── AetApplicationService.java    # AE Title管理用例
│       └── ViewerApplicationService.java # 阅片用例
│
├── pacs-infrastructure/                  # 基础设施层
│   └── .../infrastructure/
│       ├── netty/                        # Netty DICOM协议实现
│       │   ├── DicomServerBootstrap.java # SCP服务端启动
│       │   ├── DicomClientBootstrap.java # SCU客户端启动
│       │   ├── codec/
│       │   │   ├── DicomMessageDecoder.java   # PDU/PDATA解码
│       │   │   ├── DicomMessageEncoder.java   # PDU/PDATA编码
│       │   │   └── DicomPartDecoder.java      # 分片解码
│       │   └── handler/
│       │       ├── CStoreScpHandler.java
│       │       ├── CFindScpHandler.java
│       │       ├── CMoveScpHandler.java
│       │       ├── CEchoScpHandler.java
│       │       └── ScuHandler.java            # SCU通用处理器
│       ├── persistence/                  # JPA仓储实现
│       │   ├── JpaPacsPatientRepository.java
│       │   ├── JpaPacsStudyRepository.java
│       │   ├── JpaPacsSeriesRepository.java
│       │   ├── JpaPacsInstanceRepository.java
│       │   ├── JpaAetNodeRepository.java
│       │   └── entity/                  # JPA Entity(DB映射)
│       │       ├── PatientEntity.java
│       │       ├── StudyEntity.java
│       │       ├── SeriesEntity.java
│       │       ├── InstanceEntity.java
│       │       └── AetNodeEntity.java
│       ├── storage/                      # 文件存储实现
│       │   └── LocalFileStorage.java
│       └── dicomweb/                    # DICOMweb REST实现
│           ├── WadoRsController.java
│           └── QidoRsController.java
│
├── pacs-viewer/                          # 前端阅片模块
│   ├── package.json
│   ├── src/
│   │   ├── views/
│   │   │   ├── PatientList.vue          # 患者列表
│   │   │   ├── StudyList.vue            # 检查列表
│   │   │   ├── SeriesList.vue           # 序列列表
│   │   │   └── Viewer.vue              # 阅片(2D/MPR/VR)
│   │   ├── components/
│   │   │   ├── DicomViewport.vue        # Cornerstone3D封装
│   │   │   ├── Toolbar.vue              # 工具栏(WW/WL/Zoom/Pan/Measure)
│   │   │   └── StudyBrowser.vue         # 缩略图浏览
│   │   └── api/                         # 后端API调用
│   └── vite.config.ts
│
├── pacs-boot/                            # 启动模块
│   └── src/main/java/.../boot/
│       ├── PacsApplication.java         # @SpringBootApplication
│       └── config/
│           ├── NettyConfig.java
│           ├── JpaConfig.java
│           └── WebConfig.java
│
└── pacs-test/                            # 集成测试
    └── src/test/java/.../test/
        ├── integration/                  # 集成测试
        └── bench/                        # 性能基准测试
```

### 3.3 设计原则

1. **DDD分层**：Domain层纯Java无框架依赖，Infrastructure层实现技术细节
2. **完整PACS**：SCU+SCP双角色，独立PACS节点
3. **Netty高并发**：DICOM协议栈基于Netty Pipeline实现
4. **可插拔**：SCP能力可配置开关

## 4. DICOM协议封装 & AE Title管理

### 4.1 Netty Pipeline

```
SCP服务端 Pipeline:                          SCU客户端 Pipeline:
┌─────────────────────┐                     ┌─────────────────────┐
│ IdleStateHandler    │ ← 超时检测          │ IdleStateHandler    │
│ DicomMessageDecoder │ ← A-ASSOCIATE解析   │ DicomMessageEncoder │ → A-ASSOCIATE构建
│ DicomPartDecoder    │ ← P-DATA分片重组     │ DicomPartEncoder   │ → P-DATA分片发送
│ CStoreScpHandler    │ ← C-STORE业务处理   │ ScuHandler         │ → C-FIND/MOVE/STORE
│ CFindScpHandler     │ ← C-FIND业务处理    │                    │
│ CMoveScpHandler     │ ← C-MOVE业务处理    │                    │
│ CEchoScpHandler     │ ← C-ECHO业务处理    │                    │
└─────────────────────┘                     └─────────────────────┘
```

```java
@Component
public class DicomServerBootstrap {
    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();

    public void start(int port) {
        new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(new IdleStateHandler(120, 0, 0, SECONDS))
                        .addLast(new DicomMessageDecoder())
                        .addLast(new DicomPartDecoder())
                        .addLast(new CEchoScpHandler(aetManager))
                        .addLast(new CStoreScpHandler(storeService))
                        .addLast(new CFindScpHandler(queryService))
                        .addLast(new CMoveScpHandler(retrieveService, storeScu));
                }
            }).bind(port).sync();
    }
}
```

### 4.2 AE Title管理

```java
// AetNode — 领域模型 (Java 21 Record)
public record AetNode(
    Long id,
    String aet,          // AE Title
    String host,         // IP/Hostname
    int port,            // 端口
    AetRole role,        // LOCAL / REMOTE / BOTH
    String description,
    boolean enabled
) {}

public enum AetRole { LOCAL, REMOTE, BOTH }
```

```java
// AetManager — 领域服务
public class AetManager {
    private final AetNodeRepository repo;
    public AetNode getLocalAet() { ... }
    public Optional<AetNode> findByAet(String aet) { ... }
    public AetNode register(AetNode node) { ... }
    public boolean verifyConnection(String aet) { ... }  // C-ECHO验证
}
```

AE Title管理功能：
- **本地AET**：本PACS的AE Title，SCP声明身份 + SCU发起连接
- **远程AET**：已知外部PACS节点，C-MOVE SCP根据MoveDestination查表回送
- **动态注册**：运行时增删AE Title，无需重启
- **连接验证**：C-ECHO验证已注册节点可达性

## 5. 核心流程

### 5.1 接收推送 (C-STORE SCP → 存储)

```
外部PACS ──C-STORE──▶ CStoreScpHandler (Netty ChannelHandler)
  ├─ 1. DicomPartDecoder重组 → Attributes
  ├─ 2. LocalFileStorage.storeFile() → 写入文件系统
  ├─ 3. DicomStoreDomainService.indexInstance()
  │     ├─ upsert pacs_patient
  │     ├─ upsert pacs_study
  │     ├─ upsert pacs_series
  │     └─ insert pacs_instance (file_path, file_size, md5)
  └─ 4. 返回 C-STORE RSP (Success/Warning/Failure)
```

### 5.2 主动拉取 (C-FIND SCU → C-MOVE SCU)

```
DicomApplicationService.pullFromRemote(aet, query):
  ├─ DicomFindSCU.find() ──C-FIND──▶ 外部PACS → 返回Study列表
  └─ DicomMoveSCU.move() ──C-MOVE──▶ 外部PACS
       外部PACS ──C-STORE──▶ CStoreScpHandler → 走5.1存储流程
```

### 5.3 响应外部查询 (C-FIND SCP)

```
外部PACS ──C-FIND──▶ CFindScpHandler
  ├─ 解析查询级别 (PATIENT/STUDY/SERIES)
  ├─ DicomQueryDomainService.query(level, keys) → 查DB
  └─ 逐条返回匹配Attributes (Pending) → 最终返回 Success
```

### 5.4 响应外部拉取 (C-MOVE SCP → C-STORE SCU)

```
外部PACS ──C-MOVE──▶ CMoveScpHandler
  ├─ AetManager.findByAet(destination) → 解析请求方地址
  ├─ DicomRetrieveDomainService.getFiles(studyUid) → 查pacs_instance
  └─ DicomStoreSCU.store(destination, files) → 逐个C-STORE发送
```

### 5.5 主动推送 (C-STORE SCU)

```
DicomApplicationService.pushToRemote(aet, studyUid):
  ├─ DicomRetrieveDomainService.getFiles(studyUid) → 查pacs_instance
  └─ DicomStoreSCU.store(target, files) → 逐个C-STORE发送
```

### 5.6 Web阅片 (DICOMweb)

```
浏览器 ──WADO-RS──▶ WadoRsController
  ├─ GET /wado-rs/studies/{study}/series/{series}/instances/{instance}
  ├─ DicomRetrieveDomainService.getFile(sopUid) → 读文件
  └─ 返回DICOM Part 10文件 (application/dicom)

浏览器 ──QIDO-RS──▶ QidoRsController
  ├─ GET /qido-rs/studies?PatientID=xxx&StudyDate=20260101
  ├─ DicomQueryDomainService.query(STUDY, keys) → 查DB
  └─ 返回JSON (application/dicom+json)
```

## 6. 阅片模块

### 6.1 前端技术栈

| 技术 | 用途 |
|------|------|
| **Vue 3** | SPA框架，Composition API |
| **Ant Design Vue** | UI组件库(表格、表单、布局) |
| **Cornerstone3D** | 医学影像渲染引擎(WebGL) |
| **Vite** | 构建工具 |

### 6.2 页面结构

```
┌──────────────────────────────────────────────────────────┐
│  顶部导航: 患者列表 | 检查列表 | 阅片 | 系统管理(AET)   │
├──────────┬───────────────────────────────────────────────┤
│          │                                               │
│  左侧    │              主视图区                          │
│  患者列表│                                               │
│  /检查列表│    ┌─────────────────────────────────┐        │
│  /序列列表│    │     Cornerstone3D Viewport      │        │
│          │    │     (2D / MPR / VR渲染)          │        │
│          │    └─────────────────────────────────┘        │
│          │    ┌─────────────────────────────────┐        │
│          │    │  Toolbar: WW/WL|Zoom|Pan|Measure │        │
│          │    │  Annotate|MPR|VR|Invert|Reset    │        │
│          │    └─────────────────────────────────┘        │
└──────────┴───────────────────────────────────────────────┘
```

### 6.3 Cornerstone3D集成

```typescript
// DicomViewport.vue
import { RenderingEngine, Enums } from '@cornerstonejs/core';
import { init as cs3dInit } from '@cornerstonejs/core';

cs3dInit({ renderingEngineId: 'pacs-engine' });

const engine = new RenderingEngine('pacs-engine');
// 支持渲染模式: STACK(2D), MPR(三视图), VOLUME(3D VR)
```

### 6.4 后端API

| API | 方法 | 说明 |
|-----|------|------|
| `/api/patients` | GET | 患者列表(分页) |
| `/api/studies` | GET | 检查列表(按PatientID/Date过滤) |
| `/api/series` | GET | 序列列表(按StudyUID过滤) |
| `/api/instances/{sopUid}/thumbnail` | GET | 缩略图 |
| `/wado-rs/studies/{study}/series/{series}/instances/{instance}` | GET | 获取DICOM文件 |
| `/qido-rs/studies` | GET | QIDO-RS查询 |
| `/api/aet-nodes` | CRUD | AE Title管理 |
| `/api/dicom/echo` | POST | C-ECHO验证 |
| `/api/dicom/find` | POST | C-FIND查询 |
| `/api/dicom/move` | POST | C-MOVE拉取 |
| `/api/dicom/store` | POST | C-STORE推送 |

## 7. 数据库设计

### 7.1 设计原则

1. **DICOM标准四级模型**：Patient → Study → Series → Instance
2. **只存索引Tag**：高频查询Tag存表字段，低频Tag存extra_tags JSON，需要时从文件读取
3. **C-FIND查询优化**：索引覆盖 PatientID, StudyDate, AccessionNo, Modality 等常见查询组合
4. **C-MOVE检索优化**：pacs_instance.file_path 直接定位文件，无需遍历目录

### 7.2 核心表

#### pacs_patient

```sql
CREATE TABLE pacs_patient (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id       VARCHAR(128) NOT NULL,
    patient_name     VARCHAR(256),
    patient_sex      VARCHAR(4),
    patient_birth_date VARCHAR(32),
    extra_tags       JSON          COMMENT '低频Tag: Tel, Addr, Comments等',
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_patient_id (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### pacs_study

```sql
CREATE TABLE pacs_study (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_fk       BIGINT        NOT NULL,
    study_instance_uid VARCHAR(128) NOT NULL,
    accession_no     VARCHAR(128),
    study_date       VARCHAR(16),
    study_time       VARCHAR(16),
    study_desc       VARCHAR(256),
    modalities_in_study VARCHAR(128),
    referring_dr     VARCHAR(128),
    num_series       INT           DEFAULT 0,
    num_instances    INT           DEFAULT 0,
    extra_tags       JSON          COMMENT '低频Tag: Institution, Station等',
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_study_uid (study_instance_uid),
    KEY idx_patient_fk (patient_fk),
    KEY idx_accession_no (accession_no),
    KEY idx_study_date (study_date),
    KEY idx_patient_study_date (patient_fk, study_date),
    CONSTRAINT fk_study_patient FOREIGN KEY (patient_fk) REFERENCES pacs_patient(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### pacs_series

```sql
CREATE TABLE pacs_series (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    study_fk         BIGINT        NOT NULL,
    series_instance_uid VARCHAR(128) NOT NULL,
    modality         VARCHAR(32),
    series_desc      VARCHAR(256),
    body_part_examined VARCHAR(64),
    series_number    INT,
    num_instances    INT           DEFAULT 0,
    extra_tags       JSON          COMMENT '低频Tag: ProtocolName, Operators等',
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_series_uid (series_instance_uid),
    KEY idx_study_fk (study_fk),
    KEY idx_modality (modality),
    CONSTRAINT fk_series_study FOREIGN KEY (study_fk) REFERENCES pacs_study(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### pacs_instance

```sql
CREATE TABLE pacs_instance (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    series_fk        BIGINT        NOT NULL,
    sop_instance_uid VARCHAR(128)  NOT NULL,
    sop_class_uid    VARCHAR(64),
    transfer_syntax_uid VARCHAR(64),
    instance_number  INT,
    file_path        VARCHAR(512)  NOT NULL COMMENT '相对storage_dir的路径',
    file_size        BIGINT,
    file_md5         VARCHAR(32),
    extra_tags       JSON          COMMENT '低频Tag: SliceLocation, ImageComments等',
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sop_instance_uid (sop_instance_uid),
    KEY idx_series_fk (series_fk),
    CONSTRAINT fk_instance_series FOREIGN KEY (series_fk) REFERENCES pacs_series(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### aet_node — AE Title节点

```sql
CREATE TABLE aet_node (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    aet              VARCHAR(64)   NOT NULL,
    host             VARCHAR(128)  NOT NULL,
    port             INT           NOT NULL,
    role             VARCHAR(16)   NOT NULL COMMENT 'LOCAL/REMOTE/BOTH',
    node_name        VARCHAR(128),
    description      VARCHAR(256),
    enabled          BOOLEAN       DEFAULT TRUE,
    last_verified_at TIMESTAMP     COMMENT '最后C-ECHO验证时间',
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_aet (aet)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 7.3 C-FIND SCP 查询映射

| C-FIND Level | 查询条件 | SQL |
|:---:|---|---|
| PATIENT | PatientID, PatientName | `SELECT * FROM pacs_patient WHERE patient_id=?` |
| STUDY | StudyUID, PatientID, AccessionNo, StudyDate, Modality | `SELECT s.* FROM pacs_study s JOIN pacs_patient p WHERE ...` |
| SERIES | SeriesUID, StudyUID, Modality | `SELECT sr.* FROM pacs_series sr JOIN pacs_study s WHERE ...` |

### 7.4 Tag存储策略

| Tag类别 | 存储位置 | 示例 | 查询方式 |
|---------|---------|------|---------|
| **高频查询Tag** | 表字段 | PatientID, StudyUID, AccessionNo, StudyDate, Modality | SQL索引查询 |
| **低频Tag** | extra_tags JSON | InstitutionName, ProtocolName, OperatorsName | JSON函数查询/文件读取 |
| **像素数据** | 仅文件系统 | PixelData (7FE00010) | 从.dcm文件读取 |

### 7.5 数据库选型

| 方案 | 适用规模 | 优势 | 劣势 |
|------|---------|------|------|
| **SQLite** | <10万检查，开发/测试 | 零配置，starter内置 | 不支持并发写入，无网络访问 |
| **MySQL 8.0** | 10万~500万 | 团队熟悉，JSON字段，运维成熟 | 大规模JSON查询慢，水平扩展难 |
| **PostgreSQL 15+** | 10万~500万 | JSONB索引(GIN)，部分索引，性能优 | 学习成本，迁移成本 |
| **MySQL + ES** | >500万 | C-FIND走ES毫秒级，MySQL保一致性 | 双写一致性，运维复杂 |

**推荐路径**：
- starter默认内嵌SQLite，零配置启动
- 生产环境切MySQL（团队熟悉）或PostgreSQL（JSONB性能优）
- 大规模场景加ES做C-FIND查询加速

## 8. 单元测试

### 8.1 测试分层

| 层 | 测试类型 | 框架 | 覆盖范围 |
|----|---------|------|---------|
| **Domain** | 单元测试 | JUnit 5 + Mockito | 领域模型、领域服务 |
| **Application** | 单元测试 | JUnit 5 + Mockito | 用例编排逻辑 |
| **Infrastructure/Netty** | 集成测试 | JUnit 5 + EmbeddedPacs | SCP/SCU协议交互 |
| **Infrastructure/JPA** | 集成测试 | JUnit 5 + Testcontainers | DB读写 |
| **Infrastructure/DICOMweb** | 集成测试 | Spring MockMvc | REST API |
| **E2E** | 端到端测试 | JUnit 5 + dcm4che Tool | 完整DICOM交互 |

### 8.2 Domain层测试

```java
class DicomStoreDomainServiceTest {
    @Test void shouldUpsertPatientOnStore() { ... }
    @Test void shouldUpsertStudyOnStore() { ... }
    @Test void shouldRejectDuplicateSopInstanceUid() { ... }
    @Test void shouldIncrementInstanceCountOnNewInstance() { ... }
}

class AetManagerTest {
    @Test void shouldFindLocalAet() { ... }
    @Test void shouldFindRemoteByAet() { ... }
    @Test void shouldRejectDuplicateAet() { ... }
}

class DicomQueryDomainServiceTest {
    @Test void shouldQueryStudiesByPatientId() { ... }
    @Test void shouldQueryStudiesByDateRange() { ... }
    @Test void shouldQuerySeriesByStudyUid() { ... }
    @Test void shouldReturnEmptyForNoMatch() { ... }
}
```

### 8.3 Netty协议集成测试

```java
// 内置模拟PACS，用于测试SCU/SCP交互
class DicomScpIntegrationTest {
    private EmbeddedPacs server;  // 启动本地SCP
    private EmbeddedPacs client;  // SCU客户端

    @Test void shouldReceiveCStoreAndIndex() {
        // 1. 启动SCP
        server.start(11112);
        // 2. SCU发送C-STORE
        client.store("LOCAL_AET", "127.0.0.1", 11112, testDicomFile);
        // 3. 验证DB写入
        assertThat(patientRepo.findByPatientId("P001")).isPresent();
        assertThat(instanceRepo.findBySopUid(sopUid)).isPresent();
    }

    @Test void shouldRespondToCFind() {
        // 1. 先C-STORE存入数据
        client.store(..., testDicomFile);
        // 2. C-FIND查询
        var results = client.find("LOCAL_AET", "127.0.0.1", 11112,
            FindQuery.studyLevel("P001", null));
        assertThat(results).hasSize(1);
    }

    @Test void shouldRespondToCMove() {
        // C-MOVE → C-STORE回送验证
    }

    @Test void shouldHandleConcurrentCStore() {
        // 并发100个C-STORE连接
        var futures = IntStream.range(0, 100).parallel()
            .mapToObj(i -> client.storeAsync(..., fileN(i)))
            .toList();
        assertThat(futures).allMatch(f -> f.get().isSuccess());
    }
}
```

### 8.4 DICOMweb集成测试

```java
class WadoRsControllerTest {
    @Test void shouldReturnDicomFile() {
        mockMvc.perform(get("/wado-rs/studies/{study}/series/{series}/instances/{instance}",
                studyUid, seriesUid, sopUid))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/dicom"));
    }
}

class QidoRsControllerTest {
    @Test void shouldQueryStudies() {
        mockMvc.perform(get("/qido-rs/studies")
                .param("PatientID", "P001")
                .param("StudyDate", "20260101-"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].0020000D.Value[0]").value(studyUid));
    }
}
```

### 8.5 JPA仓储测试

```java
@DataJpaTest
class PacsStudyRepositoryTest {
    @Test void shouldFindByPatientFkAndStudyDateBetween() { ... }
    @Test void shouldFindByAccessionNo() { ... }
    @Test void shouldFindByStudyInstanceUid() { ... }
}
```

### 8.6 性能基准测试

```java
class CStoreBenchmark {
    @Test void benchmarkConcurrentCStore() {
        // 100并发C-STORE，测量TPS和延迟
        // 目标: >500 TPS, P99 <200ms
    }

    @Test void benchmarkCFindQuery() {
        // 10万Study数据，C-FIND查询性能
        // 目标: <10ms (有索引)
    }
}
```

## 9. 配置

```yaml
dicom:
  pacs:
    local-aet: MY_PACS
    local-port: 11112
    storage-dir: /data/pacs/storage
    file-path-pattern: "{00100020}/{0020000D}/{0020000E}/{00080018}.dcm"
    idle-timeout: 1200000
    netty:
      boss-threads: 1
      worker-threads: 0          # 0=自动(CPU核数*2)
      max-connections: 1000
      send-buffer-size: 1MB
      receive-buffer-size: 1MB
    db-type: sqlite              # sqlite / mysql / postgresql
    scu:
      enabled: true
      find-retry-count: 10
      connect-timeout: 10000
    scp:
      enabled: true
      find-enabled: true         # 允许外部C-FIND查询
      move-enabled: true         # 允许外部C-MOVE拉取
      store-enabled: true        # 允许外部C-STORE推送
```

## 10. 实施步骤

1. **Phase 1**：Maven多模块骨架 + DDD分层 + Domain模型
2. **Phase 2**：Netty DICOM协议栈（Codec + Pipeline）+ C-ECHO SCP/SCU
3. **Phase 3**：C-STORE SCP + 文件存储 + JPA持久化（核心存储闭环）
4. **Phase 4**：C-FIND SCP + DicomQueryDomainService（响应外部查询）
5. **Phase 5**：C-MOVE SCP + C-STORE SCU（响应外部拉取 + 主动推送）
6. **Phase 6**：C-FIND SCU + C-MOVE SCU（主动拉取他人）
7. **Phase 7**：DICOMweb REST API (WADO-RS + QIDO-RS)
8. **Phase 8**：Vue3阅片前端 + Cornerstone3D集成
9. **Phase 9**：AE Title管理界面 + 系统管理页面
10. **Phase 10**：多数据库适配（SQLite/MySQL/PG）+ 完整单元测试 + 性能测试

## 11. 遗漏补充

### 11.1 安全认证

```
DICOM层安全:
├── DICOM TLS (可选)          # TLS加密DICOM连接，配置keystore/truststore
└── AET白名单                  # SCP只接受已知AET的连接请求

DICOMweb层安全:
├── JWT认证                    # Spring Security + JWT Token
├── RBAC角色控制               # admin / radiologist / viewer
└── CORS配置                   # 前端跨域访问控制
```

```yaml
dicom:
  pacs:
    security:
      tls-enabled: false
      keystore-path: /etc/pacs/keystore.p12
      truststore-path: /etc/pacs/truststore.p12
      aet-whitelist: [PACS_A, PACS_B]   # 空列表=接受所有
    dicomweb:
      auth-enabled: true
      jwt-secret: ${JWT_SECRET}
```

### 11.2 IssuerOfPatientID

不同机构PatientID可能重复，需增加颁发者字段：

```sql
ALTER TABLE pacs_patient ADD COLUMN issuer_of_patient_id VARCHAR(128) COMMENT 'PatientID颁发者(00100021)';
ALTER TABLE pacs_patient DROP KEY uk_patient_id;
ALTER TABLE pacs_patient ADD UNIQUE KEY uk_patient_issuer (patient_id, issuer_of_patient_id);
```

### 11.3 STOW-RS (DICOMweb C-STORE over HTTP)

```java
@RestController
@RequestMapping("/stow-rs")
public class StowRsController {
    @PostMapping("/studies")
    public ResponseEntity<?> store(HttpServletRequest request) {
        // 解析multipart/related body中的DICOM文件
        // 调用 DicomStoreDomainService.indexInstance()
    }
}
```

### 11.4 缩略图生成

```java
@Component
public class ThumbnailService {
    @Async  // Virtual Thread异步
    public void generateThumbnail(String sopInstanceUid, String dicomFilePath) {
        // 1. dcm4che读取DICOM文件，提取中间帧像素
        // 2. 缩放到128x128，转为JPEG
        // 3. 存入 {storage_dir}/thumbnails/{sopInstanceUid}.jpg
    }
}
```

### 11.5 DB迁移管理 (Flyway)

```
pacs-boot/src/main/resources/
└── db/migration/
    ├── V001__create_pacs_patient.sql
    ├── V002__create_pacs_study.sql
    ├── V003__create_pacs_series.sql
    ├── V004__create_pacs_instance.sql
    ├── V005__create_aet_node.sql
    └── V006__add_issuer_of_patient_id.sql
```

### 11.6 Virtual Threads应用点

| 场景 | 用途 |
|------|------|
| C-STORE SCP接收 | 每个连接一个Virtual Thread，简化异步处理 |
| C-MOVE SCP回送 | 多文件并发C-STORE发送，每文件一个Virtual Thread |
| 缩略图生成 | `@Async` + Virtual Thread，不阻塞主流程 |
| DICOMweb请求 | Spring MVC请求在Virtual Thread上处理 |

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

### 11.7 日志审计

```java
public record DicomAuditLog(
    String eventType,       // C-STORE_RQ, C-FIND_RQ, C-MOVE_RQ, C-ECHO_RQ
    String callingAet,
    String calledAet,
    String studyUid,
    String patientId,
    boolean success,
    String detail,
    LocalDateTime timestamp
) {}
```

日志存储：结构化JSON日志输出，可接入ELK/Loki。

### 11.8 Docker部署

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY pacs-boot/target/pacs-boot.jar /app/pacs.jar
EXPOSE 8080 11112
ENTRYPOINT ["java", "-jar", "/app/pacs.jar"]
```

```yaml
# docker-compose.yml
services:
  pacs:
    build: .
    ports: ["8080:8080", "11112:11112"]
    volumes: ["pacs-storage:/data/pacs/storage"]
    environment:
      DICOM_PACS_DB_TYPE: mysql
      SPRING_DATASOURCE_URL: jdbc:mysql://db:3306/pacs
  db:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: pacs
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
    volumes: ["pacs-db:/var/lib/mysql"]
  viewer:
    build: ./pacs-viewer
    ports: ["3000:80"]
    environment:
      VITE_API_BASE: http://pacs:8080
volumes:
  pacs-storage:
  pacs-db:
```

## 12. 开发计划

### 12.1 整体排期（预估10周）

```
Week  1  2  3  4  5  6  7  8  9  10
P1   ███
P2      ███
P3         ████
P4             ███
P5                ███
P6                   ██
P7                      ███
P8                         ████
P9                             ███
P10                               ████
```

### 12.2 Phase 1：项目骨架（Week 1，3天）

| 任务 | 产出 | 优先级 |
|------|------|--------|
| 创建Maven多模块项目 | pom.xml × 7 | P0 |
| Domain模型(Record类) | PacsPatient/Study/Series/Instance/AetNode | P0 |
| Repository接口 | 5个Interface | P0 |
| 领域服务接口 | DicomStore/Query/Retrieve DomainService + AetManager | P0 |
| PacsApplication启动类 + 基础配置 | PacsApplication.java + application.yml | P0 |
| Flyway建表脚本(V001-V005) | 5个SQL | P0 |
| JPA Entity + Repository实现 | 5个Entity + 5个JpaRepo | P1 |

### 12.3 Phase 2：Netty协议栈 + C-ECHO（Week 2，5天）

| 任务 | 产出 | 优先级 |
|------|------|--------|
| DicomMessageDecoder(A-ASSOCIATE解析) | 1个Decoder | P0 |
| DicomMessageEncoder(A-ASSOCIATE构建) | 1个Encoder | P0 |
| DicomPartDecoder(P-DATA分片重组) | 1个Decoder | P0 |
| DicomPartEncoder(P-DATA分片发送) | 1个Encoder | P0 |
| DicomServerBootstrap(SCP启动/停止) | 1个Bootstrap | P0 |
| DicomClientBootstrap(SCU连接管理) | 1个Bootstrap | P0 |
| CEchoScpHandler + CEchoScuHandler | 2个Handler | P0 |
| AetManager实现 + AetApplicationService | 2个Service | P1 |
| C-ECHO集成测试 | EmbeddedPacs测试 | P1 |

### 12.4 Phase 3：C-STORE SCP + 存储（Week 3-4，8天）

| 任务 | 产出 | 优先级 |
|------|------|--------|
| CStoreScpHandler(Netty ChannelHandler) | 1个Handler | P0 |
| LocalFileStorage(文件写入+路径生成) | 1个Service | P0 |
| DicomStoreDomainService(四级upsert+索引) | 1个Service | P0 |
| DicomApplicationService.store编排 | 1个Service | P0 |
| AttributesConverter(Attributes→Record映射) | 1个Util | P0 |
| file_md5计算 + 缩略图异步生成 | ThumbnailService | P1 |
| C-STORE SCP集成测试(单文件+多文件) | 测试 | P0 |
| 并发C-STORE测试(100连接) | 测试 | P1 |

### 12.5 Phase 4：C-FIND SCP（Week 5，5天）

| 任务 | 产出 | 优先级 |
|------|------|--------|
| CFindScpHandler(Netty ChannelHandler) | 1个Handler | P0 |
| DicomQueryDomainService(多级别查询) | 1个Service | P0 |
| C-FIND Key→SQL查询映射 | PATIENT/STUDY/SERIES三级 | P0 |
| extra_tags JSON函数查询支持 | MySQL JSON_EXTRACT / PG JSONB | P1 |
| C-FIND SCP集成测试 | 测试 | P0 |

### 12.6 Phase 5：C-MOVE SCP + C-STORE SCU（Week 6，5天）

| 任务 | 产出 | 优先级 |
|------|------|--------|
| CMoveScpHandler(Netty ChannelHandler) | 1个Handler | P0 |
| DicomRetrieveDomainService(文件检索) | 1个Service | P0 |
| C-STORE SCU(主动发送DICOM文件) | ScuHandler扩展 | P0 |
| AetManager.findByAet(解析MoveDestination) | 方法实现 | P0 |
| C-MOVE SCP集成测试(双PACS交互) | 测试 | P0 |

### 12.7 Phase 6：C-FIND SCU + C-MOVE SCU（Week 7，3天）

| 任务 | 产出 | 优先级 |
|------|------|--------|
| C-FIND SCU(查询远程PACS) | ScuHandler扩展 | P0 |
| C-MOVE SCU(触发远程推送到本地) | ScuHandler扩展 | P0 |
| DicomApplicationService.pullFromRemote编排 | 方法实现 | P0 |
| DicomApplicationService.pushToRemote编排 | 方法实现 | P0 |
| 端到端测试(双PACS C-FIND→C-MOVE→C-STORE) | 测试 | P0 |

### 12.8 Phase 7：DICOMweb REST API（Week 7-8，5天）

| 任务 | 产出 | 优先级 |
|------|------|--------|
| WadoRsController(获取DICOM文件) | 1个Controller | P0 |
| QidoRsController(查询Studies/Series) | 1个Controller | P0 |
| StowRsController(HTTP推送DICOM) | 1个Controller | P1 |
| Spring Security + JWT认证 | SecurityConfig | P1 |
| DICOMweb集成测试(MockMvc) | 测试 | P0 |

### 12.9 Phase 8：Vue3阅片前端（Week 8-9，7天）

| 任务 | 产出 | 优先级 |
|------|------|--------|
| Vue3项目初始化(Vite + Ant Design Vue) | 项目骨架 | P0 |
| PatientList + StudyList + SeriesList页面 | 3个View | P0 |
| Cornerstone3D集成 + DicomViewport组件 | 1个Component | P0 |
| Toolbar(WW/WL/Zoom/Pan/Measure) | 1个Component | P1 |
| StudyBrowser缩略图浏览 | 1个Component | P1 |
| MPR三视图 + VR 3D渲染 | Viewport扩展 | P2 |
| 后端API调用层(axios) | api/模块 | P0 |

### 12.10 Phase 9：管理界面（Week 9，3天）

| 任务 | 产出 | 优先级 |
|------|------|--------|
| AE Title管理页面(CRUD + C-ECHO验证) | 1个View | P0 |
| DICOM操作页面(C-FIND/C-MOVE/C-STORE) | 1个View | P1 |
| 系统状态监控(连接数/存储空间) | 1个View | P2 |

### 12.11 Phase 10：多DB + 测试 + 部署（Week 10，5天）

| 任务 | 产出 | 优先级 |
|------|------|--------|
| SQLite适配(H2兼容模式) | Dialect配置 | P1 |
| PostgreSQL适配(JSONB + GIN索引) | Dialect + 迁移脚本 | P1 |
| Domain层单元测试(Mockito) | 测试类 × 4 | P0 |
| Application层单元测试 | 测试类 × 3 | P0 |
| JPA仓储测试(@DataJpaTest) | 测试类 × 5 | P0 |
| 性能基准测试(C-STORE TPS / C-FIND延迟) | 测试类 × 2 | P1 |
| Dockerfile + docker-compose.yml | 部署文件 | P1 |
| Flyway迁移脚本完善 | V001-V006 | P0 |

### 12.12 里程碑验收标准

| 里程碑 | 验收标准 |
|--------|---------|
| **M1 (Week 2)** | C-ECHO SCU/SCP双向通信成功，AET管理CRUD可用 |
| **M2 (Week 4)** | C-STORE SCP接收影像→文件存储→DB索引完整闭环，并发100连接稳定 |
| **M3 (Week 5)** | C-FIND SCP响应外部PATIENT/STUDY/SERIES查询，结果正确 |
| **M4 (Week 7)** | 双PACS交互闭环：C-FIND→C-MOVE→C-STORE全流程通过 |
| **M5 (Week 8)** | DICOMweb WADO-RS/QIDO-RS可用，JWT认证生效 |
| **M6 (Week 9)** | 前端阅片2D渲染可用，患者/检查浏览正常 |
| **M7 (Week 10)** | 多DB适配完成，单元测试覆盖率>80%，Docker一键部署 |
