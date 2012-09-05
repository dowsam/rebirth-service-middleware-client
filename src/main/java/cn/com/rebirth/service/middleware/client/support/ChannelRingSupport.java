/*
 * Copyright (c) 2005-2012 www.china-cti.com All rights reserved
 * Info:rebirth-service-middleware-client ChannelRingSupport.java 2012-7-17 16:40:50 l.xue.nong$$
 */
package cn.com.rebirth.service.middleware.client.support;

import static java.lang.String.format;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.StringUtils;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.com.rebirth.commons.component.AbstractLifecycleComponent;
import cn.com.rebirth.commons.exception.RebirthException;
import cn.com.rebirth.commons.settings.Settings;
import cn.com.rebirth.service.middleware.client.ChannelRing;
import cn.com.rebirth.service.middleware.client.Receiver;
import cn.com.rebirth.service.middleware.client.message.ChannelChangedMessage;
import cn.com.rebirth.service.middleware.commons.Message;
import cn.com.rebirth.service.middleware.commons.MessageSubscriber;
import cn.com.rebirth.service.middleware.commons.Messages;
import cn.com.rebirth.service.middleware.commons.Ring;
import cn.com.rebirth.service.middleware.commons.protocol.RmiRequest;
import cn.com.rebirth.service.middleware.commons.protocol.RmiResponse;
import cn.com.rebirth.service.middleware.commons.protocol.coder.ProtocolDecoder;
import cn.com.rebirth.service.middleware.commons.protocol.coder.ProtocolEncoder;
import cn.com.rebirth.service.middleware.commons.protocol.coder.RmiDecoder;
import cn.com.rebirth.service.middleware.commons.protocol.coder.RmiEncoder;
import cn.com.rebirth.service.middleware.commons.serializer.SerializerFactory;

import com.google.common.collect.Maps;

/**
 * The Class ChannelRingSupport.
 *
 * @author l.xue.nong
 */
public class ChannelRingSupport extends AbstractLifecycleComponent<ChannelRingSupport> implements ChannelRing,
		MessageSubscriber {

	/** The serializer factory. */
	protected final SerializerFactory serializerFactory;

	/** The messages. */
	protected final Messages messages;

	/**
	 * Instantiates a new channel ring support.
	 *
	 * @param settings the settings
	 * @param messages the messages
	 */
	protected ChannelRingSupport(Settings settings, Messages messages) {
		super(settings);
		this.serializerFactory = new SerializerFactory(settings);
		this.messages = messages;
		this.connectTimeout = componentSettings.getAsInt("connectTimeout", 500);
	}

	/** The logger. */
	private final Logger logger = LoggerFactory.getLogger(ChannelRingSupport.class);

	/** The connect timeout. */
	private final int connectTimeout;

	/** The receiver. */
	private Receiver receiver;

	/** The service channel rings. */
	private Map<String/*group+version+sign*/, Ring<ChannelRing.Wrapper>> serviceChannelRings;

	/** The bootstrap. */
	private ClientBootstrap bootstrap;

	/** The channel group. */
	private ChannelGroup channelGroup;

	/** The business handler. */
	private SimpleChannelUpstreamHandler businessHandler = new SimpleChannelUpstreamHandler() {

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
			if (null == e.getMessage() || !(e.getMessage() instanceof RmiResponse)) {
				super.messageReceived(ctx, e);
			}

			final RmiResponse resp = (RmiResponse) e.getMessage();
			Receiver.Wrapper wrapper = receiver.receive(resp.getId());
			if (null == wrapper) {
				if (logger.isInfoEnabled()) {
					logger.info(format("received response, but request was not found, looks like timeout. resp:%s",
							resp));
				}
			} else {
				wrapper.setResponse(resp);
				wrapper.signalWaitResp();
			}
		}

	};

	/** The channel pipeline factory. */
	private ChannelPipelineFactory channelPipelineFactory = new ChannelPipelineFactory() {

		@Override
		public ChannelPipeline getPipeline() throws Exception {
			ChannelPipeline pipeline = Channels.pipeline();
			pipeline.addLast("protocol-decoder", new ProtocolDecoder());
			pipeline.addLast("rmi-decoder", new RmiDecoder(serializerFactory));
			pipeline.addLast("businessHandler", businessHandler);
			pipeline.addLast("protocol-encoder", new ProtocolEncoder());
			pipeline.addLast("rmi-encoder", new RmiEncoder(serializerFactory));
			return pipeline;
		}

	};

	/* (non-Javadoc)
	 * @see cn.com.rebirth.service.middleware.commons.MessageSubscriber#receive(cn.com.rebirth.service.middleware.commons.Message)
	 */
	@Override
	public void receive(Message<?> msg) throws RebirthException {
		if (!(msg instanceof ChannelChangedMessage)) {
			return;
		}
		final ChannelChangedMessage ccMsg = (ChannelChangedMessage) msg;
		switch (ccMsg.getType()) {
		case CREATE:
			handleChannelCreate(ccMsg);
			break;
		case REMOVE:
			handleChannelRemove(ccMsg);
			break;
		}

	}

	/**
	 * Handle channel create.
	 *
	 * @param ccMsg the cc msg
	 */
	private synchronized void handleChannelCreate(ChannelChangedMessage ccMsg) {
		if (ccMsg.getType() != ChannelChangedMessage.Type.CREATE) {
			return;
		}

		final String key = ccMsg.getContent().getKey();
		final Ring<Wrapper> ring;
		if (serviceChannelRings.containsKey(key)) {
			ring = serviceChannelRings.get(key);
		} else {
			ring = new Ring<Wrapper>();
			serviceChannelRings.put(key, ring);
		}

		boolean isExisted = false;
		Iterator<Wrapper> it = ring.iterator();
		while (it.hasNext()) {
			Channel channel = it.next().getChannel();
			if (!compareAddress((InetSocketAddress) channel.getRemoteAddress(), ccMsg.getAddress())) {
				continue;
			}
			if (channel.isConnected()) {
				isExisted = true;
				if (logger.isInfoEnabled()) {
					logger.info(format("found an connected channel, ignore this event. connect:%s",
							channel.getRemoteAddress()));
				}
				break;
			} else {
				if (logger.isInfoEnabled()) {
					logger.info(format("found an disconnected channel, drop it. connect:%s", channel.getRemoteAddress()));
				}
				it.remove();
				channel.disconnect();
				channel.close();
			}
		}
		if (!isExisted) {
			final Channel channel = getChannel(ccMsg.getAddress());
			if (null != channel) {
				ring.insert(new Wrapper(channel));
			} else {
				messages.post(ccMsg);
			}
		}

	}

	/**
	 * Handle channel remove.
	 *
	 * @param ccMsg the cc msg
	 */
	private synchronized void handleChannelRemove(ChannelChangedMessage ccMsg) {
		if (ccMsg.getType() != ChannelChangedMessage.Type.REMOVE) {
			return;
		}

		final String key = ccMsg.getContent().getKey();
		if (!serviceChannelRings.containsKey(key)) {
			return;
		}
		final Ring<Wrapper> ring = serviceChannelRings.get(key);
		Channel firstChannel = null;
		Iterator<Wrapper> it = ring.iterator();
		while (it.hasNext()) {
			final Channel channel = it.next().getChannel();
			if (channel == firstChannel) {
				break;
			}
			if (null == firstChannel) {
				firstChannel = channel;
			}
			if (compareAddress((InetSocketAddress) channel.getRemoteAddress(), ccMsg.getAddress())) {
				it.remove();
				channel.disconnect();
				channel.close();
				break;
			}
		}

	}

	/**
	 * Compare address.
	 *
	 * @param a the a
	 * @param b the b
	 * @return true, if successful
	 */
	private boolean compareAddress(InetSocketAddress a, InetSocketAddress b) {
		if (null == a && null != b) {
			return false;
		}
		if (null != a && null == b) {
			return false;
		}
		if (null == a && null == b) {
			return true;
		}
		return StringUtils.equals(a.getHostName(), b.getHostName()) && a.getPort() == b.getPort();
	}

	/**
	 * Gets the channel.
	 *
	 * @param address the address
	 * @return the channel
	 */
	private Channel getChannel(InetSocketAddress address) {
		final ChannelFuture future = bootstrap.connect(address);
		future.awaitUninterruptibly();
		if (future.isCancelled()) {
			logger.warn(format("connect is cancelled. address:%s", address));
			return null;
		}
		if (!future.isSuccess()) {
			logger.warn(format("connect to %s failed.", address), future.getCause());
			return null;
		}
		if (logger.isInfoEnabled()) {
			logger.info(format("connect to %s successed.", address));
		}
		final Channel channel = future.getChannel();
		channelGroup.add(channel);
		return channel;
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.service.middleware.client.ChannelRing#ring(cn.com.rebirth.service.middleware.commons.protocol.RmiRequest)
	 */
	@Override
	public ChannelRing.Wrapper ring(RmiRequest req) {
		final String key = req.getKey();
		if (!serviceChannelRings.containsKey(key)) {
			if (logger.isInfoEnabled()) {
				logger.info(format("provider not found. key not found. req:%s", req));
			}
			return null;
		}

		Ring<ChannelRing.Wrapper> ring = serviceChannelRings.get(key);
		if (ring.isEmpty()) {
			if (logger.isInfoEnabled()) {
				logger.info(format("provider not found. ring is empty. req:%s", req));
			}
			return null;
		}

		ChannelRing.Wrapper wrapper;
		try {
			wrapper = ring.ring();
		} catch (NoSuchElementException e) {
			if (logger.isInfoEnabled()) {
				logger.info(format("provider not found. no such elements. req:%s", req));
			}
			return null;
		}

		if (wrapper.isMaybeDown()) {
			Wrapper another = null;
			final Iterator<Wrapper> it = ring.iterator();
			while (it.hasNext()) {
				Wrapper w = it.next();
				if (!w.isMaybeDown()) {
					another = w;
				}
				if (w == wrapper) {
					it.remove();
					wrapper.getChannel().disconnect();
					wrapper.getChannel().close();
					if (logger.isInfoEnabled()) {
						logger.info(format("%s maybe down. close this channel.", wrapper.getChannel()
								.getRemoteAddress()));
					}
				}
			}
			wrapper = another;
		}//if

		return wrapper;

	}

	/**
	 * Sets the receiver.
	 *
	 * @param receiver the new receiver
	 */
	public void setReceiver(Receiver receiver) {
		this.receiver = receiver;
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.commons.component.AbstractLifecycleComponent#doStart()
	 */
	@Override
	protected void doStart() throws RebirthException {
		Messages.register(this, ChannelChangedMessage.class);
		serviceChannelRings = Maps.newConcurrentMap();
		channelGroup = new DefaultChannelGroup();
		bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
				Executors.newCachedThreadPool()));

		bootstrap.setPipelineFactory(channelPipelineFactory);
		bootstrap.setOption("tcpNoDelay", true);
		bootstrap.setOption("keepAlive", true);
		bootstrap.setOption("connectTimeoutMillis", connectTimeout);
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.commons.component.AbstractLifecycleComponent#doStop()
	 */
	@Override
	protected void doStop() throws RebirthException {
		if (null != channelGroup) {
			channelGroup.close();
		}
		if (null != bootstrap) {
			bootstrap.releaseExternalResources();
		}
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.commons.component.AbstractLifecycleComponent#doClose()
	 */
	@Override
	protected void doClose() throws RebirthException {

	}

}
