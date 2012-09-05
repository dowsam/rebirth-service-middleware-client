/*
 * Copyright (c) 2005-2012 www.china-cti.com All rights reserved
 * Info:rebirth-service-middleware-client ConsumerProxyFactory.java 2012-8-6 17:35:06 l.xue.nong$$
 */
package cn.com.rebirth.service.middleware.client;

import static java.lang.String.format;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.com.rebirth.commons.exception.RebirthException;
import cn.com.rebirth.commons.service.middleware.annotation.Rmi;
import cn.com.rebirth.commons.service.middleware.annotation.RmiMethod;
import cn.com.rebirth.commons.settings.ImmutableSettings;
import cn.com.rebirth.commons.settings.Settings;
import cn.com.rebirth.commons.utils.NonceUtils;
import cn.com.rebirth.commons.utils.ResolverUtils;
import cn.com.rebirth.service.middleware.client.message.SubscribeServiceMessage;
import cn.com.rebirth.service.middleware.client.support.ConsumerSupport;
import cn.com.rebirth.service.middleware.commons.Messages;
import cn.com.rebirth.service.middleware.commons.exception.ProviderNotFoundException;
import cn.com.rebirth.service.middleware.commons.exception.ServiceNotFoundException;
import cn.com.rebirth.service.middleware.commons.exception.ThreadPoolOverflowException;
import cn.com.rebirth.service.middleware.commons.exception.UnknowCodeException;
import cn.com.rebirth.service.middleware.commons.protocol.RmiRequest;
import cn.com.rebirth.service.middleware.commons.protocol.RmiResponse;
import cn.com.rebirth.service.middleware.commons.utils.SerializerUtils;
import cn.com.rebirth.service.middleware.commons.utils.SignatureUtils;

import com.google.common.collect.Maps;

/**
 * A factory for creating ConsumerProxy objects.
 */
public class ConsumerProxyFactory {

	/** The messages. */
	private final Messages messages;

	/** The settings. */
	private final Settings settings;

	private volatile Map<Class<?>, Object> context = Maps.newHashMap();

	/**
	 * Instantiates a new consumer proxy factory.
	 */
	private ConsumerProxyFactory() {
		this(initialSettings());
	}

	/**
	 * Gets the single instance of ConsumerProxyFactory.
	 *
	 * @return single instance of ConsumerProxyFactory
	 */
	public static ConsumerProxyFactory getInstance() {
		return SingletonHolder.consumerProxyFactory;
	}

	/**
	 * The Class SingletonHolder.
	 *
	 * @author l.xue.nong
	 */
	private static class SingletonHolder {

		/** The consumer proxy factory. */
		volatile static ConsumerProxyFactory consumerProxyFactory = new ConsumerProxyFactory();
	}

	/**
	 * Initial settings.
	 *
	 * @return the settings
	 */
	private static Settings initialSettings() {
		ImmutableSettings.Builder settingsBuilder = ImmutableSettings.settingsBuilder()
				.put(ImmutableSettings.Builder.EMPTY_SETTINGS)
				.putProperties("rebirth.service.middleware.", System.getProperties()).replacePropertyPlaceholders();
		settingsBuilder.loadFromClasspath("rebirth-service-middleware-client.properties");
		settingsBuilder.putProperties("rebirth.service.middleware.", System.getProperties())
				.putProperties("es.", System.getProperties()).replacePropertyPlaceholders();
		if (settingsBuilder.get("name") == null) {
			String name = System.getProperty("name");
			if (name == null || name.isEmpty()) {
				name = settingsBuilder.get("node.name");
				if (name == null || name.isEmpty()) {
					name = "Rebirth-Service-Midddleware-Node-client" + NonceUtils.randomInt();
				}
			}

			if (name != null) {
				settingsBuilder.put("name", name);
			}
		}
		Settings v1 = settingsBuilder.build();
		settingsBuilder = ImmutableSettings.settingsBuilder().put(v1);
		return settingsBuilder.build();
	}

	/**
	 * Instantiates a new consumer proxy factory.
	 *
	 * @param settings the settings
	 */
	private ConsumerProxyFactory(final Settings settings) {
		this.settings = settings;
		this.messages = new Messages(settings);
		support = new ConsumerSupport(settings, messages);
		support.start();
		Runtime.getRuntime().addShutdownHook(new Thread() {

			@Override
			public void run() {
				support.stop();
			}

		});
		scan();
	}

	private void scan() {
		ResolverUtils<Rmi> resolverUtils = new ResolverUtils<Rmi>();
		resolverUtils.findAnnotated(Rmi.class, StringUtils.EMPTY);
		Set<Class<? extends Rmi>> classes = resolverUtils.getClasses();
		for (Class<? extends Rmi> class1 : classes) {
			if (class1.isInterface()) {
				context.put(class1, proxy(class1));
			}
		}
	}

	/** The network log. */
	private final Logger networkLog = LoggerFactory.getLogger(ConsumerProxyFactory.class);

	/** The support. */
	private ConsumerSupport support;

	/**
	 * Creates a new ConsumerProxy object.
	 *
	 * @param group the group
	 * @param version the version
	 * @param defaultTimeout the default timeout
	 * @param methodTimeoutMap the method timeout map
	 * @return the invocation handler
	 */
	private InvocationHandler createConsumerProxyHandler(final Class<?> targetInterface,final String group, final String version,
			final long defaultTimeout, final Map<String, Long> methodTimeoutMap) {
		return new InvocationHandler() {

			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				final String sign = SignatureUtils.signature(targetInterface,method);
				final long timeout = getFixTimeout(method, defaultTimeout, methodTimeoutMap);
				final RmiRequest req = new RmiRequest(settings, group, version, sign,
						SerializerUtils.changeToSerializable(args), settings.get("name"), timeout);

				final Receiver.Wrapper receiverWrapper = support.register(req);
				if (null == receiverWrapper) {
					throw new ProviderNotFoundException(format("provider not found. req:%s", req));
				}
				final ReentrantLock lock = receiverWrapper.getLock();
				long leftTimeout;
				final long start = System.currentTimeMillis();
				final ChannelRing.Wrapper channelWrapper;
				try {
					lock.lock();
					try {
						channelWrapper = waitForWrite(req);
					} catch (Throwable t) {
						support.unRegister(req.getId());
						throw t;
					}
					if (!settings.getAsBoolean("development.model", false)) {
						leftTimeout = timeout - (System.currentTimeMillis() - start);
						if (leftTimeout <= 0) {
							throw new TimeoutException(format(
									"request:%s is waiting for write ready, but timeout:%dms", req, timeout));
						}
						waitForReceive(receiverWrapper, req, leftTimeout);
					}
					waitForReceive(receiverWrapper, req, Long.MAX_VALUE);
				} finally {
					lock.unlock();
				}
				return getReturnObject(receiverWrapper, channelWrapper);
			}

		};
	}

	/**
	 * Proxy.
	 *
	 * @param <T> the generic type
	 * @param targetInterface the target interface
	 * @return the t
	 */
	public <T> T proxy(Class<T> targetInterface) {
		if (targetInterface.isInterface() || targetInterface.isAnnotationPresent(Rmi.class)) {
			Rmi rmi = targetInterface.getAnnotation(Rmi.class);
			Map<String, Long> methodTimeoutMap = findMethodTimeOut(targetInterface);
			return proxy(targetInterface, rmi.group(), rmi.version(), rmi.timeOut(), methodTimeoutMap);
		}
		return null;
	}

	/**
	 * Find method time out.
	 *
	 * @param targetInterface the target interface
	 * @return the map
	 */
	private Map<String, Long> findMethodTimeOut(Class<?> targetInterface) {
		Map<String, Long> map = Maps.newHashMap();
		Method[] methods = targetInterface.getMethods();
		for (Method method : methods) {
			RmiMethod rmiMethod = method.getAnnotation(RmiMethod.class);
			if (rmiMethod != null) {
				map.put(method.getName(), rmiMethod.timeOut());
			}
		}
		return map;
	}

	/**
	 * Proxy.
	 *
	 * @param <T> the generic type
	 * @param targetInterface the target interface
	 * @param group the group
	 * @param version the version
	 * @param defaultTimeout the default timeout
	 * @param methodTimeoutMap the method timeout map
	 * @return the t
	 * @throws RebirthException the rebirth exception
	 */
	@SuppressWarnings("unchecked")
	public <T> T proxy(Class<T> targetInterface, String group, String version, long defaultTimeout,
			Map<String, Long> methodTimeoutMap) throws RebirthException {
		T t = (T) context.get(targetInterface);
		if (t != null) {
			return t;
		}
		check(targetInterface, group, version);
		final InvocationHandler consumerProxyHandler = createConsumerProxyHandler(targetInterface,group, version, defaultTimeout,
				methodTimeoutMap);

		Object proxyObject = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
				new Class<?>[] { targetInterface }, consumerProxyHandler);
		registerService(targetInterface, group, version, defaultTimeout, methodTimeoutMap);
		context.put(targetInterface, proxyObject);
		return (T) proxyObject;

	}

	/**
	 * Wait for write.
	 *
	 * @param req the req
	 * @return the channel ring. wrapper
	 * @throws Throwable the throwable
	 */
	private ChannelRing.Wrapper waitForWrite(RmiRequest req) throws Throwable {

		ChannelRing.Wrapper channelWrapper = support.ring(req);
		if (null == channelWrapper) {
			throw new ProviderNotFoundException(format("provider not found. req:%s", req));
		}

		ChannelFuture future = channelWrapper.getChannel().write(req);
		future.awaitUninterruptibly();
		if (!future.isSuccess()) {
			Throwable cause = future.getCause();
			if (hasNetworkException(cause)) {
				channelWrapper.setMaybeDown(true);
			}
			networkLog.warn(format("write req:%s failed. maybeDown:%s", req, channelWrapper.isMaybeDown()),
					future.getCause());
			throw future.getCause();
		}
		return channelWrapper;

	}

	/**
	 * Checks for network exception.
	 *
	 * @param t the t
	 * @return true, if successful
	 */
	boolean hasNetworkException(Throwable t) {
		Throwable cause = t;
		while (cause != null) {
			if (cause instanceof IOException) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}

	/**
	 * Wait for receive.
	 *
	 * @param wrapper the wrapper
	 * @param req the req
	 * @param leftTimeout the left timeout
	 * @return the receiver. wrapper
	 * @throws TimeoutException the timeout exception
	 * @throws ProviderNotFoundException the provider not found exception
	 * @throws InterruptedException the interrupted exception
	 */
	private Receiver.Wrapper waitForReceive(Receiver.Wrapper wrapper, RmiRequest req, long leftTimeout)
			throws TimeoutException, ProviderNotFoundException, InterruptedException {
		wrapper.getWaitResp().await(leftTimeout, TimeUnit.MILLISECONDS);

		if (null == wrapper.getResponse()) {
			throw new TimeoutException(format("req:%s timeout:%dms", req, req.getTimeout()));
		}

		return wrapper;

	}

	/**
	 * Gets the return object.
	 *
	 * @param receiverWrapper the receiver wrapper
	 * @param channelWrapper the channel wrapper
	 * @return the return object
	 * @throws Throwable the throwable
	 */
	private Object getReturnObject(Receiver.Wrapper receiverWrapper, ChannelRing.Wrapper channelWrapper)
			throws Throwable {
		final RmiRequest req = receiverWrapper.getRequest();
		final RmiResponse resp = receiverWrapper.getResponse();
		final Channel channel = channelWrapper.getChannel();
		switch (resp.getCode()) {
		case RmiResponse.RESULT_CODE_FAILED_TIMEOUT:
			throw new TimeoutException(format(
					"received response, but response report provider:%s was timeout. req:%s;resp:%s;",
					channel.getRemoteAddress(), req, resp));
		case RmiResponse.RESULT_CODE_FAILED_BIZ_THREAD_POOL_OVERFLOW:
			throw new ThreadPoolOverflowException(format(
					"received response, but response report provider:%s was overflow. req:%s;resp:%s;",
					channel.getRemoteAddress(), req, resp));
		case RmiResponse.RESULT_CODE_FAILED_SERVICE_NOT_FOUND:
			throw new ServiceNotFoundException(format(
					"received response, but response report provider:%s was not found the method. req:%s;resp:%s;",
					channel.getRemoteAddress(), req, resp));
		case RmiResponse.RESULT_CODE_SUCCESSED_RETURN:
			return resp.getObject();
		case RmiResponse.RESULT_CODE_SUCCESSED_THROWABLE:
			throw (Throwable) resp.getObject();
		default:
			throw new UnknowCodeException(format(
					"received response, but response's code is illegal. provider:%s;req:%s;resp:%s;",
					channel.getRemoteAddress(), req, resp));
		}//case
	}

	/**
	 * Check.
	 *
	 * @param targetInterface the target interface
	 * @param group the group
	 * @param version the version
	 */
	private void check(Class<?> targetInterface, String group, String version) {
		if (StringUtils.isBlank(version)) {
			throw new IllegalArgumentException("version is blank");
		}
		if (StringUtils.isBlank(group)) {
			throw new IllegalArgumentException("group is blank");
		}
		if (null == targetInterface) {
			throw new IllegalArgumentException("targetInterface is null");
		}

		Method[] methods = targetInterface.getMethods();
		if (null != methods) {
			for (Method method : methods) {
				//				if (!SerializerUtils.isSerializableType(method.getReturnType())) {
				//					throw new IllegalArgumentException("method returnType is not serializable");
				//				}
				if (!SerializerUtils.isSerializableType(method.getParameterTypes())) {
					throw new IllegalArgumentException("method parameter is not serializable");
				}
			}//for
		}//if

	}

	/**
	 * Gets the fix timeout.
	 *
	 * @param method the method
	 * @param defaultTimeout the default timeout
	 * @param methodTimeoutMap the method timeout map
	 * @return the fix timeout
	 */
	private long getFixTimeout(Method method, long defaultTimeout, Map<String, Long> methodTimeoutMap) {
		if (methodTimeoutMap != null && !methodTimeoutMap.isEmpty() && methodTimeoutMap.containsKey(method.getName())) {
			Long timeout = methodTimeoutMap.get(method.getName());
			if (null != timeout && timeout > 0) {
				return timeout;
			}//if
		}//if

		return defaultTimeout > 0 ? defaultTimeout : 500;

	}

	/**
	 * Register service.
	 *
	 * @param targetInterface the target interface
	 * @param group the group
	 * @param version the version
	 * @param defaultTimeout the default timeout
	 * @param methodTimeoutMap the method timeout map
	 */
	private void registerService(Class<?> targetInterface, String group, String version, long defaultTimeout,
			Map<String, Long> methodTimeoutMap) {
		final Method[] methods = targetInterface.getMethods();
		if (ArrayUtils.isEmpty(methods)) {
			return;
		}
		for (Method method : methods) {
			final long timeout = getFixTimeout(method, defaultTimeout, methodTimeoutMap);
			final String sign = SignatureUtils.signature(targetInterface, method);
			final ConsumerService service = new ConsumerService(group, version, sign, timeout);
			messages.post(new SubscribeServiceMessage(service));
		}
	}

}
