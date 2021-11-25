/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.common.crypto;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Longs;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

import lombok.extern.slf4j.Slf4j;

/**
 * HashCash implementation for proof of work
 * It doubles required work by difficulty increase (adding one leading zero).
 *
 * See https://www.hashcash.org/papers/hashcash.pdf
 */
@Slf4j
public class HashCashService extends ProofOfWorkService {
    // Default validations. Custom implementations might use tolerance.
    private static final BiPredicate<byte[], byte[]> isChallengeValid = Arrays::equals;
    private static final BiPredicate<Integer, Integer> isDifficultyValid = Integer::equals;

    HashCashService() {
        super(0);
    }

    @Override
    public CompletableFuture<ProofOfWork> mint(String itemId, byte[] challenge, int log2Difficulty) {
        byte[] payload = getBytes(itemId);
        return mint(payload, challenge, log2Difficulty);
    }

    @Override
    public byte[] getChallenge(String itemId, String ownerId) {
        return getBytes(itemId + ownerId);
    }

    static CompletableFuture<ProofOfWork> mint(byte[] payload,
                                               byte[] challenge,
                                               int difficulty) {
        return HashCashService.mint(payload,
                challenge,
                difficulty,
                HashCashService::testDifficulty);
    }

    @Override
    boolean verify(ProofOfWork proofOfWork) {
        return verify(proofOfWork,
                proofOfWork.getChallenge(),
                proofOfWork.getNumLeadingZeros());
    }

    static boolean verify(ProofOfWork proofOfWork,
                          byte[] controlChallenge,
                          int controlDifficulty) {
        return HashCashService.verify(proofOfWork,
                controlChallenge,
                controlDifficulty,
                HashCashService::testDifficulty);
    }

    static boolean verify(ProofOfWork proofOfWork,
                          byte[] controlChallenge,
                          int controlDifficulty,
                          BiPredicate<byte[], byte[]> challengeValidation,
                          BiPredicate<Integer, Integer> difficultyValidation) {
        return HashCashService.verify(proofOfWork,
                controlChallenge,
                controlDifficulty,
                challengeValidation,
                difficultyValidation,
                HashCashService::testDifficulty);
    }

    private static boolean testDifficulty(byte[] result, long difficulty) {
        return HashCashService.numberOfLeadingZeros(result) > difficulty;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Generic
    ///////////////////////////////////////////////////////////////////////////////////////////

    static CompletableFuture<ProofOfWork> mint(byte[] payload,
                                               byte[] challenge,
                                               int difficulty,
                                               BiPredicate<byte[], Integer> testDifficulty) {
        return CompletableFuture.supplyAsync(() -> {
            long ts = System.currentTimeMillis();
            byte[] result;
            long counter = 0;
            do {
                result = toSha256Hash(payload, challenge, ++counter);
            }
            while (!testDifficulty.test(result, difficulty));
            ProofOfWork proofOfWork = new ProofOfWork(payload, counter, challenge, difficulty, System.currentTimeMillis() - ts, 0);
            log.info("Completed minting proofOfWork: {}", proofOfWork);
            return proofOfWork;
        });
    }

    static boolean verify(ProofOfWork proofOfWork,
                          byte[] controlChallenge,
                          int controlDifficulty,
                          BiPredicate<byte[], Integer> testDifficulty) {
        return verify(proofOfWork,
                controlChallenge,
                controlDifficulty,
                HashCashService.isChallengeValid,
                HashCashService.isDifficultyValid,
                testDifficulty);
    }

    static boolean verify(ProofOfWork proofOfWork,
                          byte[] controlChallenge,
                          int controlDifficulty,
                          BiPredicate<byte[], byte[]> challengeValidation,
                          BiPredicate<Integer, Integer> difficultyValidation,
                          BiPredicate<byte[], Integer> testDifficulty) {
        return challengeValidation.test(proofOfWork.getChallenge(), controlChallenge) &&
                difficultyValidation.test(proofOfWork.getNumLeadingZeros(), controlDifficulty) &&
                verify(proofOfWork, testDifficulty);
    }

    private static boolean verify(ProofOfWork proofOfWork, BiPredicate<byte[], Integer> testDifficulty) {
        byte[] hash = HashCashService.toSha256Hash(proofOfWork.getPayload(),
                proofOfWork.getChallenge(),
                proofOfWork.getCounter());
        return testDifficulty.test(hash, proofOfWork.getNumLeadingZeros());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static byte[] getBytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @VisibleForTesting
    static int numberOfLeadingZeros(byte[] bytes) {
        int numberOfLeadingZeros = 0;
        for (int i = 0; i < bytes.length; i++) {
            numberOfLeadingZeros += numberOfLeadingZeros(bytes[i]);
            if (numberOfLeadingZeros < 8 * (i + 1)) {
                break;
            }
        }
        return numberOfLeadingZeros;
    }

    private static byte[] toSha256Hash(byte[] payload, byte[] challenge, long counter) {
        byte[] preImage = org.bouncycastle.util.Arrays.concatenate(payload,
                challenge,
                Longs.toByteArray(counter));
        return Hash.getSha256Hash(preImage);
    }

    // Borrowed from Integer.numberOfLeadingZeros and adjusted for byte
    @VisibleForTesting
    static int numberOfLeadingZeros(byte i) {
        if (i <= 0)
            return i == 0 ? 8 : 0;
        int n = 7;
        if (i >= 1 << 4) {
            n -= 4;
            i >>>= 4;
        }
        if (i >= 1 << 2) {
            n -= 2;
            i >>>= 2;
        }
        return n - (i >>> 1);
    }
}
