In this repository, we copy over all MDP benchmarks from CAV'19, 
and adapt them so they can be used with MeanPayoffChecker.

consensus                disagree            agrees with CAV'19
csma-2-2                 some_before         agrees with CAV'19
firewire                 deadline            bounded reachability
ij-10                    stable              bounded reachability
ij-3                     stable              bounded reachability
pacman                   crash               agrees with CAV'19
philosophers-mdp-3       eat                 bounded reachability
pnueli-zuck-3            live                (need to make target states absorbing)
rabin-3                  live                agrees with CAV'19
wlan-0                   sent                agrees with CAV'19
zeroconf                 correct_max         agrees with CAV'19