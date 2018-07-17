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
package org.apache.wicket.threadtest.apps.app2;

import org.apache.wicket.Application;
import org.apache.wicket.DefaultPageManagerProvider;
import org.apache.wicket.Page;
import org.apache.wicket.markup.html.image.resource.DefaultButtonImageResource;
import org.apache.wicket.page.IPageManager;
import org.apache.wicket.page.PageManager;
import org.apache.wicket.pageStore.IPageStore;
import org.apache.wicket.pageStore.InSessionPageStore;
import org.apache.wicket.pageStore.NoopPageStore;
import org.apache.wicket.pageStore.RequestPageStore;
import org.apache.wicket.pageStore.SerializingPageStore;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.serialize.java.JavaSerializer;

/**
 * Test application
 */
public class TestApp2 extends WebApplication
{

	public static TestApp2 get()
	{
		return (TestApp2)Application.get();
	}

	/**
	 * Construct.
	 */
	public TestApp2()
	{
	}

	@Override
	public Class<? extends Page> getHomePage()
	{
		return Home.class;
	}

	@Override
	protected void init()
	{
		getSharedResources().add("cancelButton", new DefaultButtonImageResource("Cancel"));

		setPageManagerProvider(new DefaultPageManagerProvider(this)
		{
			@Override
			public IPageManager get()
			{
				JavaSerializer serializer = new JavaSerializer(getApplicationKey());
				
				IPageStore store = new InSessionPageStore(new NoopPageStore(), 100, serializer);
				store = new SerializingPageStore(store, serializer);
				store = new RequestPageStore(store);
				
				return new PageManager(store);
			}
		});
	}

}
