/*
 * Copyright (c) 2005-2012 www.china-cti.com All rights reserved
 * Info:rebirth-service-middleware-client ChannelChangedMessage.java 2012-7-17 16:32:54 l.xue.nong$$
 */
package cn.com.rebirth.service.middleware.client.message;

import java.net.InetSocketAddress;

import cn.com.rebirth.service.middleware.client.ConsumerService;
import cn.com.rebirth.service.middleware.commons.Message;

/**
 * The Class ChannelChangedMessage.
 *
 * @author l.xue.nong
 */
public class ChannelChangedMessage extends Message<ConsumerService> {

	/**
	 * The Enum Type.
	 *
	 * @author l.xue.nong
	 */
	public static enum Type {

		/** The create. */
		CREATE,

		/** The remove. */
		REMOVE;
	}

	/** The type. */
	private final Type type; //消息类型

	/** The address. */
	private final InetSocketAddress address; //地址

	/**
	 * Instantiates a new channel changed message.
	 *
	 * @param t the t
	 * @param address the address
	 * @param type the type
	 */
	public ChannelChangedMessage(ConsumerService t, InetSocketAddress address, Type type) {
		super(t);
		this.address = address;
		this.type = type;
	}

	/**
	 * Gets the type.
	 *
	 * @return the type
	 */
	public Type getType() {
		return type;
	}

	/**
	 * Gets the address.
	 *
	 * @return the address
	 */
	public InetSocketAddress getAddress() {
		return address;
	}

}
