package org.smartregister.p2p.sync.handler;

import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.p2p.P2PLibrary;
import org.smartregister.p2p.authorizer.P2PAuthorizationService;
import org.smartregister.p2p.contract.P2pModeSelectContract;
import org.smartregister.p2p.model.DataType;
import org.smartregister.p2p.model.P2pReceivedHistory;
import org.smartregister.p2p.model.dao.ReceiverTransferDao;
import org.smartregister.p2p.model.dao.SenderTransferDao;
import org.smartregister.p2p.shadows.ShadowAppDatabase;
import org.smartregister.p2p.shadows.ShadowTasker;
import org.smartregister.p2p.sync.data.SyncPackageManifest;
import org.smartregister.p2p.sync.handler.SyncSenderHandler;
import org.smartregister.p2p.util.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by Ephraim Kigamba - ekigamba@ona.io on 29/03/2019
 */

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowAppDatabase.class, ShadowTasker.class})
public class SyncSenderHandlerTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private SyncSenderHandler syncSenderHandler;
    @Mock
    private P2pModeSelectContract.SenderPresenter senderPresenter;

    @Mock
    private P2PAuthorizationService authorizationService;
    @Mock
    private SenderTransferDao senderTransferDao;
    @Mock
    private ReceiverTransferDao receiverTransferDao;

    private TreeSet<DataType> dataSyncOrder;

    private DataType event = new DataType("event", DataType.Type.NON_MEDIA, 1);
    private DataType client = new DataType("client", DataType.Type.NON_MEDIA, 2);
    private DataType profilePic = new DataType("profile-pic", DataType.Type.MEDIA, 3);

    @Before
    public void setUp() throws Exception {
        P2PLibrary.init(new P2PLibrary.Options(RuntimeEnvironment.application, "some password", "username"
                , authorizationService, receiverTransferDao, senderTransferDao));

        dataSyncOrder = new TreeSet<>();
        dataSyncOrder.add(profilePic);
        dataSyncOrder.add(client);
        dataSyncOrder.add(event);

        syncSenderHandler = Mockito.spy(new SyncSenderHandler(senderPresenter, dataSyncOrder, null));
    }

    @Test
    public void generateRecordsToSendShouldPopulateRemainingLastRecordIdsObjectWithDefaultIdWhenReceivedHistoryIsNull() {
        HashMap<String, Long> remainingLastRecordIds = ReflectionHelpers.getField(syncSenderHandler, "remainingLastRecordIds");

        assertEquals(0, remainingLastRecordIds.size());

        ReflectionHelpers.callInstanceMethod(syncSenderHandler, "generateRecordsToSend");

        Collection<Long> lastRecordIds = remainingLastRecordIds.values();

        assertEquals(3, remainingLastRecordIds.size());
        Iterator<Long> recordIdIterator = lastRecordIds.iterator();
        assertEquals(0, (long) recordIdIterator.next());
        assertEquals(0, (long) recordIdIterator.next());
        assertEquals(0, (long) recordIdIterator.next());
    }

    @Test
    public void generateRecordsToSendShouldPopulateRemainingLastRecordIdsObjectWithReceivedHistoryIds() {
        List<P2pReceivedHistory> receivedHistories = new ArrayList<>();
        P2pReceivedHistory eventsHistory = createReceivedHistory("event", 1045, "id");
        P2pReceivedHistory clientHistory = createReceivedHistory("client", 98, "id");

        receivedHistories.add(eventsHistory);
        receivedHistories.add(clientHistory);

        ReflectionHelpers.setField(syncSenderHandler, "receivedHistory", receivedHistories);
        HashMap<String, Long> remainingLastRecordIds = ReflectionHelpers.getField(syncSenderHandler, "remainingLastRecordIds");

        assertEquals(0, remainingLastRecordIds.size());

        ReflectionHelpers.callInstanceMethod(syncSenderHandler, "generateRecordsToSend");

        remainingLastRecordIds = ReflectionHelpers.getField(syncSenderHandler, "remainingLastRecordIds");

        assertEquals(3, remainingLastRecordIds.size());

        assertEquals(1045, (long) remainingLastRecordIds.get("event"));
        assertEquals(98, (long) remainingLastRecordIds.get("client"));
        assertEquals(0, (long) remainingLastRecordIds.get("profile-pic"));
    }

    @Test
    public void startSyncProcessShouldCallSendNextManifest() {
        Mockito.doNothing()
                .when(syncSenderHandler)
                .sendNextManifest();

        syncSenderHandler.startSyncProcess();

        Mockito.verify(syncSenderHandler, Mockito.times(1))
                .sendNextManifest();
    }

    @Test
    public void sendNextManifestShouldCallSendJSONDataManifestWhenNonMediaDataTypeIsPriority() {
        TreeSet<DataType> dataSyncOrder = ReflectionHelpers.getField(syncSenderHandler, "dataSyncOrder");
        DataType dataType1 = dataSyncOrder.last();
        dataSyncOrder.remove(dataType1);
        DataType dataType2 = dataSyncOrder.last();

        dataSyncOrder.remove(dataType2);

        syncSenderHandler.sendNextManifest();

        Mockito.verify(syncSenderHandler, Mockito.times(1))
                .sendJsonDataManifest(Mockito.any(DataType.class));
    }

    @Test
    public void sendNextManifestShouldCallSendMultimediaDataManifestWhenMediaDataTypeIsPriority() {
        TreeSet<DataType> dataSyncOrder = ReflectionHelpers.getField(syncSenderHandler, "dataSyncOrder");
        DataType dataType1 = dataSyncOrder.first();
        dataSyncOrder.remove(dataType1);
        DataType dataType2 = dataSyncOrder.first();

        dataSyncOrder.remove(dataType2);

        syncSenderHandler.sendNextManifest();

        Mockito.verify(syncSenderHandler, Mockito.times(1))
                .sendMultimediaDataManifest(Mockito.any(DataType.class));
    }

    @Test
    public void sendNextManifestShouldCallPresenterSendSyncCompleteWhenNoRemainingDataType() {
        TreeSet<DataType> dataSyncOrder = ReflectionHelpers.getField(syncSenderHandler, "dataSyncOrder");

        dataSyncOrder.clear();

        syncSenderHandler.sendNextManifest();

        Mockito.verify(senderPresenter, Mockito.times(1))
                .sendSyncComplete();
    }

    @Test
    public void sendJsonDataManifestShouldCallSendNextManifestWhenDataToSendIsNull() {
        DataType dataType = dataSyncOrder.first();
        TreeSet<DataType> singleDataSyncOrder = new TreeSet<>();
        singleDataSyncOrder.add(dataType);

        ReflectionHelpers.setField(syncSenderHandler, "dataSyncOrder", singleDataSyncOrder);
        syncSenderHandler.sendJsonDataManifest(dataType);

        Mockito.verify(syncSenderHandler, Mockito.times(1))
                .sendNextManifest();
    }

    @Test
    public void onPayloadTransferUpdateShouldCallSendNextPayloadAndResetFlagsWhenManifestStatusUpdateIsSuccess() {
        long payloadId = 9;
        int status = PayloadTransferUpdate.Status.SUCCESS;

        Mockito.doNothing()
                .when(syncSenderHandler)
                .sendNextPayload();

        PayloadTransferUpdate payloadTransferUpdate = Mockito.mock(PayloadTransferUpdate.class);

        Mockito.doReturn(payloadId)
                .when(payloadTransferUpdate)
                .getPayloadId();

        Mockito.doReturn(status)
                .when(payloadTransferUpdate)
                .getStatus();

        ReflectionHelpers.setField(syncSenderHandler, "awaitingManifestTransfer", true);
        ReflectionHelpers.setField(syncSenderHandler, "awaitingManifestId", payloadId);
        syncSenderHandler.onPayloadTransferUpdate(payloadTransferUpdate);

        Mockito.verify(syncSenderHandler, Mockito.times(1))
                .sendNextPayload();

        assertFalse((boolean) ReflectionHelpers.getField(syncSenderHandler, "awaitingManifestTransfer"));
        assertEquals(0l, ReflectionHelpers.getField(syncSenderHandler, "awaitingManifestId"));
        assertNull(ReflectionHelpers.getField(syncSenderHandler, "payloadRetry"));
    }

    @Test
    public void onPayloadTransferUpdateShouldRetrySendingManifestWhenStatusManifestUpdateIsFailure() {
        long payloadId = 9;
        int status = PayloadTransferUpdate.Status.FAILURE;

        Mockito.doNothing()
                .when(syncSenderHandler)
                .sendNextPayload();

        PayloadTransferUpdate payloadTransferUpdate = Mockito.mock(PayloadTransferUpdate.class);

        Mockito.doReturn(payloadId)
                .when(payloadTransferUpdate)
                .getPayloadId();

        Mockito.doReturn(status)
                .when(payloadTransferUpdate)
                .getStatus();

        SyncPackageManifest syncPackageManifest = Mockito.mock(SyncPackageManifest.class);

        ReflectionHelpers.setField(syncSenderHandler, "awaitingManifestTransfer", true);
        ReflectionHelpers.setField(syncSenderHandler, "awaitingManifestId", payloadId);
        ReflectionHelpers.setField(syncSenderHandler, "syncPackageManifest", syncPackageManifest);

        syncSenderHandler.onPayloadTransferUpdate(payloadTransferUpdate);

        Mockito.verify(senderPresenter, Mockito.times(1))
                .sendManifest(ArgumentMatchers.eq(syncPackageManifest));

        assertNotNull(ReflectionHelpers.getField(syncSenderHandler, "payloadRetry"));
    }

    @Test
    public void onPayloadTransferUpdateShouldReportFatalSyncErrorToPresenterWhenManifestStatusUpdateIsCancelled() {
        long payloadId = 9;
        int status = PayloadTransferUpdate.Status.CANCELED;

        Mockito.doNothing()
                .when(syncSenderHandler)
                .sendNextPayload();

        PayloadTransferUpdate payloadTransferUpdate = Mockito.mock(PayloadTransferUpdate.class);

        Mockito.doReturn(payloadId)
                .when(payloadTransferUpdate)
                .getPayloadId();

        Mockito.doReturn(status)
                .when(payloadTransferUpdate)
                .getStatus();

        ReflectionHelpers.setField(syncSenderHandler, "awaitingManifestTransfer", true);
        ReflectionHelpers.setField(syncSenderHandler, "awaitingManifestId", payloadId);
        syncSenderHandler.onPayloadTransferUpdate(payloadTransferUpdate);

        Mockito.verify(senderPresenter, Mockito.times(1))
                .errorOccurredSync(Mockito.any(Exception.class));
    }

    @Test
    public void onProcessStringShouldCallSendNextManifestWhenPayloadStatusUpdateForPayloadIsSuccess() {
        long payloadId = 9;

        Mockito.doNothing()
                .when(syncSenderHandler)
                .sendNextManifest();

        Payload awaitingPayload = Mockito.mock(Payload.class);

        Mockito.doReturn(payloadId)
                .when(awaitingPayload)
                .getId();

        int dataTypeBatchSize = 20;
        String dataTypeName = "location";

        ReflectionHelpers.setField(syncSenderHandler, "awaitingPayloadTransfer", true);
        ReflectionHelpers.setField(syncSenderHandler, "awaitingPayload", awaitingPayload);
        ReflectionHelpers.setField(syncSenderHandler, "awaitingDataTypeRecordsBatchSize", dataTypeBatchSize);
        ReflectionHelpers.setField(syncSenderHandler, "awaitingDataTypeName", dataTypeName);

        syncSenderHandler.processString(Constants.Connection.PAYLOAD_RECEIVED + payloadId);

        Mockito.verify(syncSenderHandler, Mockito.times(1))
                .sendNextManifest();

        //awaitingDataTypeName, awaitingDataTypeRecordsBatchSize
        Mockito.verify(syncSenderHandler, Mockito.times(1))
                .updateTransferProgress(Mockito.eq(dataTypeName), Mockito.eq(dataTypeBatchSize));

        assertFalse((boolean) ReflectionHelpers.getField(syncSenderHandler, "awaitingPayloadTransfer"));
        assertNull(ReflectionHelpers.getField(syncSenderHandler, "awaitingPayload"));
        assertNull(ReflectionHelpers.getField(syncSenderHandler, "payloadRetry"));
    }

    @Test
    public void onPayloadTransferUpdateShouldRetrySendingPayloadWhenPayloadStatusUpdateForPayloadIsFailureAndPayloadRetriesIsNull() {
        long payloadId = 9;
        int status = PayloadTransferUpdate.Status.FAILURE;

        PayloadTransferUpdate payloadTransferUpdate = Mockito.mock(PayloadTransferUpdate.class);

        Mockito.doReturn(payloadId)
                .when(payloadTransferUpdate)
                .getPayloadId();

        Mockito.doReturn(status)
                .when(payloadTransferUpdate)
                .getStatus();

        Payload awaitingPayload = Mockito.mock(Payload.class);

        Mockito.doReturn(payloadId)
                .when(awaitingPayload)
                .getId();

        ReflectionHelpers.setField(syncSenderHandler, "awaitingPayloadTransfer", true);
        ReflectionHelpers.setField(syncSenderHandler, "awaitingPayload", awaitingPayload);
        syncSenderHandler.onPayloadTransferUpdate(payloadTransferUpdate);

        Robolectric.getBackgroundThreadScheduler().advanceToLastPostedRunnable();

        Mockito.verify(syncSenderHandler, Mockito.times(1))
                .sendNextPayload();

        Mockito.verify(senderPresenter, Mockito.times(1))
                .sendPayload(ArgumentMatchers.eq(awaitingPayload));

        SyncSenderHandler.PayloadRetry payloadRetry = ReflectionHelpers.getField(syncSenderHandler, "payloadRetry");
        assertTrue((boolean) ReflectionHelpers.getField(syncSenderHandler, "awaitingPayloadTransfer"));
        assertNotNull(payloadRetry);

        assertEquals(2, payloadRetry.retries);
    }

    @Test
    public void onPayloadTransferUpdateShouldReportFatalErrorToPresenterWhenPayloadStatusUpdateForPayloadIsCancelled() {
        long payloadId = 9;
        int status = PayloadTransferUpdate.Status.CANCELED;

        PayloadTransferUpdate payloadTransferUpdate = Mockito.mock(PayloadTransferUpdate.class);

        Mockito.doReturn(payloadId)
                .when(payloadTransferUpdate)
                .getPayloadId();

        Mockito.doReturn(status)
                .when(payloadTransferUpdate)
                .getStatus();

        Payload awaitingPayload = Mockito.mock(Payload.class);

        Mockito.doReturn(payloadId)
                .when(awaitingPayload)
                .getId();

        ReflectionHelpers.setField(syncSenderHandler, "awaitingPayloadTransfer", true);
        ReflectionHelpers.setField(syncSenderHandler, "awaitingPayload", awaitingPayload);
        syncSenderHandler.onPayloadTransferUpdate(payloadTransferUpdate);

        Mockito.verify(senderPresenter, Mockito.times(1))
                .errorOccurredSync(Mockito.any(Exception.class));
    }

    //@Test
    // This test has been ignored for now
    /*public void sendNextPayloadShouldCallPresenterSendPayloadWhenThereIsAwaitingPayload() throws InterruptedException {
        syncSenderHandler.sendNextPayload();

        Robolectric.flushBackgroundThreadScheduler();

        Mockito.verify(senderPresenter, Mockito.times(0))
                .sendPayload(Mockito.any(Payload.class));

        Payload payload = Mockito.mock(Payload.class);
        ReflectionHelpers.setField(syncSenderHandler, "awaitingPayload", payload);

        syncSenderHandler.sendNextPayload();

        //Robolectric.flushBackgroundThreadScheduler();
        ShadowApplication.runBackgroundTasks();
        assertFalse(Robolectric.getBackgroundThreadScheduler().areAnyRunnable());
        Thread.sleep(100);

        Mockito.verify(senderPresenter, Mockito.times(1))
                .sendPayload(ArgumentMatchers.eq(payload));
    }*/

    private P2pReceivedHistory createReceivedHistory(String entityType, long lastRecordId, String sendingDeviceId) {
        P2pReceivedHistory history = new P2pReceivedHistory();
        history.setEntityType(entityType);
        history.setLastRecordId(lastRecordId);
        history.setSendingDeviceId(sendingDeviceId);

        return history;
    }

}