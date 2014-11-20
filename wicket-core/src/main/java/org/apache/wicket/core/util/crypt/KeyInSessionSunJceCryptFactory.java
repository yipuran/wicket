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
package org.apache.wicket.core.util.crypt;

import java.util.UUID;

import org.apache.wicket.Session;
import org.apache.wicket.util.crypt.ICrypt;
import org.apache.wicket.util.crypt.ICryptFactory;
import org.apache.wicket.util.crypt.SunJceCrypt;
import org.apache.wicket.util.lang.Args;

/**
 * Crypt factory that produces {@link SunJceCrypt} instances based on http session-specific
 * encryption key. This allows each user to have their own encryption key, hardening against CSRF
 * attacks.
 *
 * Note that the use of this crypt factory will result in an immediate creation of a http session
 *
 * @author igor.vaynberg
 */
public class KeyInSessionSunJceCryptFactory extends AbstractKeyInSessionCryptFactory<String> implements ICryptFactory
{	
	final String cryptMethod;

	/**
	 * Constructor using {@link javax.crypto.Cipher} {@value org.apache.wicket.util.crypt.SunJceCrypt#DEFAULT_CRYPT_METHOD}
	 */
	public KeyInSessionSunJceCryptFactory()
	{
		this(SunJceCrypt.DEFAULT_CRYPT_METHOD);
	}

	/**
	 * Constructor that uses a custom {@link javax.crypto.Cipher}
	 *
	 * @param cryptMethod
	 *              the name of the crypt method (cipher)
	 */
	public KeyInSessionSunJceCryptFactory(String cryptMethod)
	{
		this.cryptMethod = Args.notNull(cryptMethod, "Crypt method");
	}
	
	@Override
	protected String generateKey(Session session)
	{
		return session.getId() + "." + UUID.randomUUID().toString();
	}
	
	@Override
	protected ICrypt createCrypt(String key)
	{
		return new SunJceCrypt(cryptMethod, key);
	}
}
