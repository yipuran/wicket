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
package org.apache.wicket.pageStore;

import org.apache.wicket.page.IManageablePage;

/**
 * A store of pages
 */
public interface IPageStore
{
	/**
	 * This method must be called before any attempt to call
	 * {@link #addPage(IPageContext, IManageablePage)} asynchronously, as done by
	 * {@link AsynchronousPageStore}.
	 * 
	 * @return whether {@link #addPage(IPageContext, IManageablePage)} may be called asynchronously,
	 *         default is <code>false</code>
	 */
	default boolean canBeAsynchronous(IPageContext context)
	{
		return false;
	}

	/**
	 * Stores the page-
	 * 
	 * @param context
	 *            the context of the page
	 * @param id
	 *            the id of the page.
	 */
	void addPage(IPageContext context, IManageablePage page);

	/**
	 * Removes a page from storage.
	 * 
	 * @param context
	 *            the context of the page
	 * @param id
	 *            the id of the page.
	 */
	void removePage(IPageContext context, IManageablePage page);

	/**
	 * All pages should be removed from storage for the given context.
	 * 
	 * @param context
	 *            the context of the pages
	 */
	void removeAllPages(IPageContext context);

	/**
	 * Restores a page from storage.
	 * 
	 * @param context
	 *            the context of the page
	 * @param id
	 *            the id of the page.
	 * @return the page
	 */
	IManageablePage getPage(IPageContext context, int id);

	/**
	 * Detach from the current context.
	 * 
	 * @param context
	 *            the context of the pages
	 */
	default void detach(IPageContext context)
	{
	}

	/**
	 * Destroy the store.
	 */
	default void destroy()
	{
	}
}
