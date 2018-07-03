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
package org.apache.wicket;

import java.io.File;

import org.apache.wicket.page.IPageManager;
import org.apache.wicket.page.PageManager;
import org.apache.wicket.pageStore.AsynchronousPageStore;
import org.apache.wicket.pageStore.DiskPageStore;
import org.apache.wicket.pageStore.GroupingPageStore;
import org.apache.wicket.pageStore.IPageStore;
import org.apache.wicket.pageStore.InMemoryPageStore;
import org.apache.wicket.pageStore.InSessionPageStore;
import org.apache.wicket.pageStore.NoopPageStore;
import org.apache.wicket.pageStore.RequestPageStore;
import org.apache.wicket.pageStore.SerializingPageStore;
import org.apache.wicket.serialize.ISerializer;
import org.apache.wicket.settings.StoreSettings;
import org.apache.wicket.util.lang.Args;
import org.apache.wicket.util.lang.Bytes;

/**
 * A provider of a {@link PageManager} with a default chain of page {@link IPageStore}s:
 * <ul>
 * <li>{@link RequestPageStore} caching pages until end of the request</li>
 * <li>{@link InSessionPageStore} keeping the last accessed page in the session</li>
 * <li>{@link AsynchronousPageStore} moving storage of pages to a worker thread (if enabled in {@link StoreSettings#isAsynchronous()})</li>
 * <li>{@link DiskPageStore} keeping all pages, configured according to {@link StoreSettings}</li>
 * </ul>
 * An alternative chain with all pages held in-memory could be:
 * <ul>
 * <li>{@link RequestPageStore} caching pages until end of the request</li>
 * <li>{@link InSessionPageStore} keeping the last accessed page in the session</li>
 * <li>{@link SerializingPageStore} serializing all pages (so they are available for back-button </li>
 * <li>{@link InMemoryPageStore} keeping all pages</li>
 * </ul>
 * ... or if all pages should be kept in the session only without any serialization (no back-button support)
 * <ul>
 * <li>{@link RequestPageStore} caching pages until end of the request</li>
 * <li>{@link InSessionPageStore} keeping a limited count of pages in the session, e.g. 10</li>
 * <li>{@link NoopPageStore} discarding all exceeding pages</li>
 * </ul>
 * Other stores be may inserted ad libitum, e.g.
 * <ul>
 * <li>{@link GroupingPageStore} groups pages with their own maximum page limit</li>
 * </ul>
 */
public class DefaultPageManagerProvider implements IPageManagerProvider
{
	protected final Application application;

	/**
	 * Constructor.
	 *
	 * @param application
	 *            The application instance
	 */
	public DefaultPageManagerProvider(Application application)
	{
		this.application = Args.notNull(application, "application");
	}

	@Override
	public IPageManager get()
	{
		IPageStore store = newPersistentStore();

		store = newAsynchronousStore(store);

		store = newSessionStore(store);

		store = newRequestStore(store);
		
		return new PageManager(store);
	}

	/**
	 * Get the {@link ISerializer} to use for serializing of pages.
	 * 
	 * @return how to serialize pages if needed for any {@link IPageStore}
	 */
	protected ISerializer getSerializer()
	{
		return application.getFrameworkSettings().getSerializer();
	}

	/**
	 * Keep pages in the request until it is finished.
	 * 
	 * @see RequestPageStore
	 */
	protected IPageStore newRequestStore(IPageStore pageStore)
	{
		return new RequestPageStore(pageStore);
	}

	/**
	 * After requests keep pages in the session.
	 * 
	 * @see InSessionPageStore
	 */
	protected IPageStore newSessionStore(IPageStore pageStore)
	{
		return new InSessionPageStore(pageStore, getSerializer(), 1);
	}

	/**
	 * Store pages asynchronously into the persistent store.
	 * 
	 * @see AsynchronousPageStore
	 * @see StoreSettings#isAsynchronous()
	 */
	protected IPageStore newAsynchronousStore(IPageStore pageStore)
	{
		StoreSettings storeSettings = application.getStoreSettings();

		if (storeSettings.isAsynchronous())
		{
			int capacity = storeSettings.getAsynchronousQueueCapacity();
			pageStore = new AsynchronousPageStore(pageStore, capacity);
		}

		return pageStore;
	}

	/**
	 * Keep pages persistent on disk.
	 * 
	 * @see DiskPageStore
	 * @see StoreSettings#getMaxSizePerSession()
	 * @see StoreSettings#getFileStoreFolder()
	 */
	protected IPageStore newPersistentStore()
	{
		StoreSettings storeSettings = application.getStoreSettings();
		Bytes maxSizePerSession = storeSettings.getMaxSizePerSession();
		File fileStoreFolder = storeSettings.getFileStoreFolder();

		return new DiskPageStore(application.getName(), getSerializer(), fileStoreFolder, maxSizePerSession);
	}
}
