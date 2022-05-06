
A) Artifact information:

    The artifact is available as a Docker image. Alternatively the tool can be run directly from the source code.
    
    The results of Table 2 and Table 3 from our paper can be reproduced by executing shell scripts either on the Docker image or directly from the source code.
    Section B describes how to run the tool using docker container and Section C describes the way to run the tool directly from source code.
    
    Note that Table 1 is not part of any experimental results. Table 1 contains number of samples required to estimate the rates of an exponential distribution
    associated with a state-action pair in a CTMDP. The values are obtained using Lemma 1.
     
B) Running the tool using Docker container:

    This section describes how to use our Docker container to run benchmarks for estimating mean-payoff in a blackbox input Markov decision process.


B.1) Running bash on Docker image:

    The following instructions show how to run our Docker image and open the bash from which the scripts to find the mean-payoff values of different models can be
    run.    
    
    1. Install Docker following the instructions from the official website. For Windows or Mac users: Install Docker Desktop which contains Docker engine within it. At
       the time of writing this, Docker Desktop is in beta mode for linux platform.
    
    2. Once Docker is installed, open the terminal and execute the following command. 

       sudo docker pull pazhamalaim/partial-exploration-tool:v2
    
       This command will download our docker image locally into the user's system. Then run the following command to see the installed images.
    
       sudo docker images 
    
    3. Now run our image using the command 
    
       sudo docker run -dit pazhamalaim/partial-exploration-tool:v2
       
    4. A new instance of this image will be running. This can be verified by running
    
       sudo docker ps
       
       The above command lists all the running docker processes on the system.
       
    5. By default some random name will be allocated to the running image a.k.a container. This information can be found in the table that is shown by running the
       following command:
    
       sudo docker ps 
       
       There will be a name associated with the running image "pazhamalaim/partial-exploration-tool:v2".
       
    6. Run the following command to start the bash terminal of the running image.
    
       sudo docker exec -ti #container-name bash
       
       Replace #container-name with the name shown upon running "sudo docker ps -a". A new bash terminal session will open for the running image.
       
    7. Follow subsection B.2 to compute the mean-payoff of various MDP and CTMDP models. Once the results are generated, type "exit" to close the bash
       terminal. To stop the running image use the following command.
       
       sudo docker stop #container-name
       
       To remove the docker image from host machine run the following commands.
       
       sudo docker container prune
       sudo docker image rm pazhamalaim/partial-exploration-tool:v2


B.2) Replicating experiment results:

    Scripts are provided to replicate the results of Table 2 and Table 3 from the paper and their respective convergence plots. 

    1. For reproducing Table 2 results execute the following commands,
    
       chmod +x run_mdp_benchmarks.sh
       ./run_mdp_benchmarks.sh
       
       This script computes mean-payoff for various blackbox mdp models. As seen in Table 2, it computes the mean-payoff using both blackbox update and greybox update
       equations. By default this script will run each mdp benchmark once and it uses 3 threads. It will take around 2.5 hours to complete its execution. The user can
       change the number of threads as well as the number of experiments performed on each benchmark. Follow step 4, to change these parameters.
       
       Note that the results produced may not be exactly the same as the results shown in our paper. For each benchmark we got the result by taking an average over 10
       experiments. We observed by running multiple times that the mean-payoff for counter model is produced in as minimum as 2 seconds to as maximum as 42 seconds for
       blackbox with blackbox update configuration. Here in the above script, each benchmark is run only once by default. The number of experiments can be provided as
       an argument to the script as described in Step 4. 
       
       In Table 2, many MDP benchmarks have value in "Time(s)" column to be "TO", which means Time Out. Algorithm 1 is stopped after 30 mins, and the estimated bounds so
       far is reported in Table 2. Some MDP models which are stopped due to Time Out has very close convergence of lower and upper bounds. For example, sensors model has
       its lower bound as 0.3299 and its upper bound as 0.3513. If the same algorithm is run on a different/faster machine, sensors model might get converged before Time
       Out itself.

       
    2. Once the scripts finishes its execution the results will be stored in "./results" directory. A csv file similar to Table 2 will be generated and the
       corresponding plots will also be available in the results directory. The docker container does not have support to open .csv files and .png files. So copy the
       results directory to host machine by running the following commands.

       First exit the bash terminal of the docker container by running "exit" command.
       
       Then copy the results folder to host machine.
       sudo docker cp #container-name:/home/results/ ./
       
       This will copy the "results" directory into current working directory in host machine.
       
    3. For reproducing the results of Table 3 follow the above procedure with the script "run_ctmdp_benchmarks.sh". Then copy the directory "ctmdp_results" to the host
       machine. 

       
    4. In the paper, the results shown in Table 2 and Table 3 are obtained by taking average values among 10 experiments for each benchmark. 
       Here the scripts by default will run each benchmark only once. 
              
       To run each benchmark 5 times and to take the average as result, use the following command on step 1.
       
       ./run_mdp_benchmarks.sh -n 5
       
       Also by default the scripts uses 3 threads. Provide a value to "-t" option to change the number of threads. For example the following command runs each
       benchmark 5 times and it uses 4 threads.
       
       ./run_mdp_benchamrks.sh -n 5 -t 4
       
       "lscpu" would give the hardware information about user's machine and the value corresponding to "CPU(s)" is the total number of threads.
       
    5. Generating Table 2 by running each experiment once, using a single thread, will take around 8 hours. By default the script uses 3 threads with which generating
       Table 2 where each benchmark is run only once takes around 2.5 hours. Similarly generating Table 3 using a single thread will take around 6 hours, whereas using
       3 threads will reduce the running time to 2 hours.
       
    6. For few MDP and CTMDP models shell scripts have been provided to generate the plots of those models. These shell scripts are named "pacman.sh", "zeroconf.sh",
       "queuingsystem.sh" and "sjs3.sh". They generate plots similar to Figure 1 in the paper. Run any of these scripts and copy the directory "individual_results" to
       host machine, once the shell scripts are done executing. They will have the plots for these benchmarks. Each script will take at most 1 hour to finish
       execution.
       
       
B.3) Running individual models:

    Once the bash is up and ready, type ls to see the list of directories. The directory "data" contains MDP and CTMDP models within it. Use the following instructions
    to run any particular model inside the data directory.

    1. For example, the following command will find the mean-payoff of blackbox ij.3.prism model using greybox equations. 
    
       ./gradlew -p ./ run --args='meanPayoff -m data/models/ij.3.prism --informationLevel BLACKBOX --updateMethod GREYBOX --precision 0.01 --maxReward 1 --revisitThreshold 6 --errorTolerance 0.1 --pMin 0.5 --maxSuccessors 2 --iterSample 10000 --outputPath ./result_ij3.txt'
    
    The precision, revisitThreshold, errorTolerance, iterSample are parameters which can be changed and one can check the performance of the algorithm. Other parameters
    maxReward, pMin, maxSuccessors are unique to each model and those should not be changed. The output will be written to the file specified in the outputPath. For
    getting the parameters of other models, go to experimentScripts directory using "cd experimentScripts" and open the modelConfigurations python file using "cat
    modelConfigurations.py". It will show a list of configurations for various MDP and CTMDP models. Copy from there and replace it as the args in the above command.Add
    the outputPath parameter at the end of it.
    
    
    2. Run the following command to see the result written on the output file.
    
       cat result_ij3.txt
       
       In the file the first line shows the timestamps and the second and third line shows the lower bound and upper bound recorded at those timestamps respectively.
       
    
    3. To compute mean-payoff for a model (MDP/CTMDP) that is not already present inside the "data/" directory, first place the model inside either the "data/models"
       directory or "data/ctmdpModels" directory depening on the type of the model. For computing the mean-payoff the tool needs maxReward and pMin. pMin is the minimum
       transition probability in the whole model. maxSuccessors is an optional parameter which can be ignored. pMin can be found by running a script. For example assume
       a new MDP model is placed inside "data/models/" directory and is named new_model.prism. Now do the following to compute the pMin of that model.
    
       ./gradlew -p ./ run --args='modelInfo -m /data/models/new_model.prism'
       
       This will output the minimum transition probability pMin of that model. Use that info to execute step 1. maxRewards parameter can be found by manually going
       through the rewards module of the 'new_models.prism' file. If there are multiple reward models and undefined constants in the prism file, use the following
       command,
       
       ./gradlew -p ./ run --args='modelInfo -m /data/models/new_model.prism -r #reward-module-name -c #constantValues'
       
       Say 'new_models.prism' has two reward modules "r1" and "r2" and it also has an undefined constant K. Then the following will use the reward module "r1" and
       assign a value of 5 to K.
       
       ./gradlew -p ./ run --args='modelInfo -m /data/models/new_model.prism -r r1 -c K=5'
       


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
    
    For replicating the experiment results, follow the instructions in section B.2 and section B.3, except for the copying part. The corresponding results folder will
    be created in the root directory of the project itself.
    
    
    

D) Source code details:

    Algorithm 1 implemented in our paper can be traced by following the implementation of "run" function in class "OnDemandValueIterator.java". This class can be found
    in path "/src/main/java/de.tum.in.pet/implementation/meanPayoff". This procedure computes mean-payoff for whitebox mdp models. For blackbox mdp and ctmdp models,
    this algorithm is extended and implemented in "BlackOnDemandValueIterator.java" and "CTMDPBlackOnDemandValueIterator.java". BlackOnDemandValueIterator.java also
    contains other functions like "updateMec" which implements "UPDATE_MEC_VALUE" procedure in Algorithm 1 and "update" function which implements both the "UPDATE" and
    "DEFLATE" procedures from Algorithm 1. 
    
    The implementation of the SIMULATE procedure used in Algorithm 1 can be found in "BlackExplorer.java" which is located in "/lib/models/src/main/java/de/tum/in
    /probmodels/explorer/" inside function simulateMecRepeatedly2. The procedure "FIND_MECS" in Algorithm 1 is used in "handleComponents" function in
    BlackOnDemandValueIterator.java


