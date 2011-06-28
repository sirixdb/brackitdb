/*
 * [New BSD License]
 * Copyright (c) 2011, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.store.index.bracket.page;

import java.util.ArrayList;
import java.util.List;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.io.manager.BufferMgr;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.store.blob.BlobStoreAccessException;
import org.brackit.server.store.index.bracket.HintPageInformation;
import org.brackit.server.store.index.bracket.IndexOperationException;
import org.brackit.server.store.index.bracket.NavigationMode;
import org.brackit.server.store.index.bracket.SubtreeDeleteListener;
import org.brackit.server.store.page.BasePage;
import org.brackit.server.store.page.bracket.BracketKey;
import org.brackit.server.store.page.bracket.BracketNodeSequence;
import org.brackit.server.store.page.bracket.BracketPage;
import org.brackit.server.store.page.bracket.BracketPageException;
import org.brackit.server.store.page.bracket.BracketValue;
import org.brackit.server.store.page.bracket.DeletePreparation;
import org.brackit.server.store.page.bracket.DeletePrepareListener;
import org.brackit.server.store.page.bracket.DeleteSequenceInfo;
import org.brackit.server.store.page.bracket.DeleteSequencePreparation;
import org.brackit.server.store.page.bracket.DeweyIDBuffer;
import org.brackit.server.store.page.bracket.ExternalValueLoader;
import org.brackit.server.store.page.bracket.navigation.NavigationResult;
import org.brackit.server.store.page.bracket.navigation.NavigationStatus;
import org.brackit.server.tx.Tx;

/**
 * @author Martin Hiller
 * 
 */
public final class LeafBPContext extends AbstractBPContext implements Leaf {

	private static final boolean CHECK_BUFFER_INTEGRITY = false;
	
	private final float OCCUPANCY_RATE_DEFAULT = 0.5f;

	public static final int NEXT_PAGE_FIELD_NO = AbstractBPContext.RESERVED_SIZE;
	public static final int RESERVED_SIZE = NEXT_PAGE_FIELD_NO
			+ PageID.getSize();

	private class DeletePrepareListenerImpl implements DeletePrepareListener {

		private final List<PageID> externalPageIDs;
		private final SubtreeDeleteListener deleteListener;

		public DeletePrepareListenerImpl(List<PageID> externalPageIDs,
				SubtreeDeleteListener deleteListener) {
			this.externalPageIDs = externalPageIDs;
			this.deleteListener = deleteListener;
		}

		@Override
		public void externalNode(XTCdeweyID deweyID, PageID externalPageID,
				int level) throws BracketPageException {
			externalPageIDs.add(externalPageID);
			byte[] value = null;
			try {
				value = read(tx, externalPageID);
			} catch (BlobStoreAccessException e) {
				throw new BracketPageException(
						e,
						"Error reading externalized value at offset %s from blob %s",
						currentOffset, externalPageID);
			}

			try {
				deleteListener.deleteNode(deweyID, value, level);
			} catch (IndexOperationException e) {
				throw new BracketPageException(e);
			}
		}

		@Override
		public void node(XTCdeweyID deweyID, byte[] value, int level)
				throws BracketPageException {
			try {
				deleteListener.deleteNode(deweyID, value, level);
			} catch (IndexOperationException e) {
				throw new BracketPageException(e);
			}
		}

		@Override
		public void subtreeEnd() throws BracketPageException {
			try {
				deleteListener.subtreeEnd();
			} catch (IndexOperationException e) {
				throw new BracketPageException(e);
			}
		}
	}

	private class ExternalValueLoaderImpl implements ExternalValueLoader {

		@Override
		public byte[] loadExternalValue(PageID externalPageID)
				throws BracketPageException {
			try {
				return read(tx, externalPageID);
			} catch (BlobStoreAccessException e) {
				throw new BracketPageException(
						e,
						"Error reading externalized value at offset %s from blob %s",
						currentOffset, externalPageID);
			}
		}
	}

	private final ExternalValueLoader externalValueLoader = new ExternalValueLoaderImpl();

	private final BracketPage page;

	private int currentOffset;
	private DeweyIDBuffer currentDeweyID;

	private byte[] bufferedValue;
	private BracketKey.Type bufferedKeyType;

	public LeafBPContext(BufferMgr bufferMgr, Tx tx, BracketPage page) {
		super(bufferMgr, tx, page);
		this.page = page;
	}

	private void initBuffer() {
		if (currentDeweyID == null) {
			currentDeweyID = new DeweyIDBuffer();
			if (CHECK_BUFFER_INTEGRITY) {
				currentDeweyID.assignToPage(this.getPageID());
			}
		}
	}

	@Override
	public void setContext(XTCdeweyID deweyID, int offset) {

		if (currentDeweyID == null) {
			currentDeweyID = new DeweyIDBuffer();
			if (CHECK_BUFFER_INTEGRITY) {
				currentDeweyID.assignToPage(this.getPageID());
			}
		}

		currentDeweyID.setTo(deweyID);
		setCurrentOffset(offset);
	}

	@Override
	public void setContext(BracketContext context) {
		setContext(context.key, context.keyOffset);
	}

	@Override
	public BracketContext getContext() {
		return new BracketContext(currentOffset,
				currentOffset == BracketPage.BEFORE_LOW_KEY_OFFSET ? null
						: currentDeweyID.getDeweyID());
	}

	@Override
	public void assignDeweyIDBuffer(Leaf otherLeaf) {
		if (currentDeweyID != null) {
			throw new RuntimeException(
					"DeweyID buffer can only be set, if it is not initialized yet.");
		}
		LeafBPContext other = (LeafBPContext) otherLeaf;
		DeweyIDBuffer deweyIDBuffer = other.currentDeweyID;
		if (deweyIDBuffer != null) {
			if (CHECK_BUFFER_INTEGRITY) {
				deweyIDBuffer.assignToPage(this.getPageID());
			}
			this.currentDeweyID = deweyIDBuffer;
		}
	}

	@Override
	public PageID getNextPageID() {
		byte[] value = page.getHandle().page;
		return PageID.fromBytes(value, BasePage.BASE_PAGE_START_OFFSET
				+ NEXT_PAGE_FIELD_NO);
	}

	@Override
	public void setNextPageID(PageID nextPageID, boolean logged,
			long undoNextLSN) throws IndexOperationException {
		// LogOperation operation = null;
		//
		// if (logged)
		// {
		// operation =
		// BlinkIndexLogOperationHelper.createrPointerLogOperation(BlinkIndexLogOperation.PREV_PAGE,
		// getPageID(), getRootPageID(), getLowPageID(), prevPageID);
		// }

		byte[] value = page.getHandle().page;
		if (nextPageID != null) {
			nextPageID.toBytes(value, BasePage.BASE_PAGE_START_OFFSET
					+ NEXT_PAGE_FIELD_NO);
		} else {
			PageID.noPageToBytes(value, BasePage.BASE_PAGE_START_OFFSET
					+ NEXT_PAGE_FIELD_NO);
		}

		page.getHandle().setModified(true); // not covered by pageID to

		// if (logged)
		// {
		// log(tx, operation, undoNextLSN);
		// }
		// else
		// {
		// page.getHandle().setAssignedTo(tx);
		// }
	}

	@Override
	public void format(int unitID, PageID rootPageID, boolean logged,
			long undoNextLSN) throws IndexOperationException {
		this.format(true, unitID, rootPageID, 0, true, logged, undoNextLSN);
	}

	@Override
	public XTCdeweyID getKey() {
		if (currentOffset == BracketPage.BEFORE_LOW_KEY_OFFSET) {
			return null;
		} else {
			return currentDeweyID.getDeweyID();
		}
	}

	@Override
	public byte[] getValue() throws IndexOperationException {
		return getValue(currentOffset);
	}

	private byte[] getValue(int keyOffset) throws IndexOperationException {

		if (bufferedValue != null) {
			return bufferedValue;
		}

		BracketValue returnValue = page.getValue(keyOffset);
		byte[] value = returnValue.value;

		if (returnValue.externalized) {
			PageID blobPageID = PageID.fromBytes(value);

			try {
				value = read(tx, blobPageID);
			} catch (BlobStoreAccessException e) {
				throw new IndexOperationException(
						e,
						"Error reading externalized value at offset %s from blob %s",
						currentOffset, blobPageID);
			}
		}

		bufferedValue = value;
		return value;
	}

	@Override
	public boolean moveFirst() {
		initBuffer();

		NavigationResult navRes = page.navigateFirstCF(currentDeweyID);

		if (navRes.status == NavigationStatus.FOUND) {
			// adjust current offset
			setCurrentOffset(navRes.keyOffset, navRes.keyType);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean moveLast() {
		initBuffer();

		NavigationResult navRes = page.navigateLastCF(currentDeweyID);

		if (navRes.status == NavigationStatus.FOUND) {
			// adjust current offset
			setCurrentOffset(navRes.keyOffset, navRes.keyType);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean moveNext() {
		initBuffer();

		NavigationResult navRes = page.navigateNext(currentOffset,
				currentDeweyID, bufferedKeyType);

		if (navRes.status == NavigationStatus.FOUND) {
			// adjust current offset
			setCurrentOffset(navRes.keyOffset, navRes.keyType);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean setValue(byte[] value, boolean isStructureModification,
			boolean logged, long undoNextLSN) throws IndexOperationException {
		BracketValue oldValue = page.getValue(currentOffset);

		boolean externalize = externalizeValue(value);

		if (externalize) {
			value = externalize(value);
		}

		if (!page.update(value, externalize, currentOffset)) {
			return false;
		}

		if (oldValue.externalized) {
			deleteExternalized(oldValue.value);
		}

		return true;
	}

	@Override
	public String dump(String pageTitle) {
		if (pageTitle == null) {
			return page.toString();
		} else {
			return pageTitle + ":\n\n" + page;
		}
	}

	@Override
	public boolean insertRecordAfter(XTCdeweyID deweyID, byte[] record,
			int ancestorsToInsert, boolean logged, long undoNextLSN)
			throws IndexOperationException {
		initBuffer();

		boolean externalize = externalizeValue(record);

		byte[] pageRecord = record;
		if (externalize) {
			pageRecord = externalize(record);
		}

		// create bracket node sequence
		BracketNodeSequence sequence = BracketNodeSequence.fromNode(deweyID,
				pageRecord, ancestorsToInsert, externalize);

		int returnVal = page.insertSequenceAfter(sequence, currentOffset,
				currentDeweyID);

		if (returnVal == BracketPage.INSERTION_DUPLICATE) {
			throw new IndexOperationException(
					"Insertion key %s already exists in the index!", deweyID);
		} else if (returnVal == BracketPage.INSERTION_NO_SPACE) {
			if (externalize) {
				deleteExternalizedInstantly(pageRecord);
			}
			return false;
		} else {
			setCurrentOffset(returnVal);
			bufferedValue = record;
			return true;
		}
	}

	@Override
	public boolean insertRecord(XTCdeweyID deweyID, byte[] record,
			int ancestorsToInsert, boolean logged, long undoNextLSN)
			throws IndexOperationException {
		declareContextFree();
		initBuffer();

		boolean externalize = externalizeValue(record);

		byte[] pageRecord = record;
		if (externalize) {
			pageRecord = externalize(record);
		}

		// create bracket node sequence
		BracketNodeSequence sequence = BracketNodeSequence.fromNode(deweyID,
				pageRecord, ancestorsToInsert, externalize);

		int returnVal = page.insertSequence(sequence, currentDeweyID);

		if (returnVal == BracketPage.INSERTION_DUPLICATE) {
			throw new IndexOperationException(
					"Insertion key %s already exists in the index!", deweyID);
		} else if (returnVal == BracketPage.INSERTION_NO_SPACE) {
			if (externalize) {
				deleteExternalizedInstantly(pageRecord);
			}
			return false;
		} else {
			setCurrentOffset(returnVal);
			bufferedValue = record;
			return true;
		}
	}

	@Override
	public NavigationStatus navigate(NavigationMode navMode) {
		// assert(currentOffset != BracketPage.BEFORE_LOW_ID_KEYOFFSET);

		NavigationResult navRes = null;

		// call corresponding bracket page method
		switch (navMode) {
		case NEXT_SIBLING:
			navRes = page.navigateNextSibling(currentOffset, currentDeweyID,
					bufferedKeyType);
			break;
		case FIRST_CHILD:
			navRes = page.navigateFirstChild(currentOffset, currentDeweyID,
					bufferedKeyType);
			break;
		case LAST_CHILD:
			navRes = page.navigateLastChild(currentOffset, currentDeweyID);
			break;
		case PARENT:
			navRes = page.navigateParent(currentDeweyID);
			break;
		case PREVIOUS_SIBLING:
			navRes = page
					.navigatePreviousSibling(currentOffset, currentDeweyID);
			break;
		case NEXT_ATTRIBUTE:
			navRes = page.navigateNextAttribute(currentOffset, currentDeweyID);
			break;
		case TO_INSERT_POS:
		case TO_KEY:
			throw new IllegalArgumentException(String.format(
					"The NavigationMode %s is a context free operation!",
					navMode));
		default:
			throw new RuntimeException("Navigation Mode not supported!");
		}

		if (navRes.status == NavigationStatus.FOUND
				|| navRes.status == NavigationStatus.POSSIBLY_FOUND) {
			// adjust current offset
			setCurrentOffset(navRes.keyOffset, navRes.keyType);
		} else {
			moveBeforeFirst();
		}

		return navRes.status;
	}

	@Override
	public NavigationStatus navigateContextFree(XTCdeweyID referenceDeweyID,
			NavigationMode navMode) {
		declareContextFree();
		initBuffer();

		if (getEntryCount() == 0) {
			if (getNextPageID() != null) {
				throw new RuntimeException(
						"Only the last leaf page may be empty!");
			}
			if (navMode != NavigationMode.TO_INSERT_POS) {
				return NavigationStatus.BEFORE_FIRST;
			} else {
				return NavigationStatus.FOUND;
			}
		}

		NavigationResult navRes = null;

		// call corresponding bracket page method
		switch (navMode) {
		case TO_KEY:
			navRes = page.navigateToKey(referenceDeweyID, currentDeweyID);
			break;
		case TO_INSERT_POS:
			navRes = page.navigateToInsertPos(referenceDeweyID, currentDeweyID);
			break;
		case NEXT_SIBLING:
			navRes = page.navigateNextSiblingCF(referenceDeweyID,
					currentDeweyID);
			break;
		case FIRST_CHILD:
			navRes = page
					.navigateFirstChildCF(referenceDeweyID, currentDeweyID);
			break;
		case LAST_CHILD:
			navRes = page.navigateLastChildCF(referenceDeweyID, currentDeweyID);
			break;
		case PARENT:
			navRes = page.navigateParentCF(referenceDeweyID, currentDeweyID);
			break;
		case PREVIOUS_SIBLING:
			navRes = page.navigatePreviousSiblingCF(referenceDeweyID,
					currentDeweyID);
			break;
		case NEXT_ATTRIBUTE:
			navRes = page.navigateNextAttributeCF(referenceDeweyID,
					currentDeweyID);
			break;
		default:
			throw new RuntimeException("Navigation Mode not supported!");
		}

		if (navRes.status == NavigationStatus.FOUND
				|| navRes.status == NavigationStatus.POSSIBLY_FOUND) {
			// adjust current offset
			setCurrentOffset(navRes.keyOffset, navRes.keyType);
		} else {
			moveBeforeFirst();
		}

		return navRes.status;
	}

	@Override
	public boolean isBeforeFirst() {
		return (currentOffset == BracketPage.BEFORE_LOW_KEY_OFFSET);
	}

	@Override
	public boolean isLast() {
		return page.isLast(currentOffset);
	}

	@Override
	public void moveBeforeFirst() {
		currentOffset = BracketPage.BEFORE_LOW_KEY_OFFSET;
		bufferedValue = null;
		bufferedKeyType = null;
	}

	@Override
	public boolean split(Leaf rightLeaf, XTCdeweyID key, boolean forUpdate,
			boolean splitAfterCurrent, boolean logged, long undoNextLSN)
			throws IndexOperationException {

		try {
			LeafBPContext rightPage = (LeafBPContext) rightLeaf;

			this.initBuffer();
			rightPage.initBuffer();

			// prepare split
			DeletePreparation delPrep = splitAfterCurrent ? page
					.splitAfterCurrentPrepare(currentOffset, currentDeweyID)
					: page.splitPrepare(OCCUPANCY_RATE_DEFAULT, currentDeweyID);

			boolean returnLeftPage = (currentOffset < delPrep.startDeleteOffset);

			// get nodes that have to be transferred to the right page
			BracketNodeSequence nodes = page.getBracketNodeSequence(delPrep);

			// delete nodes from left page
			page.delete(delPrep, externalValueLoader);

			// set highKey/separator
			XTCdeweyID highKey = this.getHighKey();
			this.setHighKey(delPrep.startDeleteDeweyID);
			rightPage.setHighKey(highKey);

			// insert nodes into right page
			rightPage.page.insertSequenceAfter(nodes, rightPage.currentOffset,
					rightPage.currentDeweyID);

			// set correct context(s)
			if (returnLeftPage) {
				// right page
				rightPage.moveBeforeFirst();
			} else {
				// left page
				this.moveBeforeFirst();

				// right page
				if (forUpdate) {
					if (rightPage.navigateContextFree(key,
							NavigationMode.TO_KEY) != NavigationStatus.FOUND) {
						throw new RuntimeException("Error during leaf split!");
					}
				} else {
					if (rightPage.navigateContextFree(key,
							NavigationMode.TO_INSERT_POS) != NavigationStatus.FOUND) {
						throw new RuntimeException("Error during leaf split!");
					}
				}
			}

			return returnLeftPage;

		} catch (BracketPageException e) {
			throw new IndexOperationException(e);
		}
	}

	@Override
	public void copyContentAndContextTo(Leaf otherLeaf, boolean logged,
			long undoNextLSN) {
		LeafBPContext other = (LeafBPContext) otherLeaf;

		other.initBuffer();

		// copy content
		BracketNodeSequence content = page.getBracketNodeSequence(getLowKey(),
				BracketPage.LOW_KEY_OFFSET, BracketPage.KEY_AREA_END_OFFSET);
		other.page.insertSequenceAfter(content,
				BracketPage.BEFORE_LOW_KEY_OFFSET, other.currentDeweyID);

		// copy context
		otherLeaf.setContext(this.getContext());
	}

	@Override
	public int getOffset() {
		return currentOffset;
	}

	@Override
	public boolean isFirst() {
		return (currentOffset == BracketPage.LOW_KEY_OFFSET);
	}

	@Override
	public XTCdeweyID getLowKey() {
		return page.getLowKey();
	}

	@Override
	public byte[] getLowKeyBytes() {
		return page.getLowKeyBytes();
	}

	@Override
	public XTCdeweyID getHighKey() {
		return page.getContextDataAsDeweyID();
	}

	@Override
	public byte[] getHighKeyBytes() {
		return page.getContextData();
	}

	@Override
	public boolean setHighKey(XTCdeweyID highKey) {

		// store highkey as context data
		return page.setContextData(highKey.toBytes());

		// if (!page.setContextData(highKey.toBytes())) {
		// throw new RuntimeException(
		// String.format(
		// "Not enough space availabe to store the HighKey %s in leaf page %s!",
		// highKey, page.getPageID()));
		// }
	}

	public boolean setHighKeyBytes(byte[] highKeyBytes) {
		return page.setContextData(highKeyBytes);
	}

	@Override
	public BPContext createClone() throws IndexOperationException {
		LeafBPContext clone = new LeafBPContext(bufferMgr, tx, page);
		return clone;
	}

	@Override
	public int getEntryCount() {
		return page.getRecordCount();
	}

	@Override
	public int getHeight() {
		return 0;
	}

	@Override
	public boolean hasNext() {
		return !page.isLast(currentOffset);
	}

	@Override
	public boolean hasPrevious() {
		return (currentOffset > BracketPage.LOW_KEY_OFFSET);
	}

	@Override
	public void init() {
		moveBeforeFirst();
	}

	@Override
	public boolean isLastInLevel() {
		return (getNextPageID() == null);
	}

	@Override
	public void setCompressed(boolean compressed) {
	}

	@Override
	protected void setHeight(int height) {
	}

	@Override
	public boolean isLeaf() {
		return true;
	}

	@Override
	public boolean deleteSubtreeStart(SubtreeDeleteListener deleteListener,
			List<PageID> externalPageIDs, boolean isStructureModification,
			boolean logged, long undoNextLSN) throws IndexOperationException,
			EmptyLeafException {
		// assert(currentOffset != BracketPage.BEFORE_LOW_ID_KEYOFFSET);

		try {

			// prepare deletion
			boolean deleteExternalValues = (externalPageIDs == null);
			if (deleteExternalValues) {
				externalPageIDs = new ArrayList<PageID>();
			}

			DeletePreparation delPrep = null;

			if (currentOffset == BracketPage.LOW_KEY_OFFSET) {
				// maybe the page has to be deleted completely / unchained
				// -> DeleteListener's callback invocations should not be
				// propagated to the outside yet

				DelayedSubtreeDeleteListener delayedListener = new DelayedSubtreeDeleteListener(
						deleteListener);
				List<PageID> tempExternalPageIDs = deleteExternalValues ? externalPageIDs
						: new ArrayList<PageID>();
				DeletePrepareListener delPrepListener = new DeletePrepareListenerImpl(
						tempExternalPageIDs, delayedListener);
				delPrep = page.deleteSubtreeStartPrepare(currentOffset,
						currentDeweyID, delPrepListener);

				if (delPrep.endDeleteOffset == BracketPage.KEY_AREA_END_OFFSET) {
					// leaf needs to be unchained
					throw EmptyLeafException.EMPTY_LEAF_EXCEPTION;
				}

				// propagate DeleteListener's callback methods
				delayedListener.flush();
				if (!deleteExternalValues) {
					externalPageIDs.addAll(tempExternalPageIDs);
				}

			} else {
				DeletePrepareListener delPrepListener = new DeletePrepareListenerImpl(
						externalPageIDs, deleteListener);
				delPrep = page.deleteSubtreeStartPrepare(currentOffset,
						currentDeweyID, delPrepListener);
			}

			// delete external values
			if (deleteExternalValues) {
				deleteExternalized(externalPageIDs);
			}

			// delete
			page.delete(delPrep, externalValueLoader);

			// reset context information
			moveBeforeFirst();

			return (delPrep.endDeleteOffset != BracketPage.KEY_AREA_END_OFFSET);

		} catch (BracketPageException e) {
			throw new IndexOperationException(e);
		}
	}

	@Override
	public boolean deleteSubtreeEnd(XTCdeweyID subtreeRoot,
			SubtreeDeleteListener deleteListener, List<PageID> externalPageIDs,
			boolean isStructureModification, boolean logged, long undoNextLSN)
			throws IndexOperationException {
		try {
			declareContextFree();
			initBuffer();

			// prepare deletion
			boolean deleteExternalValues = (externalPageIDs == null);
			if (deleteExternalValues) {
				externalPageIDs = new ArrayList<PageID>();
			}

			DeletePrepareListener delPrepListener = new DeletePrepareListenerImpl(
					externalPageIDs, deleteListener);
			DeletePreparation delPrep = page.deleteSubtreeEndPrepare(
					subtreeRoot, currentDeweyID, delPrepListener);

			if (delPrep == null) {
				// nothing to delete
				return true;
			}

			// delete external values
			if (deleteExternalValues) {
				deleteExternalized(externalPageIDs);
			}

			if (delPrep.endDeleteOffset == BracketPage.KEY_AREA_END_OFFSET) {
				// entire page needs to be deleted -> do not physically delete
				// the page, but let it unchain by the bracket tree
				return false;
			} else {
				// delete
				page.delete(delPrep, null);
				return true;
			}

		} catch (BracketPageException e) {
			throw new IndexOperationException(e);
		}
	}

	public static void appendPageContextInfo(byte[] buffer, BracketPage page,
			StringBuilder out) {

		out.append("\tPrevious Page: ");
		out.append(PageID.fromBytes(buffer, BasePage.BASE_PAGE_START_OFFSET
				+ PREV_PAGE_FIELD_NO));
		out.append("\n\tNext Page: ");
		out.append(PageID.fromBytes(buffer, BasePage.BASE_PAGE_START_OFFSET
				+ NEXT_PAGE_FIELD_NO));
		out.append("\n\tHighKey: ");
		out.append(page.getContextDataAsDeweyID());

	}

	@Override
	public void assignDeweyIDBuffer(DeweyIDBuffer deweyIDBuffer) {
		if (currentDeweyID != null) {
			throw new RuntimeException(
					"DeweyID buffer can only be set, if it is not initialized yet.");
		}
		if (CHECK_BUFFER_INTEGRITY) {
			deweyIDBuffer.assignToPage(this.getPageID());
		}
		this.currentDeweyID = deweyIDBuffer;
	}

	@Override
	public DeweyIDBuffer getDeweyIDBuffer() {
		return currentDeweyID;
	}

	@Override
	public void cleanup() {
		super.cleanup();
		if (CHECK_BUFFER_INTEGRITY && currentDeweyID != null) {
			currentDeweyID.deassignFromPage(this.getPageID());
		}
	}

	@Override
	public DeweyIDBuffer deassignDeweyIDBuffer() {
		moveBeforeFirst();
		if (CHECK_BUFFER_INTEGRITY && currentDeweyID != null) {
			currentDeweyID.deassignFromPage(this.getPageID());
		}
		DeweyIDBuffer returnBuffer = currentDeweyID;
		currentDeweyID = null;
		return returnBuffer;
	}

	private void setCurrentOffset(int offset) {
		bufferedKeyType = null;
		if (offset <= BracketPage.LOW_KEY_OFFSET
				|| offset != currentOffset + BracketKey.PHYSICAL_LENGTH) {
			bufferedValue = null;
		}
		currentOffset = offset;
	}

	private void setCurrentOffset(int offset, BracketKey.Type keyType) {
		bufferedKeyType = keyType;
		if (offset <= BracketPage.LOW_KEY_OFFSET
				|| offset != currentOffset + BracketKey.PHYSICAL_LENGTH) {
			bufferedValue = null;
		}
		currentOffset = offset;
	}

	private void declareContextFree() {
		if (currentOffset != BracketPage.BEFORE_LOW_KEY_OFFSET) {
			throw new RuntimeException(
					"Context free operation! The current leaf position will not be preserved!");
		}
	}

	@Override
	public DeleteSequenceInfo deleteSequence(XTCdeweyID leftBorderDeweyID,
			XTCdeweyID rightBorderDeweyID, boolean logged, long undoNextLSN)
			throws IndexOperationException {

		try {
			declareContextFree();
			initBuffer();

			// prepare deletion
			DeleteSequencePreparation delPrep = page.deleteSequencePrepare(
					leftBorderDeweyID, rightBorderDeweyID, currentDeweyID);

			if (delPrep.deletePreparation == null
					|| delPrep.deleteSequenceInfo.producesEmptyLeaf) {
				// nothing to delete OR page needs to be unchained
			} else {
				// delete
				page.delete(delPrep.deletePreparation, externalValueLoader);
			}

			return delPrep.deleteSequenceInfo;

		} catch (BracketPageException e) {
			throw new IndexOperationException(e);
		}
	}

	@Override
	public boolean insertSequenceAfter(BracketNodeSequence nodes,
			boolean logged, long undoNextLSN) throws IndexOperationException {

		initBuffer();

		int returnVal = page.insertSequenceAfter(nodes, currentOffset,
				currentDeweyID);

		if (returnVal == BracketPage.INSERTION_DUPLICATE) {
			throw new IndexOperationException(
					"Insertion key %s already exists in the index!",
					nodes.getLowKey());
		} else if (returnVal == BracketPage.INSERTION_NO_SPACE) {
			return false;
		} else {
			setCurrentOffset(returnVal);
			return true;
		}
	}

	@Override
	public boolean insertSequence(BracketNodeSequence nodes, boolean logged,
			long undoNextLSN) throws IndexOperationException {
		declareContextFree();
		initBuffer();

		int returnVal = page.insertSequence(nodes, currentDeweyID);

		if (returnVal == BracketPage.INSERTION_DUPLICATE) {
			throw new IndexOperationException(
					"Insertion key %s already exists in the index!",
					nodes.getLowKey());
		} else if (returnVal == BracketPage.INSERTION_NO_SPACE) {
			return false;
		} else {
			setCurrentOffset(returnVal);
			return true;
		}
	}

	@Override
	public HintPageInformation getHintPageInformation() {
		if (currentOffset == BracketPage.BEFORE_LOW_KEY_OFFSET) {
			return null;
		} else {
			return new HintPageInformation(page.getPageID(), page.getLSN(),
					currentOffset);
		}
	}

	@Override
	public BracketNodeSequence deleteSequenceAfter(boolean logged,
			long undoNextLSN) throws IndexOperationException {
		// assert(currentOffset != BracketPage.BEFORE_LOW_ID_KEYOFFSET)

		try {

			// prepare deletion
			DeletePreparation delPrep = page.splitAfterCurrentPrepare(
					currentOffset, currentDeweyID);

			// get nodes that are supposed to be deleted
			BracketNodeSequence nodes = page.getBracketNodeSequence(delPrep);

			// delete nodes from page
			page.delete(delPrep, externalValueLoader);

			return nodes;

		} catch (BracketPageException e) {
			throw new IndexOperationException(e);
		}
	}

	@Override
	public boolean moveNextToLastRecord() throws IndexOperationException {
		initBuffer();

		NavigationResult navRes = page.navigateNextToLastCF(currentDeweyID);

		if (navRes.status == NavigationStatus.FOUND) {
			// adjust current offset
			setCurrentOffset(navRes.keyOffset, navRes.keyType);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * @see org.brackit.server.store.index.bracket.page.Leaf#navigateFirstChild()
	 */
	@Override
	public NavigationStatus navigateFirstChild() {
		// assert(currentOffset != BracketPage.BEFORE_LOW_ID_KEYOFFSET);

		NavigationResult navRes = page.navigateFirstChild(currentOffset,
				currentDeweyID, bufferedKeyType);

		if (navRes.status == NavigationStatus.FOUND) {
			// adjust current offset
			setCurrentOffset(navRes.keyOffset, navRes.keyType);
		} else {
			moveBeforeFirst();
		}

		return navRes.status;
	}

	/**
	 * @see org.brackit.server.store.index.bracket.page.Leaf#navigateNextSibling()
	 */
	@Override
	public NavigationStatus navigateNextSibling() {
		// assert(currentOffset != BracketPage.BEFORE_LOW_ID_KEYOFFSET);

		NavigationResult navRes = page.navigateNextSibling(currentOffset,
				currentDeweyID, bufferedKeyType);

		if (navRes.status == NavigationStatus.FOUND) {
			// adjust current offset
			setCurrentOffset(navRes.keyOffset, navRes.keyType);
		} else {
			moveBeforeFirst();
		}

		return navRes.status;
	}
}
