# 部署说明

## 一、整体架构

主要运行单元：

1. Java PACS 后端（`pacs-boot`）
2. Vue Viewer 前端（`pacs-viewer`）
3. Python 生成器服务（`dicom-generator`）
4. 数据库（本地默认 H2，正式环境建议 PostgreSQL / MySQL）

默认端口：

- 后端 HTTP：`8080`
- DICOM 端口：`11112`
- Viewer 开发端口：`5173`
- Python 生成器：`8000`

## 二、基础依赖

- Java 21
- Maven 3.9+
- Node.js 20+
- npm
- Python 3.11（推荐）
- PostgreSQL 16（推荐用于长期环境）

## 三、后端部署

### 1. 构建

```bash
cd E:\myLife\PacsServer
mvn clean package
```

产物：

- `pacs-boot\target\pacs-boot-0.1.0-SNAPSHOT-exec.jar`

### 2. 本地启动

```bash
java -jar pacs-boot\target\pacs-boot-0.1.0-SNAPSHOT-exec.jar
```

### 3. 建议的生产覆盖配置

```bash
SPRING_PROFILES_ACTIVE=postgresql
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/pacs
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<password>
DICOM_PACS_LOCAL_AET=MY_PACS
DICOM_PACS_LOCAL_PORT=11112
DICOM_PACS_STORAGE_DIR=/data/storage
DICOM_PACS_DICOMWEB_JWT_SECRET=<strong-secret>
```

### 4. 关键注意事项

- 必须替换默认 JWT Secret：`change-me`
- 生产环境应收紧 CORS
- 如有网络安全要求，应开启 DICOM TLS
- 建议配置 `aet-whitelist`
- 需要根据同步任务与 WADO 访问量评估存储和数据库连接池

## 四、Viewer 前端部署

### 1. 构建

```bash
cd E:\myLife\PacsServer\pacs-viewer
npm install
npm run build
```

构建输出：

- `pacs-viewer\dist`

### 2. 开发模式

```bash
npm run dev
```

开发代理会转发：

- `/api`
- `/qido-rs`
- `/wado-rs`

到 `http://localhost:8080`。

### 3. 生产环境说明

- 如果前后端不是同源部署，需要设置 `VITE_API_BASE_URL`
- 需要确保浏览器可访问 `/api`、`/qido-rs`、`/wado-rs`
- Cornerstone 相关 WASM / codec 资源要能被正确提供
- 如跨域访问，需要后端显式配置 CORS

## 五、Python Generator 部署

### 1. 安装依赖

```bash
cd E:\myLife\PacsServer\dicom-generator
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

### 2. 启动服务

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 3. 运行说明

- 生成输出默认写入 `dicom-generator\output`
- 支持直接导出、直接推送到 PACS、模拟上游 PACS
- 请求里提供的 `host/port/AET` 会直接影响 PACS 连接

## 六、Docker

仓库已包含：

- `Dockerfile`
- `docker-compose.yml`

当前 Compose 形态主要是：

- 后端服务
- PostgreSQL

暴露端口：

- `8080`
- `11112`
- `5432`

## 七、发布检查清单

1. 构建后端 JAR
2. 构建 Viewer 前端
3. 如需 Python generator，安装并验证依赖
4. 应用环境变量和 profile
5. 确认 Flyway 迁移正常
6. 验证 Viewer 到后端 API 的连通性
7. 验证 WADO / QIDO
8. 验证 DICOM C-STORE / C-FIND / C-MOVE

## 八、相关文件

- `pom.xml`
- `Dockerfile`
- `docker-compose.yml`
- `pacs-boot/src/main/resources/application.yml`
- `pacs-boot/src/main/resources/application-postgresql.yml`
- `pacs-boot/src/main/resources/application-mysql.yml`
- `pacs-viewer/vite.config.js`
- `dicom-generator/requirements.txt`
