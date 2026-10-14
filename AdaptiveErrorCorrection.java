import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Project Title:
 * Adaptive Error Correction for High-Noise Wireless Communication
 *
 * Description:
 * This program simulates a binary wireless communication system in which
 * the error-correction strength is selected according to the SNR.
 *
 * Level 1 : No error correction
 * Level 2 : Hamming (7,4)
 * Level 3 : Hamming (7,4) + Triple Repetition
 *
 * The program compares:
 * 1. Fixed Error Correction
 * 2. Adaptive Error Correction
 *
 * Performance parameters:
 * - BER (Bit Error Rate)
 * - Throughput
 * - Redundancy
 * - Number of errors
 *
 * Results are also saved to CSV files for report graphs.
 */

public class AdaptiveErrorCorrection {

    // ============================================================
    // RESULT CLASS
    // ============================================================

    static class Result {

        double snr;
        int level;
        double ber;
        double throughput;
        double redundancy;
        int errors;

        Result(double snr,
               int level,
               double ber,
               double throughput,
               double redundancy,
               int errors) {

            this.snr = snr;
            this.level = level;
            this.ber = ber;
            this.throughput = throughput;
            this.redundancy = redundancy;
            this.errors = errors;
        }
    }

    // ============================================================
    // CONFIGURATION
    // ============================================================

    static final int DATA_SIZE = 10000;

    /*
     * Fixed seed makes the experiment reproducible.
     * Every execution produces comparable results.
     */
    static final long RANDOM_SEED = 2026;

    /*
     * SNR values used for simulation.
     */
    static final double[] SNR_VALUES =
            {0, 2, 4, 6, 8, 10, 12};

    // ============================================================
    // GENERATE RANDOM BINARY DATA
    // ============================================================

    static int[] generateData(int n, Random random) {

        int[] data = new int[n];

        for (int i = 0; i < n; i++) {
            data[i] = random.nextInt(2);
        }

        return data;
    }

    // ============================================================
    // HAMMING (7,4) ENCODER
    // ============================================================

    /*
     * Hamming (7,4) format:
     *
     * Position:
     * 1  2  3  4  5  6  7
     * p1 p2 d1 p4 d2 d3 d4
     */

    static int[] hammingEncode(int[] data) {

        ArrayList<Integer> encoded = new ArrayList<>();

        for (int i = 0; i < data.length; i += 4) {

            int d1 = data[i];

            int d2 = (i + 1 < data.length)
                    ? data[i + 1] : 0;

            int d3 = (i + 2 < data.length)
                    ? data[i + 2] : 0;

            int d4 = (i + 3 < data.length)
                    ? data[i + 3] : 0;

            int p1 = d1 ^ d2 ^ d4;
            int p2 = d1 ^ d3 ^ d4;
            int p4 = d2 ^ d3 ^ d4;

            encoded.add(p1);
            encoded.add(p2);
            encoded.add(d1);
            encoded.add(p4);
            encoded.add(d2);
            encoded.add(d3);
            encoded.add(d4);
        }

        return encoded.stream()
                .mapToInt(Integer::intValue)
                .toArray();
    }

    // ============================================================
    // HAMMING (7,4) DECODER
    // ============================================================

    static int[] hammingDecode(int[] received) {

        ArrayList<Integer> decoded = new ArrayList<>();

        for (int i = 0;
             i + 6 < received.length;
             i += 7) {

            int p1 = received[i];
            int p2 = received[i + 1];
            int d1 = received[i + 2];
            int p4 = received[i + 3];
            int d2 = received[i + 4];
            int d3 = received[i + 5];
            int d4 = received[i + 6];

            int s1 = p1 ^ d1 ^ d2 ^ d4;
            int s2 = p2 ^ d1 ^ d3 ^ d4;
            int s4 = p4 ^ d2 ^ d3 ^ d4;

            int errorPosition =
                    s1 +
                    (s2 * 2) +
                    (s4 * 4);

            /*
             * Correct one-bit error.
             */
            if (errorPosition >= 1 &&
                errorPosition <= 7) {

                received[i + errorPosition - 1] ^= 1;
            }

            decoded.add(received[i + 2]);
            decoded.add(received[i + 4]);
            decoded.add(received[i + 5]);
            decoded.add(received[i + 6]);
        }

        return decoded.stream()
                .mapToInt(Integer::intValue)
                .toArray();
    }

    // ============================================================
    // TRIPLE REPETITION ENCODER
    // ============================================================

    /*
     * Each bit is transmitted three times.
     *
     * 0 -> 000
     * 1 -> 111
     */

    static int[] tripleRepeatEncode(int[] data) {

        int[] result = new int[data.length * 3];

        int index = 0;

        for (int bit : data) {

            result[index++] = bit;
            result[index++] = bit;
            result[index++] = bit;
        }

        return result;
    }

    // ============================================================
    // TRIPLE REPETITION DECODER
    // ============================================================

    /*
     * Majority voting:
     *
     * 000 -> 0
     * 001 -> 0
     * 010 -> 0
     * 011 -> 1
     * 100 -> 0
     * 101 -> 1
     * 110 -> 1
     * 111 -> 1
     */

    static int[] tripleRepeatDecode(int[] received) {

        int[] decoded =
                new int[received.length / 3];

        int index = 0;

        for (int i = 0;
             i + 2 < received.length;
             i += 3) {

            int sum =
                    received[i] +
                    received[i + 1] +
                    received[i + 2];

            decoded[index++] =
                    (sum >= 2) ? 1 : 0;
        }

        return decoded;
    }

    // ============================================================
    // ENCODER BASED ON LEVEL
    // ============================================================

    static int[] encode(int[] data, int level) {

        switch (level) {

            case 1:
                /*
                 * No error correction.
                 */
                return Arrays.copyOf(
                        data,
                        data.length);

            case 2:
                /*
                 * Hamming (7,4)
                 */
                return hammingEncode(data);

            case 3:
                /*
                 * Hamming (7,4)
                 * followed by triple repetition.
                 */
                int[] hamming =
                        hammingEncode(data);

                return tripleRepeatEncode(hamming);

            default:
                throw new IllegalArgumentException(
                        "Invalid coding level");
        }
    }

    // ============================================================
    // DECODER BASED ON LEVEL
    // ============================================================

    static int[] decode(int[] received, int level) {

        switch (level) {

            case 1:

                return Arrays.copyOf(
                        received,
                        received.length);

            case 2:

                return hammingDecode(received);

            case 3:

                int[] majority =
                        tripleRepeatDecode(received);

                return hammingDecode(majority);

            default:
                throw new IllegalArgumentException(
                        "Invalid coding level");
        }
    }

    // ============================================================
    // SNR TO NOISE PROBABILITY
    // ============================================================

    /*
     * Simplified binary symmetric channel model.
     *
     * Higher SNR -> lower probability of bit corruption.
     *
     * This is a simulation model and not an RF hardware model.
     */

    static double calculateNoiseProbability(double snr) {

        double probability =
                0.5 * Math.pow(
                        10,
                        -snr / 10.0);

        /*
         * Limit probability to valid range.
         */
        return Math.min(
                0.5,
                Math.max(
                        0.0,
                        probability));
    }

    // ============================================================
    // NOISY CHANNEL
    // ============================================================

    static int[] noisyChannel(
            int[] transmitted,
            double noiseProbability,
            Random random) {

        int[] received =
                Arrays.copyOf(
                        transmitted,
                        transmitted.length);

        for (int i = 0;
             i < received.length;
             i++) {

            /*
             * Random bit flip.
             */
            if (random.nextDouble()
                    < noiseProbability) {

                received[i] ^= 1;
            }
        }

        return received;
    }

    // ============================================================
    // ADAPTIVE CONTROLLER
    // ============================================================

    /*
     * Adaptive strategy:
     *
     * SNR >= 8 dB       -> Level 1
     * 4 <= SNR < 8 dB  -> Level 2
     * SNR < 4 dB        -> Level 3
     */

    static int selectAdaptiveLevel(
            double snr) {

        if (snr >= 8.0) {

            return 1;

        } else if (snr >= 4.0) {

            return 2;

        } else {

            return 3;
        }
    }

    // ============================================================
    // CALCULATE BER
    // ============================================================

    static double calculateBER(
            int[] original,
            int[] decoded) {

        int errors = 0;

        int length =
                Math.min(
                        original.length,
                        decoded.length);

        for (int i = 0;
             i < length;
             i++) {

            if (original[i]
                    != decoded[i]) {

                errors++;
            }
        }

        /*
         * Missing decoded bits are also
         * considered errors.
         */
        errors +=
                Math.abs(
                        original.length
                        - decoded.length);

        return (double) errors
                / original.length;
    }

    // ============================================================
    // COUNT ERRORS
    // ============================================================

    static int countErrors(
            int[] original,
            int[] decoded) {

        int errors = 0;

        int length =
                Math.min(
                        original.length,
                        decoded.length);

        for (int i = 0;
             i < length;
             i++) {

            if (original[i]
                    != decoded[i]) {

                errors++;
            }
        }

        errors +=
                Math.abs(
                        original.length
                        - decoded.length);

        return errors;
    }

    // ============================================================
    // REDUNDANCY / CODE RATE
    // ============================================================

    static double calculateRedundancy(
            int encodedLength,
            int originalLength) {

        return (double)
                encodedLength
                / originalLength;
    }

    // ============================================================
    // THROUGHPUT
    // ============================================================

    /*
     * Effective throughput:
     *
     * Successfully recovered information bits
     * ----------------------------------------- × 100
     *        transmitted coded bits
     *
     */

    static double calculateThroughput(
            int originalLength,
            int errors,
            int encodedLength) {

        int successfulBits =
                Math.max(
                        0,
                        originalLength
                        - errors);

        return
                ((double) successfulBits
                / encodedLength) * 100.0;
    }

    // ============================================================
    // RUN SINGLE EXPERIMENT
    // ============================================================

    static Result runExperiment(
            double snr,
            int level,
            Random random) {

        /*
         * Generate original information.
         */
        int[] original =
                generateData(
                        DATA_SIZE,
                        random);

        /*
         * Select coding technique.
         */
        int[] encoded =
                encode(
                        original,
                        level);

        /*
         * Calculate channel noise.
         */
        double noiseProbability =
                calculateNoiseProbability(
                        snr);

        /*
         * Pass encoded data through
         * noisy wireless channel.
         */
        int[] received =
                noisyChannel(
                        encoded,
                        noiseProbability,
                        random);

        /*
         * Decode received data.
         */
        int[] decoded =
                decode(
                        received,
                        level);

        /*
         * Calculate performance.
         */
        int errors =
                countErrors(
                        original,
                        decoded);

        double ber =
                calculateBER(
                        original,
                        decoded);

        double redundancy =
                calculateRedundancy(
                        encoded.length,
                        original.length);

        double throughput =
                calculateThroughput(
                        original.length,
                        errors,
                        encoded.length);

        return new Result(
                snr,
                level,
                ber,
                throughput,
                redundancy,
                errors);
    }

    // ============================================================
    // PRINT RESULT
    // ============================================================

    static void printResult(
            Result result) {

        System.out.printf(
                "%-8.1f %-8d %-12.6f %-15.2f %-12.2f %-10d%n",
                result.snr,
                result.level,
                result.ber,
                result.throughput,
                result.redundancy,
                result.errors);
    }

    // ============================================================
    // WRITE CSV HEADER
    // ============================================================

    static void writeHeader(
            FileWriter writer)
            throws IOException {

        writer.write(
                "SNR,Level,BER,Throughput,Redundancy,Errors\n");
    }

    // ============================================================
    // WRITE RESULT TO CSV
    // ============================================================

    static void writeResult(
            FileWriter writer,
            Result result)
            throws IOException {

        writer.write(
                String.format(
                        Locale.US,
                        "%.1f,%d,%.6f,%.2f,%.2f,%d\n",
                        result.snr,
                        result.level,
                        result.ber,
                        result.throughput,
                        result.redundancy,
                        result.errors));
    }

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(
            String[] args) {

        Random random =
                new Random(
                        RANDOM_SEED);

        /*
         * --------------------------------------------------------
         * ADAPTIVE EXPERIMENT
         * --------------------------------------------------------
         */

        System.out.println();

        System.out.println(
                "==============================================================");

        System.out.println(
                " ADAPTIVE ERROR CORRECTION FOR HIGH-NOISE "
                + "WIRELESS COMMUNICATION");

        System.out.println(
                "==============================================================");

        System.out.println();

        System.out.println(
                "ADAPTIVE ERROR CORRECTION RESULTS");

        System.out.println();

        System.out.printf(
                "%-8s %-8s %-12s %-15s %-12s %-10s%n",
                "SNR",
                "Level",
                "BER",
                "Throughput",
                "Redundancy",
                "Errors");

        System.out.println(
                "--------------------------------------------------------------");

        /*
         * CSV file for adaptive results.
         */
        try (FileWriter writer =
                     new FileWriter(
                             "adaptive_results.csv")) {

            writeHeader(writer);

            for (double snr :
                    SNR_VALUES) {

                int level =
                        selectAdaptiveLevel(
                                snr);

                Result result =
                        runExperiment(
                                snr,
                                level,
                                random);

                printResult(result);

                writeResult(
                        writer,
                        result);
            }

        } catch (IOException e) {

            System.out.println(
                    "Error writing adaptive CSV: "
                    + e.getMessage());
        }

        /*
         * --------------------------------------------------------
         * FIXED LEVEL 2 EXPERIMENT
         * --------------------------------------------------------
         *
         * Hamming (7,4) is used at every SNR.
         */

        System.out.println();

        System.out.println(
                "FIXED ERROR CORRECTION RESULTS");

        System.out.println(
                "Fixed Coding Level = 2 (Hamming 7,4)");

        System.out.println();

        System.out.printf(
                "%-8s %-8s %-12s %-15s %-12s %-10s%n",
                "SNR",
                "Level",
                "BER",
                "Throughput",
                "Redundancy",
                "Errors");

        System.out.println(
                "--------------------------------------------------------------");

        /*
         * Separate random generator so that the experiment
         * remains reproducible.
         */
        Random fixedRandom =
                new Random(
                        RANDOM_SEED);

        try (FileWriter writer =
                     new FileWriter(
                             "fixed_results.csv")) {

            writeHeader(writer);

            for (double snr :
                    SNR_VALUES) {

                Result result =
                        runExperiment(
                                snr,
                                2,
                                fixedRandom);

                printResult(result);

                writeResult(
                        writer,
                        result);
            }

        } catch (IOException e) {

            System.out.println(
                    "Error writing fixed CSV: "
                    + e.getMessage());
        }

        /*
         * --------------------------------------------------------
         * CODING LEVEL INFORMATION
         * --------------------------------------------------------
         */

        System.out.println();

        System.out.println(
                "==============================================================");

        System.out.println(
                " CODING LEVEL INFORMATION");

        System.out.println(
                "==============================================================");

        System.out.println(
                "Level 1 : No Error Correction");

        System.out.println(
                "Level 2 : Hamming (7,4)");

        System.out.println(
                "Level 3 : Hamming (7,4) + Triple Repetition");

        System.out.println();

        System.out.println(
                "Adaptive Decision:");

        System.out.println(
                "SNR >= 8 dB       -> Level 1");

        System.out.println(
                "4 dB <= SNR < 8 dB -> Level 2");

        System.out.println(
                "SNR < 4 dB        -> Level 3");

        System.out.println();

        System.out.println(
                "CSV files generated:");

        System.out.println(
                "1. adaptive_results.csv");

        System.out.println(
                "2. fixed_results.csv");

        System.out.println();

        System.out.println(
                "==============================================================");

        System.out.println(
                " SIMULATION COMPLETED SUCCESSFULLY");

        System.out.println(
                "==============================================================");
    }
}