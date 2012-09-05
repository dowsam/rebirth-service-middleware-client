/*
 * Copyright (c) 2005-2012 www.china-cti.com All rights reserved
 * Info:rebirth-service-middleware-client Receiver.java 2012-7-17 16:26:25 l.xue.nong$$
 */
package cn.com.rebirth.service.middleware.client;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import cn.com.rebirth.service.middleware.commons.protocol.RmiRequest;
import cn.com.rebirth.service.middleware.commons.protocol.RmiResponse;

/**
 * The Interface Receiver.
 *
 * @author l.xue.nong
 */
public interface Receiver {

	/**
	 * The Class Wrapper.
	 *
	 * @author l.xue.nong
	 */
	public final static class Wrapper {

		/** The request. */
		private final RmiRequest request;

		/** The response. */
		private RmiResponse response;

		/** The lock. */
		private final ReentrantLock lock;

		/** The wait resp. */
		private final Condition waitResp;

		/**
		 * Instantiates a new wrapper.
		 *
		 * @param request the request
		 */
		public Wrapper(RmiRequest request) {
			this.request = request;
			this.lock = new ReentrantLock(false);
			this.waitResp = lock.newCondition();
		}

		/**
		 * Signal wait resp.
		 */
		public void signalWaitResp() {
			try {
				lock.lock();
				waitResp.signal();
			} finally {
				lock.unlock();
			}
		}

		/**
		 * Gets the request.
		 *
		 * @return the request
		 */
		public RmiRequest getRequest() {
			return request;
		}

		/**
		 * Gets the response.
		 *
		 * @return the response
		 */
		public RmiResponse getResponse() {
			return response;
		}

		/**
		 * Sets the response.
		 *
		 * @param response the new response
		 */
		public void setResponse(RmiResponse response) {
			this.response = response;
		}

		/**
		 * Gets the lock.
		 *
		 * @return the lock
		 */
		public ReentrantLock getLock() {
			return lock;
		}

		/**
		 * Gets the wait resp.
		 *
		 * @return the wait resp
		 */
		public Condition getWaitResp() {
			return waitResp;
		}

	}

	/**
	 * Register.
	 *
	 * @param req the req
	 * @return the receiver. wrapper
	 */
	Receiver.Wrapper register(RmiRequest req);

	/**
	 * Un register.
	 *
	 * @param id the id
	 * @return the receiver. wrapper
	 */
	Receiver.Wrapper unRegister(long id);

	/**
	 * Receive.
	 *
	 * @param id the id
	 * @return the receiver. wrapper
	 */
	Receiver.Wrapper receive(long id);

}
