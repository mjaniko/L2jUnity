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
package org.l2junity.gameserver.model.stats;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiFunction;

import org.l2junity.commons.util.MathUtil;
import org.l2junity.gameserver.enums.AttributeType;
import org.l2junity.gameserver.model.actor.Creature;
import org.l2junity.gameserver.model.stats.finalizers.AttributeFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.BaseStatsFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.MAccuracyFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.MAttackFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.MAttackSpeedFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.MCritRateFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.MDefenseFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.MEvasionRateFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.MaxCpFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.MaxHpFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.MaxMpFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.PAccuracyFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.PAttackFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.PAttackSpeedFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.PCriticalRateFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.PDefenseFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.PEvasionRateFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.PRangeFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.RandomDamageFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.ShotsBonusFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.SpeedFinalizer;
import org.l2junity.gameserver.model.stats.finalizers.VampiricChanceFinalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enum of basic stats.
 * @author mkizub
 */
public enum Stats
{
	// Base stats, for each in Calculator a slot is allocated
	
	// HP, MP & CP
	MAX_HP("maxHp", new MaxHpFinalizer()),
	MAX_MP("maxMp", new MaxMpFinalizer()),
	MAX_CP("maxCp", new MaxCpFinalizer()),
	MAX_RECOVERABLE_HP("maxRecoverableHp"), // The maximum HP that is able to be recovered trough heals
	MAX_RECOVERABLE_MP("maxRecoverableMp"),
	MAX_RECOVERABLE_CP("maxRecoverableCp"),
	REGENERATE_HP_RATE("regHp"),
	REGENERATE_CP_RATE("regCp"),
	REGENERATE_MP_RATE("regMp"),
	MANA_CHARGE("manaCharge"),
	HEAL_EFFECT("healEffect"),
	HEAL_POWER("healPower"),
	
	// ATTACK & DEFENCE
	PHYSICAL_DEFENCE("pDef", new PDefenseFinalizer()),
	MAGICAL_DEFENCE("mDef", new MDefenseFinalizer()),
	PHYSICAL_ATTACK("pAtk", new PAttackFinalizer()),
	MAGIC_ATTACK("mAtk", new MAttackFinalizer()),
	PHYSICAL_SKILL_POWER("physicalSkillPower"),
	PHYSICAL_ATTACK_SPEED("pAtkSpd", new PAttackSpeedFinalizer()),
	MAGIC_ATTACK_SPEED("mAtkSpd", new MAttackSpeedFinalizer()), // Magic Skill Casting Time Rate
	ATK_REUSE("atkReuse"), // Bows Hits Reuse Rate
	SHIELD_DEFENCE("sDef"),
	CRITICAL_DAMAGE("cAtk"),
	CRITICAL_DAMAGE_SKILL("cAtkSkill"),
	CRITICAL_DAMAGE_ADD("cAtkAdd"), // this is another type for special critical damage mods - vicious stance, critical power and critical damage SA
	MAGIC_CRIT_DMG("mCritPower"),
	MAGIC_CRIT_DMG_ADD("mCritPowerAdd"),
	
	// PVP BONUS
	PVP_DAMAGE("pvpDamage"), // TODO: Implement me
	PVP_PHYSICAL_DMG("pvpPhysDmg"),
	PVP_MAGICAL_DMG("pvpMagicalDmg"),
	PVP_PHYS_SKILL_DMG("pvpPhysSkillsDmg"),
	PVP_PHYSICAL_DEF("pvpPhysDef"),
	PVP_MAGICAL_DEF("pvpMagicalDef"),
	PVP_PHYS_SKILL_DEF("pvpPhysSkillsDef"),
	
	// PVE BONUS
	PVE_PHYSICAL_DMG("pvePhysDmg"),
	PVE_PHYS_SKILL_DMG("pvePhysSkillsDmg"),
	PVE_MAGICAL_DMG("pveMagicalDmg"),
	PVE_PHYSICAL_DEF("pvePhysDef"),
	PVE_PHYS_SKILL_DEF("pvePhysSkillsDef"),
	PVE_MAGICAL_DEF("pveMagicalDef"),
	PVE_RAID_PHYSICAL_DEF("pveRaidPhysDef"),
	PVE_RAID_PHYS_SKILL_DEF("pveRaidPhysSkillsDef"),
	PVE_RAID_MAGICAL_DEF("pveRaidMagicalDef"),
	
	// ATTACK & DEFENCE RATES
	EVASION_RATE("rEvas", new PEvasionRateFinalizer()),
	MAGIC_EVASION_RATE("mEvas", new MEvasionRateFinalizer()),
	DEFENCE_CRITICAL_RATE("defCritRate"),
	DEFENCE_CRITICAL_RATE_ADD("defCritRateAdd"),
	DEFENCE_MAGIC_CRITICAL_RATE("defMCritRate"),
	DEFENCE_MAGIC_CRITICAL_RATE_ADD("defMCritRateAdd"),
	DEFENCE_CRITICAL_DAMAGE("defCritDamage"),
	DEFENCE_MAGIC_CRITICAL_DAMAGE("defMCritDamage"),
	DEFENCE_CRITICAL_DAMAGE_ADD("defCritDamageAdd"), // Resistance to critical damage in value (Example: +100 will be 100 more critical damage, NOT 100% more).
	SHIELD_DEFENCE_RATE("rShld"),
	CRITICAL_RATE("rCrit", new PCriticalRateFinalizer(), MathUtil::add, MathUtil::add, null, 1d),
	MAGIC_CRITICAL_RATE("mCritRate", new MCritRateFinalizer()),
	BLOW_RATE("blowRate"),
	EXPSP_RATE("rExp"),
	BONUS_EXP("bonusExp"),
	BONUS_SP("bonusSp"),
	ATTACK_CANCEL("cancel"),
	
	// ACCURACY & RANGE
	ACCURACY_COMBAT("accCombat", new PAccuracyFinalizer()),
	ACCURACY_MAGIC("accMagic", new MAccuracyFinalizer()),
	PHYSICAL_ATTACK_RANGE("pAtkRange", new PRangeFinalizer()),
	MAGIC_ATTACK_RANGE("mAtkRange"),
	ATTACK_COUNT_MAX("atkCountMax"),
	// Run speed, walk & escape speed are calculated proportionally, magic speed is a buff
	MOVE_SPEED("moveSpeed"),
	RUN_SPEED("runSpd", new SpeedFinalizer()),
	WALK_SPEED("walkSpd", new SpeedFinalizer()),
	SWIM_RUN_SPEED("fastSwimSpd", new SpeedFinalizer()),
	SWIM_WALK_SPEED("slowSimSpd", new SpeedFinalizer()),
	FLY_RUN_SPEED("fastFlySpd", new SpeedFinalizer()),
	FLY_WALK_SPEED("slowFlySpd", new SpeedFinalizer()),
	
	// BASIC STATS
	STAT_STR("STR", new BaseStatsFinalizer()),
	STAT_CON("CON", new BaseStatsFinalizer()),
	STAT_DEX("DEX", new BaseStatsFinalizer()),
	STAT_INT("INT", new BaseStatsFinalizer()),
	STAT_WIT("WIT", new BaseStatsFinalizer()),
	STAT_MEN("MEN", new BaseStatsFinalizer()),
	STAT_LUC("LUC", new BaseStatsFinalizer()),
	STAT_CHA("CHA", new BaseStatsFinalizer()),
	
	// Special stats, share one slot in Calculator
	
	// VARIOUS
	BREATH("breath"),
	FALL("fall"),
	
	// VULNERABILITIES
	DAMAGE_ZONE_VULN("damageZoneVuln"),
	CANCEL_VULN("cancelVuln"), // Resistance for cancel type skills
	DEBUFF_VULN("debuffVuln"),
	
	// RESISTANCES
	FIRE_RES("fireRes", new AttributeFinalizer(AttributeType.FIRE, false)),
	WIND_RES("windRes", new AttributeFinalizer(AttributeType.WIND, false)),
	WATER_RES("waterRes", new AttributeFinalizer(AttributeType.WATER, false)),
	EARTH_RES("earthRes", new AttributeFinalizer(AttributeType.EARTH, false)),
	HOLY_RES("holyRes", new AttributeFinalizer(AttributeType.HOLY, false)),
	DARK_RES("darkRes", new AttributeFinalizer(AttributeType.DARK, false)),
	BASE_ATTRIBUTE_RES("baseAttrRes"),
	MAGIC_SUCCESS_RES("magicSuccRes"),
	// BUFF_IMMUNITY("buffImmunity"), //TODO: Implement me
	ABNORMAL_RESIST_PHYSICAL("abnormalResPhysical"),
	ABNORMAL_RESIST_MAGICAL("abnormalResMagical"),
	
	// ELEMENT POWER
	FIRE_POWER("firePower", new AttributeFinalizer(AttributeType.FIRE, true)),
	WATER_POWER("waterPower", new AttributeFinalizer(AttributeType.WATER, true)),
	WIND_POWER("windPower", new AttributeFinalizer(AttributeType.WIND, true)),
	EARTH_POWER("earthPower", new AttributeFinalizer(AttributeType.EARTH, true)),
	HOLY_POWER("holyPower", new AttributeFinalizer(AttributeType.HOLY, true)),
	DARK_POWER("darkPower", new AttributeFinalizer(AttributeType.DARK, true)),
	
	// PROFICIENCY
	REFLECT_DAMAGE_PERCENT("reflectDam"),
	REFLECT_DAMAGE_PERCENT_DEFENSE("reflectDamDef"),
	REFLECT_SKILL_MAGIC("reflectSkillMagic"),
	REFLECT_SKILL_PHYSIC("reflectSkillPhysic"),
	VENGEANCE_SKILL_MAGIC_DAMAGE("vengeanceMdam"),
	VENGEANCE_SKILL_PHYSICAL_DAMAGE("vengeancePdam"),
	ABSORB_DAMAGE_PERCENT("absorbDam"),
	ABSORB_DAMAGE_CHANCE("absorbDamChance", new VampiricChanceFinalizer()),
	TRANSFER_DAMAGE_SUMMON_PERCENT("transDam"),
	MANA_SHIELD_PERCENT("manaShield"),
	TRANSFER_DAMAGE_TO_PLAYER("transDamToPlayer"),
	ABSORB_MANA_DAMAGE_PERCENT("absorbDamMana"),
	
	WEIGHT_LIMIT("weightLimit"),
	WEIGHT_PENALTY("weightPenalty"),
	
	// ExSkill
	INVENTORY_NORMAL("inventoryLimit"),
	STORAGE_PRIVATE("whLimit"),
	TRADE_SELL("PrivateSellLimit"),
	TRADE_BUY("PrivateBuyLimit"),
	RECIPE_DWARVEN("DwarfRecipeLimit"),
	RECIPE_COMMON("CommonRecipeLimit"),
	
	// Skill mastery
	SKILL_CRITICAL("skillCritical"),
	SKILL_CRITICAL_PROBABILITY("skillCriticalProbability"),
	
	// Vitality
	VITALITY_CONSUME_RATE("vitalityConsumeRate"),
	
	// Souls
	MAX_SOULS("maxSouls"),
	
	REDUCE_EXP_LOST_BY_PVP("reduceExpLostByPvp"),
	REDUCE_EXP_LOST_BY_MOB("reduceExpLostByMob"),
	REDUCE_EXP_LOST_BY_RAID("reduceExpLostByRaid"),
	
	REDUCE_DEATH_PENALTY_BY_PVP("reduceDeathPenaltyByPvp"),
	REDUCE_DEATH_PENALTY_BY_MOB("reduceDeathPenaltyByMob"),
	REDUCE_DEATH_PENALTY_BY_RAID("reduceDeathPenaltyByRaid"),
	
	// Brooches
	BROOCH_JEWELS("broochJewels"),
	
	// Summon Points
	MAX_SUMMON_POINTS("summonPoints"),
	
	// Cubic Count
	MAX_CUBIC("cubicCount"),
	
	// Exp bonus applied to vitality
	VITALITY_EXP_BONUS("vitalityExpBonus"),
	
	// Storm Sign bonus damage
	STORM_SIGN_BONUS("stormSignBonus"),
	
	// The maximum allowed range to be damaged from.
	DAMAGED_MAX_RANGE("damagedMaxRange"),
	
	// The maximum allowed range to be debuffed from.
	DEBUFFED_MAX_RANGE("debuffedMaxRange"),
	
	// Blocks given amount of debuffs.
	DEBUFF_BLOCK("debuffBlock"),
	
	// Affects the random weapon damage.
	RANDOM_DAMAGE("randomDamage", new RandomDamageFinalizer()),
	
	// Affects the random weapon damage.
	DAMAGE_CAP("damageCap"),
	
	// Lock your HP at the given value.
	HP_LOCK("hpLock"),
	
	// Lock HP, can't go below min value.
	HP_LOCK_MIN("hpLockMin"),
	
	// Maximun momentum one can charge
	MAX_MOMENTUM("maxMomentum"),
	
	// Which base stat ordinal should alter skill critical formula.
	STAT_SKILLCRITICAL("statSkillCritical"),
	STAT_SPEED("statSpeed"),
	SHOTS_BONUS("shotBonus", new ShotsBonusFinalizer());
	
	static final Logger LOGGER = LoggerFactory.getLogger(Stats.class);
	public static final int NUM_STATS = values().length;
	
	private final String _value;
	private final IStatsFunction _valueFinalizer;
	private final BiFunction<Double, Double, Double> _addFunction;
	private final BiFunction<Double, Double, Double> _mulFunction;
	private final Double _resetAddValue;
	private final Double _resetMulValue;
	
	public String getValue()
	{
		return _value;
	}
	
	Stats(String xmlString)
	{
		this(xmlString, Stats::defaultValue, MathUtil::add, MathUtil::mul, null, null);
	}
	
	Stats(String xmlString, IStatsFunction valueFinalizer)
	{
		this(xmlString, valueFinalizer, MathUtil::add, MathUtil::mul, null, null);
		
	}
	
	Stats(String xmlString, IStatsFunction valueFinalizer, BiFunction<Double, Double, Double> addFunction, BiFunction<Double, Double, Double> mulFunction, Double resetAddValue, Double resetMulValue)
	{
		_value = xmlString;
		_valueFinalizer = valueFinalizer;
		_addFunction = addFunction;
		_mulFunction = mulFunction;
		_resetAddValue = resetAddValue;
		_resetMulValue = resetMulValue;
	}
	
	public static Stats valueOfXml(String name)
	{
		name = name.intern();
		for (Stats s : values())
		{
			if (s.getValue().equals(name))
			{
				return s;
			}
		}
		
		throw new NoSuchElementException("Unknown name '" + name + "' for enum " + Stats.class.getSimpleName());
	}
	
	/**
	 * @param creature
	 * @param baseValue
	 * @return the final value
	 */
	public Double finalize(Creature creature, Optional<Double> baseValue)
	{
		try
		{
			return _valueFinalizer.calc(creature, baseValue, this);
		}
		catch (Exception e)
		{
			LOGGER.warn("Exception during finalization for : {} stat: {} : ", creature, toString(), e);
			return defaultValue(creature, baseValue, this);
		}
	}
	
	public double functionAdd(double oldValue, double value)
	{
		return _addFunction.apply(oldValue, value);
	}
	
	public double functionMul(double oldValue, double value)
	{
		return _mulFunction.apply(oldValue, value);
	}
	
	public Double getResetAddValue()
	{
		return _resetAddValue;
	}
	
	public Double getResetMulValue()
	{
		return _resetMulValue;
	}
	
	public static double defaultValue(Creature creature, Optional<Double> base, Stats stat)
	{
		final double mul = creature.getStat().getMul(stat);
		final double add = creature.getStat().getAdd(stat);
		return base.isPresent() ? defaultValue(creature, stat, base.get()) : mul * (add + creature.getStat().getMoveTypeValue(stat, creature.getMoveType()));
	}
	
	public static double defaultValue(Creature creature, Stats stat, double baseValue)
	{
		final double mul = creature.getStat().getMul(stat);
		final double add = creature.getStat().getAdd(stat);
		return (baseValue * mul) + add + creature.getStat().getMoveTypeValue(stat, creature.getMoveType());
	}
}
