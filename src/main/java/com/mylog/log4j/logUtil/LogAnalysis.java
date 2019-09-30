package com.mylog.log4j.logUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.codehaus.jackson.map.ObjectMapper;

public class LogAnalysis {
	
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final String LOG_ANALYSIS_PREFIX = "SPARK_LOG_ANALYSIS";
	
	private static final String LOG_DELIMITER = "::";
	
	private final Log log;
	
	private final String bizOperaType;

	private LogAnalysis(Log log, String bizOperaType) {
		this.log = log;
		this.bizOperaType = bizOperaType;
	}
	
	public void log(String json){
		log.info(LOG_ANALYSIS_PREFIX + LOG_DELIMITER + this.bizOperaType + LOG_DELIMITER + json);
	}
	
	public void log(Object jsonObject){
		if(jsonObject == null) return;
		try {
			log.info(LOG_ANALYSIS_PREFIX + LOG_DELIMITER + this.bizOperaType + LOG_DELIMITER + OBJECT_MAPPER.writeValueAsString(jsonObject));
		} catch (Exception e) {
			log.info("LOG_ANALYSIS log error!check jsonObject param!",e);
		}
	}
	
	public static LogAnalysis getLogAnalysis(Log bizLog,String bizOperaType){
		if(bizLog == null || StringUtils.isBlank(bizOperaType)){
			return null;
		}
		return new LogAnalysis(bizLog, bizOperaType);
	}
	
}
