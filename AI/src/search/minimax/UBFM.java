package search.minimax;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import game.Game;
import gnu.trove.list.array.TLongArrayList;
import main.FileHandling;
import main.collections.FVector;
import main.collections.FastArrayList;
import main.grammar.Report;
import metadata.ai.heuristics.Heuristics;
import metadata.ai.heuristics.terms.HeuristicTerm;
import metadata.ai.heuristics.terms.Material;
import metadata.ai.heuristics.terms.MobilityAdvanced;
import other.RankUtils;
import other.context.Context;
import other.context.TempContext;
import other.move.Move;
import other.state.State;
import other.trial.Trial;
import training.expert_iteration.ExItExperience;
import training.expert_iteration.ExItExperience.ExItExperienceState;
import training.expert_iteration.ExpertPolicy;
import utils.data_structures.ScoredMove;
import utils.data_structures.transposition_table.TranspositionTableUBFM;
import utils.data_structures.transposition_table.TranspositionTableUBFM.UBFMTTData;

/**
 * Implementation of Unbounded Best-First Minimax
 * (as described in Learning to Play Two-Player Perfect-Information Games
 * without Knowledge by Quentin Cohen-Solal (2021))
 * (chunks of code copied from AlphaBetaSearch, especially the variables initialisation)
 * 
 * The implementation completely relies on the transposition table so it is not optional.
 * 
 * note that by default the transposition table is cleared each turn, if 
 * it is not the case then it keeps growing infinitely
 * 
 * @author cyprien
*/

public class UBFM extends ExpertPolicy
{
	
	/** Boolean to activate additional outputs for debugging */
	public boolean debugDisplay = false;
	
	/** Set to true to store a description of the search tree in the treeSaveFile.
	 * The file will contain a list where each item is a tuple representing a node in the form (nodeHashes, heuristicScore, player).
	 * nodeHashes is itself a tuple containing the sequence of the hash codes that leads to the node.
	 * heuristicScore is the evaluation of the state with the heuristics (unless the state is terminal)
	 */
	public boolean savingSearchTreeDescription = false;
	public String treeSaveFile = "/home/cyprien/Documents/M1/Internship/search_trees_raw/default.sav"; //FIXME

	/** If true, each exploration will be continued up to the end of the tree. */
	protected boolean fullPlayouts = false;
	
	/** Set to true to reset the TT after each move (usually only for tree search display) */
	public boolean resetTTeachTurn = true;

	/** Value of epsilon if a randomised policy is picked (default is epsilon-greedy) */
	protected double selectionEpsilon = 0.1;
	
	/** 
	 * If set to an integer, the AI will always play for the same maximising player. This is useful when using 
	 * the same AI to play for an opponent with the same transposition table
	 */
	protected Integer forcedMaximisingPlayer = null;
	
	//-------------------------------------------------------------------------
	
	/** A type for the selection policy */
	public enum SelectionPolicy
	{
		BEST, // picks the move of the current principal path (the one with the best score)
		SAFEST // variant to pick the move that was explored the most
	}
	
	/** Selection policy used: */
	protected SelectionPolicy selectionPolicy = SelectionPolicy.SAFEST;
	
	/** A type for the exploration policy */
	public enum ExplorationPolicy
	{
		BEST, // always picks the move that seems the best
		EPSILON_GREEDY, // with a probability epsilon, picks a uniformly random move, else picks the best
		// to add: softmax
	}
	
	/** Exploration policy used: */
	protected ExplorationPolicy explorationPolicy = ExplorationPolicy.EPSILON_GREEDY;
	
	//-------------------------------------------------------------------------

	/** Value we use to initialise alpha ("negative infinity", but not really) */
	public static final float ALPHA_INIT = -1000000.f;
	
	/** Value we use to initialise beta ("positive infinity", but not really) */
	public static final float BETA_INIT = -ALPHA_INIT;
	
	/** Score we give to winning opponents in paranoid searches in states where game is still going (> 2 players) */
	public static final float PARANOID_OPP_WIN_SCORE = 10000.f;
	
	/** We skip computing heuristics with absolute weight value lower than this */
	public static final float ABS_HEURISTIC_WEIGHT_THRESHOLD = 0.001f;
	
	//-------------------------------------------------------------------------
	
	/** Our heuristic value function estimator */
	protected Heuristics heuristicValueFunction = null;
	
	/** If true, we read our heuristic function to use from game's metadata */
	final boolean heuristicsFromMetadata;
	
	/** We'll automatically return our move after at most this number of seconds if we only have one move */
	protected final double autoPlaySeconds = 0.3;
	
	/** Estimated score of the root node based on last-run search */
	protected float estimatedRootScore = 0.f;
	
	//-------------------------------------------------------------------------

	/** The maximum heuristic eval we have ever observed */
	protected float maxHeuristicEval = ALPHA_INIT;
	
	/** The minimum heuristic eval we have ever observed */
	protected float minHeuristicEval = BETA_INIT;
	
	/** String to print to Analysis tab of the Ludii app */
	protected String analysisReport = null;
	
	/** Current list of moves available in root */
	protected FastArrayList<Move> currentRootMoves = null;
	
	/** The last move we returned. Need to memorise this for Expert Iteration with AlphaBeta */
	protected Move lastReturnedMove = null;
	
	/** Root context for which we've last performed a search */
	protected Context lastSearchedRootContext = null;
	
	/** Value estimates of moves available in root */
	protected FVector rootValueEstimates = null;
	
	/** The number of players in the game we're currently playing */
	protected int numPlayersInGame = 0;
	
	/** Needed for visualisations */
	protected final float rootAlphaInit = ALPHA_INIT;
	
	/** Needed for visualisations */
	protected final float rootBetaInit = BETA_INIT;
	
	/** Transposition Table */
	protected TranspositionTableUBFM transpositionTable = null;
	
	//-------------------------------------------------------------------------
	
	/** Maximum depth of the analysis performed, for an analysis report */
	protected int maxDepthReached;
	
	/** Number of different states evaluated, for an analysis report */
	protected int nbStatesEvaluated;
	
	/** Scores of the moves from the root, for the final decision of the move to play */
	protected float[] rootMovesScores;
	
	/** numBitsPrimaryCode argument given when a TT is created (to avoid magic numbers in the code)*/
	protected final int numBitsPrimaryCodeForTT = 12;
	
	//-------------------------------------------------------------------------
	
	public StringBuffer searchTreeOutput = new StringBuffer();
	
	/** Number of calls of the recursive MinimaxBFS function since the last call of selectAction */
	protected int callsOfMinimax = 0;
	
	/** Sum of the entries in the TT at each turn, assuming the TT is reset each turn, for debug display */
	protected int totalNumberOfEntriesTT;
	
	//-------------------------------------------------------------------------

	/**
	 * Creates a standard unbounded best-first minimax searcher.
	 * @return UBFM agent
	 */
	public static UBFM createUBFM()
	{
		return new UBFM();
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor
	 */
	public UBFM()
	{
		friendlyName = "UBFM";
		heuristicsFromMetadata = true;
	}
	
	/**
	 * Constructor
	 * @param heuristicsFilepath
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	public UBFM(final String heuristicsFilepath) throws FileNotFoundException, IOException
	{
		final String heuristicsStr = FileHandling.loadTextContentsFromFile(heuristicsFilepath);
		heuristicValueFunction = (Heuristics)compiler.Compiler.compileObject
										(
											heuristicsStr, 
											"metadata.ai.heuristics.Heuristics",
											new Report()
										);
		heuristicsFromMetadata = false;
		friendlyName = "UBFM";
	}

	/**
	 * Constructor
	 * @param heuristics
	 */
	public UBFM(final Heuristics heuristics)
	{
		heuristicValueFunction = heuristics;
		heuristicsFromMetadata = false;
		friendlyName = "UBFM";
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
		maxDepthReached = 0;
		nbStatesEvaluated = 0;
		callsOfMinimax = 0;
		
		lastReturnedMove = BFSSelection(game, context, (maxSeconds >= 0) ? maxSeconds : Double.MAX_VALUE, maxIterations);

		return lastReturnedMove;
	}
	
	/**
	 * Decides the move to play from the root.
	 * @param rootTableData
	 * @param maximising
	 * @return
	 */
	protected ScoredMove finalDecision(final UBFMTTData rootTableData, boolean maximising)
	{
		switch (selectionPolicy)
		{
			case BEST:
				return rootTableData.sortedScoredMoves.get(0);
				
			case SAFEST:
				ScoredMove scoredMove;
				if (debugDisplay) 
				{
					System.out.print("sortedScoredMoves:\n(");
					for (int i=0; i<rootTableData.sortedScoredMoves.size(); i++)
					{
						scoredMove = rootTableData.sortedScoredMoves.get(i);
						System.out.print(Integer.toString(i)+": score "+Float.toString(scoredMove.score)+" ("+Integer.toString(scoredMove.nbVisits)+"); ");
					}
					System.out.println(")");
				}
				
				ScoredMove safestScoredMove = rootTableData.sortedScoredMoves.get(0);
				for (int i=0; i<rootTableData.sortedScoredMoves.size(); i++)
				{
					scoredMove = rootTableData.sortedScoredMoves.get(i);
					if
					(
						(scoredMove.nbVisits > safestScoredMove.nbVisits)
						|| 
						(
							(scoredMove.nbVisits == safestScoredMove.nbVisits)
							&& 
							( 
								(  maximising  && (scoredMove.score > safestScoredMove.score))
								|| 
								((!maximising) && (scoredMove.score < safestScoredMove.score))
							)
						)
					)
					{
						safestScoredMove = scoredMove;
					}
				}
				return safestScoredMove;
				
			default:
				System.err.println("Error: selectionPolicy not implemented");
				return rootTableData.sortedScoredMoves.get(0);
		}
	}
	
	/**
	 * Performs the unbounded best-first search algorithm.
	 * @param game
	 * @param context
	 * @param maxSeconds
	 * @param iterationLimit (set to -1 for no limit)
	 * @return
	 */
	protected Move BFSSelection
	(
		final Game game,
		final Context context,
		final double maxSeconds,
		final int iterationLimit
	)
	{
		final long startTime = System.currentTimeMillis();
		long stopTime = (maxSeconds > 0.0) ? startTime + (long) (maxSeconds * 1000) : Long.MAX_VALUE;

		currentRootMoves = new FastArrayList<Move>(game.moves(context).moves());
		
		final int numRootMoves = currentRootMoves.size();
		final State state = context.state();
		final int mover = state.playerToAgent(state.mover());
		
		final int maximisingPlayer;
		if (forcedMaximisingPlayer == null)
			maximisingPlayer = context.state().playerToAgent(context.state().mover());
		else
			maximisingPlayer = forcedMaximisingPlayer.intValue();

		if (!transpositionTable.isAllocated())
		{
			transpositionTable.allocate();
			// For visualisation purpose:
			// 0 is always a possible value because it is the value of a draw
			minHeuristicEval = 0f;
			maxHeuristicEval = 0f;
		}
		
		if (numRootMoves == 1)
		{
			// play faster if we only have one move available anyway
			if (autoPlaySeconds >= 0.0 && autoPlaySeconds < maxSeconds)
				stopTime = startTime + (long) (autoPlaySeconds * 1000);
		}
		
		// Vector for visualisation purposes
		rootValueEstimates = new FVector(numRootMoves);
		rootMovesScores = new float[numRootMoves];
		
		// To ouput a visual graph of the search tree:
		searchTreeOutput.setLength(0);
		searchTreeOutput.append("[\n");

		long zobrist = context.state().fullHash(context);
		final Context contextCopy = copyContext(context);
		TLongArrayList initialnodeHashes = new TLongArrayList();
		initialnodeHashes.add(zobrist);
		if (savingSearchTreeDescription)
			searchTreeOutput.append("("+stringOfNodeHashes(initialnodeHashes)+","+Float.toString(getContextValue(context,maximisingPlayer,initialnodeHashes,0))+","+Integer.toString((mover==maximisingPlayer)? 1:2)+"),\n");
		
		int iterationCount = 0;
		final int maxNbIterations = (iterationLimit>0)? iterationLimit : Integer.MAX_VALUE;
		while ((iterationCount == 0) || (System.currentTimeMillis() < stopTime && ( !wantsInterrupt) && (iterationCount < maxNbIterations)))
		{
			// Calling the recursive minimaxBFS:
			float minimaxResult = minimaxBFS(contextCopy, maximisingPlayer, stopTime, 1, initialnodeHashes);

			estimatedRootScore = (float) scoreToValueEst(minimaxResult, rootAlphaInit, rootBetaInit);
			iterationCount += 1;
		}
		
		final UBFMTTData rootTableData = transpositionTable.retrieve(zobrist);
		final ScoredMove finalChoice = finalDecision(rootTableData, mover==maximisingPlayer);
		
		analysisReport = friendlyName + " (player " + maximisingPlayer + ") completed an analysis that reached at some point a depth of " + maxDepthReached + ":\n";
		analysisReport += "best value observed at root "+Float.toString(finalChoice.score)+",\n";
		analysisReport += Integer.toString(nbStatesEvaluated)+" different states were evaluated\n";
		analysisReport += 
				String.format
				(
					"%d iterations, with %d calls of minimax", 
					Integer.valueOf(iterationCount), 
					Integer.valueOf(callsOfMinimax)
				);
		if ((maxSeconds > 0.) && (System.currentTimeMillis()<stopTime))
			analysisReport += " (finished analysis early) ";

		
		if (debugDisplay)
		{
			int entriesTTthisTurn = transpositionTable.nbEntries(); //assuming TT is reset each turn
			totalNumberOfEntriesTT += entriesTTthisTurn;
			System.out.println("Nb of entries in the TT this turn: "+entriesTTthisTurn+" (total: "+totalNumberOfEntriesTT+")");
//			transpositionTable.dispValueStats();
		}
		
		if (resetTTeachTurn)
		{
			transpositionTable.deallocate();
			if (debugDisplay)
				System.out.println("deallocated");
		}
		
		if (debugDisplay)
		{
			System.out.print("rootValueEstimates: (");
			for (int i=0; i<currentRootMoves.size(); i++)
			{
				System.out.print(rootValueEstimates.get(i)+".");
			}
			System.out.println(")");
		}
		
		// To ouput a visual graph of the search tree:
		searchTreeOutput.append("]");
		if (savingSearchTreeDescription)
		{
			try
			{
		      @SuppressWarnings("resource")
			FileWriter myWriter = new FileWriter(treeSaveFile);
		      myWriter.write(searchTreeOutput.toString());
		      myWriter.close();
		      System.out.println("Successfully saved search tree in a file.");
		    } 
			catch (IOException e) 
			{
		      System.out.println("An error occurred.");
		      e.printStackTrace();
		    }
		}
		
		return finalChoice.move;
	}
	
	/**
	 * Recursive strategy to evaluate the different options on a possible line of actions.
	 * 
	 * @param context
	 * @param maximisingPlayer
	 * @param stopTime
	 * @param analysisDepth
	 * @param nodeHashes : list of hashes of states that lead to this state, used to avoid loops and when saving the search tree
	 * @return the score of the context
	 */
	protected float minimaxBFS
	(
		final Context context,
		final int maximisingPlayer,
		final long stopTime,
		final int analysisDepth,
		final TLongArrayList nodeHashes
	)
	{
		final Trial trial = context.trial();
		final State state = context.state();
		final Game game = context.game();
		final int mover = state.playerToAgent(state.mover());
		
		final FastArrayList<Move> legalMoves = game.moves(context).moves();
		final int numLegalMoves = legalMoves.size();
		
		callsOfMinimax += 1;
		if (analysisDepth > maxDepthReached)
			maxDepthReached = analysisDepth;
		
		/** 
		 * First we check if the state is terminal (at least for maximising player). 
		 * If so we can just return the value of the state for maximising player
		 */
		if (trial.over() || !context.active(maximisingPlayer))
			return getContextValue(context, maximisingPlayer, nodeHashes, analysisDepth-1);
		else if (savingSearchTreeDescription) // we call getContextValue to make sure the node is added to the search tree
			getContextValue(context, maximisingPlayer, nodeHashes, analysisDepth);

		List<ScoredMove> sortedScoredMoves = null;
		final long zobrist = context.state().fullHash(context);
		final UBFMTTData tableData = transpositionTable.retrieve(zobrist);
		if (tableData != null)
			if (tableData.sortedScoredMoves != null)
				sortedScoredMoves = new ArrayList<ScoredMove>(tableData.sortedScoredMoves);
		
		float outputScore = Float.NaN; // this value should always be replaced before it is read
		
		if (sortedScoredMoves != null)
		{
			if (sortedScoredMoves.size() != numLegalMoves)
			{
				System.err.println("Error sortedScoredMoves.size() != numLegalMoves");
				sortedScoredMoves = null;
			}
		}

		boolean firstExploration = false;
		if (sortedScoredMoves == null)
		{
			/**----------------------------------------------------------------
			 * In this case it is the first full analysis of this state.
			 * Thus we compute a quick evaluation of all the possible moves to order them before exploration.
			 *-----------------------------------------------------------------
			*/
			firstExploration = true;
			
			final FVector moveScores = estimateMovesValues(legalMoves, context, maximisingPlayer, nodeHashes, analysisDepth, stopTime);

			// Create a shuffled version of list of moves indices (random tie-breaking)
			final FastArrayList<ScoredMove> tempScoredMoves = new FastArrayList<ScoredMove>(numLegalMoves);
			for (int i=0; i<numLegalMoves; i++)
			{
				tempScoredMoves.add(new ScoredMove(legalMoves.get(i), moveScores.get(i), 1));
			}
			sortedScoredMoves = new ArrayList<ScoredMove>(numLegalMoves);
			for (int i=0; i<numLegalMoves; i++)
			{
				sortedScoredMoves.add(tempScoredMoves.removeSwap(ThreadLocalRandom.current().nextInt(tempScoredMoves.size())));
			}
			if (mover == maximisingPlayer)
				Collections.sort(sortedScoredMoves); //(the natural order of scored Move Indices is decreasing)
			else
				Collections.sort(sortedScoredMoves, Collections.reverseOrder());

			if (analysisDepth==1)
			{
				for (int k=0; k<numLegalMoves; k++)
					rootValueEstimates.set(k,(float) scoreToValueEst(moveScores.get(k), rootAlphaInit, rootBetaInit));
			}
			
			outputScore = sortedScoredMoves.get(0).score;
		}
		
		if ((!firstExploration) || (fullPlayouts && !wantsInterrupt && (System.currentTimeMillis() < stopTime)))
		{
			/**----------------------------------------------------------------
			 * If we already explored this state (or if fullPlayout is true), then we will recursively explore the most promising move
			 * at this state.
			 * ----------------------------------------------------------------
			*/
			
			final int indexPicked;
			switch (explorationPolicy)
			{
			case BEST:
				indexPicked = 0;
				break;
			case EPSILON_GREEDY:
				if (ThreadLocalRandom.current().nextDouble(1.)<selectionEpsilon)
					indexPicked = ThreadLocalRandom.current().nextInt(numLegalMoves);
				else
					indexPicked = 0;
				break;
			default:
				throw new RuntimeException("Unkown exploration policy");
			}
			
			final Move bestMove = sortedScoredMoves.get(indexPicked).move;
			final int previousNbVisits = sortedScoredMoves.get(indexPicked).nbVisits; //number of times this moves was already tried
			
			final Context contextCopy = copyContext(context);
			game.apply(contextCopy, bestMove);
			
			final long newZobrist = contextCopy.state().fullHash(contextCopy);
			
			final float scoreOfMostPromisingMove;
			if ((nodeHashes.size() > 100) && (nodeHashes.contains(newZobrist)))
			{
				// security against infinite loops
				// those should only happen if both players prefer avoiding the game to change,
				// so we score this path as a draw
				if (debugDisplay)
					System.out.println("security against infinite loops activated ");
				
				scoreOfMostPromisingMove = 0f;
			}
			else
			{
				nodeHashes.add(newZobrist);
				
				/** Recursive call: */
				scoreOfMostPromisingMove = minimaxBFS(contextCopy, maximisingPlayer, stopTime, analysisDepth+1, nodeHashes);			
				
				nodeHashes.removeAt(nodeHashes.size()-1);
			}
			
			// Re-inserting the new value in the list of scored moves, last among moves of equal values			
			int k = indexPicked; //TODO: put this in a funtion
			while ((k < numLegalMoves-1))
			{
				if
				(
					((sortedScoredMoves.get(k+1).score >= scoreOfMostPromisingMove) && (mover == maximisingPlayer))
					||
					((sortedScoredMoves.get(k+1).score <= scoreOfMostPromisingMove) && (mover != maximisingPlayer))
				)
				{
					sortedScoredMoves.set(k, sortedScoredMoves.get(k+1));
					k += 1;
				}
				else
				{
					if (k > 0)
					{
						if
						(
							((sortedScoredMoves.get(k-1).score < scoreOfMostPromisingMove) && (mover == maximisingPlayer))
							||
							((sortedScoredMoves.get(k-1).score > scoreOfMostPromisingMove) && (mover != maximisingPlayer))
						)
						{
							sortedScoredMoves.set(k, sortedScoredMoves.get(k-1));
							k -= 1;
						}
						else
							break;
					}
					else
						break;
				}
			}
			sortedScoredMoves.set(k, new ScoredMove(bestMove, scoreOfMostPromisingMove, previousNbVisits+1));

			if (analysisDepth==1)
				rootValueEstimates.set(currentRootMoves.indexOf(bestMove),(float) scoreToValueEst(scoreOfMostPromisingMove, rootAlphaInit, rootBetaInit));
			
			outputScore = sortedScoredMoves.get(0).score;
			
		}
		
		
		// Updating the transposition table at each call:
		transpositionTable.store(zobrist, outputScore, analysisDepth-1, TranspositionTableUBFM.EXACT_VALUE, sortedScoredMoves);

		return outputScore;
	}

	/**
	 * Compute scores for the moves in argument
	 * 
	 * @param legalMoves
	 * @param context
	 * @param maximisingPlayer
	 * @param nodeHashes : used when we want to output the tree-search graph
	 * @param depth : stored in the transposition table (but unused for the moment)
	 * @param stopTime
	 * @return a vector with the scores of the moves
	 */
	protected FVector estimateMovesValues
	(
		final FastArrayList<Move> legalMoves,
		final Context context,
		final int maximisingPlayer,
		final TLongArrayList nodeHashes,
		final int depth,
		final long stopTime
	)
	{
		final int numLegalMoves = legalMoves.size();
		final Game game = context.game();
		final State state = context.state();
		final int mover = state.playerToAgent(state.mover());
		
		final FVector moveScores = new FVector(numLegalMoves);
		
		if (savingSearchTreeDescription)
			getContextValue(context, maximisingPlayer, nodeHashes, depth-1); //to make sure we recorded the visit of the parent node
		
		for (int i = 0; i < numLegalMoves; ++i)
		{			
			final Move m = legalMoves.get(i);
			final Context contextCopy = new TempContext(context);
			
			game.apply(contextCopy, m);

			nodeHashes.add(contextCopy.state().fullHash(contextCopy));
			final float heuristicScore = getContextValue(contextCopy, maximisingPlayer, nodeHashes, depth);
			nodeHashes.removeAt(nodeHashes.size()-1);
			
			moveScores.set(i,heuristicScore);

			// If this process is taking to long we abort the process and give the worst possible score (+-1) to the moves not evaluated:
			if (System.currentTimeMillis() >= stopTime || ( wantsInterrupt))
			{
				for (int j=i+1; j<numLegalMoves; j++)
					moveScores.set(j, mover == maximisingPlayer ? -BETA_INIT + 1 : BETA_INIT - 1);
				break;
			}
		}
		
		return moveScores;
	}
	
	/** 
	 * Method to evaluate a state, with heuristics if the state is not terminal.
	 * 
	 * @param context
	 * @param maximisingPlayer
	 * @param nodeHashes : : used when we want to output the tree-search graph
	 * @param depth : stored in the transposition table (but unused for the moment)
	 * @return
	 */
	protected float getContextValue
	(
		final Context context,
		final int maximisingPlayer,
		final TLongArrayList nodeHashes,
		final int depth // just used  to fill the depth field in the TT which is not important
	)
	{
		boolean valueRetrievedFromMemory = false;
		float heuristicScore = 0;
		final long zobrist = context.state().fullHash(context);
		final State state = context.state();
		
		final UBFMTTData tableData;
		
		tableData = transpositionTable.retrieve(zobrist);
			
		if (tableData != null)
		{
			// Already searched for data in TT, use results
			switch(tableData.valueType)
			{
			case TranspositionTableUBFM.EXACT_VALUE:
				heuristicScore = tableData.value;
				valueRetrievedFromMemory = true;
				break;
			case TranspositionTableUBFM.INVALID_VALUE:
				System.err.println("INVALID TRANSPOSITION TABLE DATA: INVALID VALUE");
				break;
			default:
				// bounds are not used up to this point
				System.err.println("INVALID TRANSPOSITION TABLE DATA: UNKNOWN");
				break;
			}
		}
		
		// Only compute heuristicScore if we didn't have a score registered in the TT
		if (!valueRetrievedFromMemory) {
			if (context.trial().over() || !context.active(maximisingPlayer))
			{
				// terminal node (at least for maximising player)
				heuristicScore = (float) RankUtils.agentUtilities(context)[maximisingPlayer] * BETA_INIT;
			}
			else {
				heuristicScore = heuristicValueFunction().computeValue(
						context, maximisingPlayer, ABS_HEURISTIC_WEIGHT_THRESHOLD);
				
				for (final int opp : opponents(maximisingPlayer))
				{
					if (context.active(opp))
						heuristicScore -= heuristicValueFunction().computeValue(context, opp, ABS_HEURISTIC_WEIGHT_THRESHOLD);
					else if (context.winners().contains(opp))
						heuristicScore -= PARANOID_OPP_WIN_SCORE;
				}
				
				minHeuristicEval = Math.min(minHeuristicEval, heuristicScore);
				maxHeuristicEval = Math.max(maxHeuristicEval, heuristicScore);	
			}
			
			// Every time a state is evaluated, we store the value in the transposition table (worth?)
			transpositionTable.store(zobrist, heuristicScore, depth, TranspositionTableUBFM.EXACT_VALUE, null);
			
			// Invert scores if players swapped (to check)
			if (context.state().playerToAgent(maximisingPlayer) != maximisingPlayer)
				heuristicScore = -heuristicScore;

			nbStatesEvaluated += 1;
		}

		if (savingSearchTreeDescription)
		{
			final int newMover = state.playerToAgent(state.mover());
			searchTreeOutput.append("("+stringOfNodeHashes(nodeHashes)+","+Float.toString(heuristicScore)+","+((newMover==maximisingPlayer)? 1:2)+"),\n");
		}
		
		return heuristicScore;
	}
	
	//-------------------------------------------------------------------------

	/**
	 * Converts a score into a value estimate in [-1, 1]. Useful for visualisations.
	 * 
	 * @param score
	 * @param alpha 
	 * @param beta 
	 * @return Value estimate in [-1, 1] from unbounded (heuristic) score.
	 */
	public double scoreToValueEst(final float score, final float alpha, final float beta)
	{
		if (score <= alpha+10)
			return -1.0;
		
		if (score >= beta-10)
			return 1.0;
		
		// Map to range [-0.8, 0.8] based on most extreme heuristic evaluations
		// observed so far.
		return -0.8 + (0.8 - -0.8) * ((score - minHeuristicEval) / (maxHeuristicEval - minHeuristicEval));
	}
	
	//-------------------------------------------------------------------------
	/**
	 * @param player
	 * @return Opponents of given player
	 */
	public int[] opponents(final int player)
	{
		final int[] opponents = new int[numPlayersInGame - 1];
		int idx = 0;
		
		for (int p = 1; p <= numPlayersInGame; ++p)
		{
			if (p != player)
				opponents[idx++] = p;
		}
		
		return opponents;
	}
	
	/**
	 * Initialising the AI (almost the same as with AlphaBeta)
	 */
	@Override
	public void initAI(final Game game, final int playerID)
	{
		
		if (heuristicsFromMetadata)
		{
			if (debugDisplay) System.out.println("Reading heuristics from game metadata...");
			
			// Read heuristics from game metadata
			final metadata.ai.Ai aiMetadata = game.metadata().ai();
			if (aiMetadata != null && aiMetadata.heuristics() != null)
			{
				heuristicValueFunction = Heuristics.copy(aiMetadata.heuristics());
			}
			else
			{
				// construct default heuristic
				heuristicValueFunction = new Heuristics(new HeuristicTerm[]{
						new Material(null, Float.valueOf(1.f), null, null),
						new MobilityAdvanced(null, Float.valueOf(0.001f))
				});
			}
		}
		
		if (heuristicValueFunction() != null)
			heuristicValueFunction().init(game);
		
		// reset these things used for visualisation purposes
		estimatedRootScore = 0.f;
		maxHeuristicEval = 0f;
		minHeuristicEval = 0f;
		analysisReport = null;
		
		currentRootMoves = null;
		rootValueEstimates = null;
		
		totalNumberOfEntriesTT = 0;
		
		// and these things for ExIt
		lastSearchedRootContext = null; //always null, so useless?
		lastReturnedMove = null;
		
		numPlayersInGame = game.players().count();
		
		transpositionTable = new TranspositionTableUBFM(numBitsPrimaryCodeForTT);
	}
	
	@Override
	public boolean supportsGame(final Game game)
	{
		if (game.isStochasticGame())
			return false;
		
		if (game.hiddenInformation())
			return false;
		
		if (game.hasSubgames())		// Cant properly init most heuristics
			return false;
		
		if (!(game.isAlternatingMoveGame()))
			return false;
		
		return true;
	}
	
	@Override
	public double estimateValue()
	{
		return scoreToValueEst(estimatedRootScore, rootAlphaInit, rootBetaInit);
	}
	
	@Override
	public String generateAnalysisReport()
	{
		return analysisReport;
	}
	
	@Override
	public AIVisualisationData aiVisualisationData()
	{
		if (currentRootMoves == null || rootValueEstimates == null)
			return null;
		
		final FVector aiDistribution = rootValueEstimates.copy();
		aiDistribution.subtract(aiDistribution.min());
	
		return new AIVisualisationData(aiDistribution, rootValueEstimates, currentRootMoves);
	}
	
	public Heuristics heuristicValueFunction() 
	{
		return heuristicValueFunction;
	}
	
	// ------------------------------------------------------------------------

	@Override
	public FastArrayList<Move> lastSearchRootMoves()
	{
		final FastArrayList<Move> moves = new FastArrayList<Move>(currentRootMoves.size());
		for (final Move move : currentRootMoves)
		{
			moves.add(move);
		}
		return moves;
	}

	@Override
	public FVector computeExpertPolicy(final double tau)
	{
		final FVector distribution = FVector.zeros(currentRootMoves.size());
		distribution.set(currentRootMoves.indexOf(lastReturnedMove), 1.f);
		distribution.softmax();
		return distribution;
	}
	
	@Override
	public List<ExItExperience> generateExItExperiences()
	{
		final FastArrayList<Move> actions = new FastArrayList<Move>(currentRootMoves.size());
		for (int i = 0; i < currentRootMoves.size(); ++i)
		{
			final Move m = new Move(currentRootMoves.get(i));
			m.setMover(currentRootMoves.get(i).mover());
    		m.then().clear();	// Can't serialise these, and won't need them
    		actions.add(m);
		}
		
    	final ExItExperience experience =
    			new ExItExperience
    			(
    				new Context(lastSearchedRootContext),
    				new ExItExperienceState(lastSearchedRootContext),
    				actions,
    				computeExpertPolicy(1.0),
    				FVector.zeros(actions.size()),
    				1.f
    			);
    	
    	return Arrays.asList(experience);
	}

	//-------------------------------------------------------------------------
	
	/**
	 * Sets the selection policy used for the final decision of the move to play.
	 * @param s
	 */
	public void setSelectionPolicy(final SelectionPolicy s)
	{
		selectionPolicy = s;
	}
	
	/**
	 * Sets if we want the AI to fully explore one path at each iteration of the algorithm (Descent UBFM).
	 * @param b
	 */
	public void setIfFullPlayouts(final boolean b)
	{
		fullPlayouts = b;
	}
	
	/**
	 * Sets the epsilon value (randomisation parameter) of the best first search.
	 * @param value
	 */
	public void setSelectionEpsilon(final float value)
	{
		selectionEpsilon = value;
	}
	
	/**
	 * Sets if we want the Transposition Table to be reset at each call of selectAction.
	 * @param value
	 */
	public void setTTReset(final boolean value)
	{
		resetTTeachTurn = value;
	}
	
	public TranspositionTableUBFM getTranspositionTable()
	{
		return transpositionTable;
	}
	
	/**
	 * Sets if we want the maximisingPLayer to remain the same regardless of whose turn it is when selectAction is called.
	 * @parama player
	 */
	public void forceAMaximisingPlayer(Integer player)
	{
		forcedMaximisingPlayer = player;
	}
	
	//-------------------------------------------------------------------------
	
	public static String stringOfNodeHashes(TLongArrayList nodeHashes)
	{
		String res = "(";
		
		for (int i=0; i<nodeHashes.size(); ++i)
		{
			res += Long.toString(nodeHashes.get(i));
			res += ",";
		}
		res += ")";
		
		return res;
	}
}
