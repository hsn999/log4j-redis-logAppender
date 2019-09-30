package com.mylog.log4j.logappender;

import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;
import org.codehaus.jackson.map.ObjectMapper;

import redis.clients.jedis.Jedis;

public class RedisAppender extends AppenderSkeleton {
	/**
	 * redis主机,日志发送会按照主机顺序存储，排列在前面的存储失败则顺序找到下一个服务进行存储，127.0.0.1:3067;127.0.0.1:
	 * 3068;127.0.0.1:3069
	 */
	private String redisHosts;

	private static final int DEFAULT_RECONNECTION_DELAY = 30000;
	private static Executor executor = Executors.newFixedThreadPool(2);

	private static final String hostReg = "^((25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9])\\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[0-9]):\\d+)"
			+ "(;(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9])\\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[0-9]):\\d+)?$";

	private Jedis[] jediss;
	/**
	 * 配置检查结果
	 */
	private boolean configCheckResult = false;
	/**
	 * 是否发送服务器信息
	 */
	private boolean locationInfo = false;
	/**
	 * 服务器别名
	 */
	private String appAlias;
	/**
	 * 服务器IP端口
	 */
	private String appIpPort;
	/**
	 * redis保存key
	 */
	private String redisKey;
	/**
	 * 所属项目组
	 */
	private String teamName;
	/**
	 * 是否需要重连
	 */
	private boolean needReconenct = false;
	/**
	 * 正在重连
	 */
	private Boolean reconnecting = false;
	/**
	 * 连接失败重试时间（默认30秒）
	 */
	private int reconnectionDelay = DEFAULT_RECONNECTION_DELAY;

	/**
	 * 初始化
	 */
	@Override
	public void activateOptions() {
		try {
			if (!checkConfig()) {
				return;
			}
			String[] redisHostsStrs = redisHosts.split(";");
			jediss = new Jedis[redisHostsStrs.length];
			for (int i = 0; i < redisHostsStrs.length; i++) {
				String redisHostStr = redisHostsStrs[i];
				String[] hostAndPort = redisHostStr.split(":");
				String host = hostAndPort[0];
				try {
					int port = Integer.parseInt(hostAndPort[1]);
					Jedis jedis = new Jedis(host, port);
					jediss[i] = jedis;
					try {
						jedis.connect();
						LogLog.warn(getNow() + "succed connect to " + host + " : " + port);
					} catch (Exception e) {
						LogLog.warn(getNow() + "connect to " + host + " : " + port + " failed, try to reconnect later.");
					}
				} catch (Exception e) {
					LogLog.error(getNow() + "RedisAppender init failed!", e);
					configCheckResult = false;
				}
			}
		} catch (Exception e) {
			LogLog.error(getNow() + e.getMessage(), e);
		}
		LogLog.warn(getNow() + "RedisAppender inited ...");
	}

	@Override
	protected void append(LoggingEvent event) {
		if (!configCheckResult) {
			return;
		}
		try {
			for (Jedis jedis : jediss) {
				try {
					if (!jedis.isConnected()) {
						reconnect();
					} else {
						if (this.locationInfo) {
							event.getLocationInformation();
						}
						if (this.appAlias != null) {
							event.setProperty("appAlias", this.appAlias);
						}
//						if (this.appIpPort != null) {
//							event.setProperty("appIpPort", appIpPort);
//						}
						//取本地ip
						event.setProperty("appIpPort", NetUtils.getLocalHost());
						
						if (this.teamName != null) {
							event.setProperty("teamName", teamName);
						}
						String logStr = toJson(event);
						if (null == logStr) {
							LogLog.warn("find null log str!!!");
							return;
						}
						jedis.rpush(redisKey, logStr);
						return;
					}
				} catch (Exception e) {
					LogLog.error(getNow() + e.getMessage(), e);
					try {
						jedis.disconnect();
					} catch (Exception e2) {
						reconnect();
					}
				}
			}
		} catch (Exception e) {
			LogLog.error(getNow() + e.getMessage(), e);
		}
	}

	/**
	 * 销毁方法
	 */
	@Override
	public void close() {
		try {
			needReconenct = false;
			for (Jedis jedis : jediss) {
				try {
					if (jedis.isConnected()) {
						jedis.disconnect();
					}
					jedis.close();
				} catch (Exception e) {
					LogLog.warn(getNow() + e.getMessage());
				}
			}
		} catch (Exception e) {
			LogLog.warn(getNow() + e.getMessage());
		}
	}

	@Override
	public boolean requiresLayout() {
		return false;
	}

	/**
	 * 校验文件
	 * 
	 * @return
	 */
	private boolean checkConfig() {
		if (null == redisHosts) {
			LogLog.warn(getNow() + "log4j config error,redisHosts is null !");
			return false;
		}
		if (!redisHosts.matches(hostReg)) {
			LogLog.warn(getNow() + "log4j config error,redisHosts is not correct !");
			return false;
		}
		this.configCheckResult = true;
		return true;
	}

	/**
	 * 重连
	 */
	private void reconnect() {
		if (!reconnecting) {
			LogLog.warn(getNow() + "trying to reconnect to redis");
			reconnecting = true;
			needReconenct = true;
			Connector connector = new Connector();
			connector.setDaemon(true);
			connector.setPriority(1);
			executor.execute(connector);
		}
	}

	/**
	 * 重连守护线程
	 * 
	 *  
	 */
	private class Connector extends Thread {
		@Override
		public void run() {
			LogLog.warn(currentThread() + getNow() + "try to run");
			while (needReconenct) {
				LogLog.warn(currentThread() + getNow() + "try to reconnect");
				synchronized (this) {
					try {
						sleep(RedisAppender.this.reconnectionDelay);
					} catch (InterruptedException e) {
						LogLog.warn(currentThread() + getNow() + e.getMessage());
					}
					int connectedCount = 0;
					for (Jedis jedis : jediss) {
						try {
							if (!jedis.isConnected()) {
								jedis.connect();
								LogLog.warn(currentThread() + getNow() + "jedis reconnected...");
							}
							connectedCount++;
						} catch (Exception e) {
							LogLog.warn(currentThread() + getNow() + "connect to redis failed try it later");
						}
					}
					if (connectedCount == jediss.length) {
						needReconenct = false;
						reconnecting = false;
					}
				}
			}
		}
	}

	public void setRedisHosts(String redisHosts) {
		this.redisHosts = redisHosts;
	}

	public void setLocationInfo(boolean locationInfo) {
		this.locationInfo = locationInfo;
	}

	public void setAppAlias(String appAlias) {
		this.appAlias = appAlias;
	}

	public void setAppIpPort(String appIpPort) {
		this.appIpPort = appIpPort;
	}

	public void setRedisKey(String redisKey) {
		this.redisKey = redisKey;
	}

	public void setReconnectionDelay(int reconnectionDelay) {
		this.reconnectionDelay = reconnectionDelay;
	}

	public void setTeamName(String teamName) {
		this.teamName = teamName;
	}

	/**
	 * 拼接字符串实现LoggingEvent对象最快速转为json格式
	 * 
	 * @param event
	 * @return
	 */
	private String toJson(LoggingEvent event) {
		StringBuffer sbf = new StringBuffer();
		assembler(sbf, "type", redisKey);
		// 获取utc时间
		Calendar cal = Calendar.getInstance();
		int zoneOffset = cal.get(Calendar.ZONE_OFFSET);
		int dstOffset = cal.get(Calendar.DST_OFFSET);
		String appAlias = event.getProperty("appAlias");
		if (null != appAlias) {
			appAlias = appAlias.toLowerCase();
		}
		cal.add(Calendar.MILLISECOND, -(zoneOffset + dstOffset));
		Map<String, Object> map = new HashMap<String, Object>();

		map.put("@timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(cal.getTimeInMillis()));
		parseMap(map, "appIpPort", event.getProperty("appIpPort"));
		parseMap(map, "appAlias", appAlias);
		parseMap(map, "threadInfo", Thread.currentThread().getName());
		parseMap(map, "priority", event.getLevel().toString());
		parseMap(map, "fileName", event.getLocationInformation().getFileName());
		parseMap(map, "className", event.getLocationInformation().getClassName());
		parseMap(map, "methodName", event.getLocationInformation().getMethodName());
		parseMap(map, "lineNumber", event.getLocationInformation().getLineNumber());
		parseMap(map, "message", event.getRenderedMessage());
		parseMap(map, "throwable", event.getThrowableStrRep());
		parseMap(map, "teamName", event.getProperty("teamName"));

		ObjectMapper mapper = new ObjectMapper();
		StringWriter out = new StringWriter();
		try {
			mapper.writeValue(out, map);
		} catch (IOException e) {
			LogLog.error("parse to json error", e);
			return null;
		}
		return out.toString();

	}

	/**
	 * key放入map
	 * 
	 * @param map
	 * @param key
	 * @param value
	 * @return
	 */
	private Map<String, Object> parseMap(Map<String, Object> map, String key, Object value) {
		if (value != null) {
			if (value instanceof String && "".equals(((String) value).trim())) {
				return map;
			}
			map.put(key, value);
		}
		return map;
	}

	/**
	 * 拼装json参数
	 * 
	 * @param sbf
	 * @param key
	 * @param val
	 * @return
	 */
	private StringBuffer assembler(StringBuffer sbf, String key, Object val) {
		if (null == val) {
			return sbf;
		}
		if (val instanceof String) {
			if ("".equals(((String) val).trim())) {
				return sbf;
			}
			sbf.append(",\"").append(key).append("\":\"").append(val).append("\"");
			return sbf;
		}
		if (val instanceof String[]) {
			sbf.append(",\"").append(key).append("\":\"");
			for (String s : (String[]) val) {
				sbf.append(s.replaceAll("\\t", "\\\\t")).append("\\r\\n");
			}
			sbf.append("\"");
			return sbf;
		}
		sbf.append(",\"").append(key).append("\":").append(val);
		return sbf;
	}

	private String getNow() {
		return new SimpleDateFormat(" yyyy-MM-dd HH:mm:ss.SSS ").format(new Date());
	}
}
