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
package org.l2junity.loginserver.db;

import java.io.Closeable;
import java.util.List;

import org.l2junity.loginserver.db.dto.Account;
import org.l2junity.loginserver.db.dto.AccountLogin;
import org.l2junity.loginserver.db.mapper.AccountLoginMapper;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

/**
 * @author NosBit
 */
@RegisterMapper(AccountLoginMapper.class)
public interface AccountLoginsDAO extends Closeable
{
	@SqlUpdate("INSERT INTO `account_logins`(`account_id`, `ip`) VALUES(:accountId, :ip)")
	@GetGeneratedKeys
	long insert(@Bind("accountId") long accountId, @Bind("ip") String ip);
	
	@SqlUpdate("INSERT INTO `account_logins`(`account_id`, `ip`) VALUES(:id, :ip)")
	@GetGeneratedKeys
	long insert(@BindBean Account account, @Bind("ip") String ip);
	
	@SqlUpdate("UPDATE `account_logins` SET `server_id` = :serverId WHERE `id` = :id")
	int updateServerId(@Bind("id") long id, @Bind("serverId") short serverid);
	
	@SqlQuery("SELECT * FROM `account_logins` WHERE `account_id` = :accountId")
	List<AccountLogin> findByAccountId(@Bind("accountId") long accountId);
	
	@SqlQuery("SELECT * FROM `account_logins` WHERE `account_id` = :id")
	List<AccountLogin> findByAccountId(@BindBean Account account);
	
	@Override
	void close();
}
