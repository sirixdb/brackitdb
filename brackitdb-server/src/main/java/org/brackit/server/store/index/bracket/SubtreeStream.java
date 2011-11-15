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
package org.brackit.server.store.index.bracket;

import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.bracket.BracketLocator;
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.store.index.IndexAccessException;
import org.brackit.server.store.index.bracket.filter.BracketFilter;

/**
 * @author Martin Hiller
 * 
 */
public final class SubtreeStream extends StreamIterator {

	private int subtreeRootLevel = -1;
	private final boolean self;

	public SubtreeStream(BracketLocator locator, BracketTree tree,
			XTCdeweyID subtreeRoot, HintPageInformation hintPageInfo,
			BracketFilter filter, boolean self) {
		super(locator, tree, subtreeRoot, hintPageInfo, filter);
		this.self = self;
	}

	/**
	 * @see org.brackit.server.store.index.bracket.StreamIterator#first()
	 */
	@Override
	protected void first() throws IndexOperationException, IndexAccessException {

		if (startDeweyID.isDocument()) {
			page = tree.openInternal(tx, locator.rootPageID,
					NavigationMode.TO_KEY,
					XTCdeweyID.newRootID(startDeweyID.getDocID()), OPEN_MODE,
					null, deweyIDBuffer);
			subtreeRootLevel = 1;
		} else {

			if (page == null) {
				// hint page could not be loaded
				page = tree.navigateViaIndexAccess(tx, locator.rootPageID,
						NavigationMode.TO_KEY, startDeweyID, OPEN_MODE,
						deweyIDBuffer);
			}
			subtreeRootLevel = page.getLevel();
			
			if (!self) {
				// move to next node
				nextInternal();
			}
		}
	}

	/**
	 * @see org.brackit.server.store.index.bracket.StreamIterator#nextInternal()
	 */
	@Override
	protected void nextInternal() throws IndexOperationException,
			IndexAccessException {

		if (!page.moveNext()) {
			// use BracketTree to load next page
			page = tree.getNextPage(tx, locator.rootPageID, page, OPEN_MODE, true);
			if (page != null && !page.moveFirst()) {
				page.cleanup();
				page = null;
			}
		}

		if (page != null && page.getLevel() <= subtreeRootLevel
				&& !page.isAttribute()) {
			// reached end of subtree
			page.cleanup();
			page = null;
		}
	}

	@Override
	protected BracketNode preFirst() throws IndexOperationException,
			IndexAccessException {
		if (self && startDeweyID.isDocument()) {
			return new BracketNode(locator);
		} else {
			return null;
		}
	}

}