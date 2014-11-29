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
package org.apache.wicket.examples;

import de.agilecoders.wicket.core.markup.html.bootstrap.behavior.BootstrapBaseBehavior;
import de.agilecoders.wicket.core.markup.html.bootstrap.html.IeEdgeMetaTag;
import de.agilecoders.wicket.core.markup.html.bootstrap.html.MetaTag;
import de.agilecoders.wicket.core.markup.html.bootstrap.html.OptimizedMobileViewportMetaTag;
import de.agilecoders.wicket.core.markup.html.references.BootlintHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.string.Strings;

/**
 * Base class for all example pages.
 * 
 * @author Jonathan Locke
 */
public class WicketExamplePage extends WebPage
{
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor
	 */
	public WicketExamplePage()
	{
		this(new PageParameters());
	}

	/**
	 * Constructor
	 * 
	 * @param pageParameters The request parameters
	 */
	public WicketExamplePage(final PageParameters pageParameters)
	{
		super(pageParameters);

		add(new OptimizedMobileViewportMetaTag("viewport"));
		add(new IeEdgeMetaTag("ie-edge"));
		add(new MetaTag("description", Model.of("description"), Model.of("Apache Wicket examples")));
		add(new MetaTag("author", Model.of("author"), Model.of("Wicket Dev team <dev@wicket.apache.org>")));


		final String packageName = getClass().getPackage().getName();
		add(new WicketExampleHeader("mainNavigation", Strings.afterLast(packageName, '.'), this));

		BootstrapBaseBehavior.addTo(this);
	}

	@Override
	protected void onInitialize()
	{
		super.onInitialize();

		explain();

		add(new Label("title", getPageTitle()));
	}

	@Override
	public void renderHead(IHeaderResponse response)
	{
		super.renderHead(response);

		if (getApplication().usesDevelopmentConfig()) {
			response.render(BootlintHeaderItem.INSTANCE);
		}
	}

	// TODO make abstract
	protected IModel<String> getPageTitle()
	{
		return Model.of("Default Title");
	}

	/**
	 * Override base method to provide an explanation
	 */
	protected void explain()
	{
	}
}
