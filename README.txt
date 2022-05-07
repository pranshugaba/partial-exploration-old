
A) Artifact information:

    The artifact is available as a Docker image. Alternatively the tool can be downloaded from gitlab [Refer to Section C] and can be compiled from source code. This 
    can be more useful for developers.
    
    The results of Table 2 and Table 3 from our paper can be reproduced by executing shell scripts either on the Docker image or directly from the source code.
    Section B describes how to run the tool using Docker container and Section C describes the way to run the tool directly from the source code.
    
    Note that Table 1 in our paper is not part of any experimental results. Table 1 contains number of samples required to estimate the rates of an exponential
    distribution associated with a state-action pair in a CTMDP. The values are obtained using Lemma 1.
    
    For generating Table 2 and 3 in our paper, we ran experiments on a desktop machine with an Intel i5 3.2 GHz quad core processor and 16 GB RAM.
    
    ------------------------------------------------------------------------------------------------------------------------------------------------------------
     
B) Running the tool using Docker container:

    This section describes how to use our Docker container to run benchmarks for estimating mean-payoff in a blackbox input MDP model.


B.1) Running bash on Docker image:

    The following instructions show how to run our Docker image and open the bash from which the scripts to find the mean-payoff values of different models can be
    run.
    
    1. Install Docker following the instructions from the official website. For Windows or Mac users: Install Docker Desktop which contains Docker engine within it. At
       the time of writing this, Docker Desktop is in beta mode for linux platform.
    
    2. Once Docker is installed, install our Docker image. Our Docker image can be installed by directly pulling it from Docker hub or by loading the tar file that is
       available with the artifact.
       
       For pulling the image from docker hub use the following command

       sudo docker pull pazhamalaim/partial-exploration-tool:v3
       
       Alternatively if you have the zip version of the artifact, then extract the zip first and then load the partial-exploration.tar Docker image using the
       following command
       
       sudo docker load < partial-exploration
    
       Then run the following command to see the installed images and verify partial-exploration Docker image is present with TAG value as v3.
    
       sudo docker images 
    
    3. Now run our image using the command 
    
       sudo docker run -dit pazhamalaim/partial-exploration-tool:v3
       
    4. A new instance of this image will be running. This can be verified by running
    
       sudo docker ps
       
       The above command lists all the running Docker processes on the system. By default some random name will be allocated to the running image a.k.a container.
       There will be a name associated with the running image "pazhamalaim/partial-exploration-tool:v3".
       
    5. Run the following command to start the bash terminal of the running image.
    
       sudo docker exec -ti #container-name bash
       
       Replace #container-name with the name shown upon running "sudo docker ps". A new bash terminal session will open for the running image.
       
    6. Follow subsection B.2 to compute the mean-payoff of various MDP and CTMDP models. Once the results are generated, type "exit" to close the bash
       terminal. To stop the running image use the following command.
       
       sudo docker stop #container-name
       
       To remove the Docker image from host machine run the following commands.
       
       sudo docker container prune
       sudo docker image rm pazhamalaim/partial-exploration-tool:v3

     ----------------------------------------------------------------------------------------------------------------------------------------------------------------

B.2) Replicating experiment results:

    Scripts are provided to replicate the results of Table 2 and Table 3 from the paper and their respective convergence plots. 

    1. For reproducing Table 2 results execute the following commands,
    
       chmod +x run_mdp_benchmarks.sh
       ./run_mdp_benchmarks.sh
       
       This script computes mean-payoff for various blackbox MDP models. As seen in Table 2, it computes the mean-payoff using both blackbox update and greybox update
       equations. By default this script will run each MDP benchmark once and the experiment is run using 3 threads. It will take around 3 hours to complete its
       execution. The user can change the number of threads as well as the number of experiments performed on each benchmark. Follow step 4, to change these parameters.
       
       Note that the results produced may not exactly be the same as the results shown in our paper. For each benchmark we got the result by taking an average over 10
       experiments. We observed by running multiple times that the mean-payoff for blackbox counter model is estimated in time that can vary between 2 and 42 seconds,
       due to the inherent-randomness of an MDP model.
       
       In Table 2, many MDP benchmarks have value in "Time(s)" column to be "TO", which means Time Out. Algorithm 1 is stopped after 30 mins, and the estimated bounds so
       far is reported in Table 2. Some MDP models which are stopped due to Time Out has very close convergence of lower and upper bounds. For example, sensors model has
       its lower bound as 0.3299 and its upper bound as 0.3513. If the same algorithm is run on a different/faster machine, sensors model might converge before Timeout.

       
    2. Once the script finishes its execution the results will be stored in "./results" directory. A csv file similar to Table 2 will be generated and the
       corresponding plots will also be available in the results directory. The Docker container does not have support to open .csv files and .png files. So copy the
       results directory to host machine by running the following commands.

       First exit the bash terminal of the docker container by running "exit" command.
       
       Then copy the results folder to host machine.
       sudo docker cp #container-name:/home/results/ ./
       
       This will copy the "results" directory into the current working directory in host machine.
       
    3. For reproducing the results of Table 3 follow the above procedure with the script "run_ctmdp_benchmarks.sh". Then copy the directory "ctmdp_results" to the host
       machine. 

       
    4. In the paper, the results shown in Table 2 and Table 3 are obtained by taking average values over 10 experiments for each benchmark. 
       Here the scripts by default will run each benchmark only once. 
              
       To run each benchmark 5 times and to take the average as result, use the following command on step 1.
       
       ./run_mdp_benchmarks.sh -n 5
       
       Also by default the scripts uses 3 threads. Provide a value to "-t" option to change the number of threads. For example, the following command runs each
       benchmark 5 times and it uses 4 threads.
       
       ./run_mdp_benchamrks.sh -n 5 -t 4
       
       The "lscpu" command would give the hardware information about user's machine and the value corresponding to "CPU(s)" is the total number of threads.
       
    5. Generating Table 2 by running each experiment once, using a single thread, will take around 8 hours. By default the script uses 3 threads with which generating
       Table 2 where each benchmark is run only once takes around 3 hours. Similarly, generating Table 3 using a single thread will take around 6 hours, whereas using
       3 threads will reduce the running time to 2 hours.
       
    6. For few MDP and CTMDP models shell scripts have been provided to generate the plots of those models. These shell scripts are named "pacman.sh", "zeroconf.sh",
       "queuingsystem.sh" and "sjs3.sh". They generate plots similar to Figure 1 in the paper. Run any of these scripts and copy the directory "individual_results" to
       host machine, once the shell scripts are done executing. They will have the plots for these benchmarks. Each script will take at most 1 hour to finish
       execution.
       
     -----------------------------------------------------------------------------------------------------------------------------------------------------------------   
       
B.3) Running individual models:

    This section describes how to produce results corresponding to individual benchmarks in Table 2 and Table 3, that is, how to produce results for individual rows
    from these tables.

    Once the bash is up and ready, type ls to see the list of directories. The directory "data" contains MDP and CTMDP models within it. Use the following instructions
    to run any particular model inside the data directory.

    1. For example, the following command will find the mean-payoff of blackbox ij.3.prism model using greybox update equations. [Refer to Section 4, page 12 in our
       paper for more information regarding greybox update equations] 
    
       ./gradlew -p ./ run --args='meanPayoff -m data/models/ij.3.prism --informationLevel BLACKBOX --updateMethod GREYBOX --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --maxSuccessors 2 --iterSample 10000 --outputPath ./result_ij3.txt'
       
       Some of the above used parameters are optional and they have the following default values. "precision" : 0.01, "revisitThreshold" : 6, "errorTolerance" : 0.1,
       "iterSample" : 10000. User can change these values according to their needs. For example specifying errorTolerance to be 0.5, will result in slightly faster
       convergence. The optional arguments can be omitted in which case the default values will be used. The following command also computes mean-payoff of ij3 model
       with the default values of optional parameters. More details on the parameters can be found in Section E. 
       
       ./gradlew -p ./ run --args='meanPayoff -m data/models/ij.3.prism --informationLevel BLACKBOX --updateMethod GREYBOX --maxReward 1 --pMin 0.5 --outputPath ./result_ij3.txt'
    
       The precision, revisitThreshold, errorTolerance, iterSample are parameters which can be changed and one can check the performance of the algorithm. Other
       parameters maxReward, pMin, maxSuccessors are unique to each model and those should not be changed. The output will be written to the file specified in the
       outputPath. For getting the parameters of other models, go to experimentScripts directory using "cd experimentScripts" and open the modelConfigurations python
       file using "cat modelConfigurations.py". It will show a list of configurations for various MDP and CTMDP models. Copy from there and replace it as the args in
       the above command. Add the outputPath parameter at the end of it.
    
    
    2. Run the following command to see the result written on the output file.
    
       cat result_ij3.txt
       
       In the file the first line shows the timestamps and the second and third line shows the lower bound and upper bound recorded at those timestamps respectively.
       
       
    ---------------------------------------------------------------------------------------------------------------------------------------------------------------   
    
B.4) Running new models outside the benchmarks:

       Here we describe how to run a new MDP/CTMDP model that is not part of the list of benchmarks provided.

       To compute mean-payoff for a new model (MDP/CTMDP) that is not already present inside the "data/" directory, first place the model inside either the "data/models"
       directory or "data/ctmdpModels" directory depening on the type of the model. For computing the mean-payoff the tool needs maxReward and pMin parameters. pMin is
       the minimum transition probability in the whole model. The pMin parameter can be found by running a script. For example, assume a new MDP model is placed inside
       "data/models/" directory and is named 'new_model.prism'. Now run the following to compute the pMin of that model.
    
       ./gradlew -p ./ run --args='modelInfo -m /data/models/new_model.prism'
       
       This will output the minimum transition probability pMin of that model. Use that info to execute step 1. The maxReward parameter can be found by going
       through the rewards module of the 'new_models.prism' file. If there are multiple reward models and uninitialized constants in the prism file, use the following
       command,
       
       ./gradlew -p ./ run --args='modelInfo -m /data/models/new_model.prism -r #reward-module-name -c #constantValues'
       
       Say 'new_models.prism' has two reward modules "r1" and "r2" and it also has an uninitialized constant K. Then the following command will use the reward module
       "r1" and assign a value of 5 to K.
       
       ./gradlew -p ./ run --args='modelInfo -m /data/models/new_model.prism -r r1 -c K=5'
       
       An MDP model can either be WHITEBOX or BLACKBOX or GREYBOX and it can be specified using the "--informationLevel" parameter. Similary update equations can either
       be BLACKBOX or GREYBOX. This can be specified using "--updateMethod" parameter. The following example shows how to compute mean-payoff for the blackbox variant
       of the new MDP model "new_model.prism" after its pMin and maxRewards. Let's assume pMin as 0.5 and maxReward as 1.
       
       ./gradlew -p ./ run --args='meanPayoff -m data/models/new_model.prism --informationLevel BLACKBOX --updateMethod GREYBOX --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --iterSample 10000 --outputPath ./result_new_model.txt'
       
    -------------------------------------------------------------------------------------------------------------------------------------------------------------------

C) Running Tool from source code:
    
    Dependencies: Java 11, Python3, numpy, matplotlib
    
    1. Cloning: Clone the source code of the tool using
    
       git clone -b mean-payoff-ctmdp https://gitlab.lrz.de/i7/partial-exploration.git/
    
    2. Populate the submodule lib/models from the partial-exploration directory by
    
       git submodule update --init --recursive 
    
    3. Change directory to prism by running
    
       cd lib/models/lib/prism/prism
    
    4. Run "make" to build
    
    5. Change directory to partial-exploration using 
    
       cd ../../../../..
    
    6. Compile the program using ./gradlew compileJava.
    
    For replicating the experiment results, follow the instructions in Section B.2, except for the copying part (sudo docker cp). The corresponding results folders will
    be created in the root directory of the project itself.
    
    For running individual models follow the instructions provided in Section B.3 and B.4. 
 
    -----------------------------------------------------------------------------------------------------------------------------------------------------------------   

D) Source code details:

    Algorithm 1 implemented in our paper can be traced by following the implementation of "run" function in class "OnDemandValueIterator.java". This class can be found
    in path "/src/main/java/de.tum.in.pet/implementation/meanPayoff". For blackbox model, this class is extended and implemented in "BlackOnDemandValueIterator.java".
    BlackOnDemandValueIterator.java also contains other functions like "updateMec" which implements "UPDATE_MEC_VALUE" procedure in Algorithm 1 and "update" function
    which implements both the "UPDATE" and "DEFLATE" procedures from Algorithm 1. 
    
    The implementation of the SIMULATE procedure used in Algorithm 1 can be found in "BlackExplorer.java" which is located in "/lib/models/src/main/java/de/tum/in
    /probmodels/explorer/" inside function simulateMecRepeatedly2. The procedure "FIND_MECS" in Algorithm 1 is used in "handleComponents" function in
    BlackOnDemandValueIterator.java
    
    The top level algorithm for computing mean-payoff for blackbox CTMDP models can be found in the class "CTMDPBlackOnDemandValueIterator.java", which can be found in
    path "/src/main/java/de.tum.in.pet/implementation/meanPayoff". 
    
    -----------------------------------------------------------------------------------------------------------------------------------------------------------------
    

E) MeanPayoff parameter descriptions:

    Following are the parameters that can be specified while computing mean-payoff for a MDP/CTMDP model as described in Section B.3.
    
     PARAMETER           REQUIRED       DEFAULT VALUE                    DESCRIPTION
    
    -m, --model          [REQUIRED]         NA                  :    Path to a specific model
    
    -c, --constants      [Optional*]         NA                 :    Values to constants specified in model file. E.g [ --const K=5,L=2  ]
    
    --rewardModule       [Optional]       First module          :    A prism MDP file can have multiple reward modules defined within it. While computing for mean-payoff
                                                                     a specific reward module needs to be specified. If none is specified then first available module
                                                                     will be used.
    
    --informationLevel   [Optional]        WHITEBOX             :    One of the values from [WHITEBOX, BLACKBOX, GREYBOX]. Section 2, Definition 3 of our paper
                                                                     describes these models.
    
    --updateMethod       [Optional]        GREYBOX              :    One of the values from [BLACKBOX, GREYBOX]. In Section 4 of our paper under heading "Updating mean
                                                                     meanpayoff values", blackbox update equation is provided.
                              
    --pMin               [REQUIRED]         NA                  :    Minimum Transition Probability in the model. This can be found by following the instructions in
                                                                     Step 3 of Section B.3 
                                                                     
    --maxReward          [REQUIRED]         NA                  :    Maximum reward in the input model over all states
    
    --precision          [Optional]         1.0e-6              :    Required precision on the estimated mean-payoff. Described in Section 3.1 in our paper. Precision
                                                                     should be in range [0, 1].
    
    --errorTolerance     [Optional]         0.1                 :    The probaility with which errors in estimating mean-payoff within the precision can be tolerated.
                                                                     Equivalent to MP-Inconfidence which is described in Section 3.1 in our paper.
    
    --revisitThreshold   [Optional]          6                  :    Number of times a state has to be visited in a single episodic run, to consider it to be a part of
                                                                     an MEC
                                                                     
    --iterSamples        [Optional]         10000               :    Refers to "n" in Line 5 of Algorithm 1 of our paper; This is the number of episodic runs before the
                                                                     difference between lower bound and upper bound is checked in Algorithm 1.
    
    
    
    
    [Optional*] --> indicates that if there are any uninitialized constants in the prism file, then this parameter has to be provided with argument being the value of
                    uninitialized constants. If the prism file contains no uninitialized constants, then this parameter can be omitted.
    
    -------------------------------------------------------------------------------------------------------------------------------------------------------------
    


