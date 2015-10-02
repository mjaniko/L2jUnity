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
package org.l2junity.gameserver.model.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.l2junity.gameserver.GameTimeController;
import org.l2junity.gameserver.ThreadPoolManager;
import org.l2junity.gameserver.ai.CtrlEvent;
import org.l2junity.gameserver.ai.CtrlIntention;
import org.l2junity.gameserver.data.xml.impl.ActionData;
import org.l2junity.gameserver.datatables.ItemTable;
import org.l2junity.gameserver.enums.ItemSkillType;
import org.l2junity.gameserver.enums.ShotType;
import org.l2junity.gameserver.instancemanager.ZoneManager;
import org.l2junity.gameserver.model.Location;
import org.l2junity.gameserver.model.WorldObject;
import org.l2junity.gameserver.model.actor.Attackable;
import org.l2junity.gameserver.model.actor.Creature;
import org.l2junity.gameserver.model.actor.Summon;
import org.l2junity.gameserver.model.actor.instance.PlayerInstance;
import org.l2junity.gameserver.model.actor.tasks.character.FlyToLocationTask;
import org.l2junity.gameserver.model.actor.tasks.character.QueuedMagicUseTask;
import org.l2junity.gameserver.model.effects.L2EffectType;
import org.l2junity.gameserver.model.events.EventDispatcher;
import org.l2junity.gameserver.model.events.impl.character.OnCreatureSkillFinishCast;
import org.l2junity.gameserver.model.events.impl.character.OnCreatureSkillUse;
import org.l2junity.gameserver.model.events.returns.TerminateReturn;
import org.l2junity.gameserver.model.holders.SkillHolder;
import org.l2junity.gameserver.model.holders.SkillUseHolder;
import org.l2junity.gameserver.model.items.L2Item;
import org.l2junity.gameserver.model.items.Weapon;
import org.l2junity.gameserver.model.items.instance.ItemInstance;
import org.l2junity.gameserver.model.skills.targets.L2TargetType;
import org.l2junity.gameserver.model.stats.Formulas;
import org.l2junity.gameserver.model.stats.Stats;
import org.l2junity.gameserver.model.zone.ZoneId;
import org.l2junity.gameserver.model.zone.ZoneRegion;
import org.l2junity.gameserver.network.client.send.ActionFailed;
import org.l2junity.gameserver.network.client.send.ExRotation;
import org.l2junity.gameserver.network.client.send.MagicSkillCanceld;
import org.l2junity.gameserver.network.client.send.MagicSkillLaunched;
import org.l2junity.gameserver.network.client.send.MagicSkillUse;
import org.l2junity.gameserver.network.client.send.SetupGauge;
import org.l2junity.gameserver.network.client.send.StatusUpdate;
import org.l2junity.gameserver.network.client.send.SystemMessage;
import org.l2junity.gameserver.network.client.send.string.SystemMessageId;
import org.l2junity.gameserver.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skill Caster implementation.
 * @author Nik
 */
public class SkillCaster implements Runnable
{
	private static final Logger _log = LoggerFactory.getLogger(SkillCaster.class);
	
	private final Creature _caster;
	private final SkillCastingType _castingType;
	
	private Creature _target; // Main target of the skill casting, when casting finished, any AOE effects will be applied.
	private Skill _skill;
	private boolean _ctrlPressed;
	private boolean _shiftPressed;
	
	private int _castTime;
	private int _reuseDelay;
	private int _castInterruptTime;
	private boolean _skillMastery;
	
	private volatile ScheduledFuture<?> _task = null;
	private final AtomicBoolean _isCasting;
	
	public SkillCaster(Creature caster, SkillCastingType castingType)
	{
		Objects.requireNonNull(caster);
		Objects.requireNonNull(castingType);
		
		_caster = caster;
		_castingType = castingType;
		_isCasting = new AtomicBoolean();
	}
	
	public boolean prepareCasting(WorldObject target, Skill skill, boolean ctrlPressed, boolean shiftPressed)
	{
		Objects.requireNonNull(skill);
		
		if (!_isCasting.compareAndSet(false, true))
		{
			_log.warn("Character: {} is attempting to cast {} on {} but he is already casting {} on {}!", _caster, skill, target, _skill, _target);
			return false;
		}
		
		// Casting failed due conditions... stop casting.
		if (!checkDoCastConditions(_caster, skill))
		{
			_isCasting.set(false);
			return false;
		}
		
		// Check if target is valid.
		target = checkCastingTarget(_caster, target, skill);
		if (target == null)
		{
			_isCasting.set(false);
			return false;
		}
		
		// TODO: Support for item target.
		if (!target.isCreature())
		{
			return false;
		}
		
		// TODO: OnCreatureSkillUse should not have targets list... that one is for OnCreatureSkillFinishCast
		final TerminateReturn term = EventDispatcher.getInstance().notifyEvent(new OnCreatureSkillUse(_caster, skill, skill.isSimultaneousCast(), (Creature) target, new WorldObject[0]), _caster, TerminateReturn.class);
		if ((term != null) && term.terminate())
		{
			_isCasting.set(false);
			return false;
		}
		
		// TODO: Unhardcode using event listeners!
		if (skill.hasEffectType(L2EffectType.RESURRECTION))
		{
			if (_caster.isResurrectionBlocked() || ((Creature) target).isResurrectionBlocked())
			{
				_caster.sendPacket(SystemMessageId.REJECT_RESURRECTION); // Reject resurrection
				target.sendPacket(SystemMessageId.REJECT_RESURRECTION); // Reject resurrection
				
				_isCasting.set(false);
				return false;
			}
		}
		
		// Get ready the casting parameters.
		_target = (Creature) target;
		_skill = skill;
		_ctrlPressed = ctrlPressed;
		_shiftPressed = shiftPressed;
		_castTime = getCastTime(_caster, _skill);
		_reuseDelay = getReuseTime(_caster, _skill);
		_skillMastery = Formulas.calcSkillMastery(_caster, _skill);
		_castInterruptTime = -2 + GameTimeController.getInstance().getGameTicks() + (_castTime / GameTimeController.MILLIS_IN_TICK);
		
		return true;
	}
	
	public void startCasting()
	{
		if (!isCasting())
		{
			_log.warn("Character: {} is starting a cast, but he is not prepared to cast!", _caster);
			return;
		}
		
		// Disable the skill during the re-use delay and create a task EnableSkill with Medium priority to enable it at the end of the re-use delay
		if (_reuseDelay > 10)
		{
			if (_skillMastery)
			{
				_reuseDelay = 100;
				_caster.sendPacket(SystemMessageId.A_SKILL_IS_READY_TO_BE_USED_AGAIN);
			}
			
			if (_reuseDelay > 30000)
			{
				_caster.addTimeStamp(_skill, _reuseDelay);
			}
			else
			{
				_caster.disableSkill(_skill, _reuseDelay);
			}
		}
		
		// Consume the required items.
		if (_skill.getItemConsumeId() > 0)
		{
			if (!_caster.destroyItemByItemId("Consume", _skill.getItemConsumeId(), _skill.getItemConsumeCount(), null, true))
			{
				_caster.sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT2);
				stopCasting(true);
				return;
			}
		}
		
		// Reduce talisman mana on skill use
		if ((_skill.getReferenceItemId() > 0) && (ItemTable.getInstance().getTemplate(_skill.getReferenceItemId()).getBodyPart() == L2Item.SLOT_DECO))
		{
			ItemInstance talisman = _caster.getInventory().getItems(i -> i.getId() == _skill.getReferenceItemId(), ItemInstance::isEquipped).stream().findAny().orElse(null);
			if (talisman != null)
			{
				talisman.decreaseMana(false, talisman.useSkillDisTime());
			}
		}
		
		// Consume skill initial MP needed for cast.
		int initmpcons = _caster.getStat().getMpInitialConsume(_skill);
		if (initmpcons > 0)
		{
			_caster.getStatus().reduceMp(initmpcons);
			StatusUpdate su = new StatusUpdate(_caster);
			su.addAttribute(StatusUpdate.CUR_MP, (int) _caster.getCurrentMp());
			_caster.sendPacket(su);
		}
		
		// Face the target
		if (_target != _caster)
		{
			_caster.setHeading(Util.calculateHeadingFrom(_caster, _target));
			_caster.broadcastPacket(new ExRotation(_caster.getObjectId(), _caster.getHeading()));
		}
		
		// Send a Server->Client packet MagicSkillUser with target, displayId, level, skillTime, reuseDelay
		final int actionId = _caster.isSummon() ? ActionData.getInstance().getSkillActionId(_skill.getId()) : -1;
		_caster.broadcastPacket(new MagicSkillUse(_caster, _target, _skill.getDisplayId(), _skill.getDisplayLevel(), _castTime, _reuseDelay, _skill.getReuseDelayGroup(), actionId, _castingType));
		
		// Send a system message to the player.
		if (_caster.isPlayer() && !_skill.isAbnormalInstant())
		{
			if (_skill.getId() == 2046) // Wolf Collar
			{
				_caster.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SUMMONING_YOUR_PET));
			}
			else
			{
				_caster.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_USE_S1).addSkillName(_skill));
			}
		}
		
		// Trigger any skill cast start effects.
		if (_skill.hasEffects(EffectScope.START))
		{
			_skill.applyEffectScope(EffectScope.START, new BuffInfo(_caster, _target, _skill, false), true, false);
		}
		
		// Before start AI Cast Broadcast Fly Effect is Need
		if (_skill.getFlyType() != null)
		{
			ThreadPoolManager.getInstance().scheduleEffect(new FlyToLocationTask(_caster, _target, _skill), 50);
		}
		
		// launch the magic in skillTime milliseconds
		if (_castTime > 0)
		{
			_caster.sendPacket(new SetupGauge(_caster.getObjectId(), SetupGauge.BLUE, _castTime));
			if (_skill.isChanneling() && (_skill.getChannelingSkillId() > 0))
			{
				_caster.getSkillChannelizer().startChanneling(_skill);
			}
		}
		
		// Casting action is starting...
		_caster.stopEffectsOnAction();
		
		// Start casting. Removed: For client animation reasons (party buffs especially) 400 ms before!
		_task = ThreadPoolManager.getInstance().scheduleGeneral(this, _castTime);
	}
	
	@Override
	public void run()
	{
		if (!isCasting())
		{
			_log.warn("Character: {} is casting, but casting is false", _caster);
			return;
		}
		
		Creature target = _target;
		Skill skill = _skill;
		
		try
		{
			Creature[] targets = skill.getTargetList(_caster, false, target);
			
			if (targets.length == 0)
			{
				switch (skill.getTargetType())
				{
					// only AURA-type skills can be cast without target
					case AURA:
					case FRONT_AURA:
					case BEHIND_AURA:
					case AURA_CORPSE_MOB:
						break;
					default:
						stopCasting(true);
						return;
				}
			}
			
			// Escaping from under skill's radius and peace zone check. First version, not perfect in AoE skills.
			int escapeRange = 0;
			if (skill.getEffectRange() > escapeRange)
			{
				escapeRange = skill.getEffectRange();
			}
			else if ((skill.getCastRange() < 0) && (skill.getAffectRange() > 80))
			{
				escapeRange = skill.getAffectRange();
			}
			
			if ((targets.length > 0) && (escapeRange > 0))
			{
				int skiprange = 0;
				int skippeace = 0;
				List<Creature> targetList = new ArrayList<>(targets.length);
				for (Creature aoeTarget : targets)
				{
					int collisionSum = _caster.getTemplate().getCollisionRadius() + aoeTarget.getTemplate().getCollisionRadius();
					if (!_caster.isInsideRadius(aoeTarget.getX(), aoeTarget.getY(), aoeTarget.getZ(), escapeRange + collisionSum, true, false))
					{
						skiprange++;
						continue;
					}
					
					if (skill.isBad())
					{
						if (_caster.isPlayer())
						{
							if (aoeTarget.isInsidePeaceZone(_caster.getActingPlayer()))
							{
								skippeace++;
								continue;
							}
						}
						else
						{
							if (aoeTarget.isInsidePeaceZone(_caster, aoeTarget))
							{
								skippeace++;
								continue;
							}
						}
					}
					targetList.add(aoeTarget);
				}
				if (targetList.isEmpty())
				{
					if (_caster.isPlayer())
					{
						if (skiprange > 0)
						{
							_caster.sendPacket(SystemMessageId.THE_DISTANCE_IS_TOO_FAR_AND_SO_THE_CASTING_HAS_BEEN_STOPPED);
						}
						else if (skippeace > 0)
						{
							_caster.sendPacket(SystemMessageId.A_MALICIOUS_SKILL_CANNOT_BE_USED_IN_A_PEACE_ZONE);
						}
					}
					stopCasting(true);
					return;
				}
			}
			
			if (!skill.isToggle())
			{
				_caster.broadcastPacket(new MagicSkillLaunched(_caster, skill.getDisplayId(), skill.getDisplayLevel(), _castingType, targets));
			}
			
			// Go through targets table
			for (WorldObject tgt : targets)
			{
				if (tgt.isPlayable())
				{
					if (_caster.isPlayer() && tgt.isSummon())
					{
						((Summon) tgt).updateAndBroadcastStatus(1);
					}
				}
				else if (_caster.isPlayable() && tgt.isAttackable())
				{
					Creature tgtCreature = (Creature) tgt;
					if (skill.getEffectPoint() > 0)
					{
						((Attackable) tgtCreature).reduceHate(_caster, skill.getEffectPoint());
					}
					else if (skill.getEffectPoint() < 0)
					{
						((Attackable) tgtCreature).addDamageHate(_caster, 0, -skill.getEffectPoint());
					}
				}
			}
			
			_caster.rechargeShots(skill.useSoulShot(), skill.useSpiritShot(), false);
			
			final StatusUpdate su = new StatusUpdate(_caster);
			
			// Consume MP of the L2Character and Send the Server->Client packet StatusUpdate with current HP and MP to all other L2PcInstance to inform
			double mpConsume = _caster.getStat().getMpConsume(skill);
			
			if (mpConsume > 0)
			{
				if (mpConsume > _caster.getCurrentMp())
				{
					_caster.sendPacket(SystemMessageId.NOT_ENOUGH_MP);
					stopCasting(true);
					return;
				}
				
				_caster.getStatus().reduceMp(mpConsume);
				su.addAttribute(StatusUpdate.CUR_MP, (int) _caster.getCurrentMp());
				_caster.sendPacket(su);
			}
			
			// Consume HP if necessary and Send the Server->Client packet StatusUpdate with current HP and MP to all other L2PcInstance to inform
			if (skill.getHpConsume() > 0)
			{
				double consumeHp = skill.getHpConsume();
				
				if (consumeHp >= _caster.getCurrentHp())
				{
					_caster.sendPacket(SystemMessageId.NOT_ENOUGH_HP);
					stopCasting(true);
					return;
				}
				
				_caster.getStatus().reduceHp(consumeHp, _caster, true);
				
				su.addAttribute(StatusUpdate.CUR_HP, (int) _caster.getCurrentHp());
				_caster.sendPacket(su);
			}
			
			if (_caster.isPlayer())
			{
				// Consume Souls if necessary
				if (skill.getMaxSoulConsumeCount() > 0)
				{
					if (!_caster.getActingPlayer().decreaseSouls(skill.getMaxSoulConsumeCount(), skill))
					{
						stopCasting(true);
						return;
					}
				}
			}
			
			// Launch the magic skill in order to calculate its effects
			_caster.callSkill(skill, targets);
			
			EventDispatcher.getInstance().notifyEvent(new OnCreatureSkillFinishCast(_caster, skill, skill.isSimultaneousCast(), target, targets), _caster);
			
			// Notify DP Scripts
			_caster.notifyQuestEventSkillFinished(skill, target);
			
			// On each repeat recharge shots before cast.
			_caster.rechargeShots(skill.useSoulShot(), skill.useSpiritShot(), false);
			
			stopCasting(false);
		}
		catch (Exception e)
		{
			_log.warn("Error while casting skill: " + skill + " caster: " + _caster + " target: " + target, e);
		}
	}
	
	public void stopCasting(boolean aborted)
	{
		// Verify for same status.
		if (!isCasting())
		{
			_log.warn("Character: " + _caster + " is attempting to stop casting skill but he is not casting!");
			return;
		}
		
		// Cancel the task and unset it.
		_task.cancel(false);
		_task = null;
		
		if (!isSimultaneousType())
		{
			// Attack target after skill use
			if ((_skill.nextActionIsAttack()) && (_caster.getTarget() instanceof Creature) && (_caster.getTarget() != _caster) && (_target != null) && (_caster.getTarget() == _target) && _target.canBeAttacked())
			{
				if ((_caster.getAI().getNextIntention() == null) || (_caster.getAI().getNextIntention().getCtrlIntention() != CtrlIntention.AI_INTENTION_MOVE_TO))
				{
					_caster.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, _target);
				}
			}
			
			if (_skill.isBad() && (_skill.getTargetType() != L2TargetType.UNLOCKABLE))
			{
				_caster.getAI().clientStartAutoAttack();
			}
			
			// If character is a player, then wipe their current cast state and check if a skill is queued.
			// If there is a queued skill, launch it and wipe the queue.
			if (_caster.isPlayer())
			{
				PlayerInstance currPlayer = _caster.getActingPlayer();
				SkillUseHolder queuedSkill = currPlayer.getQueuedSkill();
				currPlayer.setCurrentSkill(null, false, false);
				
				if (queuedSkill != null)
				{
					currPlayer.setQueuedSkill(null, false, false);
					
					// DON'T USE : Recursive call to useMagic() method
					// currPlayer.useMagic(queuedSkill.getSkill(), queuedSkill.isCtrlPressed(), queuedSkill.isShiftPressed());
					ThreadPoolManager.getInstance().executeGeneral(new QueuedMagicUseTask(currPlayer, queuedSkill.getSkill(), queuedSkill.isCtrlPressed(), queuedSkill.isShiftPressed()));
				}
			}
			
			if (_caster.isChanneling())
			{
				_caster.getSkillChannelizer().stopChanneling();
			}
			
			// Notify the AI of the L2Character with EVT_FINISH_CASTING
			_caster.getAI().notifyEvent(CtrlEvent.EVT_FINISH_CASTING);
			
			if (aborted)
			{
				_caster.broadcastPacket(new MagicSkillCanceld(_caster.getObjectId())); // broadcast packet to stop animations client-side
				_caster.sendPacket(ActionFailed.get(_castingType)); // send an "action failed" packet to the caster
			}
		}
		
		// Cleanup values and allow this SkillCaster to be used again.
		_target = null;
		_skill = null;
		_ctrlPressed = false;
		_shiftPressed = false;
		_castTime = 0;
		_reuseDelay = 0;
		_castInterruptTime = 0;
		_skillMastery = false;
		
		if (!_isCasting.compareAndSet(true, false))
		{
			_log.warn("Character: {} is finishing cast, but he has already finished.", _caster);
		}
	}
	
	public Skill getSkill()
	{
		return _skill;
	}
	
	public boolean isCtrlPressed()
	{
		return _ctrlPressed;
	}
	
	public boolean isShiftPressed()
	{
		return _shiftPressed;
	}
	
	public boolean isCasting()
	{
		return _isCasting.get();
	}
	
	public boolean isNotCasting()
	{
		return !_isCasting.get();
	}
	
	public boolean canAbortCast()
	{
		return _castInterruptTime > GameTimeController.getInstance().getGameTicks();
	}
	
	public SkillCastingType getCastingType()
	{
		return _castingType;
	}
	
	public boolean isNormalType()
	{
		return (_castingType == SkillCastingType.NORMAL) || (_castingType == SkillCastingType.NORMAL_SECOND);
	}
	
	public boolean isSimultaneousType()
	{
		return _castingType == SkillCastingType.SIMULTANEOUS;
	}
	
	/**
	 * TODO: Once target handlers are fixed, this method should be changed appropreately. Method should be static and not change target.
	 * @param caster
	 * @param target
	 * @param skill
	 * @return if skill can be casted onto target or not.
	 */
	private static WorldObject checkCastingTarget(Creature caster, WorldObject target, Skill skill)
	{
		// TODO: We need to fix target handlers and use main target + affect range of targets... we have to use main target here and not go through the target list.
		// Get all possible targets of the skill in a table in function of the skill target type
		Creature[] targets = skill.getTargetList(caster);
		
		boolean doit = false;
		
		// AURA skills should always be using caster as target
		switch (skill.getTargetType())
		{
			case AREA_SUMMON: // We need it to correct facing
				target = caster.getServitors().values().stream().findFirst().orElse(caster.getPet());
				break;
			case AURA:
			case AURA_CORPSE_MOB:
			case FRONT_AURA:
			case BEHIND_AURA:
			case GROUND:
				target = caster;
				break;
			case SELF:
			case PET:
			case SERVITOR:
			case SUMMON:
			case OWNER_PET:
			case PARTY:
			case CLAN:
			case PARTY_CLAN:
			case COMMAND_CHANNEL:
				doit = true;
			default:
				if (targets.length == 0)
				{
					return null;
				}
				
				if ((skill.isContinuous() && !skill.isDebuff()) || skill.hasEffectType(L2EffectType.CPHEAL, L2EffectType.HEAL))
				{
					doit = true;
				}
				
				if (doit)
				{
					target = targets[0];
				}
				else
				{
					target = caster.getTarget();
				}
		}
		
		return target;
	}
	
	public static boolean checkDoCastConditions(Creature caster, Skill skill)
	{
		if ((skill == null) || caster.isSkillDisabled(skill) || (((skill.getFlyRadius() > 0) || (skill.getFlyType() != null)) && caster.isMovementDisabled()))
		{
			// Send a Server->Client packet ActionFailed to the L2PcInstance
			caster.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if the caster has enough MP
		if (caster.getCurrentMp() < (caster.getStat().getMpConsume(skill) + caster.getStat().getMpInitialConsume(skill)))
		{
			// Send a System Message to the caster
			caster.sendPacket(SystemMessageId.NOT_ENOUGH_MP);
			
			// Send a Server->Client packet ActionFailed to the L2PcInstance
			caster.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if the caster has enough HP
		if (caster.getCurrentHp() <= skill.getHpConsume())
		{
			// Send a System Message to the caster
			caster.sendPacket(SystemMessageId.NOT_ENOUGH_HP);
			
			// Send a Server->Client packet ActionFailed to the L2PcInstance
			caster.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Skill mute checks.
		if (!skill.isStatic())
		{
			// Check if the skill is a magic spell and if the L2Character is not muted
			if (skill.isMagic())
			{
				if (caster.isMuted())
				{
					// Send a Server->Client packet ActionFailed to the L2PcInstance
					caster.sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
			}
			else
			{
				// Check if the skill is physical and if the L2Character is not physical_muted
				if (caster.isPhysicalMuted())
				{
					// Send a Server->Client packet ActionFailed to the L2PcInstance
					caster.sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
			}
		}
		
		// prevent casting signets to peace zone
		if (skill.isChanneling() && (skill.getChannelingSkillId() > 0))
		{
			final ZoneRegion zoneRegion = ZoneManager.getInstance().getRegion(caster);
			boolean canCast = true;
			if ((skill.getTargetType() == L2TargetType.GROUND) && caster.isPlayer())
			{
				Location wp = caster.getActingPlayer().getCurrentSkillWorldPosition();
				if (!zoneRegion.checkEffectRangeInsidePeaceZone(skill, wp.getX(), wp.getY(), wp.getZ()))
				{
					canCast = false;
				}
			}
			else if (!zoneRegion.checkEffectRangeInsidePeaceZone(skill, caster.getX(), caster.getY(), caster.getZ()))
			{
				canCast = false;
			}
			if (!canCast)
			{
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS);
				sm.addSkillName(skill);
				caster.sendPacket(sm);
				return false;
			}
		}
		
		// Check if the caster's weapon is limited to use only its own skills
		final Weapon weapon = caster.getActiveWeaponItem();
		if ((weapon != null) && weapon.useWeaponSkillsOnly() && !caster.isGM() && (weapon.getSkills(ItemSkillType.NORMAL) != null))
		{
			boolean found = false;
			for (SkillHolder sh : weapon.getSkills(ItemSkillType.NORMAL))
			{
				if (sh.getSkillId() == skill.getId())
				{
					found = true;
				}
			}
			
			if (!found)
			{
				if (caster.getActingPlayer() != null)
				{
					caster.sendPacket(SystemMessageId.THAT_WEAPON_CANNOT_USE_ANY_OTHER_SKILL_EXCEPT_THE_WEAPON_S_SKILL);
				}
				return false;
			}
		}
		
		// Check if the spell consumes an Item
		// TODO: combine check and consume
		if ((skill.getItemConsumeId() > 0) && (caster.getInventory() != null))
		{
			// Get the L2ItemInstance consumed by the spell
			ItemInstance requiredItems = caster.getInventory().getItemByItemId(skill.getItemConsumeId());
			
			// Check if the caster owns enough consumed Item to cast
			if ((requiredItems == null) || (requiredItems.getCount() < skill.getItemConsumeCount()))
			{
				// Checked: when a summon skill failed, server show required consume item count
				if (skill.hasEffectType(L2EffectType.SUMMON))
				{
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.SUMMONING_A_SERVITOR_COSTS_S2_S1);
					sm.addItemName(skill.getItemConsumeId());
					sm.addInt(skill.getItemConsumeCount());
					caster.sendPacket(sm);
				}
				else
				{
					// Send a System Message to the caster
					caster.sendPacket(SystemMessageId.THERE_ARE_NOT_ENOUGH_NECESSARY_ITEMS_TO_USE_THE_SKILL);
				}
				return false;
			}
		}
		
		if (caster.isPlayer())
		{
			PlayerInstance player = caster.getActingPlayer();
			if (player.inObserverMode())
			{
				return false;
			}
			
			if (player.isInOlympiadMode() && skill.isBlockedInOlympiad())
			{
				player.sendPacket(SystemMessageId.YOU_CANNOT_USE_THAT_SKILL_IN_A_OLYMPIAD_MATCH);
				return false;
			}
			
			if (player.isInsideZone(ZoneId.SAYUNE))
			{
				player.sendPacket(SystemMessageId.YOU_CANNOT_USE_SKILLS_IN_THE_CORRESPONDING_REGION);
				return false;
			}
			
			// Check if not in AirShip
			if (player.isInAirShip() && !skill.hasEffectType(L2EffectType.REFUEL_AIRSHIP))
			{
				SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_BE_USED_DUE_TO_UNSUITABLE_TERMS);
				sm.addSkillName(skill);
				player.sendPacket(sm);
				return false;
			}
		}
		
		return true;
	}
	
	public static int getCastTime(Creature caster, Skill skill)
	{
		// Get the Base Casting Time of the Skills.
		int skillTime = (skill.getHitTime() + skill.getCoolTime());
		
		if (!skill.isChanneling() || (skill.getChannelingSkillId() == 0))
		{
			// Calculate the Casting Time of the "Non-Static" Skills (with caster PAtk/MAtkSpd).
			if (!skill.isStatic())
			{
				skillTime = Formulas.calcAtkSpd(caster, skill, skillTime);
			}
			// Calculate the Casting Time of Magic Skills (reduced in 40% if using SPS/BSPS)
			if (skill.isMagic() && (caster.isChargedShot(ShotType.SPIRITSHOTS) || caster.isChargedShot(ShotType.BLESSED_SPIRITSHOTS)))
			{
				skillTime = (int) (0.6 * skillTime);
			}
		}
		
		// Avoid broken Casting Animation.
		// Client can't handle less than 550ms Casting Animation in Magic Skills with more than 550ms base.
		if (skill.isMagic() && ((skill.getHitTime() + skill.getCoolTime()) > 550) && (skillTime < 550))
		{
			skillTime = 550;
		}
		// Client can't handle less than 500ms Casting Animation in Physical Skills with 500ms base or more.
		else if (!skill.isStatic() && ((skill.getHitTime() + skill.getCoolTime()) >= 500) && (skillTime < 500))
		{
			skillTime = 500;
		}
		
		return skillTime;
	}
	
	public static int getReuseTime(Creature caster, Skill skill)
	{
		// Calculate the Reuse Time of the Skill
		int reuseDelay;
		if (skill.isStaticReuse() || skill.isStatic())
		{
			reuseDelay = skill.getReuseDelay();
		}
		else if (skill.isMagic())
		{
			reuseDelay = (int) (skill.getReuseDelay() * caster.getStat().getValue(Stats.MAGIC_REUSE_RATE, 1));
		}
		else if (skill.isPhysical())
		{
			reuseDelay = (int) (skill.getReuseDelay() * caster.getStat().getValue(Stats.P_REUSE, 1));
		}
		else
		{
			reuseDelay = (int) (skill.getReuseDelay() * caster.getStat().getValue(Stats.DANCE_REUSE, 1));
		}
		
		return reuseDelay;
	}
}
