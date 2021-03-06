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
package quests.Q10445_AnImpendingThreat;

import org.l2junity.gameserver.model.actor.Npc;
import org.l2junity.gameserver.model.actor.instance.PlayerInstance;
import org.l2junity.gameserver.model.quest.Quest;
import org.l2junity.gameserver.model.quest.QuestState;
import org.l2junity.gameserver.model.quest.State;

/**
 * An Impending Threat (10445)
 * @author St3eT
 */
public final class Q10445_AnImpendingThreat extends Quest
{
	// NPCs
	private static final int MATHIAS = 31340;
	private static final int TUSKA = 33839;
	private static final int BRUENER = 33840;
	// Items
	private static final int LETTER = 36681; // Curious Letter
	private static final int BADGE = 36685; // Reinforcements' Badge
	private static final int ELIXIR_LIFE = 30357; // Elixir of Life (R-grade)
	private static final int ELIXIR_MANA = 30358; // Elixir of Mind (R-grade)
	private static final int SSR = 34609; // Mysterious Soulshot (R-grade) - Event
	private static final int BSSR = 34616; // Mysterious Blessed Spiritshot (R-grade) - Event
	private static final int SOE = 37017; // Scroll of Escape: Raider's Crossroads
	// Misc
	private static final int MIN_LEVEL = 97;
	
	public Q10445_AnImpendingThreat()
	{
		super(10445);
		addStartNpc(MATHIAS);
		addTalkId(MATHIAS, TUSKA, BRUENER);
		registerQuestItems(LETTER, BADGE);
		addCondMinLevel(MIN_LEVEL, "31340-06.htm");
	}
	
	@Override
	public String onAdvEvent(String event, Npc npc, PlayerInstance player)
	{
		final QuestState st = getQuestState(player, false);
		if (st == null)
		{
			return null;
		}
		
		String htmltext = null;
		switch (event)
		{
			case "31340-02.htm":
			case "31340-03.htm":
			case "33839-02.html":
			{
				htmltext = event;
				break;
			}
			case "31340-04.htm":
			{
				st.startQuest();
				giveItems(player, LETTER, 1);
				htmltext = event;
				break;
			}
			case "33839-03.html":
			{
				if (st.isCond(1))
				{
					st.setCond(2);
					takeItems(player, LETTER, 1);
					giveItems(player, BADGE, 1);
					htmltext = event;
				}
				break;
			}
			case "33840-02.html":
			{
				if (st.isCond(2))
				{
					giveItems(player, ELIXIR_LIFE, 50);
					giveItems(player, ELIXIR_MANA, 50);
					giveItems(player, SSR, 10000);
					giveItems(player, BSSR, 10000);
					giveItems(player, SOE, 1);
					if (player.getLevel() >= MIN_LEVEL)
					{
						addExpAndSp(player, 100_506_183, 241_212);
					}
					st.exitQuest(false, true);
					htmltext = event;
				}
				break;
			}
		}
		return htmltext;
	}
	
	@Override
	public String onTalk(Npc npc, PlayerInstance player)
	{
		String htmltext = getNoQuestMsg(player);
		final QuestState st = getQuestState(player, true);
		
		switch (st.getState())
		{
			case State.CREATED:
			{
				if (npc.getId() == MATHIAS)
				{
					htmltext = "31340-01.htm";
				}
				break;
			}
			case State.STARTED:
			{
				switch (npc.getId())
				{
					case MATHIAS:
					{
						if (st.isCond(1))
						{
							htmltext = "31340-05.html";
						}
						break;
					}
					case TUSKA:
					{
						if (st.isCond(1))
						{
							htmltext = "33839-01.html";
						}
						else if (st.isCond(2))
						{
							htmltext = "33839-04.html";
						}
						break;
					}
					case BRUENER:
					{
						if (st.isCond(2))
						{
							htmltext = "33840-01.html";
						}
						break;
					}
				}
				break;
			}
			case State.COMPLETED:
			{
				if (npc.getId() == MATHIAS)
				{
					htmltext = getAlreadyCompletedMsg(player);
				}
				break;
			}
		}
		return htmltext;
	}
}