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
package org.brackit.server.node.bracket;

import org.brackit.server.io.buffer.PageID;
import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.metadata.pathSynopsis.manager.PathSynopsisMgr;
import org.brackit.server.node.DocID;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.el.ElRecordAccess;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * @author Martin Hiller
 *
 */
public class BracketLocator {
	
	public final DocID docID;

	public final PageID rootPageID;

	public final PathSynopsisMgr pathSynopsis;

	public final BracketCollection collection;

	public BracketLocator(BracketCollection collection, DocID docID, PageID rootPageID) {
		this.docID = docID;
		this.rootPageID = rootPageID;
		this.collection = collection;
		this.pathSynopsis = collection.getPathSynopsis();
	}

	public BracketLocator(BracketCollection collection, BracketLocator locator) {
		this.docID = locator.docID;
		this.rootPageID = locator.rootPageID;
		this.collection = collection;
		this.pathSynopsis = locator.pathSynopsis;
	}

	public BracketNode fromBytes(XTCdeweyID deweyID, byte[] physicalRecord)
			throws DocumentException {
		String value = null;
		PSNode psNode = null;

		int pcr = ElRecordAccess.getPCR(physicalRecord);
		byte type = ElRecordAccess.getType(physicalRecord);

		if (deweyID.isAttribute()) {
			// the physical record has the pcr that we need
			psNode = pathSynopsis.get(collection.getTX(), pcr);
			value = ElRecordAccess.getValue(physicalRecord);
		} else {
			// get the correct pcr for a (probably) virtualized element
			int level = deweyID.getLevel();
			psNode = pathSynopsis.getAncestorOrParent(collection.getTX(), pcr,
					level);
			pcr = psNode.getPCR();

			if (psNode.getLevel() == level - 1) {
				value = ElRecordAccess.getValue(physicalRecord);
			} else {
				type = Kind.ELEMENT.ID;
			}
		}

		return new BracketNode(this, deweyID, type, value, psNode);
	}
}