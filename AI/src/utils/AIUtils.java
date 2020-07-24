package utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.rng.core.RandomProviderDefaultState;

import game.Game;
import main.collections.FastArrayList;
import main.collections.StringPair;
import util.AI;
import util.Context;
import util.Move;
import util.Trial;

/**
 * Some general utility methods for AI
 * 
 * @author Dennis Soemers
 */
public class AIUtils
{
	
	//-------------------------------------------------------------------------
	
	/**
	 * Constructor
	 */
	private AIUtils()
	{
		// do not instantiate
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param game
	 * @return Constructs and returns a default AI for the given game.
	 */
	public static AI defaultAiForGame(final Game game)
	{
		return new LudiiAI();
	}
	
	/**
	 * @param allMoves List of legal moves for all current players
	 * @param mover Mover for which we want the list of legal moves
	 * @return A list of legal moves for the given mover, extracted from a given
	 * list of legal moves for any mover.
	 */
	public static FastArrayList<Move> extractMovesForMover(final FastArrayList<Move> allMoves, final int mover)
	{
		final FastArrayList<Move> moves = new FastArrayList<Move>(allMoves.size());
		
		for (final Move move : allMoves)
		{
			if (move.mover() == mover)
				moves.add(move);
		}
		
		return moves;
	}
	
	/**
	 * Converts a rank >= 1 into a utility value in [-1, 1].
	 * 
	 * @param rank
	 * @param numPlayers
	 * @return Utility for the given rank
	 */
	public static double rankToUtil(final double rank, final int numPlayers)
	{
		if (numPlayers == 1)
		{
			// a single-player game
			return 2.0 * rank - 1.0;
		}
		else
		{
			// two or more players
			return 1.0 - ((rank - 1.0) * (2.0 / (numPlayers - 1)));
		}
	}
	
	/**
	 * Computes a vector of utility values for all players based on the player
	 * rankings in the given context.
	 * 
	 * For players who do not yet have an established ranking, 0.0 will be returned.
	 * 
	 * For players who do have an established ranking, the utility value will lie
	 * in [-1, 1] based on the ranking; 1.0 for top ranking, -1.0 for bottom ranking,
	 * 0.0 for middle ranking (e.g. second player out of three), etc.
	 * 
	 * The returned array will have a length equal to the number of players plus one,
	 * such that it can be indexed directly by player number.
	 * 
	 * @param context
	 * @return The utilities.
	 */
	public static double[] utilities(final Context context)
	{
		final double[] ranking = context.trial().ranking();
		final double[] utilities = new double[ranking.length];
		final int numPlayers = ranking.length - 1;
		//System.out.println("ranking = " + Arrays.toString(ranking));
		
		for (int p = 1; p < ranking.length; ++p)
		{
			double rank = ranking[p];
			if (numPlayers > 1 && rank == 0.0)
			{
				// looks like a playout didn't terminate yet; assign "draw" ranks
				rank = context.computeNextDrawRank();
			}
			
			utilities[p] = rankToUtil(rank, numPlayers);
		}
		
//		System.out.println("ranking = " + Arrays.toString(ranking));
//		System.out.println("utilities = " + Arrays.toString(utilities));
		
		return utilities;
	}
	
	/**
	 * Computes a vector of utilities, like above, but now for agents. In states
	 * where a player-swap has occurred, the utilities will also be swapped, such
	 * that the utility values can be indexed by original-agent-index rather than
	 * role / colour / player index.
	 * 
	 * @param context
	 * @return The agent utilities.
	 */
	public static double[] agentUtilities(final Context context)
	{
		final double[] utils = utilities(context);
		final double[] agentUtils = new double[utils.length];
		
		for (int p = 1; p < utils.length; ++p)
		{
			agentUtils[p] = utils[context.state().playerToAgent(p)];
		}
		
		return agentUtils;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * @param pair
	 * @return True if the given pair of Strings is recognised as AI-related metadata
	 */
	public static boolean isAIMetadata(final StringPair pair)
	{
		final String key = pair.key();
		
		return (
				// Some basic keywords
				key.startsWith("BestAgent") ||
				key.startsWith("AIMetadataGameNameCheck") ||
				
				// Features
				isFeaturesMetadata(pair) ||
				
				// Heuristics
				isHeuristicsMetadata(pair)
				);
	}
	
	/**
	 * @param pair
	 * @return True if the given pair of Strings is recognised as features-related metadata
	 */
	public static boolean isFeaturesMetadata(final StringPair pair)
	{
		final String key = pair.key();
		return key.startsWith("Features");
	}
	
	/**
	 * @param pair
	 * @return True if the given pair of Strings is recognised as heuristics-related metadata
	 */
	public static boolean isHeuristicsMetadata(final StringPair pair)
	{
		final String key = pair.key();
		
		return (
				// Heuristic transformations
				key.startsWith("DivNumBoardCells") ||
				key.startsWith("DivNumInitPlacement") ||
				key.startsWith("Logistic") ||
				key.startsWith("Tanh") ||
				
				// Heuristic terms
				key.startsWith("CentreProximity") ||
				key.startsWith("CornerProximity") ||
				key.startsWith("CurrentMoverHeuristic") ||
				key.startsWith("Intercept") ||
				key.startsWith("LineCompletionHeuristic") ||
				key.startsWith("Material") ||
				key.startsWith("MobilitySimple") ||
				key.startsWith("OpponentPieceProximity") ||
				key.startsWith("OwnRegionsCount") ||
				key.startsWith("PlayerRegionsProximity") ||
				key.startsWith("PlayerSiteMapCount") ||
				key.startsWith("RegionProximity") ||
				key.startsWith("Score") ||
				key.startsWith("SidesProximity")
				);
	}
	
	//-------------------------------------------------------------------------
	
//	/**
//	 * @param game
//	 * @param gameOptions
//	 * @return All AI metadata relevant for given game with given options
//	 */
//	public static List<StringPair> extractAIMetadata(final Game game, final List<String> gameOptions)
//	{
//		final List<StringPair> metadata = game.metadata();
//		final List<StringPair> relevantAIMetadata = new ArrayList<StringPair>();
//		
//		for (final StringPair pair : metadata)
//		{
//			if (AIUtils.isAIMetadata(pair))
//			{
//				final String key = pair.key();
//				final String[] keySplit = key.split(Pattern.quote(":"));
//				
//				boolean allOptionsMatch = true;
//				if (keySplit.length > 1)
//				{
//					final String[] metadataOptions = keySplit[1].split(Pattern.quote(";"));
//					
//					for (int i = 0; i < metadataOptions.length; ++i)
//					{
//						if (!gameOptions.contains(metadataOptions[i]))
//						{
//							allOptionsMatch = false;
//							break;
//						}
//					}
//				}
//				
//				if (allOptionsMatch)
//				{
//					relevantAIMetadata.add(pair);
//				}
//			}
//		}
//		
//		return relevantAIMetadata;
//	}
	
	/**
	 * @param game
	 * @param gameOptions
	 * @param metadata
	 * @return All features metadata relevant for given game with given options
	 */
	public static List<StringPair> extractFeaturesMetadata
	(
		final Game game, 
		final List<String> gameOptions,
		final List<StringPair> metadata
	)
	{
		final List<StringPair> relevantFeaturesMetadata = new ArrayList<StringPair>();
		
		for (final StringPair pair : metadata)
		{
			if (AIUtils.isFeaturesMetadata(pair))
			{
				final String key = pair.key();
				final String[] keySplit = key.split(Pattern.quote(":"));
				
				boolean allOptionsMatch = true;
				if (keySplit.length > 1)
				{
					final String[] metadataOptions = keySplit[1].split(Pattern.quote(";"));
					
					for (int i = 0; i < metadataOptions.length; ++i)
					{
						if (!gameOptions.contains(metadataOptions[i]))
						{
							allOptionsMatch = false;
							break;
						}
					}
				}
				
				if (allOptionsMatch)
				{
					relevantFeaturesMetadata.add(pair);
				}
			}
		}
		
		return relevantFeaturesMetadata;
	}
	
	/**
	 * @param game
	 * @param gameOptions
	 * @param metadata
	 * @return All heuristics metadata relevant for given game with given options
	 */
	public static List<StringPair> extractHeuristicsMetadata
	(
		final Game game, 
		final List<String> gameOptions,
		final List<StringPair> metadata
	)
	{
		final List<StringPair> relevantHeuristicsMetadata = new ArrayList<StringPair>();
		
		for (final StringPair pair : metadata)
		{
			if (AIUtils.isHeuristicsMetadata(pair))
			{
				final String key = pair.key();
				final String[] keySplit = key.split(Pattern.quote(":"));
				
				boolean allOptionsMatch = true;
				if (keySplit.length > 1)
				{
					final String[] metadataOptions = keySplit[1].split(Pattern.quote(";"));
					
					for (int i = 0; i < metadataOptions.length; ++i)
					{
						if (!gameOptions.contains(metadataOptions[i]))
						{
							allOptionsMatch = false;
							break;
						}
					}
				}
				
				if (allOptionsMatch)
				{
					relevantHeuristicsMetadata.add(pair);
				}
			}
		}
		
		return relevantHeuristicsMetadata;
	}
	
	//-------------------------------------------------------------------------
	
	/**
	 * Saves a CSV file with heuristic scores of all the states encountered in
	 * the given trial.
	 * 
	 * @param origTrial
	 * @param origContext
	 * @param gameStartRNGState
	 * @param file
	 */
	public static void saveHeuristicScores
	(
		final Trial origTrial,
		final Context origContext,
		final RandomProviderDefaultState gameStartRNGState,
		final File file
	)
	{
		System.err.println("saveHeuristicScores() currently not implemented");
//		final Game game = origContext.activeGame();
//		
//		// Collect all the interesting, applicable heuristics for this game
//		final int numPlayers = game.players().count();
//		final List<Component> components = game.equipment().components();
//		final int numComponents = components.size() - 1;
//		final List<Regions> regions = game.equipment().regions();
//		final List<StateHeuristicValue> heuristics = new ArrayList<StateHeuristicValue>();
//		
//		final List<String> heuristicNames = new ArrayList<String>();
//		
//		HeuristicEnsemble ensemble = HeuristicEnsemble.fromMetadata(game, game.metadata());
//		if (ensemble == null)
//			ensemble = HeuristicEnsemble.constructDefaultHeuristics(game);
//		
//		heuristics.add(ensemble);
//		heuristicNames.add("DefaultHeuristic");
//
//		if (CentreProximity.isApplicableToGame(game))
//		{
//			for (int e = 1; e <= numComponents; ++e)
//			{
//				final FVector pieceWeights = new FVector(components.size());
//				pieceWeights.set(e, 1.f);
//				heuristics.add(new CentreProximity(game, pieceWeights));
//				heuristicNames.add("CentreProximity_" + e);
//			}
//		}
//
//		if (CornerProximity.isApplicableToGame(game))
//		{
//			for (int e = 1; e <= numComponents; ++e)
//			{
//				final FVector pieceWeights = new FVector(components.size());
//				pieceWeights.set(e, 1.f);
//				heuristics.add(new CornerProximity(game, pieceWeights));
//				heuristicNames.add("CornerProximity_" + e);
//			}
//		}
//
//		if (CurrentMoverHeuristic.isApplicableToGame(game))
//		{
//			heuristics.add(new CurrentMoverHeuristic());
//			heuristicNames.add("CurrentMoverHeuristic");
//		}
//
//		if (LineCompletionHeuristic.isApplicableToGame(game))
//		{
//			heuristics.add(new LineCompletionHeuristic(game));
//			heuristicNames.add("LineCompletionHeuristic");
//		}
//
//		if (Material.isApplicableToGame(game))
//		{
//			for (int e = 1; e <= numComponents; ++e)
//			{
//				final FVector pieceWeights = new FVector(components.size());
//				pieceWeights.set(e, 1.f);
//				heuristics.add(new Material(game, pieceWeights));
//				heuristicNames.add("Material_" + e);
//			}
//		}
//
//		if (MobilitySimple.isApplicableToGame(game))
//		{
//			heuristics.add(new MobilitySimple());
//			heuristicNames.add("MobilitySimple");
//		}
//		
//		if (OpponentPieceProximity.isApplicableToGame(game))
//		{
//			heuristics.add(new OpponentPieceProximity(game));
//			heuristicNames.add("OpponentPieceProximity");
//		}
//
//		if (OwnRegionsCount.isApplicableToGame(game))
//		{
//			heuristics.add(new OwnRegionsCount(game));
//			heuristicNames.add("OwnRegionsCount");
//		}
//
//		if (PlayerRegionsProximity.isApplicableToGame(game))
//		{
//			for (int p = 1; p <= numPlayers; ++p)
//			{
//				for (int e = 1; e <= numComponents; ++e)
//				{
//					final FVector pieceWeights = new FVector(components.size());
//					pieceWeights.set(e, 1.f);
//					heuristics.add(new PlayerRegionsProximity(game, pieceWeights, p));
//					heuristicNames.add("PlayerRegionsProximity_C" + e + "_P" + p);
//				}
//			}
//		}
//
//		if (PlayerSiteMapCount.isApplicableToGame(game))
//		{
//			heuristics.add(new PlayerSiteMapCount());
//			heuristicNames.add("PlayerSiteMapCount");
//		}
//
//		if (RegionProximity.isApplicableToGame(game))
//		{
//			for (int i = 0; i < regions.size(); ++i)
//			{
//				for (int e = 1; e <= numComponents; ++e)
//				{
//					final FVector pieceWeights = new FVector(components.size());
//					pieceWeights.set(e, 1.f);
//					heuristics.add(new RegionProximity(game, pieceWeights, i));
//					heuristicNames.add("RegionProximity_C" + e + "_R" + i);
//				}
//			}
//		}
//
//		if (Score.isApplicableToGame(game))
//		{
//			heuristics.add(new Score());
//			heuristicNames.add("Score");
//		}
//
//		if (SidesProximity.isApplicableToGame(game))
//		{
//			for (int e = 1; e <= numComponents; ++e)
//			{
//				final FVector pieceWeights = new FVector(components.size());
//				pieceWeights.set(e, 1.f);
//				heuristics.add(new SidesProximity(game, pieceWeights));
//				heuristicNames.add("SidesProximity_" + e);
//			}
//		}
//		
//		// Here we'll store all our heuristic scores
//		final float[][][] scoresMatrix = 
//				new float[1 + origTrial.numMoves() - origTrial.numInitPlace()][numPlayers + 1][heuristics.size()];
//		
//		// Re-play our trial
//		final Trial replayTrial = new Trial(game);
//		final Context replayContext = new Context(game, replayTrial);
//		replayContext.rng().restoreState(gameStartRNGState);
//		game.start(replayContext);
//		
//		// Evaluate our first state
//		for (int i = 0; i < heuristics.size(); ++i)
//		{
//			for (int p = 1; p <= numPlayers; ++p)
//			{
//				scoresMatrix[0][p][i] = heuristics.get(i).computeValue(replayContext, p, -1.f);
//			}
//		}
//		
//		int moveIdx = 0;
//					
//		while (moveIdx < replayTrial.numInitPlace())
//		{
//			++moveIdx;
//		}
//
//		while (moveIdx < origTrial.numMoves())
//		{
//			while (moveIdx < replayTrial.numMoves())
//			{
//				// looks like some actions were auto-applied (e.g. in ByScore End condition)
//				// so we just increment index without doing anything
//				++moveIdx;
//			}
//
//			if (moveIdx == origTrial.numMoves())
//				break;
//
//			final Moves legalMoves = game.moves(replayContext);
//			final List<List<Action>> legalMovesAllActions = new ArrayList<List<Action>>();
//			for (final Move legalMove : legalMoves.moves())
//			{
//				legalMovesAllActions.add(legalMove.getAllActions(replayContext));
//			}
//
//			if (game.mode().mode() == ModeType.Alternating)
//			{
//				Move matchingMove = null;
//				for (int i = 0; i < legalMovesAllActions.size(); ++i)
//				{
//					if (legalMovesAllActions.get(i).equals(origTrial.moves().get(moveIdx).getAllActions(replayContext)))
//					{
//						matchingMove = legalMoves.moves().get(i);
//						break;
//					}
//				}
//
//				if (matchingMove == null)
//				{
//					if (origTrial.moves().get(moveIdx).isPass() && legalMoves.moves().isEmpty())
//						matchingMove = origTrial.moves().get(moveIdx);
//				}
//
//				game.apply(replayContext, matchingMove);
//			}
//			else
//			{
//				// simultaneous-move game
//				// we expect the loaded move to consist of multiple moves,
//				// each of which should match one legal move
//				final List<Action> matchingSubActions = new ArrayList<Action>();
//
//				for (final Action subAction : origTrial.moves().get(moveIdx).actions())
//				{
//					final Move subMove = (Move) subAction;
//
//					Move matchingMove = null;
//					for (int i = 0; i < legalMovesAllActions.size(); ++i)
//					{
//						if (legalMovesAllActions.get(i).equals(subMove.actions()))
//						{
//							matchingMove = legalMoves.moves().get(i);
//							break;
//						}
//					}
//
//					if (matchingMove == null)
//					{
//						if (subMove.isPass() && legalMoves.moves().isEmpty())
//							matchingMove = origTrial.moves().get(moveIdx);
//					}
//
//					matchingSubActions.add(matchingMove);
//				}
//
//				final Move combinedMove = new Move(matchingSubActions);
//				combinedMove.setMover(game.players().count() + 1);
//				game.apply(replayContext, combinedMove);
//			}
//			
//			// Evaluate the state we just reached
//			for (int i = 0; i < heuristics.size(); ++i)
//			{
//				for (int p = 1; p <= numPlayers; ++p)
//				{
//					scoresMatrix[1 + moveIdx - replayTrial.numInitPlace()][p][i] = 
//							heuristics.get(i).computeValue(replayContext, p, -1.f);
//				}
//			}
//
//			++moveIdx;
//		}
//		
//		// Finally, write our CSV file
//		try (final PrintWriter writer = new PrintWriter(file))
//		{
//			// First write our header
//			for (int p = 1; p <= numPlayers; ++p)
//			{
//				for (int i = 0; i < heuristicNames.size(); ++i)
//				{
//					if (i > 0 || p > 1)
//						writer.print(",");
//					
//					writer.print("P" + p + "_" + heuristicNames.get(i));
//				}
//			}
//			
//			writer.println();
//			
//			// Now write all our rows of scores
//			for (int s = 0; s < scoresMatrix.length; ++s)
//			{
//				for (int p = 1; p <= numPlayers; ++p)
//				{
//					for (int i = 0; i < scoresMatrix[s][p].length; ++i)
//					{
//						if (i > 0 || p > 1)
//							writer.print(",");
//						
//						writer.print(scoresMatrix[s][p][i]);
//					}
//				}
//				
//				writer.println();
//			}
//		} 
//		catch (final FileNotFoundException e)
//		{
//			e.printStackTrace();
//		}
	}
	
	//-------------------------------------------------------------------------

}
