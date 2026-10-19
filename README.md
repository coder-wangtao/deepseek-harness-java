# DeepSeek Harness Java

模块 职责
app 启动入口和配置（Application.java 在这里）
trigger 触发层，比如 HTTP 接口、定时任务、消息监听
api 对外暴露的接口定义和 DTO
case 用例编排层，组合多个领域服务
types 公共类型，比如现在的 Message、ToolExecution 等模型
domain 领域层，放核心业务逻辑
infrastructure 基础设施层，负责数据库、外部调用等
