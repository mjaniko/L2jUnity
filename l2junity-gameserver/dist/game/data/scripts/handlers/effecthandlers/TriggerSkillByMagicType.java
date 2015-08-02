/*
 * Copyright (C) 2004-2015 L2J DataPack
 * 
 * This file is part of L2J DataPack.
 * 
 * L2J DataPack is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J DataPack is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package handlers.effecthandlers;

import org.l2junity.commons.util.CommonUtil;
import org.l2junity.commons.util.Rnd;
import org.l2junity.gameserver.datatables.SkillData;
import org.l2junity.gameserver.handler.ITargetTypeHandler;
import org.l2junity.gameserver.handler.TargetHandler;
import org.l2junity.gameserver.model.StatsSet;
import org.l2junity.gameserver.model.WorldObject;
import org.l2junity.gameserver.model.actor.Creature;
import org.l2junity.gameserver.model.conditions.Condition;
import org.l2junity.gameserver.model.effects.AbstractEffect;
import org.l2junity.gameserver.model.events.EventType;
import org.l2junity.gameserver.model.events.impl.character.OnCreatureSkillFinishCast;
import org.l2junity.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2junity.gameserver.model.holders.SkillHolder;
import org.l2junity.gameserver.model.skills.BuffInfo;
import org.l2junity.gameserver.model.skills.Skill;
import org.l2junity.gameserver.model.skills.targets.L2TargetType;

/**
 * Trigger skill by isMagic type.
 * @author Nik
 */
public final class TriggerSkillByMagicType extends AbstractEffect
{
	private final int[] _magicTypes;
	private final int _chance;
	private final int _skillLevelScaleTo;
	private final SkillHolder _skill;
	private final L2TargetType _targetType;
	
	/**
	 * @param attachCond
	 * @param applyCond
	 * @param set
	 * @param params
	 */
	
	public TriggerSkillByMagicType(Condition attachCond, Condition applyCond, StatsSet set, StatsSet params)
	{
		super(attachCond, applyCond, set, params);
		
		_chance = params.getInt("chance", 100);
		_magicTypes = params.getIntArray("magicTypes", ";");
		_skill = new SkillHolder(params.getInt("skillId", 0), params.getInt("skillLevel", 0));
		_skillLevelScaleTo = params.getInt("skillLevelScaleTo", 0);
		_targetType = params.getEnum("targetType", L2TargetType.class, L2TargetType.ONE);
	}
	
	public void onSkillUseEvent(OnCreatureSkillFinishCast event)
	{
		if (!CommonUtil.contains(_magicTypes, event.getSkill().getMagicType()))
		{
			return;
		}
		
		final ITargetTypeHandler targetHandler = TargetHandler.getInstance().getHandler(_targetType);
		if (targetHandler == null)
		{
			_log.warn("Handler for target type: " + _targetType + " does not exist.");
			return;
		}
		
		if ((_chance < 100) && (Rnd.get(100) > _chance))
		{
			return;
		}
		
		final Skill triggerSkill;
		if (_skillLevelScaleTo <= 0)
		{
			triggerSkill = _skill.getSkill();
		}
		else
		{
			final BuffInfo buffInfo = event.getTarget().getEffectList().getBuffInfoBySkillId(_skill.getSkillId());
			if (buffInfo != null)
			{
				triggerSkill = SkillData.getInstance().getSkill(_skill.getSkillId(), Math.min(_skillLevelScaleTo, buffInfo.getSkill().getLevel() + 1));
			}
			else
			{
				triggerSkill = _skill.getSkill();
			}
		}
		
		final WorldObject[] targets = targetHandler.getTargetList(triggerSkill, event.getCaster(), false, event.getTarget());
		
		for (WorldObject triggerTarget : targets)
		{
			if ((triggerTarget == null) || !triggerTarget.isCreature())
			{
				continue;
			}
			
			final Creature targetChar = (Creature) triggerTarget;
			if (!targetChar.isInvul())
			{
				event.getCaster().makeTriggerCast(triggerSkill, targetChar);
			}
		}
	}
	
	@Override
	public void onStart(BuffInfo info)
	{
		if ((_chance == 0) || (_skill.getSkillId() == 0) || (_skill.getSkillLvl() == 0) || (_magicTypes.length == 0))
		{
			return;
		}
		
		info.getEffected().addListener(new ConsumerEventListener(info.getEffected(), EventType.ON_CREATURE_SKILL_FINISH_CAST, (OnCreatureSkillFinishCast event) -> onSkillUseEvent(event), this));
	}
	
	@Override
	public void onExit(BuffInfo info)
	{
		info.getEffected().removeListenerIf(EventType.ON_CREATURE_SKILL_FINISH_CAST, listener -> listener.getOwner() == this);
	}
	
}