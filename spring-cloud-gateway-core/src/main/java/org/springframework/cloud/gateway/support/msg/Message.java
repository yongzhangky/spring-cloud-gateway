package org.springframework.cloud.gateway.support.msg;

import org.apache.commons.lang.StringUtils;

public class Message {

	private static volatile Message instance;

	public static Message getInstance() {
		if (null == instance) {
			synchronized (Message.class) {
				if (null == instance) {
					instance = new Message();
				}
			}
		}

		return instance;
	}

	public String getContext(String project, ErrorCode errorCode) {
		if (StringUtils.isNotEmpty(project)) {
			return String.format(errorCode.en, project);
		}
		return errorCode.en;
	}

	public String getContext(ErrorCode errorCode) {
		return errorCode.en;
	}
}
