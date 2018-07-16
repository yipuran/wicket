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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import org.apache.wicket.Session;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.page.IManageablePage;
import org.apache.wicket.pageStore.disk.PageWindowManager;
import org.apache.wicket.pageStore.disk.PageWindowManager.FileWindow;
import org.apache.wicket.serialize.ISerializer;
import org.apache.wicket.util.file.Files;
import org.apache.wicket.util.io.IOUtils;
import org.apache.wicket.util.lang.Args;
import org.apache.wicket.util.lang.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A storage of pages on disk.
 */
public class DiskPageStore implements IPersistentPageStore
{
	private static final Logger log = LoggerFactory.getLogger(DiskPageStore.class);

	private static final String KEY = "wicket:DiskPageStore";

	private static final String INDEX_FILE_NAME = "DiskPageStoreIndex";

	/**
	 * A cache that holds all page stores.
	 */
	private static final ConcurrentMap<String, DiskPageStore> DISK_STORES = new ConcurrentHashMap<>();

	private final String applicationName;

	private final ISerializer serializer;

	private final Bytes maxSizePerSession;

	private final File fileStoreFolder;

	private final ConcurrentMap<String, DiskData> diskDatas;

	/**
	 * Create a store that supports {@link SerializedPage}s only.
	 * 
	 * @param applicationName
	 *            name of application
	 * @param fileStoreFolder
	 *            folder to store to
	 * @param maxSizePerSession
	 *            maximum size per session
	 * 
	 * @see SerializingPageStore
	 */
	public DiskPageStore(String applicationName, File fileStoreFolder, Bytes maxSizePerSession)
	{
		this(applicationName, fileStoreFolder, maxSizePerSession, null);
	}

	/**
	 * Create a store to disk.
	 * 
	 * @param applicationName
	 *            name of application
	 * @param fileStoreFolder
	 *            folder to store to
	 * @param maxSizePerSession
	 *            maximum size per session
	 * @param serializer
	 *            for serialization of pages
	 */
	public DiskPageStore(String applicationName, File fileStoreFolder, Bytes maxSizePerSession,
		ISerializer serializer)
	{
		this.applicationName = Args.notNull(applicationName, "applicationName");
		this.maxSizePerSession = Args.notNull(maxSizePerSession, "maxSizePerSession");
		this.fileStoreFolder = Args.notNull(fileStoreFolder, "fileStoreFolder");
		this.serializer = serializer; // optional

		this.diskDatas = new ConcurrentHashMap<>();

		try
		{
			if (this.fileStoreFolder.exists() || this.fileStoreFolder.mkdirs())
			{
				loadIndex();
			}
			else
			{
				log.warn("Cannot create file store folder for some reason.");
			}
		}
		catch (SecurityException e)
		{
			throw new WicketRuntimeException(
				"SecurityException occurred while creating DiskPageStore. Consider using a non-disk based IPageStore implementation. "
					+ "See org.apache.wicket.Application.setPageManagerProvider(IPageManagerProvider)",
				e);
		}

		if (DISK_STORES.containsKey(applicationName))
		{
			throw new IllegalStateException(
				"Store for application with key '" + applicationName + "' already exists.");
		}
		DISK_STORES.put(applicationName, this);
	}

	@Override
	public void destroy()
	{
		log.debug("Destroying...");
		saveIndex();

		DISK_STORES.remove(applicationName);

		log.debug("Destroyed.");
	}

	@Override
	public boolean canBeAsynchronous(IPageContext context)
	{
		// session attribute must be added here *before* any asynchronous calls
		// when session is no longer available
		getSessionAttribute(context, true);

		return true;
	}

	@Override
	public IManageablePage getPage(IPageContext context, int id)
	{
		IManageablePage page = null;

		DiskData diskData = getDiskData(context, false);
		if (diskData != null)
		{
			byte[] data = diskData.loadPage(id);
			if (data != null)
			{
				if (serializer == null)
				{
					page = new SerializedPage(id, "unknown", data);
				}
				else
				{
					page = (IManageablePage)serializer.deserialize(data);
				}
			}
		}

		if (log.isDebugEnabled())
		{
			log.debug("Returning page with id '{}' in session with id '{}'",
				page != null ? "" : "(null)", id, context.getSessionId());
		}

		return page;
	}

	@Override
	public void removePage(IPageContext context, IManageablePage page)
	{
		DiskData diskData = getDiskData(context, false);
		if (diskData != null)
		{
			if (log.isDebugEnabled())
			{
				log.debug("Removing page with id '{}' in session with id '{}'", page.getPageId(),
					context.getSessionId());
			}
			diskData.removeData(page.getPageId());
		}
	}

	@Override
	public void removeAllPages(IPageContext context)
	{
		DiskData diskData = getDiskData(context, false);
		if (diskData != null)
		{
			removeDiskData(diskData);
		}
	}

	protected void removeDiskData(DiskData diskData)
	{
		synchronized (diskDatas)
		{
			diskDatas.remove(diskData.sessionIdentifier);
			diskData.unbind();
		}
	}

	/**
	 * Supports {@link SerializedPage}s too - for this to work the delegating {@link IPageStore}
	 * must use the same {@link ISerializer} as this one.
	 */
	@Override
	public void addPage(IPageContext context, IManageablePage page)
	{
		DiskData diskData = getDiskData(context, true);
		if (diskData != null)
		{
			log.debug("Storing data for page with id '{}' in session with id '{}'",
				page.getPageId(), context.getSessionId());

			byte[] data;
			String type;
			if (page instanceof SerializedPage)
			{
				data = ((SerializedPage)page).getData();
				type = ((SerializedPage)page).getPageType();
			}
			else
			{
				if (serializer == null)
				{
					throw new WicketRuntimeException(
						"DiskPageStore not configured for serialization");
				}
				data = serializer.serialize(page);
				type = page.getClass().getName();
			}

			diskData.savePage(page.getPageId(), type, data);
		}
	}

	/**
	 * 
	 * @param context
	 * @param create
	 * @return the session entry
	 */
	protected DiskData getDiskData(final IPageContext context, final boolean create)
	{
		SessionAttribute attribute = getSessionAttribute(context, create);

		if (!create && attribute == null)
		{
			return null;
		}

		return getDiskData(attribute.identifier, create);
	}

	protected DiskData getDiskData(String sessionIdentifier, boolean create)
	{
		if (!create)
		{
			return diskDatas.get(sessionIdentifier);
		}

		DiskData data = new DiskData(this, sessionIdentifier);
		DiskData existing = diskDatas.putIfAbsent(sessionIdentifier, data);
		return existing != null ? existing : data;
	}

	protected SessionAttribute getSessionAttribute(IPageContext context, boolean create)
	{
		context.bind();

		SessionAttribute attribute = context.getSessionAttribute(KEY);
		if (attribute == null && create)
		{
			attribute = new SessionAttribute(applicationName, context.getSessionId());
			context.setSessionAttribute(KEY, attribute);
		}
		return attribute;
	}

	/**
	 * Load the index
	 */
	@SuppressWarnings("unchecked")
	private void loadIndex()
	{
		File storeFolder = getStoreFolder();

		File index = new File(storeFolder, INDEX_FILE_NAME);
		if (index.exists() && index.length() > 0)
		{
			try
			{
				InputStream stream = new FileInputStream(index);
				ObjectInputStream ois = new ObjectInputStream(stream);
				try
				{
					diskDatas.clear();

					for (DiskData diskData : (List<DiskData>)ois.readObject())
					{
						diskData.pageStore = this;
						diskDatas.put(diskData.sessionIdentifier, diskData);
					}
				}
				finally
				{
					stream.close();
					ois.close();
				}
			}
			catch (Exception e)
			{
				log.error("Couldn't load DiskPageStore index from file " + index + ".", e);
			}
		}
		Files.remove(index);
	}

	/**
	 * 
	 */
	private void saveIndex()
	{
		File storeFolder = getStoreFolder();
		if (storeFolder.exists())
		{
			File index = new File(storeFolder, INDEX_FILE_NAME);
			Files.remove(index);
			try
			{
				OutputStream stream = new FileOutputStream(index);
				ObjectOutputStream oos = new ObjectOutputStream(stream);
				try
				{
					List<DiskData> list = new ArrayList<>(diskDatas.size());
					for (DiskData diskData : diskDatas.values())
					{
						if (diskData.sessionIdentifier != null)
						{
							list.add(diskData);
						}
					}
					oos.writeObject(list);
				}
				finally
				{
					stream.close();
					oos.close();
				}
			}
			catch (Exception e)
			{
				log.error("Couldn't write DiskPageStore index to file " + index + ".", e);
			}
		}
	}

	@Override
	public Set<String> getContextIdentifiers()
	{
		return Collections.unmodifiableSet(diskDatas.keySet());
	}

	/**
	 * 
	 * @param session
	 *            key
	 * @return a list of the last N page windows
	 */
	@Override
	public List<IPersistedPage> getPersistentPages(String sessionIdentifier)
	{
		List<IPersistedPage> pages = new ArrayList<>();

		DiskData diskData = getDiskData(sessionIdentifier, false);
		if (diskData != null)
		{
			PageWindowManager windowManager = diskData.getManager();

			pages.addAll(windowManager.getFileWindows());
		}
		return pages;
	}

	@Override
	public String getContextIdentifier(IPageContext context)
	{
		SessionAttribute sessionAttribute = getSessionAttribute(context, true);

		return sessionAttribute.identifier;
	}

	@Override
	public Bytes getTotalSize()
	{
		long size = 0;

		synchronized (diskDatas)
		{
			for (DiskData diskData : diskDatas.values())
			{
				size = size + diskData.size();
			}
		}

		return Bytes.bytes(size);
	}

	/**
	 * Data held on disk.
	 */
	protected static class DiskData implements Serializable
	{
		private static final long serialVersionUID = 1L;

		private transient DiskPageStore pageStore;

		private transient String fileName;

		private String sessionIdentifier;

		private PageWindowManager manager;

		protected DiskData(DiskPageStore pageStore, String sessionIdentifier)
		{
			this.pageStore = pageStore;

			this.sessionIdentifier = sessionIdentifier;
		}

		public long size()
		{
			return manager.getTotalSize();
		}

		public PageWindowManager getManager()
		{
			if (manager == null)
			{
				manager = new PageWindowManager(pageStore.maxSizePerSession.bytes());
			}
			return manager;
		}

		private String getFileName()
		{
			if (fileName == null)
			{
				fileName = pageStore.getSessionFileName(sessionIdentifier, true);
			}
			return fileName;
		}

		/**
		 * @return session id
		 */
		public String getKey()
		{
			return sessionIdentifier;
		}

		/**
		 * Saves the serialized page to appropriate file.
		 * 
		 * @param pageId
		 * @param pageType
		 * @param data
		 */
		public synchronized void savePage(int pageId, String pageType, byte data[])
		{
			if (sessionIdentifier == null)
			{
				return;
			}

			// only save page that has some data
			if (data != null)
			{
				// allocate window for page
				FileWindow window = getManager().createPageWindow(pageId, pageType, data.length);

				FileChannel channel = getFileChannel(true);
				if (channel != null)
				{
					try
					{
						// write the content
						channel.write(ByteBuffer.wrap(data), window.getFilePartOffset());
					}
					catch (IOException e)
					{
						log.error("Error writing to a channel " + channel, e);
					}
					finally
					{
						IOUtils.closeQuietly(channel);
					}
				}
				else
				{
					log.warn(
						"Cannot save page with id '{}' because the data file cannot be opened.",
						pageId);
				}
			}
		}

		/**
		 * Removes the page from pagemap file.
		 * 
		 * @param pageId
		 */
		public synchronized void removeData(int pageId)
		{
			if (sessionIdentifier == null)
			{
				return;
			}

			getManager().removePage(pageId);
		}

		/**
		 * Loads the part of pagemap file specified by the given PageWindow.
		 * 
		 * @param window
		 * @return serialized page data
		 */
		public byte[] loadData(FileWindow window)
		{
			byte[] result = null;
			FileChannel channel = getFileChannel(false);
			if (channel != null)
			{
				ByteBuffer buffer = ByteBuffer.allocate(window.getFilePartSize());
				try
				{
					channel.read(buffer, window.getFilePartOffset());
					if (buffer.hasArray())
					{
						result = buffer.array();
					}
				}
				catch (IOException e)
				{
					log.error("Error reading from file channel " + channel, e);
				}
				finally
				{
					IOUtils.closeQuietly(channel);
				}
			}
			return result;
		}

		private FileChannel getFileChannel(boolean create)
		{
			FileChannel channel = null;
			File file = new File(getFileName());
			if (create || file.exists())
			{
				String mode = create ? "rw" : "r";
				try
				{
					RandomAccessFile randomAccessFile = new RandomAccessFile(file, mode);
					channel = randomAccessFile.getChannel();
				}
				catch (FileNotFoundException fnfx)
				{
					// can happen if the file is locked. WICKET-4176
					log.error(fnfx.getMessage(), fnfx);
				}
			}
			return channel;
		}

		/**
		 * Loads the specified page data.
		 * 
		 * @param id
		 * @return page data or null if the page is no longer in pagemap file
		 */
		public synchronized byte[] loadPage(int id)
		{
			if (sessionIdentifier == null)
			{
				return null;
			}

			FileWindow window = getManager().getPageWindow(id);
			if (window == null)
			{
				return null;
			}

			return loadData(window);
		}

		/**
		 * Deletes all files for this session.
		 */
		public synchronized void unbind()
		{
			File sessionFolder = pageStore.getSessionFolder(sessionIdentifier, false);
			if (sessionFolder.exists())
			{
				Files.removeFolder(sessionFolder);
				cleanup(sessionFolder);
			}

			sessionIdentifier = null;
		}

		/**
		 * deletes the sessionFolder's parent and grandparent, if (and only if) they are empty.
		 *
		 * @see #createPathFrom(String sessionId)
		 * @param sessionFolder
		 *            must not be null
		 */
		private void cleanup(final File sessionFolder)
		{
			File high = sessionFolder.getParentFile();
			if (high != null && high.list().length == 0)
			{
				if (Files.removeFolder(high))
				{
					File low = high.getParentFile();
					if (low != null && low.list().length == 0)
					{
						Files.removeFolder(low);
					}
				}
			}
		}
	}

	/**
	 * Returns the file name for specified session. If the session folder (folder that contains the
	 * file) does not exist and createSessionFolder is true, the folder will be created.
	 * 
	 * @param sessionIdentifier
	 * @param createSessionFolder
	 * @return file name for pagemap
	 */
	private String getSessionFileName(String sessionIdentifier, boolean createSessionFolder)
	{
		File sessionFolder = getSessionFolder(sessionIdentifier, createSessionFolder);
		return new File(sessionFolder, "data").getAbsolutePath();
	}

	/**
	 * This folder contains sub-folders named as the session id for which they hold the data.
	 * 
	 * @return the folder where the pages are stored
	 */
	protected File getStoreFolder()
	{
		return new File(fileStoreFolder, applicationName + "-filestore");
	}

	/**
	 * Returns the folder for the specified sessions. If the folder doesn't exist and the create
	 * flag is set, the folder will be created.
	 * 
	 * @param sessionIdentifier
	 * @param create
	 * @return folder used to store session data
	 */
	protected File getSessionFolder(String sessionIdentifier, final boolean create)
	{
		File storeFolder = getStoreFolder();

		sessionIdentifier = sessionIdentifier.replace('*', '_');
		sessionIdentifier = sessionIdentifier.replace('/', '_');
		sessionIdentifier = sessionIdentifier.replace(':', '_');

		sessionIdentifier = createPathFrom(sessionIdentifier);

		File sessionFolder = new File(storeFolder, sessionIdentifier);
		if (create && sessionFolder.exists() == false)
		{
			Files.mkdirs(sessionFolder);
		}
		return sessionFolder;
	}

	/**
	 * creates a three-level path from the sessionId in the format 0000/0000/<sessionId>. The two
	 * prefixing directories are created from the sessionId's hashcode and thus, should be well
	 * distributed.
	 *
	 * This is used to avoid problems with Filesystems allowing no more than 32k entries in a
	 * directory.
	 *
	 * Note that the prefix paths are created from Integers and not guaranteed to be four chars
	 * long.
	 *
	 * @param sessionId
	 *            must not be null
	 * @return path in the form 0000/0000/sessionId
	 */
	private String createPathFrom(final String sessionId)
	{
		int sessionIdHashCode = sessionId.hashCode();
		if (sessionIdHashCode == Integer.MIN_VALUE)
		{
			// Math.abs(MIN_VALUE) == MIN_VALUE, so avoid it
			sessionIdHashCode += 1;
		}
		int hash = Math.abs(sessionIdHashCode);
		String low = String.valueOf(hash % 9973);
		String high = String.valueOf((hash / 9973) % 9973);
		StringBuilder bs = new StringBuilder(sessionId.length() + 10);
		bs.append(low);
		bs.append(File.separator);
		bs.append(high);
		bs.append(File.separator);
		bs.append(sessionId);

		return bs.toString();
	}

	/**
	 * Attribute held in session.
	 */
	static class SessionAttribute implements Serializable, HttpSessionBindingListener
	{

		private final String applicationName;

		/**
		 * The identifier of the session, must not be equal to {@link Session#getId()}, e.g. when
		 * the container changes the id after authorization.
		 */
		public final String identifier;

		public SessionAttribute(String applicationName, String sessionIdentifier)
		{
			this.applicationName = Args.notNull(applicationName, "applicationName");
			this.identifier = Args.notNull(sessionIdentifier, "sessionIdentifier");
		}


		@Override
		public void valueBound(HttpSessionBindingEvent event)
		{
		}

		@Override
		public void valueUnbound(HttpSessionBindingEvent event)
		{
			DiskPageStore store = DISK_STORES.get(applicationName);
			if (store == null)
			{
				log.warn(
					"Cannot remove data '{}' because disk store for application '{}' is no longer present.",
					identifier, applicationName);
			}
			else
			{
				DiskData diskData = store.getDiskData(identifier, false);
				if (diskData != null)
				{
					store.removeDiskData(diskData);
				}
			}
		}
	}
}