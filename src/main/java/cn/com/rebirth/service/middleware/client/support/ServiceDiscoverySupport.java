/*
 * Copyright (c) 2005-2012 www.china-cti.com All rights reserved
 * Info:rebirth-service-middleware-client ServiceDiscoverySupport.java 2012-7-17 16:52:10 l.xue.nong$$
 */
package cn.com.rebirth.service.middleware.client.support;

import static java.lang.String.format;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkDataListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.com.rebirth.commons.component.AbstractLifecycleComponent;
import cn.com.rebirth.commons.exception.RebirthException;
import cn.com.rebirth.commons.search.config.support.ZooKeeperExpand;
import cn.com.rebirth.commons.settings.Settings;
import cn.com.rebirth.commons.utils.ZkUtils;
import cn.com.rebirth.service.middleware.client.ConsumerService;
import cn.com.rebirth.service.middleware.client.message.ChannelChangedMessage;
import cn.com.rebirth.service.middleware.client.message.ChannelChangedMessage.Type;
import cn.com.rebirth.service.middleware.client.message.SubscribeServiceMessage;
import cn.com.rebirth.service.middleware.commons.Message;
import cn.com.rebirth.service.middleware.commons.MessageSubscriber;
import cn.com.rebirth.service.middleware.commons.Messages;

import com.google.common.collect.Maps;

/**
 * The Class ServiceDiscoverySupport.
 *
 * @author l.xue.nong
 */
public class ServiceDiscoverySupport extends AbstractLifecycleComponent<ServiceDiscoverySupport> implements
		MessageSubscriber {

	/** The messages. */
	protected final Messages messages;

	/**
	 * Instantiates a new service discovery support.
	 *
	 * @param settings the settings
	 * @param messages the messages
	 */
	protected ServiceDiscoverySupport(Settings settings, Messages messages) {
		super(settings);
		this.messages = messages;
	}

	/** The logger. */
	private final Logger logger = LoggerFactory.getLogger(ServiceDiscoverySupport.class);

	/** The services. */
	private Map<String, ConsumerService> services;

	/**
	 * Post channel changed message.
	 *
	 * @param path the path
	 * @param type the type
	 */
	private void postChannelChangedMessage(String path, Type type) {
		//"/rebirth/service/middleware/nondurable/G1/1.0.0/176349878f5a1bb7df5b61741d981d35/127.0.0.1:3658";
		final String[] strs = path.split("/");
		final String key = format("%s%s%s", strs[5]/*group*/, strs[6]/*version*/, strs[7]/*sign*/);
		final String[] addressStrs = strs[8].split(":");
		final InetSocketAddress address = new InetSocketAddress(addressStrs[0], Integer.valueOf(addressStrs[1]));
		final ConsumerService service = services.get(key);
		messages.post(new ChannelChangedMessage(service, address, type));
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.service.middleware.commons.MessageSubscriber#receive(cn.com.rebirth.service.middleware.commons.Message)
	 */
	@Override
	public void receive(Message<?> msg) throws RebirthException {
		if (!(msg instanceof SubscribeServiceMessage)) {
			return;
		}
		final SubscribeServiceMessage ssMsg = (SubscribeServiceMessage) msg;
		final ConsumerService service = ssMsg.getContent();
		final String pref = format("/rebirth/service/middleware/nondurable/%s/%s/%s", service.getGroup(),
				service.getVersion(), service.getSign());

		try {
			services.put(service.getKey(), service);
			ZooKeeperExpand.getInstance().getZkClient().subscribeChildChanges(pref, new IZkChildListener() {

				@Override
				public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {
					listenerChilds(currentChilds, pref);
				}
			});
			if (ZkUtils.pathExists(ZooKeeperExpand.getInstance().getZkClient(), pref)) {
				List<String> childs = ZooKeeperExpand.getInstance().list(pref);
				listenerChilds(childs, pref);
			}
		} catch (Exception e) {
			logger.warn(format("subscribe %s was failed", pref), e);
		}

	}

	/**
	 * Listener childs.
	 *
	 * @param childs the childs
	 * @param pref the pref
	 */
	private void listenerChilds(List<String> childs, String pref) {
		for (String ch : childs) {
			final String _ch = format("%s/%s", pref, ch);
			postChannelChangedMessage(_ch, Type.CREATE);
			ZooKeeperExpand.getInstance().getZkClient().subscribeDataChanges(_ch, new IZkDataListener() {

				@Override
				public void handleDataDeleted(String dataPath) throws Exception {
					postChannelChangedMessage(_ch, Type.REMOVE);
				}

				@Override
				public void handleDataChange(String dataPath, Object data) throws Exception {
					postChannelChangedMessage(_ch, Type.CREATE);
				}
			});
		}
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.commons.component.AbstractLifecycleComponent#doStart()
	 */
	@Override
	protected void doStart() throws RebirthException {
		Messages.register(this, SubscribeServiceMessage.class);
		services = Maps.newConcurrentMap();
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.commons.component.AbstractLifecycleComponent#doStop()
	 */
	@Override
	protected void doStop() throws RebirthException {
		if (services != null)
			services.clear();
	}

	/* (non-Javadoc)
	 * @see cn.com.rebirth.commons.component.AbstractLifecycleComponent#doClose()
	 */
	@Override
	protected void doClose() throws RebirthException {

	}

}
