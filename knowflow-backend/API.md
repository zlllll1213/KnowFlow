# KnowFlow API 接口文档

Base URL: `http://localhost:8081`

所有需要认证的接口需在 Header 中携带：

```
Authorization: Bearer <token>
```

---

## 一、用户认证

### 1.1 注册

```
POST /api/auth/register
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名，3-64 字符 |
| password | string | 是 | 密码，6-128 字符 |
| email | string | 是 | 邮箱 |

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 用户 ID |
| username | string | 用户名 |
| email | string | 邮箱 |
| createdAt | string | 创建时间 (ISO 8601) |

---

### 1.2 登录

```
POST /api/auth/login
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | long | 用户 ID |
| username | string | 用户名 |
| token | string | JWT Token，24 小时有效 |

---

### 1.3 获取当前用户

```
GET /api/auth/me
```

**Response `data`:** 同注册返回。

---

## 二、知识库

### 2.1 创建知识库

```
POST /api/kb
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 知识库名称 |
| description | string | 否 | 描述 |

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 知识库 ID |
| name | string | 名称 |
| description | string | 描述 |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

---

### 2.2 知识库列表

```
GET /api/kb/list
```

**Response `data`:** 数组，元素同 2.1。

---

### 2.3 知识库详情

```
GET /api/kb/{id}
```

**Response `data`:** 同 2.1。

---

### 2.4 更新知识库

```
PUT /api/kb/{id}
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 否 | 新名称 |
| description | string | 否 | 新描述 |

---

### 2.5 删除知识库

```
DELETE /api/kb/{id}
```

逻辑删除，返回 `"删除成功"`。

---

## 三、文档

### 3.1 上传文档

```
POST /api/document/upload
Content-Type: multipart/form-data
```

**Form 参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| kbId | long | 是 | 所属知识库 ID |
| file | file | 是 | 上传文件 |

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 文档 ID |
| kbId | long | 知识库 ID |
| fileName | string | 原始文件名 |
| fileType | string | 文件扩展名（小写） |
| fileSize | long | 文件大小（字节） |
| status | string | 状态：UPLOADED |
| errorMessage | string | 错误信息 |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

---

### 3.2 文档列表

```
GET /api/document/list?kbId={kbId}
```

**Query 参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| kbId | long | 是 | 知识库 ID |

**Response `data`:** 数组，元素同 3.1。

---

### 3.3 文档详情

```
GET /api/document/{id}
```

**Response `data`:** 同 3.1。

---

### 3.4 删除文档

```
DELETE /api/document/{id}
```

---

### 3.5 文档状态

```
GET /api/document/{id}/status
```

**Response `data`:** 状态字符串，取值：

| 值 | 说明 |
|----|------|
| UPLOADED | 已上传，等待解析 |
| PARSING | 解析中 |
| EMBEDDING | 向量化中 |
| DONE | 处理完成 |
| FAILED | 处理失败 |

---

## 四、聊天问答

### 4.1 创建会话

```
POST /api/chat/session
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| kbId | long | 是 | 知识库 ID |
| title | string | 否 | 会话标题，默认 "New Chat" |

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 会话 ID |
| kbId | long | 知识库 ID |
| userId | long | 用户 ID |
| title | string | 标题 |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

---

### 4.2 会话列表

```
GET /api/chat/session/list?kbId={kbId}
```

**Query 参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| kbId | long | 是 | 知识库 ID |

**Response `data`:** 数组，元素同 4.1。

---

### 4.3 提问

```
POST /api/chat/ask
```

**Request Body:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| kbId | long | 是 | 知识库 ID |
| sessionId | long | 是 | 会话 ID |
| question | string | 是 | 问题内容 |

**Response `data`:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 消息 ID |
| role | string | "assistant" |
| content | string | 回答内容（当前为模拟） |
| createdAt | string | 创建时间 |

---

### 4.4 聊天历史

```
GET /api/chat/history?sessionId={sessionId}
```

**Query 参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | long | 是 | 会话 ID |

**Response `data`:** 数组，每条消息：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 消息 ID |
| role | string | "user" 或 "assistant" |
| content | string | 消息内容 |
| createdAt | string | 创建时间 |

---

## 错误码参考

| code | 说明 |
|------|------|
| 0 | 成功 |
| 40001 | 参数校验失败 |
| 40010 | 用户名已存在 |
| 40011 | 邮箱已被注册 |
| 40012 | 用户名或密码错误 |
| 40013 | 用户不存在 |
| 40020 | 知识库不存在 |
| 40021 | 文档不存在 |
| 40030 | 无权访问该资源 |
| 50000 | 服务器内部错误 |
| 50001 | 文件操作失败 |
