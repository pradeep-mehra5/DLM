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

package com.phonepe.dlm.lock.storage;

import java.time.Duration;

import com.phonepe.dlm.lock.level.LockLevel;

/**
 * Storage backend for lock records. A lock is held while its record exists; records expire after their TTL.
 * <p>
 * Implementations must make {@link #write(String, LockLevel, String, Duration, long)} create-only (fail with
 * {@code LOCK_UNAVAILABLE} if the record already exists) and {@link #remove(String, LockLevel, String, long)} an
 * atomic "delete only if it still carries {@code acquiredAt}", so that a holder whose lock expired cannot delete a lock
 * re-acquired by another holder.
 */
public interface ILockStore {
    /**
     * Prepares the backend (e.g. creates tables) before first use.
     */
    void initialize();

    /**
     * Writes the lock record if it does not already exist.
     *
     * @param lockId     the lock identifier
     * @param lockLevel  the lock level ({@code DC} or {@code XDC}), which determines where the record is stored
     * @param farmId     the farm (data centre) of the caller
     * @param ttlSeconds how long the lock record lives before the store expires it
     * @throws com.phonepe.dlm.exception.DLMException with {@code LOCK_UNAVAILABLE} if the lock is already held
     * @deprecated Use {@link #write(String, LockLevel, String, Duration, long)}, which records the acquisition
     * timestamp so that {@link #remove(String, LockLevel, String, long)} can verify ownership on release.
     */
    @Deprecated
    void write(String lockId, LockLevel lockLevel, String farmId, Duration ttlSeconds);

    /**
     * Removes the lock record unconditionally.
     *
     * @param lockId     the lock identifier
     * @param lockLevel  the lock level ({@code DC} or {@code XDC}), which determines where the record is stored
     * @param farmId     the farm (data centre) of the caller
     * @deprecated Unsafe if the lock has expired and been re-acquired by another holder: this deletes the other
     * holder's lock. Use {@link #remove(String, LockLevel, String, long)}.
     */
    @Deprecated
    void remove(String lockId, LockLevel lockLevel, String farmId);

    /**
     * Writes the lock record if it does not already exist, stamped with {@code acquiredAt}.
     * <p>
     * The default implementation delegates to the deprecated {@link #write(String, LockLevel, String, Duration)}
     * (without storing {@code acquiredAt}) so that existing custom stores keep working. Stores should override it
     * to persist {@code acquiredAt}.
     *
     * @param lockId     the lock identifier
     * @param lockLevel  the lock level ({@code DC} or {@code XDC}), which determines where the record is stored
     * @param farmId     the farm (data centre) of the caller
     * @param ttlSeconds how long the lock record lives before the store expires it
     * @param acquiredAt epoch millis identifying this acquisition; must be passed back to
     *                   {@link #remove(String, LockLevel, String, long)}
     * @throws com.phonepe.dlm.exception.DLMException with {@code LOCK_UNAVAILABLE} if the lock is already held
     */
    default void write(String lockId, LockLevel lockLevel, String farmId, Duration ttlSeconds, long acquiredAt) {
        write(lockId, lockLevel, farmId, ttlSeconds);
    }

    /**
     * Removes the lock record only if it still carries {@code acquiredAt}, i.e. it is still held by the caller and
     * has not expired and been re-acquired by someone else.
     * <p>
     * The default implementation delegates to the deprecated, unconditional
     * {@link #remove(String, LockLevel, String)} and returns {@code true}, preserving the behaviour of existing custom
     * stores. Stores should override it to perform the ownership check.
     *
     * @param lockId     the lock identifier
     * @param lockLevel  the lock level ({@code DC} or {@code XDC}), which determines where the record is stored
     * @param farmId     the farm (data centre) of the caller
     * @param acquiredAt the value passed to {@link #write(String, LockLevel, String, Duration, long)} on acquisition
     * @return {@code true} if the caller's lock record was removed; {@code false} if it no longer exists or belongs to
     * another holder
     */
    default boolean remove(String lockId, LockLevel lockLevel, String farmId, long acquiredAt) {
        remove(lockId, lockLevel, farmId);
        return true;
    }

    /**
     * Releases backend resources such as connections.
     */
    void close();
}
