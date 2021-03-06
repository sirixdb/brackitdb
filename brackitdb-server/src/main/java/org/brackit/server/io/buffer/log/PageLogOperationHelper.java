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
package org.brackit.server.io.buffer.log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.buffer.log.PageLogOperation.PageUnitPair;
import org.brackit.server.tx.log.LogException;
import org.brackit.server.tx.log.LogOperation;
import org.brackit.server.tx.log.LogOperationHelper;

/**
 * @author Sebastian Baechle
 * 
 */
public class PageLogOperationHelper implements LogOperationHelper {
	private static final ArrayList<Byte> operationTypes;

	static {
		operationTypes = new ArrayList<Byte>();
		operationTypes.add(PageLogOperation.ALLOCATE);
		operationTypes.add(PageLogOperation.DEALLOCATE);
		operationTypes.add(PageLogOperation.DEALLOCATE_DEFERRED);
		operationTypes.add(PageLogOperation.CREATE_UNIT);
		operationTypes.add(PageLogOperation.DROP_UNIT);
	}

	@Override
	public Collection<Byte> getOperationTypes() {
		return operationTypes;
	}

	@Override
	public LogOperation fromBytes(byte type, ByteBuffer buffer)
			throws LogException {

		switch (type) {
		case PageLogOperation.ALLOCATE:
			return createAllocateLogOperation(buffer);
		case PageLogOperation.DEALLOCATE:
			return createDeallocateLogOperation(buffer);
		case PageLogOperation.DEALLOCATE_DEFERRED:
			return createDeferredLogOperation(buffer);
		case PageLogOperation.CREATE_UNIT:
			return createUnitLogOperation(true, buffer);
		case PageLogOperation.DROP_UNIT:
			return createUnitLogOperation(false, buffer);
		default:
			throw new LogException("Unknown operation type: %s.", type);
		}
	}

	private LogOperation createAllocateLogOperation(ByteBuffer bb) {
		PageID pageID = PageID.read(bb);
		int unitID = bb.getInt();
		return new AllocateLogOperation(pageID, unitID);
	}

	private LogOperation createDeallocateLogOperation(ByteBuffer bb) {
		PageID pageID = PageID.read(bb);
		int unitID = bb.getInt();
		return new DeallocateLogOperation(pageID, unitID);
	}

	private LogOperation createDeferredLogOperation(ByteBuffer bb) {

		// read containerID
		int containerID = bb.getInt();

		// read single pages
		int length = bb.getInt();
		PageUnitPair[] pages = new PageUnitPair[length];

		for (int i = 0; i < length; i++) {
			PageID pageID = PageID.read(bb);
			int unitID = bb.getInt();
			pages[i] = new PageUnitPair(pageID, unitID);
		}

		// read units
		length = bb.getInt();
		int[] units = new int[length];

		for (int i = 0; i < length; i++) {
			units[i] = bb.getInt();
		}

		return new DeferredLogOperation(containerID, pages, units);
	}

	private LogOperation createUnitLogOperation(boolean create, ByteBuffer bb) {
		int containerID = bb.getInt();
		int unitID = bb.getInt();
		return (create ? new CreateUnitLogOperation(containerID, unitID)
				: new DropUnitLogOperation(containerID, unitID));
	}
}
