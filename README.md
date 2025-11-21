# Producing Syntactically Correct Behavioral Specifications
Both SysMLv1.7b and other (generally textually defined) concrete syntaxes are difficult to difficult to adhere to in the face of under-specification. [^1] Often, this opaqueness leads to a "flash" and "thunder" level of knowledge requirement that is neither present in the prompter or AI agent.  Ironically, many people will go to LLMs in order to generate code to solve problems in the SysML domain, while understanding neither very well.  This repo will benefit those people, for good or ill, but it is meant for those that understand SysML enough to know which tasks are difficult because they are tedious at scale.  Even still, I offer a warning: being able to vibe code simple scripts by accurately specifying your SysML model structure, behavior and requirements is a baseline expectation for the quality of input.  

The work downstream to set up a proper vibe code environment for qualified and experienced Model Based Systems Engineers assumes this.  Otherwise, your assumptions will tend to reveal your ignorance to specifications you should know fairly well.  This pollutes the intended context, forcing the user to fight battles on two fronts at the expense of code quality and potential one-shot prompt execution.  

Implicit to this vibe code setup is that you also inject your baseline corpus of understanding regarding your work environment and MagicDraw APIs.

## Formalized Syntax
It is difficult enough to do Systems Engieering.  Model Based Systems Engineering formalized how we think and talk about systems so humans could understand each other.  In the same way, it is difficult to get LLM-based Agentic AIs to create working code from plain language specifications.  MagicVibe is intended to offer assertions in order to constrain how an LLM produces what humans would call an acceptable result.

## Constraints
Acceptability encompasses verifiability and validation, and assertions are created using validated code snippets to verify assumptions made about why code may not be acceptable.  A lot of the code generated for a groovy macro should work, but some environmental factors (e.g. specific runtime constraints) may obfuscate why specific generated macros end up failing.  This muddies the waters and confuses the LLM/agent, causing it to make up reasons for why things failed in their attempt to resolve the errors it is shown.

MagicVibe assertions may be cross-referenced against specific test cases that show a formerly contra-indicated approach working, when an LLM outputs an assumption not based on fact (e.g. hallucination) that then adversely affects future iterations on a fault premise.  This tendency to be led astray by LLMs are easily caught by experienced programmers, but dependence on a human in the loop to waste cognition recognizing anti-patterns is a waste of time.  Assertions and testcode takes the place of that, and if used a priori to any prompting for code will tend to produce consistently working code according to established _WORKING_ design patterns.

### Lay of the Land
The lib directory contains re-usable code to take care of selecting single elements lazily from a model navigation picker window (SLMNP).
The test directory contains the tests that verify assertions in ASSERTIONS file.
The base macro directory contains, at least, a utility to carry over requirement based relationships from one element to another.  It also contains AssertionsTester.groovy, which loads tests in test directory and runs them in alphabetical order.

### Directions for use
Load a zip of the contents of this repo to your LLM or agent, including this README, and your LLM should now be able to produce decent macros for use in MagicDrawv2022xR2 (at least).

### HOW-TO
Generate assumptions:
Try to make code, and when your code fails, collect the claims that your LLM states as to why it failed in a file called assumptions.
Generate assertions and testCases:
Iterate through the assumptions with your LLM, and ask it if it still believes its previous position, and if so, have it generate code in support of its position.  If you are aware that there is another, possibly better approach, prompt it to make a test case in support of your contra-indication.  Save all tests whether they log pass or fail, iterating on them only to get them to run.  Their intended purpose is to serve as a corpus for valid desing patterns.
Remember that the tests will be called by AssertionsTester, and that it will expect these tests to be in the test directory.  This simplifies test code generation, as you can even get context rot doing enough test cases on ChatGPT5.1.

### ToDo:
Come up with a better way to load lib and test directory files.  Groovy could import packages, but I don't want to go that far and veer into full-blown Java plugin development.
Find some more consistent documentation on NoMagic/Dassault's macro execution environment so I don't have to probe its limitations/mechanisms/best practices in this way.

[^1]: This repository and its contributors are releasing this work under GPLv3 to ensure that works derived from its structure and purpose are communally available.  It is not to say that generated code that utilizes this repository are not also GPLv3, but it is also not not GPLv3.  IANAL.  
