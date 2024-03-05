# mdp_max_rewards = {"zeroconf_rewards": 1, "sensors": 1, "investor": 1, "cs_nfail3": 1, "consensus.2": 1,
#                    "ij.10": 1, "ij.3": 1, "pacman": 1, "pnueli-zuck.3": 1, "wlan.0": 1, "virus": 1,
#                    "phil-nofair3": 1, "blackjack": 1.5, "counter": 10, "recycling": 2, "busyRing4": 1, "busyRingMC4": 1}

mdp_max_rewards = {
    "consensus.2-rewards": 1,
    "csma.2-2-rewards": 1,
    "pacman-rewards": 1,
    "pnueli-zuck.3-rewards": 1,
    "rabin-3-rewards": 1,
    "wlan.0-rewards": 1,
    "zeroconf-rewards": 1,
}



#ctmdp_max_rewards = {"DynamicPM-tt_3_qs_2_sctmdp": 1, "DynamicPM-tt_3_qs_6_sctmdp": 1,
#                     "ErlangStages-k500_r10": 1, "ErlangStages-k2000_r10": 1,
#                     "PollingSystem-jt1_qs1_sctmdp": 1, "PollingSystem-jt1_qs4_sctmdp": 1,
#                     "PollingSystem-jt1_qs7_sctmdp": 1, "QueuingSystem-lqs_1_rqs_1_jt_2_sctmdp": 1,
#                     "QueuingSystem-lqs_2_rqs_2_jt_3_sctmdp": 1, "SJS-procn_2_jobn_2_sctmdp": 1,
#                     "SJS-procn_2_jobn_6_sctmdp": 1, "SJS-procn_3_jobn_5_sctmdp": 1,
#                     "SJS-procn_6_jobn_2_sctmdp": 1, "toy": 1}


def get_max_reward(model_name):
    if model_name in mdp_max_rewards:
        return mdp_max_rewards[model_name]

    if model_name in ctmdp_max_rewards:
        return ctmdp_max_rewards[model_name]

    return None
