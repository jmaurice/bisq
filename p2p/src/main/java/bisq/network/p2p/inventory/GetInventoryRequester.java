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

package bisq.network.p2p.inventory;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.inventory.messages.GetInventoryRequest;
import bisq.network.p2p.inventory.messages.GetInventoryResponse;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.proto.network.NetworkEnvelope;

import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetInventoryRequester implements MessageListener {
    private final static int TIMEOUT_SEC = 90;

    private final NetworkNode networkNode;
    private final NodeAddress nodeAddress;
    private final Consumer<Map<String, Integer>> resultHandler;
    private final ErrorMessageHandler errorMessageHandler;
    private Timer timer;

    public GetInventoryRequester(NetworkNode networkNode,
                                 NodeAddress nodeAddress,
                                 Consumer<Map<String, Integer>> resultHandler,
                                 ErrorMessageHandler errorMessageHandler) {
        this.networkNode = networkNode;
        this.nodeAddress = nodeAddress;
        this.resultHandler = resultHandler;
        this.errorMessageHandler = errorMessageHandler;
    }

    public void request() {
        networkNode.addMessageListener(this);
        timer = UserThread.runAfter(this::onTimeOut, TIMEOUT_SEC);
        networkNode.sendMessage(nodeAddress, new GetInventoryRequest(Version.VERSION));
    }

    private void onTimeOut() {
        errorMessageHandler.handleErrorMessage("Timeout got triggered (" + TIMEOUT_SEC + " sec)");
        shutDown();
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof GetInventoryResponse) {
            connection.getPeersNodeAddressOptional().ifPresent(peer -> {
                if (peer.equals(nodeAddress)) {
                    GetInventoryResponse getInventoryResponse = (GetInventoryResponse) networkEnvelope;
                    resultHandler.accept(getInventoryResponse.getNumPayloadsMap());
                    shutDown();
                }
            });
        }
    }

    public void shutDown() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        networkNode.removeMessageListener(this);
    }
}
