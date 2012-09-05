/*
 * Copyright (c) 2005-2012 www.china-cti.com All rights reserved
 * Info:rebirth-service-middleware-client ChannelRing.java 2012-7-17 16:27:53 l.xue.nong$$
 */
package cn.com.rebirth.service.middleware.client;

import org.jboss.netty.channel.Channel;

import cn.com.rebirth.service.middleware.commons.protocol.RmiRequest;

/**
 * The Interface ChannelRing.
 *
 * @author l.xue.nong
 */
public interface ChannelRing {

	/**
	 * The Class Wrapper.
	 *
	 * @author l.xue.nong
	 */
	public static class Wrapper {

		/** The channel. */
		private final Channel channel; //链接

		/** The maybe down. */
		private boolean maybeDown; //可能损坏

		/**
		 * Instantiates a new wrapper.
		 *
		 * @param channel the channel
		 */
		public Wrapper(Channel channel) {
			this.channel = channel;
		}

		/**
		 * Checks if is maybe down.
		 *
		 * @return true, if is maybe down
		 */
		public boolean isMaybeDown() {
			return maybeDown;
		}

		/**
		 * Sets the maybe down.
		 *
		 * @param maybeDown the new maybe down
		 */
		public void setMaybeDown(boolean maybeDown) {
			this.maybeDown = maybeDown;
		}

		/**
		 * Gets the channel.
		 *
		 * @return the channel
		 */
		public Channel getChannel() {
			return channel;
		}

	}

	/**
	 * Ring.
	 *
	 * @param req the req
	 * @return the channel ring. wrapper
	 */
	ChannelRing.Wrapper ring(RmiRequest req);
}
