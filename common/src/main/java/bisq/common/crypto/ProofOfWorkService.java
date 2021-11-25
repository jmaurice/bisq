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

import com.google.common.base.Preconditions;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

import lombok.Getter;

public abstract class ProofOfWorkService {
    private static class InstanceHolder {
        private static final ProofOfWorkService[] INSTANCES = {
                new HashCashService(),
                new EquihashProofOfWorkService(1)
        };
    }

    public static Optional<ProofOfWorkService> forVersion(int version) {
        return version >= 0 && version < InstanceHolder.INSTANCES.length ?
                Optional.of(InstanceHolder.INSTANCES[version]) : Optional.empty();
    }

    @Getter
    private final int version;

    ProofOfWorkService(int version) {
        this.version = version;
    }

    public abstract CompletableFuture<ProofOfWork> mint(String itemId, byte[] challenge, int log2Difficulty);

    public abstract byte[] getChallenge(String itemId, String ownerId);

    abstract boolean verify(ProofOfWork proofOfWork);

    public CompletableFuture<ProofOfWork> mint(String itemId, String ownerId, int log2Difficulty) {
        return mint(itemId, getChallenge(itemId, ownerId), log2Difficulty);
    }

    public boolean verify(ProofOfWork proofOfWork,
                          String itemId,
                          String ownerId,
                          int controlLog2Difficulty,
                          BiPredicate<byte[], byte[]> challengeValidation,
                          BiPredicate<Integer, Integer> difficultyValidation) {

        Preconditions.checkArgument(proofOfWork.getVersion() == version);

        byte[] controlChallenge = getChallenge(itemId, ownerId);
        return challengeValidation.test(proofOfWork.getChallenge(), controlChallenge) &&
                difficultyValidation.test(proofOfWork.getNumLeadingZeros(), controlLog2Difficulty) &&
                verify(proofOfWork);
    }
}
