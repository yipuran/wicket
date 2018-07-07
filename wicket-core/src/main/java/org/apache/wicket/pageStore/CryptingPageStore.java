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

import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.apache.wicket.MetaDataKey;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.page.IManageablePage;
import org.apache.wicket.serialize.ISerializer;

/**
 * A store that encrypts all pages before delegating and vice versa.
 * <p>
 * All pages passing through this store are restricted to be {@link SerializedPage}s. You can
 * achieve this with
 * <ul>
 * <li>a {@link SerializingPageStore} delegating to this store and</li>
 * <li>delegating to a store that does not deserialize its pages, e.g. a {@link DiskPageStore}
 * without an {@link ISerializer}</li>.
 * </ul>
 */
public class CryptingPageStore extends DelegatingPageStore
{
	public static final String DEFAULT_CRYPT_METHOD = "PBEWithMD5AndDES";

	private final static byte[] DEFAULT_SALT = { (byte)0x15, (byte)0x8c, (byte)0xa3, (byte)0x4a,
			(byte)0x66, (byte)0x51, (byte)0x2a, (byte)0xbc };

	private final static int DEFAULT_ITERATION_COUNT = 17;

	private static final MetaDataKey<SessionData> KEY = new MetaDataKey<SessionData>()
	{
	};

	/**
	 * @param delegate
	 *            store to delegate to
	 * @param applicationName
	 *            name of application
	 */
	public CryptingPageStore(IPageStore delegate)
	{
		super(delegate);
	}

	/**
	 * Supports asynchronous {@link #addPage(IPageContext, IManageablePage)} if the delegate supports it.
	 */
	@Override
	public boolean canBeAsynchronous(IPageContext context)
	{
		context.bind();

		getSessionData(context);

		return getDelegate().canBeAsynchronous(context);
	}

	private SessionData getSessionData(IPageContext context)
	{
		SessionData data = context.getSessionData(KEY);
		if (data == null)
		{
			context.bind();

			data = context.setSessionData(KEY, new SessionData(createCipherKey(context)));
		}
		return data;
	}

	/**
	 * Create a cipher key for the given context.
	 * 
	 * @param context
	 *            context
	 * @return random UUID by default
	 */
	protected String createCipherKey(IPageContext context)
	{
		return UUID.randomUUID().toString();
	}

	/**
	 * Create a secret key.
	 * 
	 * @param cipherKey
	 *            cipher key
	 * @return secret key
	 * @throws GeneralSecurityException
	 */
	protected SecretKey createSecretKey(String cipherKey) throws GeneralSecurityException
	{
		SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(DEFAULT_CRYPT_METHOD);

		return keyFactory.generateSecret(new PBEKeySpec(cipherKey.toCharArray()));
	}

	/**
	 * Create a cipher
	 * 
	 * @param mode
	 *            mode
	 * @param secret
	 *            secret
	 * @return cipher
	 * @throws GeneralSecurityException
	 */
	protected Cipher createCipher(int mode, SecretKey secret) throws GeneralSecurityException
	{
		Cipher cipher = Cipher.getInstance(DEFAULT_CRYPT_METHOD);
		cipher.init(mode, secret, new PBEParameterSpec(DEFAULT_SALT, DEFAULT_ITERATION_COUNT));
		return cipher;
	}

	@Override
	public IManageablePage getPage(IPageContext context, int id)
	{
		IManageablePage page = super.getPage(context, id);

		if (page != null)
		{
			if (page instanceof SerializedPage == false)
			{
				throw new WicketRuntimeException("CryptingPageStore expects serialized pages");
			}
			SerializedPage serializedPage = (SerializedPage)page;

			byte[] encrypted = serializedPage.getData();
			byte[] decrypted = getSessionData(context).crypt(this, encrypted, Cipher.DECRYPT_MODE);

			page = new SerializedPage(page.getPageId(), serializedPage.getPageType(), decrypted);
		}

		return page;
	}

	@Override
	public void addPage(IPageContext context, IManageablePage page)
	{
		if (page instanceof SerializedPage == false)
		{
			throw new WicketRuntimeException("CryptingPageStore works with serialized pages only");
		}

		SerializedPage serializedPage = (SerializedPage)page;

		byte[] decrypted = serializedPage.getData();
		byte[] encrypted = getSessionData(context).crypt(this, decrypted, Cipher.ENCRYPT_MODE);

		page = new SerializedPage(page.getPageId(), serializedPage.getPageType(), encrypted);

		super.addPage(context, page);
	}

	private static class SessionData implements Serializable
	{

		private final String cipherKey;

		private transient SecretKey secretKey;

		public SessionData(String cipherKey)
		{
			this.cipherKey = cipherKey;
		}

		protected Cipher createCipher(CryptingPageStore store, int mode) throws GeneralSecurityException
		{
			SecretKey secret = getSecretKey(store);

			return store.createCipher(mode, secret);
		}

		protected synchronized SecretKey getSecretKey(CryptingPageStore store) throws GeneralSecurityException
		{
			if (secretKey == null)
			{
				secretKey = store.createSecretKey(cipherKey);
			}

			return secretKey;
		}

		public byte[] crypt(CryptingPageStore store, byte[] bytes, int mode)
		{
			try
			{
				Cipher cipher = createCipher(store, mode);

				return cipher.doFinal(bytes);
			}
			catch (GeneralSecurityException ex)
			{
				throw new WicketRuntimeException(ex);
			}
		}
	}
}