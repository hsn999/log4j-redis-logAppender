## 工程说明
* 日志收集组件,参考了https://github.com/ryantenney/log4j-redis-appender
* 配合log4j使用，在log4j配置文件中配置此Appender
* 为方便信息收集增加了相关的参数，AppAlias、TeamName、locationInfo等，以方便在ELK中进行日志的的分组处理


## 用法
* 以jar包的形式引入到其它工程
* 修改配置文件

```xml
<dependency>
	<groupId>com.mylog</groupId>
	<artifactId>log4j-redis-logAppender</artifactId>
	<version>1.0.0</version>
</dependency>
```

## 配置文件示例

log4j.rootLogger=info,logfile,logstash
#stdout
log4j.appender.stdout=org.apache.log4j.ConsoleAppender
log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
log4j.appender.stdout.layout.ConversionPattern=%d{yyyy-MM-dd HH\:mm\:ss,SSS} [%t] [%p]-[%C %M %L]-%m%n


#logfile
log4j.appender.logfile=org.apache.log4j.RollingFileAppender
log4j.appender.logfile.File=D:/test/service.log
log4j.appender.logfile.MaxFileSize=128MB
log4j.appender.logfile.MaxBackupIndex=100
log4j.appender.logfile.layout=org.apache.log4j.PatternLayout
log4j.appender.logfile.layout.ConversionPattern=%d{yyyy-MM-dd HH\:mm\:ss,SSS} [%t] [%p]-[%C %M %L]-%m%n

##
log4j.appender.logstash=com.mylog.log4j.logappender.RedisAppender
##redis的ip和端口用;分割
log4j.appender.logstash.RedisHosts=192.168.1.199:6379
log4j.appender.logstash.LocationInfo=true
##服务名，
log4j.appender.logstash.AppAlias=testApp
##保存到redis的key，需要与logstash配置一致，
log4j.appender.logstash.RedisKey=logstash_mytest
log4j.appender.logstash.ReconnectionDelay=10000
log4j.appender.logstash.Threshold=INFO
##所属业务分组（预警时用到暂定项目组
log4j.appender.logstash.TeamName=logstash_app