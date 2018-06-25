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
package org.apache.wicket.mock;

import java.util.HashMap;
import java.util.Map;

import org.apache.wicket.page.IManageablePage;
import org.apache.wicket.pageStore.IPageContext;
import org.apache.wicket.pageStore.IPageStore;

public class MockPageStore implements IPageStore
{
	private final Map<Integer, IManageablePage> pages = new HashMap<>();

	@Override
	public void destroy()
	{
		pages.clear();
	}

	@Override
	public IManageablePage getPage(IPageContext context, int id)
	{
		return pages.get(id);
	}

	@Override
	public void removePage(IPageContext context, final IManageablePage page) {
		if (page != null) {
			pages.remove(page.getPageId());
		}
	}

	@Override
	public void removeAllPages(IPageContext context)
	{
		pages.clear();
	}

	@Override
	public void addPage(IPageContext context, IManageablePage page)
	{
		pages.put(page.getPageId(), page);
	}
}