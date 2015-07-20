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
package org.l2junity.gameserver.scripting;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;

import org.l2junity.Config;
import org.l2junity.gameserver.scripting.java.JavaScriptingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caches script engines and provides functionality for executing and managing scripts.
 * @author KenM, HorridoJoho
 */
public final class ScriptEngineManager
{
	private static final Logger _log = LoggerFactory.getLogger(ScriptEngineManager.class);
	public static final Path SCRIPT_LIST_FILE = Paths.get(Config.DATAPACK_ROOT.getAbsolutePath(), "data", "scripts.cfg");
	public static final Path SCRIPT_FOLDER = Paths.get(Config.DATAPACK_ROOT.getAbsolutePath(), "data", "scripts");
	public static final Path MASTER_HANDLER_FILE = Paths.get(SCRIPT_FOLDER.toString(), "handlers", "MasterHandler.java");
	public static final Path EFFECT_MASTER_HANDLER_FILE = Paths.get(SCRIPT_FOLDER.toString(), "handlers", "EffectMasterHandler.java");
	
	private final Map<String, IExecutionContext> _extEngines = new HashMap<>();
	private IExecutionContext _currentExecutionContext = null;
	
	protected ScriptEngineManager()
	{
		final Properties props = loadProperties();
		
		// Default java engine implementation
		registerEngine(new JavaScriptingEngine(), props);
		
		// Load external script engines
		ServiceLoader.load(IScriptingEngine.class).forEach(engine -> registerEngine(engine, props));
	}
	
	private Properties loadProperties()
	{
		Properties props = null;
		try (FileInputStream fis = new FileInputStream("config/ScriptEngines.properties"))
		{
			props = new Properties();
			props.load(fis);
		}
		catch (Exception e)
		{
			props = null;
			_log.warn("Couldn't load ScriptEngines.properties: " + e.getMessage());
		}
		return props;
	}
	
	private void registerEngine(IScriptingEngine engine, Properties props)
	{
		maybeSetProperties("language." + engine.getLanguageName() + ".", props, engine);
		final IExecutionContext context = engine.createExecutionContext();
		for (String commonExtension : engine.getCommonFileExtensions())
		{
			_extEngines.put(commonExtension, context);
		}
		
		_log.info("ScriptEngine: " + engine.getEngineName() + " " + engine.getEngineVersion() + " (" + engine.getLanguageName() + " " + engine.getLanguageVersion() + ")");
	}
	
	private void maybeSetProperties(String propPrefix, Properties props, IScriptingEngine engine)
	{
		if (props == null)
		{
			return;
		}
		
		for (final Entry<Object, Object> prop : props.entrySet())
		{
			String key = (String) prop.getKey();
			String value = (String) prop.getValue();
			
			if (key.startsWith(propPrefix))
			{
				key = key.substring(propPrefix.length());
				if (value.startsWith("%") && value.endsWith("%"))
				{
					value = System.getProperty(value.substring(1, value.length() - 1));
				}
				
				engine.setProperty(key, value);
			}
		}
	}
	
	private IExecutionContext getEngineByExtension(String ext)
	{
		return _extEngines.get(ext);
	}
	
	private String getFileExtension(Path p)
	{
		final String name = p.getFileName().toString();
		final int lastDotIdx = name.lastIndexOf('.');
		if (lastDotIdx == -1)
		{
			return null;
		}
		
		final String extension = name.substring(lastDotIdx + 1);
		if (extension.isEmpty())
		{
			return null;
		}
		
		return extension;
	}
	
	private void checkExistingFile(String messagePre, Path filePath) throws Exception
	{
		if (!Files.exists(filePath))
		{
			throw new Exception(messagePre + ": " + filePath + " does not exists!");
		}
		else if (!Files.isRegularFile(filePath))
		{
			throw new Exception(messagePre + ": " + filePath + " is not a file!");
		}
	}
	
	public void executeMasterHandler() throws Exception
	{
		executeScript(MASTER_HANDLER_FILE);
	}
	
	public void executeEffectMasterHandler() throws Exception
	{
		executeScript(EFFECT_MASTER_HANDLER_FILE);
	}
	
	public void executeScriptList() throws Exception
	{
		if (Config.ALT_DEV_NO_QUESTS)
		{
			return;
		}
		
		// throws exception if not exists or not file
		checkExistingFile("ScriptList", SCRIPT_LIST_FILE);
		
		final Map<IExecutionContext, List<Path>> files = new LinkedHashMap<>();
		final Set<String> extWithoutEngine = new HashSet<>();
		
		Files.lines(SCRIPT_LIST_FILE).forEach(line ->
		{
			line = line.trim();
			if (line.isEmpty() || (line.charAt(0) == '#'))
			{
				return;
			}
			
			Path sourceFile = SCRIPT_FOLDER.resolve(line);
			try
			{
				checkExistingFile("ScriptFile", sourceFile);
			}
			catch (Exception e)
			{
				_log.warn(e.getMessage());
				return;
			}
			
			sourceFile = sourceFile.toAbsolutePath();
			final String ext = getFileExtension(sourceFile);
			if (ext == null)
			{
				_log.warn("ScriptFile: " + sourceFile + " does not have an extension to determine the script engine!");
				return;
			}
			
			final IExecutionContext engine = getEngineByExtension(ext);
			if (engine == null)
			{
				if (extWithoutEngine.add(ext))
				{
					_log.warn("ScriptEngine: No engine registered for extension " + ext + "!");
				}
				return;
			}
			
			files.computeIfAbsent(engine, k -> new LinkedList<>()).add(sourceFile);
		});
		
		for (Entry<IExecutionContext, List<Path>> entry : files.entrySet())
		{
			_currentExecutionContext = entry.getKey();
			try
			{
				Map<Path, Throwable> invokationErrors = entry.getKey().executeScripts(entry.getValue());
				for (Entry<Path, Throwable> entry2 : invokationErrors.entrySet())
				{
					_log.warn("ScriptEngine: " + entry2.getKey() + " failed execution!", entry2.getValue());
				}
			}
			finally
			{
				_currentExecutionContext = null;
			}
		}
	}
	
	public void executeScript(Path sourceFile) throws Exception
	{
		Objects.requireNonNull(sourceFile);
		
		if (!sourceFile.isAbsolute())
		{
			sourceFile = SCRIPT_FOLDER.resolve(sourceFile);
		}
		
		// throws exception if not exists or not file
		checkExistingFile("ScriptFile", sourceFile);
		
		sourceFile = sourceFile.toAbsolutePath();
		String ext = getFileExtension(sourceFile);
		Objects.requireNonNull(sourceFile, "ScriptFile: " + sourceFile + " does not have an extension to determine the script engine!");
		
		IExecutionContext engine = getEngineByExtension(ext);
		Objects.requireNonNull(engine, "ScriptEngine: No engine registered for extension " + ext + "!");
		
		_currentExecutionContext = engine;
		try
		{
			Entry<Path, Throwable> error = engine.executeScript(sourceFile);
			if (error != null)
			{
				throw new Exception("ScriptEngine: " + error.getKey() + " failed execution!", error.getValue());
			}
		}
		finally
		{
			_currentExecutionContext = null;
		}
	}
	
	public Path getCurrentLoadingScript()
	{
		return _currentExecutionContext != null ? _currentExecutionContext.getCurrentExecutingScript() : null;
	}
	
	public static ScriptEngineManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final ScriptEngineManager _instance = new ScriptEngineManager();
	}
}