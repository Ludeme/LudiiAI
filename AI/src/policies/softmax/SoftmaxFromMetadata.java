package policies.softmax;

import java.util.ArrayList;
import java.util.List;

import features.FeatureSet;
import function_approx.LinearFunction;
import game.Game;
import game.types.play.RoleType;
import metadata.ai.features.Features;

/**
 * A Softmax Policy that can automatically initialise itself by
 * using the features embedded in a game's metadata.
 * 
 * @author Dennis Soemers
 */
public class SoftmaxFromMetadata extends SoftmaxPolicy
{
	
	/**
	 * Constructor
	 */
	public SoftmaxFromMetadata()
	{
		friendlyName = "Softmax Policy (features from Game metadata)";
	}

	@Override
	public void initAI(final Game game, final int playerID)
	{
		final List<FeatureSet> featureSetsList = new ArrayList<FeatureSet>();
		final List<LinearFunction> linFuncs = new ArrayList<LinearFunction>();
		
		final Features featuresMetadata = game.metadata().ai().features();
		
		for (final metadata.ai.features.FeatureSet featureSet : featuresMetadata.featureSets())
		{
			if (featureSet.role() == RoleType.Shared)
				addFeatureSetWeights(0, featureSet.featureStrings(), featureSet.featureWeights(), featureSetsList, linFuncs);
			else
				addFeatureSetWeights(featureSet.role().owner(), featureSet.featureStrings(), featureSet.featureWeights(), featureSetsList, linFuncs);
		}
		
		this.featureSets = featureSetsList.toArray(new FeatureSet[featureSetsList.size()]);
		this.linearFunctions = linFuncs.toArray(new LinearFunction[linFuncs.size()]);
		this.playoutActionLimit = 200;
		
		super.initAI(game, playerID);
	}
	
	@Override
	public boolean supportsGame(final Game game)
	{
		// We support any game with features in metadata
		return (game.metadata().ai() != null && game.metadata().ai().features() != null);
	}
	
}
