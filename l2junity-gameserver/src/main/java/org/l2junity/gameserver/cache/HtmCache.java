/*
 * Copyright (C) 2004-2015 L2J Unity
 * 
 * This file is part of L2J Unity.
 * 
 * L2J Unity is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J Unity is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2junity.gameserver.cache;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.l2junity.Config;
import org.l2junity.commons.util.file.filter.HTMLFilter;
import org.l2junity.gameserver.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Layane
 */
public class HtmCache
{
	private static final Logger LOGGER = LoggerFactory.getLogger(HtmCache.class);
	
	private static final HTMLFilter HTML_FILTER = new HTMLFilter();
	private static final Pattern EXTEND_PATTERN = Pattern.compile("<extend template=\"([a-zA-Z0-9-_./\\ ]*)\">(.*?)</extend>", Pattern.DOTALL);
	private static final Pattern ABSTRACT_BLOCK_PATTERN = Pattern.compile("<abstract block=\"([a-zA-Z0-9-_. ]*)\" ?/>", Pattern.DOTALL);
	private static final Pattern BLOCK_PATTERN = Pattern.compile("<block name=\"([a-zA-Z0-9-_. ]*)\">(.*?)</block>", Pattern.DOTALL);
	
	private final Map<String, String> _cache = Config.LAZY_CACHE ? new ConcurrentHashMap<>() : new HashMap<>();
	
	private int _loadedFiles;
	private long _bytesBuffLen;
	
	protected HtmCache()
	{
		reload();
	}
	
	public void reload()
	{
		reload(Config.DATAPACK_ROOT);
	}
	
	public void reload(File f)
	{
		if (!Config.LAZY_CACHE)
		{
			LOGGER.info("Html cache start...");
			parseDir(f);
			LOGGER.info("{} megabytes on {} files loaded", String.format("%.3f", getMemoryUsage()), getLoadedFiles());
		}
		else
		{
			_cache.clear();
			_loadedFiles = 0;
			_bytesBuffLen = 0;
			LOGGER.info("Running lazy cache");
		}
	}
	
	public void reloadPath(File f)
	{
		parseDir(f);
		LOGGER.info("Reloaded specified path.");
	}
	
	public double getMemoryUsage()
	{
		return ((float) _bytesBuffLen / 1048576);
	}
	
	public int getLoadedFiles()
	{
		return _loadedFiles;
	}
	
	private void parseDir(File dir)
	{
		final File[] files = dir.listFiles();
		for (File file : files)
		{
			if (!file.isDirectory())
			{
				loadFile(file);
			}
			else
			{
				parseDir(file);
			}
		}
	}
	
	public String loadFile(File file)
	{
		if (HTML_FILTER.accept(file))
		{
			try
			{
				String content = processHtml(Util.readAllLines(file, StandardCharsets.UTF_8, null));
				content = content.replaceAll("(?s)<!--.*?-->", ""); // Remove html comments
				// content = content.replaceAll("\r", "").replaceAll("\n", ""); // Remove new lines
				
				String oldContent = _cache.put(file.toURI().getPath().substring(Config.DATAPACK_ROOT.toURI().getPath().length()), content);
				if (oldContent == null)
				{
					_bytesBuffLen += content.length() * 2;
					_loadedFiles++;
				}
				else
				{
					_bytesBuffLen = (_bytesBuffLen - oldContent.length()) + (content.length() * 2);
				}
				return content;
			}
			catch (Exception e)
			{
				LOGGER.warn("Problem with htm file:", e);
			}
		}
		return null;
	}
	
	public String getHtmForce(String prefix, String path)
	{
		String content = getHtm(prefix, path);
		if (content == null)
		{
			content = "<html><body>My text is missing:<br>" + path + "</body></html>";
			LOGGER.warn("Missing HTML page: {}", path);
		}
		return content;
	}
	
	public String getHtm(String prefix, String path)
	{
		String newPath = null;
		String content;
		if ((prefix != null) && !prefix.isEmpty())
		{
			newPath = prefix + path;
			content = getHtm(newPath);
			if (content != null)
			{
				return content;
			}
		}
		
		content = getHtm(path);
		if ((content != null) && (newPath != null))
		{
			_cache.put(newPath, content);
		}
		
		return content;
	}
	
	private String getHtm(String path)
	{
		if ((path == null) || path.isEmpty())
		{
			return ""; // avoid possible NPE
		}
		
		return _cache.getOrDefault(path, Config.LAZY_CACHE ? loadFile(new File(Config.DATAPACK_ROOT, path)) : null);
	}
	
	public boolean contains(String path)
	{
		return _cache.containsKey(path);
	}
	
	/**
	 * @param path The path to the HTM
	 * @return {@code true} if the path targets a HTM or HTML file, {@code false} otherwise.
	 */
	public boolean isLoadable(String path)
	{
		return HTML_FILTER.accept(new File(Config.DATAPACK_ROOT, path));
	}
	
	private String parseTemplateName(String name)
	{
		if (!name.startsWith("data/"))
		{
			if (name.startsWith("html/"))
			{
				return "data/" + name;
			}
			else if (name.startsWith("CommunityBoard/"))
			{
				return "data/html/" + name;
			}
			else if (name.startsWith("scripts/"))
			{
				return "data/scripts/" + name;
			}
		}
		return name;
	}
	
	private String processHtml(String result)
	{
		final Matcher extendMatcher = EXTEND_PATTERN.matcher(result);
		if (extendMatcher.find())
		{
			// If extend matcher finds something, process template
			final String templateName = parseTemplateName(extendMatcher.group(1));
			
			// Generate block name -> content map
			final Map<String, String> blockMap = generateBlockMap(result);
			
			// Attempt to find the template
			String template = getHtm(templateName + "-template.htm");
			if (template != null)
			{
				// Attempt to find the abstract blocks
				final Matcher blockMatcher = ABSTRACT_BLOCK_PATTERN.matcher(template);
				while (blockMatcher.find())
				{
					final String name = blockMatcher.group(1);
					if (!blockMap.containsKey(name))
					{
						LOGGER.warn(getClass().getSimpleName() + ": Abstract block definition [" + name + "] is not implemented!");
						continue;
					}
					
					// Replace the matched content with the block.
					template = template.replace(blockMatcher.group(0), blockMap.get(name));
				}
				
				// Replace the entire extend block
				result = result.replace(extendMatcher.group(0), template);
			}
			else
			{
				LOGGER.warn(getClass().getSimpleName() + ": Missing template: " + templateName + "-template.htm !");
			}
		}
		
		return result;
	}
	
	private Map<String, String> generateBlockMap(String data)
	{
		final Map<String, String> blockMap = new LinkedHashMap<>();
		final Matcher blockMatcher = BLOCK_PATTERN.matcher(data);
		while (blockMatcher.find())
		{
			final String name = blockMatcher.group(1);
			final String content = blockMatcher.group(2);
			blockMap.put(name, content);
		}
		return blockMap;
	}
	
	public static HtmCache getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final HtmCache _instance = new HtmCache();
	}
}
