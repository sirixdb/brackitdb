/*
 * [New BSD License]
 * Copyright (c) 2011-2012, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Brackit Project Team nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.io.file;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.brackit.server.util.BitArrayWrapper;
import org.brackit.server.util.BitMap;
import org.brackit.server.util.BitMapTree;
import org.brackit.server.util.FileUtil;
import org.brackit.xquery.util.log.Logger;
import org.brackit.server.xquery.function.bdb.statistics.InfoContributor;
import org.brackit.server.xquery.function.bdb.statistics.ListContainers;

/**
 * Default BlockSpace implementation using one BlockFile, simple 1:1 mapping
 * from LBA to block numbers in the file.
 * 
 * This is used to replace the old XTCioMgr01 implementation. However, the
 * allocated/released info is not stored in the block header, but only in the
 * BitArray for better performance. The BitArray can be serialized in a log-like
 * style to achieve the same effect as that of the old implementation.
 * 
 * @author Ou Yi
 * @author Sebastian Baechle
 * @author Martin Hiller
 * 
 */
public class DefaultBlockSpace implements BlockSpace, InfoContributor {

	private static final Logger log = Logger.getLogger(DefaultBlockSpace.class);

	static final byte BLOCK_IN_USE = (byte) 0xFF;
	static final int BLOCK_HEADER_LENGTH = 1;

	final int id;
	int blkSize;

	String storeRoot;
	String dataFileName;
	String metaFileName;
	BlockFile dataFile;
	RandomAccessFile metaFile;
	int iniSize;
	int extSize;
	BitMap freeSpaceInfo;

	Map<Integer, BitMap> unitMap;
	int nextUnit = 1;

	int nextFreeBlockHint;
	boolean closed = true;
	public byte[] iniBlock;
	private int freeBlockCount;
	private int usedBlockCount;

	/**
	 * Creates the object (in memory) representing the BlockSpace.
	 * 
	 * @param id
	 */
	public DefaultBlockSpace(String root, int id) {
		this.id = id;

		storeRoot = root;
		dataFileName = storeRoot + File.separator + id + ".cnt";
		metaFileName = dataFileName + ".meta";
		ListContainers.add(this);
	}

	@Override
	public boolean isUsed(int lba) {
		synchronized (freeSpaceInfo) {
			return lba >= 0 && lba < freeSpaceInfo.logicalSize()
					&& freeSpaceInfo.get(lba);
		}
	}

	@Override
	public int allocate(int lba, int unitID, boolean force)
			throws StoreException {
		freeBlockCount--;
		usedBlockCount++;
		return allocateImpl(lba, unitID, force);
	}

	private synchronized int allocateImpl(int lba, int unitID, boolean force)
			throws StoreException {

		BitMap unit = unitMap.get(unitID);
		if (unit == null) {
			throw new StoreException(String.format("UnitID %s does not exist!",
					unitID));
		}

		// lba >= 0, the caller decides which block to allocate
		if (lba >= 0) {
			while (lba >= freeSpaceInfo.logicalSize()) {
				extendStore();
			}
			if (!force && freeSpaceInfo.get(lba)) {
				throw new StoreException("block already allocated, lba: " + lba);
			}
			nextFreeBlockHint = lba + 1;
			freeSpaceInfo.set(lba);
			unit.set(lba);
			return lba; // Note we use one to one mapping for lba >> blockNo
		}

		// lba < 0, we decide which block to allocate

		int blockNo = -1;
		// first try to find a block without extending the space
		if (nextFreeBlockHint < freeSpaceInfo.logicalSize()) {
			blockNo = freeSpaceInfo.nextClearBit(nextFreeBlockHint);
			if (blockNo >= 0) {
				nextFreeBlockHint = blockNo + 1;
				freeSpaceInfo.set(blockNo);
				unit.set(blockNo);
				return blockNo; // Note we use one to one mapping for lba >>
				// blockNo
			}
		}

		// there is no free block in the existing space
		nextFreeBlockHint = freeSpaceInfo.logicalSize();
		extendStore();
		blockNo = freeSpaceInfo.nextClearBit(nextFreeBlockHint);

		nextFreeBlockHint = blockNo + 1;
		freeSpaceInfo.set(blockNo);
		unit.set(blockNo);
		return blockNo; // Note we use one to one mapping for lba >> blockNo
	}

	private synchronized void extendStore() throws StoreException {
		
		int newStoreSize = freeSpaceInfo.logicalSize() + extSize;
		extendStore(newStoreSize);
	}
	
	private synchronized void extendStore(int newSize) throws StoreException {
		
		log.debug(String.format(
				"extending block space to size %s", newSize));

		try {
			
			if (dataFile.getBlockCnt() < newSize) {
				// extend data file
				for (int i = dataFile.getBlockCnt(); i < newSize; i++) {
					dataFile.write(i, iniBlock, 1);
				}
			}

			int ext = newSize - freeSpaceInfo.logicalSize();
			if (ext > 0) {
				// extend meta data
				freeSpaceInfo.extendTo(newSize);
				freeBlockCount += ext;
				
				for (BitMap unit : unitMap.values()) {
					unit.extendTo(newSize);
				}
			}

		} catch (FileException e) {
			throw new StoreException(e);
		}
		
	}

	@Override
	public int sizeOfHeader() {
		return BLOCK_HEADER_LENGTH;
	}

	@Override
	public void release(int lba, int unitID, boolean force)
			throws StoreException {
		releaseImpl(lba, unitID, force);
		usedBlockCount--;
		freeBlockCount++;
	}

	private synchronized void releaseImpl(int lba, int unitID, boolean force)
			throws StoreException {

		// mark block as free
		freeBlock(lba, force);

		// release block from unit
		BitMap unit = unitMap.get(unitID);
		if (unit != null && unit.get(lba)) {
			unit.clear(lba);
		} else {
			if (force) {
				for (BitMap u : unitMap.values()) {
					u.clear(lba);
				}
			} else {
				throw new StoreException(String.format(
						"Block %s does not belong to unit %s.", lba, unitID));
			}
		}
	}

	private void freeBlock(int lba, boolean force) throws StoreException {

		if (!force && !freeSpaceInfo.get(lba)) {
			throw new StoreException("invalid lba, block not in use");
		}
		freeSpaceInfo.clear(lba);
		if (nextFreeBlockHint > lba) {
			nextFreeBlockHint = lba;
		}
	}

	@Override
	public synchronized void close() throws StoreException {
		log.info("closing block space: " + id);
		ListContainers.remove(this);

		if (closed) {
			throw new StoreException("invalid state, space already closed");
		}
		try {
			dataFile.close();

			syncMeta();

			metaFile.close();

			closed = true;
		} catch (FileException e) {
			throw new StoreException(e);
		} catch (IOException e) {
			throw new StoreException(e);
		}
		log.info("block space " + id + " closed");
	}

	private void syncMeta() throws StoreException {
		try {

			// write header
			metaFile.seek(0);
			metaFile.writeInt(blkSize);
			metaFile.writeInt(iniSize);
			metaFile.writeInt(extSize);

			// mark metadata as inconsistent before proceeding
			// metaFile.writeInt(1);
			// metaFile.getFD().sync();

			// write freeSpaceInfo
			freeSpaceInfo.write(metaFile);

			metaFile.writeInt(freeBlockCount);
			metaFile.writeInt(usedBlockCount);

			// metaFile.getFD().sync();

			Set<Entry<Integer, BitMap>> units = unitMap.entrySet();
			// store number of units
			metaFile.writeInt(units.size());

			// sync unit mappings
			for (Entry<Integer, BitMap> unit : units) {
				metaFile.writeInt(unit.getKey());
				unit.getValue().write(metaFile);
			}

			metaFile.getFD().sync();

			// if (markConsistent) {
			// // everything is OK. mark consistent and flush
			// metaFile.seek(12);
			// metaFile.writeInt(0);
			// metaFile.getFD().sync();
			// }

		} catch (IOException e) {
			throw new StoreException("Error syncing meta file.");
		}
	}

	@Override
	public void create(int blkSize, int iniSize, double extent)
			throws StoreException {

		File storeRootDir = new File(storeRoot);

		if (log.isTraceEnabled()) {
			log.trace("storage root directory: "
					+ storeRootDir.getAbsolutePath());
		}
		if (storeRootDir.exists()) {
			if (log.isInfoEnabled()) {
				log.warn("storage root directory exists, deleting...");
			}
			FileUtil.forceRemove(storeRootDir);
		}
		storeRootDir.mkdirs();

		if (extent > 1) {
			log.warn("ext ratio larger than 1.0");
		}
		this.blkSize = blkSize;
		this.iniSize = iniSize;
		this.extSize = (int) (iniSize * extent);

		this.usedBlockCount = 0;
		this.freeBlockCount = iniSize;

		// note byte 0 of the INIT_BLOCK is 0, indicating a block not in use,
		// see create()
		iniBlock = new byte[blkSize];
		for (int i = 0; i < iniBlock.length; i++) {
			iniBlock[i] = (byte) 0;
		}
		try {
			File data = new File(dataFileName);
			if (data.exists()) {
				data.delete();
			}
			File meta = new File(metaFileName);
			if (meta.exists()) {
				meta.delete();
			}

			dataFile = new RAFBlockFile(dataFileName, blkSize);
			dataFile.open();
			for (int i = 0; i < iniSize; i++) {
				dataFile.write(i, iniBlock, 1);
			}
			dataFile.close();

			// no need to clear the bits individually, they are all clear by
			// default
			freeSpaceInfo = new BitArrayWrapper(iniSize);

			// the first block reserved, for compatibility with the code which
			// depends on the old IOMgr
			freeSpaceInfo.set(0);

			// start with empty unit mapping
			unitMap = new HashMap<Integer, BitMap>();
			nextUnit = 1;

			metaFile = new RandomAccessFile(metaFileName,
					Constants.FILE_MODE_UNSY);

			syncMeta();

			metaFile.close();

		} catch (FileException e) {
			throw new StoreException(e);
		} catch (IOException e) {
			throw new StoreException(e);
		}
	}

	@Override
	public synchronized void open() throws StoreException {
		log.info("opening block space: " + id);
		if (!closed) {
			throw new StoreException("invalid state, space already opened");
		}
		try {

			metaFile = new RandomAccessFile(metaFileName,
					Constants.FILE_MODE_UNSY);

			blkSize = metaFile.readInt();
			iniSize = metaFile.readInt();
			extSize = metaFile.readInt();

			// boolean consistent = (metaFile.readInt() == 0);

			iniBlock = new byte[blkSize];
			for (int i = 0; i < iniBlock.length; i++) {
				iniBlock[i] = (byte) 0;
			}
			dataFile = new RAFBlockFile(dataFileName, blkSize);
			dataFile.open();

			// read freeSpaceInfo from meta file
			freeSpaceInfo = new BitArrayWrapper();
			freeSpaceInfo.read(metaFile);

			freeBlockCount = metaFile.readInt();
			usedBlockCount = metaFile.readInt();

			// read unit files from meta file
			unitMap = new HashMap<Integer, BitMap>();
			nextUnit = 1;

			int unitCount = metaFile.readInt();
			for (int i = 0; i < unitCount; i++) {
				int unitID = metaFile.readInt();
				BitMap unit = new BitMapTree();
				unit.read(metaFile);
				unitMap.put(unitID, unit);
				nextUnit = Math.max(nextUnit, unitID + 1);
			}
			
			// extend meta or data file to the common store size
			int storeSize = Math.max(dataFile.getBlockCnt(), freeSpaceInfo.logicalSize());
			extendStore(storeSize);

			closed = false;
		} catch (FileException e) {
			throw new StoreException(e);
		} catch (IOException e) {
			throw new StoreException(e);
		}
		log.info("block space " + id + " initialized");
	}

	@Override
	public int read(int lba, byte[] buffer, int numBlocks)
			throws StoreException {
		return readImpl(lba, buffer, numBlocks);
	}

	private synchronized int readImpl(int lba, byte[] buffer, int numBlocks)
			throws StoreException {
		if (lba < 0 || lba >= freeSpaceInfo.logicalSize()) {
			throw new StoreException("invalid lba");
		}
		if (!freeSpaceInfo.get(lba)) {
			throw new StoreException("reading unused block: " + lba);
		}
		int readBlocks = 1;
		while ((readBlocks < numBlocks)
				&& (freeSpaceInfo.get(lba + readBlocks)))
			readBlocks++;
		try {
			dataFile.read(lba, buffer, readBlocks);
			// if (block[0] != BLOCK_IN_USE) {
			// log.warn("a released block loaded, this should not happen: " +
			// lba);
			// }
			return readBlocks;
		} catch (FileException e) {
			throw new StoreException(e);
		}
	}

	@Override
	public void write(int lba, byte[] buffer, int numBlocks)
			throws StoreException {
		writeImpl(lba, buffer, numBlocks);
	}

	private synchronized void writeImpl(int lba, byte[] block, int numBlocks)
			throws StoreException {
		if (lba < 0 || lba >= freeSpaceInfo.logicalSize()) {
			throw new StoreException("invalid lba");
		}
		try {
			dataFile.write(lba, block, numBlocks);
		} catch (FileException e) {
			throw new StoreException(e);
		}
	}

	@Override
	public synchronized void sync() throws StoreException {
		
		syncMeta();
		
		try {
			dataFile.sync();
		} catch (FileException e) {
			throw new StoreException("Error syncing data file.");
		}
	}
	
	@Override
	public synchronized void syncData() throws StoreException {		
		try {
			dataFile.sync();
		} catch (FileException e) {
			throw new StoreException("Error syncing data file.");
		}
	}

	@Override
	public int getId() {
		return this.id;
	}

	@Override
	public synchronized int sizeOfBlock() throws StoreException {
		if (!closed) {
			return blkSize;
		} else {
			throw new StoreException("invalid state, block space is closed");
		}

	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public String getInfo() {
		StringBuilder out = new StringBuilder();
		int blocks = freeSpaceInfo.logicalSize();
		int blocksUsed = usedBlockCount;
		int blockSize = blkSize;

		out.append("#" + id + " " + dataFileName);
		out.append(" (" + (blocks * blockSize / 1024) + "KB)");
		out.append(", " + blockSize + "B blocks");
		out.append(", total " + (blocks - 1));
		out.append(", used " + blocksUsed + " ("
				+ ((blocksUsed * 100) / blocks) + "%)");
		out.append("\n");
		return out.toString();
	}

	@Override
	public int getInfoID() {
		return InfoContributor.NO_ID;
	}

	@Override
	public synchronized int createUnit(int unitID, boolean force) throws StoreException {

		if (unitID <= 0) {
			// automatically assign unitID
			unitID = nextUnit++;
		} else {

			// check validity of requested unitID
			if (unitMap.get(unitID) != null) {
				
				if (force) {
					// return existing unit
					return unitID;
				}
				
				throw new StoreException(String.format(
						"The requested unitID %s already exists!", unitID));
			}

			nextUnit = Math.max(nextUnit, unitID + 1);
		}

		unitMap.put(unitID, new BitMapTree(freeSpaceInfo.logicalSize()));

		return unitID;
	}

	@Override
	public synchronized void dropUnit(int unitID, boolean force) throws StoreException {

		// remove unit from main memory map
		BitMap unit = unitMap.remove(unitID);
		if (unit == null) {
			
			if (force) {
				// nothing to deallocate
				return;
			}
			
			throw new StoreException(String.format(
					"Unit with ID %s does not exist!", unitID));
		}

		// free blocks that belong to this unit
		Iterator<Integer> setBits = unit.getSetBits();
		while (setBits.hasNext()) {
			freeBlock(setBits.next(), force);
		}
	}
}
