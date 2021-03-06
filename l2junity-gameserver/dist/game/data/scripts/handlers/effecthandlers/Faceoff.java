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
package handlers.effecthandlers;

import org.l2junity.gameserver.model.StatsSet;
import org.l2junity.gameserver.model.effects.AbstractEffect;
import org.l2junity.gameserver.model.effects.EffectFlag;
import org.l2junity.gameserver.model.skills.BuffInfo;

/**
 * @author Sdw
 */
public class Faceoff extends AbstractEffect
{
	public Faceoff(StatsSet params)
	{
	}
	
	@Override
	public long getEffectFlags()
	{
		return EffectFlag.FACEOFF.getMask();
	}
	
	@Override
	public boolean canStart(BuffInfo info)
	{
		return info.getEffected().isPlayer();
	}
	
	@Override
	public void onStart(BuffInfo info)
	{
		info.getEffector().getActingPlayer().setAttackerObjId(info.getEffected().getObjectId());
		info.getEffected().getActingPlayer().setAttackerObjId(info.getEffector().getObjectId());
	}
	
	@Override
	public void onExit(BuffInfo info)
	{
		info.getEffector().getActingPlayer().setAttackerObjId(0);
		info.getEffected().getActingPlayer().setAttackerObjId(0);
	}
}
