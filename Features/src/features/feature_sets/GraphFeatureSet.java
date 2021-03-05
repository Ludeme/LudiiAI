package features.feature_sets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import features.features.Feature;
import features.instances.FeatureInstance;
import game.Game;
import gnu.trove.list.array.TIntArrayList;
import main.collections.FVector;
import main.collections.FastArrayList;
import util.Move;
import util.state.State;

/**
 * Implementation of Feature Set that organises its feature instances in a 
 * directed graph (instead of a forest).
 *
 * @author Dennis Soemers
 */
public class GraphFeatureSet extends BaseFeatureSet
{
	
	//-------------------------------------------------------------------------
	
	/**
	 * Construct feature set from list of features
	 * @param features
	 */
	public GraphFeatureSet(final List<Feature> features)
	{
		this.features = new Feature[features.size()];
		
		for (int i = 0; i < this.features.length; ++i)
		{
			this.features[i] = features.get(i);
			this.features[i].setFeatureSetIndex(i);
		}
		
//		reactiveInstances = null;
//		proactiveInstances = null;
//		
//		reactiveFeatures = null;
//		proactiveFeatures = null;
	}
	
	/**
	 * Loads a feature set from a given filename
	 * @param filename
	 */
	public GraphFeatureSet(final String filename)
	{
		Feature[] tempFeatures;
		
		//System.out.println("loading feature set from " + filename);
		try (Stream<String> stream = Files.lines(Paths.get(filename))){
			tempFeatures = stream.map(s -> Feature.fromString(s)).toArray(Feature[]::new);
		} 
		catch (final IOException exception) 
		{
			tempFeatures = null;
		}
		
		this.features = tempFeatures;
		
		for (int i = 0; i < features.length; ++i)
		{
			features[i].setFeatureSetIndex(i);
		}
	}
	
	//-------------------------------------------------------------------------

	@Override
	public void init(final Game newGame, final int[] supportedPlayers, final FVector weights)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<TIntArrayList> computeSparseFeatureVectors
	(
		final State state,
		final Move lastDecisionMove,
		final FastArrayList<Move> actions,
		final boolean thresholded
	)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TIntArrayList getActiveFeatureIndices
	(
		final State state,
		final int lastFrom,
		final int lastTo,
		final int from,
		final int to,
		final int player,
		final boolean thresholded
	)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<FeatureInstance> getActiveFeatureInstances
	(
		final State state,
		final int lastFrom,
		final int lastTo,
		final int from,
		final int to,
		final int player
	)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public float computeLogitFastReturn
	(
		final State state,
		final int lastFrom,
		final int lastTo,
		final int from,
		final int to,
		final float autoPlayThreshold,
		final FVector weightVector,
		final int player,
		final boolean thresholded
	)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public FeatureSet createExpandedFeatureSet
	(
		final Game targetGame,
		final FeatureInstance firstFeatureInstance,
		final FeatureInstance secondFeatureInstance
	)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void toFile(final String filepath)
	{
		// TODO Auto-generated method stub
		
	}

}
