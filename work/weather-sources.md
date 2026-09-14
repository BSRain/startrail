# 4.0 天气接口与边界

核对日期 2026-09-12。官方文档：
- https://dev.qweather.com/docs/configuration/authentication/#json-web-token
- https://dev.qweather.com/docs/configuration/api-host/
- https://dev.qweather.com/docs/api/weather/weather-hourly-forecast/
- https://www.bouncycastle.org/licence.html

请求 /weather/v1/hourly/{latitude}/{longitude}?hours=72&localTime=false&lang=zh，纬度在前、经度在后，两位小数。与旧城市 v7 接口不同。hours[].forecastTime 转 Instant；cloudCover/humidity 的 0..1 转百分比，m/s 转 km/h，降水取当前时段累计 mm。metadata.attributions 与结果共同显示。

响应有 2 MiB 解压后限制，禁止重定向，HTTPS 仅限 qweatherapi.com 子域，10 秒连接/读超时。没有真实账号请求，权限及公私钥匹配需用户导入私钥后验证。JWT 测试使用 RFC 8032 公开向量，天气截图为显式标注的合成数据。

UTC 整小时匹配，缺失字段 NaN；仅同地点同 Host 缓存 30 分钟，刷新失败不采用旧预报。响应未提供明确发布时间，仅显示获取时间，不能验证来源预报的新鲜度。未来三天内自选时段，设备时区显示；无日历接入或后台定位，位置经用户选定或主动系统定位获取。

20 分钟采样；太阳 <=-6°（亮目标）、<=-18°（深空），目标 >=20°。已知云量 >=80%、降水 >0.1 mm、风速 >=40 km/h 否决，其他字段缺失也不绕过。湿度、风速、云量及月光参与启发式排序，不是透明度、视宁度、可见度或安全预测。不含天气预警、分层云量或降水概率规则。
