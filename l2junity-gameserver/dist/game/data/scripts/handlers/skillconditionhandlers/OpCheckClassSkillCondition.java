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
package handlers.skillconditionhandlers;

import java.util.List;

import org.l2junity.gameserver.enums.SkillConditionAffectType;
import org.l2junity.gameserver.model.StatsSet;
import org.l2junity.gameserver.model.WorldObject;
import org.l2junity.gameserver.model.actor.Creature;
import org.l2junity.gameserver.model.skills.ISkillCondition;
import org.l2junity.gameserver.model.skills.Skill;

/**
 * @author UnAfraid
 */
public class OpCheckClassSkillCondition implements ISkillCondition
{
	private final List<Integer> _classId;
	private final SkillConditionAffectType _affectType;
	private final boolean _isWithin;
	
	public OpCheckClassSkillCondition(StatsSet params)
	{
		_classId = params.getList("classId", Integer.class);
		_affectType = params.getEnum("affectType", SkillConditionAffectType.class);
		_isWithin = params.getBoolean("isWithin");
	}
	
	@Override
	public boolean canUse(Creature caster, Skill skill, WorldObject target)
	{
		switch (_affectType)
		{
			case CASTER:
			{
				return caster.isPlayer() && (_isWithin == (_classId.contains(target.getActingPlayer().getClassId().getId())));
			}
			case TARGET:
			{
				if ((target != null) && !target.isPlayer())
				{
					return _isWithin == (_classId.contains(target.getActingPlayer().getClassId().getId()));
				}
				break;
			}
		}
		return false;
	}
}
