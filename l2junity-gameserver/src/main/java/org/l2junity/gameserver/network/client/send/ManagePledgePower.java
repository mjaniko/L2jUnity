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

import org.l2junity.gameserver.model.L2Clan;
import org.l2junity.gameserver.network.client.OutgoingPackets;
import org.l2junity.network.PacketWriter;

public class ManagePledgePower implements IClientOutgoingPacket
{
	private final int _action;
	private final L2Clan _clan;
	private final int _rank;
	
	public ManagePledgePower(L2Clan clan, int action, int rank)
	{
		_clan = clan;
		_action = action;
		_rank = rank;
	}
	
	@Override
	public boolean write(PacketWriter packet)
	{
		// TODO: Verify this!
		if (_action == 1)
		{
			OutgoingPackets.MANAGE_PLEDGE_POWER.writeId(packet);
			
			packet.writeD(_rank);
			packet.writeD(_action);
			packet.writeD(_clan.getRankPrivs(_rank).getBitmask());
			return true;
		}
		return false;
	}
}
