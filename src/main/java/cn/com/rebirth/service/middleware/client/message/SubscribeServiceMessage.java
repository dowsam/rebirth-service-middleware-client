/*
 * Copyright (c) 2005-2012 www.china-cti.com All rights reserved
 * Info:rebirth-service-middleware-client SubscribeServiceMessage.java 2012-7-17 16:33:29 l.xue.nong$$
 */
package cn.com.rebirth.service.middleware.client.message;

import cn.com.rebirth.service.middleware.client.ConsumerService;
import cn.com.rebirth.service.middleware.commons.Message;

/**
 * The Class SubscribeServiceMessage.
 *
 * @author l.xue.nong
 */
public class SubscribeServiceMessage extends Message<ConsumerService> {

	/**
	 * Instantiates a new subscribe service message.
	 *
	 * @param t the t
	 */
	public SubscribeServiceMessage(ConsumerService t) {
		super(t);
	}

}
