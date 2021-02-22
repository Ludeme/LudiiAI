package expert_iteration.params;

/**
 * Wrapper around params for objective function(s) in training runs.
 *
 * @author Dennis Soemers
 */
public class ObjectiveParams
{
	
	//-------------------------------------------------------------------------
	
	/** If true, we'll train a policy on TSPG objective (see CoG 2019 paper) */
	public boolean trainTSPG;
	
	/** If true, we'll use importance sampling weights based on episode durations for CE-loss */
	public boolean importanceSamplingEpisodeDurations;
	
	/** If true, we'll use extra exploration based on cross-entropy losses */
	public boolean ceExplore;
	
	/** Proportion of exploration policy in our behaviour mix */
	public float ceExploreMix;
	
	/** Discount factor gamma for rewards awarded to CE Explore policy */
	public double ceExploreGamma;
	
	/** If true, our CE Explore policy will not be trained, but remain completely uniform */
	public boolean ceExploreUniform;
	
	/** If true, we ignore importance sampling when doing CE Exploration */
	public boolean noCEExploreIS;
	
	/** If true, we use Weighted Importance Sampling instead of Ordinary Importance Sampling for any of the above */
	public boolean weightedImportanceSampling;
	
	/** If true, we don't do any value function learning */
	public boolean noValueLearning;
	
	/** If true, Biased MCTS will use Act, Search and Learn as described in the MCTS as Regularized Policy Optimization paper */
	public boolean mctsRegPolOpt;
	
	/** If true, weight samples based on the expected improvement in value */
	public boolean expDeltaValWeighting;
	
	/** Minimum per-sample weight when weighting samples based on expected immprovement in value */
	public double expDeltaValWeightingLowerClip;
	
	/** If true, we handle move aliasing by putting the maximum mass among all aliased moves on each of them */
	public boolean handleAliasing;
	
	/** Lambda param for weight decay (~= 2c for L2 regularisation, in absence of momentum) */
	public double weightDecayLambda;
	
	//-------------------------------------------------------------------------

}
