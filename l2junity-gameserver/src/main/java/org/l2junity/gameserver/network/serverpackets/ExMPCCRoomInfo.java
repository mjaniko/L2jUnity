/*
 * Copyright (C) 2004-2015 L2J Server
 * 
 * This file is part of L2J Server.
 * 
 * L2J Server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2junity.gameserver.network.serverpackets;

import org.l2junity.gameserver.model.matching.CommandChannelMatchingRoom;

/**
 * @author Sdw
 */
public class ExMPCCRoomInfo extends L2GameServerPacket
{
	private final CommandChannelMatchingRoom _room;
	
	public ExMPCCRoomInfo(CommandChannelMatchingRoom room)
	{
		_room = room;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0x9C);
		
		writeD(_room.getId());
		writeD(_room.getMaxMembers());
		writeD(_room.getMinLvl());
		writeD(_room.getMaxLvl());
		writeD(_room.getLootType());
		writeD(_room.getLocation());
		writeS(_room.getTitle());
	}
}
