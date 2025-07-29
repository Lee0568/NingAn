# NingAn


## 更新日志
* Jul 4, 2025 前端渲染
* Jul 14,2025 描述你的更改内容
* Jul 27, 2025 蜜罐内容翻新，新建蜜罐数据库，路径识别逻辑优化
* Jul 28, 2025 修改登录逻辑
* Jul 29, 2025 稳步推进


## 运行
````
version: '3.8'

services:
ning:
image: guest121/ningan
ports:
- "8080:8080"
- "8090:8090"
- "65535:65535"
environment:
- SERVER_PORT=8080
restart: unless-stopped
 ````


### 待处理

* 可视化大屏
* 终端
* 文件管理


## 技术栈

springboot + thymeleaf + sqlite

* 自定义注解
* 全局异常捕获，404捕获
* 多端口多页面
* 后台指定端口配置