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
package org.l2junity.gameserver.network.client.send;

import java.util.Set;

import org.l2junity.Config;
import org.l2junity.gameserver.data.sql.impl.ClanTable;
import org.l2junity.gameserver.enums.NpcInfoType;
import org.l2junity.gameserver.enums.Team;
import org.l2junity.gameserver.model.L2Clan;
import org.l2junity.gameserver.model.actor.Npc;
import org.l2junity.gameserver.model.actor.instance.L2GuardInstance;
import org.l2junity.gameserver.model.skills.AbnormalVisualEffect;
import org.l2junity.gameserver.model.zone.ZoneId;
import org.l2junity.gameserver.network.client.OutgoingPackets;
import org.l2junity.gameserver.network.client.send.string.NpcStringId;
import org.l2junity.network.PacketWriter;

/**
 * @author UnAfraid
 */
public class NpcInfo extends AbstractMaskPacket<NpcInfoType>
{
	private final Npc _npc;
	private final byte[] _masks = new byte[]
	{
		(byte) 0x00,
		(byte) 0x0C,
		(byte) 0x0C,
		(byte) 0x00,
		(byte) 0x00
	};
	
	private int _initSize = 0;
	private int _blockSize = 0;
	
	private int _clanCrest = 0;
	private int _clanLargeCrest = 0;
	private int _allyCrest = 0;
	private int _allyId = 0;
	private int _clanId = 0;
	private int _statusMask = 0;
	private final Set<AbnormalVisualEffect> _abnormalVisualEffects;
	private final String _title;
	
	public NpcInfo(Npc npc)
	{
		_npc = npc;
		_abnormalVisualEffects = npc.getCurrentAbnormalVisualEffects();
		
		if (_npc.isInvisible())
		{
			_title = "Invisible";
		}
		else if (Config.L2JMOD_CHAMPION_ENABLE && _npc.isChampion())
		{
			_title = (Config.L2JMOD_CHAMP_TITLE);
		}
		else if (_npc.getTemplate().isUsingServerSideTitle())
		{
			_title = _npc.getTemplate().getTitle();
		}
		else
		{
			_title = _npc.getTitle();
		}
		
		if (npc.getTemplate().getDisplayId() != npc.getTemplate().getId())
		{
			_masks[2] |= 0x10;
		}
		
		addComponentType(NpcInfoType.ATTACKABLE, NpcInfoType.UNKNOWN1, NpcInfoType.TITLE, NpcInfoType.ID, NpcInfoType.POSITION, NpcInfoType.ALIVE, NpcInfoType.RUNNING);
		
		if (npc.getHeading() > 0)
		{
			addComponentType(NpcInfoType.HEADING);
		}
		
		if ((npc.getStat().getPAtkSpd() > 0) || (npc.getStat().getMAtkSpd() > 0))
		{
			addComponentType(NpcInfoType.ATK_CAST_SPEED);
		}
		
		if (npc.getRunSpeed() > 0)
		{
			addComponentType(NpcInfoType.SPEED_MULTIPLIER);
		}
		
		if ((npc.getLeftHandItem() > 0) || (npc.getRightHandItem() > 0))
		{
			addComponentType(NpcInfoType.EQUIPPED);
		}
		
		if (npc.getTeam() != Team.NONE)
		{
			addComponentType(NpcInfoType.TEAM);
		}
		
		if (npc.getState() > 0)
		{
			addComponentType(NpcInfoType.DISPLAY_EFFECT);
		}
		
		if (npc.isInsideZone(ZoneId.WATER) || npc.isFlying())
		{
			addComponentType(NpcInfoType.SWIM_OR_FLY);
		}
		
		if (npc.isFlying())
		{
			addComponentType(NpcInfoType.FLYING);
		}
		
		if (npc.getCloneObjId() > 0)
		{
			addComponentType(NpcInfoType.CLONE);
		}
		
		if (npc.getMaxHp() > 0)
		{
			addComponentType(NpcInfoType.MAX_HP);
		}
		
		if (npc.getMaxMp() > 0)
		{
			addComponentType(NpcInfoType.MAX_MP);
		}
		
		if (npc.getCurrentHp() <= npc.getMaxHp())
		{
			addComponentType(NpcInfoType.CURRENT_HP);
		}
		
		if (npc.getCurrentMp() <= npc.getMaxMp())
		{
			addComponentType(NpcInfoType.CURRENT_MP);
		}
		
		if (npc.getTemplate().getDisplayId() != npc.getTemplate().getId())
		{
			addComponentType(NpcInfoType.NAME);
		}
		
		if (npc.getNameString() != null)
		{
			addComponentType(NpcInfoType.NAME_NPCSTRINGID);
		}
		
		if (npc.getTitleString() != null)
		{
			addComponentType(NpcInfoType.TITLE_NPCSTRINGID);
		}
		
		if (!_abnormalVisualEffects.isEmpty() || npc.isInvisible())
		{
			addComponentType(NpcInfoType.ABNORMALS);
		}
		
		if (npc.getEnchantEffect() > 0)
		{
			addComponentType(NpcInfoType.ENCHANT);
		}
		
		if (npc.getTransformationDisplayId() > 0)
		{
			addComponentType(NpcInfoType.TRANSFORMATION);
		}
		
		if (npc.isShowSummonAnimation())
		{
			addComponentType(NpcInfoType.SUMMONED);
		}
		
		if (npc.getClanId() > 0)
		{
			L2Clan clan = ClanTable.getInstance().getClan(npc.getClanId());
			if (clan != null)
			{
				_clanId = clan.getId();
				_clanCrest = clan.getCrestId();
				_clanLargeCrest = clan.getCrestLargeId();
				_allyCrest = clan.getAllyCrestId();
				_allyId = clan.getAllyId();
				
				addComponentType(NpcInfoType.CLAN);
			}
		}
		
		addComponentType(NpcInfoType.UNKNOWN8);
		
		if (npc.getPvpFlag() > 0)
		{
			addComponentType(NpcInfoType.PVP_FLAG);
		}
		
		// TODO: Confirm me
		if (npc.isInCombat())
		{
			_statusMask |= 0x01;
		}
		if (npc.isDead())
		{
			_statusMask |= 0x02;
		}
		if (npc.isTargetable())
		{
			_statusMask |= 0x04;
		}
		if (npc.isShowName())
		{
			_statusMask |= 0x08;
		}
		
		if (_statusMask != 0)
		{
			addComponentType(NpcInfoType.VISUAL_STATE);
		}
	}
	
	@Override
	protected byte[] getMasks()
	{
		return _masks;
	}
	
	@Override
	protected void onNewMaskAdded(NpcInfoType component)
	{
		calcBlockSize(_npc, component);
	}
	
	private void calcBlockSize(Npc npc, NpcInfoType type)
	{
		switch (type)
		{
			case ATTACKABLE:
			case UNKNOWN1:
			{
				_initSize += type.getBlockLength();
				break;
			}
			case TITLE:
			{
				_initSize += type.getBlockLength() + (_title.length() * 2);
				break;
			}
			case NAME:
			{
				_blockSize += type.getBlockLength() + (npc.getName().length() * 2);
				break;
			}
			default:
			{
				_blockSize += type.getBlockLength();
				break;
			}
		}
	}
	
	@Override
	public boolean write(PacketWriter packet)
	{
		OutgoingPackets.NPC_INFO.writeId(packet);
		
		packet.writeD(_npc.getObjectId());
		packet.writeC(_npc.isShowSummonAnimation() ? 0x02 : 0x00); // // 0=teleported 1=default 2=summoned
		packet.writeH(37); // mask_bits_37
		packet.writeB(_masks);
		
		// Block 1
		packet.writeC(_initSize);
		
		if (containsMask(NpcInfoType.ATTACKABLE))
		{
			packet.writeC(_npc.isAttackable() && !(_npc instanceof L2GuardInstance) ? 0x01 : 0x00);
		}
		if (containsMask(NpcInfoType.UNKNOWN1))
		{
			packet.writeD(0x00); // unknown
		}
		if (containsMask(NpcInfoType.TITLE))
		{
			packet.writeS(_title);
		}
		
		// Block 2
		packet.writeH(_blockSize);
		if (containsMask(NpcInfoType.ID))
		{
			packet.writeD(_npc.getTemplate().getDisplayId() + 1000000);
		}
		if (containsMask(NpcInfoType.POSITION))
		{
			packet.writeD(_npc.getX());
			packet.writeD(_npc.getY());
			packet.writeD(_npc.getZ());
		}
		if (containsMask(NpcInfoType.HEADING))
		{
			packet.writeD(_npc.getHeading());
		}
		if (containsMask(NpcInfoType.UNKNOWN2))
		{
			packet.writeD(0x00); // Unknown
		}
		if (containsMask(NpcInfoType.ATK_CAST_SPEED))
		{
			packet.writeD(_npc.getPAtkSpd());
			packet.writeD(_npc.getMAtkSpd());
		}
		if (containsMask(NpcInfoType.SPEED_MULTIPLIER))
		{
			packet.writeE((float) _npc.getStat().getMovementSpeedMultiplier());
			packet.writeE(_npc.getStat().getAttackSpeedMultiplier());
		}
		if (containsMask(NpcInfoType.EQUIPPED))
		{
			packet.writeD(_npc.getRightHandItem());
			packet.writeD(0x00); // Armor id?
			packet.writeD(_npc.getLeftHandItem());
		}
		if (containsMask(NpcInfoType.ALIVE))
		{
			packet.writeC(_npc.isDead() ? 0x00 : 0x01);
		}
		if (containsMask(NpcInfoType.RUNNING))
		{
			packet.writeC(_npc.isRunning() ? 0x01 : 0x00);
		}
		if (containsMask(NpcInfoType.SWIM_OR_FLY))
		{
			packet.writeC(_npc.isInsideZone(ZoneId.WATER) ? 0x01 : _npc.isFlying() ? 0x02 : 0x00);
		}
		if (containsMask(NpcInfoType.TEAM))
		{
			packet.writeC(_npc.getTeam().getId());
		}
		if (containsMask(NpcInfoType.ENCHANT))
		{
			packet.writeD(_npc.getEnchantEffect());
		}
		if (containsMask(NpcInfoType.FLYING))
		{
			packet.writeD(_npc.isFlying() ? 0x01 : 00);
		}
		if (containsMask(NpcInfoType.CLONE))
		{
			packet.writeD(_npc.getCloneObjId()); // Player ObjectId with Decoy
		}
		if (containsMask(NpcInfoType.UNKNOWN8))
		{
			// No visual effect
			packet.writeD(0x00); // Unknown
		}
		if (containsMask(NpcInfoType.DISPLAY_EFFECT))
		{
			packet.writeD(_npc.getState());
		}
		if (containsMask(NpcInfoType.TRANSFORMATION))
		{
			packet.writeD(_npc.getTransformationDisplayId()); // Transformation ID
		}
		if (containsMask(NpcInfoType.CURRENT_HP))
		{
			packet.writeD((int) _npc.getCurrentHp());
		}
		if (containsMask(NpcInfoType.CURRENT_MP))
		{
			packet.writeD((int) _npc.getCurrentMp());
		}
		if (containsMask(NpcInfoType.MAX_HP))
		{
			packet.writeD(_npc.getMaxHp());
		}
		if (containsMask(NpcInfoType.MAX_MP))
		{
			packet.writeD(_npc.getMaxMp());
		}
		if (containsMask(NpcInfoType.SUMMONED))
		{
			packet.writeC(0x00); // 2 - do some animation on spawn
		}
		if (containsMask(NpcInfoType.UNKNOWN12))
		{
			packet.writeD(0x00);
			packet.writeD(0x00);
		}
		if (containsMask(NpcInfoType.NAME))
		{
			packet.writeS(_npc.getName());
		}
		if (containsMask(NpcInfoType.NAME_NPCSTRINGID))
		{
			final NpcStringId nameString = _npc.getNameString();
			packet.writeD(nameString != null ? nameString.getId() : -1); // NPCStringId for name
		}
		if (containsMask(NpcInfoType.TITLE_NPCSTRINGID))
		{
			final NpcStringId titleString = _npc.getTitleString();
			packet.writeD(titleString != null ? titleString.getId() : -1); // NPCStringId for title
		}
		if (containsMask(NpcInfoType.PVP_FLAG))
		{
			packet.writeC(_npc.getPvpFlag()); // PVP flag
		}
		if (containsMask(NpcInfoType.NAME_COLOR))
		{
			packet.writeD(0x00); // Name color
		}
		if (containsMask(NpcInfoType.CLAN))
		{
			packet.writeD(_clanId);
			packet.writeD(_clanCrest);
			packet.writeD(_clanLargeCrest);
			packet.writeD(_allyId);
			packet.writeD(_allyCrest);
		}
		
		if (containsMask(NpcInfoType.VISUAL_STATE))
		{
			packet.writeC(_statusMask);
		}
		
		if (containsMask(NpcInfoType.ABNORMALS))
		{
			packet.writeH(_abnormalVisualEffects.size() + (_npc.isInvisible() ? 1 : 0));
			for (AbnormalVisualEffect abnormalVisualEffect : _abnormalVisualEffects)
			{
				packet.writeH(abnormalVisualEffect.getClientId());
			}
			if (_npc.isInvisible())
			{
				packet.writeH(AbnormalVisualEffect.STEALTH.getClientId());
			}
		}
		return true;
	}
}