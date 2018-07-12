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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.wicket.MockPage;
import org.apache.wicket.mock.MockPageStore;
import org.junit.Test;

/**
 * Test for {@link RequestPageStore}
 * 
 * @author svenmeier
 */
public class RequestPageStoreTest
{

	@Test
	public void test()
	{
		MockPageStore mockStore = new MockPageStore();
		
		DummyPageContext context = new DummyPageContext();

		RequestPageStore store = new RequestPageStore(mockStore);
		
		MockPage page1 = new MockPage(2);
		MockPage page2 = new MockPage(3);
		MockPage page3 = new MockPage(4);
		
		store.addPage(context, page1);
		store.addPage(context, page2);
		store.addPage(context, page3);
		
		assertTrue("no pages delegated before detach", mockStore.getPages().isEmpty());
		
		store.detach(context);
		
		assertEquals("pages delegated on detach", 3, mockStore.getPages().size());
		
		mockStore.getPages().clear();
		
		assertNull("no page in request store", store.getPage(context, 2));
		assertNull("no page in request store", store.getPage(context, 3));
		assertNull("no page in request store", store.getPage(context, 4));
	}
}
