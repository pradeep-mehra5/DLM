/**
 * Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.dlm.hbase.storage;

import com.phonepe.dlm.exception.DLMException;
import com.phonepe.dlm.exception.ErrorCode;
import com.phonepe.dlm.lock.level.LockLevel;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Store-level tests for {@link HBaseStore}'s ownership-checked release.
 */
class HBaseStoreTest {
    private static final String TABLE = "dlm_locks";
    private static final String LOCK_ID = "LOCK_ID";
    private static final String FARM_ID = "FA1";
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final long ACQUIRED_AT = 1_700_000_000_123L;

    private HBaseConnectionStub connection;
    private HBaseStore store;

    @BeforeEach
    void setUp() {
        connection = new HBaseConnectionStub();
        store = HBaseStore.builder()
                .connection(connection)
                .tableName(TABLE)
                .build();
    }

    @Test
    void ownerCheckedRemoveDeletesOwnLock() {
        store.write(LOCK_ID, LockLevel.DC, FARM_ID, TTL, ACQUIRED_AT);

        assertTrue(store.remove(LOCK_ID, LockLevel.DC, FARM_ID, ACQUIRED_AT));
        assertTrue(connection.getTable(TABLE).getData().isEmpty());
    }

    @Test
    void ownerCheckedRemoveReturnsFalseWhenLockAlreadyExpired() {
        store.write(LOCK_ID, LockLevel.DC, FARM_ID, TTL, ACQUIRED_AT);
        connection.getTable(TABLE).getData().clear(); // simulate TTL expiry

        assertFalse(store.remove(LOCK_ID, LockLevel.DC, FARM_ID, ACQUIRED_AT));
    }

    @Test
    void ownerCheckedRemoveLeavesAnotherHoldersLockIntact() {
        store.write(LOCK_ID, LockLevel.DC, FARM_ID, TTL, ACQUIRED_AT + 1_000);

        assertFalse(store.remove(LOCK_ID, LockLevel.DC, FARM_ID, ACQUIRED_AT));
        assertFalse(connection.getTable(TABLE).getData().isEmpty());
    }

    @Test
    void ownerCheckedRemoveWrapsIOException() throws IOException {
        final Connection failingConnection = mock(Connection.class);
        when(failingConnection.getTable(any(TableName.class))).thenThrow(new IOException("unreachable"));
        final HBaseStore failingStore = HBaseStore.builder()
                .connection(failingConnection)
                .tableName(TABLE)
                .build();

        final DLMException exception = assertThrows(DLMException.class,
                () -> failingStore.remove(LOCK_ID, LockLevel.DC, FARM_ID, ACQUIRED_AT));

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedWriteAndRemoveStillLockAndUnlock() {
        store.write(LOCK_ID, LockLevel.DC, FARM_ID, TTL);
        final DLMException exception = assertThrows(DLMException.class,
                () -> store.write(LOCK_ID, LockLevel.DC, FARM_ID, TTL));
        assertEquals(ErrorCode.LOCK_UNAVAILABLE, exception.getErrorCode());

        store.remove(LOCK_ID, LockLevel.DC, FARM_ID);

        assertTrue(connection.getTable(TABLE).getData().isEmpty());
    }
}
