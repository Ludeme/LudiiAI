<img align="right" src="./resources/ludii-logo-64x64.png">

# Ludii AI Source Code

[![license](https://img.shields.io/github/license/Ludeme/LudiiAI)](LICENSE)
[![release-version](https://img.shields.io/github/release-pre/Ludeme/LudiiAI)](https://github.com/Ludeme/LudiiAI/releases)
[![twitter](https://img.shields.io/twitter/follow/ludiigames?style=social)](https://twitter.com/intent/follow?screen_name=ludiigames)

This repository contains the source code for the built-in AIs of the [Ludii general game system](https://ludii.games/),
as well some of the AI code used for some of the [Digital Ludeme Project publications](http://www.ludeme.eu/outputs/) 
(specifically for the ones that focus on AI playing and/or training).

Note that this source code on its own cannot be directly compiled and/or run; these files import various core Ludii
classes for which source code is not included. The `.class` files for all of these are included in the main
[Ludii JAR download](https://ludii.games/download.php). We do hope that this AI source code may be useful for:

1. Clarifying any implementation details / providing a reference implementation for some of our AI publications.
2. Providing more sophisticated and advanced examples for AI implementations that can be used in Ludii (for 
simple examples, we refer to the dedicated [Ludii Example AI repository](https://github.com/Ludeme/LudiiExampleAI)
and the [Ludii tutorials](https://ludiitutorials.readthedocs.io)).

## Included AI Algorithms

The following AI algorithms are included:
- **Random**.
- **Flat Monte-Carlo search**.
- **Monte-Carlo Tree Search (MCTS)**, with various options for customisation. This includes a standard UCT,
a PUCT selection phase (as in AlphaZero, albeit with a simpler function approximator for the policy, 
and no value head), GRAVE, tree reuse, etc.
- **Alpha-Beta Search**, with iterative deepening and options for various heuristics. Uses a paranoid
search in games with more than two players, and re-starts with a Max<sup>N</sup> search if there is
still time left and the paranoid search was run to completion.
- **Ludii AI**: an agent that uses hints written in the metadata of game files to automatically select
the "best" agent from all the above (based on our own experiments) for the current game and options.

## Related Papers

> Cameron Browne, Dennis J.N.J. Soemers, and Eric Piette (2019). “[Strategic Features for General Games](http://ceur-ws.org/Vol-2313/)”. 
> In *Proceedings of the 2nd Workshop on Knowledge Extraction from Games (KEG)*, pp. 70–75. 
> [[pdf](http://ceur-ws.org/Vol-2313/KEG_2019_paper_8.pdf)]

The general game features described in this paper are implemented in the [Features](https://github.com/Ludeme/LudiiAI/tree/master/Features)
module. Note that there have been some extensions since the publication of that paper.

> Dennis J.N.J. Soemers, Éric Piette, and Cameron Browne (2019). “[Biasing MCTS with Features for General Games](https://ieeexplore.ieee.org/document/8790141)”. 
> In *2019 IEEE Congress on Evolutionary Computation (CEC 2019)*, pp. 442–449. 
> [[pdf (preprint)](https://arxiv.org/pdf/1903.08942)]

The features and Biased MCTS described in this paper are implemented in the [Features](https://github.com/Ludeme/LudiiAI/tree/master/Features)
and [AI](https://github.com/Ludeme/LudiiAI/tree/master/AI) modules, respectively. Note that the feature discovery and training process
are not yet fully included; we hope to move those over into these modules at a later date.

> Dennis J.N.J. Soemers, Éric Piette, Matthew Stephenson, and Cameron Browne (2019). 
> “[Learning Policies from Self-Play with Policy Gradients and MCTS Value Estimates](https://ieeexplore.ieee.org/document/8848037)”. 
> In *2019 IEEE Conference on Games (COG 2019)*, pp. 329–336.
> [[pdf](http://www.ieee-cog.org/2019/papers/paper_91.pdf)]

The features, MCTS agents and softmax policies used in this paper are implemented in the [Features](https://github.com/Ludeme/LudiiAI/tree/master/Features)
and [AI](https://github.com/Ludeme/LudiiAI/tree/master/AI) modules. Note that the training processes are not yet fully included;
we hope to move those over into these modules at a later date.

> Dennis J.N.J. Soemers, Éric Piette, Matthew Stephenson, and Cameron Browne (2020). 
> “Manipulating the Distributions of Experience used for Self-Play Learning in Expert Iteration”. 
> In 2020 IEEE Conference on Games (CoG 2020), to appear. 
> [[pdf preprint](https://arxiv.org/pdf/2006.00283)]

The features and different MCTS agents used in this paper are implemented in the [Features](https://github.com/Ludeme/LudiiAI/tree/master/Features)
and [AI](https://github.com/Ludeme/LudiiAI/tree/master/AI) modules, respectively. Note that the training processes
are not yet fully included; we hope to move those over into these modules at a later date.

## Contact Info

The preferred method for getting help with troubleshooting, suggesting or
requesting additional functionality, or asking other questions about AI
development for Ludii, is [creating new Issues on the github repository](https://github.com/Ludeme/LudiiAI/issues).
Alternatively, the following email address may be used: `ludii(dot)games(at)gmail(dot)com`.

## Changelog

- 27 July, 2020: Initial publication of this repo, source code matches the public Ludii v1.0.0 release.

## Acknowledgements

This repository is part of the European Research Council-funded Digital Ludeme Project (ERC Consolidator Grant \#771292), being run by Cameron Browne at Maastricht University's Department of Data Science and Knowledge Engineering. 

<a href="https://erc.europa.eu/"><img src="./resources/LOGO_ERC-FLAG_EU_.jpg" title="Funded by the European Research Council" alt="European Research Council Logo" height="384"></a>
