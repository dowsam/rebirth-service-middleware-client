/*
 * Copyright (c) 2005-2012 www.china-cti.com All rights reserved
 * Info:rebirth-service-middleware-client ConsumerService.java 2012-7-17 16:27:11 l.xue.nong$$
 */
package cn.com.rebirth.service.middleware.client;

/**
 * The Class ConsumerService.
 *
 * @author l.xue.nong
 */
public class ConsumerService {

	/** The key. */
	private final String key; //group+version+sign

	/** The group. */
	private final String group; //服务分组

	/** The version. */
	private final String version; //服务版本

	/** The sign. */
	private final String sign; //服务签名

	/** The timeout. */
	private final long timeout; //超时时间

	/**
	 * Instantiates a new consumer service.
	 *
	 * @param group the group
	 * @param version the version
	 * @param sign the sign
	 * @param timeout the timeout
	 */
	public ConsumerService(String group, String version, String sign, long timeout) {
		this.group = group;
		this.version = version;
		this.sign = sign;
		this.timeout = timeout;
		this.key = group + version + sign;
	}

	/**
	 * Gets the sign.
	 *
	 * @return the sign
	 */
	public String getSign() {
		return sign;
	}

	/**
	 * Gets the group.
	 *
	 * @return the group
	 */
	public String getGroup() {
		return group;
	}

	/**
	 * Gets the version.
	 *
	 * @return the version
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Gets the timeout.
	 *
	 * @return the timeout
	 */
	public long getTimeout() {
		return timeout;
	}

	/**
	 * Gets the key.
	 *
	 * @return the key
	 */
	public String getKey() {
		return key;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return String.format("sign=%s;group=%s;version=%s;timeout=%s", sign, group, version, timeout);
	}
}
