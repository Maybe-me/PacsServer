# Python DICOM Generator 接口文档

## 一、概览

默认服务地址：

`http://localhost:8000`

模块目录：

`dicom-generator`

启动命令：

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## 二、依赖

来自 `dicom-generator/requirements.txt`：

- `fastapi==0.115.0`
- `uvicorn==0.30.6`
- `pydicom==2.4.4`
- `pynetdicom==2.1.1`
- `httpx==0.27.2`
- `numpy==1.26.4`

## 三、接口列表

| 方法 | 路径 | 请求体 | 返回 |
|---|---|---|---|
| GET | `/health` | - | `{ "status": "ok" }` |
| POST | `/generate/jobs` | `GenerateJobRequest` | `GenerateJobStatus` |
| GET | `/generate/jobs/{job_id}` | - | `GenerateJobStatus` |
| POST | `/generate/export` | `GenerateJobRequest` | `GenerateJobResult` |
| POST | `/generate/push-to-pacs` | `GenerateJobRequest` | `GenerateJobResult` |
| POST | `/generate/simulate-upstream-pacs` | `GenerateJobRequest` | `GenerateJobResult` |

## 四、模型说明

### 1. `DicomIdentityOverrides`

- `patient_id`
- `patient_name`
- `accession_number`
- `study_instance_uid`
- `series_instance_uid`
- `sop_instance_uid`
- `study_date`
- `study_time`
- `series_number`
- `instance_number_start`

### 2. `PixelTransformOptions`

- `flip_horizontal`
- `flip_vertical`
- `rotate_180`
- `reverse_frames`

### 3. `PacsTarget`

- `host`
- `port`
- `called_aet`
- `calling_aet`（默认 `PYGEN`）

### 4. `GenerateJobRequest`

- `template_path`
- `output_directory`
- `mode`（`export`、`push_to_pacs`、`simulate_upstream_pacs`）
- `recursive`
- `overwrite`
- `identity`
- `pixel_transform`
- `tag_overrides`
- `pacs_target`

### 5. `GenerateJobResult`

- `job_id`
- `mode`
- `output_directory`
- `generated_count`
- `pushed_count`
- `instances`

### 6. `GenerateJobStatus`

- `job_id`
- `status`（`pending`、`running`、`completed`、`failed`）
- `request`
- `created_at`
- `updated_at`
- `result`
- `error`

## 五、调用示例

### 1. 健康检查

```bash
curl http://localhost:8000/health
```

### 2. 直接导出

```bash
curl -X POST http://localhost:8000/generate/export \
  -H "Content-Type: application/json" \
  -d '{
    "template_path": "E:\\sample-library\\templates\\ct",
    "output_directory": "E:\\output\\direct",
    "mode": "export",
    "overwrite": true,
    "identity": {
      "patient_id": "P001",
      "patient_name": "Demo^Patient"
    },
    "tag_overrides": {
      "StudyDescription": "Generated Study"
    }
  }'
```

### 3. 异步任务

```bash
curl -X POST http://localhost:8000/generate/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "template_path": "E:\\sample-library\\templates\\ct",
    "mode": "export",
    "overwrite": true
  }'
```

然后轮询：

```bash
curl http://localhost:8000/generate/jobs/{job_id}
```

### 4. 推送到 PACS

```bash
curl -X POST http://localhost:8000/generate/push-to-pacs \
  -H "Content-Type: application/json" \
  -d '{
    "template_path": "E:\\sample-library\\templates\\ct",
    "mode": "push_to_pacs",
    "overwrite": true,
    "pacs_target": {
      "host": "127.0.0.1",
      "port": 11112,
      "called_aet": "MY_PACS",
      "calling_aet": "PYGEN"
    }
  }'
```

## 六、运行说明

- 默认输出目录在 `dicom-generator/output`
- `simulate_upstream_pacs` 会使用独立的输出子目录
- 任务状态保存在内存中
- 异步执行依赖一个较小的线程池

## 七、相关源码位置

- `dicom-generator/app/main.py`
- `dicom-generator/app/models.py`
- `dicom-generator/app/service.py`
- `dicom-generator/tests/test_api.py`
- `dicom-generator/tests/test_generator_service.py`
