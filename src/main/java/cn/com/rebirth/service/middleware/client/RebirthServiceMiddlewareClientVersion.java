/*
 * Copyright (c) 2005-2012 www.china-cti.com All rights reserved
 * Info:rebirth-service-middleware-client RebirthServiceMiddlewareClientVersion.java 2012-7-17 16:23:42 l.xue.nong$$
 */
package cn.com.rebirth.service.middleware.client;

import cn.com.rebirth.commons.AbstractVersion;
import cn.com.rebirth.commons.Version;

/**
 * The Class RebirthServiceMiddlewareClientVersion.
 *
 * @author l.xue.nong
 */
public class RebirthServiceMiddlewareClientVersion extends AbstractVersion implements Version {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -6268831263177470306L;

	/* (non-Javadoc)
	 * @see cn.com.rebirth.commons.Version#getModuleName()
	 */
	@Override
	public String getModuleName() {
		return "rebirth-service-middleware-client";
	}

}
