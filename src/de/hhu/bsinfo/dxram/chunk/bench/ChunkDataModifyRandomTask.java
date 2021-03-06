/*
 * Copyright (C) 2018 Heinrich-Heine-Universitaet Duesseldorf, Institute of Computer Science,
 * Department Operating Systems
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package de.hhu.bsinfo.dxram.chunk.bench;

import com.google.gson.annotations.Expose;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hhu.bsinfo.dxram.chunk.ChunkAnonService;
import de.hhu.bsinfo.dxram.chunk.ChunkService;
import de.hhu.bsinfo.dxram.data.ChunkAnon;
import de.hhu.bsinfo.dxram.data.ChunkIDRanges;
import de.hhu.bsinfo.dxram.data.ChunkState;
import de.hhu.bsinfo.dxram.ms.Signal;
import de.hhu.bsinfo.dxram.ms.Task;
import de.hhu.bsinfo.dxram.ms.TaskContext;
import de.hhu.bsinfo.dxutils.eval.Stopwatch;
import de.hhu.bsinfo.dxutils.serialization.Exporter;
import de.hhu.bsinfo.dxutils.serialization.Importer;
import de.hhu.bsinfo.dxutils.serialization.ObjectSizeUtil;

/**
 * Task to modify (get/put) chunks on a node using different patterns
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 25.01.2017
 */
public class ChunkDataModifyRandomTask implements Task {
    private static final Logger LOGGER = LogManager.getFormatterLogger(ChunkDataModifyRandomTask.class.getSimpleName());

    private static final int PATTERN_GET_LOCAL = 0;
    private static final int PATTERN_GET_PUT_LOCAL = 1;
    private static final int PATTERN_GET_REMOTE_ONLY_SUCCESSOR = 2;
    private static final int PATTERN_GET_PUT_REMOTE_ONLY_SUCCESSOR = 3;
    private static final int PATTERN_GET_REMOTE_ONLY_RANDOM = 4;
    private static final int PATTERN_GET_PUT_REMOTE_ONLY_RANDOM = 5;
    private static final int PATTERN_GET_REMOTE_LOCAL_MIXED_RANDOM = 6;
    private static final int PATTERN_GET_PUT_REMOTE_LOCAL_MIXED_RANDOM = 7;

    @Expose
    private int m_numThreads = 1;
    @Expose
    private long m_opCount = 100000;
    @Expose
    private int m_chunkBatch = 10;
    @Expose
    private boolean m_writeContentsAndVerify = false;
    @Expose
    private boolean m_continousChunksPerBatch = false;
    @Expose
    private int m_pattern = PATTERN_GET_LOCAL;

    @Override
    public int execute(final TaskContext p_ctx) {
        boolean remote = m_pattern > PATTERN_GET_PUT_LOCAL;
        boolean doPut = m_pattern % 2 == 1;

        if (remote && p_ctx.getCtxData().getSlaveNodeIds().length < 2) {
            System.out.println("Not enough slaves (min 2) to execute this task");
            return -1;
        }

        ChunkService chunkService = p_ctx.getDXRAMServiceAccessor().getService(ChunkService.class);
        ChunkAnonService chunkAnonService = p_ctx.getDXRAMServiceAccessor().getService(ChunkAnonService.class);

        ChunkIDRanges allChunkRanges = ChunkTaskUtils.getChunkRangesForTestPattern(m_pattern / 2, p_ctx, chunkService);
        long totalChunkCount = allChunkRanges.getTotalChunkIDsOfRanges();
        long[] chunkCountsPerThread = ChunkTaskUtils.distributeChunkCountsToThreads(totalChunkCount, m_numThreads);
        ChunkIDRanges[] chunkRangesPerThread = ChunkTaskUtils.distributeChunkRangesToThreads(chunkCountsPerThread, allChunkRanges);
        long[] operationsPerThread = ChunkTaskUtils.distributeChunkCountsToThreads(m_opCount, m_numThreads);

        Thread[] threads = new Thread[m_numThreads];
        Stopwatch[] time = new Stopwatch[m_numThreads];
        for (int i = 0; i < time.length; i++) {
            time[i] = new Stopwatch();
        }

        System.out.printf("Modifying (and checking %d) %d random chunks (pattern %d) in batches of %d chunk(s) with %d thread(s)...\n",
                m_writeContentsAndVerify ? 1 : 0, m_opCount, m_pattern, m_chunkBatch, m_numThreads);

        for (int i = 0; i < threads.length; i++) {
            int threadIdx = i;
            threads[i] = new Thread(() -> {

                int counter = 0;

                long[] chunkIds = new long[m_chunkBatch];
                long operations = operationsPerThread[threadIdx];
                ChunkIDRanges chunkRanges = chunkRangesPerThread[threadIdx];

                if (operationsPerThread[threadIdx] % m_chunkBatch > 0) {
                    operations++;
                }

                // happens if no chunks were created
                if (!chunkRanges.isEmpty()) {
                    while (operations > 0) {
                        int batchCnt = 0;

                        if (!m_continousChunksPerBatch) {
                            // all random chunk IDs for batch
                            while (operations > 0 && batchCnt < chunkIds.length) {
                                chunkIds[batchCnt] = chunkRanges.getRandomChunkIdOfRanges();

                                operations--;
                                batchCnt++;
                            }
                        } else {
                            while (operations > 0 && batchCnt < chunkIds.length) {
                                operations--;
                                batchCnt++;
                            }

                            // ensure continuous chunk IDs
                            while (true) {
                                chunkIds[0] = chunkRanges.getRandomChunkIdOfRanges();
                                if (chunkRanges.isInRanges(chunkIds[0] + batchCnt)) {
                                    for (int j = 0; j < batchCnt; j++) {
                                        chunkIds[j] = chunkIds[0] + j;
                                    }

                                    break;
                                }
                            }
                        }

                        ChunkAnon[] chunks = new ChunkAnon[chunkIds.length];
                        time[threadIdx].start();
                        int ret = chunkAnonService.get(chunks, chunkIds);
                        time[threadIdx].stopAndAccumulate();

                        if (ret != chunks.length) {
                            for (int j = 0; j < chunks.length; j++) {
                                if (chunks[j].getState() != ChunkState.OK) {
                                    LOGGER.error("Error getting chunk %s\n", chunks[j]);
                                }
                            }
                        }

                        if (m_writeContentsAndVerify) {
                            for (int j = 0; j < chunks.length; j++) {
                                if (chunks[j].getState() == ChunkState.OK) {
                                    byte[] buffer = chunks[j].getData();

                                    for (int k = 0; k < buffer.length; k++) {
                                        buffer[k] = (byte) k;
                                    }
                                }
                            }
                        }

                        if (doPut) {
                            time[threadIdx].start();
                            ret = chunkAnonService.put(chunks);
                            time[threadIdx].stopAndAccumulate();

                            if (ret != chunks.length) {
                                for (int j = 0; j < chunks.length; j++) {
                                    if (chunks[j].getState() != ChunkState.OK) {
                                        LOGGER.error("Error putting chunk %s\n", chunks[j]);
                                    }
                                }
                            }

                            if (m_writeContentsAndVerify) {
                                ChunkAnon[] chunksToVerify = new ChunkAnon[chunkIds.length];
                                ret = chunkAnonService.get(chunksToVerify, chunkIds);

                                if (ret != chunkIds.length) {
                                    for (int j = 0; j < chunksToVerify.length; j++) {
                                        if (chunksToVerify[j].getState() != ChunkState.OK) {
                                            LOGGER.error("Error getting chunk to verify %s\n", chunksToVerify[j]);
                                        }
                                    }
                                }

                                for (int j = 0; j < chunksToVerify.length; j++) {
                                    if (chunksToVerify[j].getState() != ChunkState.OK) {
                                        continue;
                                    }

                                    byte[] buffer = chunksToVerify[j].getData();

                                    for (int k = 0; k < buffer.length; k++) {
                                        if (buffer[k] != (byte) k) {
                                            LOGGER.error("Contents of chunk %s are not matching written contents: 0x%X != 0x%X", chunksToVerify[j], buffer[k],
                                                    k);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        boolean threadJoinFailed = false;
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (final InterruptedException e) {
                LOGGER.error("Joining thread failed", e);
                threadJoinFailed = true;
            }
        }

        if (threadJoinFailed) {
            return -2;
        }

        System.out.print("Times per thread:");
        for (int i = 0; i < m_numThreads; i++) {
            System.out.printf("\nThread-%d: %f sec", i, time[i].getAccumulatedTimeAsUnit().getSecDouble());
        }
        System.out.println();

        // total time is measured by the slowest thread
        long totalTime = 0;
        for (int i = 0; i < m_numThreads; i++) {
            long t = time[i].getAccumulatedTime();
            if (t > totalTime) {
                totalTime = t;
            }
        }

        System.out.printf("Total time: %f sec\n", totalTime / 1000.0 / 1000.0 / 1000.0);
        System.out.printf("Throughput: %f chunks/sec\n", 1000.0 * 1000.0 * 1000.0 / ((double) totalTime / m_opCount));

        return 0;
    }

    @Override
    public void handleSignal(final Signal p_signal) {

    }

    @Override
    public void exportObject(final Exporter p_exporter) {
        p_exporter.writeInt(m_numThreads);
        p_exporter.writeLong(m_opCount);
        p_exporter.writeInt(m_chunkBatch);
        p_exporter.writeBoolean(m_writeContentsAndVerify);
        p_exporter.writeBoolean(m_continousChunksPerBatch);
        p_exporter.writeInt(m_pattern);
    }

    @Override
    public void importObject(final Importer p_importer) {
        m_numThreads = p_importer.readInt(m_numThreads);
        m_opCount = p_importer.readLong(m_opCount);
        m_chunkBatch = p_importer.readInt(m_chunkBatch);
        m_writeContentsAndVerify = p_importer.readBoolean(m_writeContentsAndVerify);
        m_continousChunksPerBatch = p_importer.readBoolean(m_continousChunksPerBatch);
        m_pattern = p_importer.readInt(m_pattern);
    }

    @Override
    public int sizeofObject() {
        return Integer.BYTES * 3 + ObjectSizeUtil.sizeofBoolean() * 2 + Long.BYTES;
    }
}
