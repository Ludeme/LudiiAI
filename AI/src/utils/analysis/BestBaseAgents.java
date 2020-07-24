package utils.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Wrapper around collected data on the best base agents in all games
 * 
 * TODO could probably make sure we only ever load the data once...
 *
 * @author Dennis Soemers
 */
public class BestBaseAgents
{
	
	//-------------------------------------------------------------------------
	
	/** Map of entries (mapping from cleaned game names to entries of data) */
	private final Map<String, Entry> entries;
	
	//-------------------------------------------------------------------------
	
	/**
	 * @return Loads and returns the analysed data as stored so far.
	 */
	public static BestBaseAgents loadData()
	{
		final Map<String, Entry> entries = new HashMap<String, Entry>();
		final File file = new File("../AI/resources/Analysis/BestBaseAgents.csv");
		
		try (final BufferedReader reader = new BufferedReader(new FileReader(file)))
		{
			reader.readLine();	// headers line, which we don't use
			
			for (String line; (line = reader.readLine()) != null; /**/)
			{
				final String[] lineSplit = line.split(Pattern.quote(","));
				entries.put(lineSplit[0], new Entry
						(
							lineSplit[0],
							lineSplit[1],
							Float.parseFloat(lineSplit[2]),
							lineSplit[3],
							Long.parseLong(lineSplit[4])
						));
			}
		} 
		catch (final IOException e)
		{
			e.printStackTrace();
		}
		
		return new BestBaseAgents(entries);
	}
	
	/**
	 * Constructor
	 * @param entries
	 */
	private BestBaseAgents(final Map<String, Entry> entries)
	{
		this.entries = entries;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param cleanGameName
	 * @return Stored entry for given game name
	 */
	public Entry getEntry(final String cleanGameName)
	{
		return entries.get(cleanGameName);
	}
	
	/**
	 * @return Set of all game keys in our file
	 */
	public Set<String> keySet()
	{
		return entries.keySet();
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * An entry with data for one game in our collected data.
	 *
	 * @author Dennis Soemers
	 */
	public static class Entry
	{
		
		/** Name of game for which we stored data (cleaned for filepath-friendliness) */
		private final String cleanGameName;
		
		/** String description of top agent */
		private final String topAgent;
		
		/** Win percentage of the top agent in last evaluation (against set of other base agents) */
		private final float topScore;
		
		/** Which heuristics are best? (either "OldHeuristics" or "NewStartingHeuristics") */
		private final String bestHeuristics;
		
		/** 
		 * Time (in milliseconds since midnight, January 1, 1970 UTC) when we last 
		 * analysed Alpha-Beta agents for this game 
		 */
		private final long lastEvaluated;
		
		/**
		 * Constructor
		 * @param cleanGameName
		 * @param topHeuristic
		 * @param topAgent
		 * @param bestHeuristics
		 * @param lastEvaluated
		 */
		protected Entry
		(
			final String cleanGameName, 
			final String topAgent, 
			final float topScore, 
			final String bestHeuristics,
			final long lastEvaluated
		)
		{
			this.cleanGameName = cleanGameName;
			this.topAgent = topAgent;
			this.topScore = topScore;
			this.bestHeuristics = bestHeuristics;
			this.lastEvaluated = lastEvaluated;
		}
		
		/**
		 * @return Name of game for which we stored data (cleaned for filepath-friendliness)
		 */
		public String cleanGameName()
		{
			return cleanGameName;
		}
		
		/**
		 * @return String description of top agent
		 */
		public String topAgent()
		{
			return topAgent;
		}
		
		/**
		 * @return Win percentage of the top base agent
		 */
		public float topScore()
		{
			return topScore;
		}
		
		/**
		 * @return Which heuristics are best? (either "OldHeuristics" or "NewStartingHeuristics")
		 */
		public String bestHeuristics()
		{
			return bestHeuristics;
		}
		
		/**
		 * @return Time (in milliseconds since midnight, January 1, 1970 UTC) when we last 
		 * 	analysed base agents for this game 
		 */
		public long lastEvaluated()
		{
			return lastEvaluated;
		}
		
	}
	
	//-------------------------------------------------------------------------

}
