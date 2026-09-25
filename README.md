# 精馏塔简捷法核算服务（distillation-shortcut-service）

二元精馏塔简捷法选型核算的纯后端服务。一次 HTTP 调用返回：

1. **最小回流比**（Underwood）
2. **最少理论板数**（Fenske，全回流基准）
3. **逐板阶梯结果**（McCabe-Thiele，给定实际回流比下每块理论板的液相/气相组成、所属塔段、进料板位置）

无网页、无流程编辑器，仅结构化 JSON 入参/出参。

## 技术栈

- Java 17 + Spring Boot 3.3（Web + JUnit 5）
- Maven 多阶段 Docker 构建：构建阶段 `maven:3.9-eclipse-temurin-17`，运行阶段 `eclipse-temurin:17-jre-alpine`

## 模块划分（按职责拆开）

```
service/
├── equilibrium/  EquilibriumRelation        相平衡关系（全服务唯一定义处）
├── material/     MaterialBalance(Calculator) 总物料/组分衡算、残差校验
├── calc/         FenskeCalculator           Nmin
│                 UnderwoodCalculator        Rmin（二分求 theta）
├── stepping/     OperatingLine(Factory)     精馏段/提馏段操作线（同一衡算+q 推出）
│                 McCabeThieleStepper        逐板阶梯迭代（硬上限防死循环）
├── validation/   InputValidator             结构化入参校验
└── DistillationShortcutService               编排
controller/       DistillationController      HTTP 接口
error/            ErrorCode/ServiceException/GlobalExceptionHandler
```

**相平衡单点定义**：`EquilibriumRelation`（`y=αx/(1+(α-1)x)` 及其逆形式）只存在一处；
Fenske、操作线工厂、逐板阶梯全部注入同一个实例，不存在两份会打架的平衡曲线。

## 接口

`POST /api/distillation/shortcut`

请求体：

```json
{
  "feedFlow": 1.0,
  "feedComposition": 0.5,
  "distillateComposition": 0.95,
  "bottomsComposition": 0.05,
  "feedThermalFactor": 1.0,
  "relativeVolatility": 2.5,
  "refluxRatio": 5.0
}
```

响应（对外只含三个字段）：

```json
{
  "minimumRefluxRatio": 1.1,
  "minimumStages": 6.42687,
  "stepping": {
    "requestedRefluxRatio": 5.0,
    "totalStages": 8,
    "feedStageIndex": 4,
    "balance": { "feedFlow": 1.0, "distillateFlow": 0.5, "bottomsFlow": 0.5,
                 "residual": 0.0, "componentResidual": 0.0 },
    "stages": [
      { "stageNumber": 1, "liquidComposition": 0.8837, "vaporComposition": 0.8947,
        "section": "RECTIFYING", "feedStage": false }
    ]
  }
}
```

### 错误（结构化）

所有错误响应体统一为 `{code, message, detail, fields}`；参数校验错误的 `code` 为 `INVALID_INPUT`，
具体到字段的原因放在 `fields`（键为字段名，值为原因列表，如组成越界、进料量非正、α 非正、组成顺序颠倒、回流比非法、缺字段）。

| HTTP | code | 触发条件 |
|---|---|---|
| 400 | `INVALID_INPUT` | 组成不在 0~1（含端点 0/1）、进料量 ≤0、α 非正、组成顺序不是 xD>zF>xB、回流比为负/非有限/缺字段、非法 JSON |
| 422 | `SEPARATION_IMPOSSIBLE` | 相对挥发度 α ≤ 1，「无法分离」，后续一律不算 |
| 422 | `MATERIAL_NOT_CLOSED` | 衡算残差超过容差（相对 1e-9 / 绝对 1e-10），或两操作线交叉验证不一致 |
| 422 | `REFLUX_INSUFFICIENT` | R ≤ Rmin，「回流不足」，逐板之前直接拒绝，不会吐出板数 |
| 422 | `STRIPPING_FLOW_INVALID` | 该 q 与 R 下提馏段气相流量 V'≤0（汽相进料、回流过小的物理不可行区） |
| 422 | `STEP_NOT_CONVERGING` | 阶梯达 10 万级上限或组成不再下降（夹点保护，绝不陷死循环） |

## 计算约定

- 恒相对挥发度二元体系，重关键组分挥发度取 1，轻/重组分摩尔分数。
- 全凝器，塔顶从 (xD, xD) 起手；每级水平到平衡线（一块理论板）、垂直到操作线。
- 进料板：阶梯液相组成首次越过两操作线交点 xq 时，由精馏段操作线切换为提馏段操作线。
- 提馏段线：`L'=L+qF`，`V'=V-(1-q)F`，`y=(L'/V')x - B·xB/V'`，构造时校验过 (xB,xB) 且与精馏段线交于 q 线。
- 终止：液相组成掉到 xB 以下，阶梯数即理论板数（含再沸器一块板）。

## 测试（自动化判据，共 62 个用例全部通过）

- **物料闭合**：多组合法输入，总残差与组分残差均 ≤ 容差（`MaterialBalanceCalculatorTest`、服务级）。
- **全回流逼近 Fenske**：R=1e6 时 `|N阶梯 - Nmin| ≤ 1`（离散允许差一块），5 组任务（q 覆盖 0/0.5/1/1.5）参数化。
- **板数对 R 单调不增**：7 个逐级放大的回流比 × 5 组任务。q<1 时紧邻 Rmin 存在精馏段夹点区且要求
  V'=(R+1)D-(1-q)F>0，测试从可稳定收敛的回流比起步。
- **R ≤ Rmin 必报错**：等于、0.999×、0.5×、0 均返回 `REFLUX_INSUFFICIENT`，不会吐出板数。
- **q 敏感性**：q=0/1/1.5 下提馏段斜率随 q 单调减小、Rmin 随 q 增大（0.7 < 1.1 < 2.15），D/B 与残差不受 q 影响。
- 边界：组成越界（含端点）、F≤0、α 非正/≤1、顺序颠倒、缺字段、NaN、非法 JSON，以及 HTTP 层断言（`DistillationControllerTest`）。

## 构建与运行

```bash
# 本地
mvn clean package
java -jar target/distillation-shortcut-service-1.0.0.jar

# Docker（一条命令两阶段构建）
docker build -t distillation-shortcut .
docker run --rm -p 8080:8080 distillation-shortcut

# 调用
curl -s -X POST localhost:8080/api/distillation/shortcut \
  -H 'Content-Type: application/json' \
  -d '{"feedFlow":1.0,"feedComposition":0.5,"distillateComposition":0.95,
       "bottomsComposition":0.05,"feedThermalFactor":1.0,
       "relativeVolatility":2.5,"refluxRatio":5.0}'
```
