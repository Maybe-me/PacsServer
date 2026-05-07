# Java 后端接口文档

## 一、概览

默认 HTTP 地址：

`http://localhost:8080`

主要接口分组：

- 管理接口：`/api/admin/*`
- Viewer 接口：`/api/viewer/*`
- DICOMweb 接口：`/qido-rs/*`、`/wado-rs/*`

## 二、鉴权与访问说明

- 代码中存在 `dicom.pacs.dicomweb.*` 相关鉴权配置。
- 默认 JWT Secret 仍为 `change-me`，生产环境必须覆盖。
- 当前审阅到的 Controller 中，没有看到明确的方法级权限注解。
- CORS 默认策略偏宽松，生产环境应限制来源和方法。

## 三、管理接口

### 1. AET 管理

基础路径：`/api/admin/aets`

| 方法 | 路径 | 请求体 | 返回 |
|---|---|---|---|
| GET | `/api/admin/aets` | - | `List<AetNode>` |
| POST | `/api/admin/aets` | `AetNodeRequest` | `AetNode` |
| PUT | `/api/admin/aets/{aet}` | `AetNodeRequest` | `AetNode` |
| DELETE | `/api/admin/aets/{aet}` | - | `204 No Content` |

`AetNodeRequest` 主要字段：

- `aet`
- `host`
- `port`
- `role`
- `nodeName`
- `description`
- `enabled`

示例：

```bash
curl -X POST http://localhost:8080/api/admin/aets \
  -H "Content-Type: application/json" \
  -d '{
    "aet": "REMOTE_PACS",
    "host": "127.0.0.1",
    "port": 11113,
    "role": "REMOTE",
    "nodeName": "remote-pacs",
    "description": "远程 PACS 节点",
    "enabled": true
  }'
```

### 2. DICOM 运维接口

基础路径：`/api/admin/dicom`

| 方法 | 路径 | 请求体 | 返回 |
|---|---|---|---|
| POST | `/echo` | `TargetAetRequest` | `{ targetAet, reachable }` |
| POST | `/remote-find/studies` | `RemoteQueryRequest` | `List<Map<String, String>>` |
| POST | `/remote-find/series` | `RemoteQueryRequest` | `List<Map<String, String>>` |
| POST | `/remote-pull` | `RemotePullRequest` | `{ targetAet, destinationAet, movedCount }` |
| POST | `/remote-push` | `RemotePushRequest` | `{ targetAet, pushedCount }` |

请求记录类型：

- `TargetAetRequest(targetAet)`
- `RemoteQueryRequest(targetAet, criteria)`
- `RemotePullRequest(targetAet, criteria, destinationAet)`
- `RemotePushRequest(targetAet, criteria)`

示例：

```bash
curl -X POST http://localhost:8080/api/admin/dicom/echo \
  -H "Content-Type: application/json" \
  -d '{ "targetAet": "REMOTE_PACS" }'
```

### 3. 同步任务管理

基础路径：`/api/admin/sync`

| 方法 | 路径 | 请求体 | 返回 |
|---|---|---|---|
| GET | `/jobs` | - | `List<SyncJobConfig>` |
| POST | `/jobs` | `SyncJobRequest` | `SyncJobConfig` |
| PUT | `/jobs/{jobName}` | `SyncJobRequest` | `SyncJobConfig` |
| DELETE | `/jobs/{jobName}` | - | `204 No Content` |
| GET | `/executions` | query params | 执行记录列表 |
| GET | `/executions/summary` | query params | 汇总结果 |
| POST | `/pull/{jobName}` | - | 执行结果 |
| POST | `/push/{jobName}` | - | 执行结果 |

`SyncJobRequest` 关键字段：

- `jobType`
- `jobName`
- `targetAet`
- `destinationAet`
- `patientId`
- `modality`
- `studyDateLookbackDays`
- `preventLoopToSource`
- `skipRemoteDuplicates`
- `maxStudiesPerRun`
- `maxInstancesPerRun`
- `throttleDelayMs`
- `sourceAetAllowList`
- `sourceAetBlockList`
- `maxRetryCount`
- `failureThreshold`
- `paused`
- `enabled`

## 四、Viewer 接口

基础路径：`/api/viewer`

### 1. 查询检查列表

| 方法 | 路径 | 查询参数 |
|---|---|---|
| GET | `/api/viewer/studies` | `patientId`、`issuerOfPatientId`、`modality`、`studyDateFrom`、`studyDateTo` |

返回字段：

- `studyInstanceUid`
- `accessionNo`
- `studyDate`
- `studyDescription`
- `modalitiesInStudy`
- `numSeries`
- `numInstances`

示例：

```bash
curl "http://localhost:8080/api/viewer/studies?patientId=P001&modality=CT"
```

### 2. 查询某个检查下的序列

| 方法 | 路径 |
|---|---|
| GET | `/api/viewer/studies/{studyInstanceUid}/series` |

返回字段：

- `seriesInstanceUid`
- `modality`
- `seriesDescription`
- `bodyPartExamined`
- `numInstances`

### 3. 查询某个序列下的实例

| 方法 | 路径 |
|---|---|
| GET | `/api/viewer/studies/{studyInstanceUid}/series/{seriesInstanceUid}/instances` |

返回字段包含：

- `sopInstanceUid`
- `instanceNumber`
- `sopClassUid`
- `transferSyntaxUid`
- `fileSize`
- `rows`
- `columns`
- `numberOfFrames`
- `samplesPerPixel`
- `bitsAllocated`
- `bitsStored`
- `highBit`
- `planarConfiguration`
- `pixelRepresentation`
- `photometricInterpretation`
- `windowCenter`
- `windowWidth`
- `renderable`
- `wadoUri`

该接口是前端 Viewer 做兼容性判断和 Cornerstone 渲染的主要输入。

## 五、DICOMweb 接口

### 1. QIDO-RS

基础路径：`/qido-rs`

| 方法 | 路径 | 查询参数 |
|---|---|---|
| GET | `/qido-rs/studies` | `PatientID`、`IssuerOfPatientID`、`StudyInstanceUID`、`AccessionNumber`、`ModalitiesInStudy`、`StudyDate` |
| GET | `/qido-rs/studies/{studyInstanceUid}/series` | `SeriesInstanceUID`、`Modality` |

返回格式为 DICOMweb 风格的 VR + Value 数组结构。

### 2. WADO-RS

| 方法 | 路径 | 返回 |
|---|---|---|
| GET | `/wado-rs/studies/{studyInstanceUid}/series/{seriesInstanceUid}/instances/{sopInstanceUid}` | `application/dicom` |

示例：

```bash
curl -o image.dcm \
  "http://localhost:8080/wado-rs/studies/{study}/series/{series}/instances/{instance}"
```

## 六、相关源码位置

- `pacs-infrastructure/src/main/java/com/mylife/pacs/infrastructure/rest/admin/AetAdminController.java`
- `pacs-infrastructure/src/main/java/com/mylife/pacs/infrastructure/rest/admin/DicomOperationsController.java`
- `pacs-infrastructure/src/main/java/com/mylife/pacs/infrastructure/rest/admin/SyncAdminController.java`
- `pacs-infrastructure/src/main/java/com/mylife/pacs/infrastructure/rest/viewer/ViewerRestController.java`
- `pacs-infrastructure/src/main/java/com/mylife/pacs/infrastructure/dicomweb/QidoRsController.java`
- `pacs-infrastructure/src/main/java/com/mylife/pacs/infrastructure/dicomweb/WadoRsController.java`
