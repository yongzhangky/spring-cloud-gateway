package org.springframework.cloud.gateway.support.msg;

public class CnMessage extends Message {

	private static volatile CnMessage instance;

	public static CnMessage getInstance() {
		if (null == instance) {
			synchronized (CnMessage.class) {
				if (null == instance) {
					instance = new CnMessage();
				}
			}
		}

		return instance;
	}

	@Override
	public String getProjectNoResourceGroup(String project) {
		return String.format("[GATEWAY-000003] 当前项目”%s”未绑定资源组，请联系管理员为其绑定资源组。", project);
	}

	@Override
	public String getProjectNoInstance(String project) {
		return String.format("[GATEWAY-000002] 当前项目“%s”没有可用实例。请联系管理员检查实例状态后重试。", project);
	}

	@Override
	public String getNoInstance() {
		return "[GATEWAY-000001] 没有可用实例。请联系管理员检查实例状态后重试。";
	}

	@Override
	public String getSysError() {
		return "[GATEWAY-000000] 服务器异常";
	}
}
