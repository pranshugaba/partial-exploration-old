mdp_true_values = {"zeroconf_rewards": 1, "sensors": 0.333, "investor": 0.95, "cs_nfail3": 0.333, "consensus.2": 0.1083,
                   "ij.10": 1, "ij.3": 1, "pacman": 0.5511, "pnueli-zuck.3": 1, "wlan.0": 1, "virus": 0,
                   "phil-nofair3": 2.4286, "blackjack": 0, "counter": 5, "recycling": 1.454, "busyRing4": 1,
                   "busyRingMC4": 1}

ctmdp_true_values = {"DynamicPM-tt_3_qs_2_sctmdp": 199.9965, "DynamicPM-tt_3_qs_6_sctmdp": 294.4229,
                     "ErlangStages-k500_r10": 200, "ErlangStages-k2000_r10": 55.00,
                     "ftwc_001_mrmc": 163.7786, "ftwc_008_mrmc": 30.8546,
                     "PollingSystem-jt1_qs1_sctmdp": 39.6516, "PollingSystem-jt1_qs4_sctmdp": 29.9995,
                     "PollingSystem-jt1_qs7_sctmdp": 199.9984, "QueuingSystem-lqs_1_rqs_1_jt_2_sctmdp": 175.6629,
                     "QueuingSystem-lqs_2_rqs_2_jt_3_sctmdp": 173.5139, "SJS-procn_2_jobn_2_sctmdp": 200,
                     "SJS-procn_2_jobn_6_sctmdp": 199.8144, "SJS-procn_3_jobn_5_sctmdp": 386.0029,
                     "SJS-procn_6_jobn_2_sctmdp": 300, "toy": 2}


def get_true_value(model_name):
    if model_name in mdp_true_values:
        return mdp_true_values[model_name]

    if model_name in ctmdp_true_values:
        return ctmdp_true_values[model_name]

    return None
