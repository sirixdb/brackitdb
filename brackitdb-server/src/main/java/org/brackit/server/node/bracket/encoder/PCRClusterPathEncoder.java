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
package org.brackit.server.node.bracket.encoder;

import org.brackit.server.metadata.pathSynopsis.PSNode;
import org.brackit.server.node.XTCdeweyID;
import org.brackit.server.node.bracket.BracketCollection;
import org.brackit.server.node.bracket.BracketLocator;
import org.brackit.server.node.bracket.BracketNode;
import org.brackit.server.node.txnode.IndexEncoder;
import org.brackit.server.store.Field;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.Kind;

/**
 * @author Karsten Schmidt
 *
 */
public class PCRClusterPathEncoder implements IndexEncoder<BracketNode> {
	
	private final BracketCollection collection;

	private static final byte[] DUMMY_VALUE = new byte[0];

	public PCRClusterPathEncoder(BracketCollection collection) {
		this.collection = collection;
	}

	@Override
	public BracketNode decode(byte[] key, byte[] value) throws DocumentException {
		
		XTCdeweyID deweyID = Field.PCRCOLLECTIONDEWEYID.decodeDeweyID(
				collection.getID(), key);
		int pcr = Field.PCRCOLLECTIONDEWEYID.decodePCR(key);
		BracketNode document = collection.getDocument(deweyID.getDocID().getDocNumber());

		if (deweyID.isAttribute()) {
			return document.getNode(deweyID);
		}

		BracketLocator locator = document.getLocator();
		PSNode psNode = locator.pathSynopsis.get(pcr);
		byte type = (deweyID.level == psNode.getLevel() ? Kind.ELEMENT.ID
				: Kind.TEXT.ID);

		return new BracketNode(locator, deweyID, type, null, psNode);
	}

	@Override
	public byte[] encodeKey(BracketNode node) throws DocumentException {
		return Field.PCRCOLLECTIONDEWEYID.encode(node.getDeweyID(), node
				.getPCR());
	}

	@Override
	public byte[] encodeValue(BracketNode node) throws DocumentException {
		return DUMMY_VALUE;
	}

	@Override
	public Field getKeyType() {
		return Field.PCRCOLLECTIONDEWEYID;
	}

	@Override
	public Field getValueType() {
		return Field.NULL;
	}

	@Override
	public boolean sortKey() {
		return true;
	}

	@Override
	public boolean sortValue() {
		return false;
	}
}
