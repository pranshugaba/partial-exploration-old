mdp_true_values = {"zeroconf_rewards": 1, "sensors": 0.333, "investor": 0.95, "cs_nfail3": 0.333, "consensus.2": 0.1083,
                   "ij.10": 1, "ij.3": 1, "pacman": 0.5511, "pnueli-zuck.3": 1, "wlan.0": 1, "virus": 0,
                   "phil-nofair3": 2.4286, "blackjack": 0, "counter": 5, "recycling": 1.454, "busyRing4": 1,
                   "busyRingMC4": 1}

ctmdp_true_values = {"DynamicPM-tt_3_qs_2_sctmdp": 1.0,
                     "ErlangStages-k500_r10": 1.0,
                     "PollingSystem-jt1_qs1_sctmdp": 0.922, "PollingSystem-jt1_qs4_sctmdp": 0.999,
                     "PollingSystem-jt1_qs7_sctmdp": 0.999,
                     "QueuingSystem-lqs_1_rqs_1_jt_2_sctmdp": 0.8783,
                     "SJS-procn_2_jobn_2_sctmdp": 1.0,
                     "SJS-procn_2_jobn_6_sctmdp": 0.999,
                     "SJS-procn_6_jobn_2_sctmdp": 1, "toy": 1}


def get_true_value(model_name):
    if model_name in mdp_true_values:
        return mdp_true_values[model_name]

    if model_name in ctmdp_true_values:
        return ctmdp_true_values[model_name]

    return None
