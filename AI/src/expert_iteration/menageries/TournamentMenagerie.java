package expert_iteration.menageries;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import expert_iteration.ExpertPolicy;
import expert_iteration.params.AgentsParams;
import game.Game;
import gnu.trove.list.array.TFloatArrayList;
import main.collections.FVector;
import metadata.ai.features.Features;
import metadata.ai.heuristics.Heuristics;
import util.Context;
import utils.AIUtils;

/**
 * Menagerie for Elo-based tournament mode (like in Polygames)
 *
 * @author Dennis Soemers
 */
public class TournamentMenagerie implements Menagerie
{
	
	//-------------------------------------------------------------------------
	
	/** Our dev checkpoint */
	private AgentCheckpoint dev;
	
	/** Population of checkpoints */
	private final List<AgentCheckpoint> population = new ArrayList<AgentCheckpoint>();
	
	/** First indexed by Player ID (in game), secondly indexed by index of population list */
	private TFloatArrayList[] populationElosTable;
	
	/** Elo ratings for dev (indexed by Player ID) */
	private float[] devElos;
	
	/** Do we have to add our new dev to the population? */
	private boolean shouldAddDev = false;
	
	//-------------------------------------------------------------------------

	@Override
	public TournamentDrawnAgentsData drawAgents(final Game game, final AgentsParams agentsParams)
	{
		if (shouldAddDev)
		{
			population.add(dev);
			shouldAddDev = false;
			
			// Initialise Elo ratings for new checkpoint
			for (int p = 1; p < devElos.length; ++p)
			{
				populationElosTable[p].add(devElos[p]);
			}
		}
		
		final int[] agentIndices = new int[devElos.length];
		agentIndices[0] = -1;
		final List<ExpertPolicy> agents = new ArrayList<ExpertPolicy>(devElos.length);
		agents.add(null);
		
		// We will always use dev for at least one of the players
		final int devIndex = ThreadLocalRandom.current().nextInt(1, devElos.length);
		
		for (int p = 1; p < devElos.length; ++p)
		{
			if (p == devIndex)
			{
				agents.add(dev.generateAgent(game, agentsParams));
				agentIndices[p] = -1;
			}
			else
			{
				// Compute vector of probabilities for this player for all checkpoints based on Elo ratings
				final FVector probs = new FVector(populationElosTable[p]);
				final float max = probs.max();
				
				for (int i = 0; i < probs.dim(); ++i)
				{
					probs.set(i, (float) Math.exp((probs.get(i) - max) / 400.f));
				}
				
				final int sampledAgentIdx = probs.sampleProportionally();
				agents.add(population.get(sampledAgentIdx).generateAgent(game, agentsParams));
				agentIndices[p] = sampledAgentIdx;
			}
		}
		
		return new TournamentDrawnAgentsData(agents, devIndex, agentIndices);
	}
	
	//-------------------------------------------------------------------------
	
	@Override
	public void initialisePopulation
	(
		final Game game,
		final AgentsParams agentsParams, 
		final Features features, 
		final Heuristics heuristics
	)
	{
		dev = new AgentCheckpoint(agentsParams.expertAI, features, heuristics);
		population.clear();
		shouldAddDev = true;
		
		final int numPlayers = game.players().count();
		populationElosTable = new TFloatArrayList[numPlayers + 1];
		devElos = new float[numPlayers + 1];
		
		for (int p = 1; p <= numPlayers; ++p)
		{
			devElos[p] = 0.f;	// Initialise Elo rating
			populationElosTable[p] = new TFloatArrayList();
		}
	}
	
	@Override
	public void updateDevFeatures(final Features features)
	{
		dev = new AgentCheckpoint(dev.agentName, features, dev.heuristicsMetadata);
		shouldAddDev = true;
	}
	
	@Override
	public void updateDevHeuristics(final Heuristics heuristics)
	{
		dev = new AgentCheckpoint(dev.agentName, dev.featuresMetadata, heuristics);
		shouldAddDev = true;
	}
	
	@Override
	public void updateOutcome(final Context context, final DrawnAgentsData drawnAgentsData)
	{
		final TournamentDrawnAgentsData d = (TournamentDrawnAgentsData) drawnAgentsData;
		final double[] utilities = AIUtils.agentUtilities(context);
		
		float sumElos = 0.f;
		
		for (int p = 1; p < devElos.length; ++p)
		{
			if (p == d.devIdx())
				sumElos += devElos[p];
			else
				sumElos += populationElosTable[p].getQuick(d.agentIndices()[p]);
		}
		
		for (int p = 1; p < devElos.length; ++p)
		{
			final double pUtility = utilities[p];
			final float pElo;
			if (p == d.devIdx())
				pElo = devElos[p];
			else
				pElo = populationElosTable[p].getQuick(d.agentIndices()[p]);
			
			final float avgOpponentsElo = (sumElos - pElo) / (devElos.length - 1);
			
			final double expectedWinProb = 1.0 / (1.0 + (Math.pow(10.0, (pElo - avgOpponentsElo) / 400.0)));
			final double expectedUtil = 2.0 * expectedWinProb - 1.0;
			
			// Update Elo rating
			if (p == d.devIdx())
				devElos[p] += 30 * (pUtility - expectedUtil);
			else
				populationElosTable[p].setQuick(d.agentIndices()[p], populationElosTable[p].getQuick(d.agentIndices()[p]) + 30 * (float) (pUtility - expectedUtil));
		}
	}
	
	//-------------------------------------------------------------------------
	
	@Override
	public String generateLog()
	{
		final StringBuilder sb = new StringBuilder();
		
		sb.append("Dev Elos:\n");
		for (int p = 1; p < devElos.length; ++p)
		{
			sb.append("P" + p + ": " + devElos[p] + "\n");
		}
		sb.append("\n");
		
		sb.append("Checkpoint Elos:\n");
		for (int i = 0; i < population.size(); ++i)
		{
			sb.append("Checkpoint " + i + ": ");
			for (int p = 1; p < devElos.length; ++p)
			{
				sb.append(populationElosTable[p].getQuick(i));
				if (p + 1 < devElos.length)
					sb.append(", ");
			}
			sb.append("\n");
		}
		sb.append("\n");
		
		return sb.toString();
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Subclass of DrawnAgentsData; additionally remembers indexes of agents
	 * that were drawn, such that we can correctly update Elo ratings when
	 * trial is done.
	 *
	 * @author Dennis Soemers
	 */
	public static class TournamentDrawnAgentsData extends DrawnAgentsData
	{
		
		/** Player index for which we picked the dev checkpoint */
		private final int devIdx;
		
		/** For every player ID (except devIdx), the index of the checkpoint we used there */
		private final int[] agentIndices;

		/**
		 * Constructor
		 * @param agents
		 * @param devIdx 
		 * @param agentIndices 
		 */
		public TournamentDrawnAgentsData(final List<ExpertPolicy> agents, final int devIdx, final int[] agentIndices)
		{
			super(agents);
			this.devIdx = devIdx;
			this.agentIndices = agentIndices;
		}
		
		/**
		 * @return Player index for which we picked the dev checkpoint
		 */
		public int devIdx()
		{
			return devIdx;
		}
		
		/**
		 * @return For every player ID (except devIdx), the index of the checkpoint we used there
		 */
		public int[] agentIndices()
		{
			return agentIndices;
		}
		
	}
	
	//-------------------------------------------------------------------------

}
