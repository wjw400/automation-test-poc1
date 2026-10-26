# 接口 PGP 处理：实施说明

日期：2026-10-24。

## 已实现的用户流程

1. 导入普通 OpenAPI，或包含受支持 `x-platform-processing` 扩展的 OpenAPI。
2. 打开 API → 选择接口 → “请求与响应处理”。
3. 配置请求和响应的保护方式、密文位置及可选业务 Schema。
4. 用例引用接口，填写业务数据和断言；执行时选择已有 Profile。
5. 展开用例的接口处理摘要，或查看运行报告的 Actions，检查真实执行顺序。

没有扩展时允许完全手动配置。手动保存不会改写原始 OpenAPI。只识别本平台支持的扩展名称、结构和版本，不把任意 `x-` 字段当作可执行内容。

业务 Schema 与处理声明统一来自有效接口配置，不保留已移除扩展格式的读取、展示回退或迁移适配。

## 配置格式

```yaml
x-platform-processing:
  version: 1
  request:
    protection: pgp-sign-encrypt
    ciphertext:
      location: json-pointer
      pointer: /data/encryptdata
    envelope:
      metadata:
        channel: partner
        correlationId: ${correlationId}
    businessSchema:
      $ref: '#/components/schemas/CreateOrderPayload'
  responses:
    '200':
      protection: pgp-decrypt-verify
      ciphertext:
        location: json-pointer
        pointer: /result/payload
      businessSchema:
        $ref: '#/components/schemas/Order'
    '400':
      protection: none
    '500':
      protection: none
```

请求支持 `none`、`pgp-encrypt`、`pgp-sign-encrypt`；响应支持 `none`、`pgp-decrypt`、`pgp-decrypt-verify`。

`location: body` 表示整个 HTTP body 是 ASCII Armor 文本；`json-pointer` 表示 JSON 中的字符串字段。请求和响应分别配置，字段名不固定。

JSON Pointer 示例：`/payload`、`/data/encryptdata`；字段名中的 `/` 编码为 `~1`，`~` 编码为 `~0`。请求组装时自动创建缺少的对象容器；遇到标量容器时报错。数组位置需在 envelope 中预先声明，不根据数字路径猜测数组。

envelope 可以包含静态字段和现有 `${variableName}` 变量，平台把密文写入指定位置。业务数据整体序列化为 UTF-8 JSON，再执行 PGP 保护。

响应按实际状态码依次匹配精确值、范围（例如 `2XX`）和 `default`。没有匹配规则会报配置错误；不会尝试猜测或在解密失败时回退明文。`none` 用于普通 JSON 响应，空响应保留空业务值。

兼容导入 `location: json-field` 加 `field: payload` 的顶层字段简写，以及现有两个 `system/pgp-json-envelope-*@1` 引用。旧引用转换为有效 PGP 配置；不支持的旧参数/端口覆盖会给出诊断，要求改成明确的字段路径。

## Actions 顺序

固定执行顺序如下：

1. 解析请求变量。
2. 用例 `actions.beforeSend`：处理业务请求。
3. 接口自动处理：加密或签名加密，再组装传输请求。
4. 用例 `actions.afterRequestProcessing`：处理已组装的传输请求。
5. HTTP 调用，保存原始响应。
6. 接口自动处理：按状态码提取密文、解密及按配置验签。
7. 用例 `actions.afterReceive`。
8. 断言及变量提取。

自动生成的步骤使用同一个 Action 执行器，报告以 `origin: API` 标记；用例步骤标记为 `CASE`。自动步骤不写回用例正文，不要求用户重复编辑。

用例编辑器在“发送前 / 接收后”Actions 列表中按上述顺序统一展示用例步骤和接口继承步骤，并连续编号。继承项标注“来自接口 · 顺序固定”，不提供用例内删除、重排或单独编辑。发送前依次展示业务 Actions、接口 PGP 和封装、传输报文 Actions；接收后先按实际 HTTP 状态选择一条响应规则，再执行用例 Actions。普通 JSON 规则和加密响应规则作为互斥分支展示。

Actions 内的“编辑接口处理配置”入口打开共享配置编辑器，并提示保存会影响所有引用此接口的用例。保存后列表刷新，但继承项仍不写回用例 DSL。切换 manual 或 wire 时列表按实际执行行为隐藏对应继承项。已有重复 PGP Action 会提示处理，接口已负责该方向的 PGP 时，新增菜单不再提供重复的 PGP 选项。

有效接口处理配置和手工 PGP Actions 同时存在时，执行前明确阻止重复处理。用例可通过 `processingMode` 显式选择：

| 模式 | 行为 |
| --- | --- |
| `automatic`（默认） | 使用接口的请求和响应处理配置 |
| `wire` | 跳过接口请求加密与封装，保留接口响应处理；用于发送损坏密文等负向测试。显式配置的用例 Actions 仍按顺序执行 |
| `manual` | 跳过接口加解密，仅执行用例手工 Actions；用于明确替代接口处理的特殊场景 |

断言和提取来源：

- `business.response.body`：接口处理完成后的业务 JSON；未配置处理时读取原始 JSON。
- `wire.response.body` 和现有 `response.body`：原始 HTTP 响应。
- `decrypted.<name>`：现有手动解密 Action 产生的命名结果。

报告的“业务响应 / 解密结果”直接展示 `business.response.body` 对应的值；持久化解密结果字典中的 `business` 是结果名称，不额外包进展示的业务 JSON。其他命名结果按 `decrypted.<name>` 单独展示。JSONPath 始终相对于所选数据源：例如业务对象为 `{ "id": "O3011" }` 时使用 `$.id`；只有业务数据本身含有 `business` 字段时才需要 `$.business.id`。此调整仅改变展示，不修改历史执行证据或断言读取语义。

## Agent 场景编排契约与静态校验

`getApiOperationSchema` 返回 `operation.authoring`，上下文中的 Operation 也提供同一份编排契约。它按平台当前有效配置选择业务或传输 Schema，展开本地引用（含嵌套数组元素），并同时提供请求必填状态、声明的示例/默认值以及每种响应的数据源和字段结构。原始 `requestBody`、`responses` 仍表示传输协议，不应据其密文封装猜测业务字段。

Agent 新建、修改、校验和发布草稿共用静态契约检查：请求必填字段、禁止的额外字段、基本类型、枚举、数组长度、数值边界，以及简单 JSONPath 是否由所选响应 Schema 声明。加密业务字段误用原始响应数据源时，会提示改用 `business.response.body`；变量名不决定响应字段名。原始响应的合法封装字段仍允许断言。平台配置覆盖 OpenAPI 扩展后，编排和校验立即采用当前有效 Schema。

这不是完整的运行时 JSON Schema 校验：动态变量、无法静态推断的 Action 输出、复杂 JSONPath、未展开或组合变体的 Schema 不会被猜测成确定的校验结果。本地引用按深度和节点预算展开；递归、外部和无法解析的引用保留诊断，Agent 应继续读取必要片段或补齐业务契约。明确只预期 4xx/5xx 的负向场景将请求形状问题保留为 WARNING，不阻止有意构造非法输入；响应路径仍需要选择正确的数据源。此检查不证明示例商品仍有库存或业务资源在当前环境中存在。

## 配置更新与重新导入

平台保存有效配置、当前导入配置、来源、修订和复核状态。保存请求必须携带当前修订；过期写入返回 409，数据库乐观锁处理并发更新。

重新导入优先按 method + path 保留接口记录；原端点已不存在时，再尝试唯一 operationId 匹配。手动配置不会被导入覆盖。配置过处理规则的接口在文档发生变化时保守标记待复核；同内容重复导入不要求复核。

接口页面可以比较导入配置和当前配置，选择保留手动配置并保存，或使用导入配置。自动执行在待复核或有效导入配置存在错误时被阻止。显式 manual 模式仍可用于自行处理。

可从接口页面导出当前配置，便于与原始 OpenAPI 一起携带。

## 历史报告

每个 Step 固化：

- 有效接口配置、来源、修订、处理模式、接口 method/path 和文档更新时间。
- 当时的原始 operation 和 components，保留 Schema 引用上下文。
- 业务请求、实际 HTTP 请求、原始响应、解密结果、Action 输入输出、状态和错误。

历史读取只使用已保存的 StepRun 数据，不重新查询当前接口配置。后续修改不会改写历史报告。配置快照不复制运行时 Profile 的私钥或口令。

## 存储及 API

数据库迁移为 `V26__operation_processing_configuration.sql`。按项目及接口提供：

- `GET /api/projects/{projectId}/operations/{operationId}/processing`
- `PUT /api/projects/{projectId}/operations/{operationId}/processing`，正文为 `{revision, configuration}`。
- `POST /api/projects/{projectId}/operations/{operationId}/processing/use-imported`，正文为 `{revision}`。

接口契约及 Agent 的接口读取工具均包含有效处理配置。Agent 获得自动处理说明，使用业务响应来源生成断言，不需要生成重复 PGP Actions。

## 本次边界

支持 JSON 业务数据、ASCII Armor PGP、嵌套 JSON 字段及整个 body。暂未扩展额外 Base64 层、部分字段加密、任意脚本及通用流程编排。

业务 Schema 用于接口展示、生成上下文和草稿阶段的静态契约检查。没有增加通用 JSON Schema 运行时校验器；不能把静态检查等同于已校验每次请求/响应。

密钥来自当前运行时 Profile，按用途分别配置：`signingPrivateKey` / `signingPrivateKeyPassphrase` 为我方请求签名私钥及口令，`decryptionPrivateKey` / `decryptionPrivateKeyPassphrase` 为我方响应解密私钥及口令，`encryptionPublicKey` 为对方请求加密公钥，`verificationPublicKey` 为对方响应验签公钥。我方签名和解密可以使用同一把私钥，也可以分别配置；对方两个用途的公钥同样独立。不要求我方公钥或对方私钥。V28 迁移按原有用途重命名数据库字段，保留现有 Profile 内容；接口和 DSL 统一使用新名称，不提供旧字段别名。

旧用例若已手动配置 PGP Actions，需删除重复处理或显式选择 `manual`；系统不会静默忽略已有 Actions。

## 验证

自动化检查覆盖：真实签名/无签名 PGP 往返、不同嵌套字段、envelope 变量、Action 顺序、明文错误响应、签名缺失、字段缺失、原始传输模式、重复加密拦截、扩展兼容、普通 OpenAPI 手动配置、重新导入及修订冲突、持久化执行与历史快照不变性，以及界面编辑和无效 JSON 保存拦截。

浏览器检查使用独立 H2 数据库和临时服务，验证导入、接口配置入口、嵌套路径保存、来源变化和执行顺序预览。
