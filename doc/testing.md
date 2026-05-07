# 测试与验证说明

## 一、后端 Maven 测试

### 1. 全量构建与测试

```bash
cd E:\myLife\PacsServer
mvn clean package
```

### 2. 通过 `pacs-test` 跑集成测试

```bash
mvn -am -pl pacs-test test
```

### 3. 常见定向执行

```bash
mvn -am -pl pacs-test test -Dtest=DicomwebIntegrationTest
mvn -am -pl pacs-test test -Dtest=*Sync*
```

### 4. 主要集成测试

- `PacsApplicationContextTest`
- `DicomwebIntegrationTest`
- `DicomTransportIntegrationTest`
- `ScheduledDicomSyncIntegrationTest`
- `RemoteDicomApplicationServiceIntegrationTest`
- `PacsPersistenceIntegrationTest`
- `PublicSampleLibraryIntegrationTest`
- `PythonGeneratorDirectPacsIntegrationTest`
- `PythonGeneratorScheduledPullIntegrationTest`

## 二、Viewer 前端验证

当前前端主要依赖构建验证：

```bash
cd E:\myLife\PacsServer\pacs-viewer
npm install
npm run build
```

当前状态：

- 已具备生产构建能力
- 还没有正式接入前端自动化测试框架

## 三、Python Generator 测试

```bash
cd E:\myLife\PacsServer\dicom-generator
python -m unittest discover tests
```

覆盖内容：

- export 接口
- 异步任务状态流转
- 推送 PACS
- 模拟上游 PACS
- 元数据重写
- 像素变换

## 四、当前回归保护重点

- Viewer 实例接口集成测试已校验：
  - `rows`
  - `columns`
  - `numberOfFrames`
  - `photometricInterpretation`
  - `renderable`
  - `bitsStored`
  - `highBit`
- Viewer 在 Cornerstone 兼容性修复后，持续使用构建进行回归确认
- Java 集成测试已覆盖 QIDO / WADO、存储、同步、Python generator 联动路径

## 五、当前缺口

- 还没有前端组件级自动化测试
- 还没有浏览器 E2E 测试
- Python generator 当前以 unittest 为主
- 部分 generator 验收路径依赖外部输入文件，缺失时会跳过
