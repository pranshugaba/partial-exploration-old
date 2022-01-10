package de.tum.in.pet.implementation.meanPayoff;

public class CTMDPNSamplesTable {
    private static final double[] epsilon = {0.03, 0.05, 0.1, 0.2};
    private static final double[] deltaT = {0.1, 0.05, 0.0001, 0.0000001};

    private static final double[][] N_SAMPLES = {
            {7000, 9000, 23000, 60000},
            {2500, 3100, 8000, 13400},
            {650, 800, 2100, 3500},
            {160, 200, 530, 920}
    };

    public static double getEpsilon(long nSamples, double transDelta) {
        int column = getCorrespondingTransDeltaColumn(transDelta);

        // If transDelta is too small, we will check the last column
        if (column == -1) {
            column = 3;
        }

        return getCorrespondingEpsilon(nSamples, column);
    }

    private static int getCorrespondingTransDeltaColumn(double transDelta) {
        for (int i = 0; i < 4; i++) {
            if (deltaT[i] <= transDelta) {
                return i;
            }
        }

        return -1;
    }

    private static double getCorrespondingEpsilon(double nSamples, int transDeltaColumn) {
        for (int i = 0; i < 4; i++) {
            if (nSamples >= N_SAMPLES[i][transDeltaColumn]) {
                return epsilon[i];
            }
        }

        // If nSamples is too small, we choose the largest epsilon
        return epsilon[3];
    }
}
