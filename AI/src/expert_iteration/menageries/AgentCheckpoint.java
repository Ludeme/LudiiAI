package expert_iteration.menageries;

import java.io.IOException;

import expert_iteration.ExpertPolicy;
import expert_iteration.params.AgentsParams;
import game.Game;
import main.FileHandling;
import main.grammar.Report;
import metadata.ai.features.Features;
import metadata.ai.heuristics.Heuristics;
import metadata.ai.misc.BestAgent;
import policies.softmax.SoftmaxPolicy;
import search.mcts.MCTS;
import search.mcts.finalmoveselection.RobustChild;
import search.mcts.selection.AG0Selection;
import search.minimax.AlphaBetaSearch;
import utils.AIFactory;

/**
 * A checkpoint containing all the data required to reproduce a version of an
 * agent at some point in a training process.
 *
 * @author Dennis Soemers
 */
public class AgentCheckpoint
{
	
	//-------------------------------------------------------------------------
	
	/** Type of agent */
	protected final String agentName;
	
	/** Features metadata (can be null if this checkpoint doesn't use features) */
	protected final Features featuresMetadata;
	
	/** Heuristics metadata (can be null if this checkpoint doesn't use heuristics) */
	protected final Heuristics heuristicsMetadata;
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor
	 * @param agentName
	 * @param featuresMetadata
	 * @param heuristicsMetadata
	 */
	public AgentCheckpoint
	(
		final String agentName,
		final Features featuresMetadata,
		final Heuristics heuristicsMetadata
	)
	{
		this.agentName = agentName;
		this.featuresMetadata = featuresMetadata;
		this.heuristicsMetadata = heuristicsMetadata;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param game
	 * @param agentsParams 
	 * @return An agent generated based on this checkpoint
	 */
	public ExpertPolicy generateAgent(final Game game, final AgentsParams agentsParams)
	{
		final ExpertPolicy ai;
		
		if (agentName.equals("BEST_AGENT"))
		{
			try
			{
				final BestAgent bestAgent = (BestAgent) language.compiler.Compiler.compileObject
						(
							FileHandling.loadTextContentsFromFile(agentsParams.bestAgentsDataDir + "/BestAgent.txt"), 
							"metadata.ai.misc.BestAgent",
							new Report()
						);

				if (bestAgent.agent().equals("AlphaBeta") || bestAgent.agent().equals("Alpha-Beta"))
				{
					ai = new AlphaBetaSearch(agentsParams.bestAgentsDataDir + "/BestHeuristics.txt");
				}
				else if (bestAgent.agent().equals("AlphaBetaMetadata"))
				{
					ai = new AlphaBetaSearch();
				}
				else if (bestAgent.agent().equals("UCT"))
				{
					ai = (ExpertPolicy) AIFactory.createAI("UCT");
				}
				else if (bestAgent.agent().equals("MC-GRAVE"))
				{
					ai = (ExpertPolicy) AIFactory.createAI("MC-GRAVE");
				}
				else if (bestAgent.agent().equals("Biased MCTS"))
				{
					final Features features = (Features) language.compiler.Compiler.compileObject
							(
								FileHandling.loadTextContentsFromFile(agentsParams.bestAgentsDataDir + "/BestFeatures.txt"), 
								"metadata.ai.features.Features",
								new Report()
							);

					ai = MCTS.createBiasedMCTS(features, agentsParams.playoutFeaturesEpsilon);
				}
				else if (bestAgent.agent().equals("Biased MCTS (Uniform Playouts)"))
				{
					final Features features = (Features) language.compiler.Compiler.compileObject
							(
								FileHandling.loadTextContentsFromFile(agentsParams.bestAgentsDataDir + "/BestFeatures.txt"), 
								"metadata.ai.features.Features",
								new Report()
							);

					ai = MCTS.createBiasedMCTS(features, 1.0);
				}
				else if (bestAgent.agent().equals("Biased MCTS (RegPolOpt)"))
				{
					final Features features = (Features) language.compiler.Compiler.compileObject
							(
								FileHandling.loadTextContentsFromFile(agentsParams.bestAgentsDataDir + "/BestFeatures.txt"), 
								"metadata.ai.features.Features",
								new Report()
							);

					ai = MCTS.createRegPolOptMCTS(features, true);
				}
				else if (bestAgent.agent().equals("Biased MCTS (RegPolOpt, Uniform Playouts)"))
				{
					final Features features = (Features) language.compiler.Compiler.compileObject
							(
								FileHandling.loadTextContentsFromFile(agentsParams.bestAgentsDataDir + "/BestFeatures.txt"), 
								"metadata.ai.features.Features",
								new Report()
							);

					ai = MCTS.createRegPolOptMCTS(features, false);
				}
				else if (bestAgent.agent().equals("Random"))
				{
					// Don't wanna train with Random, so we'll take UCT instead
					ai = MCTS.createUCT();
				}
				else
				{
					System.err.println("Unrecognised best agent: " + bestAgent.agent());
					return null;
				}
			}
			catch (final IOException e)
			{
				e.printStackTrace();
				return null;
			}
		}
		else if (agentName.equals("FROM_METADATA"))
		{
			ai = (ExpertPolicy) AIFactory.fromMetadata(game);

			if (ai == null)
			{
				System.err.println("AI from metadata is null!");
				return null;
			}
		}
		else if (agentName.equals("Biased MCTS"))
		{
			final SoftmaxPolicy policy = new SoftmaxPolicy(featuresMetadata, agentsParams.playoutFeaturesEpsilon);
			
			final MCTS mcts = 
					new MCTS
					(
						new AG0Selection(), 
						policy,
						new RobustChild()
					);

			mcts.setLearnedSelectionPolicy(policy);
			mcts.friendlyName = "Biased MCTS";
			ai = mcts;
		}
		else if (agentName.equals("UCT"))
		{
			ai = MCTS.createUCT();
		}
		else
		{
			System.err.println("Cannot recognise expert AI: " + agentsParams.expertAI);
			return null;
		}

		if (ai instanceof MCTS)
		{
			// Need to preserve root node such that we can extract distributions from it
			((MCTS) ai).setPreserveRootNode(true);
		}
		
		return ai;
	}
	
	//-------------------------------------------------------------------------

}
