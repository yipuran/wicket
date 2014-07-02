/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.resource;

import java.nio.charset.Charset;

import org.apache.wicket.MetaDataKey;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.ClientProperties;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.protocol.http.request.WebClientInfo;
import org.apache.wicket.request.IUrlRenderer;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.resource.UrlResourceReference;
import org.apache.wicket.util.string.Strings;


/**
 * A resource reference that loads jQuery javascript library from a content delivery network.
 * To add a JQuery resource reference to a component, do not use this reference, but use
 * {@link org.apache.wicket.settings.JavaScriptLibrarySettings#getJQueryReference()}
 * to prevent version conflicts.
 *
 * It uses the browser info to decide which version of jQuery to use. 1.x for IE8 and IE9, and 2.x for all others.
 *
 * @since 6.17.0
 */
public class JQueryCdnResourceReference extends UrlResourceReference
{
	private static final long serialVersionUID = 1L;

	/**
	 * The key for the metadata that is used as a cache to calculate the version
	 * only once per request cycle
	 */
	private static final MetaDataKey<String> KEY = new MetaDataKey<String>()
	{
	};

	private static class Holder {
		private static final JQueryCdnResourceReference INSTANCE = new JQueryCdnResourceReference();
	}

	/**
	 * Normally you should not use this method, but use
	 * {@link org.apache.wicket.settings.JavaScriptLibrarySettings#getJQueryReference()}
	 * to prevent version conflicts.
	 *
	 * @return the single instance of the resource reference
	 */
	public static JQueryCdnResourceReference get()
	{
		return Holder.INSTANCE;
	}

	/**
	 * The url to the preferred content delivery network.
	 * You may change it by adding the following code in YourApplication#init() method:
	 * <code>
	 *     JQueryCdnResourceReference.CDN_URL = Url.parse("http://ajax.aspnetcdn.com/ajax/jQuery/jquery-__VERSION__.min.js", null, true);
	 * </code>
	 */
	public static Url CDN_URL = Url.parse("//ajax.googleapis.com/ajax/libs/jquery/__VERSION__/jquery.min.js", null, true);

	/**
	 * Constructor
	 */
	public JQueryCdnResourceReference()
	{
		super(CDN_URL);
	}

	@Override
	public Url getUrl()
	{
		return new JQueryCdnUrl();
	}

	/**
	 * @return the version to use for IE8 and IE9, e.g. "1.10.0"
	 */
	protected String getVersion1()
	{
		return JQueryResourceReference.VERSION_1;
	}

	/**
	 * @return the version to use for modern browsers, e.g. "2.1.1"
	 */
	protected String getVersion2()
	{
		return DynamicJQueryResourceReference.VERSION_2;
	}

	/**
	 * A specialization of Url that knows how to render itself.
	 * This is needed to be able to calculate the version of jQuery to use dynamically.
	 */
	private class JQueryCdnUrl extends Url implements IUrlRenderer
	{
		private JQueryCdnUrl()
		{
			super(CDN_URL);
		}

		@Override
		public String toString(StringMode mode, Charset charset)
		{
			String version = getVersion();
			String withoutVersion = super.toString(StringMode.FULL, charset);
			return Strings.replaceAll(withoutVersion, "__VERSION__", version).toString();
		}

		private String getVersion()
		{
			RequestCycle requestCycle = RequestCycle.get();
			String version = requestCycle.getMetaData(KEY);
			if (version == null)
			{
				WebClientInfo clientInfo;
				version = getVersion2();
				if (Session.exists())
				{
					WebSession session = WebSession.get();
					clientInfo = session.getClientInfo();
				}
				else
				{
					clientInfo = new WebClientInfo(requestCycle);
				}
				ClientProperties clientProperties = clientInfo.getProperties();
				if (clientProperties.isBrowserInternetExplorer() && clientProperties.getBrowserVersionMajor() < 9)
				{
					version = getVersion1();
				}

				requestCycle.setMetaData(KEY, version);
			}
			return version;
		}

		@Override
		public String renderFullUrl(Url url, Url baseUrl)
		{
			return toString(StringMode.FULL);
		}

		@Override
		public String renderRelativeUrl(Url url, Url baseUrl)
		{
			return renderFullUrl(url, baseUrl);
		}
	}
}
