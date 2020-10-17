package search.mcts;

import java.util.List;

import org.json.JSONObject;

import expert_iteration.ExItExperience;
import expert_iteration.ExpertPolicy;
import game.Game;
import game.types.state.GameType;
import main.collections.FVector;
import main.collections.FastArrayList;
import metadata.ai.features.Features;
import policies.softmax.SoftmaxFromMetadata;
import policies.softmax.SoftmaxPolicy;
import search.mcts.backpropagation.Backpropagation;
import search.mcts.finalmoveselection.FinalMoveSelectionStrategy;
import search.mcts.finalmoveselection.MaxAvgScore;
import search.mcts.finalmoveselection.ProportionalExpVisitCount;
import search.mcts.finalmoveselection.RobustChild;
import search.mcts.nodes.BaseNode;
import search.mcts.nodes.Node;
import search.mcts.nodes.OpenLoopNode;
import search.mcts.playout.PlayoutStrategy;
import search.mcts.playout.RandomPlayout;
import search.mcts.selection.AG0Selection;
import search.mcts.selection.SelectionStrategy;
import search.mcts.selection.UCB1;
import util.AI;
import util.Context;
import util.Move;
import util.Trial;
import utils.AIUtils;

/**
 * A modular implementation of Monte-Carlo Tree Search (MCTS) for playing games
 * in Ludii.
 * 
 * @author Dennis Soemers
 */
public class MCTS extends ExpertPolicy
{
	
	//-------------------------------------------------------------------------
	
	/**
	 * Different strategies for initializing Q(s, a) values (or V(s) values of
	 * nodes)
	 * 
	 * @author Dennis Soemers
	 */
	public static enum QInit
	{
		/** 
		 * Give unvisited nodes a very large value 
		 * (actually 10,000 rather than infinity) 
		 */
		INF,
		
		/** 
		 * Estimate the value of unvisited nodes as a loss (-1). This is a 
		 * highly pessimistic value for unvisited nodes, and causes us to rely 
		 * much more on prior distribution. Word on the street is that DeepMind
		 * does this in Alpha(Go) Zero.
		 */
		LOSS,
		
		/** 
		 * Estimate the value of unvisited nodes as a draw (0.0). This causes
		 * us to prioritize empirical wins over unvisited nodes.
		 */
		DRAW,
		
		/**
		 * Estimate the value of unvisited nodes as a win (1). Very similar to
		 * INF, just a bit less extreme.
		 */
		WIN,
		
		/**
		 * Estimate the value of unvisited nodes as the value estimate of the
		 * parent (with corrections for mover).
		 */
		PARENT,
	}
	
	//-------------------------------------------------------------------------
	
	// Basic members of MCTS
	
	/** Root node of the last search process */
	protected BaseNode rootNode = null;
	
	/** Implementation of Selection phase */
	protected SelectionStrategy selectionStrategy;
	
	/** Implementation of Play-out phase */
	protected PlayoutStrategy playoutStrategy;
	
	/** Implementation of Backpropagation of results through the tree */
	protected Backpropagation backpropagation;
	
	/** Algorithm to select move to play in the "real" game after searching */
	protected FinalMoveSelectionStrategy finalMoveSelectionStrategy;
	
	/** Strategy for init of Q-values for unvisited nodes. */
	protected QInit qInit = QInit.PARENT; // TODO allow customisation
	
	/** Flags indicating what data needs to be backpropagated */
	protected int backpropFlags = 0;
	
	/** We'll automatically return our move after at most this number of seconds if we only have one move */
	protected double autoPlaySeconds = 0.0;	// TODO allow customisation
	
	//-------------------------------------------------------------------------
	
	/** State flags of the game we're currently playing */
	protected long currentGameFlags = 0;
	
	/** 
	 * We'll memorize the number of iterations we have executed in our last 
	 * search here 
	 */
	protected int lastNumMctsIterations = -1;
	
	/**
	 * We'll memorize the number of actions we have executed in play-outs
	 * during our last search here
	 */
	protected int lastNumPlayoutActions = -1;
	
	/**
	 * Value estimate of the last move we returned
	 */
	protected double lastReturnedMoveValueEst = 0.0;
	
	/** String to print to Analysis tab of the Ludii app */
	protected String analysisReport = null;
	
	/** 
	 * If true, we preserve our root node after running search. Will increase memory usage,
	 * but allows us to use it to access data afterwards (for instance for training algorithms)
	 */
	protected boolean preserveRootNode = false;
	
	//-------------------------------------------------------------------------
	
	// Following members are related to and/or required because of Tree Reuse
	
	/** 
	 * Whether or not to reuse trees generated in previous 
	 * searches in the same game 
	 */
	protected boolean treeReuse = true;
	
	/** 
	 * Need to memorise this such that we know which parts of the tree to 
	 * traverse to before starting Tree Reuse 
	 */
	protected int lastActionHistorySize = 0;
	
	//-------------------------------------------------------------------------
	
	/** A learned policy to use in Selection phase */
	protected SoftmaxPolicy learnedSelectionPolicy = null;
	
	//-------------------------------------------------------------------------
	
	/** 
	 * Creates standard UCT algorithm, with exploration constant = sqrt(2.0)
	 * @return UCT agent
	 */
	public static MCTS createUCT()
	{
		return createUCT(Math.sqrt(2.0));
	}
	
	/**
	 * Creates standard UCT algorithm with parameter for 
	 * UCB1's exploration constant
	 * 
	 * @param explorationConstant
	 * @return UCT agent
	 */
	public static MCTS createUCT(final double explorationConstant)
	{
		final MCTS uct = 
				new MCTS
				(
					new UCB1(explorationConstant), 
					new RandomPlayout(200),
					new RobustChild()
				);
		
		uct.friendlyName = "UCT";
		
		return uct;
	}
	
	/**
	 * Creates a Biased MCTS agent which attempts to use features and
	 * weights embedded in a game's metadata file.
	 * @param biasPlayouts
	 * @return Biased MCTS agent
	 */
	public static MCTS createBiasedMCTS(final boolean biasPlayouts)
	{
		final SoftmaxPolicy softmax = new SoftmaxFromMetadata();
		final MCTS mcts = 
				new MCTS
				(
					new AG0Selection(), 
					biasPlayouts ? softmax : new RandomPlayout(200),
					new RobustChild()
				);
		
		mcts.setLearnedSelectionPolicy(softmax);
		mcts.friendlyName = biasPlayouts ? "Biased MCTS" : "Biased MCTS (Uniform Playouts)";
		
		return mcts;
	}
	
	/**
	 * Creates a Biased MCTS agent using given collection of features
	 * 
	 * @param features
	 * @param biasPlayouts
	 * @return Biased MCTS agent
	 */
	public static MCTS createBiasedMCTS(final Features features, final boolean biasPlayouts)
	{
		final SoftmaxPolicy softmax = new SoftmaxPolicy(features);
		final MCTS mcts = 
				new MCTS
				(
					new AG0Selection(), 
					biasPlayouts ? softmax : new RandomPlayout(200),
					new RobustChild()
				);
		
		mcts.setLearnedSelectionPolicy(softmax);
		mcts.friendlyName = biasPlayouts ? "Biased MCTS" : "Biased MCTS (Uniform Playouts)";
		
		return mcts;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor with arguments for all strategies
	 * @param selectionStrategy
	 * @param playoutStrategy
	 * @param finalMoveSelectionStrategy
	 */
	public MCTS
	(
		final SelectionStrategy selectionStrategy, 
		final PlayoutStrategy playoutStrategy, 
		final FinalMoveSelectionStrategy finalMoveSelectionStrategy
	)
	{
		this.selectionStrategy = selectionStrategy;
		this.playoutStrategy = playoutStrategy;
		
		backpropFlags = selectionStrategy.backpropFlags();
		
		this.backpropagation = new Backpropagation(backpropFlags);
		this.finalMoveSelectionStrategy = finalMoveSelectionStrategy;
	}
	
	//-------------------------------------------------------------------------

	@Override
	public Move selectAction
	(
		final Game game, 
		final Context context, 
		final double maxSeconds,
		final int maxIterations,
		final int maxDepth
	)
	{
		final long startTime = System.currentTimeMillis();
		long stopTime = (maxSeconds > 0.0) ? startTime + (long) (maxSeconds * 1000) : Long.MAX_VALUE;
		final int maxIts = (maxIterations >= 0) ? maxIterations : Integer.MAX_VALUE;
				
		int numIterations = 0;
		
		// Find or create root node
		if (treeReuse && rootNode != null)
		{
			// Want to reuse part of existing search tree
			
			// Need to traverse parts of old tree corresponding to 
			// actions played in the real game
			final List<Move> actionHistory = context.trial().moves();
			int offsetActionToTraverse = actionHistory.size() - lastActionHistorySize;
			
			if (offsetActionToTraverse < 0)
			{
				// something strange happened, probably forgot to call
				// initAI() for a newly-started game. Won't be a good
				// idea to reuse tree anyway
				rootNode = null;
			}
			
			while (offsetActionToTraverse > 0)
			{
				final Move move = actionHistory.get(actionHistory.size() - offsetActionToTraverse);
				rootNode = rootNode.findChildForMove(move);
				//System.out.println("move to traverse: " + move);
				
				if (rootNode == null)
				{
					// Didn't have a node in tree corresponding to action 
					// played, so can't reuse tree
					break;
				}
								
				--offsetActionToTraverse;
			}
		}
		
		if (rootNode == null || !treeReuse)	
		{
			// Need to create a fresh root
			rootNode = createNode(this, null, null, null, context);
			//System.out.println("NO TREE REUSE");
		}
		else
		{
			//System.out.println("successful tree reuse");
			
			// We're reusing a part of previous search tree
			// Clean up unused parts of search tree from memory
			rootNode.setParent(null);
						
			// TODO in nondeterministic games + OpenLoop MCTS, we'll want to 
			// decay statistics gathered in the entire subtree here
		}
		
		rootNode.rootInit(context);
		
		if (rootNode.numLegalMoves() == 1)
		{
			// play faster if we only have one move available anyway
			if (autoPlaySeconds >= 0.0 && autoPlaySeconds < maxSeconds)
				stopTime = startTime + (long) (autoPlaySeconds * 1000);
		}
		
		lastActionHistorySize = context.trial().moves().size();
		
		lastNumPlayoutActions = 0;
		
		// Search until we have to stop
		while (numIterations < maxIts && System.currentTimeMillis() < stopTime && !wantsInterrupt)
		{
			/*********************
				Selection Phase
			*********************/
			BaseNode current = rootNode;
			current.startNewIteration(context);
			
			while (current.contextRef().trial().status() == null)
			{
				final int selectedIdx = selectionStrategy.select(current);
				BaseNode nextNode = current.childForNthLegalMove(selectedIdx);
				
				final Context newContext = current.traverse(selectedIdx);
				
				if (nextNode == null)
				{
					/*********************
							Expand
					 *********************/
					nextNode = 
							createNode
							(
								this, 
								current, 
								newContext.trial().lastMove(), 
								current.nthLegalMove(selectedIdx), 
								newContext
							);
					current.addChild(nextNode, selectedIdx);
					current = nextNode;
					current.updateContextRef();
					break;	// stop Selection phase
				}
				
				current = nextNode;
				current.updateContextRef();
			}
			
			final Context playoutContext = current.playoutContext();
			Trial endTrial = current.contextRef().trial();
			int numPlayoutActions = 0;
			
			if (!endTrial.over())
			{
				// did not reach a terminal game state yet
				
				/********************************
							Play-out
				 ********************************/
				
				final int numActionsBeforePlayout = current.contextRef().trial().moves().size();
				
				endTrial = playoutStrategy.runPlayout(playoutContext);
				numPlayoutActions = (endTrial.moves().size() - numActionsBeforePlayout);
				
				lastNumPlayoutActions += 
						(playoutContext.trial().moves().size() - numActionsBeforePlayout);
			}
			
			/***************************
				Backpropagation Phase
			 ***************************/
			backpropagation.update(current, playoutContext, AIUtils.agentUtilities(playoutContext), numPlayoutActions);
			
			++numIterations;
		}
		
		lastNumMctsIterations = numIterations;
		
		final Move returnMove = finalMoveSelectionStrategy.selectMove(rootNode);
		
		if (!wantsInterrupt)
		{
			int moveVisits = -1;
			
			for (int i = 0; i < rootNode.numLegalMoves(); ++i)
			{
				final BaseNode child = rootNode.childForNthLegalMove(i);
	
				if (child != null)
				{
					if (rootNode.nthLegalMove(i).equals(returnMove))
					{
						final int mover = rootNode.deterministicContextRef().state().mover();
						moveVisits = child.numVisits();
						lastReturnedMoveValueEst = child.averageScore(mover, rootNode.deterministicContextRef().state());
						break;
					}
				}
			}
			
			final int numRootIts = rootNode.numVisits();
			
			analysisReport = 
					friendlyName + 
					" made move after " +
					numRootIts +
					" iterations (selected child visits = " +
					moveVisits +
					", value = " +
					lastReturnedMoveValueEst +
					").";
		}
		else
		{
			analysisReport = null;
		}
		
		// We can already try to clean up a bit of memory here
		if (!preserveRootNode)
		{
			if (!treeReuse)
			{
				rootNode = null;	// clean up entire search tree
			}
			else if (!wantsInterrupt)	// only clean up if we didn't pause the AI / interrupt it
			{
				rootNode = rootNode.findChildForMove(returnMove);
				
				if (rootNode != null)
				{
					rootNode.setParent(null);
					++lastActionHistorySize;
				}
			}
		}
		
		//System.out.println(numIterations + " MCTS iterations");
		return returnMove;
	}
	
	/**
	 * @param mcts
	 * @param parent
	 * @param parentMove
	 * @param parentMoveWithoutConseq
	 * @param context
	 * @return New node
	 */
	private BaseNode createNode
	(
		final MCTS mcts, 
    	final BaseNode parent, 
    	final Move parentMove, 
    	final Move parentMoveWithoutConseq,
    	final Context context
    )
	{
		if ((currentGameFlags & GameType.Stochastic) == 0L)
		{
			//System.out.println("creating node with parent move: " + parentMove);
			return new Node(mcts, parent, parentMove, parentMoveWithoutConseq, context);
		}
		else
		{
			return new OpenLoopNode(mcts, parent, parentMove, parentMoveWithoutConseq, context.game());
		}
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Sets number of seconds after which we auto-play if we only have one legal move.
	 * @param seconds
	 */
	public void setAutoPlaySeconds(final double seconds)
	{
		autoPlaySeconds = seconds;
	}
	
	/**
	 * Set whether or not to reuse tree from previous search processes
	 * @param treeReuse
	 */
	public void setTreeReuse(final boolean treeReuse)
	{
		this.treeReuse = treeReuse;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @return Flags indicating what data we need to backpropagate
	 */
	public int backpropFlags()
	{
		return backpropFlags;
	}
	
	/**
	 * @return Learned (linear, softmax) policy for Selection phase
	 */
	public SoftmaxPolicy learnedSelectionPolicy()
	{
		return learnedSelectionPolicy;
	}
	
	/**
	 * @return Play-out strategy used by this MCTS object
	 */
	public PlayoutStrategy playoutStrategy()
	{
		return playoutStrategy;
	}
	
	/**
	 * @return Init strategy for Q-values of unvisited nodes
	 */
	public QInit qInit()
	{
		return qInit;
	}
	
	/**
	 * @return Current root node
	 */
	public BaseNode rootNode()
	{
		return rootNode;
	}
	
	/**
	 * Sets the learned policy to use in Selection phase
	 * @param policy
	 */
	public void setLearnedSelectionPolicy(final SoftmaxPolicy policy)
	{
		learnedSelectionPolicy = policy;
	}
	
	/**
	 * Sets the Q-init strategy
	 * @param init
	 */
	public void setQInit(final QInit init)
	{
		qInit = init;
	}
	
	/**
	 * Sets whether we want to preserve root node after running search
	 * @param preserveRootNode
	 */
	public void setPreserveRootNode(final boolean preserveRootNode)
	{
		this.preserveRootNode = preserveRootNode;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @return Number of MCTS iterations performed during our last search
	 */
	public int getNumMctsIterations()
	{
		return lastNumMctsIterations;
	}
	
	/**
	 * @return Number of actions executed in play-outs during our last search
	 */
	public int getNumPlayoutActions()
	{
		return lastNumPlayoutActions;
	}
	
	//-------------------------------------------------------------------------
	
	@Override
	public void initAI(final Game game, final int playerID)
	{
		// store state flags
		currentGameFlags = game.gameFlags();
		
		// reset counters
		lastNumMctsIterations = -1;
		lastNumPlayoutActions = -1;
		
		// reset tree reuse stuff
		rootNode = null;
		lastActionHistorySize = 0;
		
		// instantiate feature sets for selection policy
		if (learnedSelectionPolicy != null)
		{
			learnedSelectionPolicy.initAI(game, playerID);
		}
		
		// may also have to instantiate feature sets for Playout policy if it doubles as an AI
		if (playoutStrategy instanceof AI)
		{
			if (playoutStrategy != learnedSelectionPolicy)
			{
				final AI aiPlayout = (AI) playoutStrategy;
				aiPlayout.initAI(game, playerID);
			}
		}
		
		// reset visualisation stuff
		lastReturnedMoveValueEst = 0.0;
		analysisReport = null;
	}
	
	@Override
	public boolean supportsGame(final Game game)
	{
		final long gameFlags = game.gameFlags();
		
		// this MCTS implementation does not support simultaneous-move games
		if ((gameFlags & GameType.Simultaneous) != 0L)
			return false;
		
		if (learnedSelectionPolicy != null && !learnedSelectionPolicy.supportsGame(game))
			return false;
		
		return playoutStrategy.playoutSupportsGame(game);
	}
	
	@Override
	public double estimateValue()
	{
		return lastReturnedMoveValueEst;
	}
	
	@Override
	public String generateAnalysisReport()
	{
		return analysisReport;
	}
	
	@Override
	public AIVisualisationData aiVisualisationData()
	{
		if (rootNode == null)
			return null;

		if (rootNode.numVisits() == 0)
			return null;

		final int numChildren = rootNode.numLegalMoves();
		final FVector aiDistribution = new FVector(numChildren);
		final FVector valueEstimates = new FVector(numChildren);
		final int mover = rootNode.contextRef().state().mover();
		final FastArrayList<Move> moves = new FastArrayList<>();

		for (int i = 0; i < numChildren; ++i)
		{
			final BaseNode child = rootNode.childForNthLegalMove(i);

			if (child == null)
			{
				aiDistribution.set(i, 0);

				if (rootNode.numVisits() == 0)
					valueEstimates.set(i, 0.f);
				else
					valueEstimates.set(i, (float) rootNode.valueEstimateUnvisitedChildren(mover,
							rootNode.contextRef().state()));
			}
			else
			{
				aiDistribution.set(i, child.numVisits());
				valueEstimates.set(i, (float) child.averageScore(mover, rootNode.contextRef().state()));
			}

			if (valueEstimates.get(i) > 1.f)
				valueEstimates.set(i, 1.f);
			else if (valueEstimates.get(i) < -1.f)
				valueEstimates.set(i, -1.f);

			moves.add(rootNode.nthLegalMove(i));
		}
		
		return new AIVisualisationData(aiDistribution, valueEstimates, moves);
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param json
	 * @return MCTS agent constructed from given JSON object
	 */
	public static MCTS fromJson(final JSONObject json)
	{
		final SelectionStrategy selection = 
				SelectionStrategy.fromJson(json.getJSONObject("selection"));
		final PlayoutStrategy playout = 
				PlayoutStrategy.fromJson(json.getJSONObject("playout"));
		final FinalMoveSelectionStrategy finalMove = 
				FinalMoveSelectionStrategy.fromJson(json.getJSONObject("final_move"));
		final MCTS mcts = new MCTS(selection, playout, finalMove);
		
		if (json.has("tree_reuse"))
		{
			mcts.setTreeReuse(json.getBoolean("tree_reuse"));
		}

		if (json.has("friendly_name"))
		{
			mcts.friendlyName = json.getString("friendly_name");
		}
		
		return mcts;
	}
	
	//-------------------------------------------------------------------------
	
	@Override
	public FastArrayList<Move> lastSearchRootMoves()
	{
		return rootNode.movesFromNode();
	}
	
	@Override
	public FVector computeExpertPolicy(final double tau)
	{
		return rootNode.computeVisitCountPolicy(tau);
	}
	
	@Override
	public ExItExperience generateExItExperience()
	{
		return rootNode.generateExItExperience();
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param lines
	 * @return Constructs an MCTS object from instructions in the 
	 * given array of lines
	 */
	public static MCTS fromLines(final String[] lines)
	{
		// defaults - main parts
		SelectionStrategy selection = new UCB1();
		PlayoutStrategy playout = new RandomPlayout(200);
		FinalMoveSelectionStrategy finalMove = new RobustChild();

		// defaults - some extras
		boolean treeReuse = false;
		SoftmaxPolicy learnedSelectionPolicy = null;
		String friendlyName = "MCTS";

		for (String line : lines)
		{
			final String[] lineParts = line.split(",");

			//-----------------------------------------------------------------
			// main parts
			//-----------------------------------------------------------------
			if (lineParts[0].toLowerCase().startsWith("selection="))
			{
				if (lineParts[0].toLowerCase().endsWith("ucb1"))
				{
					selection = new UCB1();
					selection.customise(lineParts);
				}
				else if (lineParts[0].toLowerCase().endsWith("ag0selection") || 
						lineParts[0].toLowerCase().endsWith("alphago0selection")
						)
				{
					selection = new AG0Selection();
					selection.customise(lineParts);
				}
				else
				{
					System.err.println("Unknown selection strategy: " + line);
				}
			}
			else if (lineParts[0].toLowerCase().startsWith("playout="))
			{
				playout = PlayoutStrategy.constructPlayoutStrategy(lineParts);
			}
			else if (lineParts[0].toLowerCase().startsWith("final_move="))
			{
				if (lineParts[0].toLowerCase().endsWith("maxavgscore"))
				{
					finalMove = new MaxAvgScore();
					finalMove.customize(lineParts);
				}
				else if (lineParts[0].toLowerCase().endsWith("robustchild"))
				{
					finalMove = new RobustChild();
					finalMove.customize(lineParts);
				}
				else if 
				(
					lineParts[0].toLowerCase().endsWith("proportional") || 
					lineParts[0].toLowerCase().endsWith("proportionalexpvisitcount")
				)
				{
					finalMove = new ProportionalExpVisitCount(1.0);
					finalMove.customize(lineParts);
				}
				else
				{
					System.err.println("Unknown final move selection strategy: " + line);
				}
			}
			//-----------------------------------------------------------------
			// extras
			//-----------------------------------------------------------------
			else if (lineParts[0].toLowerCase().startsWith("tree_reuse="))
			{
				if (lineParts[0].toLowerCase().endsWith("true"))
				{
					treeReuse = true;
				}
				else if (lineParts[0].toLowerCase().endsWith("false"))
				{
					treeReuse = false;
				}
				else
				{
					System.err.println("Error in line: " + line);
				}
			}
			else if (lineParts[0].toLowerCase().startsWith(
					"learned_selection_policy="))
			{
				if (lineParts[0].toLowerCase().endsWith("playout"))
				{
					// our playout strategy is our learned Selection policy
					learnedSelectionPolicy = (SoftmaxPolicy) playout;
				}
				else if 
				(
					lineParts[0].toLowerCase().endsWith("softmax") || 
					lineParts[0].toLowerCase().endsWith("softmaxplayout")
				)
				{
					learnedSelectionPolicy = new SoftmaxPolicy();
					learnedSelectionPolicy.customise(lineParts);
				}
			}
			else if (lineParts[0].toLowerCase().startsWith("friendly_name="))
			{
				friendlyName = 
						lineParts[0].substring("friendly_name=".length());
			}
		}

		MCTS mcts = new MCTS(selection, playout, finalMove);

		mcts.setTreeReuse(treeReuse);
		mcts.setLearnedSelectionPolicy(learnedSelectionPolicy);
		mcts.friendlyName = friendlyName;

		return mcts;
	}
	
	//-------------------------------------------------------------------------

}
