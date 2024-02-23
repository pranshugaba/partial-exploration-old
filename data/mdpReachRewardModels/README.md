In this repository, we copy over all MDP benchmarks from CAV'19, 
and adapt them so they can be used with MeanPayoffChecker.

                                        original   original      modified  modified    
model                    property       states     transitions   states    transitions  value   status
                                                                                                            
consensus                disagree         272         492         272        492                did not remove non-self loop  transitions
csma-2-2                 some_before     1038        1282        1038       1282                agrees with CAV'19
pacman                   crash            498         620         498        620                agrees with CAV'19
pnueli-zuck-3            live            2701        9981        1985       7233                agrees with CAV'19
rabin-3                  live           27766      137802        2804      12348                agrees with CAV'19
wlan-0                   sent            2954        5202        2954       5202                agrees with CAV'19

zeroconf                 correct_max                                                            agrees with CAV'19

firewire                 deadline                                                               bounded reachability
ij-10                    stable                                                                 bounded reachability
ij-3                     stable                                                                 bounded reachability
philosophers-mdp-3       eat                                                                    bounded reachability
