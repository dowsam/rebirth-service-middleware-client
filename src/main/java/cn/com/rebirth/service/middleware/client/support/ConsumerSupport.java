/*
 * Copyright (c) 2005-2012 www.china-cti.com All rights reserved
 * Info:rebirth-service-middleware-client ConsumerSupport.java 2012-7-17 17:06:03 l.xue.nong$$
 */
package cn.com.rebirth.service.middleware.client.support;

import static java.lang.String.format;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.com.rebirth.commons.component.AbstractLifecycleComponent;
import cn.com.rebirth.commons.exception.RebirthException;
import cn.com.rebirth.commons.settings.Settings;
import cn.com.rebirth.service.middleware.client.ChannelRing;
import cn.com.rebirth.service.middleware.client.Receiver;
import cn.com.rebirth.service.middleware.commons.Messages;
import cn.com.rebirth.service.middleware.commons.protocol.RmiRequest;

import com.google.common.collect.Maps;

/**
 * The Class ConsumerSupport.
 *
 * @author l.xue.nong
 */
public class ConsumerSupport extends AbstractLifecycleComponent<ConsumerSupport> implements Receiver, ChannelRing {

	/** The messages. */
	protected final Messages messages;

	/**
	 * Instantiates a new consumer support.
	 *
	 * @param settings the settings
	 * @param messages the messages
	 */
	public ConsumerSupport(Settings settings, Messages messages) {
		super(settings);
		this.messages = messages;
	}

	/** The logger. */
	private final Logger logger = LoggerFactory.getLogger(ConsumerSupport.class);

	/** The channel ring support. */
	private ChannelRingSupport channelRingSupport;

	/** The service discovery support. */
	private ServiceDiscoverySupport serviceDiscoverySupport;

	/** The wrappers. */
	private Map<Long, Receiver.Wrapper> wrappers;

	/* (non-Javadoc)
	 * @see cn.com.rebirth.service.middleware.client.Receiver#register(cn.com.rebirth.service.middleware.commons.protocol.RmiRequest)
	 */
	@Override
	public Receiver.Wrapper register(RmiRequest req) {
		if (wrappers.containsKey(req.getId())) {
			if (logger.isInfoEnabled()) {
				logger.info(format("an duplicate request existed, this one will ignore. req:%s", req));
			}
			return wrappers.get(req.getId());
		}

		final Receiver.Wrapper wrapper = new Receiver.Wrapper(req);
		wrappers.put(req.getId(), wrapper);
		return wrapper;
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.service.middleware.client.Receiver#unRegister(long)
	 */
	@Override
	public Receiver.Wrapper unRegister(long id) {
		return wrappers.remove(id);
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.service.middleware.client.Receiver#receive(long)
	 */
	@Override
	public Receiver.Wrapper receive(long id) {
		return wrappers.remove(id);
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.service.middleware.client.ChannelRing#ring(cn.com.rebirth.service.middleware.commons.protocol.RmiRequest)
	 */
	@Override
	public ChannelRing.Wrapper ring(RmiRequest req) {
		return channelRingSupport.ring(req);
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.commons.component.AbstractLifecycleComponent#doStart()
	 */
	@Override
	protected void doStart() throws RebirthException {
		// new 
		channelRingSupport = new ChannelRingSupport(settings, messages);
		serviceDiscoverySupport = new ServiceDiscoverySupport(settings, messages);

		// setter
		channelRingSupport.setReceiver(this);

		// init
		channelRingSupport.start();
		serviceDiscoverySupport.start();

		wrappers = Maps.newConcurrentMap();
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.commons.component.AbstractLifecycleComponent#doStop()
	 */
	@Override
	protected void doStop() throws RebirthException {
		if (null != channelRingSupport) {
			channelRingSupport.stop();
		}
		if (null != serviceDiscoverySupport) {
			serviceDiscoverySupport.stop();
		}
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.commons.component.AbstractLifecycleComponent#doClose()
	 */
	@Override
	protected void doClose() throws RebirthException {

	}

}
